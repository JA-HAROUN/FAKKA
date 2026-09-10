package com.oae.fakka.service;

import com.oae.fakka.dto.SettlementResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Turns a set of balances into the payments that clear them (FR-32, FR-33).
 *
 * <h2>A pure function</h2>
 * No repositories, no entities, no request: it takes a balance map and returns transfers, so it
 * can be tested against hand-written numbers, including the awkward ones a real group would take
 * weeks to produce.
 *
 * <h2>The algorithm</h2>
 * Repeatedly take the largest creditor and the largest debtor and move the smaller of the two
 * amounts between them. Each step zeroes at least one of the pair, so at most one transfer per
 * member minus one is ever produced, and the remainder always lands exactly on zero because the
 * amounts are integers.
 * <p>
 * Both sides are heaps rather than sorted lists, because after a partial transfer the residual
 * has to compete for "largest" again: settling 500 against 300 leaves a debtor of 200 who may no
 * longer be the biggest one. A single pass over two sorted lists would pair up different people
 * and is not what "largest against largest, repeatedly" means.
 *
 * <h2>How minimal is it</h2>
 * This is the standard greedy simplification and it always lands within the n-1 bound, which is
 * what FR-33 asks for. It is not provably the fewest possible transfers in every case: finding
 * that is the partition problem, so an input where subsets happen to cancel exactly can be
 * settled in fewer transfers than greedy finds. Worth knowing before anyone treats the count as
 * optimal, and not worth an exponential search for a bill-splitting app.
 *
 * <h2>Determinism</h2>
 * Ties on amount break on the lower user id, so the same balances always produce the same list
 * in the same order, whatever order the map was built in. A settlement list that reshuffled
 * between two reads of the same data would look like the debts had changed.
 */
@Service
public class DebtSimplificationService {

    /** Largest amount first; the lower id wins a tie, which is what makes the output stable. */
    private static final Comparator<Party> LARGEST_FIRST =
            Comparator.comparingLong(Party::amount).reversed().thenComparing(Party::userId);

    /**
     * The transfers that settle these balances.
     *
     * @param balancesInPiastres user id to balance, positive for owed to them, negative for owed
     *                           by them. Zero balances are ignored, and members not in the map
     *                           are simply not involved.
     * @return one transfer per step, largest first by construction. Empty when everyone is
     *         already settled.
     */
    public List<SettlementResponse> simplify(Map<Long, Long> balancesInPiastres) {
        PriorityQueue<Party> creditors = new PriorityQueue<>(LARGEST_FIRST);
        PriorityQueue<Party> debtors = new PriorityQueue<>(LARGEST_FIRST);

        balancesInPiastres.forEach((userId, balance) -> {
            if (balance > 0) {
                creditors.add(new Party(userId, balance));
            } else if (balance < 0) {
                // Debts are carried as positive magnitudes so both heaps order the same way.
                debtors.add(new Party(userId, Math.negateExact(balance)));
            }
        });

        List<SettlementResponse> settlements = new ArrayList<>();

        /*
         * Stops when either side runs out. For balances that sum to zero -- which is every real
         * group, by BR-5 -- both empty on the same step and nothing is left over. For a map that
         * does not balance, this settles as much as it can and leaves the excess unpaid, which is
         * more useful to a caller than refusing to answer.
         */
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Party creditor = creditors.poll();
            Party debtor = debtors.poll();

            long amount = Math.min(creditor.amount(), debtor.amount());
            settlements.add(new SettlementResponse(debtor.userId(), creditor.userId(), amount));

            long creditRemaining = creditor.amount() - amount;
            long debtRemaining = debtor.amount() - amount;
            if (creditRemaining > 0) {
                creditors.add(new Party(creditor.userId(), creditRemaining));
            }
            if (debtRemaining > 0) {
                debtors.add(new Party(debtor.userId(), debtRemaining));
            }
        }

        return List.copyOf(settlements);
    }

    /** One side of one pending transfer: who, and how much is still outstanding. */
    private record Party(Long userId, long amount) {
    }
}
