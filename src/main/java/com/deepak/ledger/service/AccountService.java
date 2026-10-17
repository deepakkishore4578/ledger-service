
package com.deepak.ledger.service;

import com.deepak.ledger.entity.Account;
import com.deepak.ledger.entity.AccountType;
import com.deepak.ledger.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.deepak.ledger.exception.ResourceNotFoundException;

import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account createAccount(String name, AccountType type) {
        Account account = new Account(name, type);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() ->
                        new com.deepak.ledger.exception.ResourceNotFoundException(
                                "Account not found: " + id
                        ));
    }
    @Transactional(readOnly = true)
    public List<Account> getAllAccounts(){
        return accountRepository.findAll();
    }
}
