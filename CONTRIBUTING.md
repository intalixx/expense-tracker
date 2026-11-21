# Contributing

Thanks for helping! The most useful contribution is **support for another bank**: its SMS texts and, if it has one, its app notifications. You don't need to know Android to do it.

By contributing you agree that your work is released under the project's licence (GNU AGPL v3, see `LICENSE`).

## Rules that matter

1. **Privacy first. Use dummy data only.** Never commit a real message. Before pasting a sample, replace everything personal:
   - account / card digits → `1234` (also allowed: `4321`, `9876`)
   - names → made-up ones (`JOHN Q SAMPLE`), UPI ids → `jane.doe@okaxis`, `shop@upi`
   - reference numbers → made-up digits; amounts may stay or change
   - no phone numbers, no balances, no links containing personal tokens

   The sample test rejects some real-looking data (10-digit numbers, other account digits), but it can't catch everything. Reviewers will check, and so should you.
2. **When in doubt, don't guess.** A wrong automatic entry is worse than a message sitting in the review list. Templates only need to cover wording you have actually seen. Unknown wording falls through to the generic parser, and anything unclear goes to the review list.
3. **Don't loosen the safety nets.** Templates can add phrases to a bank's `reject` list but can't remove the global ones (OTP, failed, "will be debited", requests, offers ...).
4. **No new permissions, no network.** The app has no internet permission, and that is a feature. Changes that add one will not be merged.
5. **Never rename a bank that has shipped.** `Bank.name` is stored in users' account links (`"kotak:1234"`). Renaming it silently breaks their account routing.
6. **One bank per pull request** if you can, and keep the change small.
7. All tests must pass before you open the PR (see "Running the tests").

## Adding a bank (3 steps)

Also add the bank to `supported.md`.

### 1. Create `app/src/main/java/com/intalix/expense/tracker/parse/banks/<Name>.kt`

Copy `Kotak.kt` and edit it. Example for a made-up UCO Bank:

```kotlin
package com.intalix.expense.tracker.parse.banks

import com.intalix.expense.tracker.parse.Bank
import com.intalix.expense.tracker.parse.credit
import com.intalix.expense.tracker.parse.debit

val Uco = Bank(
    name = "UCO Bank",
    senders = listOf("UCOBNK"),                 // from "AX-UCOBNK-S": drop the "AX-" and "-S"
    apps = listOf("com.uco.mbanking"),          // package name of the bank's app, if you want its notifications read
    sms = listOf(
        debit("A/c XX{acct} debited by Rs.{amount} on {date} to {payee}. UPI Ref {ref}"),
        credit("A/c XX{acct} credited by Rs.{amount} on {date} from {payee}. UPI Ref {ref}"),
    ),
    notif = listOf(                              // lines of a notification are joined with " | "
        debit("₹{amount} paid to {payee} | Debited from XX{acct}"),
    ),
)
```

Writing a template:

- Copy the message and replace the changing parts with placeholders. Everything else must match literally.
- Case and spacing don't matter. Any run of spaces (even none) matches any run of spaces.
- Placeholders: `{amount}` `{acct}` (last 3-4 digits) `{payee}` `{ref}` `{date}` `{any}`. Only `{amount}` is required. Each of `{amount}`, `{acct}`, `{payee}` and `{ref}` can be used once per template; `{date}` and `{any}` can be repeated.
- `{payee}` stops at the words `on`, `via`, `using`, `ref`, `upi`, `txn`, `if`, `not`, `call`, `avl`, `bal`, `is`, at ` | `, `·`, `•`, at a full stop followed by a space, or at a comma. Put the text that follows the payee right after it in the pattern.
- One template per distinct wording. Debit and credit are separate templates (`debit(...)` / `credit(...)`).
- A typo in a placeholder (`{amnt}`) fails the tests with a clear message.
- Only if the placeholders can't express something, use `debitRegex(...)` / `creditRegex(...)` with a named group `(?<amount>...)`. Prefer not to.
- `senders` are the sender IDs in UPPER CASE without the operator prefix and suffix (at least 4 characters; a sender matches if it contains the ID). `reject` is optional: extra lower-case phrases that mean "not a payment" for this bank.

### 2. Register it

In `parse/Registry.kt`, add your bank to `Banks.all` (one line).

### 3. Add samples

Create `app/src/test/resources/samples/<name>.txt` (or add to an existing file). Each sample is a block; blocks are separated by `---`, and lines starting with `#` are comments:

```
kind: sms
sender: AX-UCOBNK-S
text: A/c XX1234 debited by Rs.500.00 on 01-01-26 to TEST SHOP. UPI Ref 600000000001
expect: DEBIT 500.00 acct=1234 payee="Test Shop" ref=600000000001 bank="UCO Bank" template
---
kind: sms
sender: AX-UCOBNK-S
text: Your OTP is 123456. Do not share it.
expect: IGNORED
```

- `kind` is `sms` (default) or `notif`. SMS blocks need `sender:`; notification blocks need `package:`. In notification text write a line break as `\n`.
- `expect:` is `IGNORED` or `DEBIT|CREDIT <amount>` followed by any of `acct=` `payee="..."` `ref=` `bank=`, plus these flags: `template` (a bank template must match it, so use it for the messages your templates cover) and `review` (parsed, but should go to the review list).
- The payee is expected **title-cased** for SMS (`"Test Shop"`) and exactly as written for notifications.
- Add a few **negative** samples too: OTPs, failed payments, balance alerts, offers, and anything else the bank sends that must not become an entry.

## Adding a payment app (PhonePe, BHIM, supermoney, Paytm ...)

Payment apps are not banks: they have no SMS sender and no account of their own, only notifications. The enabled apps are listed in `supported.md`; update it when you add one.

1. Create `parse/apps/<Name>.kt`: `val PhonePe = PaymentApp(name = "PhonePe", packages = listOf("com.phonepe.app"))`. Find the package with `adb shell pm list packages | grep <name>`.
2. Add it to `PaymentApps.all` in `parse/Registry.kt` (one line). That also makes the app watched; nothing else to change.
3. Post a real notification (or a screenshot of one, with your own details replaced by dummy data) and check the amount, payee and note come out right. The generic rules in `NotificationParser` already understand "Paid ₹100 to X" and "X paid you ₹100" and the note line under it. Only if the wording is different, give the app its own `notif = listOf(debit(...), credit(...))` templates (same placeholders as banks), and `reject = listOf(...)` for look-alikes such as cashback or reminders.
4. Add a unit test in `PaymentAppTest.kt` with the dummy-data notification text.

## Running the tests

```
gradle :app:testDebugUnitTest
```

A passing run ends with `BUILD SUCCESSFUL`. Failures are listed in `app/build/test-results/testDebugUnitTest/`. `BankSamplesTest` checks that:

- every sample parses as its `expect:` line says
- every sample marked `template` really is matched by a bank template
- every template has at least one sample
- one bank's messages never match another bank's templates
- samples contain no real-looking account numbers or phone numbers


## Other contributions

- Bug reports: include the (dummy-ised) message text and what you expected.
- Code changes: keep parsers pure Kotlin (no Android classes) so they stay unit-testable, and add or update tests. See `CLAUDE.md` for a map of the code.
- Please don't add analytics, ads, network calls or cloud sync.
