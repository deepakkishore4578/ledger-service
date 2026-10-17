
package com.deepak.ledger.service;

import com.deepak.ledger.entity.Account;
import com.deepak.ledger.entity.EntryDirection;
import com.deepak.ledger.entity.LedgerEntry;
import com.deepak.ledger.entity.Transfer;
import com.deepak.ledger.repository.AccountRepository;
import com.deepak.ledger.repository.LedgerEntryRepository;
import com.deepak.ledger.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public Transfer transfer(
            Long sourceAccountId,
            Long destinationAccountId,
            long amountPaise,
            String idempotencyKey
    ) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key must not be blank"
            );
        }


        var existingTransfer =
                transferRepository.findByIdempotencyKey(idempotencyKey);

        if (existingTransfer.isPresent()) {
            Transfer previous = existingTransfer.get();

            boolean sameRequest =
                    previous.getSourceAccount().getId().equals(sourceAccountId)
                            && previous.getDestinationAccount().getId().equals(destinationAccountId)
                            && previous.getAmountPaise() == amountPaise;

            if (!sameRequest) {
                throw new IllegalArgumentException(
                        "Idempotency key was already used for a different transfer"
                );
            }

            return previous;
        }


        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must differ"
            );
        }

        if (amountPaise <= 0) {
            throw new IllegalArgumentException(
                    "Transfer amount must be positive"
            );
        }

        Account source = accountRepository.findById(sourceAccountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Source account not found: " + sourceAccountId
                        )
                );

        Account destination = accountRepository.findById(destinationAccountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Destination account not found: " + destinationAccountId
                        )
                );

        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new IllegalArgumentException(
                    "Account currencies must match"
            );
        }

        // Update both account balances.
        source.debit(amountPaise);
        destination.credit(amountPaise);

        // Create the transfer record.
        Transfer transfer = transferRepository.save(
                new Transfer(source, destination, amountPaise,idempotencyKey)
        );

        // Record the debit side.
        ledgerEntryRepository.save(
                new LedgerEntry(
                        transfer, source, EntryDirection.DEBIT, amountPaise
                )
        );

        // Record the credit side.
        ledgerEntryRepository.save(
                new LedgerEntry(
                        transfer, destination, EntryDirection.CREDIT, amountPaise
                )
        );

        return transfer;
    }
}
