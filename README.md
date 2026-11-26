# Variable Hatch（可变仓室）

一个 GTNH 2.9.0 / GT5U 5.09.54.265 的小型 addon：**用可配置档位的仓室顶掉 tier 选择题**。GTNH 2.8.4 请使用 1.0.3。

多方块只装一个能源仓、又想跑更高等级的配方时，原版的办法是拆机器换更高级的仓、或者堆更多仓。
本模组把这件事收进一个方块：结构等级、供电等级、电流三项都在它自己的 GUI 里切。

- **运行依赖**：GT5U 与 UniMixins；MT-3662 兼容补丁使用 Mixin，无自定义网络包。
- **能源仓**：`Variable Energy Hatch`，继承 `MTEHatchEnergy`，因此任何接受普通能源仓的多方块都认它。
- **其余仓室**：可变动力仓、可变输入/输出仓，分别继承 GT 对应的仓室类。

## 使用方法

右键方块打开 GUI，三项即时生效：

| 项 | 默认 | 可选 | 作用 |
|---|---|---|---|
| 结构等级 Structure tier | LV | ULV..UXV(0..13) | 向结构检查申报的 tier。**只对少数机器有效**（显式读 `getTierForStructure()` 的那几台，见下），EBF/LCR 这类机器根本不看，拨了不会有任何变化 |
| 供电等级 Power tier | LV | ULV..UXV(0..13) | 它向机器申报的电压 = `V[供电等级]`，也就是这台机器的功率预算上限 |
| 电流 Amperage | 2A | {2, 4, 16, 64} | 申报的工作电流（通常单仓时不进入预算；MT-3662 有兼容补丁） |

每一项都是 `[−][只读显示][+]`：显示框里是当前档位（如 `1 · LV` / `2 A`），
**数值只能用两侧的箭头改**，没有输入框、也不吃滚轮。Shift + 点击箭头 = 等级一次调 4 级、
电流直接跳到 2A 或 64A。鼠标悬停在框或箭头上会显示该档位的电压与预算。

关键理解：**机器的功率预算 = 供电等级 × 电流**，而 GT 的超频层数由预算决定
（`log4(预算 / 配方 EU/t)`，每层时间 ÷2、EU/t ×4）。所以"选供电等级"等价于"控制配方的工作电压"。

在 MT-3662 单独使用本仓时这一行成立；其他机器的单仓限制见下。装了多个能源仓时 GT 通常使用「各仓电压的平均值 × 合计工作电流」，
只有在各仓电压相同时才与上式一致 —— 见限制 4。

结构等级与供电等级**互相独立**：可以把结构等级设在 LV 塞进一台 MV 机器，同时把供电等级设到 UV。

但要写清一件事（对过 GT5U 源码）：**绝大多数多方块对能源仓的 tier 没有任何要求**。成型判定只问
"这是不是一个 `MTEHatchEnergy`"（`HatchElementBuilder.java:434-438` → `MTEMultiBlockBase.java:2006-2009`），
`filterByMTETier` 那个区间检查只用在结构**提示 / 自动放置**上（`GTStructureUtility.java:751-762`），
不参与成型。所以"结构等级"的真实适用范围是：

| 机器 | 规则 | 依据 |
|---|---|---|
| goodgenerator 大型聚变 | 仓 tier **≥** 它要求的 tier，否则结构不成立 | `MTELargeFusionComputer.java:483/487` |
| 大型流体提取机 | 玻璃低于 UEV 时，仓 tier **≤** 玻璃 tier | `MTELargeFluidExtractor.java:177` |
| gtpp 化工厂 / 精炼厂 / 核反应堆 | 只影响结构提示与自动放置，不影响成型 | `hatchItemFilterAnd(filterByMTETier(...))` |

两个改不动的反例：GT 自家聚变 MK1/2/3 **直接比较 `mTier`**（`MTEFusionComputer.java:233`），本仓的 `mTier`
恒为 LV，在那三台上会被无条件拒绝；虫洞发生器只收 tectech 的 `MTEHatchEnergyMulti`
（`MTEWormholeGenerator.java:275`），根本装不进去。这两处不是结构等级能救的。

## 四条必须知道的限制

1. **往下切有爆炸风险**。GT 的进线判定是 `进线电压 > getInputVoltage()` 就炸
   （`BaseMetaTileEntity.java:1758-1762`）。把供电等级切到低于现有供电电压时，下一次进包就炸仓。
2. **多数机器单仓时电流档位不生效**。GT 的 `setProcessingLogicPower` 在"恰好一个能源仓且无 exotic 仓"时
   把可用电流硬写成 1A（`MTEMultiBlockBase.java:1021`）。此时预算 = 供电等级 × 1A，与电流档位无关。
   电流要参与预算，机器上得有 ≥2 个能源仓（或另有 exotic 仓）。MT-3662 的兼容补丁允许单个可变仓使用设定电流，并在计算电压时读取当前供电等级；机器外壳的电压限制仍然有效。
3. **结构等级只在少数机器上生效，GT 自家聚变族则完全不接受本仓**。生效范围见上表；在 EBF、LCR 这类
   机器上拨它不会有任何变化 —— 那不是坏了，是这台机器本来就不看。而在 GT 聚变 MK1/2/3 上，本仓会被
   **无条件拒绝**（那三台读 `mTier`，而 `mTier` 恒为 LV 且是 final 字段），调结构等级没有用
   —— 聚变请用原版能源仓。结构检查失败的表现是回到未成型状态（方块保留），把值调回去即恢复。
4. **多个可变仓混用不同供电等级会让预算失真**。GT 的预算是「各能源仓电压的**平均值** × 合计工作电流」，
   不是各仓之和。例：LV@64A + EV@2A 会被算成 1040V × 66A = 68,640 EU/t，而这两个仓实际上最多只能
   收下约 6,700 EU/t（各受自己的电压 × `maxAmperesIn()` 限制，还没算线缆载流）
   —— 机器会选中一个它永远跑不动的配方，表现为「配方已选中但进度条不动」。
   多个可变仓请设成**同一个供电等级**。另注：这里的合计电流取 `maxWorkingAmperesIn()`，
   就是档位值本身（不含 `maxAmperesIn()` 里 `A + A/4` 的进线损耗余量）—— 16A 档就按 16A 计。

## 配方

工作台 3×3，中心一件对应的 **LV 原版仓室**，四周 8 格 **原版拉杆**：

```
拉杆 拉杆 拉杆
拉杆 仓   拉杆
拉杆 拉杆 拉杆
```

| 产物 | 中心件 |
|---|---|
| 可变能源仓 | LV 能源仓 |
| 可变动力仓 | LV 动力仓 |
| 可变输入仓 | LV 输入仓 |
| 可变输出仓 | LV 输出仓 |

成本极低且 LV 就能做 —— 属于"早期就替代全部仓室选择"的强力工具，这是刻意的设计取舍；
若要加门槛，只改中心件为更高 tier 的仓室即可（形状不用动）。

## 与 GT-Simple-Wireless-Network 的配合

不需要任何跨模组代码。GTSWN 的无线能源覆盖板是往任意 `BaseMetaTileEntity` 调
`increaseStoredEnergyUnits` 灌电，并且按宿主的 `getEUCapacity()` 决定补多少
（`GTswn_Cover_EnergyWireless.java:121-123`），而本仓的容量随供电等级自动变化。
所以把 GTSWN 覆盖板贴在本仓上，配置电压 = 供电等级、电流足够大，就能**无线**喂饱它
（网络照扣 1+损耗）。

## 可变动力仓

在工作台用 **LV 动力仓 + 8 个拉杆**合成。右键可分别设置结构等级、输出等级（均为 ULV–UXV）和电流（2／4／16／64A），默认为 LV／LV／2A。动力仓从正面出电，标称输出上限为电压 × 电流，实际输出取决于发电量和电网。

普通动力仓结构入口对可变动力仓开放四档，机器自己的仓数量和结构等级要求仍然适用。比如 GT++ 熔盐反应堆仍要求四个 EV–LuV 结构等级的动力仓。专用动力仓入口和控制器的发电逻辑由各机器决定。

先停机再降低输出档位。GT 通用输出路径在动力仓总容量低于发电量时可能引发多方块爆炸，单 tick 实际注入量超过 2,147,483,647 EU 时也受其计数范围限制。拆下后保留设置，单独放入工作台可重置档位。

## 可变输入/输出仓

两种方块，同样用「LV 原版仓室 + 8 拉杆」合成：

| 方块 | 可调项 | 容量 |
|---|---|---|
| 可变输入仓 | 结构等级 | 8000 L × 2^结构等级 |
| 可变输出仓 | 结构等级 | 8000 L × 2^结构等级 |

- 只有一个结构等级，它同时喂给 `getTierForStructure()` 和 `getCapacity()`
  （`8000L << tier`，tier 0..13），与原版 `mTier` 的作用一致。降低档位不会删除罐里已有的液体，只是超出
  新容量的部分无法继续填入。
- 外壳颜色跟随结构等级；物品形态按物品 NBT 渲染。拆下后保留设置，单独放入工作台可重置档位
  （同时保留覆盖板等物品数据）。
- 直接读 `mTier` 而非 `getTierForStructure()` 的机器不会看到结构等级 —— 本仓的 `mTier` 恒为 LV。
  GT++ 核反应堆等使用 `getTierForStructure()` 的检查则正常生效。
- 2.9.0 中这两种原版仓室已迁移到 MUI2，本模组的 GUI 也基于 MUI2 原生控件实现。
- 物品总线没有做：`CommonMetaTileEntity.mInventory` 是 final 数组，槽数不能在运行时增减；固定容量又无法
  体现"可变档位"，因此只保留液体仓。

## 构建

- **必须 Java 25**（`.java-version`）。
- 编译目标 `GT5U 5.09.54.265`（GTNH 2.9.0）。
- `./gradlew build`，产物 `build/libs/gt-variable-hatch-<版本>.jar`（只发主 jar，不带 `-dev`/`-sources`）。
- **改版本时三处同改**：`gradle.properties` 的 `RELEASE_VERSION`、`src/main/resources/mcmod.info` 的 `version`、
  `@Mod` 注解（`Tags` 自动）。GTNH 模板**不替换** mcmod.info，游戏 Mods 界面显示的就是它，改完必须重编。

## 当前状态

**构建产物**：`build/libs/gt-variable-hatch-1.1.0.jar`，
`Tags.VERSION` 与 `mcmod.info` 的版本均为 `1.1.0`。

已写：构建脚本、主类、四个 MTE 本体（能源仓/动力仓三个可调项；液体仓一个档位）、MUI2 GUI、
NBT/同步流、信息面板、注册、配方、中英 lang、mcmod.info。

**尚未验证**：2.9.0 游戏内启动、MUI2 GUI 布局与同步、机器运行行为。

信息面板里的 tier 名仍是 `GTValues.VOLTAGE_NAMES` 的英文长名（如 "Ultra Low Voltage"），
中文客户端下这一项不本地化。

## 目录

```
src/main/java/com/lelqwq/gtvh/
├── GTVariableEnergyHatch.java              主类（preInit 构造 MTE，init 注册配方）
├── client/                                 物品渲染、tooltip 清理
├── common/api/enums/MetaTileEntityID.java  MTE ID（BASE 15000，避开 GTSWN 的 14600 段）
├── common/gui/                             MUI2 GUI（两个液体仓）
├── common/machine/                         四个方块本体与共享设置
├── common/recipe/                          重置配方
├── loader/{MachineLoader,RecipeLoader}.java
└── mixins/                                 精密组装机、动力仓准入
```

工程细节、GT5U 行号依据与未决项都在 [`DESIGN.md`](DESIGN.md)。
