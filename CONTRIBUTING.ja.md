# MaiNF への貢献ガイド

[English](CONTRIBUTING.md) • [简体中文](CONTRIBUTING.zh-CN.md) • [日本語](CONTRIBUTING.ja.md)

**MaiNF** へのご関心、誠にありがとうございます！バグ報告、新機能のご提案、コードのコントリビューション、翻訳の改善など、あらゆるご参加を歓迎します。

---

## 🛠️ 開発環境のセットアップ

1. **必要な環境**:
   * JDK 17
   * Android SDK（コンパイル対象：API 35、最小：API 26）
   * Android Studio（Ladybug 以降推奨）またはコマンドライン Gradle

2. **クローンとビルド**:
   ```bash
   git clone https://github.com/your-username/MaiNF.git
   cd MaiNF
   ./gradlew assembleDebug
   ```

3. **単体テストの実行**:
   変更をコミットする前に、すべての単体テストが正常に通過することを確認してください：
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 📋 コーディング規約

* **言語とスタイル**: Kotlin 公式コーディングスタイル規約、および Jetpack Compose の最新ベストプラクティスを遵守してください。
* **アーキテクチャ**: ビジネスロジックを疎結合かつテスト可能に保ち、非同期処理には Kotlin Coroutines と `StateFlow` を用いてください。
* **多言語対応**: UI 表示用テキストを追加または変更する場合、以下の 3 つのファイルすべてに対応するキーを追加してください：
  * `app/src/main/res/values/strings.xml`（英語）
  * `app/src/main/res/values-zh/strings.xml`（簡体字中国語）
  * `app/src/main/res/values-ja/strings.xml`（日本語）
  `LocalizationCompletenessTest` を実行することでキーの過不足を自動検証できます。
* **セキュリティ**: サーバーデーモン（`FtpServer` および `SmbServer`）内のパストラバーサル境界チェックを維持してください。外部から送信されたパス文字列を検証なしで使用しないでください。

---

## 🚀 Pull Request の送信手順

1. 本リポジトリを Fork し、`main` からブランチを作成します：
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. 変更内容を分かりやすいコミットメッセージとともにコミットします。
3. 実機またはエミュレーター上で動作を確認します。
4. `main` ブランチに対して Pull Request を作成し、変更内容と目的を明記してください。
