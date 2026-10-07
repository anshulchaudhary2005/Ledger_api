package com.ledgersystem.demo.scheduler;
import com.ledgersystem.demo.client.MockStripeClient;
import com.ledgersystem.demo.entity.LedgerEntry;
import com.ledgersystem.demo.entity.Transaction;
import com.ledgersystem.demo.repository.LedgerEntryRepository;
import com.ledgersystem.demo.repository.TransactionRepository;
import com.ledgersystem.demo.service.WithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaSweeper {
    private final TransactionRepository transactionRepository;
    private final WithdrawalService withdrawalService;
    private final MockStripeClient stripeClient;
    private final LedgerEntryRepository ledgerEntryRepository;
    @Scheduled(fixedDelay = 60000)
    public void recoverStuckTransactions() {
        LocalDateTime fiveMinsAgo = LocalDateTime.now().minusMinutes(5);
        List<Transaction> stuckTxs = transactionRepository
                .findByStatusAndCreatedAtBefore("PENDING_WITHDRAWAL", fiveMinsAgo);
        for (Transaction tx : stuckTxs) {
            log.info("Recovering stuck transaction: {}", tx.getId());
            try {
                boolean didStripeProcessIt = stripeClient.verifyTransactionStatus(tx.getIdempotencyKey());
                if (didStripeProcessIt) {
                    withdrawalService.markAsCommitted(tx.getId());
                    log.info("Stripe confirmed success. Marked COMMITTED.");
                } else {
                    List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
                    LedgerEntry originalDebit = entries.stream()
                            .filter(entry -> entry.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Could not find debit entry to compensate"));
                    withdrawalService.executeCompensation(
                            tx.getId(),
                            originalDebit.getAccount().getId(),
                            originalDebit.getAmount().abs()
                    );
                    log.info("Stripe confirmed failure. Compensation executed.");
                }
            } catch (Exception e) {
                log.error("Sweeper failed to verify status with Stripe. Will retry next run.", e);
            }
        }
    }
}
