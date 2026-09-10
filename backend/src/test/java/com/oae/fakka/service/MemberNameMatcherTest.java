package com.oae.fakka.service;

import com.oae.fakka.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MemberNameMatcher}: a pure function, so no mocks and no Spring context.
 * <p>
 * The behaviour that matters most here is the refusal to guess -- two members who could equally
 * be "Mohamed" must come back empty, not a coin flip -- so several tests exist purely to pin that
 * down at each stage (exact, name-part, near-miss) rather than to prove the happy path.
 */
class MemberNameMatcherTest {

    private final MemberNameMatcher matcher = new MemberNameMatcher();

    private static final User AHMED = user(1L, "Ahmed Ragy");
    private static final User MOHAMED = user(2L, "Mohamed Salah");
    private static final User ZEINAB = user(3L, "Zeinab Hassan");

    private static final List<User> GROUP = List.of(AHMED, MOHAMED, ZEINAB);

    @Test
    void matchesTheWholeNameExactly() {
        assertThat(matcher.matchOne("Ahmed Ragy", GROUP)).contains(AHMED);
    }

    @Test
    void matchesIgnoringCase() {
        assertThat(matcher.matchOne("mohamed salah", GROUP)).contains(MOHAMED);
        assertThat(matcher.matchOne("MOHAMED SALAH", GROUP)).contains(MOHAMED);
    }

    @Test
    void matchesIgnoringSurroundingWhitespace() {
        assertThat(matcher.matchOne("  Ahmed Ragy  ", GROUP)).contains(AHMED);
    }

    @Test
    void matchesIgnoringPunctuation() {
        assertThat(matcher.matchOne("Ahmed Ragy.", GROUP)).contains(AHMED);
        assertThat(matcher.matchOne("\"Zeinab Hassan\"", GROUP)).contains(ZEINAB);
    }

    /** How people actually write: a first name alone, or a surname alone. */
    @Test
    void matchesASingleNamePart() {
        assertThat(matcher.matchOne("Ahmed", GROUP)).contains(AHMED);
        assertThat(matcher.matchOne("Salah", GROUP)).contains(MOHAMED);
        assertThat(matcher.matchOne("zeinab", GROUP)).contains(ZEINAB);
    }

    /** An exact whole-name match wins even when a part-match on somebody else exists too. */
    @Test
    void anExactWholeNameMatchOutranksAPartialMatchOnSomebodyElse() {
        List<User> members = List.of(user(1L, "Ahmed Ragy"), user(2L, "Ragy Youssef"));

        assertThat(matcher.matchOne("Ahmed Ragy", members)).contains(members.get(0));
    }

    /** A one-letter typo, common when typing quickly. */
    @Test
    void matchesANearMissWithinTypoDistance() {
        assertThat(matcher.matchOne("Ahmad Ragy", GROUP)).contains(AHMED);
        assertThat(matcher.matchOne("Mohammed Salah", GROUP)).contains(MOHAMED);
    }

    /** A near miss on just the one part, not the whole name. */
    @Test
    void matchesANearMissOnASingleNamePart() {
        assertThat(matcher.matchOne("Ahmad", GROUP)).contains(AHMED);
    }

    @Test
    void returnsEmptyForANameMatchingNobody() {
        assertThat(matcher.matchOne("Youssef", GROUP)).isEmpty();
    }

    /**
     * The central behaviour: two members share a first name, so "Mohamed" alone cannot be
     * resolved and must not be guessed at.
     */
    @Test
    void returnsEmptyWhenTwoMembersShareTheMatchedNamePart() {
        List<User> members = List.of(
                user(1L, "Mohamed Salah"), user(2L, "Mohamed Ali"), user(3L, "Ahmed Ragy"));

        assertThat(matcher.matchOne("Mohamed", members)).isEmpty();
    }

    /** The same ambiguity, but at the near-miss stage rather than the exact one. */
    @Test
    void returnsEmptyWhenANearMissMatchesTwoMembersEqually() {
        List<User> members = List.of(user(1L, "Alaa"), user(2L, "Alia"));

        // "Ala" is one edit from both "Alaa" and "Alia".
        assertThat(matcher.matchOne("Ala", members)).isEmpty();
    }

    @Test
    void returnsEmptyForBlankOrNullInput() {
        assertThat(matcher.matchOne("", GROUP)).isEmpty();
        assertThat(matcher.matchOne("   ", GROUP)).isEmpty();
        assertThat(matcher.matchOne(null, GROUP)).isEmpty();
    }

    @Test
    void returnsEmptyForAnEmptyOrNullMemberList() {
        assertThat(matcher.matchOne("Ahmed", List.of())).isEmpty();
        assertThat(matcher.matchOne("Ahmed", null)).isEmpty();
    }

    /** Scoped to the list given: a name matching nobody in this group is not resolved elsewhere. */
    @Test
    void neverMatchesOutsideTheGivenMemberList() {
        List<User> smallGroup = List.of(AHMED);

        assertThat(matcher.matchOne("Mohamed Salah", smallGroup)).isEmpty();
    }

    /**
     * A short name gets a tighter typo allowance (1, not 2) so a two-letter-off guess is refused
     * rather than guessed at. "Jona" is two edits from "John" -- within the two-edit allowance a
     * longer name would get, but past the one-edit allowance a four-letter name gets.
     */
    @Test
    void aShortNameUsesATighterTypoAllowanceThanALongerOne() {
        List<User> members = List.of(user(1L, "John"));

        assertThat(matcher.matchOne("Jona", members)).isEmpty();
    }

    /** Distance grows past the allowance for longer names, so a garbled name is refused. */
    @Test
    void aNameTooFarFromAnyMemberIsNotMatched() {
        assertThat(matcher.matchOne("Xyzzyx Plugh", GROUP)).isEmpty();
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
