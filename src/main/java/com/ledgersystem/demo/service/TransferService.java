package com.ledgersystem.demo.service;
import com.ledgersystem.demo.entity.Account;
import com.ledgersystem.demo.entity.LedgerEntry;
import com.ledgersystem.demo.entity.Transaction;
import com.ledgersystem.demo.repository.AccountRepository;
import com.ledgersystem.demo.repository.LedgerEntryRepository;
import com.ledgersystem.demo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    @Transactional
    public void executeTransfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey) {
        log.info("Initiating transfer of {} from {} to {} with key {}", amount, fromAccountId, toAccountId, idempotencyKey);
        if (transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.warn("Idempotency key {} already exists. Skipping processing to prevent double charge.", idempotencyKey);
            return;
        }
        List<UUID> sortedIds = Stream.of(fromAccountId, toAccountId)
                .sorted()
                .toList();
        List<Account> accounts = accountRepository.findByIdsForUpdate(sortedIds);
        if (accounts.size() != 2) {
            throw new IllegalArgumentException("One or both accounts do not exist.");
        }
        Account fromAccount = accounts.stream().filter(a -> a.getId().equals(fromAccountId)).findFirst().orElseThrow();
        Account toAccount = accounts.stream().filter(a -> a.getId().equals(toAccountId)).findFirst().orElseThrow();
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds in account: " + fromAccountId);
        }
        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .status("COMMITTED")
                .build();
        transactionRepository.save(tx);
        LedgerEntry debit = LedgerEntry.builder()
                .transaction(tx)
                .account(fromAccount)
                .amount(amount.negate()) 
                .build();
        LedgerEntry credit = LedgerEntry.builder()
                .transaction(tx)
                .account(toAccount)
                .amount(amount) 
                .build();
        ledgerEntryRepository.saveAll(List.of(debit, credit));
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        log.info("Transfer complete. Transaction ID: {}", tx.getId());
    }
}
