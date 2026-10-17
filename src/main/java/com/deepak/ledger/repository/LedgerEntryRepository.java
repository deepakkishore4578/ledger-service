
package com.deepak.ledger.repository;

import com.deepak.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransferId(Long transferId);

    List<LedgerEntry> findByAccountId(Long accountId);
}
