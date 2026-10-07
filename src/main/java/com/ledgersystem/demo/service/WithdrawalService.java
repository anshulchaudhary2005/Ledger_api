package com.ledgersystem.demo.service;
import com.ledgersystem.demo.client.MockStripeClient;
import com.ledgersystem.demo.entity.Account;
import com.ledgersystem.demo.entity.LedgerEntry;
import com.ledgersystem.demo.entity.Transaction;
import com.ledgersystem.demo.repository.AccountRepository;
import com.ledgersystem.demo.repository.LedgerEntryRepository;
import com.ledgersystem.demo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final MockStripeClient stripeClient; 
    public void processWithdrawal(UUID accountId, BigDecimal amount, String idempotencyKey) {
        Transaction tx = reserveFundsLocally(accountId, amount, idempotencyKey);
        boolean stripeSuccess = false;
        try {
            stripeSuccess = stripeClient.sendMoney(accountId, amount, idempotencyKey);
        } catch (Exception e) {
            log.error("Stripe API failed or timed out. Transaction {} is stuck in PENDING.", tx.getId());
        }
        if (stripeSuccess) {
            markAsCommitted(tx.getId());
        } else {
            executeCompensation(tx.getId(), accountId, amount);
        }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction reserveFundsLocally(UUID accountId, BigDecimal amount, String idempotencyKey) {
        Account account = accountRepository.findByIdForUpdate(accountId).orElseThrow();
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .status("PENDING_WITHDRAWAL")
                .build();
        transactionRepository.save(tx);
        LedgerEntry debit = LedgerEntry.builder()
                .transaction(tx)
                .account(account)
                .amount(amount.negate()) 
                .build();
        ledgerEntryRepository.save(debit);
        account.setBalance(account.getBalance().subtract(amount));
        return tx;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsCommitted(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId).orElseThrow();
        tx.setStatus("COMMITTED");
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeCompensation(UUID transactionId, UUID accountId, BigDecimal amount) {
        Transaction tx = transactionRepository.findById(transactionId).orElseThrow();
        Account account = accountRepository.findByIdForUpdate(accountId).orElseThrow();
        LedgerEntry credit = LedgerEntry.builder()
                .transaction(tx)
                .account(account)
                .amount(amount) 
                .build();
        ledgerEntryRepository.save(credit);
        account.setBalance(account.getBalance().add(amount));
        tx.setStatus("FAILED");
        log.info("Compensation complete. Refunded {} to account {}", amount, accountId);
    }
}
