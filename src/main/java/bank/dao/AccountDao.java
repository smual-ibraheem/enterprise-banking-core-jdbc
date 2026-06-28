package bank.dao;

import bank.config.DatabaseConnection;
import bank.core.Account;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * AccountDao (Data Access Object)
 * الطبقة الوحيدة المسؤولة عن تنفيذ استعلامات جدول الحسابات.
 */
public class AccountDao {

    // دالة لحفظ الحساب الجديد في قاعدة البيانات
    public void saveAccount(Account account) {

        // استعلام الـ SQL. لاحظ أننا نستخدم علامات الاستفهام (?) بدلاً من دمج القيم مباشرة
        String sql = "INSERT INTO accounts (id, owner_name, balance, state, version) VALUES (?, ?, ?, ?, ?)";

        // Try-with-resources: الأقواس () تضمن إغلاق الاتصال تلقائياً فور الانتهاء أو حدوث خطأ
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // تعبئة المتغيرات (?) بالقيم الحقيقية من كائن الحساب
            stmt.setString(1, account.getId());
            stmt.setString(2, account.getOwnerName());
            stmt.setLong(3, account.getBalance());
            stmt.setString(4, account.getState().name()); // تحويل الـ Enum إلى نص
            stmt.setInt(5, 0); // الـ Version يبدأ دائماً من 0 عند الإنشاء

            // تنفيذ الأمر (إرساله للداتابيز)
            stmt.executeUpdate();

        } catch (SQLException e) {
            // كمهندسين، لا نبتلع الخطأ (Swallow Exception)، بل نوقفه ونرميه كخطأ قاتل
            throw new RuntimeException("Database error while saving account: " + account.getId(), e);
        }
    }

    // دالة للبحث عن حساب بواسطة الـ ID وقراءة الـ Version لحماية التزامن
    public Account findById(String id) {
        String sql = "SELECT * FROM accounts WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);

            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String ownerName = rs.getString("owner_name");
                    long balance = rs.getLong("balance");
                    String stateStr = rs.getString("state");

                    // 1. جلب الـ version الحالي المخزن في الداتابيز
                    int version = rs.getInt("version");

                    bank.state.AccountState state = bank.state.AccountState.valueOf(stateStr);

                    // 2. تمرير الـ version للباني المخصص لإعادة بناء الكائن بكامل حالته
                    return new Account(id, ownerName, balance, state, version);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while fetching account: " + id, e);
        }

        // إذا لم يعثر على الحساب
        return null;
    }

    /**
     * تحديث رصيد الحساب وحالته في قاعدة البيانات.
     * تطبق الدالة مفهوم Optimistic Locking لحماية النظام من الـ Race Conditions.
     */
    public void updateAccount(Account account) {
        // الاستعلام يزيد الـ version بمقدار 1 بشرط مطابقة الـ version الحالي
        String sql = "UPDATE accounts SET balance = ?, state = ?, version = version + 1 WHERE id = ? AND version = ?";

        // استخدام Try-with-resources لضمان إغلاق الموارد فوراً ومنع تسريب الذاكرة
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // تعبئة البيانات بالترتيب الصحيح للاستعلام
            stmt.setLong(1, account.getBalance());
            stmt.setString(2, account.getState().name()); // تحويل الـ Enum إلى نص
            stmt.setString(3, account.getId());
            stmt.setInt(4, account.getVersion()); // الـ version القديم الذي قرأناه مسبقاً

            // تنفيذ التحديث واستقبال عدد الأسطر المتأثرة
            int affectedRows = stmt.executeUpdate();

            // إذا كان عدد الأسطر 0، فهذا يعني خطأ تزامن صريح (شخص آخر سبقه بالتعديل)
            if (affectedRows == 0) {
                throw new RuntimeException("Optimistic Lock Exception: Account " + account.getId()
                        + " was modified or updated by another concurrent transaction!");
            }

            // خطوة حاسمة: تحديث الـ version داخل كائن الجافا ليبقى متطابقاً مع قاعدة البيانات
            account.setVersion(account.getVersion() + 1);

        } catch (SQLException e) {
            // معالجة الخطأ ورميه كـ RuntimeException لمنع استمرار النظام في حال فشل الداتابيز
            throw new RuntimeException("Database error while updating account: " + account.getId(), e);
        }
    }

    // Overloaded Method for ACID Transactions
    public void updateAccount(Connection conn, Account account) throws SQLException {
        String sql = "UPDATE accounts SET balance = ?, state = ?, version = version + 1 WHERE id = ? AND version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, account.getBalance());
            stmt.setString(2, account.getState().name());
            stmt.setString(3, account.getId());
            stmt.setInt(4, account.getVersion());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new RuntimeException("Optimistic Lock Exception: Concurrent modification detected!");
            }
            account.setVersion(account.getVersion() + 1);
        }
    }
}