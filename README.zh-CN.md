# Sink for Android

![Android CI](https://github.com/wmy2981/sink-android/actions/workflows/android.yml/badge.svg)

[Sink](https://github.com/ccbikai/Sink) 短链服务的第三方原生 Android 客户端。使用 Kotlin + Jetpack Compose 构建，界面遵循 Material Design 3，通过 Sink 的 REST API 连接任意自建实例。

[English](README.md) | **简体中文**

## 功能

- **连接实例**：填写实例地址与 `NUXT_SITE_TOKEN`，连接前调用 `/api/verify` 校验。
- **短链列表**：网页版风格的站点图标、游标分页、下拉刷新、无限加载，支持按短码/链接/备注搜索，按时间或短码排序，按状态（有效/已过期/全部）和标签筛选。
- **新建 / 编辑 / 删除短链**：支持目标地址、自定义短码、备注、过期时间、最多 10 个标签、访问密码、附带查询参数、目标地址伪装、反向代理、安全提示、社交预览（标题、描述、图片）、应用商店跳转与地理分流。
- **编辑辅助**：随机短码（跟随实例配置的位数）或 AI 建议短码、AI 生成社交标题与描述、UTM 参数生成器、OpenGraph 图片上传。
- **快捷操作**：复制短链、在浏览器打开、系统分享、生成二维码。
- **外观**：跟随系统/浅色/深色主题，Android 12+ 可选用系统动态取色。
- **多语言界面**：支持英文与简体中文，跟随系统语言切换。
- **隐私优先**：凭据只保存在本机 DataStore 的私有存储中，不会上传到第三方。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3，BOM 2024.12）
- MVVM：`ViewModel` + `StateFlow` + 手写 `AppContainer`
- Retrofit + OkHttp + kotlinx.serialization
- DataStore Preferences（实例地址、令牌、主题）
- ZXing core（二维码生成）

## 构建

环境要求：JDK 17、Android SDK Platform 35、Build-Tools 35.0.0。

```bash
# 可选：创建签名配置（未提供时 release 使用 debug 签名）
cat > keystore.properties <<PROPS
storeFile=keystore/sink-release.jks
storePassword=******
keyAlias=sink
keyPassword=******
PROPS

./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

调试构建：`./gradlew assembleDebug`。

单元测试：`./gradlew testDebugUnitTest`（覆盖 API 客户端、URL 规范化、标签、链接校验、UTM 参数与请求体映射）。

## 多语言

应用内置英文（默认）与简体中文，跟随系统语言显示。Android 13 及以上还可以在「设置 → 应用 → Sink → 应用语言」中单独为 Sink 选择语言。界面文案位于 `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh-rCN/strings.xml`；新增语言时创建 `app/src/main/res/values-<qualifier>/strings.xml`，并在 `app/src/main/res/xml/locales_config.xml` 中登记该语言。

## CI 与 APK 产物

GitHub Actions 工作流 `.github/workflows/android.yml` 在推送到 `main`、PR 与手动触发时执行：

1. 运行单元测试；
2. 构建 debug 与 release APK；
3. 将两个 APK 上传为构建产物 `sink-android-<run_number>`，在 Actions 运行页面底部 Artifacts 下载。

Release 签名通过仓库 Secrets 注入：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。未配置 Secret 时（例如来自 fork 的 PR）自动回退为 debug 签名。

## 发行

`.github/workflows/release.yml` 在推送 `v*` 标签时自动发行。标签必须与 `app/build.gradle.kts` 中的 `versionName` 一致（例如标签 `v1.2.0` 对应 `versionName = "1.2.0"`），否则工作流失败。它会运行单元测试、用发行签名 Secrets 构建 release APK，并创建标题为 `Sink for Android <版本号>` 的 GitHub Release，自动生成说明，并将 APK 以 `sink-android-<版本号>.apk` 附在发行页。该工作流强制要求签名 Secrets，因此不会发出 debug 签名的 APK。

## 使用的 Sink API

| 接口 | 用途 |
| --- | --- |
| `GET /api/verify` | 校验令牌并读取当前身份 |
| `GET /api/link/list` | 分页列出短链（sort/status/tag/cursor） |
| `GET /api/link/search` | 关键词搜索 |
| `GET /api/link/tags` | 标签及计数 |
| `GET /api/link/query` | 读取单条短链（编辑前加载） |
| `POST /api/link/create` | 新建短链 |
| `PUT /api/link/edit` | 编辑短链（保留网页端配置的高级字段） |
| `POST /api/link/delete` | 删除短链 |
| `GET /api/link/ai` | 用 Workers AI 建议短码 |
| `GET /api/link/og-ai` | 用 Workers AI 建议社交标题与描述 |
| `POST /api/upload/image` | 上传社交预览图片（需要 R2） |

认证方式为请求头 `Authorization: Bearer <NUXT_SITE_TOKEN>`。

## 已知限制

- 仅支持站点令牌认证，暂不支持 Cloudflare Access 的交互式浏览器登录。
- 图片上传需要实例配置 R2，AI 建议需要绑定 Workers AI，反向代理模式需要在实例上开启；实例不支持时应用会给出明确提示。
- 随机短码按钮跟随实例的 `NUXT_PUBLIC_SLUG_DEFAULT_LENGTH`；若无法读取 dashboard 页面（例如被 Cloudflare Access 拦截），会回退为 6 位。
- 短链图标由 `unavatar.webp.se` 加载，与网页端使用同一服务。
- 需要设备可直接访问实例地址；自建实例若只在内网，请确保手机处于同一网络。

## 目录结构

```
app/src/main/java/cool/sink/mobile
├── data/          # 模型、Retrofit API、DataStore、Repository
├── ui/            # Compose 主题、组件、页面、ViewModel
├── util/          # 格式化、URL 校验、二维码
├── MainActivity.kt
└── SinkApplication.kt
```
