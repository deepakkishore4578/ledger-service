
package com.deepak.ledger.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private Account destinationAccount;

    @Column(nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.COMPLETED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Transfer() {
    }

    public Transfer(
            Account sourceAccount,
            Account destinationAccount,
            long amountPaise,
            String idempotencyKey
    ) {
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amountPaise = amountPaise;
        this.idempotencyKey=idempotencyKey;
    }

    public Long getId() {
        return id;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Account getDestinationAccount() {
        return destinationAccount;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
}
