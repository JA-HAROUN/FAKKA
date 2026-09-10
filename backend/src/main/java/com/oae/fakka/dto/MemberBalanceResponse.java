package com.oae.fakka.dto;

import com.oae.fakka.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One member standing in one group, broken into the four things that produce it (FR-11, FR-31).
 * <p>
 * The parts are returned, not just the net: "you are owed 350" answers a different question from
 * "you paid 900, consumed 550, and have been handed 0 since", and a group dashboard that shows
 * only the net cannot explain itself when somebody disagrees with it.
 * <p>
 * The arithmetic is checkable from the fields, which is the point of listing them:
 * <pre>
 *   net = (paid + settledOut) - (owed + settledIn)
 * </pre>
 * Settlements are separate from expenses rather than folded into {@code paid} and {@code owed},
 * because a member who has been paid back has not consumed anything extra, and a breakdown that
 * said so would be a lie that happened to add up.
 * <p>
 * All amounts are piastres, matching the rest of the API: 35000 is 350.00 EGP. Every member of
 * the group appears, including anyone with no activity at all, who reads zeros -- absent and
 * settled are different answers, and BR-5 only balances if nobody is missing.
 */
@Schema(name = "MemberBalance", description = "What one member paid, owes, settled, and nets")
public record MemberBalanceResponse(

        @Schema(description = "The member user id", example = "1")
        Long userId,

        @Schema(description = "Display name", example = "Ahmed Ragy")
        String name,

        @Schema(description = "Profile image URL, null if unset")
        String profileImageUrl,

        @Schema(description = "Total this member paid as payer of expenses, in piastres",
                example = "90000")
        long paid,

        @Schema(description = "Total this member owes as a participant in expenses, in piastres",
                example = "55000")
        long owed,

        @Schema(description = "Total this member has handed over in paid settlements, in piastres",
                example = "0")
        long settledOut,

        @Schema(description = "Total this member has received in paid settlements, in piastres",
                example = "0")
        long settledIn,

        @Schema(description = "(paid + settledOut) - (owed + settledIn), per BR-4: positive means "
                + "the group owes them", example = "35000")
        long net,

        @Schema(description = "Sign of net, for the same indicator the dashboard uses")
        BalanceStatus status
) {

    public static MemberBalanceResponse of(
            User member, long paid, long owed, long settledOut, long settledIn) {

        long net = (paid + settledOut) - (owed + settledIn);
        return new MemberBalanceResponse(
                member.getId(),
                member.getName(),
                member.getProfileImageUrl(),
                paid,
                owed,
                settledOut,
                settledIn,
                net,
                BalanceStatus.of(net));
    }
}
