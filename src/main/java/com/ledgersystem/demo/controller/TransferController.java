package com.ledgersystem.demo.controller;
import com.ledgersystem.demo.dto.TransferRequest;
import com.ledgersystem.demo.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {
    private final TransferService transferService;
    @PostMapping
    public ResponseEntity<?> executeTransfer(@Valid @RequestBody TransferRequest request) {
        log.info("Received transfer request: {}", request);
        try {
            transferService.executeTransfer(
                    request.getFromAccountId(),
                    request.getToAccountId(),
                    request.getAmount(),
                    request.getIdempotencyKey()
            );
            return ResponseEntity.ok().body(Map.of(
                    "status", "SUCCESS",
                    "message", "Transfer completed successfully",
                    "idempotencyKey", request.getIdempotencyKey()
            ));
        } catch (IllegalStateException e) {
            log.warn("Transfer failed due to business rule: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Transfer failed due to invalid arguments: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Unexpected error during transfer", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "error", "An internal error occurred while processing the transfer"
            ));
        }
    }
}
