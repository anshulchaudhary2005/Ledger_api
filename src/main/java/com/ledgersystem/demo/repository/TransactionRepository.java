package com.ledgersystem.demo.repository;
import com.ledgersystem.demo.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    List<Transaction> findByStatusAndCreatedAtBefore(String pendingWithdrawal, LocalDateTime fiveMinsAgo);
}
