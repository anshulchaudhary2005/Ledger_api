package com.ledgersystem.demo.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;
@Data
public class TransferRequest {
    @NotNull(message = "Sender account ID is required")
    private UUID fromAccountId;
    @NotNull(message = "Receiver account ID is required")
    private UUID toAccountId;
    @NotNull(message = "Transfer amount is required")
    @DecimalMin(value = "0.01", message = "Transfer amount must be strictly positive")
    private BigDecimal amount;
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
