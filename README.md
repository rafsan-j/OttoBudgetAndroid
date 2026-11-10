# OttoBudget V8 — Minimalist Offline-First Expense, Income, Recurring & Savings Vault Tracker

**OttoBudget** is a fast, privacy-first native Android personal finance application built with **Kotlin**, **Jetpack Compose (Material 3)**, **AndroidX Biometric**, and **Room SQLite**.

## Key Features in V8
- **Separate PIN/Biometric Savings Vault with Spendable Wallet Subtraction**:
  - Create dedicated savings goals (e.g., *"New iPhone"*, *"Gift for Parents"*, *"Emergency Fund"*) with **per-wallet criteria** (Allowed Wallets & Minimum Spendable Wallet Floor protection).
  - Assigning money from `Cash`, `bKash`, or `Bank Card` into a Vault goal **immediately subtracts that amount from your visible spendable wallet balance** so you never accidentally spend earmarked savings—without inflating your Monthly Expense Cap.
  - Accessible via the **Spendable | Savings Vault** switcher on the Wallets screen or via a **stealth long-press on the Home Total Balance card**, protected by your 4-digit iOS Passcode and Device Biometrics when Vault Lock is enabled.
- **Confirmation-Based Recurring Expenses & Income with Scheduled Notifications**:
  - Schedule recurring bills (e.g., *House Rent*, *WiFi*) or regular income (*Part-Time Job*, *Tuition*) for any day of the month and exact notification time (`HH:MM`).
  - Balances are **never** added or subtracted automatically—on the due date, a local notification fires and a **Pending Recurring Confirmation** banner appears on the Home screen waiting for your 1-tap confirmation.
- **Opt-In On-Device SMS & Notification Auto-Parser (bKash, Nagad, Rocket & BD Banks)**:
  - 100% offline regex parser (`MfsSmsNotificationParser`) detects incoming transaction SMS/notifications (extracting Amount, Fee, `TrxID`, and Wallet) and displays a 1-tap **"Log ৳250 from bKash?"** confirmation pill on the Home screen.
- **Custom Date Picker in Quick-Add**:
  - Log forgotten past expenses, income, or transfers for any date using 1-tap `Today`, `Yesterday`, `2 Days Ago`, or the full Calendar Date Picker dialog.
- **Toggleable Monthly Cap**:
  - Enable or disable the Monthly Budget Cap ceiling and progress bar anytime in Settings.
- **Clean Matte-Frosted Navigation Dock (Zero Border Glow / Zero Horizontal Line)**:
  - Refined floating dock with uniform matte hairline borders, transparent system navigation bar integration, and multi-part animated vector icons.

## Building from Source
1. Clone this repository and open the root folder in **Android Studio**.
2. Let Gradle sync dependencies automatically (JDK 17, AGP 8.7.2, Kotlin 2.0.21).
3. Run on an Android device or emulator (`minSdk 26`, `targetSdk 35`).
