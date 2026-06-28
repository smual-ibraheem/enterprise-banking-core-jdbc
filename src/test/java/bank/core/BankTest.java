package bank.core;

import bank.config.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BankTest (Integration Test)
 * يختبر المحرك البنكي كاملاً مع قاعدة البيانات ويتأكد من عمل الـ ACID Transactions.
 */
class BankTest {

    // قائمة لتتبع الحسابات التي يتم إنشاؤها أثناء الاختبار لحذفها لاحقاً
    private List<String> testAccountIds;

    @BeforeEach
    void setUp() {
        testAccountIds = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        // تنظيف صارم: مسح أي عملية أو حساب تم إنشاؤه في هذا الاختبار
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String accountId : testAccountIds) {
                // 1. مسح العمليات المرتبطة (الابن)
                String deleteTx = "DELETE FROM transactions WHERE from_account_id = ? OR to_account_id = ?";
                try (PreparedStatement stmtTx = conn.prepareStatement(deleteTx)) {
                    stmtTx.setString(1, accountId);
                    stmtTx.setString(2, accountId);
                    stmtTx.executeUpdate();
                }
                // 2. مسح الحساب (الأب)
                String deleteAcc = "DELETE FROM accounts WHERE id = ?";
                try (PreparedStatement stmtAcc = conn.prepareStatement(deleteAcc)) {
                    stmtAcc.setString(1, accountId);
                    stmtAcc.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to clean up test data in BankTest.");
        }
    }

    // دالة مساعدة (Helper) لإنشاء الحساب وتتبعه أوتوماتيكياً
    private Account createTrackedAccount(Bank bank, String ownerName, long initialBalance) {
        Account account = bank.createAccount(ownerName, initialBalance);
        testAccountIds.add(account.getId()); // تتبع الـ ID من أجل الحذف
        return account;
    }

    // ================== الاختبارات الهندسية ==================

    @Test
    void createAccount_shouldCreateActiveAccountWithInitialBalance() {
        Bank bank = new Bank(1_000_000, 5_000_000);
        Account account = createTrackedAccount(bank, "Smual", 10_000);

        // يجب قراءة الحساب من الداتابيز للتأكد من حفظه فعلياً وليس في الذاكرة فقط
        Account dbAccount = bank.getAccount(account.getId());

        assertNotNull(dbAccount, "يجب أن يكون الحساب محفوظاً في الداتابيز");
        assertEquals("Smual", dbAccount.getOwnerName());
        assertEquals(10_000, dbAccount.getBalance());
    }

    @Test
    void deposit_shouldIncreaseAccountBalanceInDatabase() {
        Bank bank = new Bank(1_000_000, 5_000_000);
        Account account = createTrackedAccount(bank, "Sara", 10_000);

        Transaction transaction = bank.deposit(account.getId(), 5_000);
        Account dbAccount = bank.getAccount(account.getId()); // قراءة الرصيد الجديد من الداتابيز

        assertEquals(Transaction.TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(15_000, dbAccount.getBalance(), "الرصيد في الداتابيز يجب أن يزداد");
    }

    @Test
    void deposit_shouldRejectInvalidAmount() {
        Bank bank = new Bank(1_000_000, 5_000_000);
        Account account = createTrackedAccount(bank, "Sara", 10_000);

        // الآن النظام سيرمي استثناء فوراً لأن القيمة 0 أو سالبة هي خطأ هيكلي، وليست عملية بنكية تُسجل
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bank.deposit(account.getId(), 0)
        );
        assertEquals("Amount must be strictly positive", exception.getMessage());
    }

    @Test
    void withdraw_shouldDecreaseAccountBalanceInDatabase() {
        Bank bank = new Bank(1_000_000, 5_000_000);
        Account account = createTrackedAccount(bank, "Smual", 10_000);

        Transaction transaction = bank.withdraw(account.getId(), 3_000);
        Account dbAccount = bank.getAccount(account.getId());

        assertEquals(Transaction.TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(7_000, dbAccount.getBalance(), "الرصيد في الداتابيز يجب أن ينقص");
    }

    @Test
    void withdraw_shouldRejectInsufficientFunds() {
        Bank bank = new Bank(1_000_000, 5_000_000);
        Account account = createTrackedAccount(bank, "Smual", 2_000);

        Transaction transaction = bank.withdraw(account.getId(), 5_000);
        Account dbAccount = bank.getAccount(account.getId());

        assertEquals(Transaction.TransactionStatus.REJECTED, transaction.getStatus());
        assertEquals(Transaction.RejectionReason.INSUFFICIENT_FUNDS, transaction.getRejectionReason());
        assertEquals(2_000, dbAccount.getBalance());
    }

    @Test
    void transfer_shouldMoveMoneyBetweenAccountsAtomically() {
        Bank bank = new Bank(1_000_000, 5_000_000);
        Account from = createTrackedAccount(bank, "Smual", 10_000);
        Account to = createTrackedAccount(bank, "Sara", 2_000);

        Transaction transaction = bank.transfer(from.getId(), to.getId(), 4_000);

        Account dbFrom = bank.getAccount(from.getId());
        Account dbTo = bank.getAccount(to.getId());

        assertEquals(Transaction.TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(6_000, dbFrom.getBalance(), "رصيد المرسل يجب أن ينقص");
        assertEquals(6_000, dbTo.getBalance(), "رصيد المستقبل يجب أن يزداد");
    }

    @Test
    void transfer_shouldRejectSameAccountTransfer() {
        Bank bank = new Bank(1_000_000, 5_000_000);
        Account account = createTrackedAccount(bank, "Smual", 10_000);

        Transaction transaction = bank.transfer(account.getId(), account.getId(), 1_000);
        Account dbAccount = bank.getAccount(account.getId());

        assertEquals(Transaction.TransactionStatus.REJECTED, transaction.getStatus());
        assertEquals(Transaction.RejectionReason.SAME_ACCOUNT_TRANSFER, transaction.getRejectionReason());
        assertEquals(10_000, dbAccount.getBalance());
    }

    @Test
    void transfer_shouldRejectWhenToAccountDoesNotExist() {
        Bank bank = new Bank(1_000_000, 5_000_000);
        Account from = createTrackedAccount(bank, "Smual", 10_000);

        // لا داعي لتتبعه لأنه حساب وهمي لم يٌنشأ
        Transaction transaction = bank.transfer(from.getId(), "missing-account-id", 1_000);
        Account dbFrom = bank.getAccount(from.getId());

        assertEquals(Transaction.TransactionStatus.REJECTED, transaction.getStatus());
        assertEquals(Transaction.RejectionReason.ACCOUNT_NOT_FOUND, transaction.getRejectionReason());
        assertEquals(10_000, dbFrom.getBalance());
    }

    @Test
    void withdraw_shouldRejectWhenDailyLimitExceeded() {
        // الحد اليومي 5000 فقط
        Bank bank = new Bank(1_000_000, 5_000);
        Account account = createTrackedAccount(bank, "Smual", 20_000);

        Transaction first = bank.withdraw(account.getId(), 4_000);
        Transaction second = bank.withdraw(account.getId(), 2_000);

        Account dbAccount = bank.getAccount(account.getId());

        assertEquals(Transaction.TransactionStatus.SUCCESS, first.getStatus());
        assertEquals(Transaction.TransactionStatus.REJECTED, second.getStatus());
        assertEquals(Transaction.RejectionReason.DAILY_LIMIT_EXCEEDED, second.getRejectionReason());
        assertEquals(16_000, dbAccount.getBalance(), "الداتابيز يجب أن تحمي الرصيد من السحب الثاني");
    }

    @Test
    void deposit_shouldRejectWhenMaxTransactionExceeded() {
        // الحد الأقصى للعملية الواحدة هو 1000
        Bank bank = new Bank(1_000, 5_000);
        Account account = createTrackedAccount(bank, "Sara", 10_000);

        Transaction transaction = bank.deposit(account.getId(), 2_000);
        Account dbAccount = bank.getAccount(account.getId());

        assertEquals(Transaction.TransactionStatus.REJECTED, transaction.getStatus());
        assertEquals(Transaction.RejectionReason.MAX_TX_EXCEEDED, transaction.getRejectionReason());
        assertEquals(10_000, dbAccount.getBalance());
    }

    @Test
    void createAccount_shouldRejectNegativeInitialBalance() {
        Bank bank = new Bank(1_000_000, 5_000_000);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bank.createAccount("Smual", -1_000)
        );

        assertEquals("Initial balance cannot be negative", exception.getMessage());
    }

    @Test
    void createAccount_shouldRejectEmptyOwnerName() {
        Bank bank = new Bank(1_000_000, 5_000_000);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bank.createAccount("   ", 10_000)
        );

        assertEquals("Owner name cannot be empty", exception.getMessage());
    }
}