package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // FIX: added for transactional balance updates

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static final SecureRandom secureRandom = new SecureRandom(); // FIX: made final

    @Transactional // FIX: added
    public AccountResponse createAccount(CreateAccountRequest request){
        log.info("Create account for: {}", request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account already exists for email: "+request.getEmail());
        }

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE); // FIX: was request.ACTIVE (compile error)
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVINGS
                        ? new BigDecimal("100000")
                        : new BigDecimal("500000")
        );
        Account savedAccount = accountRepository.save(account);
        log.info("Account created: {}", savedAccount.getAccountNumber());

        return mapToResponse(savedAccount);
    }

    /**
     * Get account by account number
     */
    @Transactional(readOnly = true) // FIX: added
    public AccountResponse getAccount(String accountNumber){
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));

        return mapToResponse(account);
    }

    /**
     * Get balance
     */
    @Transactional(readOnly = true) // FIX: added
    public BigDecimal getBalance(String accountNumber){
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));

        return account.getBalance();
    }

    /**
     * Block account - called by Fraud detection Service via Kafka
     */
    @Transactional // FIX: added
    public void blockAccount(String accountNumber) {
        log.info("Blocking account: {}", accountNumber);
        Account account = accountRepository.findByAccountNumberForUpdate(accountNumber) // FIX: row lock
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked: {}", accountNumber);
    }

    /**
     * Deduct balance from sender account.
     */
    @Transactional // FIX: added — check + update must be atomic
    public void deductBalance(String accountNumber, BigDecimal amount){
        log.info("Deducting balance {} from account: {}", amount, accountNumber);

        Account account = accountRepository.findByAccountNumberForUpdate(accountNumber) // FIX: row lock prevents double-spend
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));

        if(account.getStatus() != AccountStatus.ACTIVE){
            throw new RuntimeException("Account is not active: " + accountNumber); // FIX: missing separator
        }

        if(account.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Insufficient funds for account: " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Balance updated. New Balance: {}", account.getBalance());
    }

    /**
     * Credit balance
     * Called by Transaction Service via kafka
     */
    @Transactional // FIX: added
    public void creditBalance(String accountNumber, BigDecimal amount){
        log.info("Crediting {} to account: {}", amount, accountNumber); // FIX: args were inside the string

        Account account = accountRepository.findByAccountNumberForUpdate(accountNumber) // FIX: row lock
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Balance updated. New Balance: {}", account.getBalance());
    }

    // Generate unique 12 digit account number
    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse mapToResponse(Account account){
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt()); // FIX: names now match (createdAt in entity + DTO)

        return response;
    }
}