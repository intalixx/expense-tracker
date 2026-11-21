# Expense Tracker

A private, offline expense tracker for Android, built for Indian users. It keeps your money in a few clear places (accounts, categories, budgets) and fills most of it in for you by reading bank SMS and UPI app notifications on the phone.

**Expense Tracker is for India only, for now.** It works with Indian rupees (₹) only, and it understands Indian bank SMS and UPI apps (see [supported.md](supported.md)).

**The app has no internet permission. Nothing ever leaves your device.**

## Contents

- [Features](#features)
- [Privacy](#privacy)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Setup guide](#setup-guide)
- [Building and running](#building-and-running)
- [Running the tests](#running-the-tests)
- [Release builds](#release-builds)
- [Testing SMS and notifications on the emulator](#testing-sms-and-notifications-on-the-emulator)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Records**: a monthly list of income and expenses grouped by day, with a quiet income / expense / balance summary. Add or edit an entry with a built-in calculator, an account picker (showing each account's balance) and a category grid.
- **Automatic entries**
  - Reads bank debit/credit SMS and turns them into records. Anything it is unsure about waits in a **review list** for you to confirm.
  - Optionally watches payment-app and bank-app notifications (see [supported.md](supported.md)). This helps when an SMS is late or never arrives. Payment-app notifications also carry the note the sender typed, which is saved with the entry as `From X (Note: …)`.
  - **No double counting.** The same payment can arrive as an SMS, a payment-app notification and a bank-app notification, in any order. They are merged into one record, and a later message only adds the missing note or corrects the account.
  - **People & payees** (Settings). Every person or shop you paid or received from is listed. Pick a category once (e.g. a person → Salary) and every later payment goes there, optionally with no review. Past entries in "Other" are moved too.
  - Remembers account choices, can scan existing messages (30 / 60 / 90 days / all time) and works in the background without the app open.
- **Analysis**: two dropdown filters, a period (Daily, Weekly, Monthly, 3 Months, 6 Months, Yearly) and a view (Expense overview, Income overview, Expense flow, Income flow).
- **Budgets, Accounts, Categories**: set limits, track balances per account, and manage your own categories.
- **Backup**: export and import a backup file (importing replaces all current data). Backups from the MyMoney app (`.mbak`) can be imported too.
- **Security**: optional 4-digit passcode.

## Privacy

- No `INTERNET` permission, so the app cannot send data anywhere.
- Permissions used: `RECEIVE_SMS` and `READ_SMS` (bank messages) and `POST_NOTIFICATIONS` (telling you an entry was added). All are optional, and each feature can be switched off in Settings.
- Notification access (to read payment-app notifications) is a separate Android setting. The app explains why and asks once.

## Tech stack

| Area | Used |
|---|---|
| Language | Kotlin 2.2 (JVM target 17) |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Storage | Room 2.8 (SQLite), annotation processing with KSP |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 8.13 |
| Android versions | `minSdk` 26 (Android 8.0), `targetSdk` 35, `compileSdk` 36 |
| Tests | JUnit 4. The SMS, notification, calculator and period logic is plain Kotlin, so tests run on your computer without a device |

Money is stored as whole paise (`amountPaise`, an integer) to avoid rounding errors. A record's `type` is `1` for income and `2` for expense.

## Project structure

```
expense-tracker/
├── app/                                  # The only Gradle module (the Android app)
│   ├── build.gradle.kts                  # SDK versions, dependencies, signing, test options
│   ├── proguard-rules.pro                # Shrinker rules for release builds
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Permissions, activity, SMS receiver, notification listener
│       │   ├── java/com/intalix/expense/tracker/
│       │   │   ├── MainActivity.kt       # App entry point, navigation, first-run dialogs
│       │   │   ├── Screens.kt            # Tabs: Analysis, Budgets, Accounts, Categories, Settings
│       │   │   ├── Records.kt            # Records tab (month bar, summary row, grouped list)
│       │   │   ├── Entry.kt              # Add/edit entry: calculator, account picker, category grid
│       │   │   ├── Common.kt             # Shared widgets (Chip, ChipRow, Hairline, PickDropdown, ...)
│       │   │   ├── Period.kt             # Analysis periods: span, shift, label, buckets
│       │   │   ├── Calc.kt               # Calculator expression evaluator
│       │   │   ├── Vm.kt                 # ViewModel: state shared by the screens
│       │   │   ├── Data.kt               # Room database: entities, DAO, migrations
│       │   │   ├── SmsParser.kt          # Bank SMS -> transaction (generic parser)
│       │   │   ├── NotificationParser.kt # Payment/bank app notification -> transaction
│       │   │   ├── Classifier.kt         # Guesses the category of a transaction
│       │   │   ├── Ingest.kt             # Turns parsed messages into records or review items; merges duplicates; Prefs
│       │   │   ├── Receivers.kt          # SMS receiver and notification listener service
│       │   │   ├── Payees.kt             # Payee names and "payee -> category" rules
│       │   │   ├── PayeesScreen.kt       # Settings -> People & payees screen
│       │   │   ├── Mbak.kt               # Import of MyMoney (.mbak) backups
│       │   │   └── parse/                # Per-bank and per-app message templates
│       │   │       ├── Template.kt       # Template language ("Rs.{amount} debited ...")
│       │   │       ├── PaymentApp.kt     # PaymentApp definition
│       │   │       ├── Registry.kt       # Lists of all banks (Banks.all) and apps (PaymentApps.all)
│       │   │       ├── banks/            # One file per bank (Kotak.kt, Uco.kt)
│       │   │       └── apps/             # One file per payment app (GooglePay.kt)
│       │   └── res/
│       │       ├── drawable/             # Launcher and notification icons (vector drawables)
│       │       ├── mipmap-anydpi-v26/    # Adaptive launcher icon
│       │       ├── font/                 # App fonts
│       │       ├── values/               # Colours and light theme
│       │       └── values-night/         # Dark theme
│       └── test/
│           ├── java/com/intalix/expense/tracker/   # JUnit tests (parsers, calculator, periods, payees, backup import)
│           └── resources/samples/        # Sample bank messages, dummy data only (one file per bank)
├── branding/                             # Logo source SVGs, PNGs and brand guidelines (GUIDELINES.md)
├── build.gradle.kts                      # Root build file: plugin versions
├── settings.gradle.kts                   # Module list and repositories (Google, Maven Central)
├── gradle.properties                     # Gradle settings (memory, AndroidX)
├── run_app.ps1                           # Windows: build, start emulator, install and open the app
├── run_gradle.ps1                        # Windows: run a cached Gradle 9.3.1 with JAVA_HOME/ANDROID_HOME set
├── run_gradle.sh                         # Same for macOS / Linux / Git Bash
├── supported.md                          # Banks and payment apps the app understands
├── CONTRIBUTING.md                       # How to add a bank or payment app
├── CLAUDE.md                             # Detailed developer notes (architecture rules, emulator tips)
└── LICENSE                               # AGPL-3.0
```

Not in the repository (ignored by git): `build/`, `.gradle/`, `local.properties`, APK files, and `signing/` (the release keystore).

## Prerequisites

You do **not** need Android Studio. Everything can be done from the command line. Android Studio is still the easiest way to get everything in one install if you prefer it.

| Requirement | Version | Why |
|---|---|---|
| JDK | 17 or newer (tested with 17 and 20) | Runs Gradle and compiles Kotlin |
| Android SDK Platform | `android-36` | `compileSdk = 36` |
| Android SDK Build-Tools | `35.0.0` | Used by the Android Gradle Plugin (Gradle downloads it if missing and licenses are accepted) |
| Android SDK Platform-Tools | latest | `adb`, to install the app on a device |
| Gradle | 8.13 or newer (tested with 9.3.1) | Builds the project. **The repo has no Gradle wrapper (`gradlew`), so Gradle must be installed** |
| A device | Android 8.0 (API 26) or newer | A phone with USB debugging, or an emulator |
| Internet | first build only | Downloads plugins and libraries. Later builds work with `--offline` |

Optional, only for the emulator: Android Emulator and a system image (about 1.5 GB), plus hardware virtualisation (Windows Hypervisor Platform on Windows, KVM on Linux).

## Setup guide

The steps below use Windows PowerShell. macOS and Linux notes follow each step.

### 1. Install a JDK

Install JDK 17 or newer (for example from [Adoptium](https://adoptium.net/) or `winget install EclipseAdoptium.Temurin.17.JDK`). Check it:

```powershell
java -version
```

Set `JAVA_HOME` to the JDK **folder, not its `bin` folder**. Gradle rejects a path ending in `\bin`.

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\jdk-17', 'User')   # use your JDK path
```

macOS / Linux: add `export JAVA_HOME=/path/to/jdk` to `~/.zshrc` or `~/.bashrc`.

### 2. Install the Android SDK (command line)

1. Download **"Command line tools only"** for your OS from <https://developer.android.com/studio> (bottom of the page).
2. Create the SDK folder and extract the zip so the layout is exactly:
   ```
   %LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
   ```
   The zip contains a folder named `cmdline-tools`. Rename it to `latest` and put it inside `Sdk\cmdline-tools\`.
   macOS: `~/Library/Android/sdk/cmdline-tools/latest/`. Linux: `~/Android/Sdk/cmdline-tools/latest/`.
3. Accept the licenses and install the packages:
   ```powershell
   $sdk = "$env:LOCALAPPDATA\Android\Sdk"
   & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
   & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "build-tools;35.0.0" "platform-tools"
   ```
   Answer `y` to each license.

If you already have a `platform-tools` folder that sdkmanager didn't install, it installs a second copy as `platform-tools-2`. Delete the old folder and rename the new one.

### 3. Set ANDROID_HOME and PATH

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdk, 'User')
$p = [Environment]::GetEnvironmentVariable('Path', 'User')
[Environment]::SetEnvironmentVariable('Path', "$p;$sdk\platform-tools;$sdk\emulator", 'User')
```

**Close and reopen the terminal** afterwards; open terminals don't see new variables. Check with `adb version`.

Instead of `ANDROID_HOME`, you can create `local.properties` in the project root with `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`. This file is ignored by git.

macOS / Linux: `export ANDROID_HOME=$HOME/Library/Android/sdk` (macOS) or `$HOME/Android/Sdk` (Linux), and add `$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator` to `PATH`.

### 4. Install Gradle

Pick one:

- **Package manager**: `winget install Gradle.Gradle`, `scoop install gradle`, `brew install gradle` (macOS) or [SDKMAN!](https://sdkman.io/) `sdk install gradle 9.3.1` (Linux/macOS). Check with `gradle --version`.
- **Helper scripts**: `run_gradle.ps1` / `run_gradle.sh` call Gradle 9.3.1 from `~/.gradle/wrapper/dists/gradle-9.3.1-bin/`. That folder exists if any project on your machine has used Gradle 9.3.1 before (Android Studio creates it too). If it's missing, use the package manager option.

### 5. Get a device

**Option A: your phone (quickest)**

1. Settings → About phone → tap **Build number** 7 times to enable Developer options.
2. Settings → Developer options → turn on **USB debugging** (on Xiaomi/Redmi also **Install via USB**).
3. Connect with a USB **data** cable (charge-only cables won't work), set USB mode to **File transfer**, and tap **Allow** on the "Allow USB debugging?" prompt.
4. `adb devices` should list the phone as `device`. `unauthorized` means you still need to tap Allow on the phone.

**Option B: an emulator**

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "emulator" "system-images;android-35;google_apis;x86_64"
& "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n Pixel_2 -k "system-images;android-35;google_apis;x86_64" -d pixel_2
emulator -accel-check     # should report that WHPX (Windows) or KVM (Linux) is usable
```

On Apple Silicon Macs use the `arm64-v8a` image instead of `x86_64`. `run_app.ps1` expects an emulator named `Pixel_2` by default (`-Avd <name>` to use another).

## Building and running

### One command (Windows)

From the project root:

```powershell
.\run_app.ps1             # build, start the emulator if no device is connected, install and open the app
.\run_app.ps1 -NoBuild    # reinstall the last built APK and open it
.\run_app.ps1 -Avd Other  # use a different emulator
```

It uses `gradle` from your PATH, or falls back to `run_gradle.ps1`.

### Step by step (any OS)

```powershell
emulator -avd Pixel_2                 # start the emulator (or connect your phone); leave it running
gradle :app:assembleDebug             # build -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.intalix.expense.tracker -c android.intent.category.LAUNCHER 1   # open the app
```

Without `gradle` on the PATH, replace `gradle` with `.\run_gradle.ps1` (Windows) or `bash ./run_gradle.sh` (macOS/Linux/Git Bash). They take the same arguments.

Useful Gradle flags: `-q` (quiet; prints nothing on success), `--offline` (no network after the first build).

**Without a cable or adb:** copy `app-debug.apk` to the phone (Drive, email, messaging app), open it and allow "Install unknown apps".

The first build takes several minutes while Gradle downloads dependencies. Later builds are much faster.

## Running the tests

```powershell
gradle :app:testDebugUnitTest
```

- Results: `app/build/test-results/testDebugUnitTest/*.xml`; HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Tests run on your computer (JVM), no device needed.
- `BankSamplesTest` checks every sample in `app/src/test/resources/samples/` against the bank templates, and that samples contain only dummy data.
- Run the tests after changing `SmsParser`, `NotificationParser`, `Calc`, `Period` or any bank template.

**Android's regex engine is stricter than the desktop JVM's.** A pattern like `{(\w+)}` with an unescaped `}` passes the unit tests but crashes the app at start. Escape literal braces (`\{(\w+)\}`), and after any regex or template change, open the app on a device, not just the tests.

## Release builds

```powershell
gradle :app:assembleRelease
```

Release builds are minified and shrunk with R8. Signing is read from `signing/keystore.properties`:

```properties
storeFile=expense-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

with the keystore file in the same `signing/` folder. That folder is ignored by git and is **not** in the repository. Never commit or share it. Without it, the build produces an unsigned `app-release-unsigned.apk`, which Android won't install until it is signed.

Android only installs an update signed with the same key as the installed app. To switch a device between debug and release builds, uninstall the app first (this deletes its data).

## Testing SMS and notifications on the emulator

Send a fake bank SMS (emulator only):

```powershell
adb emu sms send HDFCBK "Rs.150.00 debited from a/c XX1234 on 19-11-26 to VPA zomato@icici (UPI Ref No 600000000001). Not you? Call 18002586161"
adb emu sms send HDFCBK "Rs.640.00 credited to a/c XX1234 on 19-11-26 by VPA icon@upi (UPI Ref No 600000000002)"
```

- Grant SMS permission in the app and turn on "auto add" in Settings, otherwise entries go to the review list.
- Use a new UPI reference number each time; duplicates are dropped by reference.
- OTP, promotional, failed and "will be debited" messages are ignored on purpose, which makes them good negative tests.

Payment-app notifications can't be faked for another app's package with adb, so that path is covered by `NotificationParserTest`. See [CLAUDE.md](CLAUDE.md) for a workaround used during development.

## Troubleshooting

| Problem | Fix |
|---|---|
| `JAVA_HOME is set to an invalid directory` | `JAVA_HOME` must be the JDK folder, not `...\bin`. Reopen the terminal after changing it |
| `SDK location not found` | Set `ANDROID_HOME`, or add `sdk.dir=...` to `local.properties` |
| `gradle` / `adb` is not recognized | Not on PATH. Install Gradle (step 4) / add `platform-tools` to PATH (step 3), then reopen the terminal |
| `Gradle 9.3.1 not found under ~/.gradle/wrapper/dists` | The helper script needs a cached Gradle 9.3.1. Install Gradle and run `gradle` directly |
| License errors during the build | `sdkmanager --licenses` and accept all |
| `adb: no devices/emulators found` | Start the emulator or connect the phone. If the phone doesn't show in File Explorer either, the cable or USB mode is the problem |
| Device shows as `unauthorized` | Tap **Allow** on the phone's USB debugging prompt; replug if it didn't appear |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed app is signed with a different key (debug vs release). Uninstall it first (deletes its data) |
| Emulator is very slow or won't start | Run `emulator -accel-check`. On Windows, turn on "Windows Hypervisor Platform" in Windows Features |
| App crashes right at start after a template change | Unescaped `{` or `}` in a regex (see [Running the tests](#running-the-tests)) |
| Deprecation warnings during the build (`kotlinOptions`, `ReceiptLong`, ...) | Harmless; the build still succeeds |

## Contributing

Banks send their messages in different wordings. Adding support for another bank (SMS and app notifications) is a small, self-contained change: one file per bank plus some dummy-data samples. See [CONTRIBUTING.md](CONTRIBUTING.md). Detailed architecture notes and rules are in [CLAUDE.md](CLAUDE.md).

## License

Licensed under the [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0). If you modify this app and distribute it or let others use it over a network, you must make your modified source available under the same license.
