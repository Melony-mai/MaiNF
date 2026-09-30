<p align="center">
  <img src="MaiNF.png" width="128" height="128" alt="MaiNF Logo" />
</p>

<h1 align="center">MaiNF</h1>

<p align="center">
  <strong>Two-Way File Transfer & Management over FTP, FTPS, and SMB for Android</strong>
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="README.zh-CN.md">简体中文</a> •
  <a href="README.ja.md">日本語</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue.svg" alt="Min SDK: 26" />
  <img src="https://img.shields.io/badge/Target%20SDK-35%20(Android%2015)-blue.svg" alt="Target SDK: 35" />
  <img src="https://img.shields.io/badge/Language-Kotlin%202.0-purple.svg" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-orange.svg" alt="UI: Jetpack Compose" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg" alt="License: Apache 2.0" />
</p>

---

## 📖 Overview

**MaiNF** is a modern, native Android file transfer and remote file management application. Built entirely with **Jetpack Compose** and **Material Design 3**, it delivers a seamless, reliable experience for both personal local-area network (LAN) sharing and remote server management.

MaiNF is uniquely designed with **two-way connectivity**:
1. **Client Mode (Outgoing)**: Connect from your Android device to remote FTP, FTPS, and SMB (v2/v3) servers, browse files, upload, download, and manage remote storage.
2. **Server Mode (Incoming / Host Share)**: Turn your Android device into a local file server! Expose selected directories over embedded FTP and SMB2 daemons so computers, laptops, TVs, and other phones on the Wi-Fi network can transfer files directly to and from your device without installing extra apps.

---

## ✨ Features

### 🔄 1. Two-Way Connectivity (Client & Server)

* **Client Mode (Outgoing)**:
  * **FTP & FTPS**: Connect via plain FTP or TLS-encrypted FTPS (Explicit). Supports passive and active transfer modes, customizable timeouts, UTF-8 directory listings, and anonymous or authenticated logins.
  * **SMB2 / SMB3**: Connect to Windows file shares, Samba servers, and NAS devices (Synology, QNAP, TrueNAS, etc.). Supports guest authentication, user credentials, custom workgroups/domains, and specific share paths.
  * **Remote Operations**: Streaming file upload and download, directory navigation, folder creation, renaming, and batch deletion.
* **Server Mode (Incoming / Host Share)**:
  * **Embedded FTP Daemon**: Pure-Kotlin RFC 959 FTP server supporting `USER`, `PASS`, `PASV`, `EPSV`, `RETR`, `STOR`, `DELE`, `RMD`, `MKD`, `LIST`, and `MLSD`.
  * **Embedded SMB2 Daemon**: Pure-Kotlin SMB2 server handling direct-TCP framing (port `4450`), dialect negotiation (SMB 2.1), session setup, tree connect, directory querying, reading, and writing.
  * **Network Dashboard**: Displays real-time device Wi-Fi/LAN IPv4 address, clickable connection URLs (`ftp://<ip>:2121` and `\\<ip>:4450\<share>`), and live access logs.
  * **Granular Controls**: Configurable ports (default `2121` and `4450`), guest/anonymous toggles, custom credentials, read-only mode, and directory sandboxing.

---

### 🚀 2. Transfer Engine & Foreground Service

* **Background Resilience**: Dedicated Android Foreground Service (`TransferService`) with real-time persistent notifications, preventing Android OS task-killing during large file transfers.
* **Real-Time Transfer Metrics**: Displays live transfer progress percentage, total bytes transferred, and real-time transfer speed (`KB/s` or `MB/s`).
* **Integrity & Safety**: Downloads are written to `.part` temporary files and atomically renamed only upon completion, preventing incomplete or damaged files from corrupting storage.
* **Queue Management**: Transfer queue supports pausing, cancelling, retrying failed transfers, and batch-clearing completed tasks.

---

### 🌐 3. Trilingual Localization

Full native localization across all user-facing screens, dialogs, buttons, connection profiles, and error messages:
* 🇺🇸 **English** (`values/strings.xml`)
* 🇨🇳 **Simplified Chinese (简体中文)** (`values-zh/strings.xml`)
* 🇯🇵 **Japanese (日本語)** (`values-ja/strings.xml`)
* Includes an **in-app runtime language switcher** dialog as well as native support for Android 13+ per-app language settings.

---

### 🛡️ 4. Security & Sandboxing

* **Path Traversal Protection**: Both FTP and SMB server implementations enforce strict canonical path boundary checks (`targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)`), preventing path-traversal attacks (e.g., `../../` or sibling-prefix bypasses).
* **Read-Only Protection**: Optional toggle to prevent remote devices from modifying or deleting files on the host device.
* **Non-Root Port Safety**: Defaults to unprivileged ports (`2121` for FTP, `4450` for SMB) requiring no root access on standard Android devices.

---

### 🎨 5. Modern Native UI (Material Design 3)

Built from the ground up with **Jetpack Compose**:
* **5 Bottom Navigation Tabs**:
  1. 🖥️ **Servers**: Save and manage FTP/FTPS/SMB connection profiles with quick-connect badges.
  2. 📁 **Remote Browser**: Tree navigation, breadcrumbs, search/filtering, file size & date display, and action menus.
  3. ⚡ **Transfers**: Active transfer queue, transfer speed monitors, and retry/cancel actions.
  4. 💾 **Local Storage**: Browse downloaded files in `Downloads/MaiNF`, open files with system apps via `FileProvider`, or share them.
  5. 📡 **Host Share**: Start/stop FTP & SMB servers, view connection URLs, configure ports, and inspect live client activity logs.
* **Adaptive Theming**: Edge-to-edge layout, dynamic color theming, and responsive UI states.

---

## 🏗️ Architecture & Tech Stack

```
MaiNF
├── app/src/main/java/com/mai/nf/
│   ├── client/                  # Outgoing client engine (FTP & SMB2/3)
│   │   ├── ClientFactory.kt     # Unified client instantiation
│   │   ├── FtpClientImpl.kt     # Apache Commons Net client wrapper
│   │   ├── RemoteFileClient.kt  # Common client interface
│   │   └── SmbClientImpl.kt     # SMBJ / BouncyCastle wrapper
│   ├── data/                    # Repositories (Server profiles, Transfer queue)
│   ├── model/                   # Domain models (Protocol, ServerProfile, TransferItem, RemoteFile)
│   ├── server/                  # Incoming server daemons (pure-Kotlin)
│   │   ├── ftp/FtpServer.kt     # RFC 959 FTP daemon
│   │   ├── smb/SmbServer.kt     # SMB2 direct-TCP daemon
│   │   └── LocalServerManager.kt# Service coordinator & IP address detector
│   ├── service/                 # Android Foreground Services
│   │   ├── HostServerService.kt # Foreground service keeping host servers alive
│   │   ├── TransferManager.kt   # Transfer worker scope & progress calculation
│   │   └── TransferService.kt   # Foreground service for active file transfers
│   └── ui/                      # Jetpack Compose UI
│       ├── components/          # Reusable UI dialogs & controls
│       ├── screens/             # 5 core navigation tabs
│       ├── theme/               # Material 3 Color, Typography, and Theme
│       └── viewmodel/           # MainViewModel (MVI / MVVM state flow)
└── app/src/test/java/com/mai/nf/# Unit test suite (Security, Models, Localization)
```

### Libraries & Dependencies
* **UI**: AndroidX Compose (Material 3, Extended Icons, Activity Compose)
* **Networking (Client)**:
  * [Apache Commons Net 3.11.1](https://commons.apache.org/proper/commons-net/) — FTP / FTPS
  * [smbj 0.13.0](https://github.com/hierynomus/smbj) & [BouncyCastle 1.78.1](https://www.bouncycastle.org/) — SMB2 / SMB3
* **Networking (Server)**: Embedded pure-Kotlin non-blocking / multithreaded socket daemons
* **Concurrency**: Kotlin Coroutines & `StateFlow`
* **Storage**: Android Storage Access Framework (SAF) & AndroidX `FileProvider`

---

## 🛠️ Building & Installation

### Prerequisites
* **JDK 17** (e.g. OpenJDK 17, Eclipse Temurin 17)
* **Android SDK** with `compileSdk = 35`, `minSdk = 26`
* **Gradle 8.7** (managed via `gradlew`)

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/MaiNF.git
   cd MaiNF
   ```

2. **Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

3. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Locate APK**:
   The compiled APK will be available at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Install on Device via ADB**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🔒 Security

For security vulnerability reporting or technical details regarding our path sandboxing implementation, please refer to [SECURITY.md](SECURITY.md).

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Please check the [CONTRIBUTING.md](CONTRIBUTING.md) guidelines before submitting a pull request.

---

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
