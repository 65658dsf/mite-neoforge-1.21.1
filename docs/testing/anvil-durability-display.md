# 铁砧耐久显示验证

开发署名：NingMeng123。日期：2026-10-17。实现始于 `f50872c0c76df903c8f3d56bff3cf4e9b8b64b23`，提交时保留后续的 `0ec4720` 命令功能。用户已授权提交并推送；没有操作游戏窗口，逐项画面与真实多人验证仍待手测。已有提示删除及其他无关未提交文件不纳入本次提交。

物品提示、铁砧界面及 Jade 显示的范围和手测步骤见[功能说明](../anvil-durability-display.md)。只新增读取和显示，不改维修磨损或铁砧掉落规则。

## 最终结果

| 验证 | 结果 | 工作区证据 |
| --- | --- | --- |
| 提交快照构建与 JUnit | 构建成功；1525 项，失败/错误/跳过均为 0 | `build/verification/anvil-commit-verified.log`、`build/verification/anvil-commit-verified.json` |
| 提交快照无 Jade 全量服务端 | 1824 项必需 GameTest 全通过；新增物品与菜单检查包含真实掉落/保存/再放置、完整大数同步、维修及旧零经验结果点击，包含后续命令功能的回归测试 | 同上日志；快照内隔离世界 `build/verification/gametest-run/` |
| 有 Jade 隔离服务端 | 1 项必需 GameTest 通过，实际遍历七种材质的 21 个阶段状态；验证真实服务器读取、无写入及无效目标拒绝 | `build/verification/anvil-durability-jade-focused.log` |
| Jade 客户端 API | 实际 Jade 15.10.6；21 状态、15 类非法数据、精确翻译参数和普通物品排除通过；原作物检查仍通过 | `build/verification/anvil-durability-client-verified.log`、`anvil-jade-audit.json`、`crop-jade-audit.json` |
| 原生物品提示事件 | 自动注册的真实事件总线调用通过；普通/高级各追加一次，原有装备/门提示对照通过，物品数量和组件不变 | `build/verification/anvil-item-tooltip-audit.json` |
| 提交快照发布包 | 1152 个生产类、3088 个资源逐字节匹配；2403 JSON、326 Mixin 声明正确，新类/配置标题已打包，未混入 Jade API/JAR、测试或参考源码 | `build/verification/anvil-commit-package.json` |

无窗口客户端检查没有创建 Minecraft 实例、世界或图形上下文，也没有执行渲染，因此不等同于已观察界面位置、颜色或完成真实多人测试。

提交范围复验从暂存树 `ff4aa5c6590ef7ab8fc69d182716e193d46926fd` 导出独立快照，路径为 `build/verification/anvil-commit-ff4aa5c6590e/`。该快照包含 `0ec4720` 命令功能，排除了工作区原有的工作台提示删除与其他无关文件；构建、全量服务端测试及发布包审计均在快照中执行。验证后仅补充本文和实施状态中的结果记录，生产及测试源码未再更改。原工作区首次回归记录为 `anvil-durability-verified.log`（1820 项）。

## 回归修正与隔离前提

读取缺失的方块实体时，普通 `Level.getBlockEntity` 会为合法实体方块创建默认数据。本次显示改为检查已加载区块并使用 `EntityCreationType.CHECK`，没有精确实体时隐藏，避免查看动作创建一份全新耐久。菜单新增四个 16 位同步字段显式在已有字段之后注册，保留费用索引 0、来源维修索引 1；既有零费用维修点击测试与新增协议断言全部保持通过。

物品测试的空模板只有 1×1×1，原放置点在模板清理范围外。夹具现每轮显式清空目标，仍真实调用 `BlockItem.place` 并严格核对数量、组件和重放数值，未改生产放置规则。

datagen 的 GAME 事件总线默认保持 shutdown，因此直接投递事件会静默跳过全部监听器。客户端审计先检查三个已有自动注册的提示监听器和停用时的对照，再只调用公开 `start()` 激活原总线，核对精确提示，最后恢复原 shutdown 状态；没有手动注册监听器或运行游戏客户端。

带 Jade 的全量旧测试会创建未完成模组握手的 EmbeddedChannel 仿真连接，Jade 登录 ping 在这些连接上被拒绝。本轮没有改这些旧夹具或生产网络代码：完整回归使用无 Jade 环境；Jade 实际服务端读取改在仅包含 `AnvilJadeGameTests` 的隔离测试集合中执行，该测试不创建玩家连接。所有生产代码仍完整加载。

数据生成的 24 份无关物品标签仅有 `remove` 数组顺序变化，经逐文件比较证明其他字段和移除成员完全一致后恢复原字节，最终构建在恢复后完成。记录为 `build/verification/anvil-durability-resource-order.json`。

## 执行入口

普通客户端/服务端与隔离验证都使用 Java 21、Minecraft 1.21.1、NeoForge 21.1.259。外部 Jade 只复制到隔离目录，未移动或替换用户 `run/mods`。

```powershell
.\gradlew.bat runData -PmiteCropJadeAudit=true -PmiteDataDirectory=build/verification/anvil-durability-jade-run --console=plain
.\gradlew.bat build runGameTestServer -PmiteGameTestDirectory=build/verification/anvil-durability-verified-run --console=plain
```

Jade 定向验证使用以下临时 init 脚本，保存在 `build/verification/anvil-durability-jade-focused.init.gradle`；新的 build 目录隔离旧的全量测试类，模板资源保留。复跑时应选未使用的目录，只在其运行目录 `mods/` 放 Jade。

```groovy
gradle.beforeProject { p ->
    if (p.path == ':') {
        p.layout.buildDirectory.set(p.layout.projectDirectory.dir('build/verification/anvil-durability-jade-focused-build'))
        p.afterEvaluate {
            p.sourceSets.gametest.java.setIncludes(['**/AnvilJadeGameTests.java'])
        }
    }
}
```

```powershell
.\gradlew.bat --no-configuration-cache -I build/verification/anvil-durability-jade-focused.init.gradle runGameTestServer -PmiteGameTestDirectory=build/verification/anvil-durability-jade-focused-run --console=plain
```

## 测试包

本次提交快照构建：`build/verification/anvil-commit-ff4aa5c6590e/build/libs/mite-0.1.0.jar`。

SHA-256：`3ef66445b479796f37ff0364fdce7bc44bcd8acb9275a5e9468848076b12c8e5`。
