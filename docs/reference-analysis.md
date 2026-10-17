# R196 参考资料分析管线

开发者：NingMeng123

该管线完成了参考包的安全提取、JVM 结构解析、官方原版字节对照和内容声明库存。**结构分析完成不代表全量玩法语义已经还原。** 生成结果中的 `semantic_review_complete` 固定为 `false`，待规则审阅者逐项核实启用条件、调用链和现代适配方案。

## 输入与已核实结果

输入文件为仓库根目录 `MITE 1.6.4 R196.zip`，SHA-256：

```text
fb7ad265d05749e0cd1e54938f6c36ecbe9ea1d4d90d4a04c519615e1e0218c3
```

| 检查 | 结果与含义 |
| --- | --- |
| Class 结构 | 1,186 个 class 全部解析成功，全部带有 SourceFile |
| 原版对照 | 同 JVM 类名比较得到 880 个字节不同、306 个原版未包含的类 |
| 模块分类 | 覆盖全部 class，按源码文件名启发式分类；未分类基础设施单独保留 |
| 反编译输出 | CFR 0.152 输出 1,185 个 Java 文件；一个内部枚举并入外层文件 |
| 反编译限制 | `zg` / `ItemBlock.tryPlaceAsBlock` 在 CFR summary 中报告无法完全结构化，必须核对字节码 |
| 资源包 | 安全提取内嵌资源包，含一个语言文件；未执行任何归档安装器 |
| 内容声明 | 447 个物品字段、221 个方块字段、89 个材料字段的初始化证据 |
| 装备材料 | 17 组枚举构造参数，记录耐久系数、附魔能力、品质上限引用和名称 |
| 制造库存 | 16 个配方矩阵、282 个配方调用点；未展开循环，不等于最终配方数 |
| 生成证据 | 55 个使用 Random 的生成相关方法，记录随机调用及方法数值字面量 |
| 受限运行时注册 | 667 个实际物品、221 个方块、89 个材料、902 个有序合成注册、28 个熔炼注册 |
| 实体与附魔 | 85 个实体名/ID/Class 注册关系（未实例化实体）；38 个附魔及其名称、难度、权重与级数上限 |

`Packet89PlaySoundOnServerAtEntity$enum_sound` 保留独立的 class 证据，但 CFR 输出位于 `Packet89PlaySoundOnServerAtEntity.java`。索引的 `decompiled_path` 已处理此类内部类映射。CFR 对缺失第三方依赖的注释与局部结构警告均需在语义审阅中考虑，反编译输出不是可直接编译的移植源文件。

## 重建与查询

工具只依赖 Python 标准库；反编译步骤另需可运行 CFR 的 Java。默认路径从脚本位置推导，不要求固定工作目录。联网仅用于官方 Mojang 元数据和已锁定版本的 CFR。

```powershell
# 下载时核验官方元数据中的 SHA-1，CFR 核验已记录的 SHA-256。
python tools/reference/analyze.py --download --decompile

# 从已存在的官方 client / CFR 缓存离线重建。
python tools/reference/analyze.py --decompile

# 根据本地反编译结果重建事实库存。
python tools/reference/catalog_content.py

# 查看 SourceFile、方法以及精确字节码引用。
python tools/reference/query.py --source FoodStats --method nutrition --details
python tools/reference/query.py --source EntityPlayer --method health
python tools/reference/query.py --calls-to "java/util/Random.nextInt"

# 独立二进制夹具、路径攻击及内容提取测试。
python -m unittest discover -s tools/reference -p 'test_*.py' -v
```

可选的运行时注册导出在独立 JVM 中执行**注册初始化**，用途是展开循环及矩阵得到最终注册表。它与纯静态分析分开，并要求 Java 17–23；Java 24 之后不再支持该导出的权限拦截器，工具将拒绝运行，而不会自动去掉防护。当前环境可用：

```powershell
python tools/reference/runtime_extract.py --java 'D:\Coding\huanjing\jdk\bin\java.exe'
python tools/reference/validate_registry.py
```

导出前补齐 Mojang 官方 1.6.4 元数据列出的 16 个普通 Java 库并逐一核验 SHA-1，不下载 native 库。R196 覆盖 jar 仅包含归档索引列出的 class，并重新核验每个 class hash，避免残留文件混入。初始化顺序为 `aqz` 方块注册先于 CraftingManager，以遵循旧版方块、物品和统计系统的循环依赖。不会调用游戏 main，也不会创建客户端、服务器、世界或任何实体实例。

若 Java 不在 PATH，可传 `--java '绝对路径/java.exe'`。不传 `--download` 时不会联网；不存在官方 client 缓存则仍可生成 R196 索引，但不会生成可比较的原版差异。`--decompile` 即使在离线模式也会重新核对 CFR 的锁定 SHA-256，拒绝执行不匹配的工具。

## 输出与数据契约

`reference-data/` 保存可版本管理的事实索引；第三方二进制、原资源、完整常量表和反编译源码只写入被忽略的 `.reference/`，不复制到模组 `src`。

| 路径 | 内容 |
| --- | --- |
| `reference-data/summary.json` | 归档 hash、class 总数、启发式分类、原版比较计数和局限 |
| `reference-data/archive-manifest.json` | 每个输入条目大小、hash、提取或跳过动作 |
| `reference-data/class-index.json` / `.csv` | JVM 类名、SourceFile、模块、成员数量、hash、反编译映射 |
| `reference-data/class-differences.json` | 按名称与描述符匹配的字段和方法新增、移除、变化 |
| `reference-data/content-catalog.json` | 注册声明、装备材料参数、配方矩阵及调用点、随机生成候选证据 |
| `reference-data/language-index.json` | 语言文件位置、hash、条目数量 |
| `reference-data/tool-provenance.json` | 官方元数据、client、CFR 的下载来源及校验信息 |
| `reference-data/decompilation.json` | CFR 返回码、输出数量和 summary 中的结构警告 |
| `reference-data/runtime-registry.json` | 受限 JVM 中的最终注册表、静态开关与原始字段数值 |
| `reference-data/runtime-provenance.json` | 运行时 helper hash、覆盖 jar hash、Java 路径和依赖校验 |
| `reference-data/runtime-validation.json` | 静态与运行时证据交叉验证结果及例外统计 |
| `.reference/r196/class-details.json` | 详细字段、方法、常量池、实际字节码引用、数值字面量及 LineNumberTable |
| `.reference/r196/classes/` | 原 class；可用 `javap -c -p -l` 进一步核验 |
| `.reference/r196/decompiled/` | CFR 产生的本地参考源码和 summary |
| `.reference/r196/resources/` | 资源包安全解包结果，仅用来分析已有内容，不用于还原旧版材质 |
| `.reference/r196/languages.json` | 本地完整语言条目，仅作为内容名称辅助证据 |
| `.reference/r196/metadata/` | README、更新日志、版本 JSON 等原始文本元数据 |
| `.reference/downloads/` | 官方 client、CFR 和元数据缓存 |

`class-details.json` 中的方法 `code.references` 来自实际 opcode 解码，记录字节码 offset、opcode、目标与最近的原始源代码行号。`pool_references` 仅表示常量池中存在引用，不能证明方法执行。`source_line` 是原编译源码行号，内容库存的 `decompiled_line` 是 CFR 输出行号，二者不能互换。

内容表达式的 `kind` 区分已解析字面量、符号、构造调用、数组、类型转换与待求值表达式。只有数字、字符串、布尔及 null 字面量被求值；例如 `j * 4` 与 `Skill.FARMING.id` 保留为待核实表达式。物品构造器首参数可能是偏移值，不能直接当最终旧版注册 ID。构造器继承行为、后续修改、配方覆盖次序、循环和开关必须继续追踪。

生成相关方法中的数值不直接标记成最终概率。例如 `nextInt(16)` 可能选择坐标而非掉落判定；候选库存只定位随机调用和原始数值行号，防止把常量表误读成玩法结论。

运行时库存验证得到 756 个注册静态符号匹配、667 个构造器 ID 与独立静态参数一致，另将 `yc.genericFood` 单列为未进入物品数组的哑对象。因此 447 个物品静态声明对应 446 个普通注册物品，加上 221 个方块物品，共 667 个实际注册物品。

902 个合成注册包含 505 个普通有序配方、393 个普通无序配方和 4 个算法配方（染色、烟花、地图复制、地图扩展）。地图扩展虽继承有序配方且有模板，仍标记为动态，其模板中心的数量 0 是占位参数。`CraftingManager.findMatchingRecipe` 内的特殊维修逻辑，以及熔炉对沙子的批量消耗和热度分支，不会仅凭注册表被视为已经覆盖。注册顺序、旧版子类型通配值 32767、工作台材料及技能位集全部原样记录；其中 `professions_suppressed=true`，技能位集存在不等于技能门槛默认启用。

## 安全边界与验证

解包前拒绝绝对路径、`..`、Windows 盘符/数据流、反斜杠、保留设备名、尾部点或空格、大小写重复路径、ZIP 符号链接以及超限条目。写入前再次验证解析后的目标路径仍在目标目录内，避免既存链接重定向。每项未压缩大小上限为 128 MiB，总上限 512 MiB，嵌套资源包采用相同检查。

`Setup.exe`、`Setup.command` 和归档附带的 `7za.exe` 均保留在输入压缩包中，管线不提取或运行。纯静态管线只把 Java class 当作数据，唯一运行的外部分析程序是通过 SHA-256 锁定的 CFR。

可选的 `RegistryExport` 会加载原参考 class 的静态初始化器，因此额外安装不可卸载的只读 guard：拒绝文件写入/删除/执行、无关文件读取、网络、子进程、native 加载、新线程和 `System.exit`。每次导出均先执行 7 项防护探针，确认文件写入、进程、网络、native、新线程、卸载 guard、无关文件读取被拒绝；任一失败则终止。它不是运行任意不可信程序的通用沙盒，而是经过静态检查后的特定注册导出附加防护。原注册程序报告的首条错误也会保存在输出中，本次为 null。

测试覆盖 JVM 魔数和每个截断位置、modified UTF-8 空字符及代理对、双槽 long/double 常量、字段 ConstantValue、方法调用行号、tableswitch/lookupswitch/wide 边界、成员差异、恶意 ZIP 路径、大小写冲突、嵌套穿越、大小限制、安装器跳过以及未验证 CFR 的执行阻断。内容测试覆盖嵌套矩阵、含逗号和空格的配方字符串、流式参数、循环不展开、静态循环局部变量遮蔽字段。

Windows 不允许创建实际符号链接时，该集成测试会明确跳过；独立测试通过受控的路径解析结果验证相同的越界防护分支，仍会执行。

官方来源：[CFR 发布页面](https://www.benf.org/other/cfr/)、[Mojang 版本列表](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)。CFR 发布页公布的 0.152 MD5 为 `8a85ada8cec494121246805a5562b82b`，本次下载同时记录并锁定 SHA-256 `f686e8f3ded377d7bc87d216a90e9e9512df4156e75b06c655a16648ae8765b2`。官方 Minecraft client SHA-1 为 `1703704407101cf72bd88e68579e3696ce733ecd`，完整来源链保存在 provenance 中。
