package com.ledgersystem.demo.service;

import com.ledgersystem.demo.entity.Account;
import com.ledgersystem.demo.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class TransferServiceConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void testConcurrentTransfers_ShouldNotDoubleSpend() throws InterruptedException {
        // 1. Setup: Alice has $100, Bob has $0
        Account alice = accountRepository.save(Account.builder().userId("Alice").balance(new BigDecimal("100.00")).currency("USD").build());
        Account bob = accountRepository.save(Account.builder().userId("Bob").balance(new BigDecimal("0.00")).currency("USD").build());

        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        // 2. Fire 10 simultaneous threads, each trying to transfer $50 from Alice to Bob
        for (int i = 0; i < numberOfThreads; i++) {
            String idempotencyKey = UUID.randomUUID().toString(); // Unique key per thread so they don't get blocked by the idempotency check
            executorService.execute(() -> {
                try {
                    transferService.executeTransfer(alice.getId(), bob.getId(), new BigDecimal("50.00"), idempotencyKey);
                } catch (IllegalStateException e) {
                    // We EXPECT 8 of these threads to fail with "Insufficient funds"
                    System.out.println("Thread failed gracefully: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to finish
        latch.await();

        // 3. The Truth: If the locks worked, exactly 2 transfers succeeded ($100 total).
        Account updatedAlice = accountRepository.findById(alice.getId()).orElseThrow();
        Account updatedBob = accountRepository.findById(bob.getId()).orElseThrow();

        assertEquals(0, updatedAlice.getBalance().compareTo(BigDecimal.ZERO), "Alice's balance should be exactly 0");
        assertEquals(0, updatedBob.getBalance().compareTo(new BigDecimal("100.00")), "Bob's balance should be exactly 100");
    }
}