package bank;

import bank.core.Account;
import bank.core.Bank;
import bank.core.Transaction;

import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        // إعدادات البنك
        long maxTransactionAmount = 1_000_000;
        long dailyLimit = 5_000_000;

        Bank bank = new Bank(maxTransactionAmount, dailyLimit);
        Scanner scanner = new Scanner(System.in);

        // تم حذف الـ Seed Data لأن البيانات الآن تُحفظ في الداتابيز بشكل دائم

        boolean running = true;

        while (running) {
            printMenu();

            int choice = readIntOrRetry(scanner, "Choose option: ");
            if (choice == -1) {
                continue;
            }

            switch (choice) {
                case 1 -> createAccount(bank, scanner);
                case 2 -> showAccountDetails(bank, scanner);
                case 3 -> deposit(bank, scanner);
                case 4 -> withdraw(bank, scanner);
                case 5 -> transfer(bank, scanner);
                case 6 -> {
                    running = false;
                    System.out.println("Goodbye! Disconnected from Database.");
                }
                default -> System.out.println("Invalid option. Try again.");
            }
        }
        scanner.close();
    }

    // =========================
    // Menu (UI only)
    // =========================
    private static void printMenu() {
        System.out.println("\n=========== BANK SYSTEM (DB CONNECTED) ===========");
        System.out.println("1) Create Account");
        System.out.println("2) Account Details & Statement");
        System.out.println("3) Deposit");
        System.out.println("4) Withdraw");
        System.out.println("5) Transfer");
        System.out.println("6) Exit");
        System.out.println("--------------------------------------------------");
    }

    // =========================
    // Create Account
    // =========================
    private static void createAccount(Bank bank, Scanner scanner) {
        System.out.print("Enter owner name: ");
        String ownerName = scanner.nextLine().trim();

        Long initialBalance = readLongOrNull(scanner, "Enter initial balance (Enter for 0): ", true);
        if (initialBalance == null) {
            System.out.println("Invalid amount. Initial balance set to 0.");
            initialBalance = 0L;
        }

        try {
            Account account = bank.createAccount(ownerName, initialBalance);
            System.out.println("\n✅ Account created and saved to Database successfully.");
            System.out.println("Account ID: " + account.getId());
            System.out.println("Owner     : " + account.getOwnerName());
            System.out.println("Balance   : " + account.getBalance());
        } catch (IllegalArgumentException e) {
            System.out.println("❌ Account creation failed: " + e.getMessage());
        }
    }

    // =========================
    // Account Details
    // =========================
    private static void showAccountDetails(Bank bank, Scanner scanner) {
        System.out.print("Enter account ID: ");
        String accountId = scanner.nextLine().trim();

        if (accountId.isEmpty()) {
            System.out.println("Account ID cannot be empty.");
            return;
        }

        Account account = bank.getAccount(accountId);
        if (account == null) {
            System.out.println("❌ Account not found in Database.");
            return;
        }

        int limit = 5;
        Integer userLimit = readIntWithDefault(scanner, "How many recent transactions to show? (Enter for 5): ", 5);

        if (userLimit < 1 || userLimit > 50) {
            System.out.println("Limit must be between 1 and 50. Using 5.");
            limit = 5;
        } else {
            limit = userLimit;
        }

        System.out.println("\n--- Account Details ---");
        System.out.println("ID      : " + account.getId());
        System.out.println("Owner   : " + account.getOwnerName());
        System.out.println("Balance : " + account.getBalance());
        System.out.println("State   : " + account.getState());
        System.out.println("Version : " + account.getVersion() + " (Optimistic Lock Tracker)");

        List<Transaction> recent = bank.getRecentTransactions(accountId, limit);

        System.out.println("\n--- Recent Transactions (max " + limit + ") ---");
        if (recent.isEmpty()) {
            System.out.println("No transactions found.");
            return;
        }

        for (Transaction tx : recent) {
            String line = String.format("%s | %s | Amount: %d | Status: %s",
                    tx.getTimestamp(), tx.getType(), tx.getAmount(), tx.getStatus());

            if (tx.getStatus() == Transaction.TransactionStatus.REJECTED) {
                line += " | Reason: " + tx.getRejectionReason();
            }

            if (tx.getType() == Transaction.TransactionType.TRANSFER) {
                line += String.format(" | From: %s -> To: %s", tx.getFromAccountId(), tx.getToAccountId());
            }
            System.out.println(line);
        }
    }

    // =========================
    // Operations
    // =========================
    private static void deposit(Bank bank, Scanner scanner) {
        System.out.print("Enter account ID: ");
        String accountId = scanner.nextLine().trim();

        if (accountId.isEmpty()) return;

        Long amount = readLongOrNull(scanner, "Enter amount to deposit: ", false);
        if (amount == null || amount <= 0) {
            System.out.println("Amount must be a positive number.");
            return;
        }

        Transaction tx = bank.deposit(accountId, amount);
        printTransactionResult(tx);
    }

    private static void withdraw(Bank bank, Scanner scanner) {
        System.out.print("Enter account ID: ");
        String accountId = scanner.nextLine().trim();

        if (accountId.isEmpty()) return;

        Long amount = readLongOrNull(scanner, "Enter amount to withdraw: ", false);
        if (amount == null || amount <= 0) {
            System.out.println("Amount must be a positive number.");
            return;
        }

        Transaction tx = bank.withdraw(accountId, amount);
        printTransactionResult(tx);
    }

    private static void transfer(Bank bank, Scanner scanner) {
        System.out.print("Enter FROM account ID: ");
        String fromId = scanner.nextLine().trim();
        if (fromId.isEmpty()) return;

        System.out.print("Enter TO account ID: ");
        String toId = scanner.nextLine().trim();
        if (toId.isEmpty()) return;

        if (fromId.equals(toId)) {
            System.out.println("Cannot transfer to the same account.");
            return;
        }

        Long amount = readLongOrNull(scanner, "Enter amount to transfer: ", false);
        if (amount == null || amount <= 0) {
            System.out.println("Amount must be a positive number.");
            return;
        }

        Transaction tx = bank.transfer(fromId, toId, amount);
        printTransactionResult(tx);
    }

    // =========================
    // Transaction Result
    // =========================
    private static void printTransactionResult(Transaction tx) {
        System.out.println("\n--- Transaction Result ---");
        System.out.println("Transaction ID: " + tx.getId());
        System.out.println("Type          : " + tx.getType());
        System.out.println("Amount        : " + tx.getAmount());
        System.out.println("Status        : " + tx.getStatus());

        if (tx.getStatus() == Transaction.TransactionStatus.REJECTED) {
            System.out.println("Rejection     : " + tx.getRejectionReason());
        } else {
            System.out.println("✅ Database   : Committed Successfully (ACID)");
        }

        if (tx.getType() == Transaction.TransactionType.TRANSFER) {
            System.out.println("From Account  : " + tx.getFromAccountId());
            System.out.println("To Account    : " + tx.getToAccountId());
        }
    }

    // =========================
    // Input Helpers
    // =========================
    private static int readIntOrRetry(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Invalid option. Please enter a number.");
            return -1;
        }
    }

    private static Integer readIntWithDefault(Scanner scanner, String prompt, int defaultValue) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number. Using " + defaultValue + ".");
            return defaultValue;
        }
    }

    private static Long readLongOrNull(Scanner scanner, String prompt, boolean allowEmpty) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (allowEmpty && input.isEmpty()) return 0L;
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}