package com.oae.fakka.service;

import com.oae.fakka.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves a name written by a person, or produced by a model, to a member of one group.
 *
 * <h2>Pure, and scoped to the group it is given</h2>
 * No repositories and no state: the candidate list is an argument, which is what makes the group
 * scoping structural rather than a rule to remember. "Ahmed" can only ever resolve to somebody
 * already in the group the caller passed in.
 *
 * <h2>Ambiguity is not a match</h2>
 * The most important behaviour here is the refusal. Two members called Mohamed and a text that
 * says "Mohamed" is not a 50/50 guess to be taken -- it is a question for the person reviewing
 * the draft. Guessing would assign a debt to the wrong member, and it would do it silently, so
 * every stage below returns a member only when exactly one candidate matches at that stage.
 *
 * <h2>The stages, loosest last</h2>
 * <ol>
 *   <li>The whole name, exactly.</li>
 *   <li>A single name part: "Ahmed" for "Ahmed Ragy", or "Salah" for "Mohamed Salah". This is how
 *       people actually write, and it is the stage that earns the class its name.</li>
 *   <li>A near miss on the whole name or on one part, within one or two characters. Covers a typo
 *       and a transliteration that differs by a letter, which is common for Arabic names written
 *       in Latin script.</li>
 * </ol>
 * Each stage only runs if the previous found nothing, so a member named exactly right always wins
 * over somebody who is merely close.
 */
@Service
public class MemberNameMatcher {

    /** Anything not a letter, a digit or a space: trailing full stops, quotes, stray punctuation. */
    private static final String NOT_NAME_CHARACTER = "[^\\p{L}\\p{N}\\s]";

    /**
     * A short name has no room for a typo allowance: at two characters, a distance of one makes
     * everything match everything. Longer names get two, which covers a doubled letter and a
     * swapped vowel without letting unrelated names collide.
     */
    private static final int SHORT_NAME_LENGTH = 4;

    /**
     * The one member this name refers to, if that can be decided.
     *
     * @return empty when nothing matches and, deliberately, also when several members match
     *         equally well. The caller reports the name as unresolved either way, and the person
     *         reviewing the draft picks
     */
    public Optional<User> matchOne(String rawName, List<User> members) {
        if (rawName == null || members == null || members.isEmpty()) {
            return Optional.empty();
        }

        String name = normalise(rawName);
        if (name.isEmpty()) {
            return Optional.empty();
        }

        return onlyMatch(members, member -> normalise(member.getName()).equals(name))
                .or(() -> onlyMatch(members, member -> hasNamePart(member, name)))
                .or(() -> onlyMatch(members, member -> isNearMiss(member, name)));
    }

    /** The single member satisfying the test, or empty if none or more than one does. */
    private static Optional<User> onlyMatch(List<User> members, java.util.function.Predicate<User> test) {
        List<User> matches = members.stream().filter(test).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static boolean hasNamePart(User member, String name) {
        for (String part : normalise(member.getName()).split(" ")) {
            if (part.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Close enough on the whole name, or on any one part of it. */
    private static boolean isNearMiss(User member, String name) {
        String memberName = normalise(member.getName());
        if (isWithinTypoDistance(memberName, name)) {
            return true;
        }
        for (String part : memberName.split(" ")) {
            if (isWithinTypoDistance(part, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithinTypoDistance(String candidate, String name) {
        int allowance = Math.min(candidate.length(), name.length()) <= SHORT_NAME_LENGTH ? 1 : 2;
        /*
         * A length gap wider than the allowance cannot come back under it, and skipping those
         * saves computing a distance matrix for every member against every name.
         */
        if (Math.abs(candidate.length() - name.length()) > allowance) {
            return false;
        }
        return editDistance(candidate, name) <= allowance;
    }

    /**
     * Levenshtein distance, two rows at a time.
     * <p>
     * Written out rather than pulled in: it is fifteen lines, and the alternative is a dependency
     * for one function in a build that already keeps its dependency list short on purpose.
     */
    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }

        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitution = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + substitution);
            }
            int[] finished = previous;
            previous = current;
            current = finished;
        }

        return previous[right.length()];
    }

    /**
     * Lower case, no punctuation, single spaces. Case folding uses the root locale so that the
     * result does not depend on where the server happens to be running.
     */
    private static String normalise(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll(NOT_NAME_CHARACTER, " ")
                        .replaceAll("\\s+", " ")
                        .trim();
    }
}
