# MITE: Modern

开发者：**NingMeng123**。原作 Minecraft Is Too Easy 由 **Avernite** 创作。

将 Minecraft 1.6.4 MITE R196 的生存与成长规则重实现到 Minecraft 1.21.1 NeoForge，保留现代主世界地形、材质和功能内容，并逐项调整平衡。支持单人游戏和独立服务器。

## 当前状态

项目正在建立 R196 的规则证据、现代适配规格和可运行开发工程。**目前不是完成版 MITE 复刻，不应将工程能够启动视为全部玩法已实现。**

实现与验证状态见 [实施状态](docs/implementation-status.md)。每项玩法只有完成游戏实测后才会标为已验证。

## 开发环境

- Java 21（64 位）
- Minecraft 1.21.1
- NeoForge 21.1.259
- ModDevGradle 2.0.148
- Windows 使用项目自带的 `gradlew.bat`

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

使用独立测试存档，不导入 1.6.4 存档。

## 每个小功能的交付流程

1. 为功能记录 R196 证据或明确的现代适配设计。
2. 实现并通过适用的编译、自动测试和资源检查。
3. 打开开发客户端，在游戏内验证该功能，记录步骤、结果与证据。
4. 修复发现的问题并复测。
5. 使用中文提交说明，将通过实测的代码推送到指定 GitHub 仓库。

仅编译成功或仅通过 GameTest 不等于完成游戏实测。测试失败或尚未测试的功能不得推送为完成项。

## 参考来源

- 用户提供的 `MITE 1.6.4 R196.zip`，SHA-256：`fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3`
- [MITE 官方网站](https://avernite.ca/MITE/)
- [官方配方](https://avernite.ca/MITE/crafting_recipes/)
- [官方生存提示](https://avernite.ca/MITE/hints/)
- [NeoForge 1.21.1 文档](https://docs.neoforged.net/docs/1.21.1/)

原始游戏包、下载依赖和反编译源码只用于本地分析，放在 Git 忽略目录中。发布内容以本项目实现、规则数据及文档为准。
