# Compilando o Demeter

[← Voltar ao índice](../README.md)

---

## Ambiente de compilação

O Demeter foi escrito e compilado contra:

- JDK 7 (Oracle ou OpenJDK 7)
- Android SDK 29
- Gradle 1.x
- Android Gradle Plugin 1.x

Qualquer pessoa pode reproduzir esse ambiente instalando essas versões exatas. Não é preciso AIDE — o AIDE é o que o autor usa no dia a dia por conveniência no celular, mas o projeto compila com o toolchain padrão de 2014.

---

## Opção A — AIDE (recomendado no celular)

1. Instale o AIDE pela Play Store (versão 3.x ou superior).
2. Abra o repositório como projeto existente.
3. Build → Run. Pronto.

---

## Opção B — Desktop (JDK 7 + SDK 29 + Gradle 1.x)

1. Instale o JDK 7 (Oracle ou OpenJDK).
2. Baixe o Android SDK 29 dos arquivos do Google.
3. Instale o Gradle 1.x — não o 4 nem o 7.
4. Configure `ANDROID_HOME` apontando para o SDK.
5. Rode `gradle assembleDebug`.

Se você conseguir portar para Gradle moderno e quiser contribuir com o port, mande um PR. Contribuições são bem-vindas.

---

## Por que Java 7?

O Demeter é escrito e compilado num celular com AIDE, que usa JDK 7 e Gradle 1.x. Não é uma decisão de desempenho — é a ferramenta que o autor tem e usa todo dia. Se quiser portar para um toolchain moderno, mande um PR.

---

## O que NÃO é preciso

- Android Studio moderno
- Gradle 7+
- Java 8+
- Kotlin
- Kotlin DSL
- Dependências externas

O projeto não tem uma única dependência de terceiros. Tudo o que usa vem do SDK padrão do Android.

---

Copyright © 2026 LexusYTG — leonpackpro@gmail.com
