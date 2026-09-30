# Architecture & Technical Design

[English](ARCHITECTURE.md) • [简体中文](ARCHITECTURE.zh-CN.md) • [日本語](ARCHITECTURE.ja.md)

This document provides a comprehensive overview of the technical architecture, design patterns, and internal components of **MaiNF**.

---

## 🏛️ High-Level Architectural Pattern

MaiNF adopts a modern, reactive **MVVM / MVI** architecture built natively for **Jetpack Compose**:

```
┌───────────────────────────────────────────────────────────┐
│                      Jetpack Compose UI                   │
│   (ServersScreen, BrowserScreen, TransfersScreen, etc.)   │
└─────────────────────────────▲─────────────────────────────┘
                              │ observes StateFlow / sends events
┌─────────────────────────────▼─────────────────────────────┐
│                       MainViewModel                       │
│      - connectionStatus, currentPath, remoteFiles         │
│      - localFiles, transfers, hostConfig, hostLogs        │
└─────────────────┬───────────────────────┬─────────────────┘
                  │                       │
      ┌───────────▼──────────┐ ┌──────────▼────────────┐
      │  Outgoing Client Engine │ │ Incoming Host Server   │
      │  - ClientFactory       │ │ - LocalServerManager   │
      │  - FtpClientImpl       │ │ - FtpServer (RFC 959)  │
      │  - SmbClientImpl       │ │ - SmbServer (SMB2)     │
      └───────────┬──────────┘ └──────────┬────────────┘
                  │                       │
      ┌───────────▼───────────────────────▼────────────┐
      │          Android Foreground Services           │
      │   - TransferService (keeps transfers active)   │
      │   - HostServerService (keeps daemons running)  │
      └────────────────────────────────────────────────┘
```

---

## 🔌 1. Outgoing Client Architecture (`com.mai.nf.client`)

To provide an extensible abstraction across distinct network protocols:

1. **`RemoteFileClient` Interface**:
   Declares standard file operations:
   * `connect(): Result<Unit>`
   * `listFiles(path: String): Result<List<RemoteFile>>`
   * `downloadFile(remotePath, destStream, fileSize, onProgress, isCancelled): Result<Unit>`
   * `uploadFile(sourceStream, remotePath, totalBytes, onProgress, isCancelled): Result<Unit>`
   * `createDirectory(path: String): Result<Unit>`
   * `deleteFile(path: String): Result<Unit>`
   * `deleteDirectory(path: String): Result<Unit>`
   * `rename(oldPath: String, newPath: String): Result<Unit>`
   * `close()`

2. **`ClientFactory`**:
   Instantiates either `FtpClientImpl` (for `Protocol.FTP` and `Protocol.FTPS`) or `SmbClientImpl` (for `Protocol.SMB`) based on the active `ServerProfile`.

3. **`FtpClientImpl`**:
   Wraps Apache Commons Net (`FTPClient` / `FTPSClient`). Configures socket timeouts, controls passive mode (`enterLocalPassiveMode()`), sets binary transfer mode, and translates FTP file listings into domain `RemoteFile` instances.

4. **`SmbClientImpl`**:
   Wraps the `smbj` SMB2/SMB3 library with BouncyCastle security provider. Resolves authentication contexts (guest or credentials), connects to target shares (`DiskShare`), navigates remote trees, and streams data with cancellable byte progress callbacks.

---

## 📡 2. Incoming Host Server Architecture (`com.mai.nf.server`)

MaiNF embeds pure-Kotlin server daemons directly inside the Android runtime without requiring C/C++ native binaries or external packages:

1. **`FtpServer` (RFC 959 Pure-Kotlin Daemon)**:
   * Listens on a configurable port (default `2121`).
   * Handles multi-client concurrent connections via Kotlin Coroutines on `Dispatchers.IO`.
   * Implements passive data transfers via dynamic local port allocation (`PASV` and `EPSV`).
   * Supports file streaming (`RETR`, `STOR`), directory navigation (`CWD`, `CDUP`, `PWD`), directory creation/deletion (`MKD`, `RMD`), file operations (`DELE`, `RNFR`/`RNTO`), and directory listings (`LIST`, `MLSD`).

2. **`SmbServer` (SMB2 Direct-TCP Daemon)**:
   * Listens on port `4450` using NetBIOS Direct-TCP framing (`0x00` length header).
   * Implements the core SMB2 header protocol:
     * `0x0000` (`SMB2_NEGOTIATE`): Negotiates dialect `0x0210` (SMB 2.1).
     * `0x0001` (`SMB2_SESSION_SETUP`): Accepts guest / anonymous or authenticated sessions.
     * `0x0003` (`SMB2_TREE_CONNECT`): Connects to the designated disk share.
     * `0x0005` (`SMB2_CREATE`): Opens files or directories and assigns volatile handle IDs.
     * `0x000E` (`SMB2_QUERY_DIRECTORY`): Returns Little-Endian serialized file information records (`FILE_BOTH_DIR_INFORMATION`).
     * `0x0008` (`SMB2_READ`) & `0x0009` (`SMB2_WRITE`): Streams file payload using `RandomAccessFile`.
     * `0x0006` (`SMB2_CLOSE`): Releases file handles.

3. **`LocalServerManager`**:
   * Inspects network interfaces (`NetworkInterface.getNetworkInterfaces()`) to discover the active Wi-Fi or LAN IPv4 address.
   * Manages server lifecycles, aggregates client count metrics, and streams server logs to the UI via `StateFlow`.

---

## ⚡ 3. Background Services & Transfer Engine (`com.mai.nf.service`)

1. **`TransferManager`**:
   * Manages transfer queues, `.part` download file buffers, cancellation tokens, and live throughput speed metrics (`MB/s`).
   * Calculates instantaneous speeds every 400ms delta to prevent UI jitter.
2. **`TransferService`**:
   * Declared in `AndroidManifest.xml` with `android:foregroundServiceType="dataSync"`.
   * Posts persistent notification with real-time transfer progress bar.
3. **`HostServerService`**:
   * Runs in the foreground with ongoing notification to maintain network sockets open when the app is minimized or the screen is turned off.

---

## 🛡️ 4. Security & Sandboxing Model

* **Path Canonicalization Check**:
  ```kotlin
  val rootPath = rootDir.canonicalPath
  val targetPath = targetFile.canonicalPath
  val isSafe = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
  if (!isSafe) { /* Reject with 550 Permission Denied or STATUS_ACCESS_DENIED */ }
  ```
  This guarantees that neither `..` traversal sequences nor sibling directories with identical prefix characters can escape the user-selected shared folder.
* **Storage Isolation**:
  * Downloaded files are placed in standard external storage (`Downloads/MaiNF`).
  * File sharing uses AndroidX `FileProvider` with content URIs, keeping internal file paths private.
