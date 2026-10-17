
package com.deepak.ledger.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryDirection direction;

    @Column(nullable = false)
    private long amountPaise;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LedgerEntry() {
    }

    public LedgerEntry(
            Transfer transfer,
            Account account,
            EntryDirection direction,
            long amountPaise
    ) {
        this.transfer = transfer;
        this.account = account;
        this.direction = direction;
        this.amountPaise = amountPaise;
    }

    public Long getId() {
        return id;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public Account getAccount() {
        return account;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
