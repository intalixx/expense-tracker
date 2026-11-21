# Expense Tracker (Android)

Personal, offline expense tracker for Indian users. Native Android, Kotlin + Jetpack Compose, single `app` module.
- Package / applicationId: `com.intalix.expense.tracker`
- Source: `app/src/main/java/com/intalix/expense/tracker/`
- Tests: `app/src/test/java/com/intalix/expense/tracker/` (JUnit, pure-Kotlin: `CalcTest`, `PeriodTest`, SMS parser tests)
- Money is stored as integer paise (`amountPaise`); record `type`: 1 = income, 2 = expense.

## Code map
| File | What it holds |
|---|---|
| `MainActivity.kt`, `Screens.kt` | Navigation and tab screens (Analysis = `StatsScreen`, Budgets, Accounts, Categories) |
| `Records.kt` | Records tab (month bar, quiet income/expense/balance row, grouped list) |
| `Entry.kt` | Add/edit entry screen: calculator, account picker (shows balances), category grid |
| `Period.kt` | `Period` enum + `Periods` helpers (span / shift / label / buckets) for Analysis filters |
| `Common.kt` | Shared widgets: `Chip`, `ChipRow`, `Hairline`, `OutBox`, `Caps`, `PickDropdown` (compact pill dropdown) |
| `PayeesScreen.kt`, `Payees.kt` | Settings -> People & payees: payee list + "payee -> category" rule (`Payee` entity in `Data.kt`, keyed by `merchantKey(name)` + direction). `Ingest.process` applies the rule; `Payees.setRule` can move past entries. DB is v2 (`MIGRATION_1_2` turns old `merchant_rules` into `payees`) |
| `Vm.kt`, `Data.kt` | ViewModel, Room entities/DAO |
| `parse/` (`Template.kt`, `Registry.kt`, `banks/<Bank>.kt`) | Per-bank message templates (sentence + `{placeholders}`); `Banks.all` is the registry. Samples: `app/src/test/resources/samples/*.txt` |
| `SmsParser.kt`, `Classifier.kt`, `Ingest.kt`, `Receivers.kt` | SMS / notification parsing -> auto-added entries or review list; `Prefs` lives in `Ingest.kt` |
| `Mbak.kt` | Import of MyMoney (`.mbak`) backups |
| `Calc.kt` | Calculator expression evaluator used by Entry |

## Build / test
Run from the project root. The commands are the same on Windows, macOS and Linux; add `--offline` if you have no network and `-q` for quiet output:
```
gradle :app:testDebugUnitTest :app:assembleDebug
gradle :app:assembleRelease
```
With `-q` nothing prints on success. Test results: `app/build/test-results/testDebugUnitTest/*.xml`.
- Optional helpers that set `JAVA_HOME`/`ANDROID_HOME` and call the cached Gradle, same args as `gradle`: `bash ./run_gradle.sh ...` (macOS/Linux/Git Bash) or `.un_gradle.ps1 ...` (Windows PowerShell). Use one on this machine if plain `gradle` isn't on the PATH.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk` (minified, signed with `signing/expense-release.jks` via `signing/keystore.properties`; never commit or print those).

## Emulator
- SDK: standard Android SDK (`$ANDROID_HOME` is set). adb: `$ANDROID_HOME/platform-tools/adb.exe`
- AVD: `Pixel_2` (`~/.android/avd/Pixel_2.avd`). Start it in the background:
  ```
  "$ANDROID_HOME/emulator/emulator.exe" -avd Pixel_2 -no-snapshot-save > "$TEMP/emu.log" 2>&1 &
  ```
  Then `adb wait-for-device` and poll `adb shell getprop sys.boot_completed` until it prints `1`.
- **Install the RELEASE APK, not debug.** The app already on the emulator is signed with the release key, so installing the debug APK fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. `adb install -r app/build/outputs/apk/release/app-release.apk` updates in place and keeps the data. Do not `adb uninstall` (it wipes the emulator's test data) unless the user agrees.
- Launch: `adb shell monkey -p com.intalix.expense.tracker -c android.intent.category.LAUNCHER 1`
- Screenshot: `adb exec-out screencap -p > "$TEMP/shots2/x.png"` then view it with the Read tool. Scratch screenshots live in `$TEMP/shots` / `$TEMP/shots2`.

## Sending a test SMS (emulator only)
```
adb emu sms send HDFCBK "Rs.150.00 debited from a/c XX1234 on 19-11-26 to VPA zomato@icici (UPI Ref No 600000000001). Not you? Call 18002586161"
adb emu sms send HDFCBK "Rs.640.00 credited to a/c XX1234 on 19-11-26 by VPA icon@upi (UPI Ref No 600000000002)"
```
- Prints `OK`. The app needs SMS permission granted (and "auto add" on in Settings) to create the entry; otherwise it lands in the review list.
- Use a fresh UPI ref number each time (duplicates are de-duplicated by reference).
- OTP/promo/failed/"will be debited" texts are intentionally ignored by `SmsParser` — good negative tests.
- Mark test data as such; the emulator DB is not the user's real data, but still avoid wiping it.

## MyMoney reference files
- The original MyMoney app APK was used only as a design reference for the Analysis filters. It is not part of this repo; pull it with `adb pull` from a phone that has MyMoney installed if needed.
- Backup-analysis scratch: `$TEMP/an/dec.py` (decoder), `$TEMP/an/data2.json` (decoded backup), `$TEMP/an/payees.tsv`.
- Confirmed from the APK: period modes Daily / Weekly / Monthly / 3 Months / 6 Months / Yearly, and a flow chart layout. Nothing more detailed was extracted.

## Design conventions
- Quiet UI: no heavy borders/boxes for secondary info (e.g. the Records summary is a single borderless row of small text).
- Theme colours come from `pal` (`pal.income`, `pal.expense`, `pal.soft`, `pal.note`); reuse `Hairline`, `Chip`, `ChipRow` from `Common.kt` instead of new widgets.
- Analysis tab keeps its own period/anchor state; it does not use the shared month selector (`vm.month`). Its filters are two side-by-side `PickDropdown`s (period: Daily/Weekly/Monthly/3 Months/6 Months/Yearly; view: Expense overview/Income overview/Expense flow/Income flow) — not chip rows.
- Records summary: label above, value below, three equal-weight columns, single line with ellipsis so long amounts can't overlap.
- Entry screen selectors have no "Account"/"Category" captions; the account selector shows only its balance above the name. Both selectors share a min height (60dp).

## Working rules
- **Android regex is stricter than the desktop JVM** (unit tests run on the JVM): escape every literal `}` and `{` in a `Regex`, e.g. `\{(\w+)\}`. A bare `}` passes the unit tests but crashes the app at start. After any regex/template change, launch the app on the emulator, not just the tests.
- Kotak SMS may carry only the receiver's name (no `to`, no UPI id); `parse/banks/Kotak.kt` has a template for it. Payee rules key on names as well as UPI ids.
- After UI changes: build, install the release APK on the emulator, and screenshot to verify before reporting done.
- Run the unit tests after changing `SmsParser`, `Calc`, or `Period`.
- This folder is a git repository (branch `main`). Commit only when the user asks. `.gitignore` already excludes `signing/`, keystores, APKs, `build/` and `local.properties`.

## Notification ingest (payment apps; supported list in supported.md)
- Payment apps live in `parse/PaymentApp.kt` (`PaymentApp(name, packages, notif, reject)`), one file per app in `parse/apps/`, registered in `PaymentApps.all` (`parse/Registry.kt`). `Watched.packages` = payment-app packages + bank-app packages and is what the listener filters on. To enable PhonePe/BHIM/supermoney etc.: add a file + one line (see CONTRIBUTING.md "Adding a payment app"). User said only GPay for now; others wait for notification screenshots.
- `PaymentNotificationListener` (Receivers.kt) reads notifications from every package in `Watched.packages` when "Watch payment apps" is on and notification access is granted. It joins all text lines (title, text, bigText, subText, summary, textLines) and skips `MessagingStyle` (chat) notifications so typed chat text can't fake a payment.
- `NotificationParser` (own parser, not `SmsParser`: "X sent you ₹5" is a credit) returns `ParsedTxn` incl. `userNote` (the note the payer typed). Real GPay wording is NOT verified against live notifications; unknown wording is skipped, payee-less payments go to the review list.
- `Ingest.process` runs under a `Mutex`. Entry note = "To X · note" / "From X · note" (`composeNote`; `recategorize` uses `substringBefore(" · ")`).
- No double count: a later SMS merges into an unmatched notification Rec (same amount+type, within 30 min, `findUnmatchedNotifRec`) by attaching its reference; a notification after an SMS is caught by `findNear`/`findPendingNear`.
- adb can't post notifications as a watched package, so this path is covered by `NotificationParserTest` only; the merge logic has no automated test.
- Late SMS: an SMS within 30 min of a notification entry merges into it automatically. 30 min - 24 h later, with an unmatched notification entry of the exact same amount, it is queued for review ("Same amount as an app-notification entry from earlier…") so the user decides; after 24 h it is a normal new entry. No payee/name matching (UPI ids are often phone numbers). A notification arriving after its SMS adds its note to the SMS entry (10 min window, `findNear`).
- Emulator test of notifications: `adb shell cmd notification allow_listener com.intalix.expense.tracker/com.intalix.expense.tracker.PaymentNotificationListener`, then `adb shell "cmd notification post -t 'NAME paid you Rs.1.00' tag 'note text'"`. This needs `"com.android.shell"` temporarily added to `watched` in Receivers.kt (not shipped).
- Notes typed by the payer are stored as `From X (Note: …)` / `To X (Note: …)`; text is sanitised by `NotificationParser.sanitizeNote` (control/zero-width/bidi chars stripped, whitespace collapsed, capped at 200 chars). A note arriving after the SMS entry exists is merged into it and the "added" notification is refreshed.
- Notification access is a special setting (not a runtime permission), so an app update never asks for it. `MainActivity.Root` shows a one-time dialog (`Prefs.listenerAsked`) when it is off and the watch switch is on; Settings keeps an "Allow notification access" action.
- Arrival order does not matter: notification→SMS and SMS→notification both end as one record with the note (all Ingest work is serialised by a Mutex). If the SMS is waiting in the review list, a later notification stores its note on the pending item (`NOTE_TAG` in `Ingest.kt`), used when you accept it.
- Bank-app notifications: the bank's `apps` in `parse/banks/<Bank>.kt` map package → bank (currently Kotak: `com.kotak.bank.mobile`, e.g. "₹1.00 sent to x@okaxis / Amount debited from XX1234"). `parse(text, pkg)` fills `bank` + `accountLast4` (XX1234), so the entry lands on the account linked to `kotak:1234`. With no payee, direction comes from "debited/credited" and the item goes to review. Both debit and credit shapes were confirmed from real screenshots (credit: "₹1.00 received from X / Amount credited to XX1234. Check out details.").
- One payment can arrive as GPay + bank-app + SMS (any order): all share the same amount/direction time window and merge into one record; a later notification only adds the note or fixes the account.
- Background: `NotificationListenerService` is started by Android when a watched app posts, even if our process was killed or the app was swiped from recents (verified with `am kill`). A user "Force stop" in Android settings disables receivers + listener until the app is opened once.

## Privacy rule for shared screenshots/images
- If the user shares an image (e.g. a notification or SMS screenshot), copy only its *shape/wording* into code, tests, comments and docs. Never reuse sensitive details from it (names, UPI IDs, phone numbers, account digits, reference numbers, amounts tied to real people). Use dummy data instead (e.g. `JANE DOE`, `jane@okaxis`, `XX1234`, `600000000001`), unless the user explicitly asks to reuse the exact data.

## Bank templates (adding a bank)
- Pipeline: global reject list -> bank found by SMS sender ID (`Banks.forSender`) or app package (`Banks.forPackage`) -> that bank's templates (first match wins, `certain=true`) -> otherwise the generic parser in `SmsParser` / `NotificationParser`, unchanged. The message body alone never selects a bank.
- A bank = one file `parse/banks/<Name>.kt` (`Bank(name, senders, apps, sms, notif, reject)`) + one line in `Banks.all` + samples in `src/test/resources/samples/<name>.txt`. `BankSamplesTest` checks every sample, that every template has a sample, that banks don't leak into each other, and that samples contain only dummy data.
- `Bank.name` is stored in account links (`"kotak:1234"`): never rename a shipped bank. Windows/AGP: a `/*` inside a KDoc comment opens a nested comment — don't write `samples/*.txt` in comments.
- Full contributor guide: CONTRIBUTING.md.
- UCO Bank (`parse/banks/Uco.kt`): SMS has only amount/date/last digits ("A/c XX1234 Debited with Rs.11.00 on 19-11-2026 by UCO-UPI"): no payee, no UPI id, no ref. Entries are added with an empty note and the normal category guess. Sender ID is `UCOBNK` (operators add a prefix/suffix, e.g. `JX-UCOBNK-T`, `VK-UCOBNK`).
- Payee-less banks (UCO sends no name or UPI id): the entry gets the note `Payment · UCO Bank XX1234` / `Received · …` (`Payees.fallbackNote`; `Payees.fromNote` ignores it so it is never a payee, and no payee rule is looked up). If a GPay/app notification for the same amount arrives within 10 min, `Ingest.process` fills in "To/From X", the payer's note, and re-categorises if still "Other". An SMS is never treated as a duplicate of a nearby entry (UCO has no reference, so two same-amount payments must both count); only notification twins merge. Emulator-verified. Notification test via adb: quote the whole command (`adb shell "cmd notification post -t 'A paid you Rs.1' tag 'note'"`), otherwise the title is word-split.
- Bank → account links (`AccountLink`, key `<bank lowercase>:<last4>`) can be added/removed by hand in Accounts → edit account ("Bank accounts that go here"); they are otherwise created when you confirm a payment from review.

## Logo / brand kit
- Mark: an H whose crossbar is the lower bar of a rupee sign (pine `#14604C`, gold `#F2B632`, cream `#FFF8E6`). Source SVGs, lockups, 512 px Play icon and rules are in `branding/` (`GUIDELINES.md`).
- App icons are hand-written VectorDrawables of the same geometry: `drawable/ic_launcher_fg.xml` (adaptive fg on `@color/launcher_bg`), `ic_launcher_mono.xml` (themed), `ic_stat.xml` (notification). Change the mark in all of them together. minSdk 26, so no legacy mipmap PNGs.
- Wordmark font is Nunito ExtraBold (OFL), outlined in the lockup SVGs.
