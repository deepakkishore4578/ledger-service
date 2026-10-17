
package com.deepak.ledger.service;

import com.deepak.ledger.entity.Account;
import com.deepak.ledger.entity.AccountType;
import com.deepak.ledger.entity.EntryDirection;
import com.deepak.ledger.entity.LedgerEntry;
import com.deepak.ledger.entity.Transfer;
import com.deepak.ledger.repository.AccountRepository;
import com.deepak.ledger.repository.LedgerEntryRepository;
import com.deepak.ledger.repository.TransferRepository;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TransferServiceTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void successfulTransferUpdatesBalancesAndCreatesTwoEntries() {
        // Arrange: create and fund the source account.
        Account source = new Account("Source", AccountType.BORROWER);
        source.credit(1000);
        source = accountRepository.save(source);

        Account destination = new Account(
                "Destination", AccountType.BORROWER
        );
        destination = accountRepository.save(destination);

        // Act: transfer 500 paise (₹5).
        Transfer transfer = transferService.transfer(
                source.getId(),
                destination.getId(),
                500,
                "test-transfer-1"
        );

        // Reload the accounts to verify persisted balances.
        Account updatedSource = accountRepository
                .findById(source.getId())
                .orElseThrow();

        Account updatedDestination = accountRepository
                .findById(destination.getId())
                .orElseThrow();

        assertEquals(500, updatedSource.getBalancePaise());
        assertEquals(500, updatedDestination.getBalancePaise());

        // Assert: exactly two entries belong to this transfer.
        List<LedgerEntry> entries =
                ledgerEntryRepository.findByTransferId(transfer.getId());

        assertEquals(2, entries.size());

        assertEquals(
                1,
                entries.stream()
                        .filter(e -> e.getDirection() == EntryDirection.DEBIT)
                        .count()
        );

        assertEquals(
                1,
                entries.stream()
                        .filter(e -> e.getDirection() == EntryDirection.CREDIT)
                        .count()
        );

        assertTrue(entries.stream()
                .allMatch(e -> e.getAmountPaise() == 500));

        assertTrue(transferRepository.findById(transfer.getId()).isPresent());
    }


    @Test
    void insufficientFundsRejectsTransferWithoutChangingBalancesOrCreatingEntries() {
        // Arrange
        Account source = new Account("Source", AccountType.BORROWER);
        source.credit(1000);
        source = accountRepository.save(source);

        Account destination = new Account(
                "Destination", AccountType.BORROWER
        );
        destination = accountRepository.save(destination);

        Long sourceId = source.getId();
        Long destinationId = destination.getId();

        // Act and assert
        assertThrows(
                IllegalStateException.class,
                () -> transferService.transfer(sourceId, destinationId, 2000, "insufficient-funds-test")
        );

        // Verify balances remain unchanged.
        Account updatedSource = accountRepository.findById(sourceId)
                .orElseThrow();

        Account updatedDestination = accountRepository.findById(destinationId)
                .orElseThrow();

        assertEquals(1000, updatedSource.getBalancePaise());
        assertEquals(0, updatedDestination.getBalancePaise());

        // Verify no transfer or ledger entries were committed.
        assertEquals(0, transferRepository.count());
        assertEquals(0, ledgerEntryRepository.count());
    }


    @Test
    void repeatedRequestWithSameIdempotencyKeyTransfersMoneyOnlyOnce() {
        // Arrange: create and fund the accounts.
        Account source = new Account("Source", AccountType.BORROWER);
        Account destination = new Account("Destination", AccountType.FEES);

        source.credit(1000);

        source = accountRepository.save(source);
        destination = accountRepository.save(destination);

        // Act: submit the same transfer twice with the same key.
        Transfer first = transferService.transfer(
                source.getId(), destination.getId(), 500, "payment-123"
        );

        Transfer second = transferService.transfer(
                source.getId(), destination.getId(), 500, "payment-123"
        );

        // Assert: both calls refer to the same transfer.
        assertEquals(first.getId(), second.getId());

        Account updatedSource = accountRepository.findById(source.getId())
                .orElseThrow();
        Account updatedDestination = accountRepository.findById(destination.getId())
                .orElseThrow();

        assertEquals(500, updatedSource.getBalancePaise());
        assertEquals(500, updatedDestination.getBalancePaise());

        assertEquals(1, transferRepository.count());
        assertEquals(2, ledgerEntryRepository.count());
    }


    @Test
    void reusingIdempotencyKeyWithDifferentAmountIsRejected() {
        Account source = new Account("Source", AccountType.BORROWER);
        source.credit(2000);
        source = accountRepository.save(source);

        Account destination = new Account("Destination", AccountType.FEES);
        destination = accountRepository.save(destination);

        Long sourceId = source.getId();
        Long destinationId = destination.getId();

        transferService.transfer(sourceId, destinationId, 500, "payment-456");

        assertThrows(
                IllegalArgumentException.class,
                () -> transferService.transfer(
                        sourceId, destinationId, 700, "payment-456"
                )
        );

        Account updatedSource = accountRepository.findById(sourceId).orElseThrow();
        Account updatedDestination = accountRepository.findById(destinationId).orElseThrow();

        assertEquals(1500, updatedSource.getBalancePaise());
        assertEquals(500, updatedDestination.getBalancePaise());
        assertEquals(1, transferRepository.count());
        assertEquals(2, ledgerEntryRepository.count());
    }


    @Test
    void differentIdempotencyKeysAllowSeparateTransfers() {
        // Arrange: create and fund the accounts.
        Account source = new Account("Source", AccountType.BORROWER);
        source.credit(2000);
        source = accountRepository.save(source);

        Account destination = new Account("Destination", AccountType.FEES);
        destination = accountRepository.save(destination);

        Long sourceId = source.getId();
        Long destinationId = destination.getId();

        // Act: make two transfers with different keys.
        Transfer first = transferService.transfer(
                sourceId, destinationId, 500, "payment-111"
        );

        Transfer second = transferService.transfer(
                sourceId, destinationId, 300, "payment-222"
        );

        // Assert: two distinct transfers were created.
        assertNotEquals(first.getId(), second.getId());

        Account updatedSource = accountRepository.findById(sourceId).orElseThrow();
        Account updatedDestination = accountRepository.findById(destinationId).orElseThrow();

        assertEquals(1200, updatedSource.getBalancePaise());
        assertEquals(800, updatedDestination.getBalancePaise());

        assertEquals(2, transferRepository.count());
        assertEquals(4, ledgerEntryRepository.count());
    }








}
