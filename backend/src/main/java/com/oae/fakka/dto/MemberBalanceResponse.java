package com.oae.fakka.dto;

import com.oae.fakka.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One member standing in one group, broken into its two halves (FR-11, FR-31).
 * <p>
 * Both halves are returned, not just the net: "you are owed 350" answers a different question
 * from "you paid 900 and consumed 550", and a group dashboard that only shows the net cannot
 * explain itself when somebody disagrees with it.
 * <p>
 * All amounts are piastres, matching the rest of the API: 35000 is 350.00 EGP. Every member of
 * the group appears, including anyone with no expenses at all, who reads 0/0/0 -- absent and
 * settled are different answers, and BR-5 only balances if nobody is missing.
 */
@Schema(name = "MemberBalance", description = "What one member paid, owes, and is owed on net")
public record MemberBalanceResponse(

        @Schema(description = "The member user id", example = "1")
        Long userId,

        @Schema(description = "Display name", example = "Ahmed Ragy")
        String name,

        @Schema(description = "Profile image URL, null if unset")
        String profileImageUrl,

        @Schema(description = "Total this member paid as payer, in piastres", example = "90000")
        long paid,

        @Schema(description = "Total this member owes as a participant, in piastres", example = "55000")
        long owed,

        @Schema(description = "paid minus owed (BR-4): positive means the group owes them",
                example = "35000")
        long net,

        @Schema(description = "Sign of net, for the same indicator the dashboard uses")
        BalanceStatus status
) {

    public static MemberBalanceResponse of(User member, long paid, long owed) {
        long net = paid - owed;
        return new MemberBalanceResponse(
                member.getId(),
                member.getName(),
                member.getProfileImageUrl(),
                paid,
                owed,
                net,
                BalanceStatus.of(net));
    }
}
