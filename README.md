# 🏦 Enterprise Banking Core (JDBC)

![Java](https://img.shields.io/badge/Java-17-blue.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0-orange.svg)
![Maven](https://img.shields.io/badge/Maven-3.3-C71A36.svg)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

*A robust, terminal-based backend engine built from scratch using Java and Raw JDBC to demonstrate database infrastructure, transaction management, and data integrity.*

---

## 🔄 Architecture Evolution (From V1 to V2)
> **⚠️ Note to Reviewers:** This project represents a major architectural upgrade from my initial **[V1 In-Memory Bank System](https://github.com/smual-ibraheem/bank-account-system)**.
>
> While V1 successfully established the core domain logic and strict validation policies, this V2 release tackles real-world infrastructure challenges. I deliberately separated the Data Access Layer to transition from volatile memory to persistent MySQL, bridging the gap between raw business logic and enterprise-grade data integrity.

---

## 📌 Overview
**Enterprise Banking Core** is intentionally developed without modern ORMs (like Hibernate) to demonstrate a deep, foundational understanding of SQL optimization, database infrastructure, and ACID compliance.

It simulates a real-world financial core where partial executions are fatal, enforcing strict architectural rules to guarantee that no funds are lost during catastrophic failures.

---

## 🏗️ Architectural Highlights & Concepts Mastered

- ✔️ **ACID Transactions (Manual Control):** Implemented the `Connection Passing Pattern` to bind multiple DAO operations (deduct, add, log) into a single atomic session. Utilizing `conn.setAutoCommit(false)`, `commit()`, and `rollback()` to prevent the "Partial Execution Trap."
- ✔️ **Optimistic Locking:** Engineered concurrency control using a `version` column to prevent Lost Updates when multiple threads attempt to modify the same bank account simultaneously.
- ✔️ **Separation of Concerns (SoC):** Strictly segregated business logic (`Bank` Service) from database access (`DAO` Pattern), ensuring high maintainability and testability.
- ✔️ **Smart Configuration Hierarchy:** Developed a fail-safe environment variable injection system (`getEnvOrDefault`). The system securely reads from CI/CD pipelines (GitHub Actions) while seamlessly falling back to local credentials for frictionless developer onboarding.
- ✔️ **Defensive Programming:** Extensive use of Guard Clauses to filter out illegal operations (e.g., zero/negative amounts, phantom accounts) before they ever reach the database layer.

---

## ⚙️ Tech Stack
* **Language:** Java 17
* **Database:** MySQL 8.0
* **Build Tool:** Apache Maven
* **Testing:** JUnit 5 (Automated Integration Testing)
* **CI/CD:** GitHub Actions

---

## 🧪 Testing Strategy (TDD Approach)
The `src/test` directory contains a comprehensive suite of automated integration tests that interact with a live test database.
* **Test Isolation:** Each test generates unique dummy data (e.g., `TEST-UUID`) and rigorously tears it down post-execution, respecting strict Foreign Key constraints.
* **Reflection for Edge Cases:** Strategically utilized Java Reflection API to manipulate private fields and force edge-case scenarios (like simulating concurrent balance modifications) without breaking production encapsulation.

---

## 🚀 Quick Start

### 1. Database Setup
Execute the following schema in your local MySQL instance:
```sql
CREATE DATABASE bank_system;
USE bank_system;

CREATE TABLE accounts (
    id VARCHAR(36) PRIMARY KEY,
    owner_name VARCHAR(100) NOT NULL,
    balance BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE transactions (
    id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    timestamp DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(50),
    from_account_id VARCHAR(36),
    to_account_id VARCHAR(36),
    FOREIGN KEY (from_account_id) REFERENCES accounts(id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(id)
);