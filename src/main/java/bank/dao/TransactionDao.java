package bank.dao;

import bank.config.DatabaseConnection;
import bank.core.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * TransactionDao
 * مسؤول عن تخزين واسترجاع العمليات المالية.
 * (Append-Only) لا يحتوي على دوال تحديث أو حذف نهائياً.
 */
public class TransactionDao {

    // 1. دالة لحفظ العملية الجديدة في الداتابيز
    public void saveTransaction(Transaction tx) {
        String sql = "INSERT INTO transactions (id, type, amount, status, rejection_reason, from_account_id, to_account_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, tx.getId());
            stmt.setString(2, tx.getType().name());
            stmt.setLong(3, tx.getAmount());
            stmt.setString(4, tx.getStatus().name());

            // معالجة القيم التي قد تكون Null (مثل سبب الرفض في العمليات الناجحة)
            stmt.setString(5, tx.getRejectionReason() != null ? tx.getRejectionReason().name() : null);
            stmt.setString(6, tx.getFromAccountId()); // سيكون Null في حالة الإيداع
            stmt.setString(7, tx.getToAccountId());   // سيكون Null في حالة السحب

            // تحويل وقت الجافا (LocalDateTime) إلى وقت تفهمه الداتابيز (Timestamp)
            stmt.setTimestamp(8, Timestamp.valueOf(tx.getTimestamp()));

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Database error while saving transaction: " + tx.getId(), e);
        }
    }

    // Overloaded Method for ACID Transactions
    public void saveTransaction(Connection conn, Transaction tx) throws SQLException {
        String sql = "INSERT INTO transactions (id, type, amount, status, rejection_reason, from_account_id, to_account_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tx.getId());
            stmt.setString(2, tx.getType().name());
            stmt.setLong(3, tx.getAmount());
            stmt.setString(4, tx.getStatus().name());
            stmt.setString(5, tx.getRejectionReason() != null ? tx.getRejectionReason().name() : null);
            stmt.setString(6, tx.getFromAccountId());
            stmt.setString(7, tx.getToAccountId());
            stmt.setTimestamp(8, Timestamp.valueOf(tx.getTimestamp()));
            stmt.executeUpdate();
        }
    }

    // 2. دالة لجلب آخر العمليات لحساب معين (تُستخدم لكشف الحساب - Statement)
    public List<Transaction> getRecentTransactionsForAccount(String accountId, int limit) {
        // الاستعلام يجلب العمليات التي يكون فيها الحساب إما مرسلاً أو مستقبلاً، ويرتبها من الأحدث للأقدم
        String sql = "SELECT * FROM transactions WHERE from_account_id = ? OR to_account_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, accountId);
            stmt.setString(2, accountId);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    Transaction.TransactionType type = Transaction.TransactionType.valueOf(rs.getString("type"));
                    long amount = rs.getLong("amount");
                    LocalDateTime timestamp = rs.getTimestamp("created_at").toLocalDateTime();
                    Transaction.TransactionStatus status = Transaction.TransactionStatus.valueOf(rs.getString("status"));

                    String rejectionReasonStr = rs.getString("rejection_reason");
                    Transaction.RejectionReason rejectionReason = rejectionReasonStr != null ? Transaction.RejectionReason.valueOf(rejectionReasonStr) : null;

                    String fromAccount = rs.getString("from_account_id");
                    String toAccount = rs.getString("to_account_id");

                    transactions.add(new Transaction(id, type, amount, timestamp, status, rejectionReason, fromAccount, toAccount));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while fetching transactions for account: " + accountId, e);
        }
        return transactions;
    }

    // 3. دالة حساب الحد اليومي (لمسة المهندسين الكبار)
    public long getDailyTotalForAccount(String accountId, java.time.LocalDate day) {
        // بدلاً من جلب كل العمليات للجافا وحسابها بـ Loop، نجعل الداتابيز تحسب المجموع بـ SUM()
        // هذا أسرع بآلاف المرات في بيئة الإنتاج الحقيقية
        String sql = "SELECT SUM(amount) AS daily_total FROM transactions " +
                "WHERE from_account_id = ? AND status = 'SUCCESS' " +
                "AND (type = 'WITHDRAW' OR type = 'TRANSFER') " +
                "AND DATE(created_at) = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, accountId);
            stmt.setDate(2, java.sql.Date.valueOf(day));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("daily_total"); // ترجع المجموع، أو 0 إذا لم تكن هناك عمليات
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while calculating daily total: " + accountId, e);
        }
        return 0;
    }
}