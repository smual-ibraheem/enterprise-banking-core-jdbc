-- 1. إنشاء قاعدة البيانات (إن لم تكن موجودة) واستخدامها
CREATE DATABASE IF NOT EXISTS bank_system;
USE bank_system;

-- 2. إنشاء جدول الحسابات
CREATE TABLE accounts (
    id VARCHAR(36) PRIMARY KEY,
    owner_name VARCHAR(100) NOT NULL,
    balance BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL, -- سيحتوي على: ACTIVE, FROZEN, CLOSED
    version INT NOT NULL DEFAULT 0, -- السلاح السري: Optimistic Locking لمنع التزامن
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- جدار الحماية الأول: يمنع الرصيد السالب نهائياً
    CONSTRAINT chk_balance_positive CHECK (balance >= 0)
);

-- 3. إنشاء جدول العمليات (سجل تاريخي لا يُعدل)
CREATE TABLE transactions (
    id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(20) NOT NULL, -- DEPOSIT, WITHDRAW, TRANSFER
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL, -- SUCCESS, REJECTED
    rejection_reason VARCHAR(50), 
    from_account_id VARCHAR(36),
    to_account_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- جدار الحماية الثاني: يمنع العمليات الصفرية أو السالبة
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    
    -- ربط العمليات بالحسابات لضمان عدم وجود تحويل لحساب وهمي
    FOREIGN KEY (from_account_id) REFERENCES accounts(id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(id)
);