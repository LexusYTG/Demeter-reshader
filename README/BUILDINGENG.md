# Building Demeter

[← Back to index](../README.md)

---

## Build environment

Demeter was written and compiled against:

- JDK 7 (Oracle or OpenJDK 7)
- Android SDK 29
- Gradle 1.x
- Android Gradle Plugin 1.x

Anyone can reproduce this environment by installing those exact versions. AIDE is not required — AIDE is what the author uses day-to-day for mobile convenience, but the project compiles with the standard 2014 toolchain.

---

## Option A — AIDE (recommended on phone)

1. Install AIDE from Play Store (version 3.x or higher).
2. Open the repo as an existing project.
3. Build → Run. Done.

---

## Option B — Desktop (JDK 7 + SDK 29 + Gradle 1.x)

1. Install JDK 7 (Oracle or OpenJDK).
2. Download Android SDK 29 from Google's archives.
3. Install Gradle 1.x — not 4 or 7.
4. Set `ANDROID_HOME` to point to the SDK.
5. Run `gradle assembleDebug`.

If you manage to port it to modern Gradle and want to contribute the port, send a PR. Contributions are welcome.

---

## Why Java 7?

Demeter is written and compiled on a phone with AIDE, which uses JDK 7 and Gradle 1.x. This isn't a performance decision — it's the tool the author has and uses daily. If you want to port it to a modern toolchain, send a PR.

---

## What you DON'T need

- Modern Android Studio
- Gradle 7+
- Java 8+
- Kotlin
- Kotlin DSL
- External dependencies

The project has zero third-party dependencies. Everything it uses comes from the standard Android SDK.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
