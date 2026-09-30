# アーキテクチャと技術設計

[English](ARCHITECTURE.md) • [简体中文](ARCHITECTURE.zh-CN.md) • [日本語](ARCHITECTURE.ja.md)

本書では、**MaiNF** のシステム全体におけるアーキテクチャ設計、デザインパターン、各プロトコルの実装および内部コンポーネントについて詳述します。

---

## 🏛️ 全体アーキテクチャパターン

MaiNF は **Jetpack Compose** に最適化されたリアクティブな **MVVM / MVI** パターンを採用しています：

```
┌───────────────────────────────────────────────────────────┐
│                      Jetpack Compose UI 層                 │
│   (ServersScreen, BrowserScreen, TransfersScreen など)    │
└─────────────────────────────▲─────────────────────────────┘
                              │ StateFlow の購読 / ユーザー操作イベントの送信
┌─────────────────────────────▼─────────────────────────────┐
│                       MainViewModel                       │
│      - 状態管理: 接続状態、カレントパス、リモートファイル一覧   │
│      - データ連携: ローカルファイル、転送キュー、サーバーログ │
└─────────────────┬───────────────────────┬─────────────────┘
                  │                       │
      ┌───────────▼──────────┐ ┌──────────▼────────────┐
      │ クライアント接続エンジン │ │  端末内ホストサーバーエンジン│
      │  - ClientFactory       │ │ - LocalServerManager   │
      │  - FtpClientImpl       │ │ - FtpServer (RFC 959)  │
      │  - SmbClientImpl       │ │ - SmbServer (SMB2)     │
      └───────────┬──────────┘ └──────────┬────────────┘
                  │                       │
      ┌───────────▼───────────────────────▼────────────┐
      │             Android フォアグラウンドサービス           │
      │   - TransferService (バックグラウンド転送を維持)      │
      │   - HostServerService (端末内待受サーバーを維持)     │
      └────────────────────────────────────────────────┘
```

---

## 🔌 1. クライアントアーキテクチャ (`com.mai.nf.client`)

異なるネットワーク通信プロトコル間でファイル操作インターフェースを統一し、UI 層との結合度を最小限に抑えています：

1. **`RemoteFileClient` インターフェース**：
   共通のリモートファイル操作コントラクトを定義：
   * `connect(): Result<Unit>`：接続と認証
   * `listFiles(path: String): Result<List<RemoteFile>>`：特定階層のファイル一覧取得
   * `downloadFile(...)`：進捗コールバックおよび中断処理に対応したストリーミング受信
   * `uploadFile(...)`：進捗コールバック付きストリーミング送信
   * `createDirectory(path: String)`：新規ディレクトリ作成
   * `deleteFile(path: String)` / `deleteDirectory(path: String)`：ファイル・フォルダーの削除
   * `rename(oldPath: String, newPath: String)`：名前の変更
   * `close()`：ソケットおよびセッションの解放

2. **`ClientFactory`**：
   サーバープロファイル（`ServerProfile`）に指定された `Protocol` に基づき、`FtpClientImpl`（FTP / FTPS）または `SmbClientImpl`（SMB）を動的に生成。

3. **`FtpClientImpl`**：
   Apache Commons Net をラップ。パッシブモード（`enterLocalPassiveMode()`）、バイナリ転送モードの設定、明示的 TLS（FTPS）ハンドシェイク、およびディレクトリ一覧のパースを担当。

4. **`SmbClientImpl`**：
   `smbj` および BouncyCastle を使用。SMB2/SMB3 のセッション確立、認証コンテキスト（ゲストまたはクレデンシャル）、対象共有フォルダー（`DiskShare`）へのツリー接続、キャンセル可能なストリーミング送受信を実現。

---

## 📡 2. ホストサーバーアーキテクチャ (`com.mai.nf.server`)

C/C++ ネイティブライブラリや外部ツールに依存せず、純粋な Kotlin のみで Android 上に直接ソケットデーモンを内蔵しています：

1. **`FtpServer` (RFC 959 準拠 Kotlin FTP デーモン)**：
   * 指定ポート（デフォルト `2121`）で待受。
   * Kotlin Coroutines（`Dispatchers.IO`）を活用し、複数クライアントの同時接続を非同期処理。
   * 動的ポート割り当てによるパッシブ転送（`PASV` および `EPSV`）に対応。
   * ストリーミング送受信（`RETR`、`STOR`）、階層移動（`CWD`、`CDUP`、`PWD`）、フォルダー作成・削除（`MKD`、`RMD`）、ファイル操作（`DELE`、`RNFR`/`RNTO`）、一覧表示（`LIST`、`MLSD`）を完全サポート。

2. **`SmbServer` (SMB2 Direct-TCP デーモン)**：
   * `4450` 番ポートで NetBIOS Direct-TCP フレーム（`0x00` 長さヘッダー）を受信。
   * SMB2 プロトコルヘッダーの主要コマンドを実装：
     * `0x0000` (`SMB2_NEGOTIATE`)：SMB 2.1 ダイアレクトをネゴシエート。
     * `0x0001` (`SMB2_SESSION_SETUP`)：ゲストまたは認証セッションを確立。
     * `0x0003` (`SMB2_TREE_CONNECT`)：指定されたディスク共有に接続。
     * `0x0005` (`SMB2_CREATE`)：ファイルやディレクトリを開き、揮発性ハンドル ID を発行。
     * `0x000E` (`SMB2_QUERY_DIRECTORY`)：リトルエンディアン形式で `FILE_BOTH_DIR_INFORMATION` レコードをシリアライズ。
     * `0x0008` (`SMB2_READ`) および `0x0009` (`SMB2_WRITE`)：`RandomAccessFile` を用いたブロック送受信。
     * `0x0006` (`SMB2_CLOSE`)：ファイルハンドルをクローズ。

3. **`LocalServerManager`**：
   * 端末の `NetworkInterface` を探索し、Wi-Fi または LAN のアクティブな IPv4 アドレスを自動検出。
   * サーバーライフサイクルの管理、接続クライアント数の集計、UI へのリアルタイムログ送信を担当。

---

## ⚡ 3. バックグラウンドサービスと転送管理 (`com.mai.nf.service`)

1. **`TransferManager`**：
   * 複数転送タスクのキューイング、`.part` 一時ファイルの管理、キャンセル制御、リアルタイム転送速度（`MB/s`）の算出。
   * 400ms 間隔で転送速度をサンプリング・スムージングし、UI 表示の激しいチラつきを防止。
2. **`TransferService`**：
   * `AndroidManifest.xml` に `android:foregroundServiceType="dataSync"` として宣言。
   * 通知領域に進行状況プログレスバーを常駐表示し、大容量ファイル転送時の OS による強制停止を防止。
3. **`HostServerService`**：
   * アプリがバックグラウンドに移行した場合や画面ロック時でも、内蔵サーバーの待受ソケットを維持する常駐フォアグラウンドサービス。

---

## 🛡️ 4. セキュリティとサンドボックス保護

* **正規パス境界検証アルゴリズム**：
  ```kotlin
  val rootPath = rootDir.canonicalPath
  val targetPath = targetFile.canonicalPath
  val isSafe = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
  if (!isSafe) { /* アクセス拒否：550 Permission Denied または STATUS_ACCESS_DENIED */ }
  ```
  このチェックにより、`../` 等によるディレクトリ横断攻撃や、同名プレフィックスを持つ隣接ディレクトリへのすり抜けを防止します。
* **データ分離と共有保護**：
  * ダウンロード済みファイルは標準の外部保存領域（`Downloads/MaiNF`）に配置。
  * ファイルの外部共有には AndroidX `FileProvider` を使用し、安全な Content URI 経由でのみ受け渡しを行います。
