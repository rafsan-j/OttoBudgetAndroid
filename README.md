# OttoBudget V7 — Minimalist Offline-First Expense, Income & Wallet Tracker

**OttoBudget** is a fast, privacy-first native Android personal finance application built with **Kotlin**, **Jetpack Compose (Material 3)**, **AndroidX Biometric**, and **Room SQLite**.

## Key Features in V7
- **Winking Otto Splash & Full-Bleed Adaptive Icon**: Launches with a smooth winking animation of **Otto** and includes a full-bleed `#6BD0C2` Android Adaptive Icon that fits circular, squircle, teardrop, and square launchers identically.
- **Persistent Dark Mode with `#6BD0C2` Turquoise Palette & Sun-to-Moon Morph**:
  - Toggling between Light Mode and Dark Mode morphs the top-right theme button between a **Sun** icon and a **Crescent Half-Moon** icon with spring rotation physics.
  - Dark Mode uses the signature **Otto Turquoise (`#6BD0C2`)** accent color (no yellow) and automatically persists your theme preference across app restarts.
- **iOS-Style Lock Screen PIN & Device Biometric Security**:
  - Protect your financial data with a 4-digit passcode featuring an iOS Lock Screen-inspired frosted circular keypad, spring-animated passcode dots, horizontal error shake feedback, and native **Fingerprint / Face Unlock** via `androidx.biometric.BiometricPrompt`.
- **iOS Glassmorphic Floating Navigation Bar & Animated Icons**:
  - Translucent frosted glassmorphic dock with a specular top border, sliding frosted pill indicator (no bottom dot), and interactive multi-part vector animations (`itshover.com` style) across Home, Ledger, Quick-Add, Wallets, and Profile/Settings.
- **Fixed-Bounds 7D Spend / Income / Line Analytics**:
  - Strictly locked card and chart container dimensions (`238dp` card / `148dp` chart viewport) so switching between `Out`, `In`, and `Line` or tapping any day of the week never shifts surrounding layout elements.
- **Built-In FAQ & Guide**:
  - Expandable **Settings / FAQ & Help** section answering common questions including *"What is the Monthly Cap?"*, *"How does Zero-Schema Cloud Sync work?"*, *"How do Magic Words work?"*, and *"Why does Transit Pass lock the category to Transport?"*.
- **Zero-AI "Magic Words" Auto-Categorization**:
  - Pre-loaded with 200+ generalized keywords across Expense and Income categories. Typing a description automatically selects the matching category in `<1ms` offline, with a dedicated **Categories & Magic Words** studio to add, edit, or delete categories and trigger words.
- **Zero-Schema-Exposure Cloud Sync & Local Backup**:
  - Packages and encrypts your ledger into an AES-256-GCM single-blob vault (`otto_vault/vault.enc`) inside an auto-provisioned private Supabase Storage bucket—requiring **zero SQL schema files** and never exposing internal database structure.

## Building from Source
1. Clone this repository and open the root folder in **Android Studio**.
2. Let Gradle sync dependencies automatically (JDK 17, AGP 8.7.2, Kotlin 2.0.21).
3. Run on an Android device or emulator (`minSdk 26`, `targetSdk 35`).
