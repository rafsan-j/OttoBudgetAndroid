# OttoBudget V9 — Minimalist Offline-First Expense, Income, Scheduler & Savings Vault Tracker

**OttoBudget** is a fast, privacy-first native Android personal finance application built with **Kotlin**, **Jetpack Compose (Material 3)**, **AndroidX Biometric**, and **Room SQLite**.

## Key Features in V9
- **Seamless Transparent-Background Winking Otto Splash**:
  - Uses a pure-white transparent-background silhouette (`otto_v9_transparent.webp`) over a full-screen `#6BD0C2` turquoise canvas with zero square borders, animating Otto's right eye into a smooth wink on launch.
- **Unified `#6BD0C2` Otto Turquoise Signature Theme (Light & Dark Mode)**:
  - Light Mode pairs crisp white cards with the signature `#6BD0C2` Otto Turquoise accent palette, while Dark Mode pairs deep obsidian surfaces with `#6BD0C2`—persisting your theme choice across app restarts.
- **Zero-API Conversational AI-Chat Onboarding & Interactive Spotlight Tour**:
  - Otto interviews you through an animated chat interface (with bouncing typing indicators, quick-select pills, custom text input, progress bar, and `"skip"` support) and guides you through a 4-step animated spotlight tour.
  - Supports optional **Zero-Database Telemetry Forwarding** via Google Forms / Google Apps Script or Discord/Telegram webhooks.
- **Redesigned Wallets Screen (`Available | Total` Pills + Floating Vault Button)**:
  - Switch between **Available** (net spendable balance after Vault deductions) and **Total** (actual full balance in the wallet) at the top of Wallets, with clean `Balance • <Wallet>` card headers and a **Floating Savings Vault Button** at the bottom-right.
  - Every Vault Goal is 100% editable (rename, change icon, adjust target amount, edit wallet criteria, and directly adjust assigned amounts per wallet).
- **Press & Hold to Edit Past Transactions + Delete Confirmation & Undo**:
  - Long-press any transaction on Home or Ledger to edit its Type, Amount, Description, Category, Wallet, or Date.
  - Deleting any item requires confirmation and pops up an **Undo** snackbar to restore accidental deletions.
- **Floating Notification / SMS Alert Button on Home**:
  - Appears at the bottom-right of the Home screen *only* when new on-device bKash/Nagad/Rocket/Bank SMS alerts or due **Scheduler** items are waiting for your confirmation.

## Building from Source
1. Clone this repository and open the root folder in **Android Studio**.
2. Let Gradle sync dependencies automatically (JDK 17, AGP 8.7.2, Kotlin 2.0.21).
3. Run on an Android device or emulator (`minSdk 26`, `targetSdk 35`).
