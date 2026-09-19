# 编译 Demeter

[← 返回索引](../README.md)

---

## 编译环境

Demeter 的编写和编译基于：

- JDK 7（Oracle 或 OpenJDK 7）
- Android SDK 29
- Gradle 1.x
- Android Gradle Plugin 1.x

任何人只要安装这些确切版本，都可以复现这套环境。不需要 AIDE —— AIDE 只是作者日常在手机上图方便用的，但项目本身可以用 2014 年的标准工具链编译。

---

## 方案 A —— AIDE（手机推荐）

1. 从 Play Store 安装 AIDE（3.x 或更高版本）。
2. 将仓库作为现有项目打开。
3. Build → Run。完成。

---

## 方案 B —— 桌面端（JDK 7 + SDK 29 + Gradle 1.x）

1. 安装 JDK 7（Oracle 或 OpenJDK）。
2. 从 Google 存档下载 Android SDK 29。
3. 安装 Gradle 1.x —— 不是 4 也不是 7。
4. 把 `ANDROID_HOME` 指向 SDK。
5. 运行 `gradle assembleDebug`。

如果你成功移植到现代 Gradle 并想贡献这份移植，请提交 PR。欢迎贡献。

---

## 为什么是 Java 7？

Demeter 是在手机上用 AIDE 编写和编译的，AIDE 使用 JDK 7 和 Gradle 1.x。这不是性能决策 —— 只是作者手头每天在用的工具。如果想移植到现代工具链，欢迎提交 PR。

---

## 你【不需要】什么

- 现代 Android Studio
- Gradle 7+
- Java 8+
- Kotlin
- Kotlin DSL
- 外部依赖

本项目零第三方依赖。所用的一切都来自标准 Android SDK。

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
