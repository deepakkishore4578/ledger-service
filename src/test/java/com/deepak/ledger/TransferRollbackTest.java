
package com.deepak.ledger.service;

import com.deepak.ledger.entity.Account;
import com.deepak.ledger.entity.AccountType;
import com.deepak.ledger.entity.LedgerEntry;
import com.deepak.ledger.repository.AccountRepository;
import com.deepak.ledger.repository.LedgerEntryRepository;
import com.deepak.ledger.repository.TransferRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class TransferRollbackTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private LedgerEntryRepository ledgerEntryRepositorySpy;

    @Test
    void failedLedgerEntrySaveRollsBackEntireTransfer() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long sourceId = tx.execute(status -> {
            Account source = new Account("Rollback Source", AccountType.BORROWER);
            source.credit(1000);
            source = accountRepository.save(source);

            Account destination = new Account(
                    "Rollback Destination", AccountType.BORROWER);
            destination = accountRepository.save(destination);

            return source.getId();
        });

        Long destinationId = accountRepository.findAll().stream()
                .filter(a -> "Rollback Destination".equals(a.getName()))
                .map(Account::getId)
                .findFirst()
                .orElseThrow();

        doAnswer(new org.mockito.stubbing.Answer<LedgerEntry>() {
            private int calls = 0;

            @Override
            public LedgerEntry answer(
                    org.mockito.invocation.InvocationOnMock invocation)
                    throws Throwable {
                calls++;

                if (calls == 2) {
                    throw new RuntimeException("Simulated ledger failure");
                }

                return (LedgerEntry) invocation.callRealMethod();
            }
        }).when(ledgerEntryRepositorySpy).save(any(LedgerEntry.class));

        assertThrows(RuntimeException.class,
                () -> transferService.transfer(sourceId, destinationId, 500,"rollback-test-1"));

        entityManager.clear();

        Account updatedSource = accountRepository.findById(sourceId).orElseThrow();
        Account updatedDestination =
                accountRepository.findById(destinationId).orElseThrow();

        assertEquals(1000, updatedSource.getBalancePaise());
        assertEquals(0, updatedDestination.getBalancePaise());
        assertEquals(0, transferRepository.count());
        assertEquals(0, ledgerEntryRepository.count());
    }
}
