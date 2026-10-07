package com.ledgersystem.demo.client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;
@Service
@Profile("!prod") 
@Slf4j
public class MockStripeClient implements StripeClient {
    @Override
    public boolean sendMoney(UUID accountId, BigDecimal amount, String idempotencyKey) {
        log.info("[MOCK STRIPE] Processing transfer of {} with key {}", amount, idempotencyKey);
        simulateNetworkDelay();
        if (idempotencyKey.startsWith("TIMEOUT_")) {
            log.warn("[MOCK STRIPE] Simulating network timeout/crash!");
            throw new RuntimeException("Stripe API Timeout");
        }
        if (idempotencyKey.startsWith("REJECT_")) {
            log.warn("[MOCK STRIPE] Transfer rejected by Stripe.");
            return false;
        }
        log.info("[MOCK STRIPE] Transfer succeeded.");
        return true;
    }
    @Override
    public boolean verifyTransactionStatus(String idempotencyKey) {
        log.info("[MOCK STRIPE] Sweeper verifying status for key {}", idempotencyKey);
        if (idempotencyKey.startsWith("TIMEOUT_SUCCESS_")) {
            return true;
        }
        return false;
    }
    private void simulateNetworkDelay() {
        try {
            Thread.sleep(500); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
