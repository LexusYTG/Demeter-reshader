# Compilare Demeter

[← Torna all'indice](../README.md)

---

## Ambiente di compilazione

Demeter è stato scritto e compilato con:

- JDK 7 (Oracle o OpenJDK 7)
- Android SDK 29
- Gradle 1.x
- Android Gradle Plugin 1.x

Chiunque può riprodurre questo ambiente installando quelle versioni esatte. AIDE non è necessario — AIDE è quello che l'autore usa quotidianamente per comodità sul telefono, ma il progetto compila con il toolchain standard del 2014.

---

## Opzione A — AIDE (consigliato sul telefono)

1. Installa AIDE dal Play Store (versione 3.x o superiore).
2. Apri il repo come progetto esistente.
3. Build → Run. Fatto.

---

## Opzione B — Desktop (JDK 7 + SDK 29 + Gradle 1.x)

1. Installa JDK 7 (Oracle o OpenJDK).
2. Scarica l'Android SDK 29 dagli archivi di Google.
3. Installa Gradle 1.x — non 4 o 7.
4. Imposta `ANDROID_HOME` puntando all'SDK.
5. Esegui `gradle assembleDebug`.

Se incontri errori portandolo a Gradle moderno, esiste un branch della community con il port fatto dai collaboratori.

---

## Perché Java 7?

Perché il target reale di Demeter sono telefoni di fascia bassa con Android 13 e 4 GB di RAM (Unisoc T606, Helio G85, Mali-G57 MP1), non i Pixel. Lambda, stream e try-with-resources di Java 8 portano un overhead di allocazione che su quei chip si sente. Java 7 non è un capriccio: è la decisione tecnica corretta per il target.

---

## Cosa NON serve

- Android Studio moderno
- Gradle 7+
- Java 8+
- Kotlin
- Kotlin DSL
- Dipendenze esterne

Il progetto non ha nemmeno una dipendenza di terze parti. Tutto ciò che usa arriva dall'SDK Android standard.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
