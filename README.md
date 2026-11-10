# OttoBudget — Minimalist Offline-First Expense, Income & Wallet Tracker

**OttoBudget** is a fast, privacy-first native Android personal finance application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Room SQLite**.

## Key Features
- **Winking Otto Splash & Adaptive Icon**: Launches with a smooth winking animation of **Otto** and includes a full-bleed `#6BD0C2` Android Adaptive Icon that fits circular, squircle, teardrop, and square launchers identically.
- **Zero-AI "Magic Words" Auto-Categorization**: Pre-loaded with 200+ generalized keywords across Expense and Income categories. Typing a description automatically selects the matching category in `<1ms` offline, with a dedicated **Categories & Magic Words** studio to add, edit, or delete categories and trigger words.
- **Smart Transit Pass Lock & Overdraft Guard**:
  - Selecting **Transit Pass / Rapid Pass** automatically locks the expense category to **Transport** and grays out other categories.
  - Selecting or saving an expense on a wallet with insufficient funds immediately triggers an **Insufficient Balance Alert** popup.
- **Sculptural Provider-Colored Wallets**: Automatic brand palettes for **bKash** (Pink/Magenta), **Nagad** (Orange), **Rocket** (Purple), **Cash** (Acid Lime), **Bank Card** (Obsidian), **Transit Pass** (Blue), and **Savings** (Emerald), plus a 1-tap circular color swatch editor and direct balance editor.
- **Zero-Schema-Exposure Cloud Sync & Local Backup**:
  - Dedicated **Settings / Backup & Cloud Sync** screen.
  - Packages and obfuscates your ledger into an encrypted single-blob vault (`otto_vault_v6.dat`) and auto-provisions a private Supabase Storage bucket via REST—requiring **zero SQL schema files** and never exposing internal database structure.
  - Full local **Export CSV/JSON**, **Import Backup**, and **Clear Data** support.

## Building from Source
1. Clone this repository and open the root folder in **Android Studio**.
2. Let Gradle sync dependencies automatically (JDK 17, AGP 8.7.2, Kotlin 2.0.21).
3. Run on an Android device or emulator (`minSdk 26`, `targetSdk 35`).
