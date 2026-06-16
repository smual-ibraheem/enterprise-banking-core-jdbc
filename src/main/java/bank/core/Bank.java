package bank.core;

import bank.ledger.TransactionLog;
import bank.policy.TransactionPolicy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bank = Orchestrator
 * مسؤول عن تنسيق العمليات بين الحسابات والسياسات والتسجيل.
 */
public class Bank {

    // ===== Fields =====
    private final Map<String, Account> accounts;
    private final TransactionLog transactionLog;
    private final TransactionPolicy policy;

    // ===== Constructor =====
    public Bank(long maxTransactionAmount, long dailyLimit) {
        this.accounts = new HashMap<>();
        this.transactionLog = new TransactionLog();
        this.policy = new TransactionPolicy(maxTransactionAmount, dailyLimit, transactionLog);
    }

    // ===== Public API =====
    public Account createAccount(String ownerName, long initialBalance) {

        if (ownerName == null || ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner name cannot be empty");
        }

        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }

        String accountId = generateAccountId();
        Account account = new Account(accountId, ownerName.trim(), initialBalance);
        accounts.put(accountId, account);
        return account;
    }

    public Transaction deposit(String accountId, long amount) {
        Account account = findAccount(accountId);
        if (account == null) {
            return rejectedDeposit(
                    accountId,
                    amount,
                    Transaction.RejectionReason.ACCOUNT_NOT_FOUND
            );
        }

        TransactionPolicy.PolicyResult result = policy.validateDeposit(account, amount);

        if (!result.isAllowed()) {
            return rejectedDeposit(accountId, amount, result.getReason());
        }

        account.setBalance(account.getBalance() + amount);
        return successDeposit(accountId, amount);
    }

    public Transaction withdraw(String accountId, long amount) {
        Account account = findAccount(accountId);
        if (account == null) {
            return rejectedWithdraw(accountId,
                    amount,
                    Transaction.RejectionReason.ACCOUNT_NOT_FOUND);
        }

        TransactionPolicy.PolicyResult result = policy.validateWithdraw(account, amount);

        if (!result.isAllowed()) {
            return rejectedWithdraw(accountId, amount, result.getReason());
        }

        account.setBalance(account.getBalance() - amount);
        return successWithdraw(accountId, amount);
    }

    public Transaction transfer(String fromAccountId,
                                String toAccountId,
                                long amount) {

        Account fromAccount = findAccount(fromAccountId);
        if (fromAccount == null) {
            return rejectedTransfer(
                    fromAccountId,
                    toAccountId,
                    amount,
                    Transaction.RejectionReason.ACCOUNT_NOT_FOUND
            );
        }

        Account toAccount = findAccount(toAccountId);
        if (toAccount == null) {
            return rejectedTransfer(
                    fromAccountId,
                    toAccountId,
                    amount,
                    Transaction.RejectionReason.ACCOUNT_NOT_FOUND
            );
        }

        TransactionPolicy.PolicyResult result = policy.validateTransfer(fromAccount, toAccount, amount);

        if (!result.isAllowed()) {
            return rejectedTransfer(
                    fromAccountId,
                    toAccountId,
                    amount,
                    result.getReason()
            );
        }

        fromAccount.setBalance(fromAccount.getBalance() - amount);
        toAccount.setBalance(toAccount.getBalance() + amount);

        return successTransfer(fromAccountId, toAccountId, amount);
    }

    // ===== Private Shared Helpers =====
    private Account findAccount(String accountId) {
        return accounts.get(accountId);
    }

    public Account getAccount(String accountId) {
        return findAccount(accountId);
    }

    public List<Account> listAccounts() {
        List<Account> result = new ArrayList<>(accounts.values());
        result.sort(Comparator.comparing(Account::getId));
        return result;
    }

    public List<Transaction> getRecentTransactions(String accountId, int limit) {
        return transactionLog.getRecentTransactionsForAccount(accountId, limit);
    }

    private String generateAccountId() {
        return UUID.randomUUID().toString();
    }

    private String generateTransactionId() {
        return "TX-" + UUID.randomUUID();
    }

    private Transaction record(Transaction tx) {
        transactionLog.append(tx);
        return tx;
    }

    // ===== Deposit Helpers =====
    private Transaction rejectedDeposit(String accountId,
                                        long amount,
                                        Transaction.RejectionReason reason) {

        return record(new Transaction(
                generateTransactionId(),
                Transaction.TransactionType.DEPOSIT,
                amount,
                LocalDateTime.now(),
                Transaction.TransactionStatus.REJECTED,
                reason,
                null,
                accountId
        ));
    }

    private Transaction successDeposit(String accountId, long amount) {
        return record(new Transaction(
                generateTransactionId(),
                Transaction.TransactionType.DEPOSIT,
                amount,
                LocalDateTime.now(),
                Transaction.TransactionStatus.SUCCESS,
                null,
                null,
                accountId
        ));
    }

    // ===== Withdraw Helpers =====
    private Transaction rejectedWithdraw(String accountId,
                                         long amount,
                                         Transaction.RejectionReason reason) {

        return record(new Transaction(
                generateTransactionId(),
                Transaction.TransactionType.WITHDRAW,
                amount,
                LocalDateTime.now(),
                Transaction.TransactionStatus.REJECTED,
                reason,
                accountId,
                null
        ));
    }

    private Transaction successWithdraw(String accountId, long amount) {
        return record(new Transaction(
                generateTransactionId(),
                Transaction.TransactionType.WITHDRAW,
                amount,
                LocalDateTime.now(),
                Transaction.TransactionStatus.SUCCESS,
                null,
                accountId,
                null
        ));
    }

    // ===== Transfer Helpers =====
    private Transaction rejectedTransfer(String fromAccountId,
                                         String toAccountId,
                                         long amount,
                                         Transaction.RejectionReason reason) {

        return record(new Transaction(
                generateTransactionId(),
                Transaction.TransactionType.TRANSFER,
                amount,
                LocalDateTime.now(),
                Transaction.TransactionStatus.REJECTED,
                reason,
                fromAccountId,
                toAccountId
        ));
    }

    private Transaction successTransfer(String fromAccountId,
                                        String toAccountId,
                                        long amount) {

        return record(new Transaction(
                generateTransactionId(),
                Transaction.TransactionType.TRANSFER,
                amount,
                LocalDateTime.now(),
                Transaction.TransactionStatus.SUCCESS,
                null,
                fromAccountId,
                toAccountId
        ));
    }
}
