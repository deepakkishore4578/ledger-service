# Double-Entry Ledger Service

A transactional financial ledger backend built with Java and Spring Boot, designed to explore the correctness challenges involved in moving money between accounts.

The project focuses on **transaction integrity, double-entry bookkeeping, idempotent APIs, concurrency control, failure recovery, and reconciliation**.

Rather than treating a transfer as a simple balance update, the service models a transfer as a business operation that must preserve financial invariants even when requests fail or are retried.

## Table of Contents

- [Overview](#overview)
- [Features and Implementation Status](#features-and-implementation-status)
- [Architecture](#architecture)
- [Financial Invariants](#financial-invariants)
- [Double-Entry Ledger](#double-entry-ledger)
- [Transaction Management and Rollback](#transaction-management-and-rollback)
- [Idempotency and Retry Safety](#idempotency-and-retry-safety)
- [Concurrency and Race Conditions](#concurrency-and-race-conditions)
- [Reconciliation](#reconciliation)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [REST API](#rest-api)
- [Testing Strategy](#testing-strategy)
- [Engineering Challenges](#engineering-challenges)
- [Roadmap](#roadmap)

## Overview

The Double-Entry Ledger Service provides REST APIs for managing accounts and transferring money between them.

Every successful transfer updates the source and destination balances and records corresponding debit and credit ledger entries within a database transaction.

The project explores a central financial-systems problem:

**How do we ensure that a transfer is processed correctly exactly once from the client's perspective, without leaving inconsistent balances or incomplete ledger records?**

This requires more than a successful API response. It requires explicit business invariants, transactional boundaries, retry handling, database constraints, and concurrency control.

## Features and Implementation Status

| Feature | Status |
|---|---|
| Account creation and retrieval | Implemented |
| Balance retrieval | Implemented |
| Transfers between accounts | Implemented |
| Double-entry ledger recording | Implemented |
| Validation of transfer amounts and currencies | Implemented |
| Transactional rollback on failure | Implemented and tested |
| Idempotency-key lookup | Implemented |
| Duplicate-request protection in sequential requests | Implemented and tested |
| Rejection of key reuse with different transfer details | Implemented and tested |
| Database-level protection against concurrent duplicate requests | To be implemented and tested |
| Concurrency-safe account balance updates | To be implemented and tested |
| Automated ledger reconciliation | Planned |
| MySQL production configuration and migration workflow | Planned |
| Docker and CI pipeline | Planned |

The status table distinguishes currently tested behavior from engineering work that remains.

## Architecture

The application follows a layered architecture.

```text
                  Client
                    |
                    v
             REST Controllers
                    |
                    v
              Service Layer
                    |
          +---------+----------+
          |                    |
          v                    v
   Account Repository    Transfer Repository
                               |
                               v
                      Ledger Entry Repository
          |                    |
          +---------+----------+
                    |
                    v
                Database
```

### Main components

- **Account:** Represents an account, its type, currency, balance, and negative-balance policy.
- **Transfer:** Represents a money movement between two accounts, including its amount, status, creation time, and idempotency key.
- **LedgerEntry:** Records the debit or credit associated with a transfer.
- **Repositories:** Provide persistence and query operations using Spring Data JPA.
- **Services:** Enforce business rules and coordinate account updates, transfer persistence, and ledger recording.
- **Controllers:** Expose the REST API.
- **Exception handling:** Converts application and validation errors into HTTP responses.

## Financial Invariants

The service is designed around the following invariants.

### 1. Valid transfer amount

A transfer must have a positive amount:

\[
\text{amountPaise} > 0
\]

### 2. Distinct accounts

The source and destination accounts must differ.

### 3. Currency consistency

Both accounts must use the same currency.

### 4. Sufficient funds

Unless an account explicitly allows a negative balance, a debit must not exceed its available balance.

### 5. Atomic transfer

A successful transfer must update both account balances and persist its transfer and ledger records together.

### 6. Balanced ledger entries

For every ordinary transfer, the debit and credit entries must have equal amounts:

\[
\text{Total Debits} = \text{Total Credits}
\]

These are application-level and transaction-level requirements. Concurrent requests still require appropriate database coordination to preserve them under simultaneous execution.

## Double-Entry Ledger

The service records two ledger entries for a successful transfer.

For a transfer of ₹5 from Account A to Account B:

| Account | Direction | Amount |
|---|---|---:|
| Account A | DEBIT | ₹5 |
| Account B | CREDIT | ₹5 |

The amount is stored in paise to avoid floating-point arithmetic for monetary values.

For example:

- ₹1 = 100 paise
- ₹5 = 500 paise
- ₹20 = 2,000 paise

Each ledger entry references the transfer and its associated account.

The ledger records the financial movement independently of the account balance fields. This makes it possible to build historical queries and reconciliation checks.

## Transaction Management and Rollback

The transfer service uses Spring's `@Transactional` annotation to group related persistence operations into a database transaction.

A transfer involves:

1. Loading the source and destination accounts.
2. Validating the transfer.
3. Updating the account balances.
4. Persisting the transfer record.
5. Persisting the debit entry.
6. Persisting the credit entry.

If an unchecked exception occurs during the transactional operation and escapes the transaction boundary, Spring's default rollback behavior rolls back the transaction.

### Failure simulation

The rollback test uses Mockito to simulate a ledger persistence failure.

The test allows the first ledger-entry save to proceed, then throws an exception during the second save.

After the transaction fails, the test verifies that:

- The source balance remains unchanged.
- The destination balance remains unchanged.
- No transfer record is committed.
- No ledger entries are committed.

This tests an important failure scenario: a transfer must not leave partially committed financial state.

## Idempotency and Retry Safety

Network failures can create ambiguity.

A client may submit a transfer successfully but lose the response. If it retries the request, the server must distinguish a retry from a new transfer.

The service accepts an `Idempotency-Key` HTTP header and stores the key with the transfer.

### Current behavior

- A new key allows a new transfer.
- Repeating the same request with the same key returns the existing transfer.
- Reusing a key with different accounts or a different amount is rejected.

### Example

First request:

```http
POST /api/v1/transfers
Idempotency-Key: payment-123
Content-Type: application/json
```

```json
{
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amountPaise": 500
}
```

If the same request is repeated with `payment-123`, the service returns the existing transfer rather than applying the balance changes again.

### Database uniqueness

The transfer entity declares the idempotency key as unique. This is an important database safeguard, but a lookup followed by an insert is not, by itself, concurrency-safe.

Two simultaneous requests may both observe that a key does not exist before either inserts it. The database constraint can reject a duplicate insert, but the application must also handle that conflict correctly.

Concurrent idempotency handling is a planned improvement.

## Concurrency and Race Conditions

Concurrency is one of the key engineering challenges for a financial transfer service.

Consider an account with a balance of 1,000 paise. Two requests attempt to debit 800 paise at almost the same time.

Without suitable concurrency control, both requests might read the original balance before either update becomes visible. If both then proceed independently, the service could permit more money to be transferred than the account holds.

This is a classic race condition.

### Concurrency scenarios to address

**1. Concurrent debits**

Multiple requests attempt to spend the same account balance.

**2. Concurrent updates**

Two transactions update the same account, potentially causing lost updates or stale reads.

**3. Duplicate requests**

Two requests arrive simultaneously with the same idempotency key.

**4. Opposite-direction transfers**

One request transfers from Account A to B while another transfers from B to A, potentially creating deadlocks if locks are acquired inconsistently.

### Planned concurrency-control strategies

**Pessimistic locking**

Use database row locks, such as JPA's `PESSIMISTIC_WRITE`, when loading accounts for a transfer. Competing transactions must wait for the relevant locks to be released.

**Optimistic locking**

Add a JPA `@Version` field to detect conflicting updates. A transaction that updates stale data can fail and may need a controlled retry.

**Consistent lock ordering**

Acquire account locks in a consistent order, such as ascending account ID, to reduce deadlock risk when transfers involve the same accounts in opposite directions.

**Database uniqueness and conflict handling**

Retain a unique constraint on the idempotency key and explicitly handle duplicate-key conflicts arising from concurrent requests.

**Concurrency tests**

Use separate transactions and simultaneous requests to verify that conflicting operations cannot violate the balance or idempotency rules.

These mechanisms have not yet been implemented and verified as a complete concurrency solution.

## Reconciliation

A reconciliation process compares the persisted account balances against the balances implied by ledger entries.

For each account, the expected balance can be derived from its initial balance and its ledger movements, using the appropriate sign convention for debits and credits.

A reconciliation job could:

1. Read account records and ledger entries.
2. Calculate the expected balance for each account.
3. Compare the calculated balance with the stored account balance.
4. Identify discrepancies.
5. Report inconsistencies for investigation.

Reconciliation is particularly useful for detecting bugs, incomplete data migrations, and unexpected inconsistencies.

**Status:** Planned; the automated reconciliation job is not currently implemented.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Backend programming language |
| Spring Boot | Application framework |
| Spring Web | REST API development |
| Spring Data JPA | Persistence abstraction |
| Hibernate | Object-relational mapping |
| H2 | Local in-memory database |
| MySQL Connector/J | MySQL connectivity |
| Gradle | Build automation |
| JUnit 5 | Automated testing |
| Mockito | Test doubles and failure simulation |

## Project Structure

```text
src/
├── main/
│   ├── java/com/deepak/ledger/
│   │   ├── controller/
│   │   │   ├── AccountController.java
│   │   │   └── TransferController.java
│   │   ├── entity/
│   │   │   ├── Account.java
│   │   │   ├── EntryDirection.java
│   │   │   ├── LedgerEntry.java
│   │   │   ├── Transfer.java
│   │   │   └── TransferStatus.java
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── ResourceNotFoundException.java
│   │   ├── repository/
│   │   │   ├── AccountRepository.java
│   │   │   ├── LedgerEntryRepository.java
│   │   │   └── TransferRepository.java
│   │   ├── service/
│   │   │   ├── AccountService.java
│   │   │   └── TransferService.java
│   │   └── LedgerServiceApplication.java
│   └── resources/
│       └── application.properties
└── test/
    └── java/com/deepak/ledger/
        ├── TransferRollbackTest.java
        └── TransferServiceTest.java
```

The tree illustrates the main application components. Test paths should be adjusted if additional test classes are located in other packages.

## Getting Started

### Prerequisites

- Java 21 JDK
- Git
- IntelliJ IDEA or another Java IDE (optional)

### Clone the repository

```bash
git clone https://github.com/deepakkkishore4578/ledger-service.git
cd ledger-service
```

Verify that Java is installed:

```bash
java -version
```

### Run the tests

On macOS or Linux:

```bash
./gradlew clean test
```

### Start the application

```bash
./gradlew bootRun
```

The application normally starts on port `8080`, unless configured otherwise.

The local configuration uses an H2 in-memory database. Data stored in an in-memory database is generally lost when the database shuts down.

## REST API

### Account endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/accounts` | Create an account |
| GET | `/api/v1/accounts` | List accounts |
| GET | `/api/v1/accounts/{id}` | Retrieve an account |
| GET | `/api/v1/accounts/{id}/balance` | Retrieve an account balance |

### Transfer endpoint

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/transfers` | Create a transfer |

Example transfer request:

```http
POST /api/v1/transfers
Idempotency-Key: payment-123
Content-Type: application/json
```

```json
{
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amountPaise": 500
}
```

The request transfers ₹5 from account 1 to account 2.

## Testing Strategy

Run the complete test suite:

```bash
./gradlew clean test
```

The current suite covers:

- Successful transfers and balance updates.
- Debit and credit ledger entries.
- Insufficient-funds rejection.
- Transaction rollback after a simulated ledger failure.
- Repeated requests with the same idempotency key.
- Rejection of the same key with a different amount.
- Separate transfers with different idempotency keys.

### Planned advanced tests

- Two concurrent debits against the same account.
- Concurrent requests using the same idempotency key.
- Transfers in opposite directions between the same accounts.
- Lock acquisition and deadlock handling.
- Optimistic-lock conflicts and controlled retries.
- Reconciliation of stored balances against ledger movements.
- Integration tests against MySQL.

## Engineering Challenges

This project explores several backend engineering topics:

- Transaction boundaries and rollback semantics.
- Financial invariants and double-entry bookkeeping.
- Idempotent API design.
- Database uniqueness constraints.
- Race conditions and concurrent state changes.
- Pessimistic and optimistic locking.
- Deadlock prevention and recovery.
- Integration testing and failure simulation.
- Reconciliation and data consistency.

## Roadmap

### Phase 1 — Core ledger

- Account management.
- Transfer processing.
- Debit and credit ledger entries.
- Transaction rollback tests.

### Phase 2 — Idempotency

- Idempotency-key persistence.
- Sequential retry protection.
- Rejection of conflicting key reuse.
- Concurrent duplicate-request handling.

### Phase 3 — Concurrency safety

- Database locking or optimistic concurrency control.
- Safe concurrent debits.
- Consistent lock ordering.
- Concurrent integration tests.

### Phase 4 — Reconciliation

- Ledger-based balance calculations.
- Reconciliation job.
- Discrepancy reporting.
- Tests for inconsistent balances.

### Phase 5 — Production readiness

- MySQL configuration.
- Database migrations.
- Docker.
- CI pipeline.
- API documentation.
- Authentication and authorization.
- Monitoring, structured logging, and audit trails.

## Project Status

The project currently implements account management, transfers, double-entry ledger recording, transactional rollback, and basic idempotency behavior.

The next major engineering milestones are **concurrency-safe transfers, race-condition testing, and ledger reconciliation**.

The objective is to evolve the service from a basic transactional backend into a more robust financial-system design exercise.

## License

No license has been specified. Add an appropriate license before distributing or reusing the project publicly.
