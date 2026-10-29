# 开发与发布

## 本地构建与验证

使用 JDK 21 和 Android SDK API 37，通过仓库的 Gradle Wrapper 构建。依赖与插件版本集中维护在 `gradle/libs.versions.toml`，升级后同时验证 Debug、Release 与插件模块。配置 `ANDROID_HOME` 或本地 `local.properties` 的 `sdk.dir` 后，在仓库根目录执行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :plugins:yue:yue-plugin:assembleDebug
./gradlew :app:lintDebug :app:detekt
```

普通 `detekt` 检查只读源码，可以与编译或其他只读分析一起运行；发现违规时仍按现有规则和 baseline 报告并失败。需要自动修复时显式执行：

```sh
./gradlew detekt --auto-correct
```

自动修复会改写源码，执行时应避免与读取或修改同一源码的编译、分析任务重叠。现有 `excludeCorrectable: false` 会让修复当次仍报告可修复违规并返回失败；修复后检查差异，并重新运行普通 Detekt、编译和测试。CI 使用普通检查，不依赖自动修复；Detekt 内部 AST 并行设置独立于自动修复，无需为此调整。

调试 APK 位于 `app/build/outputs/apk/debug/`。Yue HTML 插件可用 `bash plugins/yue-html/dispatch.sh --package-only` 打包（需要 `zip`）。ZIP 根目录包含 `config`、`index.html` 和 `imgTouchCanvas.js`。省略 `--package-only` 会同时尝试向已连接设备分发。

HTML 插件的 `file.fullPath()` 返回解析后的完整 URI，供 `plugin.base64(...)` 读取。保留 URI 的 scheme 和 authority，不要仅传递路径部分。

文件操作的目标校验按 URI 路径规范化点段和重复斜杠，保留路径字符，并分别比较 scheme、authority 和存储树。不要使用宿主系统的 `java.io.File` 路径分隔符，使 Windows 本地测试与 Android 保持一致。窗口重建回归测试从应用缓存目录启动；真实触摸点击前等待目标状态渲染和按钮位置稳定，避免权限浮层和弹窗移动干扰。

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

后台复制、移动、删除和插件文件任务统一在服务的协程作用域中执行。`FileOperationEventBus.shared` 在任务结束时广播列表失效事件（包括失败和取消，以覆盖部分文件变更）。Paging 缓存属于 `FileListSearchViewModel.viewModelScope`，不能绑定会随导航销毁的视图作用域。文件列表的观察者与收集器均绑定当前视图；按 STARTED 生命周期订阅任务事件并调用 Paging 刷新；事件保留最新一次，返回前台或重建视图后也会刷新，不依赖 Activity 的结果回调，也不弹出 Toast。

`FileOperationHost` 在应用的串行协调调度器上维护按任务编号隔离的不可变快照。快照包含操作类型、处理项、目标目录及数量是否已知，插件未报告数量时使用不定进度，不显示虚假的百分比。每次操作使用新编号，相同编号不会重复执行；弹窗通过参数记录编号，重建后订阅该任务，关闭一个弹窗不会清除其他任务状态。删除保持原始 URI，在服务内串行执行并重新检查存在性、文件类型和目录内容；已不存在的目标按成功处理并明确提示无需重复删除。权限失败且文件仍存在时保留失败结果，零字节文件使用数量进度。

文件操作弹窗使用统一布局：状态标题、处理项与目标目录、进度、保留到结束后的数量/容量摘要，以及按需展开的详情。正文可滚动，底部取消和关闭操作固定。取消先进入 CANCELLING，执行线程确认退出后才进入 CANCELLED；已经提交的文件更改不回滚。计算和文件处理回调检查任务取消状态。后台运行期间保留任务快照，但不会在返回首页时重新弹出；后台任务成功、失败或取消后自动清理快照。

状态测试覆盖复制、移动、删除和插件的准备、运行、停止中、成功、失败、取消，含无操作、零字节、空目录和混合目录。设备测试覆盖实际文件读写、源文件消失、目标失效、不可删除文件、实际取消，以及浅色/深色、小屏和大字号的弹窗渲染；运行：

```sh
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.storyteller_f.giant_explorer.service.FileOperationStateMatrixTest,com.storyteller_f.giant_explorer.control.FileOperationDialogStateTest
```

弹窗状态截图由设备测试写入应用外部图片目录的 `file-task-states/`，属于测试产物，不提交到仓库。

## 依赖更新

common-ui-list 系列依赖通过 `commonUiList` 统一使用 `0.0.1-alpha2`，包括运行库、注解和 KSP 编译器。点击回调通过 `bindingAdapterPosition` 或 `viewholder` 从当前 adapter 的 `ItemHolderProvider` 获取条目；无效位置返回空时结束回调，不再读取 ViewHolder 上的旧 `itemHolder` 属性。库移除了 `SimpleDialogFragment`，宿主的 `GiantDialogFragment` 直接继承原生 `DialogFragment`，实现结果回传接口并在销毁视图时清理绑定。网格导航回归测试覆盖切换网格、进入子目录、系统返回三次及图标菜单绑定，连接设备后运行：

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.storyteller_f.giant_explorer.control.FileGridNavigationTest,com.storyteller_f.giant_explorer.control.FileDeletionNavigationTest,com.storyteller_f.giant_explorer.service.FileOperationRegressionTest
```

`.github/dependabot.yml` 每周一检查根 Gradle 多模块工程和 GitHub Actions。Gradle 更新统一合并为一组，GitHub Actions 更新单独合并为另一组；不再按 Gradle artifact 命名空间拆分。不自动合并，也不排除主版本升级。

这些分组用于常规版本更新；安全更新不受这些分组规则控制。本地维护的 AAR 不由 Dependabot 升级。配置合入默认分支后由 GitHub 执行，升级 PR 仍需通过构建与测试。

## CI 缓存

`.github/workflows/test.yml` 保留静态分析、单元测试、主应用编译与 GEP 内容检查。仅 `main` 的 push 运行允许三个作业写入 Gradle 缓存；PR 始终只读，恢复默认分支的兼容缓存。

三个作业分别覆盖 detekt 及规则插件、JUnit 测试运行时、Li/Yue 插件打包依赖，因此各自保存缓存，避免单一写入者遗漏其他检查的依赖。使用 `setup-gradle` 默认的缓存去重和成功后清理，不额外添加预热作业或重复的缓存 action。Release 的 tag 缓存不作为 PR 的缓存来源。

验证缓存策略时，先运行 main，再创建新的 PR：检查 Gradle 作业摘要中的恢复/保存条目，并通过仓库 Actions 缓存列表确认 `refs/heads/main` 下存在缓存、PR 未写入缓存。记录缓存总大小、各作业冷/热运行耗时和 Setup/Post Setup Gradle 耗时；后者包含恢复、清理及上传成本，不能仅以构建步骤耗时评估收益。冷/热运行应使用相同的构建配置；共享 runner 的耗时波动不代表缓存策略的稳定收益。

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

File operations submitted by a file-list window retain their UUID task key and carry a sealed
`FileTaskOrigin.Window` identity. The identity is saved across Activity recreation. The task Host
atomically rejects another submission from that window while any task still requests its dialog,
including completed results. `FileTaskDialogState` represents `Idle` or `Showing(taskKey)` without
nullable state fields. Each Activity derives its dialog from durable task state, so another window
cannot consume its presentation request. Closing a result or choosing background execution releases
the window; task completion alone does not. Back and outside taps do not dismiss the task dialog.
Non-window service callers use the explicit `Detached` origin and do not request an Activity dialog.
