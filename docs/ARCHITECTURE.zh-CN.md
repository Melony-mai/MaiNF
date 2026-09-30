# 系统架构与技术设计

[English](ARCHITECTURE.md) • [简体中文](ARCHITECTURE.zh-CN.md) • [日本語](ARCHITECTURE.ja.md)

本文档详细介绍了 **MaiNF** 的整体系统架构、设计模式、通信协议实现及内部核心模块。

---

## 🏛️ 整体架构模式

MaiNF 采用专为 **Jetpack Compose** 设计的响应式 **MVVM / MVI** 架构：

```
┌───────────────────────────────────────────────────────────┐
│                      Jetpack Compose 界面层                │
│   (ServersScreen, BrowserScreen, TransfersScreen 等)      │
└─────────────────────────────▲─────────────────────────────┘
                              │ 观察 StateFlow / 发送用户交互事件
┌─────────────────────────────▼─────────────────────────────┐
│                       MainViewModel                       │
│      - 状态管理：连接状态、当前路径、远程文件列表           │
│      - 数据调度：本地文件、传输队列、主机配置、服务日志     │
└─────────────────┬───────────────────────┬─────────────────┘
                  │                       │
      ┌───────────▼──────────┐ ┌──────────▼────────────┐
      │  出站客户端连接引擎   │ │   入站主机服务端引擎   │
      │  - ClientFactory       │ │ - LocalServerManager   │
      │  - FtpClientImpl       │ │ - FtpServer (RFC 959)  │
      │  - SmbClientImpl       │ │ - SmbServer (SMB2)     │
      └───────────┬──────────┘ └──────────┬────────────┘
                  │                       │
      ┌───────────▼───────────────────────▼────────────┐
      │              Android 系统前台服务              │
      │   - TransferService (维持后台传输长连接)       │
      │   - HostServerService (维持本地服务后台待机)   │
      └────────────────────────────────────────────────┘
```

---

## 🔌 1. 出站客户端架构 (`com.mai.nf.client`)

为了统一跨网络协议的文件操作接口，降低上层 UI 与特定协议库的耦合：

1. **`RemoteFileClient` 核心接口**：
   定义标准远程文件操作契约：
   * `connect(): Result<Unit>`：建立连接并完成身份认证
   * `listFiles(path: String): Result<List<RemoteFile>>`：拉取指定目录文件列表
   * `downloadFile(...)`：流式下载，支持实时进度与中断取消回调
   * `uploadFile(...)`：流式上传，支持进度回调
   * `createDirectory(path: String)`：新建远程目录
   * `deleteFile(path: String)` / `deleteDirectory(path: String)`：删除文件或目录
   * `rename(oldPath: String, newPath: String)`：重命名
   * `close()`：释放网络套接字与会话资源

2. **`ClientFactory` 统一工厂**：
   依据用户选定的服务器连接配置（`ServerProfile`）中的 `Protocol`，动态实例化 `FtpClientImpl`（针对 FTP 与 FTPS）或 `SmbClientImpl`（针对 SMB）。

3. **`FtpClientImpl`**：
   封装 Apache Commons Net 组件库。负责处理主动/被动传输模式（`enterLocalPassiveMode()`）、二进制流传输配置、显式 TLS 握手及目录结果解析映射。

4. **`SmbClientImpl`**：
   基于 `smbj` 与 BouncyCastle 实现。处理 SMB2/SMB3 会话协商、鉴权上下文（访客模式或用户名密码）、挂载目标磁盘共享（`DiskShare`），并实现具备取消检查的流式读写。

---

## 📡 2. 入站主机服务端架构 (`com.mai.nf.server`)

MaiNF 在 Android 运行时内部以纯 Kotlin 原生实现服务守护进程，无需引入额外的 C/C++ 动态链接库或外部二进制执行程序：

1. **`FtpServer` (RFC 959 纯 Kotlin 守护进程)**：
   * 监听指定端口（默认 `2121`）。
   * 基于 Kotlin 协程与 `Dispatchers.IO` 处理多客户端并发连入。
   * 支持通过动态端口分配实现被动数据传输（`PASV` 与 `EPSV`）。
   * 完整支持流式读写（`RETR`、`STOR`）、目录切换与查询（`CWD`、`CDUP`、`PWD`）、目录创建与删除（`MKD`、`RMD`）、文件操作（`DELE`、`RNFR`/`RNTO`）以及目录列表获取（`LIST`、`MLSD`）。

2. **`SmbServer` (SMB2 Direct-TCP 守护进程)**：
   * 监听 `4450` 端口，采用标准 NetBIOS Direct-TCP 报文帧头（`0x00` 长度前缀）。
   * 原生实现核心 SMB2 指令分发逻辑：
     * `0x0000` (`SMB2_NEGOTIATE`)：完成 SMB 2.1 协议版本协商。
     * `0x0001` (`SMB2_SESSION_SETUP`)：建立访客或免密安全会话。
     * `0x0003` (`SMB2_TREE_CONNECT`)：挂载指定名称的磁盘共享。
     * `0x0005` (`SMB2_CREATE`)：定位并打开文件/目录句柄，分配轻量级 ID。
     * `0x000E` (`SMB2_QUERY_DIRECTORY`)：按小端序（Little-Endian）序列化生成 `FILE_BOTH_DIR_INFORMATION` 目录结构。
     * `0x0008` (`SMB2_READ`) 与 `0x0009` (`SMB2_WRITE`)：通过 `RandomAccessFile` 实现分片流式读写。
     * `0x0006` (`SMB2_CLOSE`)：回收文件句柄资源。

3. **`LocalServerManager`**：
   * 遍历本地网络接口（`NetworkInterface`），实时解析设备在局域网 Wi-Fi 中的 IPv4 地址。
   * 统一管控各服务生命周期，统计活跃连接数，并通过 `StateFlow` 向 UI 提供实时连接日志流。

---

## ⚡ 3. 后台服务与传输调度 (`com.mai.nf.service`)

1. **`TransferManager`**：
   * 管理多任务传输队列、`.part` 临时缓存文件生命周期、任务取消令牌以及实时速率计算（`MB/s`）。
   * 采用 400ms 速率阻尼平滑算法，避免界面传输速度数值高频抖动。
2. **`TransferService`**：
   * 在 `AndroidManifest.xml` 中声明 `android:foregroundServiceType="dataSync"`。
   * 维持前台常驻通知并刷新动态进度条，避免大文件传输被系统后台回收机制杀死。
3. **`HostServerService`**：
   * 作为常驻前台服务，在应用进入后台或锁屏状态下维持本地服务端套接字常开。

---

## 🛡️ 4. 安全防护与沙盒设计

* **规范路径边界校验算法**：
  ```kotlin
  val rootPath = rootDir.canonicalPath
  val targetPath = targetFile.canonicalPath
  val isSafe = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
  if (!isSafe) { /* 拒绝访问：返回 550 Permission Denied 或 STATUS_ACCESS_DENIED */ }
  ```
  该逻辑从根本上杜绝了利用 `../` 相对路径逃逸或同名前缀同级目录绕过共享根目录的安全隐患。
* **本地数据隔离**：
  * 所有下载文件均存放于系统外部存储公共目录（`Downloads/MaiNF`）。
  * 对外分享文件统一通过 AndroidX `FileProvider` 授予单次临时 Content URI，防止本地物理路径泄露。
