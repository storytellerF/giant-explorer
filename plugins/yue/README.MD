# yue【阅】

图片浏览器

giant-explorer 的插件

独立应用可从系统文件选择器或 Giant 的文件菜单打开获授权的当前图片。安装在 Giant 内的 Fragment GEP 插件还可浏览同目录中的图片。

## Fragment GEP 打包

在仓库根目录执行 `./gradlew :plugins:yue:yue-plugin:packGep`。
产物为 `plugins/yue/yue-plugin/build/outputs/gep/yue.gep`。

`yue-gep` 是用于插件资源打包的 Android application 模块，没有启动页面。
它将 `yue-plugin` 的代码、编译后的 Manifest、资源表和插件元数据打包，
然后原样复制为 `.gep`，供宿主通过 DexClassLoader 和 Android 包资源加载接口读取。
该产物无需作为应用安装，也不使用发布签名密钥；独立应用仍由 `yue-app` 构建。
不要将 AAR 解压后重新压缩为 Fragment GEP，因为 AAR 中的资源尚未完成链接。
