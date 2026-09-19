# Compiler Demeter

[← Retour à l'index](../README.md)

---

## Environnement de compilation

Demeter a été écrit et compilé avec :

- JDK 7 (Oracle ou OpenJDK 7)
- Android SDK 29
- Gradle 1.x
- Android Gradle Plugin 1.x

N'importe qui peut reproduire cet environnement en installant ces versions exactes. AIDE n'est pas nécessaire — AIDE est ce que l'auteur utilise au quotidien par commodité sur mobile, mais le projet compile avec le toolchain standard de 2014.

---

## Option A — AIDE (recommandé sur téléphone)

1. Installez AIDE depuis le Play Store (version 3.x ou supérieure).
2. Ouvrez le dépôt comme projet existant.
3. Build → Run. Terminé.

---

## Option B — Desktop (JDK 7 + SDK 29 + Gradle 1.x)

1. Installez le JDK 7 (Oracle ou OpenJDK).
2. Téléchargez l'Android SDK 29 depuis les archives de Google.
3. Installez Gradle 1.x — pas 4 ni 7.
4. Configurez `ANDROID_HOME` pour pointer vers le SDK.
5. Lancez `gradle assembleDebug`.

Si vous rencontrez des erreurs lors du port vers un Gradle moderne, il existe une branche communautaire avec le port réalisé par des contributeurs.

---

## Pourquoi Java 7 ?

Parce que la cible réelle de Demeter, ce sont des téléphones d'entrée de gamme sous Android 13 avec 4 GB de RAM (Unisoc T606, Helio G85, Mali-G57 MP1), pas des Pixels. Les lambdas, streams et try-with-resources de Java 8 apportent un overhead d'allocation qui se ressent sur ces puces. Java 7 n'est pas un caprice : c'est la décision technique correcte pour la cible.

---

## Ce dont vous n'avez PAS besoin

- Android Studio moderne
- Gradle 7+
- Java 8+
- Kotlin
- Kotlin DSL
- Dépendances externes

Le projet n'a aucune dépendance tierce. Tout ce qu'il utilise vient du SDK Android standard.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
