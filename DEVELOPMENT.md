# 开发与发布

## 本地构建与验证

使用 JDK 21 和 Android SDK API 37，通过仓库的 Gradle Wrapper 构建。依赖与插件版本集中维护在 `gradle/libs.versions.toml`，升级后同时验证 Debug、Release 与插件模块。配置 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir` 后，在仓库根目录执行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :plugins:yue:yue-plugin:assembleDebug
./gradlew :app:lintDebug :app:detekt
```

调试 APK 位于 `app/build/outputs/apk/debug/`。Yue HTML 插件可用 `bash plugins/yue-html/dispatch.sh --package-only` 打包（需要 `zip`）。ZIP 根目录包含 `config`、`index.html` 和 `imgTouchCanvas.js`。省略 `--package-only` 会同时尝试向已连接设备分发。

HTML 插件的 `file.fullPath()` 返回解析后的完整 URI，供 `plugin.base64(...)` 读取。保留 URI 的 scheme 和 authority，不要仅传递路径部分。

共享 `FileSystemProvider` 通过应用 `Context.classLoader` 显式加载 `FileInstanceFactory`，以支持没有应用上下文类加载器的 Binder 调用线程。保留与文件系统库相同的路径规范化；复制文件描述符后关闭原输入流。设备回归测试覆盖这项类加载边界。

## 界面规范

Li 和 Yue 独立应用使用 Material 3 浅色/深色主题、可滚动的介绍与使用指引页，并由 Activity 统一避让系统栏。Li 的压缩操作由 Giant 宿主提供，独立页展示操作指引；Yue 首页使用系统文件选择器打开图片。Yue 预览底部提示单独占据布局空间，不覆盖图片。

Giant、Li、Yue 的启动图标使用矢量 adaptive icon，并提供单色图层；minSdk 26 下不再维护旧位图启动图标。Giant 和 Li 的 Debug 通知通过资源覆盖使用透明底单色图标，不改变 LeakCanary 的诊断行为。插件操作菜单读取已安装应用的图标；导入插件按入口类识别 Li/Yue，HTML 和未知插件使用类型图标，重命名插件包不会影响识别。

主应用 EasyLauncher 任务显式跟踪 `src/main/res/**/ic_launcher*.xml`，图标变更会重新生成 Debug 标记；不要直接修改 `build/generated/res/easylauncherDebug` 中的产物。

Debug 通知覆盖保留与 LeakCanary 2.14 相同的 `drawable-anydpi-v21` 限定符，避免系统优先选中依赖资源；对应 `lint.xml` 仅对该目录豁免 `ObsoleteSdkInt`。`keep_notification_icon.xml` 保留由依赖字节码引用的通知资源。升级 LeakCanary 时核对资源名称与限定符。

- 菜单和浮层共用 `design_styles.xml` 中的样式。内容弹窗继承 `GiantDialogFragment`，统一圆角、最大宽度和键盘缩放；长表单使用滚动容器。路径选择的正文可滚动，操作区固定在底部；避免固定窗口高度遮挡软键盘上方的按钮。排序面板固定标题与完成按钮，只滚动选项区。文件菜单按传输、信息、删除分组，删除使用错误色。

- `app/src/main/res/values/design_styles.xml` 定义共享文字、按钮、表单、工具栏与弹窗样式。颜色使用 Material 语义角色，浅色和深色主题共用这些组件。
- 页面外边距使用 `screen_padding`，宽屏通过 `values-w600dp` 增大边距。交互控件至少保留 48dp 的触摸区域，长内容放入可滚动容器。
- 原生界面沿用 ViewBinding；后台任务中的 Compose 行通过 `GiantComposeTheme` 读取宿主颜色。界面样式不单独维护业务状态。
- 独立 Activity 使用 `applyScreenInsets()` 处理系统栏、刘海和键盘；主文件页分别处理顶部工具栏及底部内容区域。
- HTML 插件在 WebView 渲染进程退出时关闭预览，取消未完成的桥接调用，并销毁 WebView 与仍归 Android 所有的消息端口；已转交给 JavaScript 的端口不再重复传输或关闭。设备回归测试通过 `chrome://crash` 验证宿主应用仍能运行。WebKit 1.17.1 的检查器会误报 Kotlin 父类构造调用，因此仅该客户端实例局部抑制 `MissingOnRenderProcessGone`；升级到包含 [b/548989591 修复](https://android.googlesource.com/platform/frameworks/support/+/8003b908c7bfaecd6636512ad5cb1720a142ad51%5E%21/) 的稳定版后移除。
- 页面文案同时维护中文默认资源和 `values-en`，已有控件 ID 与导航参数保持稳定。协议名、品牌名等不可翻译资源只在默认资源中定义；端口号保持不分组的 ASCII 数字格式。
- Activity 背景由窗口主题绘制，避免页面根布局重复绘制同色背景。导航宿主使用 `FragmentContainerView`，从 `supportFragmentManager` 获取 `NavHostFragment.navController`，并验证 Activity 重建后仍保持正确目的地。

界面改动后检查：文件列表与网格、抽屉、连接列表及表单、插件列表及详情、后台任务、root、设置、关于页、文件操作弹窗和图片浏览。至少覆盖浅色/深色、窄屏/横屏、大字号，以及表单打开键盘的状态。

存储弹窗通过 `StorageSpaceHost` 在 IO 调度器读取容量，视图按 STARTED 生命周期渲染；销毁视图时取消读取。容量统一使用系统本地化格式，明确展示已用、可用和总容量。可用取 `StatFs.availableBytes`，已用为总容量减可用（包含系统和预留空间）；未挂载或无法访问时显示说明，不显示虚假的零容量或原始挂载状态。

后台复制、移动、删除和插件文件任务统一在服务的协程作用域中执行。`FileOperationEventBus.shared` 在任务结束时广播列表失效事件（包括失败和取消，以覆盖部分文件变更）。文件列表按视图的 STARTED 生命周期订阅并调用 Paging 刷新；事件保留最新一次，返回前台或重建视图后也会刷新，不依赖 Activity 的结果回调，也不弹出 Toast。

## 依赖更新

common-ui-list 系列依赖通过 `commonUiList` 统一使用 `0.0.1-alpha2`，包括运行库、注解和 KSP 编译器。点击回调通过 `bindingAdapterPosition` 或 `viewholder` 从当前 adapter 的 `ItemHolderProvider` 获取条目；无效位置返回空时结束回调，不再读取 ViewHolder 上的旧 `itemHolder` 属性。库移除了 `SimpleDialogFragment`，宿主的 `GiantDialogFragment` 直接继承原生 `DialogFragment`，实现结果回传接口并在销毁视图时清理绑定。网格导航回归测试覆盖切换网格、进入子目录、系统返回三次及图标菜单绑定，连接设备后运行：

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.storyteller_f.giant_explorer.control.FileGridNavigationTest,com.storyteller_f.giant_explorer.service.FileOperationRegressionTest
```

`.github/dependabot.yml` 每周一检查根 Gradle 多模块工程和 GitHub Actions。Gradle 更新统一合并为一组，GitHub Actions 更新单独合并为另一组；不再按 Gradle artifact 命名空间拆分。不自动合并，也不排除主版本升级。

这些分组用于常规版本更新；安全更新不受这些分组规则控制。本地维护的 AAR 不由 Dependabot 升级。配置合入默认分支后由 GitHub 执行，升级 PR 仍需通过构建与测试。

## 发布流程

推送 `v*` 标签会触发 `.github/workflows/release.yml`：

- 构建主应用 APK、Li 和 Yue 的 GEP 插件，以及 Yue HTML 插件包，并保存为 GitHub Actions 构建产物。
- 将标签去掉 `v` 前缀作为版本号，发布 `giant-explorer-plugin-core` 到 Maven Central。

主应用签名需要配置 `SIGNING_KEY`（Base64 编码的签名文件）、`ALIAS`、`STORE_PASSWORD` 和 `KEY_PASSWORD` 仓库 Secrets。Maven Central 发布需要 `CENTRAL_USERNAME`、`CENTRAL_PASSWORD`、`GPG_PRIVATE_KEY` 和 `GPG_PASSPHRASE`。

本地发布插件核心库需要 JDK 21、Android SDK（API 37）和可用的依赖下载环境，在仓库根目录执行：

```sh
./gradlew :giant-explorer-plugin-core:publishToMavenLocal -PlocalUnsignedPublication=true
```

本地无签名模式不注册远端发布目标和签名附件，产物写入本地 Maven 仓库；也可使用现有的 `publish-local.sh` 脚本。仓库不再提供 JitPack、GitHub Packages 或 GitHub Releases 发布流程。
