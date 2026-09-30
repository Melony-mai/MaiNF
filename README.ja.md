<p align="center">
  <img src="MaiNF.png" width="128" height="128" alt="MaiNF ロゴ" />
</p>

<h1 align="center">MaiNF</h1>

<p align="center">
  <strong>FTP・FTPS・SMB 対応 Android 双方向ファイル転送＆リモート管理アプリ</strong>
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="README.zh-CN.md">简体中文</a> •
  <a href="README.ja.md">日本語</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/プラットフォーム-Android-green.svg" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/最小%20SDK-26%20(Android%208.0)-blue.svg" alt="Min SDK: 26" />
  <img src="https://img.shields.io/badge/ターゲット%20SDK-35%20(Android%2015)-blue.svg" alt="Target SDK: 35" />
  <img src="https://img.shields.io/badge/言語-Kotlin%202.0-purple.svg" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-orange.svg" alt="UI: Jetpack Compose" />
  <img src="https://img.shields.io/badge/ライセンス-Apache%202.0-lightgrey.svg" alt="License: Apache 2.0" />
</p>

---

## 📖 概要

**MaiNF** は、最新の **Jetpack Compose** および **Material Design 3** ガイドラインに完全準拠して設計された、モダンで直感的な Android ネイティブファイル転送・リモートファイル管理アプリケーションです。

MaiNF の最大の特徴は、**双方向通信（クライアント＆サーバー両対応）** です。
1. **クライアントモード（外部へ接続）**：Android 端末から外部の FTP / FTPS / SMB (v2/v3) サーバーへ接続し、リモートディレクトリの閲覧、ファイルのアップロード・ダウンロード、管理が行えます。
2. **サーバーモード（この端末を共有）**：Android 端末自体をローカルファイルサーバー化！純粋な Kotlin で実装された組み込み FTP および SMB2 デーモンを起動し、同じ Wi-Fi/LAN 内の PC（Windows / Mac / Linux）やスマートテレビ、他のスマートフォンから直接端末内のファイルへアクセス・送受信が可能です。

---

## ✨ 主な機能

### 🔄 1. 双方向接続（クライアント＆サーバー完全サポート）

* **クライアントモード（外部サーバーへの接続）**：
  * **FTP & FTPS**：通常の FTP および明示的 TLS 暗号化（FTPS Explicit）に対応。パッシブ（PASV）/ アクティブ（PORT）モードの切り替え、タイムアウト設定、UTF-8 ファイル名、匿名または認証ログインをサポート。
  * **SMB2 / SMB3**：Windows 共有フォルダー、Samba サーバー、各種 NAS デバイス（Synology、QNAP、TrueNAS など）へ簡単接続。ゲスト接続、アカウント認証、ワークグループ/ドメイン指定、特定共有名の参照に対応。
  * **リモートファイル操作**：大容量ファイルのストリーミングアップロード・ダウンロード、ディレクトリ階層の移動、新規フォルダー作成、名前変更、削除。
* **サーバーモード（ホスト共有機能）**：
  * **組み込み FTP デーモン**：RFC 959 準拠の純 Kotlin FTP サーバーを内蔵。`USER`、`PASS`、`PASV`、`EPSV`、`RETR`、`STOR`、`DELE`、`RMD`、`MKD`、`LIST`、`MLSD` などの主要コマンドを完全サポート。
  * **組み込み SMB2 デーモン**：直接 TCP（ポート `4450`）を用いた純 Kotlin SMB2 サーバー。ダイアレクトネゴシエーション（SMB 2.1）、セッションセットアップ、ツリー接続、ディレクトリンクエリ、ファイル読み書きに対応。
  * **ネットワークステータス表示**：端末の Wi-Fi/LAN IPv4 アドレスを自動取得・表示し、接続用 URL（`ftp://<ip>:2121` や `\\<ip>:4450\<共有名>`）およびリアルタイム接続ログを画面上で確認可能。
  * **きめ細やかな設定**：ポート番号のカスタマイズ（標準：FTP `2121`、SMB `4450`）、匿名アクセスの許可/拒否、ユーザー認証、読み取り専用モード、共有フォルダー指定。

---

### 🚀 2. 転送エンジンとフォアグラウンドサービス

* **バックグラウンド転送の安定性**：専用の Android フォアグラウンドサービス（`TransferService`）と通知領域でのリアルタイム進捗表示により、OS の省電力機能やタスク終了による大容量転送の中断を防止。
* **リアルタイム転送メトリクス**：転送進捗率（%）、転送済みバイト数、現在の転送速度（`KB/s` または `MB/s`）をリアルタイムに計算・表示。
* **安全性とデータ整合性**：ダウンロードファイルはまず `.part` 一時ファイルとして書き込まれ、転送完了後にアトミックにリネームされるため、破損した不完全ファイルがストレージに残る心配がありません。
* **キュー管理**：実行中の転送のキャンセル、失敗したファイルの一括再試行、完了タスクのクリアに対応。

---

### 🌐 3. 3言語完全ローカライゼーション

すべての画面、ダイアログ、ボタン、設定項目、メッセージ、エラー表示において完全な多言語対応を実現：
* 🇺🇸 **英語 (English)** (`values/strings.xml`)
* 🇨🇳 **簡体字中国語 (简体中文)** (`values-zh/strings.xml`)
* 🇯🇵 **日本語** (`values-ja/strings.xml`)
* アプリ内の右上に設置された**言語切り替えダイアログ**からいつでも言語を変更可能。また、Android 13+ のアプリ別言語設定にもネイティブ対応。

---

### 🛡️ 4. セキュリティとサンドボックス保護

* **パストラバーサル（ディレクトリ横断）対策**：FTP および SMB サーバー実装において厳格な正規パス（Canonical Path）境界チェック（`targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)`）を実施。`../` などの相対指定や前方一致の悪用による共有ディレクトリ外へのアクセスを完全遮断。
* **読み取り専用モード**：外部接続クライアントによる端末内ファイルの変更・削除を制限する保護スイッチを搭載。
* **Root 不要の安全ポート設計**：標準で一般権限ポート（FTP `2121`、SMB `4450`）を採用しており、端末の Root 化を一切必要としません。

---

### 🎨 5. 洗練されたネイティブ UI（Material Design 3）

**Jetpack Compose** によるフル実装：
* **5 つのボトムナビゲーションタブ**：
  1. 🖥️ **サーバー (Servers)**：登録済みサーバー（FTP/FTPS/SMB）の管理とクイック接続。
  2. 📁 **リモートブラウザー (Remote)**：パンくずリストナビゲーション、ディレクトリ内検索・フィルター、ファイル情報表示、各種操作メニュー。
  3. ⚡ **転送状況 (Transfers)**：アクティブ転送キュー、転送速度メーター、キャンセル・再試行操作。
  4. 💾 **ローカル保存先 (Local)**：`Downloads/MaiNF` 内のダウンロード済みファイルを管理。`FileProvider` を経由して外部アプリで開く・共有・削除。
  5. 📡 **ホスト共有 (Host Share)**：端末内 FTP/SMB サーバーの開始・停止、接続情報の確認、接続ログの確認。
* **レスポンシブなデザイン**：Edge-to-Edge（全画面表示）対応、Material 3 ダイナミックカラーによる美しいテーマ。

---

## 🏗️ アーキテクチャと技術スタック

```
MaiNF
├── app/src/main/java/com/mai/nf/
│   ├── client/                  # クライアント接続エンジン (FTP & SMB2/3)
│   │   ├── ClientFactory.kt     # クライアント生成ファクトリ
│   │   ├── FtpClientImpl.kt     # Apache Commons Net による FTP/FTPS 実装
│   │   ├── RemoteFileClient.kt  # 共通ファイルクライアントインターフェース
│   │   └── SmbClientImpl.kt     # SMBJ / BouncyCastle による SMB 実装
│   ├── data/                    # リポジトリ層 (サーバー設定、転送キュー管理)
│   ├── model/                   # ドメインモデル (プロトコル、サーバー設定、転送項目など)
│   ├── server/                  # 組み込みサーバーエンジン (純 Kotlin 実装)
│   │   ├── ftp/FtpServer.kt     # RFC 959 準拠 FTP デーモン
│   │   ├── smb/SmbServer.kt     # SMB2 Direct-TCP サーバーデーモン
│   │   └── LocalServerManager.kt# サーバーマネージャー兼ローカル IP 検出
│   ├── service/                 # Android フォアグラウンドサービス
│   │   ├── HostServerService.kt # サーバー待受維持用フォアグラウンドサービス
│   │   ├── TransferManager.kt   # 転送処理スコープ＆速度計算ロジック
│   │   └── TransferService.kt   # ファイル転送維持用フォアグラウンドサービス
│   └── ui/                      # Jetpack Compose UI 層
│       ├── components/          # 共通 UI コンポーネント (言語設定ダイアログなど)
│       ├── screens/             # 5 大主要画面コンポーネント
│       ├── theme/               # Material 3 カラー、タイポグラフィ、テーマ
│       └── viewmodel/           # MainViewModel (MVI / MVVM 状態管理)
└── app/src/test/java/com/mai/nf/# 単体テストスイート (セキュリティ、モデル、多言語検証)
```

### 使用ライブラリ・依存関係
* **UI フレームワーク**：AndroidX Compose（Material 3、Extended Icons、Activity Compose）
* **クライアントネットワーク**：
  * [Apache Commons Net 3.11.1](https://commons.apache.org/proper/commons-net/) — FTP / FTPS
  * [smbj 0.13.0](https://github.com/hierynomus/smbj) & [BouncyCastle 1.78.1](https://www.bouncycastle.org/) — SMB2 / SMB3
* **サーバーネットワーク**：純 Kotlin によるノンブロッキング・マルチスレッドソケットデーモン
* **非同期・並行処理**：Kotlin Coroutines & `StateFlow`
* **ストレージ連携**：Android Storage Access Framework (SAF) & `FileProvider`

---

## 🛠️ ビルドとインストール方法

### 必要環境
* **JDK 17**（OpenJDK 17 または Eclipse Temurin 17 推奨）
* **Android SDK**（`compileSdk = 35`、最小 `minSdk = 26`）
* **Gradle 8.7**（プロジェクト付属の `gradlew` により自動適用）

### ビルド手順

1. **リポジトリのクローン**：
   ```bash
   git clone https://github.com/your-username/MaiNF.git
   cd MaiNF
   ```

2. **単体テストの実行**：
   ```bash
   ./gradlew testDebugUnitTest
   ```

3. **デバッグ用 APK のビルド**：
   ```bash
   ./gradlew assembleDebug
   ```

4. **出力 APK ファイルの確認**：
   ビルド完了後、APK は以下に出力されます：
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. **ADB 経由での端末インストール**：
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🔒 セキュリティについて

セキュリティ脆弱性の報告方法や、パス境界サンドボックスの詳細については [SECURITY.md](SECURITY.md) をご参照ください。

---

## 🤝 コントリビューション

バグ報告、機能改善のご提案、Pull Request を歓迎します。詳細については [CONTRIBUTING.md](CONTRIBUTING.md) をご確認ください。

---

## 📄 ライセンス

本プロジェクトは Apache License 2.0 の下で公開されています。詳細は [LICENSE](LICENSE) をご覧ください。
