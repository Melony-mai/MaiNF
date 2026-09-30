# Contributing to MaiNF

[English](CONTRIBUTING.md) • [简体中文](CONTRIBUTING.zh-CN.md) • [日本語](CONTRIBUTING.ja.md)

Thank you for your interest in contributing to **MaiNF**! We welcome bug reports, feature suggestions, code contributions, and translation improvements.

---

## 🛠️ Development Setup

1. **Prerequisites**:
   * JDK 17
   * Android SDK (API 35 compile target, API 26 minimum)
   * Android Studio Ladybug (or newer) or command-line Gradle

2. **Clone & Build**:
   ```bash
   git clone https://github.com/your-username/MaiNF.git
   cd MaiNF
   ./gradlew assembleDebug
   ```

3. **Run Unit Tests**:
   Ensure all tests pass before submitting changes:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 📋 Coding Guidelines

* **Language & Style**: Follow official Kotlin coding conventions and modern Android Jetpack Compose best practices.
* **Architecture**: Keep business logic clean, decoupled, and testable. Utilize `StateFlow` and Kotlin Coroutines for asynchronous work.
* **Localization**: When adding or updating user-facing strings, make sure to add the corresponding keys to all three resource files:
  * `app/src/main/res/values/strings.xml` (English)
  * `app/src/main/res/values-zh/strings.xml` (Simplified Chinese)
  * `app/src/main/res/values-ja/strings.xml` (Japanese)
  Verify key parity by running `LocalizationCompletenessTest`.
* **Security**: Maintain directory traversal checks in server daemons (`FtpServer` and `SmbServer`). Never trust raw input paths from remote clients.

---

## 🚀 Submitting a Pull Request

1. Fork the repository and create your branch from `main`:
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. Commit your changes with clear, descriptive commit messages.
3. Test your changes thoroughly on a device or emulator.
4. Open a Pull Request against the `main` branch with a description of your changes and why they are needed.
