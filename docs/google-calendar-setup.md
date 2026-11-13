# Set up your own Google Calendar integration

This guide takes a fresh XCalendar checkout from demo mode to connecting your
own Google account on Android, iOS, and JVM desktop. You create and own the Google
Cloud project and OAuth clients. No access to the maintainer's Cloud project is
needed, and no working credentials are included in the repository.

You can configure just one platform or all three. Each person running a private
fork can use their own project. If you distribute a configured app, your users
authorize that app's project; they do not each need to create a Cloud project.
See [public distribution](#public-distribution) before doing that.

## Contents

- [1. Prepare the checkout](#1-prepare-the-checkout)
- [2. Create a Google Cloud project](#2-create-a-google-cloud-project)
- [3. Enable the Calendar API](#3-enable-the-calendar-api)
- [4. Configure consent and test users](#4-configure-consent-and-test-users)
- [5. Create the Android client](#5-create-the-android-client)
- [6. Create the iOS client](#6-create-the-ios-client)
- [7. Create the desktop client](#7-create-the-desktop-client)
- [8. Add local configuration](#8-add-local-configuration)
- [9. Build and run](#9-build-and-run)
- [10. Connect and verify synchronization](#10-connect-and-verify-synchronization)
- [Using several Google accounts together](#using-several-google-accounts-together)
- [Current behavior and limits](#current-behavior-and-limits)
- [Troubleshooting](#troubleshooting)
- [Public distribution](#public-distribution)
- [Implementation and automated checks](#implementation-and-automated-checks)

## 1. Prepare the checkout

Clone the repository or your fork and run commands from its root:

```sh
git clone https://github.com/Debanshu777/XCalendar.git
cd XCalendar
./kotlin --version
./kotlin show modules
```

Use the checked-in Kotlin Toolchain **0.13.0** wrapper. On Windows, replace
`./kotlin` with `.\kotlin.bat`; shell examples below use macOS/Linux syntax unless
marked PowerShell. This project is configured with YAML and has no Gradle setup
or `signingReport` task to run. See the [main README](../README.md) for general
installation and packaging instructions.

| Target | Requirements |
| --- | --- |
| All | A Google account, network access, and permission to create/configure a Cloud project. Dependencies download on the first build. |
| Android | Android SDK with compile SDK 37, `ANDROID_HOME` pointing to it, and an API 33+ device or emulator with Google Play services. |
| iOS | macOS, full Xcode selected as the developer directory, an installed iOS SDK, and Apple signing credentials for a real iPhone. |
| Desktop | Windows, macOS, or Linux with a graphical desktop and a default browser. The wrapper provisions the build JDK. |

XCalendar calls Google directly from the installed app. This implementation does
not require a hosted backend, Firebase, a service account, or a Google API key.
The optional `API_KEY` setting is for **Calendarific holidays**, not Google OAuth.

Decide on your application identities before creating mobile clients:

| Target | Repository default / input | Google client type |
| --- | --- | --- |
| Android | `applicationId: com.debanshu.xcalendar` in [module.yaml](../app/androidApp/module.yaml), plus your build's signing SHA-1 | Android |
| iOS | `com.debanshu.xcalendar.XCalendar` when `TEAM_ID` is empty; see [Config.xcconfig](../app/iosApp/Configuration/Config.xcconfig) | iOS |
| Desktop, including macOS JVM | The client ID placed in `GOOGLE_DESKTOP_CLIENT_ID` | Desktop app |

For an independently published fork, choose your own Android application ID and
iOS bundle ID. Kotlin source package names can remain unchanged. The Android
OAuth package must match **applicationId**, not the separate `namespace` value.
Changing either mobile identifier later requires a matching OAuth registration.

## 2. Create a Google Cloud project

1. Open [Google Cloud Console](https://console.cloud.google.com/) using the account
   that should own your integration.
2. Open the project selector and choose **New Project**.
3. Enter a descriptive name such as `XCalendar Personal`. Choose an available
   project ID and the appropriate organization/location for your account.
4. Create the project, then select it in the project selector.
5. Keep that project selected for every API and OAuth step below.

The project ID identifies the Cloud project. It is different from an OAuth
client ID and is not a setting read by XCalendar. See Google's
[project creation guide](https://developers.google.com/workspace/guides/create-project)
if your account cannot create a project.

## 3. Enable the Calendar API

1. In your selected project, open **APIs & Services → Library**.
2. Search for **Google Calendar API**, open it, and choose **Enable**.
3. Check **APIs & Services → Enabled APIs & services** for Google Calendar API.
   Its detail page should offer management controls rather than an Enable button.

Alternatively, with the Google Cloud CLI already authenticated to the correct
account, use your own project ID:

```sh
gcloud services enable calendar-json.googleapis.com --project=YOUR_PROJECT_ID
```

Enabling the API does not create OAuth clients or grant access to a user's
calendar. Those are separate steps. See Google's
[API enablement guide](https://developers.google.com/workspace/guides/enable-apis).

## 4. Configure consent and test users

Console labels can change. The relevant pages are under **Google Auth Platform**;
older Console layouts may start at **APIs & Services → OAuth consent screen**.

1. Open **Google Auth Platform → Branding** and select **Get Started** if prompted.
2. Set your app name and user support email.
3. Choose **External** for personal Google accounts and contributors outside a
   Workspace organization. **Internal** applies to an eligible organization.
4. Provide your developer contact email, review Google's policy, and finish setup.
5. Open **Audience**. Keep the publishing status **Testing** for development.
6. Under **Test users → Add users**, add the exact Google account email you will
   use inside XCalendar, plus any other testers. Save.
7. Open **Data Access → Add or remove scopes**, select the scopes below, then save.

See Google's [consent setup guide](https://developers.google.com/workspace/guides/configure-oauth-consent).

These are the scopes requested by
[`GOOGLE_SCOPES`](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleModels.kt):

| Scope requested by XCalendar | Purpose |
| --- | --- |
| `openid` | Identify the connected Google account. |
| `email` | Display and bind the account email. Console may label this `https://www.googleapis.com/auth/userinfo.email`. |
| `profile` | Display the account name/profile. Console may label this `https://www.googleapis.com/auth/userinfo.profile`. |
| `https://www.googleapis.com/auth/calendar.calendarlist.readonly` | List calendars available to the account. |
| `https://www.googleapis.com/auth/calendar.events` | Read events and save changes to calendars the account can write to. |

If a Calendar scope is hard to find, filter by Google Calendar API or use the
manual scope-entry field. Both Calendar scopes are needed by the current app.
The event scope permits deletion at the API level, although XCalendar does not
yet expose Google event deletion. Calendar permissions still determine which
calendars are writable. See the [Calendar scope reference](https://developers.google.com/workspace/calendar/api/auth).

**Checkpoint:** the correct project is selected, your account is a test user,
and both Calendar scopes appear in Data Access. An account that owns the Cloud
project should still be added explicitly as a test user for this setup.

## 5. Create the Android client

Skip this section if you are not building Android.

### Find the actual application ID and signing certificate

1. Check `settings.android.applicationId` in
   [app/androidApp/module.yaml](../app/androidApp/module.yaml). The default is
   `com.debanshu.xcalendar`.
2. Build the variant you intend to install:

   ```sh
   ./kotlin build -m androidApp -p android
   ```

3. Read the certificate from that APK using Android SDK Build Tools. Replace
   `YOUR_BUILD_TOOLS_VERSION` with an installed directory under
   `$ANDROID_HOME/build-tools`:

   ```sh
   "$ANDROID_HOME/build-tools/YOUR_BUILD_TOOLS_VERSION/apksigner" verify --print-certs \
     build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk
   ```

   PowerShell equivalent:

   ```powershell
   & "$env:ANDROID_HOME\build-tools\YOUR_BUILD_TOOLS_VERSION\apksigner.bat" verify --print-certs `
     "build\tasks\_androidApp_buildAndroidDebug\gradle-project-debug.apk"
   ```

4. Copy the **Signer #1 certificate SHA-1 digest**. Do not use SHA-256 or a hash
   of the APK file. If the Console requires colon-separated hex, format the
   digest as pairs such as `AA:BB:...`.

That APK path is the current 0.13.0 debug output. The `gradle-project` filename is
an internal toolchain output; build through `./kotlin`. If the output location
changes, use the APK path printed by the build. `apksigner` requires a working
Java installation; a JDK's `bin` directory can be added to your local `PATH`.
See the [official apksigner reference](https://developer.android.com/tools/apksigner).

If you already manage the signing keystore, you can inspect its certificate:

```sh
keytool -list -v -keystore /path/to/your.keystore -alias YOUR_KEY_ALIAS
```

Enter its password at the prompt. Verify that this is the certificate signing
the installed APK; do not assume another developer's debug fingerprint matches
yours. For Play-distributed builds, use the **app signing certificate** from Play
Console, which may differ from the upload certificate. Google's
[Android authorization guide](https://developer.android.com/identity/authorization)
explains these registration requirements.

### Register the client

1. Open **Google Auth Platform → Clients → Create client**.
2. Choose **Android** and a name such as `XCalendar Android Debug`.
3. Enter your **application ID** as Package name and the **SHA-1** above.
4. Create the client. Register additional clients for other signing certificates
   you use, such as release or Play app signing.

The resulting Android client ID is useful for identifying the registration in
Cloud Console. **There is no `GOOGLE_ANDROID_CLIENT_ID` property in XCalendar.**
The Android implementation uses Google Play services `AuthorizationClient` and
the installed package/signature. It does not read `google-services.json` or need
a Web client for its current on-device flow.

## 6. Create the iOS client

Skip this section if you are not building iOS.

### Choose a bundle ID that you can sign

The checked-in Xcode project currently resolves its bundle ID as
`${BUNDLE_ID}${TEAM_ID}`. With the defaults in
[Config.xcconfig](../app/iosApp/Configuration/Config.xcconfig), that is
`com.debanshu.xcalendar.XCalendar`.

For your own signed build, choose a unique bundle ID, for example
`org.example.xcalendar`, replacing `org.example` with your own identifier. You
can set `BUNDLE_ID` in the ignored `Google.local.xcconfig` file in step 8.

**Keep the repository's `TEAM_ID` variable empty.** Select the Apple signing team
in Xcode or pass `DEVELOPMENT_TEAM` to `xcodebuild`. Setting `TEAM_ID` would also
append the team ID to the bundle identifier and can make it differ from the
OAuth registration. Google's iOS client **Team ID** field is a separate field;
it does not require changing the repository's `TEAM_ID` variable.

### Register the client

1. Open **Google Auth Platform → Clients → Create client**.
2. Choose **iOS**, name it, and enter your final **Bundle ID** exactly.
3. The **App Store ID** can be omitted for local development. If supplying an
   Apple **Team ID**, use the one from your Apple developer team.
4. Create the client and copy the full **Client ID** and **iOS URL scheme**.

Google documents the mobile client fields in its
[credential creation guide](https://developers.google.com/workspace/guides/create-credentials).

The two values have this relationship; these examples are deliberately fake:

```text
Client ID:      123456789012-exampleios.apps.googleusercontent.com
iOS URL scheme: com.googleusercontent.apps.123456789012-exampleios
```

Reverse the order of the dot-separated parts of the full client ID, not the
characters within each part. Use only the scheme in Xcode, with no `://` or path.
Google explains this convention in its [iOS configuration guide](https://developers.google.com/identity/sign-in/ios/start-integrating#add_your_oauth_client_id_and_custom_url_scheme).

XCalendar's iOS implementation uses `ASWebAuthenticationSession` and derives the
callback as `REVERSED_CLIENT_ID:/oauthredirect`. The existing `Info.plist` already
references `$(GOOGLE_REVERSED_CLIENT_ID)`. The setup here only needs the local
values; it does not require adding a Google Sign-In SDK, CocoaPods, Firebase, or
a `GoogleService-Info.plist` file.

## 7. Create the desktop client

1. Open **Google Auth Platform → Clients → Create client**.
2. Choose **Desktop app** and a name such as `XCalendar Desktop`.
3. Create it and copy the full **Client ID**.

Use this type for the JVM desktop app on **Windows, macOS, and Linux**. The
macOS JVM application uses the desktop flow, separate from the iOS application.

XCalendar opens the system browser and waits on
`http://127.0.0.1:<temporary-port>/oauth2callback`. The OS chooses the port for each
attempt; no hosted redirect endpoint or fixed port is configured in the repo.
Use the Desktop app client type rather than configuring a Web application's
redirect list. Keep XCalendar running while completing the browser flow.

The current implementation reads only the desktop client ID and sends PKCE S256
with the authorization-code exchange. It has no client-secret setting. Google's
[desktop OAuth guide](https://developers.google.com/identity/protocols/oauth2/native-app)
describes the loopback flow and lists the token endpoint's client secret as
optional. Do not put a Web client secret in `API_KEY`.

## 8. Add local configuration

### Root local.properties

For a new checkout with no `local.properties`, copy the template:

```sh
cp local.properties.example local.properties
```

On PowerShell, use `Copy-Item local.properties.example local.properties`.
**If the file already exists, merge the settings instead of overwriting it** so
existing SDK or Calendarific configuration is preserved.

Fill in the full IDs for the platforms you are using, without quotes, angle
brackets, or `http://` prefixes:

```properties
GOOGLE_IOS_CLIENT_ID=123456789012-exampleios.apps.googleusercontent.com
GOOGLE_DESKTOP_CLIENT_ID=123456789012-exampledesktop.apps.googleusercontent.com
```

Replace both example IDs with your own. Leave unused platform keys blank.
Do not put the Android client ID in either of these fields. The
[app-config plugin](../build-logic/app-config/src/AppConfig.kt) generates
`BuildKonfig` during the build; do not edit generated Kotlin files.

### iOS local configuration

On a fresh iOS checkout, copy the iOS template:

```sh
cp app/iosApp/Configuration/Google.local.xcconfig.example \
  app/iosApp/Configuration/Google.local.xcconfig
```

If it already exists, update the values in place. For the example iOS client and
a custom bundle identifier, its contents would be:

```xcconfig
GOOGLE_REVERSED_CLIENT_ID = com.googleusercontent.apps.123456789012-exampleios
BUNDLE_ID = org.example.xcalendar
```

Replace both values with your own. Omit `BUNDLE_ID` if keeping the repository
default. The optional include in `Config.xcconfig` loads this file automatically.

Before building, confirm this mapping:

| Value | Must match |
| --- | --- |
| Android OAuth package + SHA-1 | The application ID and certificate of the APK you install |
| iOS OAuth Bundle ID | The signed app's `CFBundleIdentifier` |
| `GOOGLE_IOS_CLIENT_ID` | Your iOS OAuth client, including `.apps.googleusercontent.com` |
| `GOOGLE_REVERSED_CLIENT_ID` | The dot-reversed value of that same iOS client ID |
| `GOOGLE_DESKTOP_CLIENT_ID` | Your Desktop app OAuth client |

Both local files are ignored by Git. The `.example` templates are intentionally
tracked. Confirm the ignore rules with:

```sh
git check-ignore local.properties app/iosApp/Configuration/Google.local.xcconfig
```

Client IDs identify installed apps and are included in built binaries; they are
not account passwords. Keep personal configuration, downloaded credential files,
keystores, private keys, access/refresh tokens, and device IDs out of commits and
issue reports. This repository does not automatically ignore every possible
downloaded credential filename, so keep those downloads outside the checkout.

Rebuild and restart after changing configuration. Already-installed apps and
previously packaged desktop distributions still contain the old values.

## 9. Build and run

### Android

```sh
./kotlin build -m androidApp -p android
adb devices -l
./kotlin run -m androidApp --device-id YOUR_ANDROID_DEVICE_ID
```

Choose the device ID from your current `adb` output. Use a device/emulator with
Google Play services and an account you added as a test user. For release builds,
register the certificate that actually signs that release before testing OAuth.

### Desktop

```sh
./kotlin build -m desktopApp -p jvm
./kotlin run -m desktopApp
```

For a distributable folder built with the new configuration:

```sh
./kotlin do packageDesktop -m desktopApp
```

Keep the resulting `XCalendar.jar` and `lib/` folder together. See the
[README distribution instructions](../README.md#distribution) for launchers and
native installers. Build on each target OS to include its native libraries.

### iOS simulator

```sh
./kotlin build -m iosApp -p iosSimulatorArm64
./kotlin run -m iosApp -p iosSimulatorArm64
```

Choose a simulator runtime supported by the installed Xcode SDK. The simulator
uses the same iOS OAuth client when its bundle ID matches the device build.

### Your physical iPhone

1. Connect the iPhone to your Mac, unlock it, and trust the computer if prompted.
2. Enable Developer Mode on the phone if Xcode requests it. Finish any required
   restart/confirmation on the device.
3. Open `app/iosApp/module.xcodeproj` in Xcode and sign in to your Apple account
   under Xcode settings if needed.
4. Select the **iosApp** target, enable automatic signing, and select your team.
   Keep the resolved bundle identifier identical to your Google iOS client.
5. Select the **app** scheme and your connected iPhone as the run destination.
   Run the app. Its build phase invokes the checked-in Kotlin wrapper.

For a repeatable terminal workflow, use your own device and team identifiers:

```sh
xcrun devicectl list devices
```

Replace the placeholders below before running. This uses a separate ignored
build directory and leaves the repository's `TEAM_ID` empty:

```sh
xcodebuild -project app/iosApp/module.xcodeproj -scheme app \
  -configuration Debug -destination 'id=YOUR_IPHONE_DEVICE_ID' \
  -derivedDataPath build/iphone-setup -allowProvisioningUpdates \
  TEAM_ID= DEVELOPMENT_TEAM=YOUR_APPLE_TEAM_ID CODE_SIGNING_ALLOWED=YES build
```

The bundle ID comes from `Config.xcconfig` and your optional local override.
Your Apple account must be allowed to sign it. Automatic provisioning may create
or update development profiles for your account.

Before installing, inspect the actual built app:

```sh
/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' \
  build/iphone-setup/Build/Products/Debug-iphoneos/XCalendar.app/Info.plist
/usr/libexec/PlistBuddy -c 'Print :CFBundleURLTypes:0:CFBundleURLSchemes:0' \
  build/iphone-setup/Build/Products/Debug-iphoneos/XCalendar.app/Info.plist
```

The first output must equal the Google iOS client's Bundle ID; the second must
equal your reversed iOS client ID. Install and launch with those verified values:

```sh
xcrun devicectl device install app --device YOUR_IPHONE_DEVICE_ID \
  build/iphone-setup/Build/Products/Debug-iphoneos/XCalendar.app
xcrun devicectl device process launch --device YOUR_IPHONE_DEVICE_ID YOUR_BUNDLE_ID
```

The project currently names the app `XCalendar.app`. Adjust these paths if your
fork changes `APP_NAME`. `./kotlin build -m iosApp -p iosArm64` can compile the
device target separately, but compilation alone does not install a signed app.

## 10. Connect and verify synchronization

Use a test account or create a dedicated test calendar in Google Calendar first.
The steps below intentionally write real events to the selected Google calendar.

1. Open XCalendar and tap **Connect** in the top bar, then **Connect Google
   Calendar** in the dialog.
2. Select the Google account added under your project's test users. Review the
   app name and allow both Calendar permissions. Complete any Google password, passkey, or 2FA prompts. Finish the flow within
   ten minutes on iOS/desktop.
3. Return to XCalendar. The account dialog should show your email, calendar list,
   and a last-synced timestamp. Close it to view events for the displayed month.
4. Create a uniquely named event, such as `XCalendar setup test`, on your writable
   test calendar. Confirm its title, date, time zone, and duration in
   [Google Calendar](https://calendar.google.com/).
5. Edit the event in XCalendar and confirm the change in Google Calendar.
6. Change the event in Google Calendar, then use **Sync all** in XCalendar's
   account dialog. Verify the remote edit appears.
7. Try an all-day event and an existing recurring occurrence. Editing an
   occurrence must not change the entire series. If available, also check that
   a shared read-only calendar cannot be edited.
8. Delete your test event in Google Calendar and sync XCalendar to confirm removal.
9. Fully close and reopen XCalendar. Connected accounts should appear without a
   browser prompt, followed by a refresh. Repeat offline: the last cached events
   should remain available, with an error for the failed refresh.
10. Test **Remove account**, then fully close and reopen. That account must stay
    removed; any other connected accounts should return. Reconnect when finished.
11. For a dedicated test account, revoke the app grant in Google's account
    connections, then sync. Use **Reconnect** for that account and complete any
    Google identity verification. Other accounts and an open event draft should
    remain intact.

Repeat on every platform you intend to support. A successful build verifies
compilation, not that Google accepted the project/client configuration. Declining
consent should leave the app usable and show a connection error without uploading
demo events.

## Using several Google accounts together

One installation can connect personal, work, or other Google accounts and display
their calendars together. Each account authorizes the same platform OAuth client;
you do not create another Cloud project or client for every login. While the
project is in Testing, add **every account you intend to connect** to
**Google Auth Platform → Audience → Test users**. Work accounts may also need
their Workspace administrator's permission. See Google's
[consent configuration guide](https://developers.google.com/workspace/guides/configure-oauth-consent).

1. Connect your first account using **Connect → Connect Google Calendar**.
2. Open the account control in the top bar and select **Add Google account**.
3. Choose a different account in Google's account picker and grant Calendar access.
4. The dialog now groups calendars under their account names/emails. Use the
   checkboxes to show or hide each calendar in the combined calendar view.
5. When creating an event, choose a calendar under the desired account's email.
   Only writable calendars are offered. XCalendar uses that account's session
   to save; editing an existing event retains its account and calendar.
6. Use **Sync all** to refresh connected accounts. Each account has its own
   last-synced time and errors. A failed account retains its prior snapshot while
   the other accounts can finish refreshing.
7. **Remove account** signs out just that account and clears its cached data.
   The remaining accounts stay connected, and no Google events are deleted.

Selecting an already-connected account through **Add Google account** replaces
its authorization session without adding a duplicate account. Access-token
expiry is normally handled automatically. If Google requires new consent or
identity verification, use **Reconnect** on the affected account. Cancelling or failing to add an account keeps
existing accounts connected.

If two accounts subscribe to the same Google calendar, it appears under both
accounts and may display events twice. Hide one copy using its checkbox. The
copies retain their account-specific permissions and event identifiers.

Connections survive app restarts. Calendar visibility choices currently last
for the running app session and need to be selected again after restarting.

## Staying signed in, refresh tokens, and 2FA

On launch, XCalendar loads saved accounts and their cached calendars before
contacting Google. A temporary network or Google server failure preserves those
accounts and cached events. It never opens a login screen during background
refresh. The first upgrade from the older, memory-only implementation requires
one fresh connection per account because that version saved no refresh token.

Credential storage depends on the platform:

| Platform | Storage and requirements |
| --- | --- |
| iOS | Account metadata and refresh tokens are stored in the app's Keychain item, accessible after the first device unlock and restricted to this device. |
| Android | Account identities are encrypted with an Android Keystore AES-GCM key in app-private, backup-excluded storage. Google Play services manages the OAuth tokens; XCalendar silently requests tokens for the saved account. |
| macOS desktop | An AES-GCM encrypted archive under `~/Library/Application Support/XCalendar/google-sessions/`; its encryption key is in macOS Keychain. |
| Windows desktop | An AES-GCM encrypted archive under `%APPDATA%/XCalendar/google-sessions/`; its encryption key is in Windows Credential Manager. |
| Linux desktop | An AES-GCM encrypted archive under `~/.local/share/XCalendar/google-sessions/`; its encryption key is held by a Freedesktop Secret Service provider over the session D-Bus (commonly GNOME Keyring). A running, unlocked provider is required. The Java backend does not require `libgnome-keyring` or `libsecret` native libraries; see [java-keyring](https://github.com/javakeyring/java-keyring). |

Desktop uses the pinned `java-keyring` dependency for native credential-store
access. There is no plaintext credential fallback. Desktop vault access follows
the OS user's permissions; it is not a guarantee of isolation from other
programs running as that user. A development launch through a shared JVM can
also share its Keychain identity. Review the library's
[packaging and security considerations](https://github.com/javakeyring/java-keyring#security-concerns)
before distributing a signed desktop build. Calendar/event snapshots in
Room are separate from the credential vault; they are not encrypted by this
session-storage feature. Credentials belong to the current device/OS user and
are not a portable backup or a way to share a Google login with other people.

On iOS/desktop, refresh credentials are requested using Google's offline OAuth
flow. Access tokens remain in memory, renew before expiry, and a rejected token
triggers one fresh-token retry. Rotated refresh tokens are saved securely. On
Android, the rejected token is cleared from the managed token cache before retry.
Revoked/expired grants and missing Calendar permissions mark only the affected
account as needing **Reconnect**. Its cached events remain visible. A reconnect
started for a particular account must finish with that same account selected.
See Google's [native-app OAuth flow](https://developers.google.com/identity/protocols/oauth2/native-app)
and [credential-storage recommendations](https://developers.google.com/identity/protocols/oauth2/resources/best-practices).

Passwords, passkeys, one-time codes, and 2FA recovery codes stay in Google's UI.
XCalendar never collects or stores them. **Reconnect** opens Google's flow with
a ten-minute time limit, including any identity verification Google requires.
Cancellation leaves existing accounts and an open event draft intact. Complete
reconnection, then retry saving the draft. This does not bypass Google's 2FA or
Workspace policies.

Google can expire or revoke a refresh token. In particular, an external project
in **Testing** requesting Calendar scopes has a seven-day refresh-token lifetime.
Reconnect when required; persistent local storage cannot extend that lifetime.
See Google's [refresh-token expiration guidance](https://developers.google.com/identity/protocols/oauth2#expiration).

If the OS vault is locked or unavailable, unlock it and use **Retry saved sign-in**.
XCalendar does not overwrite unreadable credentials or silently discard accounts.
An explicit **Remove account** deletes that account's saved sign-in and cached
snapshot. If secure deletion fails, it reports the error instead of claiming the
account was removed. Removal does not revoke Google's grant or affect your other
accounts. An OS-key loss or damaged archive needs a local reset before connecting
again; see the recovery instructions below.

## Current behavior and limits

| Area | Current implementation |
| --- | --- |
| Accounts | Multiple saved Google accounts, with a combined view and per-calendar visibility controls. Visibility choices reset on restart. |
| Session storage | OS-protected durable account restoration and automatic token refresh. Revoked/expired grants require reconnecting only the affected account. Google handles 2FA. |
| Local data | Account-specific calendar/event snapshots are stored in Room. Saved accounts restore their snapshots on launch, including offline. Removing an account deletes only its snapshot. |
| Refresh range | The displayed month plus its preceding and following months. Moving to another month changes the range; this is not an import of the entire calendar history. |
| Refresh timing | Connect, month changes, **Sync all**, and a 60-second timer while the app is resumed and no event editor is open. No background or push sync. |
| Consistency | All calendar/event pages for an account must succeed before replacing its snapshot. A failure retains that account's prior snapshot while other accounts continue refreshing. Refresh imports remote edits and removals. |
| Event writes | Online creation and edits of standard events on writable calendars. Saving waits for Google's response; there is no offline write queue. |
| Conflicts | Updates use the event version opened in the editor. A changed version requires refreshing/reopening. Stable creation IDs prevent duplicate retries; a different draft is not silently accepted as a prior successful save. |
| Recurrence | Existing occurrences are expanded by Google. An edit affects one occurrence. Creating recurring series or changing an entire series is not supported. |
| Other unsupported operations | Moving events between calendars, editing special Google event types, creating/managing calendars, and deleting Google events in XCalendar. Use Google Calendar for these operations, then refresh. |
| Remove account | Clears only the selected account's session and cached snapshot. It does not delete Google events, revoke Google's permission grant, or sign out other accounts. |
| Development database | Schema upgrades currently recreate the local database. Local data can be lost; migrations are intentionally deferred during development. |

Patches update editor-owned fields and preserve other Google fields such as
attendees and conferencing. Demo events remain separate and are never uploaded
automatically. The app uses bounded full refreshes, not Google's incremental
`syncToken` protocol.

To remove the project's Google permission grant, manage it through your
[Google account connections](https://myaccount.google.com/connections). To remove
the local configuration, clear the relevant local settings and rebuild; deleting
the files does not change an app already installed on a device.

## Troubleshooting

| Symptom | Check / recovery |
| --- | --- |
| “Google sign-in is not configured for this app yet.” | On iOS/desktop, fill the correct key in root `local.properties`, then rebuild and restart/reinstall. Check spelling and avoid quotes or URL prefixes. |
| `access_denied`, app in Testing, or account not allowed | Add the account selected on the device under **Audience → Test users** in the same project as the client. Grant both Calendar permissions when reconnecting. |
| `org_internal` or `admin_policy_enforced` | Check Audience and Workspace administrator policy. A personal account cannot use an organization-only client. Use an allowed account/project or contact its administrator. |
| Calendar API disabled / permission error on first sync | Enable **Google Calendar API** in the project that owns the client. Confirm both scopes are configured and granted. Retry after the Console change has taken effect. |
| Android sign-in fails while iOS/desktop work | Inspect the exact installed APK's application ID and SHA-1. Register the certificate for that variant and check Google Play services. An Android client ID pasted into `local.properties` cannot fix a signature mismatch. |
| Android works in debug but fails after release/Play installation | Register the release or Play app-signing certificate, as appropriate. Recheck the final application ID. |
| `redirect_uri_mismatch`, `invalid_client`, or no return to the iOS app | Confirm the client type is iOS, compare the built bundle ID with the registration, and compare the built URL scheme with the reversed iOS client ID. Check for an accidental `TEAM_ID` suffix; rebuild/reinstall. |
| iPhone build fails signing or provisioning | Select your Apple team and a bundle ID it can sign, pair/unlock the phone, and enable Developer Mode when requested. Check Xcode's resolved build settings before changing OAuth configuration. |
| Toolchain iOS run cannot find the app bundle | Use the Xcode scheme or the explicit `xcodebuild` and `devicectl` workflow above. The current artifact is `XCalendar.app`; do not install a guessed `.app` path. |
| iOS simulator destination unavailable | Install/select a simulator runtime supported by your Xcode SDK, or use your physical iPhone. A newer runtime alone does not guarantee SDK compatibility. |
| Desktop browser cannot return to XCalendar or times out | Confirm a **Desktop app** client, keep the app running, and finish within ten minutes, including Google identity verification. Allow the browser to reach the app's local loopback listener; check local firewall/proxy policy. Start a fresh connection after a timeout. |
| Browser says connected, but the app shows an error | The browser callback message only confirms receipt of a code. Token exchange and calendar loading can still fail. Read the app's error and recheck client type, scopes, API enablement, and connectivity. |
| “Allow ... calendar list and event access ...” | Reconnect and grant both Calendar scopes. Profile-only consent or granting only event access is insufficient. |
| Events outside the current month are missing | Only the displayed and adjacent months are loaded. Navigate to the desired month and sync. Verify the selected Google account and calendar. |
| An event is read-only / says to edit in Google Calendar | Check calendar write permissions and the event type. Special types and some operations are unsupported in XCalendar. |
| “This event changed” / conflict when saving | Sync, close and reopen the event, then reapply your edit. XCalendar deliberately avoids overwriting newer Google changes. |
| Refresh fails and old events remain visible | The last complete snapshot is retained. Restore connectivity/permission and sync again; old data is not proof of a successful current refresh. |
| Reconnect requested after relaunch | The old memory-only build needs one fresh connection per account. Otherwise check whether the OAuth client changed, the grant was revoked, test authorization expired, or the OS vault is unavailable. |
| Saved sign-in cannot be restored | Unlock the device/system keyring and use **Retry saved sign-in**. Do not remove credentials for a temporary vault or network failure. If storage remains damaged or its key is lost, follow the local reset instructions below. |
| Adding another account is denied | Add that account's email to the project's test users and check any Workspace restrictions. Existing connected accounts remain available. |
| Events appear twice after adding another account | Check whether both accounts subscribe to the same calendar. Hide one copy in the account dialog. |
| Authorization expires during extended testing | External apps in Testing receive refresh tokens with a seven-day lifetime when requesting Calendar scopes. Reconnect; see Google's [token expiration guidance](https://developers.google.com/identity/protocols/oauth2#expiration). |

An unverified-app message can appear for a development project. Check that it
identifies your own project and that the account is an intended tester. Public
releases need the applicable verification process below. Do not treat test-mode
warnings or limits as a production onboarding flow.

When reporting a problem, include the platform, build variant, error text, client
**type**, and whether bundle/package/signature values match. Redact tokens,
authorization codes, calendar contents, and personal account details from logs
and screenshots.

### Resetting unrecoverable local sign-in storage

Use this only after ruling out a locked vault, missing Secret Service provider,
and temporary permissions problems. A reset removes all saved Google connections
on that installation; it does not delete remote Google events. Close XCalendar
before changing its local storage.

- **Desktop:** remove the `google-sessions` directory at the platform path above
  and the app's vault entry (service `com.debanshu.xcalendar.google-sessions`,
  account `aes-gcm-key-v1`) using your OS credential manager. Reopen and connect
  each account. Leave the calendar database alone.
- **Android:** clearing XCalendar's app storage resets the encrypted archive and
  local database. This also removes local/demo data. Reopen and connect again.
- **iOS:** Keychain entries may survive uninstalling an app. Reinstalling alone
  is not a reliable reset. A developer troubleshooting a damaged item must
  explicitly remove only this app's Keychain service
  `com.debanshu.xcalendar.google.sessions.v1`, account `sessions`, using a scoped
  debug build. The normal app intentionally has no automatic vault-wipe path.

## Public distribution

Publishing the source under an open-source license does not configure or verify
an OAuth project. Maintainers distributing a configured build should:

1. Own the project's branding and contact details and provide the required public
   app/privacy information. Request only scopes used by the app.
2. Review Google's verification requirements and complete the applicable process
   before general distribution. Changing the publishing status is not itself
   verification. See Google's [sensitive-scope verification guide](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification).
3. Register the actual production package/bundle IDs and Android signing
   certificates. Generate platform configuration in the build environment;
   do not commit a developer's local settings or downloaded credentials.
4. Test consent, reconnect, account removal, permission revocation, multiple accounts,
   conflicts, and all supported platforms against the production registration.
5. Test OS-vault permissions, locked-vault recovery, restart/offline restoration,
   and refresh-token rotation on every supported OS. Add database migrations
   before promising data-preserving upgrades; the development database currently
   allows destructive schema changes.

Contributors running their own builds can remain on a separate development
project with their own testers. This keeps their consent settings, API usage,
and client registrations under their control.

## Implementation and automated checks

Useful source entry points:

| Component | Source |
| --- | --- |
| Scopes and Google data mapping | [GoogleModels.kt](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleModels.kt) |
| Durable account archive | [GoogleSessionStore.kt](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleSessionStore.kt) |
| OS-protected storage | [iOS](../app/shared/src@ios/com/debanshu/xcalendar/data/google/GoogleSecureStorage.ios.kt), [Android](../app/shared/src@android/com/debanshu/xcalendar/data/google/GoogleSecureStorage.android.kt), [desktop](../app/shared/src@jvm/com/debanshu/xcalendar/data/google/GoogleSecureStorage.desktop.kt) |
| Browser token exchange, state and PKCE | [GoogleAuth.kt](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleAuth.kt) |
| Android authorization | [GoogleAuth.android.kt](../app/shared/src@android/com/debanshu/xcalendar/data/google/GoogleAuth.android.kt) |
| iOS browser session and redirect | [GoogleAuth.ios.kt](../app/shared/src@ios/com/debanshu/xcalendar/data/google/GoogleAuth.ios.kt) |
| Desktop browser and loopback callback | [GoogleAuth.desktop.kt](../app/shared/src@jvm/com/debanshu/xcalendar/data/google/GoogleAuth.desktop.kt) |
| Google API calls and sync repository | [GoogleCalendarApi.kt](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleCalendarApi.kt), [GoogleCalendarRepository.kt](../app/shared/src/com/debanshu/xcalendar/data/google/GoogleCalendarRepository.kt) |
| Build-time client configuration | [AppConfig.kt](../build-logic/app-config/src/AppConfig.kt) |

Run the relevant tests from the repository root:

```sh
./kotlin test -m app-config -p jvm
./kotlin test -m shared -p jvm
./kotlin test -m shared -p android
./kotlin test -m shared -p iosSimulatorArm64
```

The iOS tests require a supported simulator on macOS even if you manually test
the app on a real iPhone. Automated coverage includes scope/callback validation,
pagination, time mapping, conflict/retry handling, per-account token/write routing,
combined calendar visibility, atomic cache replacement, restart/offline session
restoration, refresh-token rotation, transient-failure retention, and account
removal without resurrection.
These tests use controlled inputs and do not authenticate your Cloud project;
complete the live checklist in step 10 as well.

The simulator's native Keychain round-trip uses dedicated test services and fake
values. Kotlin Toolchain's default command-line test executable has no Keychain
entitlement, so this one check reports a skip for `errSecMissingEntitlement`
(`-34018`); the shared logic tests still run. Other Keychain errors fail the
check. Validate the native round-trip in a signed app-hosted test with Keychain
access, and verify real sign-in and restart in the signed app. Adding synthetic
entitlements to an ad-hoc command-line executable alone may be rejected by
CoreSimulator's launch policy. A skipped native check is not proof
that secure storage works on a device. Desktop encryption tests use a fake vault;
also exercise the actual credential manager on each desktop OS you distribute.
