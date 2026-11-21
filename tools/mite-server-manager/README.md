# MITE 服务端管理器

开发者 **NingMeng123**。基于 Go、Wails 和本地 Web 前端的 Windows x64 单文件应用，适用于 Minecraft 1.21.1 的 MITE: Modern / Modern ITE NeoForge 服务端。

## 使用

1. 把 `MITE-Server-Manager.exe` 放在已有服务端根目录并双击打开，也可以在控制台顶部选择目录或通过 `--server "D:\服务端目录"` 指定。在空目录打开时，点击 **下载服务端**，选择 CurseForge 版本并查看对应客户端的更新日志。管理器下载并校验服务端 ZIP，解压到新建的版本子目录，再自动切换到该服务端。
2. 打开 **启动设置**，点 **扫描 Java** 查找本机安装并选择可用的 64 位 Java 21。结果自动保存在本机，下次打开时直接显示。Java 安装有变化时可重新扫描。也可以浏览选择 `java.exe` 或手动输入 Java 安装目录。留空时会在启动时自动查找。最小和最大内存使用 MiB，保存时更新服务端的 JVM 参数文件。
3. 首次运行时，在 **启动设置** 查看并接受 Minecraft EULA。点击 **启动服务器** 后，若缺少运行库，管理器会调用服务端随附的 NeoForge 安装器。首次安装按公网 IP 国家码选源。中国大陆依次尝试 BMCLAPI、MCMirror、SJTUG BMCLAPI，并交由官方源补齐缺项。其他地区或无法识别时使用官方源。镜像文件须与安装器中的大小和 SHA-1 一致。
4. 服务端启动后可查看实时日志、在控制台执行 Minecraft 命令，或从在线列表选择玩家执行踢出、封禁、OP 和白名单操作。管理员也可以输入玩家用户名处理离线玩家。停服按钮会发送 `stop` 并等待服务器保存世界。
5. **服务器与 MITE** 支持按中文标题搜索和编辑服务器、MITE 本体和 ITE 扩展设置。每个文件都可以切换到原文。MITE 配置在对应模组首次成功启动后生成。停服后保存，下一次启动时加载更改。
6. 管理器打开时检查已识别的 MITE Modern ITE 服务端更新。发现新版后可在控制台查看更新日志。停服并确认风险后，管理器先把服务端备份为 ZIP，保存在同级的 `MITE-Server-Backups` 目录，再下载并校验新版，更新整合包管理的文件。若需恢复，可把备份解压到新的空目录，再在管理器中选中恢复后的服务端。

管理器通过进程输入和输出来控制由本窗口启动的本地服务端。窗口使用 Windows 的 Microsoft Edge WebView2 Runtime。

右上角可切换 **简体中文 / English**，应用会记住选择。

## 从源码构建

在本工具目录执行。构建需要 Go 1.25 或更新版本以及 Wails v2 CLI，前端资源直接嵌入 EXE。

```powershell
go install github.com/wailsapp/wails/v2/cmd/wails@v2.15.0
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

产物位于仓库的 `build/mite-server-manager/MITE-Server-Manager.exe`。
