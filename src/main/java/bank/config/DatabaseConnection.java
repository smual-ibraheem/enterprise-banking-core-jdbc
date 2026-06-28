package bank.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * كلاس مسؤول عن إدارة الاتصال بقاعدة البيانات.
 * مصمم كـ (Utility Class) لمنع التكرار واستهلاك الذاكرة.
 */
public class DatabaseConnection {

    // قراءة الإعدادات من بيئة التشغيل لحماية البيانات الحساسة
    private static final String URL = System.getenv("DB_URL");
    private static final String USER = System.getenv("DB_USER");
    private static final String PASSWORD = System.getenv("DB_PASSWORD");

    // منع إنشاء كائنات (Objects) من هذا الكلاس
    private DatabaseConnection() {
    }

    /**
     * يفتح اتصالاً آمناً مع قاعدة البيانات.
     *
     * @return كائن Connection النشط
     * @throws SQLException في حال غياب الإعدادات أو فشل الاتصال
     */
    public static Connection getConnection() throws SQLException {

        // التحقق من وجود المتغيرات قبل الاتصال (Fast-Fail)
        if (URL == null || USER == null || PASSWORD == null) {
            throw new SQLException("Critical Error: Database environment variables (DB_URL, DB_USER, DB_PASSWORD) are missing!");
        }

        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}