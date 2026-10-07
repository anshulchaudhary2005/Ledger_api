package com.ledgersystem.demo.client;
import java.math.BigDecimal;
import java.util.UUID;
public interface StripeClient {
    boolean sendMoney(UUID accountId, BigDecimal amount, String idempotencyKey);
    boolean verifyTransactionStatus(String idempotencyKey);
}
