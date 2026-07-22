# NotiPeek

English | [繁體中文](README.zh-TW.md)

An Android app that lets you **read incoming messages (LINE, and any other app)
without marking them as read**. It captures notifications and stores them
locally, so you can peek at a LINE message without opening the chat — LINE never
sends a read receipt.

> **Status: early scaffold (v0.1.0).** Core capture + local storage + a minimal
> UI are implemented. Not yet device-tested — build it in Android Studio to run.

## How it works — and why it doesn't mark messages as read

LINE only sends a read receipt when **you open the chat**. NotiPeek never opens
the chat. Instead it registers a **`NotificationListenerService`**: once you
grant *Notification access*, Android delivers a copy of every posted notification
to the app. Reading the notification is a system-level action the source app has
no visibility into — so a LINE message read this way stays **unread** in LINE.

There is no other way to do this: LINE offers no public API for reading messages.
The notification stream is the only source.

## Honest limitations (inherent to the mechanism, not bugs)

- **Only messages that arrive while access is granted are captured.** There is no
  way to fetch history — if it wasn't delivered as a notification, it isn't here.
- **LINE notifications must be enabled.** If you silence LINE, nothing arrives.
- **Stickers / images / voice** usually appear as placeholder text ("[Sticker]",
  "[Photo]") — the actual media is not in the notification.
- **Very long messages may be truncated** to the notification preview.
- Messaging apps re-post the whole conversation on each new message; NotiPeek
  **deduplicates** so each line is stored once.

## Features

- Captures notifications from **any** app (general-purpose peeker; LINE is the
  main use case).
- Extracts **individual messages** from `MessagingStyle` notifications (LINE,
  WhatsApp, Messenger …) with sender + timestamp; falls back to inbox / big-text
  / plain styles for everything else.
- **Local history** in a Room database — survives notification dismissal.
- Grouped by app; tap to see the message stream; clear per-app or all.
- **Privacy by design:** the app declares **no `INTERNET` permission** —
  captured messages never leave the device.

## Requirements & compatibility

- **Android 8.0 (API 26) or newer** — `minSdk 26`, `targetSdk 34`,
  `compileSdk 34`.
- Built with **AGP 8.5.2 / Gradle 8.9 / Kotlin 1.9.24 / Jetpack Compose
  (BOM 2024.09) / Room 2.6.1**. JDK 17.
- No third-party services; only AndroidX / Jetpack libraries.

## Build & run

1. Open the project in **Android Studio** (Koala / 2024.1+). It will download the
   Gradle wrapper and SDK components on first sync.
   - Command line instead of the IDE? Generate the wrapper once with a local
     Gradle 8.9 (`gradle wrapper`), then `./gradlew assembleDebug`.
2. Run on a device or emulator (Android 8.0+).
3. On first launch, tap **前往開啟通知存取 / Grant notification access** and enable
   NotiPeek in the system list.
4. Send yourself a LINE (or other) message — it appears in NotiPeek, and stays
   **unread** in LINE.

> `local.properties` (your SDK path) and the Gradle wrapper JAR are not committed;
> Android Studio provides both.

## Project structure

```
app/src/main/java/com/notipeek/
├─ NotiPeekApp.kt                     Application; owns the repository
├─ data/                             Room: entity, DAO, database, repository
│  ├─ CapturedMessage.kt             one stored message (+ dedupe key)
│  ├─ MessageDao.kt                  queries + per-app summaries
│  ├─ AppDatabase.kt
│  └─ MessageRepository.kt
├─ service/
│  └─ PeekNotificationListener.kt    THE CORE: capture + MessagingStyle parsing
└─ ui/
   ├─ MainActivity.kt                Compose screens (home / conversation)
   ├─ MessageViewModel.kt
   └─ Theme.kt
```

## Portability / migration notes

- Pure AndroidX/Jetpack; no vendor SDKs — clones and builds anywhere Android
  Studio runs (Windows / macOS / Linux).
- All state is a local Room DB (`notipeek.db`) on the device; nothing is synced
  or uploaded. Moving to a new phone starts fresh (by design — messages stay on
  the device that received them).
- Only the notification listener component is registered by the system by name;
  it is kept by a ProGuard rule for release builds.

## Ethical note

Use this on **your own device, for your own messages**. Reading other people's
private conversations without consent may be illegal in your jurisdiction.

## License

MIT License, see [LICENSE](LICENSE).
