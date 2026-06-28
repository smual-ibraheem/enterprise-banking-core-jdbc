package bank.policy;

import bank.core.Account;
import bank.core.Transaction;
import bank.state.AccountState;
import bank.dao.TransactionDao;

/**
 * TransactionPolicy (سياسة العمليات)
 * ----------------------------------
 * مسؤوليته: اتخاذ القرار فقط (Allow/Reject) بناءً على قواعد العمل.
 * لا ينفّذ عمليات مالية، لا يغيّر أرصدة، ولا يسجّل في TransactionLog.
 */
public class TransactionPolicy {

    // إعدادات (Config) تُمرَّر من الخارج (مثلاً من Bank) بدل hard-code
    private final long maxTransactionAmount;
    private final long dailyLimit;
    private final TransactionDao transactionDao;

    public TransactionPolicy(long maxTransactionAmount, long dailyLimit, TransactionDao transactionDao) {
        this.maxTransactionAmount = maxTransactionAmount;
        this.dailyLimit = dailyLimit;
        this.transactionDao = transactionDao;
    }

    /**
     * PolicyResult (نتيجة السياسة)
     * بدل boolean فقط: نرجّع قرار + سبب رفض واضح (إن وجد).
     */
    public static class PolicyResult {
        private final boolean allowed;
        private final Transaction.RejectionReason reason; // null إذا allowed

        private PolicyResult(boolean allowed, Transaction.RejectionReason reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public static PolicyResult allow() {
            return new PolicyResult(true, null);
        }

        public static PolicyResult reject(Transaction.RejectionReason reason) {
            return new PolicyResult(false, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Transaction.RejectionReason getReason() {
            return reason;
        }
    }

    // ===== Basic Rules (قواعد أساسية) =====

    /**
     * checkState (فحص حالة الحساب)
     * - FROZEN/CLOSED => رفض مباشر
     */
    public PolicyResult checkState(Account account) {
        AccountState state = account.getState();

        if (state == AccountState.FROZEN) {
            return PolicyResult.reject(Transaction.RejectionReason.ACCOUNT_FROZEN);
        }
        if (state == AccountState.CLOSED) {
            return PolicyResult.reject(Transaction.RejectionReason.ACCOUNT_CLOSED);
        }

        return PolicyResult.allow();
    }

    /**
     * checkAmount (فحص المبلغ)
     * - amount <= 0 => رفض
     */
    public PolicyResult checkAmount(long amount) {
        if (amount <= 0) {
            return PolicyResult.reject(Transaction.RejectionReason.INVALID_AMOUNT);
        }
        return PolicyResult.allow();
    }

    /**
     * checkSufficientFunds (فحص الرصيد الكافي)
     * - amount > balance => رفض
     */
    public PolicyResult checkSufficientFunds(long balance, long amount) {
        if (amount > balance) {
            return PolicyResult.reject(Transaction.RejectionReason.INSUFFICIENT_FUNDS);
        }
        return PolicyResult.allow();
    }

    /**
     * checkMaxTransaction (فحص الحد الأعلى للعملية الواحدة)
     * - amount > maxTransactionAmount => رفض
     */
    public PolicyResult checkMaxTransaction(long amount) {
        if (amount > maxTransactionAmount) {
            return PolicyResult.reject(Transaction.RejectionReason.MAX_TX_EXCEEDED);
        }
        return PolicyResult.allow();
    }

    /**
     * checkDailyLimit
     * فحص الحدّ اليومي للعمليات الصادرة من حساب معيّن.
     * - نحسب مجموع عمليات اليوم لهذا الحساب.
     * - إذا (المجموع + المبلغ الجديد) أكبر من الحدّ اليومي → رفض.
     */
    public PolicyResult checkDailyLimit(String accountId, long amount) {
        long todayTotal =
                transactionDao.getDailyTotalForAccount(accountId, java.time.LocalDate.now());
        //if(todayTotal > dailyLimit - amount)-->لمسة احترافية صامتة يمنع overflow لو ارقامك كبيرة
        if (todayTotal > dailyLimit - amount) {
            return PolicyResult.reject(Transaction.RejectionReason.DAILY_LIMIT_EXCEEDED);
        }
        return PolicyResult.allow();
    }

    // ===== Validation Flows (تجميع القواعد حسب نوع العملية) =====

    public PolicyResult validateWithdraw(Account account, long amount) {
        PolicyResult stateResult = checkState(account);
        if (!stateResult.isAllowed()) return stateResult;

        PolicyResult amountResult = checkAmount(amount);
        if (!amountResult.isAllowed()) return amountResult;

        PolicyResult maxResult = checkMaxTransaction(amount);
        if (!maxResult.isAllowed()) return maxResult;

        PolicyResult fundsResult = checkSufficientFunds(account.getBalance(), amount);
        if (!fundsResult.isAllowed()) return fundsResult;

        PolicyResult dailyResult = checkDailyLimit(account.getId(), amount);
        if (!dailyResult.isAllowed()) return dailyResult;

        return PolicyResult.allow();
    }

    public PolicyResult validateDeposit(Account account, long amount) {
        PolicyResult stateResult = checkState(account);
        if (!stateResult.isAllowed()) return stateResult;

        PolicyResult amountResult = checkAmount(amount);
        if (!amountResult.isAllowed()) return amountResult;

        PolicyResult maxResult = checkMaxTransaction(amount);
        if (!maxResult.isAllowed()) return maxResult;

        return PolicyResult.allow();
    }

    public PolicyResult validateTransfer(Account fromAccount, Account toAccount, long amount) {
        // أرخص فحص أولاً: منع التحويل لنفس الحساب
        if (fromAccount.getId().equals(toAccount.getId())) {
            return PolicyResult.reject(Transaction.RejectionReason.SAME_ACCOUNT_TRANSFER);
        }

        PolicyResult fromStateResult = checkState(fromAccount);
        if (!fromStateResult.isAllowed()) return fromStateResult;

        PolicyResult toStateResult = checkState(toAccount);
        if (!toStateResult.isAllowed()) return toStateResult;

        PolicyResult amountResult = checkAmount(amount);
        if (!amountResult.isAllowed()) return amountResult;

        PolicyResult maxResult = checkMaxTransaction(amount);
        if (!maxResult.isAllowed()) return maxResult;

        // الرصيد يُفحص فقط على الحساب المصدر
        PolicyResult fundsResult = checkSufficientFunds(fromAccount.getBalance(), amount);
        if (!fundsResult.isAllowed()) return fundsResult;

        PolicyResult dailyResult = checkDailyLimit(fromAccount.getId(), amount);
        if (!dailyResult.isAllowed()) return dailyResult;

        return PolicyResult.allow();
    }
}
