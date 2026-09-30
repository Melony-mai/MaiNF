# 参与 MaiNF 贡献

[English](CONTRIBUTING.md) • [简体中文](CONTRIBUTING.zh-CN.md) • [日本語](CONTRIBUTING.ja.md)

感谢你对 **MaiNF** 项目的关注与支持！我们非常欢迎各种形式的贡献，包括但不限于问题反馈、新功能建议、代码优化以及多语言翻译改进。

---

## 🛠️ 本地开发环境准备

1. **环境依赖**：
   * JDK 17
   * Android SDK（编译目标版本 API 35，最低支持 API 26）
   * Android Studio（Ladybug 或更新版本）或命令行 Gradle 环境

2. **源码克隆与构建**：
   ```bash
   git clone https://github.com/your-username/MaiNF.git
   cd MaiNF
   ./gradlew assembleDebug
   ```

3. **执行单元测试**：
   在提交代码前，请确保所有测试均通过：
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 📋 代码与开发规范

* **编码规范**：遵循 Kotlin 官方代码风格指南及 Android Jetpack Compose 现代架构最佳实践。
* **架构设计**：保持业务逻辑清晰、解耦且易于测试。并发与异步任务统一使用 Kotlin 协程（Coroutines）与 `StateFlow`。
* **多语言支持**：新增或修改任何界面文本时，必须同步更新以下三个语言资源文件：
  * `app/src/main/res/values/strings.xml`（英语）
  * `app/src/main/res/values-zh/strings.xml`（简体中文）
  * `app/src/main/res/values-ja/strings.xml`（日语）
  可通过运行 `LocalizationCompletenessTest` 自动校验三语键名一致性。
* **安全要求**：严禁弱化服务端（`FtpServer` 与 `SmbServer`）中的路径穿越校验，始终不得直接信任客户端传入的原始路径字符串。

---

## 🚀 提交 Pull Request

1. Fork 本仓库并基于 `main` 分支创建新的功能分支：
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. 提交代码并附带清晰、语义化的 Commit 说明。
3. 在真机或模拟器上完整验证功能。
4. 向 `main` 分支发起 Pull Request，并在描述中详述改动背景与实现方案。
