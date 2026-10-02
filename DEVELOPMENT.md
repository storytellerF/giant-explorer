# 开发与发布

## 本地构建与验证

使用 JDK 21 和 Android SDK API 37，配置 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir` 后，在仓库根目录执行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :plugins:yue:yue-plugin:assembleDebug
./gradlew :app:lintDebug :app:detekt
```

调试 APK 位于 `app/build/outputs/apk/debug/`。Yue HTML 插件可用 `bash plugins/yue-html/dispatch.sh` 打包。

## 界面规范

- `app/src/main/res/values/design_styles.xml` 定义共享文字、按钮、表单、工具栏与弹窗样式。颜色使用 Material 语义角色，浅色和深色主题共用这些组件。
- 页面外边距使用 `screen_padding`，宽屏通过 `values-w600dp` 增大边距。交互控件至少保留 48dp 的触摸区域，长内容放入可滚动容器。
- 原生界面沿用 ViewBinding；后台任务中的 Compose 行通过 `GiantComposeTheme` 读取宿主颜色。界面样式不单独维护业务状态。
- 独立 Activity 使用 `applyScreenInsets()` 处理系统栏、刘海和键盘；主文件页分别处理顶部工具栏及底部内容区域。
- 页面文案同时维护中文默认资源和 `values-en`，已有控件 ID 与导航参数保持稳定。

界面改动后检查：文件列表与网格、抽屉、连接列表及表单、插件列表及详情、后台任务、root、设置、关于页、文件操作弹窗和图片浏览。至少覆盖浅色/深色、窄屏/横屏、大字号，以及表单打开键盘的状态。

## 发布流程

推送 `v*` 标签会触发 `.github/workflows/release.yml`：

- 构建主应用 APK、Li 和 Yue 的 GEP 插件，以及 Yue HTML 插件包，并保存为 GitHub Actions 构建产物。
- 将标签去掉 `v` 前缀作为版本号，发布 `giant-explorer-plugin-core` 到 Maven Central。

主应用签名需要配置 `SIGNING_KEY`（Base64 编码的签名文件）、`ALIAS`、`STORE_PASSWORD` 和 `KEY_PASSWORD` 仓库 Secrets。Maven Central 发布需要 `CENTRAL_USERNAME`、`CENTRAL_PASSWORD`、`GPG_PRIVATE_KEY` 和 `GPG_PASSPHRASE`。

本地发布插件核心库需要 JDK 21、Android SDK（API 37）和可用的依赖下载环境，在仓库根目录执行：

```sh
./gradlew :giant-explorer-plugin-core:publishToMavenLocal
```

产物写入本地 Maven 仓库；也可使用现有的 `publish-local.sh` 脚本。仓库不再提供 JitPack、GitHub Packages 或 GitHub Releases 发布流程。
