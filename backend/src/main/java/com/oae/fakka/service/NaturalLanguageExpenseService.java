package com.oae.fakka.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oae.fakka.dto.CreateExpenseRequest;
import com.oae.fakka.dto.ParseExpenseTextRequest;
import com.oae.fakka.dto.ParsedExpenseDraftResponse;
import com.oae.fakka.dto.SplitType;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.AiResponseNotUsableException;
import com.oae.fakka.exception.AiUnavailableException;
import com.oae.fakka.exception.ApiException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.stream.Collectors;

/**
 * Turns a sentence into a proposed expense (FR-20 to FR-23).
 *
 * <h2>It cannot write, by construction</h2>
 * The only repositories injected are the two it reads members from. There is no expense
 * repository here and no {@link ExpenseService}, so this class could not persist an expense if
 * somebody asked it to -- which is what BR-6 needs: an AI suggestion becomes an expense only by
 * going back through {@code POST /api/groups/{groupId}/expenses} with a person having looked at
 * it. The transaction is {@code readOnly} as a second statement of the same thing.
 *
 * <h2>The model extracts; this class decides</h2>
 * A clean division, and it is deliberate. The model reads names and an amount out of prose, which
 * is what it is good at. Everything with a right answer stays here: converting EGP to piastres,
 * resolving a name to a member of this group, and deciding whether what came back is usable at
 * all. The model is never asked to do arithmetic or to invent an id.
 *
 * <h2>Failure is a first-class outcome (BR-7)</h2>
 * Three shapes of failure, three answers, none of them a 500:
 * <ul>
 *   <li>Not configured, unreachable, timed out, or empty: {@link AiUnavailableException}, a 503.</li>
 *   <li>Answered, but the answer is not JSON, is missing a field, has an unusable amount, or
 *       names nobody in the group: {@link AiResponseNotUsableException}, a 422.</li>
 *   <li>Anything unforeseen: caught at the end of {@link #parse} and reported as the 503, because
 *       an unhandled 500 would tell the client nothing about what to do and the answer is always
 *       the same -- offer the manual form.</li>
 * </ul>
 */
@Slf4j
@Service
public class NaturalLanguageExpenseService {

    /**
     * The instruction. Three things in it are load-bearing:
     * <ul>
     *   <li><strong>Only JSON.</strong> Prose around the object is tolerated by the reader below,
     *       but asking for a bare object is what makes that the exception rather than the rule.</li>
     *   <li><strong>Names from the list.</strong> The model is given the group members and told to
     *       use those spellings, which turns name resolution from guesswork into a lookup.</li>
     *   <li><strong>EGP, not piastres.</strong> Asking for the unit as written in the text keeps
     *       the model away from arithmetic; multiplying by a hundred is this application job.</li>
     * </ul>
     */
    private static final String SYSTEM_PROMPT = """
            You extract expense details from short, informal descriptions of shared spending.

            Reply with ONLY a JSON object, no prose, no markdown fences, in exactly this shape:
            {"description": string, "totalAmount": number, "paidBy": string, \
            "participants": [string], "splitType": "EQUAL" | "CUSTOM"}

            Rules:
            - description: a short label for the expense, such as "Dinner" or "Uber to the airport".
            - totalAmount: the total in EGP as a plain number, as written in the text. Use 900 for \
            "900 EGP", 12.5 for "12.50". Never multiply or divide it.
            - paidBy: the person who paid, spelled exactly as it appears in the member list below.
            - participants: everyone who shares the cost, spelled exactly as in the member list. \
            Include the payer only if they also consumed something.
            - splitType: "EQUAL" when the cost is shared evenly or the text does not say otherwise. \
            "CUSTOM" when the text describes people consuming different things or different amounts.
            - Use only names from the member list. If somebody mentioned is not on it, leave them out.
            - If the text does not describe an expense, or has no amount, reply with \
            {"error": "not an expense"}.
            """;

    /** How the group membership is handed to the model, appended to the text being parsed. */
    private static final String MEMBER_LIST_PREFIX = "Group members: ";

    /**
     * The same ceiling the manual endpoint enforces. A model that hallucinates an enormous number
     * should be refused here rather than produce a draft that the save endpoint would reject
     * anyway, and the two limits must not be able to drift apart.
     */
    private static final long MAX_AMOUNT_IN_PIASTRES = CreateExpenseRequest.MAX_AMOUNT_IN_PIASTRES;

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberNameMatcher memberNameMatcher;
    private final LlmClient llmClient;

    /*
     * Constructed directly rather than injected. Spring's own Jackson auto-configuration in
     * this Boot 4.1 stack publishes a Jackson 3 tools.jackson.databind.json.JsonMapper -- used
     * for HTTP request and response bodies -- not this classic ObjectMapper, so there is no
     * Spring-managed bean of this type to ask for. Nothing about parsing a small object out of
     * an LLM reply needs to share configuration with HTTP message conversion anyway, and a
     * plain ObjectMapper is cheap and stateless enough that owning one locally is simpler than
     * wiring a bean for it.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NaturalLanguageExpenseService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            MemberNameMatcher memberNameMatcher,
            LlmClient llmClient) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.memberNameMatcher = memberNameMatcher;
        this.llmClient = llmClient;
    }

    /**
     * Parses the text into a draft. Writes nothing, ever.
     * <p>
     * The unknown-group 404 and the unavailable-parser 503 both happen before the model is called,
     * so a misconfigured deployment or a bad id costs nothing.
     */
    @Transactional(readOnly = true)
    public ParsedExpenseDraftResponse parse(Long groupId, ParseExpenseTextRequest request) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
        if (!llmClient.isAvailable()) {
            throw new AiUnavailableException("The expense parser is not configured");
        }

        List<User> members = groupMemberRepository.findMembersOf(groupId);
        if (members.isEmpty()) {
            // Nothing to resolve names against, so there is no draft to be had.
            throw new AiResponseNotUsableException("This group has no members to attribute an expense to");
        }

        try {
            String reply = llmClient.complete(SYSTEM_PROMPT, promptFor(request.text(), members));
            LlmExpenseJson extracted = readJson(reply);
            return toDraft(extracted, members);
        } catch (ApiException deliberate) {
            // Already the right status with the right message; passing it through is the point.
            throw deliberate;
        } catch (Exception unexpected) {
            /*
             * The BR-7 backstop. Whatever went wrong -- a parser bug here, something a dependency
             * threw that was not anticipated -- the caller gets the one answer that helps, and the
             * detail goes to the log where it can be fixed.
             */
            log.error("Unexpected failure parsing expense text for group id={}", groupId, unexpected);
            throw new AiUnavailableException("The expense parser failed unexpectedly", unexpected);
        }
    }

    /** The text, plus the member names the model is allowed to use. */
    private static String promptFor(String text, List<User> members) {
        String names = members.stream().map(User::getName).collect(Collectors.joining(", "));
        return MEMBER_LIST_PREFIX + names + "\n\n" + text;
    }

    /**
     * Reads the model reply as the expected object.
     * <p>
     * The braces are located rather than the whole reply parsed, because a model that has been
     * told to return bare JSON still occasionally wraps it in a fence or a sentence. Tolerating
     * that is cheap; the alternative is failing a parse over a decoration.
     */
    private LlmExpenseJson readJson(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("Expense parser reply contained no JSON object");
            throw new AiResponseNotUsableException("The expense parser did not return an expense");
        }

        String json = reply.substring(start, end + 1);
        try {
            LlmExpenseJson extracted = objectMapper.readValue(json, LlmExpenseJson.class);
            if (extracted == null) {
                throw new AiResponseNotUsableException("The expense parser did not return an expense");
            }
            return extracted;
        } catch (JacksonException exception) {
            // The reply itself is not logged: it is model output derived from user text.
            log.warn("Expense parser reply was not valid JSON", exception);
            throw new AiResponseNotUsableException("The expense parser returned a malformed answer");
        }
    }

    /**
     * Turns the extracted fields into a draft, refusing anything that could not be reviewed
     * sensibly.
     */
    private ParsedExpenseDraftResponse toDraft(LlmExpenseJson extracted, List<User> members) {
        String description = blankToNull(extracted.description());
        if (description == null) {
            throw new AiResponseNotUsableException("The text does not describe an expense");
        }

        long totalAmount = toPiastres(extracted.totalAmount());

        SequencedSet<String> unresolved = new LinkedHashSet<>();

        User payer = resolve(extracted.paidBy(), members, unresolved).orElse(null);
        if (payer == null) {
            /*
             * A draft with no payer cannot be saved and cannot be sensibly reviewed either -- BR-2
             * needs exactly one, and the form has nobody to pre-select. Better to send the user to
             * the manual form than to a half-filled one.
             */
            throw new AiResponseNotUsableException(
                    "Could not tell which group member paid");
        }

        List<UserSummaryResponse> participants = resolveParticipants(extracted, members, unresolved);
        if (participants.isEmpty()) {
            // BR-3: an expense nobody shares is not an expense.
            throw new AiResponseNotUsableException(
                    "Could not tell which group members shared the expense");
        }

        return new ParsedExpenseDraftResponse(
                description,
                totalAmount,
                /*
                 * No category: the model is not asked for one, and inventing FOOD for everything
                 * would be a suggestion the reviewer has to undo more often than accept.
                 */
                null,
                UserSummaryResponse.from(payer),
                participants,
                splitTypeOf(extracted.splitType()),
                List.copyOf(unresolved));
    }

    /**
     * Participants, in the order the model listed them, without repeats.
     * <p>
     * Order matters downstream: an equal split gives the rounding remainder to the participants at
     * the front of the list, so the sequence the reviewer sees is the sequence that decides who
     * pays the extra piastre.
     */
    private List<UserSummaryResponse> resolveParticipants(
            LlmExpenseJson extracted, List<User> members, SequencedSet<String> unresolved) {

        if (extracted.participants() == null) {
            return List.of();
        }

        SequencedSet<Long> seen = new LinkedHashSet<>();
        List<UserSummaryResponse> participants = new ArrayList<>();
        for (String name : extracted.participants()) {
            resolve(name, members, unresolved)
                    .filter(member -> seen.add(member.getId()))
                    .ifPresent(member -> participants.add(UserSummaryResponse.from(member)));
        }
        return participants;
    }

    /** Resolves one name, recording it as unresolved when the matcher will not commit. */
    private Optional<User> resolve(String name, List<User> members, SequencedSet<String> unresolved) {
        String trimmed = blankToNull(name);
        if (trimmed == null) {
            return Optional.empty();
        }

        Optional<User> match = memberNameMatcher.matchOne(trimmed, members);
        if (match.isEmpty()) {
            // Reported rather than dropped: the form asks who was meant.
            unresolved.add(trimmed);
        }
        return match;
    }

    /**
     * EGP to piastres, exactly.
     * <p>
     * Rounded to the piastre rather than refused, because a third decimal from a model is noise
     * and failing the whole parse over it would send the user to the manual form for nothing. The
     * result is still a draft a person checks before it is saved.
     */
    private static long toPiastres(BigDecimal amountInEgp) {
        if (amountInEgp == null) {
            throw new AiResponseNotUsableException("Could not find an amount in the text");
        }

        long piastres;
        try {
            piastres = amountInEgp.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new AiResponseNotUsableException("The amount in the text is out of range");
        }

        if (piastres <= 0) {
            throw new AiResponseNotUsableException("The amount in the text is not a positive value");
        }
        if (piastres > MAX_AMOUNT_IN_PIASTRES) {
            throw new AiResponseNotUsableException("The amount in the text is out of range");
        }
        return piastres;
    }

    /**
     * An unrecognised or missing split type becomes EQUAL, which is the ordinary case (FR-18) and
     * the one the reviewer is most likely to accept. Only the two known values are honoured, so a
     * model inventing a third does not reach the form.
     */
    private static SplitType splitTypeOf(String splitType) {
        if (splitType == null) {
            return SplitType.EQUAL;
        }
        return SplitType.CUSTOM.name().equals(splitType.trim().toUpperCase(Locale.ROOT))
                ? SplitType.CUSTOM
                : SplitType.EQUAL;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
