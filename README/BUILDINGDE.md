# Demeter kompilieren

[← Zurück zum Index](../README.md)

---

## Build-Umgebung

Demeter wurde geschrieben und kompiliert gegen:

- JDK 7 (Oracle oder OpenJDK 7)
- Android SDK 29
- Gradle 1.x
- Android Gradle Plugin 1.x

Jeder kann diese Umgebung reproduzieren, indem er genau diese Versionen installiert. AIDE ist nicht nötig — AIDE nutzt der Autor im Alltag aus Bequemlichkeit auf dem Handy, aber das Projekt kompiliert mit dem Standard-Toolchain von 2014.

---

## Option A — AIDE (empfohlen auf dem Handy)

1. Installiere AIDE aus dem Play Store (Version 3.x oder höher).
2. Öffne das Repository als bestehendes Projekt.
3. Build → Run. Fertig.

---

## Option B — Desktop (JDK 7 + SDK 29 + Gradle 1.x)

1. Installiere JDK 7 (Oracle oder OpenJDK).
2. Lade das Android SDK 29 aus Googles Archiven.
3. Installiere Gradle 1.x — nicht 4 oder 7.
4. Setze `ANDROID_HOME` auf das SDK.
5. Führe `gradle assembleDebug` aus.

Wenn du es auf modernes Gradle portierst und den Port beitragen willst, schick eine PR. Beiträge sind willkommen.

---

## Warum Java 7?

Weil Demeters echtes Ziel Einsteiger-Handys mit Android 13 und 4 GB RAM sind (Unisoc T606, Helio G85, Mali-G57 MP1), nicht Pixels. Lambdas, Streams und try-with-resources aus Java 8 bringen einen Alloc-Overhead, der auf diesen Chips spürbar ist. Java 7 ist keine Laune: es ist die technisch korrekte Entscheidung für das Ziel.

---

## Was du NICHT brauchst

- Modernes Android Studio
- Gradle 7+
- Java 8+
- Kotlin
- Kotlin DSL
- Externe Abhängigkeiten

Das Projekt hat null Fremd-Abhängigkeiten. Alles, was es nutzt, kommt aus dem Standard-Android-SDK.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
