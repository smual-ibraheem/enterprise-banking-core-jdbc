package bank.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseConnectionTest
 * يختبر صحة الاتصال بقاعدة البيانات قبل تشغيل أي شيء آخر.
 */
class DatabaseConnectionTest {

    @Test
    void testConnectionIsValid() {
        // نستخدم try-with-resources لضمان الإغلاق حتى في الاختبارات
        try (Connection conn = DatabaseConnection.getConnection()) {

            // 1. نتأكد أن الكائن ليس Null
            assertNotNull(conn, "اتصال قاعدة البيانات يجب ألا يكون Null");

            // 2. نرسل إشارة Ping للداتابيز (بمهلة ثانيتين) لنتأكد أنها تستجيب
            assertTrue(conn.isValid(2), "يجب أن يكون الاتصال مفتوحاً وصالحاً للاستخدام");

        } catch (SQLException e) {
            fail("حدث خطأ أثناء محاولة الاتصال بقاعدة البيانات: " + e.getMessage());
        }
    }
}