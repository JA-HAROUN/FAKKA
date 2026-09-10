package com.oae.fakka.service;

import com.oae.fakka.dto.ParseExpenseTextRequest;
import com.oae.fakka.dto.ParsedExpenseDraftResponse;
import com.oae.fakka.dto.SplitType;
import com.oae.fakka.dto.UserSummaryResponse;
import com.oae.fakka.entity.User;
import com.oae.fakka.exception.AiResponseNotUsableException;
import com.oae.fakka.exception.AiUnavailableException;
import com.oae.fakka.exception.ResourceNotFoundException;
import com.oae.fakka.repository.GroupMemberRepository;
import com.oae.fakka.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link NaturalLanguageExpenseService}, the piece BR-7 depends on most: nothing
 * that can go wrong here -- an unreachable model, a malformed reply, a name matching nobody --
 * may surface as anything other than a 503 or a 422 with a message that points at the manual
 * form.
 * <p>
 * {@link MemberNameMatcher} is mocked so each test controls name resolution directly rather than
 * depending on {@code MemberNameMatcherTest}'s fuzzy-matching rules. The service's own JSON
 * reader is not mockable and is not mocked: the JSON-tolerance behaviour -- markdown fences,
 * malformed syntax, a wrong field type -- is exactly what these tests exercise, through the
 * literal reply text each test hands the mocked {@link LlmClient}.
 */
@ExtendWith(MockitoExtension.class)
class NaturalLanguageExpenseServiceTest {

    private static final long GROUP_ID = 10L;

    private static final User AHMED = user(1L, "Ahmed Ragy");
    private static final User MOHAMED = user(2L, "Mohamed Salah");
    private static final User ZEINAB = user(3L, "Zeinab Hassan");
    private static final List<User> MEMBERS = List.of(AHMED, MOHAMED, ZEINAB);

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private MemberNameMatcher memberNameMatcher;

    @Mock
    private LlmClient llmClient;

    private NaturalLanguageExpenseService service;

    @BeforeEach
    void setUp() {
        service = new NaturalLanguageExpenseService(
                groupRepository, groupMemberRepository, memberNameMatcher, llmClient);
    }

    /*
     * The happy path, and the three questions that matter about it: names resolve to the right
     * ids, the amount converts from EGP to piastres exactly, and the split type is read correctly.
     */

    @Test
    void parsesAWellFormedReplyIntoADraft() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                {"description": "Dinner", "totalAmount": 900, "paidBy": "John", \
                "participants": ["John", "Ahmed"], "splitType": "EQUAL"}
                """);
        givenResolves("John", AHMED);
        givenResolves("Ahmed", AHMED);

        ParsedExpenseDraftResponse draft = service.parse(GROUP_ID, request("John paid 900 for dinner"));

        assertThat(draft.description()).isEqualTo("Dinner");
        assertThat(draft.totalAmount()).isEqualTo(90_000L);
        assertThat(draft.paidBy()).isEqualTo(UserSummaryResponse.from(AHMED));
        assertThat(draft.splitType()).isEqualTo(SplitType.EQUAL);
        assertThat(draft.suggestedCategory()).isNull();
        assertThat(draft.unresolvedNames()).isEmpty();
    }

    /** A fractional EGP amount converts to piastres exactly, with no drift. */
    @Test
    void convertsAFractionalAmountToPiastresExactly() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("12.50", "Ahmed", "[\"Ahmed\"]", "EQUAL"));
        givenResolves("Ahmed", AHMED);

        ParsedExpenseDraftResponse draft = service.parse(GROUP_ID, request("text"));

        assertThat(draft.totalAmount()).isEqualTo(1_250L);
    }

    /** A third decimal from the model is noise, rounded to the nearest piastre rather than refused. */
    @Test
    void roundsAThirdDecimalToTheNearestPiastre() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("12.999", "Ahmed", "[\"Ahmed\"]", "EQUAL"));
        givenResolves("Ahmed", AHMED);

        ParsedExpenseDraftResponse draft = service.parse(GROUP_ID, request("text"));

        assertThat(draft.totalAmount()).isEqualTo(1_300L);
    }

    /** CUSTOM is honoured, case-insensitively, exactly as the model wrote it. */
    @Test
    void recognisesACustomSplitTypeCaseInsensitively() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("900", "Ahmed", "[\"Ahmed\",\"Mohamed\"]", "custom"));
        givenResolves("Ahmed", AHMED);
        givenResolves("Mohamed", MOHAMED);

        ParsedExpenseDraftResponse draft = service.parse(GROUP_ID, request("text"));

        assertThat(draft.splitType()).isEqualTo(SplitType.CUSTOM);
    }

    /** A missing splitType is the ordinary case (FR-18), not a failure. */
    @Test
    void aMissingSplitTypeDefaultsToEqual() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                {"description": "Dinner", "totalAmount": 900, "paidBy": "Ahmed", \
                "participants": ["Ahmed"]}
                """);
        givenResolves("Ahmed", AHMED);

        assertThat(service.parse(GROUP_ID, request("text")).splitType()).isEqualTo(SplitType.EQUAL);
    }

    /** A split type the model invented does not reach the form as if it were real. */
    @Test
    void anUnrecognisedSplitTypeDefaultsToEqual() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("900", "Ahmed", "[\"Ahmed\"]", "HALF_AND_HALF"));
        givenResolves("Ahmed", AHMED);

        assertThat(service.parse(GROUP_ID, request("text")).splitType()).isEqualTo(SplitType.EQUAL);
    }

    /** The payer can also be a participant; nothing here second-guesses that. */
    @Test
    void thePayerCanAlsoAppearAmongTheParticipants() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("900", "Ahmed", "[\"Ahmed\",\"Mohamed\",\"Zeinab\"]", "EQUAL"));
        givenResolves("Ahmed", AHMED);
        givenResolves("Mohamed", MOHAMED);
        givenResolves("Zeinab", ZEINAB);

        ParsedExpenseDraftResponse draft = service.parse(GROUP_ID, request("text"));

        assertThat(draft.participants())
                .containsExactly(
                        UserSummaryResponse.from(AHMED),
                        UserSummaryResponse.from(MOHAMED),
                        UserSummaryResponse.from(ZEINAB));
    }

    /** Two names the model wrote as the same person collapse to one row, not two. */
    @Test
    void aRepeatedParticipantNameIsNotDoubleCounted() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("900", "Ahmed", "[\"Ahmed\",\"Ahmed Ragy\"]", "EQUAL"));
        givenResolves("Ahmed", AHMED);
        givenResolves("Ahmed Ragy", AHMED);

        ParsedExpenseDraftResponse draft = service.parse(GROUP_ID, request("text"));

        assertThat(draft.participants()).containsExactly(UserSummaryResponse.from(AHMED));
    }

    /** A name that matches nobody is reported for review rather than silently dropped. */
    @Test
    void aParticipantMatchingNobodyIsListedAsUnresolved() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("900", "Ahmed", "[\"Ahmed\",\"Youssef\"]", "EQUAL"));
        givenResolves("Ahmed", AHMED);
        given(memberNameMatcher.matchOne(eq("Youssef"), any())).willReturn(Optional.empty());

        ParsedExpenseDraftResponse draft = service.parse(GROUP_ID, request("text"));

        assertThat(draft.participants()).containsExactly(UserSummaryResponse.from(AHMED));
        assertThat(draft.unresolvedNames()).containsExactly("Youssef");
    }

    /*
     * Refusals before the model is even asked (BR-7 has an answer for every one of these, and
     * none of them costs a network call).
     */

    @Test
    void reports404ForAnUnknownGroupWithoutCallingTheModel() {
        given(groupRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> service.parse(99L, request("text")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Group 99 was not found");

        verifyNoInteractions(llmClient, groupMemberRepository);
    }

    /** An unconfigured or switched-off parser is 503, not an attempt that then fails. */
    @Test
    void reports503WhenTheParserIsNotAvailableWithoutCallingItOrLoadingMembers() {
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);
        given(llmClient.isAvailable()).willReturn(false);

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("The expense parser is not configured; enter the expense manually instead");

        verify(llmClient, never()).complete(anyString(), anyString());
        verifyNoInteractions(groupMemberRepository);
    }

    /** Nothing to resolve names against, so there is no draft to be had. */
    @Test
    void reports422WhenTheGroupHasNoMembersWithoutCallingTheModel() {
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);
        given(llmClient.isAvailable()).willReturn(true);
        given(groupMemberRepository.findMembersOf(GROUP_ID)).willReturn(List.of());

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("This group has no members");

        verify(llmClient, never()).complete(anyString(), anyString());
    }

    /*
     * Failures from the model call itself.
     */

    /** An {@code AiUnavailableException} the client already raised is passed through unchanged. */
    @Test
    void passesThroughAnUnavailableExceptionFromTheClientUnchanged() {
        givenGroupExistsWithMembers();
        given(llmClient.complete(anyString(), anyString()))
                .willThrow(new AiUnavailableException("The expense parser could not be reached"));

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("The expense parser could not be reached; enter the expense manually instead");
    }

    /**
     * The BR-7 backstop: whatever the client threw that was not anticipated becomes the one
     * answer that helps, never an unhandled 500.
     */
    @Test
    void wrapsAnUnexpectedExceptionFromTheClientAsUnavailable() {
        givenGroupExistsWithMembers();
        given(llmClient.complete(anyString(), anyString()))
                .willThrow(new IllegalStateException("connection pool exhausted"));

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("failed unexpectedly")
                .hasMessageContaining("enter the expense manually instead");
    }

    /*
     * Failures in reading the reply -- reachable, but not usable.
     */

    @Test
    void reports422WhenTheReplyContainsNoJsonAtAll() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("Sorry, I can't help with that.");

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("did not return an expense");
    }

    /** A model told to return bare JSON still sometimes wraps it; that decoration is tolerated. */
    @Test
    void toleratesJsonWrappedInProseOrAMarkdownFence() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                Sure, here you go:
                ```json
                {"description": "Dinner", "totalAmount": 900, "paidBy": "Ahmed", \
                "participants": ["Ahmed"], "splitType": "EQUAL"}
                ```
                """);
        givenResolves("Ahmed", AHMED);

        assertThat(service.parse(GROUP_ID, request("text")).description()).isEqualTo("Dinner");
    }

    @Test
    void reports422ForSyntacticallyBrokenJson() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("{\"description\": \"Dinner\", totalAmount: }");

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("malformed answer");
    }

    /** A field of the wrong type is exactly as unusable as syntactically broken JSON. */
    @Test
    void reports422WhenAFieldHasTheWrongType() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                {"description": "Dinner", "totalAmount": "a lot", "paidBy": "Ahmed", \
                "participants": ["Ahmed"], "splitType": "EQUAL"}
                """);

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("malformed answer");
    }

    /** An unrecognised extra field must not break a reply that is otherwise perfectly usable. */
    @Test
    void toleratesUnknownFieldsInTheReply() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                {"description": "Dinner", "totalAmount": 900, "paidBy": "Ahmed", \
                "participants": ["Ahmed"], "splitType": "EQUAL", "currency": "EGP"}
                """);
        givenResolves("Ahmed", AHMED);

        assertThat(service.parse(GROUP_ID, request("text")).description()).isEqualTo("Dinner");
    }

    /** The system prompt's own escape hatch for non-expense text: refused, not guessed at. */
    @Test
    void reports422WhenTheModelSaysTheTextIsNotAnExpense() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("{\"error\": \"not an expense\"}");

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("does not describe an expense");
    }

    @Test
    void reports422WhenDescriptionIsMissing() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                {"totalAmount": 900, "paidBy": "Ahmed", "participants": ["Ahmed"], "splitType": "EQUAL"}
                """);

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("does not describe an expense");
    }

    @Test
    void reports422WhenDescriptionIsBlank() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("\"description\": \"   \",", "900", "Ahmed", "[\"Ahmed\"]", "EQUAL"));

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("does not describe an expense");
    }

    @Test
    void reports422WhenTotalAmountIsMissing() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                {"description": "Dinner", "paidBy": "Ahmed", "participants": ["Ahmed"], "splitType": "EQUAL"}
                """);

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("Could not find an amount");
    }

    @Test
    void reports422WhenTotalAmountIsZeroOrNegative() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("0", "Ahmed", "[\"Ahmed\"]", "EQUAL"));

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("not a positive value");
    }

    /** The same ceiling the manual endpoint enforces, so a hallucinated total is refused here too. */
    @Test
    void reports422WhenTotalAmountExceedsTheSharedCeiling() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("2000000000", "Ahmed", "[\"Ahmed\"]", "EQUAL"));

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("out of range");
    }

    /** An amount too large even to convert must fail the same way as one merely over the cap. */
    @Test
    void reports422WhenTotalAmountOverflowsOnConversion() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith(
                "100000000000000000000000000000", "Ahmed", "[\"Ahmed\"]", "EQUAL"));

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("out of range");
    }

    /** BR-2: a draft nobody clearly paid for cannot be reviewed, so it is refused rather than half-built. */
    @Test
    void reports422WhenThePayerCannotBeResolved() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("900", "Youssef", "[\"Ahmed\"]", "EQUAL"));
        given(memberNameMatcher.matchOne(eq("Youssef"), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("Could not tell which group member paid");
    }

    @Test
    void reports422WhenPaidByIsMissing() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                {"description": "Dinner", "totalAmount": 900, "participants": ["Ahmed"], "splitType": "EQUAL"}
                """);

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("Could not tell which group member paid");
    }

    /** BR-3: an expense nobody shares is refused, whether the model omitted the field or left it empty. */
    @Test
    void reports422WhenParticipantsIsMissing() {
        givenGroupExistsWithMembers();
        givenTheModelReplies("""
                {"description": "Dinner", "totalAmount": 900, "paidBy": "Ahmed", "splitType": "EQUAL"}
                """);
        givenResolves("Ahmed", AHMED);

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("Could not tell which group members shared");
    }

    @Test
    void reports422WhenParticipantsIsEmpty() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("900", "Ahmed", "[]", "EQUAL"));
        givenResolves("Ahmed", AHMED);

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("Could not tell which group members shared");
    }

    /** Every participant name matching nobody leaves nothing usable, same as an empty list. */
    @Test
    void reports422WhenEveryParticipantIsUnresolved() {
        givenGroupExistsWithMembers();
        givenTheModelReplies(jsonWith("900", "Ahmed", "[\"Youssef\",\"Sara\"]", "EQUAL"));
        givenResolves("Ahmed", AHMED);
        given(memberNameMatcher.matchOne(eq("Youssef"), any())).willReturn(Optional.empty());
        given(memberNameMatcher.matchOne(eq("Sara"), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.parse(GROUP_ID, request("text")))
                .isInstanceOf(AiResponseNotUsableException.class)
                .hasMessageContaining("Could not tell which group members shared");
    }

    private void givenGroupExistsWithMembers() {
        given(groupRepository.existsById(GROUP_ID)).willReturn(true);
        given(llmClient.isAvailable()).willReturn(true);
        given(groupMemberRepository.findMembersOf(GROUP_ID)).willReturn(MEMBERS);
    }

    private void givenTheModelReplies(String reply) {
        given(llmClient.complete(anyString(), anyString())).willReturn(reply);
    }

    private void givenResolves(String name, User member) {
        given(memberNameMatcher.matchOne(eq(name), any())).willReturn(Optional.of(member));
    }

    private static String jsonWith(
            String totalAmount, String paidBy, String participantsJsonArray, String splitType) {
        return """
                {"description": "Dinner", "totalAmount": %s, "paidBy": "%s", \
                "participants": %s, "splitType": "%s"}
                """.formatted(totalAmount, paidBy, participantsJsonArray, splitType);
    }

    /** Overload for the blank-description test, which needs one more field spliced in. */
    private static String jsonWith(
            String extraFieldPrefix, String totalAmount, String paidBy,
            String participantsJsonArray, String splitType) {
        return """
                {%s "totalAmount": %s, "paidBy": "%s", \
                "participants": %s, "splitType": "%s"}
                """.formatted(extraFieldPrefix, totalAmount, paidBy, participantsJsonArray, splitType);
    }

    private static ParseExpenseTextRequest request(String text) {
        return new ParseExpenseTextRequest(text);
    }

    private static User user(Long id, String name) {
        return User.builder()
                .id(id)
                .name(name)
                .email("user%d@example.com".formatted(id))
                .passwordHash("$2a$10$" + "x".repeat(53))
                .build();
    }
}
