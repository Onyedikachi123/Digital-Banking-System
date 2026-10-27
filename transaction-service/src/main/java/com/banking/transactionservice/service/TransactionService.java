package com.banking.transactionservice.service;


import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;


    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

    /**
     * SAGA STEP - 1: Initiate transfer
     * Debucts from sender via feign
     * Saves transaction as PROCESSING.
     * Publish event to kafka for fraud check
     * @param request
     * @return
     */
    public TransactionResponse transfer(TransferRequest request) {

    }
}
