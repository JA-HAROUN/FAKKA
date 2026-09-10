package com.oae.fakka.dto;

import com.oae.fakka.entity.Group;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sign rule behind the dashboard indicator (FR-5).
 * <p>
 * Worth testing on its own even though the balance engine is not written yet: this is the half
 * of the contract that is final, and Phase 5 has to be able to trust it. The boundary at zero is
 * the whole point -- settled is exactly zero, not near zero.
 */
class BalanceStatusTest {

    @ParameterizedTest
    @CsvSource({
            "1, POSITIVE",
            "35000, POSITIVE",
            "9223372036854775807, POSITIVE",
            "-1, NEGATIVE",
            "-12550, NEGATIVE",
            "-9223372036854775808, NEGATIVE",
            "0, SETTLED",
    })
    void derivesStatusFromTheSignOfTheBalance(long balanceInPiastres, BalanceStatus expected) {
        assertThat(BalanceStatus.of(balanceInPiastres)).isEqualTo(expected);
    }

    /**
     * One piastre either side of zero must not read as settled. In minor units this is an exact
     * comparison, which is the reason balances are integers rather than a decimal EGP amount.
     */
    @Test
    void onePiastreIsNotSettled() {
        assertThat(BalanceStatus.of(1L)).isEqualTo(BalanceStatus.POSITIVE);
        assertThat(BalanceStatus.of(-1L)).isEqualTo(BalanceStatus.NEGATIVE);
        assertThat(BalanceStatus.of(0L)).isEqualTo(BalanceStatus.SETTLED);
    }

    /** A card cannot be built with a status that contradicts its balance, because it derives it. */
    @Test
    void groupCardDerivesItsOwnStatus() {
        Group group = Group.builder()
                .id(10L)
                .name("Dinner")
                .imageUrl("https://img.example.com/dinner.jpg")
                .createdBy(1L)
                .build();

        assertThat(GroupCardResponse.of(group, 3, 35_000L).status()).isEqualTo(BalanceStatus.POSITIVE);
        assertThat(GroupCardResponse.of(group, 3, -35_000L).status()).isEqualTo(BalanceStatus.NEGATIVE);
        assertThat(GroupCardResponse.of(group, 3, 0L).status()).isEqualTo(BalanceStatus.SETTLED);

        GroupCardResponse card = GroupCardResponse.of(group, 3, -35_000L);
        assertThat(card.groupId()).isEqualTo(10L);
        assertThat(card.name()).isEqualTo("Dinner");
        assertThat(card.imageUrl()).isEqualTo("https://img.example.com/dinner.jpg");
        assertThat(card.memberCount()).isEqualTo(3);
        assertThat(card.userBalance()).isEqualTo(-35_000L);
    }
}
