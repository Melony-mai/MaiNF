<p align="center">
  <img src="MaiNF.png" width="128" height="128" alt="MaiNF 图标" />
</p>

<h1 align="center">MaiNF</h1>

<p align="center">
  <strong>基于 FTP、FTPS 和 SMB 的 Android 双向文件传输与远程管理工具</strong>
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="README.zh-CN.md">简体中文</a> •
  <a href="README.ja.md">日本語</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/平台-Android-green.svg" alt="平台: Android" />
  <img src="https://img.shields.io/badge/最低%20SDK-26%20(Android%208.0)-blue.svg" alt="最低 SDK: 26" />
  <img src="https://img.shields.io/badge/目标%20SDK-35%20(Android%2015)-blue.svg" alt="目标 SDK: 35" />
  <img src="https://img.shields.io/badge/开发语言-Kotlin%202.0-purple.svg" alt="语言: Kotlin" />
  <img src="https://img.shields.io/badge/界面框架-Jetpack%20Compose%20%7C%20Material%203-orange.svg" alt="界面: Jetpack Compose" />
  <img src="https://img.shields.io/badge/开源协议-Apache%202.0-lightgrey.svg" alt="开源协议: Apache 2.0" />
</p>

---

## 📖 项目简介

**MaiNF** 是一款专为 Android 打造的现代化、原生文件传输与远程存储管理应用。全应用采用 **Jetpack Compose** 与 **Material Design 3** 规范构建，旨在为局域网（LAN）设备互联、跨平台传输与远程服务器管理提供轻量、高效且直观的体验。

MaiNF 的核心特色在于完善的**双向连通性支持**：
1. **客户端模式（主动连接）**：从 Android 设备连接外部 FTP、FTPS 以及 SMB (v2/v3) 服务器，浏览远程目录，上传、下载并管理远程文件。
2. **服务端模式（作为主机共享）**：将当前 Android 设备直接转变为文件服务器！内置纯 Kotlin 实现的轻量级 FTP 与 SMB2 服务守护进程，局域网内的电脑（Windows / macOS / Linux）、智能电视或其他移动设备无需安装额外客户端，即可直接通过网络连接并互传文件。

---

## ✨ 核心特性

### 🔄 1. 双向连通（客户端与服务端全支持）

* **客户端模式（发送/请求端）**：
  * **FTP & FTPS**：支持标准 FTP 与显式 TLS 加密（FTPS Explicit）。提供被动模式（PASV）与主动模式（PORT）自由切换、连接超时设定、UTF-8 字符集支持，以及匿名或账号密码登录。
  * **SMB2 / SMB3**：无缝连接 Windows 共享文件夹、Samba 服务器以及各品牌 NAS 设备（群晖 Synology、威联通 QNAP、TrueNAS 等）。支持访客免密登录、指定域/工作组登录以及指定共享名访问。
  * **远程文件操作**：支持大文件流式上传与下载、多级目录深入浏览、新建远程文件夹、重命名、单文件或批量删除。
* **服务端模式（主机共享端）**：
  * **内置 FTP 守护进程**：纯 Kotlin 实现的标准 RFC 959 FTP 服务，完整实现 `USER`、`PASS`、`PASV`、`EPSV`、`RETR`、`STOR`、`DELE`、`RMD`、`MKD`、`LIST` 及 `MLSD` 等指令。
  * **内置 SMB2 守护进程**：纯 Kotlin 实现的直接 TCP（端口 `4450`）SMB2 服务，支持协议协商（SMB 2.1）、会话建立、树连接、目录元数据查询、以及文件读写。
  * **实时网络看板**：自动检测并显示本机 Wi-Fi/局域网 IPv4 地址、直观给出标准连接地址（如 `ftp://<ip>:2121` 与 `\\<ip>:4450\<共享名>`），并展示客户端实时连接与操作日志。
  * **灵活服务配置**：支持自定义服务端口（默认 FTP `2121`，SMB `4450`）、一键开启/关闭匿名访问、自定义管理账号、只读模式保护以及指定挂载共享目录。

---

### 🚀 2. 传输引擎与前台持久服务

* **后台长连接保障**：集成 Android 前台服务（`TransferService`）与常驻进度通知栏，彻底避免在多文件或大文件传输过程中因系统内存回收导致任务中断。
* **实时动态传输指标**：实时计算并反馈当前传输百分比、已传输字节数以及动态传输速率（`KB/s` 或 `MB/s`）。
* **传输安全与数据完整性**：所有下载文件均先写入 `.part` 临时暂存文件，传输校验完毕后再原子重命名，防止未完成或损坏的文件破坏本地存储。
* **队列管理**：支持随时取消进行中的任务、对异常中断的文件一键重试，以及一键清空历史完成记录。

---

### 🌐 3. 全量多语言本地化（三语支持）

全应用严格完成多语言本地化，覆盖全部界面、弹窗、设置项、协议配置、提示信息及错误反馈：
* 🇺🇸 **英语 (English)** (`values/strings.xml`)
* 🇨🇳 **简体中文** (`values-zh/strings.xml`)
* 🇯🇵 **日语 (日本語)** (`values-ja/strings.xml`)
* 界面右上角配备**应用内实时语言切换器**，同时原生兼容 Android 13+ 系统级单应用偏好语言设置。

---

### 🛡️ 4. 安全防护与沙盒机制

* **目录穿越防御**：FTP 与 SMB 服务端全面采用严格的规范路径（Canonical Path）边界验证机制（`targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)`），杜绝任何尝试通过 `../` 或相似前缀逃逸共享根目录的越权漏洞。
* **只读模式切换**：服务端支持一键开启“只读模式”，阻止外部连入设备随意修改或删除宿主设备的本地文件。
* **免 Root 权限运行**：服务端默认采用非特权高位端口（FTP `2121`，SMB `4450`），无需对 Android 系统进行 Root 破解即可安全运行。

---

### 🎨 5. 现代化原生界面（Material Design 3）

遵循 Google 最新设计语言，基于 **Jetpack Compose** 打造：
* **5 大底部导航页面**：
  1. 🖥️ **服务器列表 (Servers)**：集中管理保存的所有 FTP/FTPS/SMB 连接配置，展示连接状态指示器。
  2. 📁 **远程文件浏览 (Remote)**：面包屑路径导航、当前目录实时搜索过滤、文件大小与修改日期展示、长按/点击快捷操作菜单。
  3. ⚡ **传输队列 (Transfers)**：正在传输任务视图、动态速率图示、重试与取消快捷操作。
  4. 💾 **本地存储 (Local)**：直接查看保存在 `Downloads/MaiNF` 目录下的已下载文件，支持通过 `FileProvider` 调用系统外部程序打开或分享。
  5. 📡 **主机共享 (Host Share)**：一键启动/停止本地 FTP 与 SMB 服务，展示局域网 IP 与连接快捷方式，浏览实时连接日志。
* **全适配设计**：支持沉浸式全面屏（Edge-to-Edge）、动态 Material 3 配色方案与完善的键盘交互体验。

---

## 🏗️ 架构与技术栈

```
MaiNF
├── app/src/main/java/com/mai/nf/
│   ├── client/                  # 客户端连接引擎 (FTP 与 SMB2/3)
│   │   ├── ClientFactory.kt     # 客户端统一工厂
│   │   ├── FtpClientImpl.kt     # 基于 Apache Commons Net 的 FTP 实现
│   │   ├── RemoteFileClient.kt  # 统一文件客户端接口
│   │   └── SmbClientImpl.kt     # 基于 SMBJ / BouncyCastle 的 SMB 实现
│   ├── data/                    # 本地数据持久化 (服务器配置库、传输队列库)
│   ├── model/                   # 领域实体模型 (协议、服务器配置、传输实体、远程文件)
│   ├── server/                  # 服务端引擎 (纯 Kotlin 实现)
│   │   ├── ftp/FtpServer.kt     # RFC 959 FTP 服务守护进程
│   │   ├── smb/SmbServer.kt     # SMB2 Direct-TCP 服务守护进程
│   │   └── LocalServerManager.kt# 局域网服务协调器与 IP 检测器
│   ├── service/                 # Android 前台服务组件
│   │   ├── HostServerService.kt # 维持本地服务端运行的前台服务
│   │   ├── TransferManager.kt   # 传输调度器与速率计算引擎
│   │   └── TransferService.kt   # 维持后台文件传输的前台服务
│   └── ui/                      # Jetpack Compose 界面层
│       ├── components/          # 通用对话框组件 (语言选择、服务器编辑等)
│       ├── screens/             # 5 大功能导航页面
│       ├── theme/               # Material 3 主题、色彩与排版样式
│       └── viewmodel/           # MainViewModel (MVI / MVVM 状态流)
└── app/src/test/java/com/mai/nf/# 自动化测试套件 (路径安全测试、模型测试、本地化完整性校验)
```

### 核心依赖组件
* **UI 界面**：AndroidX Compose（Material 3、Extended Icons、Activity Compose）
* **客户端网络**：
  * [Apache Commons Net 3.11.1](https://commons.apache.org/proper/commons-net/) — FTP / FTPS 协议
  * [smbj 0.13.0](https://github.com/hierynomus/smbj) & [BouncyCastle 1.78.1](https://www.bouncycastle.org/) — SMB2 / SMB3 协议
* **服务端网络**：纯 Kotlin 实现的高并发、非阻塞套接字守护进程
* **异步与并发**：Kotlin 协程（Coroutines）与响应式状态流（`StateFlow`）
* **系统存储集成**：Android 存储访问框架（SAF）与 `FileProvider`

---

## 🛠️ 项目构建与安装指南

### 环境要求
* **JDK 17**（如 OpenJDK 17 或 Eclipse Temurin 17）
* **Android SDK**（`compileSdk = 35`，最低支持 `minSdk = 26`）
* **Gradle 8.7**（已通过工程自带 `gradlew` 配置）

### 本地编译步骤

1. **克隆仓库源码**：
   ```bash
   git clone https://github.com/your-username/MaiNF.git
   cd MaiNF
   ```

2. **运行单元测试套件**：
   ```bash
   ./gradlew testDebugUnitTest
   ```

3. **编译调试版 APK**：
   ```bash
   ./gradlew assembleDebug
   ```

4. **产物位置**：
   编译生成的 APK 文件将位于：
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. **通过 ADB 安装到设备**：
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🔒 安全性说明

如需了解关于目录沙盒防护机制的实现细节或上报安全漏洞，请参阅 [SECURITY.md](SECURITY.md)。

---

## 🤝 参与贡献

欢迎提交 Issue 或 Pull Request！在开始之前，请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解贡献流程。

---

## 📄 开源许可证

本项目基于 Apache License 2.0 协议开源，详情请参阅 [LICENSE](LICENSE) 文件。
