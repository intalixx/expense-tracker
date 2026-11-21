# Expense Tracker

A private, offline expense tracker for Android, built for Indian users. It keeps your money in a few clear places (accounts, categories, budgets) and fills most of it in for you by reading bank SMS and UPI app notifications on the phone.

**Expense Tracker is for India only, for now.** It works with Indian rupees (₹) only, not other currencies, and it understands Indian bank SMS and UPI apps (see [supported.md](supported.md)).

**The app has no internet permission. Nothing ever leaves your device.**

## What it does

- **Records** – a monthly list of income and expenses grouped by day, with a quiet income / expense / balance summary. Add or edit an entry with a built-in calculator, an account picker (showing each account's balance) and a category grid.
- **Automatic entries**
  - Reads bank debit/credit SMS and turns them into records. Anything it is unsure about waits in a **review list** for you to confirm.
  - Optionally watches payment-app and bank-app notifications (see [supported.md](supported.md) for the banks and apps covered), which helps when an SMS is late or never arrives. Payment-app notifications also carry the note the sender typed, which is saved with the entry as `From X (Note: …)`.
  - **No double counting.** The same payment can arrive as an SMS, a payment-app notification and a bank-app notification, in any order. They are merged into one record, and a later message only adds the missing note or corrects the account.
  - **People & payees lookup** (Settings). Every person or shop you paid or received from is listed. Pick a category once (e.g. a person → Salary) and every later payment goes there, optionally with no review; past entries in “Other” are moved too.
  - Remembers account choices, can scan existing messages (30 / 60 / 90 days / all time) and works in the background without the app open.
- **Analysis** – two dropdown filters: a period (Daily, Weekly, Monthly, 3 Months, 6 Months, Yearly) and a view (Expense overview, Income overview, Expense flow, Income flow).
- **Budgets, Accounts, Categories** – set limits, track balances per account, and manage your own categories.
- **Backup** – export and import a backup file. Importing replaces all current data.
- **Security** – optional 4-digit passcode.

## Privacy

- No `INTERNET` permission, so the app cannot send data anywhere.
- SMS and notification access are optional, and each can be switched off in Settings.
- Notification access is a separate Android setting. The app explains why and asks once.

## Build

Requirements: Android SDK, JDK 17+, Gradle (or Android Studio). The commands below are the same on Windows, macOS and Linux.

```
gradle :app:testDebugUnitTest :app:assembleDebug
gradle :app:assembleRelease
```

If `gradle` isn't on your PATH, use the helper for your OS instead (same arguments): `bash ./run_gradle.sh ...` on macOS/Linux/Git Bash, or `.un_gradle.ps1 ...` in Windows PowerShell. Both expect Gradle 9.3.1 in `~/.gradle` and Android Studio's bundled JDK; edit the paths at the top if yours differ.

Details for contributors and tooling are in [CLAUDE.md](CLAUDE.md).

## Tech

Kotlin, Jetpack Compose (Material 3), Room, a single `app` module. `minSdk` 26, `targetSdk` 35. The SMS, notification, calculator and period logic is plain Kotlin with JUnit tests.

## Contributing

Banks send their messages in different wordings. Adding support for another bank (SMS and app notifications) is a small, self-contained change: one file per bank plus some dummy-data samples. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Licensed under the [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0). If you modify this app and distribute it or let others use it over a network, you must make your modified source available under the same license.
