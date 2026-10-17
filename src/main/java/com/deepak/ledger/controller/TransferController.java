
package com.deepak.ledger.controller;

import com.deepak.ledger.entity.Transfer;
import com.deepak.ledger.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transfer createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        return transferService.transfer(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amountPaise(),
                idempotencyKey
        );
    }

    public record CreateTransferRequest(
            @NotNull Long sourceAccountId,
            @NotNull Long destinationAccountId,
            @Positive long amountPaise
    ) {}
}
