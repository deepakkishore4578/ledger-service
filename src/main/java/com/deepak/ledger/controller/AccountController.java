
package com.deepak.ledger.controller;

import com.deepak.ledger.entity.Account;
import com.deepak.ledger.entity.AccountType;
import com.deepak.ledger.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(
                request.name(),
                request.type()
        );
    }

    @GetMapping("/{id}")
    public Account getAccount(@PathVariable Long id) {
        return accountService.getAccount(id);
    }
    @GetMapping
    public List<Account> getAllAccounts(){
        return accountService.getAllAccounts();
    }


    @GetMapping("/{id}/balance")
    public AccountBalanceResponse getAccountBalance(
            @PathVariable Long id) {

        Account account = accountService.getAccount(id);

        return new AccountBalanceResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalancePaise()
        );
    }

    public record AccountBalanceResponse(
            Long accountId,
            String currency,
            long balancePaise
    ) {}


    public record CreateAccountRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type
    ) {}
}
