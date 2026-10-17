
package com.deepak.ledger.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false)
    private long balancePaise = 0L;

    @Column(nullable = false)
    private boolean allowNegative = false;

    protected Account() {
        // Required by JPA
    }

    public Account(String name, AccountType type) {
        this.name = name;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalancePaise() {
        return balancePaise;
    }

    public boolean isAllowNegative() {
        return allowNegative;
    }

    public void debit(long amountPaise) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }

        if (!allowNegative && amountPaise > balancePaise) {
            throw new IllegalStateException(
                    "Insufficient funds"
            );
        }

        balancePaise -= amountPaise;
    }

    public void credit(long amountPaise) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }

        balancePaise = Math.addExact(balancePaise, amountPaise);
    }

}
