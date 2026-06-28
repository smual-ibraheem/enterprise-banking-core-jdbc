package bank.core;

import bank.state.AccountState;

public class Account {

    // ===== Identity (هوية الحساب) =====
    private final String id;
    private final String ownerName;

    // ===== Mutable State (حالة قابلة للتغيير – فقط عبر النظام) =====
    private long balance;
    private AccountState state;
    private int version; // حقل خاص بحماية التزامن (Optimistic Locking)

    // ===== Constructor (باني) =====
    public Account(final String id, final String ownerName, final long initialBalance) {
        this.id = id;
        this.ownerName = ownerName;
        this.balance = initialBalance;
        this.state = AccountState.ACTIVE; // الحساب يبدأ نشط
        this.version = 0; // أي حساب جديد يبدأ نسخته من صفر
    }

    // ===== Reconstitution Constructor (يُستخدم فقط من قبل الداتابيز أو إطارات العمل) =====
    public Account(String id, String ownerName, long balance, AccountState state, int version) {
        this.id = id;
        this.ownerName = ownerName;
        this.balance = balance;
        this.state = state;
        this.version = version;
    }

    // ===== Getters (قراءة فقط) =====
    public String getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getBalance() {
        return balance;
    }

    public AccountState getState() {
        return state;
    }

    public int getVersion() { return version; }

    // ===== Package-Private Mutators (تغيير داخلي فقط) =====
    // تستخدم فقط من Bank (النظام)

    void setBalance(long balance) {
        this.balance = balance;
    }

    void setState(AccountState state) {
        this.state = state;
    }

    public void setVersion(int version) { this.version = version; }
}