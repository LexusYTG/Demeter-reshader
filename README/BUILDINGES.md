# Compilando Demeter

[← Volver al índice](../README.md)

---

## Entorno de compilación

Demeter fue escrito y compilado contra:

- JDK 7 (Oracle u OpenJDK 7)
- Android SDK 29
- Gradle 1.x
- Android Gradle Plugin 1.x

Cualquiera puede reproducir este entorno instalando esas versiones exactas. No hace falta AIDE — AIDE es lo que el autor usa en el día a día por comodidad en móvil, pero el proyecto compila con el toolchain estándar de 2014.

---

## Opción A — AIDE (recomendado para el teléfono)

1. Instalá AIDE desde Play Store (versión 3.x o superior).
2. Abrí el repo como proyecto existente.
3. Build → Run. Listo.

---

## Opción B — Desktop (JDK 7 + SDK 29 + Gradle 1.x)

1. Instalá JDK 7 (Oracle u OpenJDK).
2. Descargá el Android SDK 29 desde los archivos de Google.
3. Instalá Gradle 1.x — no 4 ni 7.
4. Configurá `ANDROID_HOME` apuntando al SDK.
5. Ejecutá `gradle assembleDebug`.

Si lográs portarlo a Gradle moderno y querés contribuir el port, mandá un PR. Los aportes son bienvenidos.

---

## ¿Por qué Java 7?

Porque el target real de Demeter son celulares de entrada con Android 13 y 4 GB de RAM (Unisoc T606, Helio G85, Mali-G57 MP1), no Pixels. Las lambdas, streams y try-with-resources de Java 8 traen overhead de alocación que en esos chips se siente. Java 7 no es un capricho: es la decisión técnica correcta para el target.

---

## ¿Qué NO hace falta?

- Android Studio moderno
- Gradle 7+
- Java 8+
- Kotlin
- Kotlin DSL
- Dependencias externas

El proyecto no tiene una sola dependencia de terceros. Todo lo que usa viene del SDK estándar de Android.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
