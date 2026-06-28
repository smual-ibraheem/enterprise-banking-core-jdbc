package bank.core;

import bank.config.DatabaseConnection;
import bank.dao.AccountDao;
import bank.dao.TransactionDao;
import bank.policy.TransactionPolicy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

public class Bank {

    // لا يوجد HashMap بعد اليوم. الاعتماد الكلي على الداتابيز.
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final TransactionPolicy policy;

    public Bank(long maxTransactionAmount, long dailyLimit) {
        this.accountDao = new AccountDao();
        this.transactionDao = new TransactionDao();
        // تمرير الـ DAO بدلاً من الـ Log للسياسة
        this.policy = new TransactionPolicy(maxTransactionAmount, dailyLimit, transactionDao);
    }

    public Account createAccount(String ownerName, long initialBalance) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner name cannot be empty");
        }
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }

        String accountId = generateAccountId();
        // إنشاء الكائن
        Account account = new Account(accountId, ownerName.trim(), initialBalance);

        // حفظه فوراً في الداتابيز
        accountDao.saveAccount(account);
        return account;
    }

    public Transaction deposit(String accountId, long amount) {
        // Guard Clause: حماية من الأرقام الصفرية والسالبة قبل لمس الداتابيز
        if (amount <= 0) throw new IllegalArgumentException("Amount must be strictly positive");

        Account account = accountDao.findById(accountId);
        // حماية الداتابيز: إذا لم نجد الحساب، نمرر null بدلاً من الـ ID الوهمي لكي لا ينفجر الـ Foreign Key
        if (account == null) return recordRejected(null, null, amount, Transaction.TransactionType.DEPOSIT, Transaction.RejectionReason.ACCOUNT_NOT_FOUND);

        TransactionPolicy.PolicyResult result = policy.validateDeposit(account, amount);
        if (!result.isAllowed()) return recordRejected(accountId, null, amount, Transaction.TransactionType.DEPOSIT, result.getReason());

        account.setBalance(account.getBalance() + amount);
        Transaction tx = createSuccessTransaction(accountId, null, amount, Transaction.TransactionType.DEPOSIT);

        executeFinancialOperation(account, null, tx);
        return tx;
    }

    public Transaction withdraw(String accountId, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be strictly positive");

        Account account = accountDao.findById(accountId);
        if (account == null) return recordRejected(null, null, amount, Transaction.TransactionType.WITHDRAW, Transaction.RejectionReason.ACCOUNT_NOT_FOUND);

        TransactionPolicy.PolicyResult result = policy.validateWithdraw(account, amount);
        if (!result.isAllowed()) return recordRejected(accountId, null, amount, Transaction.TransactionType.WITHDRAW, result.getReason());

        account.setBalance(account.getBalance() - amount);
        Transaction tx = createSuccessTransaction(accountId, null, amount, Transaction.TransactionType.WITHDRAW);

        executeFinancialOperation(account, null, tx);
        return tx;
    }

    public Transaction transfer(String fromAccountId, String toAccountId, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be strictly positive");

        Account fromAccount = accountDao.findById(fromAccountId);
        Account toAccount = accountDao.findById(toAccountId);

        // حماية الداتابيز من الأرقام الوهمية (Foreign Key Protection)
        if (fromAccount == null || toAccount == null) {
            String safeFrom = (fromAccount != null) ? fromAccountId : null;
            String safeTo = (toAccount != null) ? toAccountId : null;
            return recordRejected(safeFrom, safeTo, amount, Transaction.TransactionType.TRANSFER, Transaction.RejectionReason.ACCOUNT_NOT_FOUND);
        }

        TransactionPolicy.PolicyResult result = policy.validateTransfer(fromAccount, toAccount, amount);
        if (!result.isAllowed()) return recordRejected(fromAccountId, toAccountId, amount, Transaction.TransactionType.TRANSFER, result.getReason());

        fromAccount.setBalance(fromAccount.getBalance() - amount);
        toAccount.setBalance(toAccount.getBalance() + amount);
        Transaction tx = createSuccessTransaction(fromAccountId, toAccountId, amount, Transaction.TransactionType.TRANSFER);

        executeFinancialOperation(fromAccount, toAccount, tx);
        return tx;
    }

    // =====================================================================
    // السلاح الهندسي: تنفيذ العمليات المالية ككتلة واحدة (ACID Database Transaction)
    // =====================================================================
    private void executeFinancialOperation(Account fromAccount, Account toAccount, Transaction tx) {
        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1. إيقاف الحفظ التلقائي (نبدأ الـ Transaction)
            conn.setAutoCommit(false);

            try {
                // 2. تحديث الحسابات وتسجيل العملية باستخدام نفس الاتصال (Connection)
                if (fromAccount != null) accountDao.updateAccount(conn, fromAccount);
                if (toAccount != null) accountDao.updateAccount(conn, toAccount);
                transactionDao.saveTransaction(conn, tx);

                // 3. تأكيد الحفظ للجميع بضربة واحدة
                conn.commit();
            } catch (Exception e) {
                // 4. في حال فشل أي جزء، تراجع عن كل شيء (حماية الأموال)
                conn.rollback();
                throw new RuntimeException("Financial transaction failed, everything rolled back cleanly.", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error during financial operation", e);
        }
    }

    // ===== Helpers =====
    public Account getAccount(String accountId) {
        return accountDao.findById(accountId);
    }

    public List<Transaction> getRecentTransactions(String accountId, int limit) {
        return transactionDao.getRecentTransactionsForAccount(accountId, limit);
    }

    private String generateAccountId() { return UUID.randomUUID().toString(); }
    private String generateTransactionId() { return UUID.randomUUID().toString(); }

    private Transaction recordRejected(String fromId, String toId, long amount, Transaction.TransactionType type, Transaction.RejectionReason reason) {
        Transaction tx = new Transaction(generateTransactionId(), type, amount, LocalDateTime.now(), Transaction.TransactionStatus.REJECTED, reason, fromId, toId);
        transactionDao.saveTransaction(tx); // الرفض لا يحتاج ACID لأنه مجرد تسجيل
        return tx;
    }

    private Transaction createSuccessTransaction(String fromId, String toId, long amount, Transaction.TransactionType type) {
        return new Transaction(generateTransactionId(), type, amount, LocalDateTime.now(), Transaction.TransactionStatus.SUCCESS, null, fromId, toId);
    }
}