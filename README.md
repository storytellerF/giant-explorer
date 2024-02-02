# 文件管理器

## 功能

1. 插件
2. 访问document provider
3. 暴露出一个content provider
4. 访问ftp，sftp，ftps，smb，webdav 资源
5. root 访问

关于第三条，本APP 会暴露一个content provider，
但是这个uri 对应的资源可以是另一个APP 提供的content provider，
虽然这听起来很棒，但是在某些手机上有可能无法正常工作，并且原有的授权还会失效。
即使在某些某些手机上可以使用，也无法正常使用**canRead**（这原本是用来鉴权的，当前并不会影响使用）。

## Settings

1. split

通过代码控制打开分屏模式至少需要Android 7，其中至少达到Android 12L 才可以在全屏模式打开分屏，否则只能在用户手动进入分屏模式时在另一侧打开一个分屏窗口。

2. freeform

至少需要 Android 11。启用此选项时会显示一个Toast 告知用户此功能是否可用。

3. freeform/split

优先级：freeform > split > normal

不会根据现有的结论限制某一个选项的使用，因为无法排除OEMs 自行支持的情况。