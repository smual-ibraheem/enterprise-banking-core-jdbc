package bank.dao;

import bank.config.DatabaseConnection;
import bank.core.Account;
import bank.core.Transaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TransactionDaoTest (Integration Test)
 * يختبر إدراج العمليات وحساب الحد اليومي، مع الحفاظ على نظافة الداتابيز.
 */
class TransactionDaoTest {

    private AccountDao accountDao;
    private TransactionDao transactionDao;

    private String testAccountId;
    private String testTransactionId;

    @BeforeEach
    void setUp() {
        accountDao = new AccountDao();
        transactionDao = new TransactionDao();

        // توليد IDs وهمية للاختبار
        testAccountId = "TEST-ACC-" + UUID.randomUUID().toString().substring(0, 15);
        testTransactionId = UUID.randomUUID().toString();

        // خطوة هندسية إلزامية: إدراج حساب وهمي أولاً لكي لا يرفض الـ Foreign Key العملية
        Account dummyAccount = new Account(testAccountId, "Test User", 5000);
        accountDao.saveAccount(dummyAccount);
    }

    @AfterEach
    void tearDown() {
        // التنظيف العكسي: نحذف الابن (العملية) أولاً، ثم الأب (الحساب) لتجنب أخطاء القيود
        try (Connection conn = DatabaseConnection.getConnection()) {

            String deleteTx = "DELETE FROM transactions WHERE id = ?";
            try (PreparedStatement stmt1 = conn.prepareStatement(deleteTx)) {
                stmt1.setString(1, testTransactionId);
                stmt1.executeUpdate();
            }

            String deleteAcc = "DELETE FROM accounts WHERE id = ?";
            try (PreparedStatement stmt2 = conn.prepareStatement(deleteAcc)) {
                stmt2.setString(1, testAccountId);
                stmt2.executeUpdate();
            }

        } catch (SQLException e) {
            System.err.println("فشل في تنظيف بيانات اختبار TransactionDao");
        }
    }

    @Test
    void testSaveTransactionAndCalculateDailyTotal() {
        // 1. Arrange (التجهيز: إنشاء عملية سحب ناجحة بقيمة 1500)
        Transaction tx = new Transaction(
                testTransactionId,
                Transaction.TransactionType.WITHDRAW,
                1500,
                LocalDateTime.now(),
                Transaction.TransactionStatus.SUCCESS,
                null,
                testAccountId,
                null
        );

        // 2. Act (التنفيذ: حفظ العملية وجلب المجموع اليومي من الداتابيز مباشرة)
        transactionDao.saveTransaction(tx);
        long dailyTotal = transactionDao.getDailyTotalForAccount(testAccountId, LocalDate.now());

        // 3. Assert (التحقق: يجب أن تقوم الداتابيز بجمع 1500 بنجاح عبر دالة SUM)
        assertEquals(1500, dailyTotal, "محرك الداتابيز يجب أن يحسب المجموع اليومي للعمليات الناجحة بدقة");
    }
}