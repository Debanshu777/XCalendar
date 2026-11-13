# XCalendar 📅

A modern, cross-platform calendar application built with **Kotlin Multiplatform** and **Compose Multiplatform**, supporting **Android**, **iOS**, and **Desktop** (Windows, macOS, Linux). Features Material 3 Expressive design, multiple calendar views, event management, and holiday integration.

**Connect your own Google Calendar:** follow the
[complete Google Cloud and OAuth setup guide](docs/google-calendar-setup.md).
It covers your own Cloud project, Android/iOS/desktop clients, local configuration,
physical iPhone setup, sync verification, and troubleshooting. No maintainer
credentials are required. Google integration is in development; review its
[current behavior and limits](docs/google-calendar-setup.md#current-behavior-and-limits).

## 🎯 What is XCalendar?

XCalendar is a feature-rich calendar application that provides multiple viewing modes (day, week, month, 3-day, and schedule views) with seamless event management capabilities. Built using modern Kotlin Multiplatform development practices, it offers a native experience across Android, iOS, and Desktop platforms.

## 📱 Screenshots & Demo

### **App Overview**
| Android | iOS | Desktop                                                                                                                                  |
|---------|-----|------------------------------------------------------------------------------------------------------------------------------------------|
|<img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android.gif" width="200" height="400" alt="Android Demo"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios.gif" width="200" height="400" alt="iOS Demo"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop.gif" width="400" height="400" alt="Desktop Demo"> |

### **Calendar Views**
| View | Android | iOS | Desktop |
|------|---------|-----|---------|
| **Month View** | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android_month.png" width="200" height="400" alt="Android Month"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios_month.png" width="200" height="400" alt="iOS Month"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop_month.png" width="600" height="400" alt="Desktop Month"> |
| **Week View** | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android_week.png" width="200" height="400" alt="Android Week"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios_week.png" width="200" height="400" alt="iOS Week"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop_week.png" width="600" height="400" alt="Desktop Week"> |
| **Day View** | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android_day.png" width="200" height="400" alt="Android Day"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios_day.png" width="200" height="400" alt="iOS Day"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop_day.png" width="600" height="400" alt="Desktop Day"> |
| **3-Day View** | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android_three_day.png" width="200" height="400" alt="Android 3-Day"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios_three_day.png" width="200" height="400" alt="iOS 3-Day"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop_three_day.png" width="600" height="400" alt="Desktop 3-Day"> |
| **Schedule View** | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android_schedule.png" width="200" height="400" alt="Android Schedule"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios_schedule.png" width="200" height="400" alt="iOS Schedule"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop_schedule.png" width="600" height="400" alt="Desktop Schedule"> |

### **Event Management**
| Feature | Android | iOS | Desktop |
|---------|---------|-----|---------|
| **Add Event** | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android_add_event.png" width="200" height="400" alt="Android Add Event"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios_add_event.png" width="200" height="400" alt="iOS Add Event"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop_add_event.png" width="600" height="400" alt="Desktop Add Event"> |
| **View Event** | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android_view_event.png" width="200" height="400" alt="Android View Event"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios_view_event.png" width="200" height="400" alt="iOS View Event"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop_view_event.png" width="600" height="400" alt="Desktop View Event"> |

### **Navigation & UI**
### **App Bar** 
| Android | iOS | Desktop |
|---------|-----|---------|
| <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/android_app_bar.png" width="200" height="400" alt="Android App Bar"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/ios_app_bar.png" width="200" height="400" alt="iOS App Bar"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/desktop_app_bar.png" width="600" height="400" alt="Desktop App Bar"> |

### **Bottom Bar**
| Static | Animated |
|--------|----------|
| <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/bottom_bar.png" width="400" alt="Bottom Bar"> | <img src="https://github.com/Debanshu777/XCalendar/raw/main/assets/screenshots/bottom_bar_gif.gif" width="400" alt="Bottom Bar Animation"> |

## ✨ Key Features

- **Multiple Calendar Views**
  - 📅 Month View - Traditional monthly calendar layout
  - 📆 Week View - 7-day weekly schedule
  - 📋 Day View - Detailed single-day view
  - 📊 3-Day View - Compact 3-day overview
  - 📝 Schedule View - List-based event schedule with infinite scroll

- **Cross-Platform Support**
  - 🤖 Android (API 33+)
  - 🍎 iOS (arm64 devices and Apple Silicon Simulator)
  - 🖥️ Desktop (Windows, macOS, Linux)

- **Event Management**
  - ➕ Create, edit, and delete events
  - 🎨 Multiple calendar support (Personal, Work, Family, Birthdays, Gym)
  - ⏰ Event reminders and notifications
  - 🔄 Recurring event support (daily, weekly, monthly, yearly)
  - 📍 Location-based events
  - 🌍 All-day event support

- **Calendar Integration**
  - 👥 Multi-user support
  - 🎯 Primary and secondary calendar management
  - 👁️ Calendar visibility controls
  - 🎨 Color-coded calendar categories

- **Google Calendar (development)**
  - Connect several Google accounts on Android, iOS, or desktop
  - View their calendars together and toggle individual calendar visibility
  - View real calendars and create/edit events on writable calendars
  - Refresh Google changes while the app is open
  - [Configure your own OAuth project](docs/google-calendar-setup.md)

- **Holiday Support**
  - 🏛️ Built-in holiday data (currently supports India)
  - 📅 Holiday display across all views
  - 🌐 Calendarific API integration for live data

- **Modern UI/UX**
  - 🎨 Material Design 3 Expressive implementation
  - 🌈 Dynamic color theming with MaterialKolor
  - ✨ Expressive motion animations
  - 📐 Responsive adaptive layouts
  - 👆 Smooth gesture-based navigation
  - 📱 Native platform integration
  - 🌓 Automatic dark/light theme (follows system)

## 🏗️ Architecture & Technical Stack

### **Technology Stack**

| Category | Technology | Version |
|----------|------------|---------|
| **Build System** | Kotlin Toolchain | 0.13.0 |
| **Language** | Kotlin | 2.4.20 (toolchain default) |
| **UI Framework** | Compose Multiplatform | 1.12.1 (toolchain default) |
| **UI Design** | Material 3 Expressive | Toolchain Compose catalog |
| **Code Generation** | KSP | 2.3.12 (toolchain default) |
| **Database** | Room / SQLite Bundled | 2.8.4 / 2.6.2 |
| **Networking** | Ktor Client | 3.5.0 |
| **DI** | Koin / Koin Annotations | 4.2.1 / 2.3.1 |
| **Navigation** | Navigation3 / Lifecycle | 1.2.0 / 2.11.0 |
| **Date/Time** | kotlinx-datetime | 0.8.0 |
| **Image Loading** | Landscapist Coil3 | 2.9.7 |
| **Dynamic Colors** | MaterialKolor | 4.1.1 |
| **Caching** | Store5 | 5.1.0-alpha08 |
| **Adaptive UI** | Compose Adaptive | 1.3.0 |
| **Collections** | kotlinx-collections-immutable | 0.4.0 |
| **Logging** | Kermit | 2.1.0 |
| **Glass Effects** | ShaderX | 0.1.3 |
| **Android SDK** | Compile / Target / Minimum | 37 / 36 / 33 |
| **Java** | Provisioned JDK / JVM release | 25 / 23 |

Application library versions live in [`libs.versions.toml`](libs.versions.toml).
The pinned wrapper owns compiler, Compose, and KSP defaults. Android and desktop
use Ktor OkHttp; iOS uses Ktor Darwin. Platform entry points are separate modules.

### **Project Structure**

```text
XCalendar/
├── kotlin / kotlin.bat             # Checksummed Kotlin Toolchain wrappers
├── project.yaml                    # Module and plugin registration
├── libs.versions.toml              # Application dependency catalog
├── AGENTS.md                       # Setup, architecture, and validation guidelines
├── app/
│   ├── app.module-template.yaml    # Shared compiler and platform settings
│   ├── androidApp/                 # Android app, src/AndroidManifest.xml, res/
│   ├── desktopApp/                 # JVM desktop app and src/ entry point
│   ├── iosApp/                     # Swift/Kotlin src/, assets, module.xcodeproj
│   └── shared/
│       ├── module.yaml             # Shared dependencies and Room/Koin KSP
│       ├── src/                    # Common Kotlin: UI, domain, data, DI
│       ├── src@android/            # Android database adapter and Koin bridge
│       ├── src@ios/                # iOS database adapter
│       ├── src@iosArm64/           # Device Koin bridge
│       ├── src@iosSimulatorArm64/  # Simulator Koin bridge
│       ├── src@jvm/                # Desktop database adapter and Koin bridge
│       ├── test/                   # Common regression tests
│       └── composeResources/       # Shared images and resource accessors
├── build-logic/
│   ├── app-config/                 # Local API key and OAuth client ID generation
│   └── desktop-distribution/       # Desktop packaging with complete classpath
├── assets/                         # Demo data and screenshots
├── docs/google-calendar-setup.md   # Contributor Google Cloud/OAuth walkthrough
├── local.properties.example       # Copyable configuration template
└── local.properties               # Local OAuth client IDs / optional API_KEY (ignored)
```

CI runs JVM tests and desktop packaging on Linux, macOS, and Windows, plus
Android build/tests and an iOS simulator build on their respective hosts.

The module layout follows Kratos: `src/`, `src@<platform>/`, `test/`, and YAML
configuration. See [AGENTS.md](AGENTS.md) for contribution and validation rules.

### **Data Models**

**Event** - Calendar events with full scheduling support

```kotlin
data class Event(
    val id: String,
    val calendarId: String,
    val calendarName: String,
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Long,           // Epoch milliseconds
    val endTime: Long,
    val isAllDay: Boolean,
    val isRecurring: Boolean,
    val recurringRule: String?,    // iCal RRULE format
    val reminderMinutes: List<Int>,
    val color: Int                 // ARGB color
)
```

**Calendar** - User calendar categories

```kotlin
data class Calendar(
    val id: String,
    val name: String,
    val color: Int,
    val userId: String,
    val isVisible: Boolean,
    val isPrimary: Boolean
)
```

**Holiday** - Regional holiday information

```kotlin
data class Holiday(
    val id: String,
    val name: String,
    val date: Long,
    val countryCode: String
)
```

### **Database Schema**

| Entity | Table | Foreign Key |
|--------|-------|-------------|
| UserEntity | users | - |
| CalendarEntity | calendars | users.id |
| EventEntity | events | calendars.id (CASCADE) |
| EventReminderEntity | event_reminders | events.id |
| HolidayEntity | holidays | - |
| SyncFailureEntity | sync_failures | - |

**Storage Locations:**
- **Android**: `app_data/databases/calendar.db`
- **iOS**: `Documents/calendar.db`
- **Desktop**:
  - Windows: `%APPDATA%/XCalendar/calendar.db`
  - macOS: `~/Library/Application Support/XCalendar/calendar.db`
  - Linux: `~/.local/share/XCalendar/calendar.db`

### **Data Sources**

- **Static Data**: Events and calendars served from `assets/` directory
- **Google Calendar**: OAuth-authorized calendar/event reads and online event writes,
  with account-specific Room snapshots; see the [integration guide](docs/google-calendar-setup.md)
- **Dynamic Data**: Holiday information fetched from Calendarific API (when API key is configured)
- **Local Storage**: Room database for offline data persistence
- **API Integration**: Ktor client for remote data fetching
- **Caching Layer**: Store5 for efficient data caching with bookkeeping
- **Logging**: Kermit multiplatform logging for debugging

### **Key Components**

- **SwipeablePager**: Gesture-based navigation for calendar views
- **SwipeableCalendarView**: Interactive calendar with swipe gestures
- **BaseCalendarScreen**: Shared screen for Day/Week/3-Day views
- **CalendarBottomNavigationBar**: View switcher with FAB for adding events
- **ScheduleStateHolder**: Infinite scroll pagination for schedule view
- **Modal Bottom Sheets**: Add/Edit event dialogs
- **EventViewModel**: Feature-specific ViewModel for event operations
- **Store5 Caching**: Event and Holiday stores with bookkeeping and validation
- **Use Cases**: Clean architecture use cases for calendar, event, holiday, and user operations

## 🚀 Getting Started

### **Prerequisites**

- Network access for the wrapper and Maven dependencies on the first build.
- Android SDK for Android builds; set `ANDROID_HOME` to an existing installation.
- Full Xcode and an iOS SDK/simulator runtime for iOS (macOS only).
- Kotlin Toolchain and Kotlin Multiplatform IDE plugins for project import.

The checked-in wrapper bootstraps its runtime and provisions JDK 25 for builds.
There is no machine-specific Java path or Gradle sync step. The JVM bytecode
release remains 23.

### **Installation**

```sh
git clone https://github.com/Debanshu777/XCalendar.git
cd XCalendar
./kotlin --version
./kotlin show modules
```

On Windows use `.\kotlin.bat` in place of `./kotlin`. Open the repository root
in a compatible IDE with the Kotlin Toolchain plugin.

#### Existing checkouts previously opened with Gradle

Android Studio may retain the old Gradle project link in ignored `.idea/`
settings after switching to this branch. If sync reports that XCalendar
"does not contain a Gradle build", reimport the project:

1. Close the XCalendar project in the IDE.
2. Move its `.idea/` directory into a backup directory outside the active
   project settings (for example, under ignored `.kotlin/ide-backups/`).
3. Use **File → Open** to reopen the repository root with the **Kotlin Toolchain**
   plugin enabled, and let it import `project.yaml`.
4. Use the toolchain's run configuration for the desired app module, or run
   `./kotlin run -m androidApp` / `./kotlin run -m desktopApp` from the root.

Do not run `gradle init` or recreate Gradle settings. A Gradle JVM warning during
this failed sync is a symptom of the stale project import; selecting a different
Gradle JVM will not restore the removed Gradle build.

For live holiday data, optionally add your Calendarific key to root
`local.properties` (see [Calendarific](https://calendarific.com)):

```properties
API_KEY=your_calendarific_api_key
```

The local app-config plugin generates the same `BuildKonfig.API_KEY` contract
for every platform. Missing configuration produces an empty key. Keep this file
and generated outputs out of version control. API keys compiled into client apps
are extractable; use a backend proxy for credentials that must remain secret.

For real Google Calendar data, follow the
[Google Calendar setup guide](docs/google-calendar-setup.md). Start with
[`local.properties.example`](local.properties.example) and, for iOS,
[`Google.local.xcconfig.example`](app/iosApp/Configuration/Google.local.xcconfig.example).
Merge settings into existing local files instead of overwriting them. Google
OAuth is separate from the optional Calendarific API key above.

### **Build, Run, and Test**

Run these commands from the repository root:

```sh
# Android
./kotlin build -m androidApp -p android
./kotlin run -m androidApp

# Desktop (Windows, macOS, Linux)
./kotlin build -m desktopApp -p jvm
./kotlin run -m desktopApp

# iOS simulator / device (macOS only)
./kotlin build -m iosApp -p iosSimulatorArm64
./kotlin run -m iosApp -p iosSimulatorArm64
./kotlin build -m iosApp -p iosArm64

# Regression tests
./kotlin test -m app-config -m desktop-distribution -p jvm
./kotlin test -m shared -p jvm
./kotlin test -m shared -p android
./kotlin test -m shared -p iosSimulatorArm64
```

Use `-v release` for release builds. `./kotlin build` builds all configured
platforms. iOS tests need an available simulator. Use `./kotlin run --help` for
`--device-id` selection. Avoid routine cleans so incremental build caches remain
useful.

### **Distribution**

```sh
# Desktop distribution for the current OS/architecture
./kotlin do packageDesktop -m desktopApp

# Android release app bundle; configure release signing before publishing
./kotlin package -m androidApp -f aab
```

The desktop command prints a directory containing a launcher `XCalendar.jar`
and a `lib/` folder. Keep them together and run:

```sh
java --enable-native-access=ALL-UNNAMED -jar <distribution-directory>/XCalendar.jar
```

The packaging plugin preserves distinct dependencies that share a filename.
Kotlin Toolchain 0.13's built-in `executable-jar` format loses some AndroidX and
JetBrains redirect JARs, so use `packageDesktop` for this application. Build on
each target OS because Compose includes native libraries.

Create native installers with a full JDK's `jpackage`, using the printed
distribution directory as input, for example on macOS:

```sh
jpackage --type dmg --name XCalendar --app-version 1.0.0 \
  --input <distribution-directory> --main-jar XCalendar.jar \
  --java-options --enable-native-access=ALL-UNNAMED --dest build/distributions
```

Use `--type msi` on Windows or `--type deb` on Linux with the host's packaging
tools installed. Kotlin Toolchain 0.13 has no built-in DMG/MSI/DEB tasks.

For iOS, open `app/iosApp/module.xcodeproj`. It retains XCalendar's app identity,
icons, and signing configuration and invokes the Kotlin wrapper automatically.
Swift imports `KotlinModules`; the old `ComposeApp` framework and Gradle build
phase have been removed.

## 📱 App Flow & Navigation

### **Navigation Structure**

The app uses **Navigation3** for type-safe navigation:

```
CalendarApp (Main Container)
├── CalendarTopAppBar
│   ├── Month/Year Title (expandable)
│   ├── Search Button
│   ├── Today Button
│   └── Connect / Google Account
├── NavigationHost (Content Area)
│   ├── MonthScreen (default)
│   ├── WeekScreen
│   ├── DayScreen
│   ├── ThreeDayScreen
│   └── ScheduleScreen
├── CalendarBottomNavigationBar
│   ├── View Selector Icons
│   └── Add Event FAB
└── Modal Bottom Sheets
    ├── AddEventDialog
    └── EventDetailsDialog
```

### **Screen Interactions**

- **Month View**: Tap dates to navigate to day view, swipe to change months
- **Week/Day/3-Day Views**: Tap events for details, swipe to navigate between dates
- **Schedule View**: Infinite scroll with month/week headers and event grouping
- **Bottom Navigation**: Switch calendar views, add events via FAB
- **Top App Bar**: Expand for mini calendar, tap date badge to go to today

### **State Management**

| Component | Purpose |
|-----------|---------|
| `CalendarViewModel` | Main UI state, calendar and user operations |
| `EventViewModel` | Event CRUD operations and state |
| `DateStateHolder` | Current date, selected date, view month |
| `ScheduleStateHolder` | Infinite scroll pagination |
| `CalendarUiState` | Immutable state snapshot |

**Data Flow:**
```
Data Sources (Room + Ktor API)
       ↓ Store5 (caching + bookkeeping)
Repository (domain mapping)
       ↓ Flow<List<T>>
Use Cases (business logic)
       ↓ Flow<List<T>>
ViewModel (combine, debounce)
       ↓ StateFlow<CalendarUiState>
UI Composables (collectAsState)
```

## 🔧 Configuration & Customization

### **Calendar Categories**

The app comes with pre-configured calendar categories:
- **Personal**: Personal events and appointments
- **Work**: Professional meetings and deadlines
- **Family**: Family-related events
- **Birthdays**: Birthday reminders and celebrations
- **Gym**: Fitness and workout schedules

### **Holiday Integration**

- **API**: [Calendarific](https://calendarific.com) (free tier available)
- **Region**: India (IN) - more regions planned
- **Storage**: Room database with offline caching
- **Fallback**: Static data when API unavailable
- **Configuration**: Add `API_KEY` to `local.properties`

### **Event Features**

- **Reminders**: Multiple reminder options (15min, 30min, 1hr, 1day, 1week)
- **Recurring Events**: Support for daily, weekly, monthly, and yearly patterns (iCal RRULE format)
- **Location**: Optional location information for events
- **All-day Events**: Mark events that span entire days

## 🧪 Testing

The shared tests cover event/calendar use cases and ViewModel state. The JVM
integration test resolves the generated Koin graph and reads/writes an in-memory
Room database. Build-plugin tests cover missing, changed, and escaped API keys,
plus runtime classpath collisions and stale dependency cleanup.
Run the commands in **Build, Run, and Test** above.

## 📊 Performance Features

- **Lazy Loading**: Efficient event loading with LazyColumn/LazyVerticalGrid
- **Infinite Pagination**: Schedule view loads data as you scroll
- **State Optimization**: 
  - `distinctUntilChanged()` prevents redundant updates
  - `debounce(30ms)` batches rapid state changes
  - `ImmutableList` for stable recomposition
- **Flow Sharing**: `shareIn(WhileSubscribed(5000))` for efficient data sharing
- **Compose Stability**: Immutable models and the toolchain's Compose compiler
- **Memory Management**: Scoped coroutines with proper lifecycle handling

## 🔮 Future Enhancements

- [x] Google Calendar connection and foreground sync (development; [limits](docs/google-calendar-setup.md#current-behavior-and-limits))
- [ ] Outlook Calendar integration
- [ ] Push notifications for reminders
- [x] Dark/Light theme switching
- [x] Desktop application support
- [ ] Custom calendar colors
- [ ] Event sharing capabilities
- [ ] Multi-language support
- [ ] Widget support (Android/iOS)
- [ ] Real-time calendar sync
- [ ] Multiple holiday region support
- [ ] Web application support

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### **Development Guidelines**

- Follow Kotlin coding conventions
- Use Compose best practices
- Maintain clean architecture principles
- Add appropriate test coverage

### **API Integration Notes**

- **Demo Data**: Events and calendars from `assets/` remain separate from Google data
- **Google Calendar**: Bring your own Cloud project and platform OAuth clients;
  follow the [setup guide](docs/google-calendar-setup.md)
- **Holiday API**: Requires API key in `local.properties` for live holiday data
- **Google Refresh Failures**: Preserve the last complete account snapshot and show
  an error; demo events are not uploaded or substituted for a successful sync

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- Material 3 Expressive Design
- Database management with [Room](https://developer.android.com/training/data-storage/room)
- Dependency injection using [Koin](https://insert-koin.io/)
- Dynamic theming with [MaterialKolor](https://github.com/jordond/MaterialKolor)
- Holiday data from [Calendarific](https://calendarific.com)
- Navigation with [Navigation3](https://developer.android.com/jetpack/androidx/releases/navigation3)
- Caching with [Store5](https://github.com/MobileNativeFoundation/Store)
- Logging with [Kermit](https://github.com/touchlab/Kermit)

---

**XCalendar** - Modern calendar experience across platforms 📱🖥️💻

## Google Calendar connection

Use **Connect → Connect Google Calendar** to authorize your account, then open
its dialog for **Add Google account**, **Sync all**, or **Remove account**. The current integration supports
viewing real calendars and creating/editing standard events on writable calendars
across Android, iOS, and JVM desktop. Demo events are never uploaded automatically.

The [detailed setup guide](docs/google-calendar-setup.md) walks through:

1. Creating your own Google Cloud project and enabling Google Calendar API.
2. Configuring consent, test users, and the required scopes.
3. Registering Android signing fingerprints, iOS bundle IDs, and desktop clients.
4. Filling the ignored local configuration using the checked-in templates.
5. Building and installing, including on a physical iPhone.
6. Verifying real event synchronization and resolving common setup errors.

Connect multiple accounts and show their calendars together, with account-specific
event saves and removal. Connected accounts are restored after restart using
OS-protected credentials, and access tokens refresh automatically. Google handles
passwords and 2FA; revoked or expired authorization prompts a reconnect for only
that account. The app refreshes the displayed and adjacent months while open and
requires connectivity to save. Google event deletion, recurring-series creation/editing,
calendar moves, and background synchronization are not implemented. See
[current behavior and limits](docs/google-calendar-setup.md#current-behavior-and-limits)
and [public distribution](docs/google-calendar-setup.md#public-distribution)
before sharing a configured build.
