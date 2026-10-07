# Immutable Ledger API

A robust, production-ready financial ledger built with Java 21 and Spring Boot. This system implements a strict double-entry accounting ledger backed by PostgreSQL, designed to handle high-concurrency financial transactions without deadlocks or race conditions.

## Features

- **Double-Entry Accounting:** Every transaction creates balanced Debit and Credit `LedgerEntry` records. Money is never created or destroyed.
- **Deadlock Prevention:** Implements strict row-level pessimistic locking (`SELECT ... FOR UPDATE`) with alphanumerically sorted UUIDs to completely eliminate database deadlocks under high concurrent load.
- **Idempotency:** Protects against network retries and duplicate requests. If a client sends the same `idempotencyKey` twice, the database's `UNIQUE` constraint strictly blocks double-spending.
- **Saga Pattern:** Handles external real-world withdrawals (e.g., to Stripe) using a Saga flow. Includes local reservations, compensation logic for rejections, and a background `@Scheduled` Sweeper to recover stuck transactions caused by network timeouts.
- **Flyway Migrations:** Automatically tracks and manages database schema changes on startup.

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running.
- (Optional) Java 21 and Maven if you wish to run it locally without Docker.

## How to Start the Application

The entire infrastructure (PostgreSQL database and Spring Boot Java API) is fully containerized.

1. Open your terminal in the root folder of this project.
2. Run the following command:
   ```bash
   docker-compose up -d --build
   ```
3. The API will be available at `http://localhost:8080`. (It takes about 15-30 seconds to fully boot up).

*On startup, the system automatically runs Flyway migrations to create the database tables and runs a `DataSeeder` to create two test accounts:*
- **Alice:** `11111111-1111-1111-1111-111111111111` (Balance: $1000)
- **Bob:** `22222222-2222-2222-2222-222222222222` (Balance: $0)

## How to Test Internal Transfers

Internal transfers move money instantly between two accounts inside our database using atomic database transactions.

### 1. Send a Successful Transfer
Send this JSON via POST to `http://localhost:8080/api/v1/transfers`:

```json
{
  "fromAccountId": "11111111-1111-1111-1111-111111111111",
  "toAccountId": "22222222-2222-2222-2222-222222222222",
  "amount": 50.00,
  "idempotencyKey": "unique_transfer_001"
}
```

### 2. Verify the Database Balances
To check that the money moved, connect to the PostgreSQL database container:
```bash
docker exec ledger-db psql -U ledger_user -d ledger_db -c "SELECT user_id, balance FROM accounts;"
```

## How to Test External Withdrawals (Saga Pattern)

Withdrawals mock an external API call to Stripe. The `MockStripeClient` reads your `idempotencyKey` to decide whether to simulate a success, failure, or network crash.

*Note: Assume the withdrawal endpoint is `POST /api/v1/withdrawals`.*

### Scenario A: Normal Success
Use a standard idempotency key.
```json
{
  "accountId": "11111111-1111-1111-1111-111111111111",
  "amount": 10.00,
  "idempotencyKey": "withdraw_001"
}
```
**Result:** Money is deducted locally, Stripe succeeds, status changes to `COMMITTED`.

### Scenario B: Stripe Rejection (Fraud)
Prefix the key with `REJECT_`.
```json
{
  "accountId": "11111111-1111-1111-1111-111111111111",
  "amount": 10.00,
  "idempotencyKey": "REJECT_001"
}
```
**Result:** Money is deducted, but Stripe rejects the transfer. The `Compensation` logic kicks in, refunding the money back to the account. Status changes to `FAILED`.

### Scenario C: Network Timeout
Prefix the key with `TIMEOUT_`.
```json
{
  "accountId": "11111111-1111-1111-1111-111111111111",
  "amount": 10.00,
  "idempotencyKey": "TIMEOUT_001"
}
```
**Result:** MockStripe throws a fake network Exception. The Java API catches it but intentionally does not roll back. The transaction is stuck in `PENDING_WITHDRAWAL`. 
**The Fix:** Wait 5 minutes. The `SagaSweeper.java` background job will automatically wake up, detect the stuck transaction, verify it with Stripe, and fix it!
