# 构建、安装与发布

## 环境

| 项目 | 当前配置 |
| --- | --- |
| 最低运行版本 | Android 8.0 / API 26 |
| compileSdk / targetSdk | 36 / 36 |
| Android Build Tools | 36.0.0 |
| JDK | 17 或 21；CI 使用 17 |
| Java / Kotlin 字节码目标 | 17 |
| Gradle Wrapper | 8.11.1 |
| Android Gradle Plugin | 8.10.1 |
| Kotlin / Compose compiler | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| applicationId | `com.workhub.app` |

依赖版本集中在 `gradle/libs.versions.toml`，少量模块依赖位于 `app/build.gradle.kts`。SDK 和 JDK 不随仓库分发。

## Android Studio

1. 克隆仓库并打开根目录，使用支持当前 Android Gradle Plugin 的 Android Studio。
2. 在 SDK Manager 安装 Platform 36、Build Tools 36.0.0 和 Platform Tools。
3. Gradle JDK 选择 17 或 21，等待同步。
4. 选择 API 26+ 设备或模拟器，运行 `app`。

界面默认深色，AI 密钥默认空。无需后台服务即可运行本地功能。

## 命令行

```bash
git clone https://github.com/zhengmoumou13/DEFY.git
cd DEFY
# JAVA_HOME 指向你安装的 JDK；ANDROID_HOME 指向 Android SDK。
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

也可以在项目根目录创建未跟踪的 `local.properties`：

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

Windows 使用 `gradlew.bat`；SDK 路径可用正斜杠。不要提交 `local.properties` 或自己电脑的路径。首次构建需访问 Google Maven、Maven Central、Gradle Plugin Portal 和 Gradle 分发服务；依赖缓存齐全后才可加 `--offline`。

输出：

| 产物 | 路径 |
| --- | --- |
| 调试 APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 单元测试报告 | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Lint 报告 | `app/build/reports/lint-results-debug.html` |
| 未签名 Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` |

Gradle Wrapper 分发校验值位于 `gradle-wrapper.properties`，来源为 [Gradle 官方校验文件](https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256)。

## 安装与更新

```bash
adb devices
adb -s YOUR_DEVICE_SERIAL install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_DEVICE_SERIAL shell am start -n com.workhub.app/.MainActivity
```

使用 `-s` 指定设备，避免装到错误的手机。手机可能需要解锁并确认安装。`-r` 是保留数据的覆盖安装；不要使用 `pm clear` 作为普通升级步骤。

**同包名只有签名一致才能覆盖更新。**

- 本地 Debug 构建使用该开发环境的调试签名。
- GitHub Actions 的临时构建使用运行环境的调试签名，不保证跨运行一致。
- Release 发布页应标明签名与校验值；不要假设它一定能覆盖任意本地构建。
- 出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 时先导出 `.dwb`，确认原签名或改用兼容安装来源。卸载会删除应用私有数据；备份不包含 API Key。

## 正式签名构建

仓库不包含签名私钥、密码或自动生成的正式发布身份。可以先构建未签名版本，再用自己长期保管的密钥签名：

```bash
./gradlew :app:assembleRelease
# 以下工具来自 SDK Build Tools；把路径替换为自己的安装位置。
zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/DEFY-aligned.apk
apksigner sign --ks /secure/path/defy-release.jks --out /tmp/DEFY-release.apk /tmp/DEFY-aligned.apk
apksigner verify --verbose --print-certs /tmp/DEFY-release.apk
```

让 `apksigner` 交互式读取密码，避免把密码写进 shell 历史。密钥丢失会影响后续升级能力；不要将 `.jks`、`.keystore`、密码文件或签名环境变量写进仓库。首次公开预览若提供 Debug APK，会明确标注为测试构建，不作为正式商店发行包。

## CI

[Android workflow](../.github/workflows/android.yml) 在 `main` 推送、Pull Request 和手动触发时运行：

1. 检出代码、准备 JDK 17 与 Android SDK。
2. 检查公开文件集合，构建并运行 JVM 测试与 Lint。
3. 上传 Debug APK 和测试 / Lint 报告为临时 Actions artifacts。

CI 不需要个人 AI Key，不调用真实 AI 服务，不进行设备端端到端测试，也不会自动发布正式版本。Actions 依赖固定到提交 SHA；后续更新应核验来源。

## 发布清单

1. 更新 `versionCode` / `versionName`、`CHANGELOG.md` 和变更说明。
2. 运行公开文件检查、密钥扫描、测试、构建与 Lint。
3. 在模拟器 / 真机检查新改动涉及的流程和明暗主题。
4. 检查第三方数据版本、许可证及媒体授权。
5. 生成明确类型的 APK、SHA-256 和签名证书指纹；不要发布签名私钥。
6. 创建对应源码标签，发布页关联准确的提交、安装说明与已知限制。

## 常见构建问题

- **SDK location not found**：配置 `ANDROID_HOME` 或 `local.properties`。
- **Gradle / Java 不兼容**：检查 `java -version` 与 Android Studio Gradle JDK，使用 17 或 21。
- **缺少 API 36 / Build Tools**：在 SDK Manager 安装对应组件并接受 SDK 条款。
- **依赖下载失败**：检查仓库网络；首次构建不要用 `--offline`，不要将私人 Maven 凭据写进项目。
- **KSP / Room 编译失败**：保持 Kotlin / KSP 配对版本；数据结构变更同步增加迁移。
- **Lint 有警告**：当前基线仍有警告；新增改动应修复相关问题，不要统一关闭检测。
