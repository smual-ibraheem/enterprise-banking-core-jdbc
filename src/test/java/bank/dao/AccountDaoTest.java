package bank.dao;

import bank.core.Account;
import bank.state.AccountState;
import bank.config.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccountDaoTest (Integration Test)
 * يختبر العمليات الفعلية مع قاعدة البيانات.
 * يستخدم الـ Reflection لتعديل الحقول المخفية (Encapsulated) دون كسر تصميم الكائنات.
 */
class AccountDaoTest {

    private AccountDao accountDao;
    private String testAccountId;

    @BeforeEach
    void setUp() {
        accountDao = new AccountDao();
        testAccountId = "TEST-" + UUID.randomUUID().toString().substring(0, 20);
    }

    @AfterEach
    void tearDown() {
        String sql = "DELETE FROM accounts WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, testAccountId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to clean up test data.");
        }
    }

    @Test
    void testSaveAndFindAccount_Success() {
        // 1. Arrange
        Account newAccount = new Account(testAccountId, "Smual Test", 7000);

        // 2. Act
        accountDao.saveAccount(newAccount);
        Account fetchedAccount = accountDao.findById(testAccountId);

        // 3. Assert
        assertNotNull(fetchedAccount, "يجب أن يجد الحساب في قاعدة البيانات");
        assertEquals("Smual Test", fetchedAccount.getOwnerName(), "الاسم يجب أن يتطابق");
        assertEquals(7000, fetchedAccount.getBalance(), "الرصيد يجب أن يتطابق");
        assertEquals(AccountState.ACTIVE, fetchedAccount.getState(), "حالة الحساب يجب أن تكون نشطة افتراضياً");
        assertEquals(0, fetchedAccount.getVersion(), "الـ Version يجب أن يبدأ من 0");
    }

    @Test
    void testOptimisticLocking_PreventsConcurrentUpdates() throws Exception {
        // 1. التجهيز: حفظ حساب أساسي
        Account account = new Account(testAccountId, "Concurrency Test", 1000);
        accountDao.saveAccount(account);

        // 2. التنفيذ: جلب الحساب مرتين (محاكاة عمليتين متزامنتين)
        Account thread1Account = accountDao.findById(testAccountId);
        Account thread2Account = accountDao.findById(testAccountId);

        // === السلاح الهندسي: استخدام Reflection لكسر الكبسلة في بيئة الاختبار فقط ===
        Field balanceField = Account.class.getDeclaredField("balance");
        balanceField.setAccessible(true); // نفتح المجال لتعديل المتغير المخفي

        // العملية الأولى تسحب 200 وتنجح
        balanceField.set(thread1Account, 800L);
        assertDoesNotThrow(() -> accountDao.updateAccount(thread1Account));

        // العملية الثانية تحاول سحب 500 بناءً على النسخة القديمة.. يجب أن تفشل!
        balanceField.set(thread2Account, 500L);

        // 3. التحقق: نتأكد أن النظام يرمي استثناء التزامن
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            accountDao.updateAccount(thread2Account);
        });

        assertTrue(exception.getMessage().contains("Optimistic Lock Exception"), "يجب حظر العملية المتزامنة");
    }
}