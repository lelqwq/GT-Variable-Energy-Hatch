# Variable Energy Hatch（可变能源仓）

一个 GTNH 2.8.4 / GT5U 5.09.51 的小型 addon：**一个方块顶掉能源仓的 tier 选择题**。

多方块只装一个能源仓、又想跑更高等级的配方时，原版的办法是拆机器换更高级的仓、或者堆更多仓。
本模组把这件事收进一个方块：结构等级、供电等级、电流三项都在它自己的 GUI 里切。

- **纯 GT5U 扩展**：没有 Mixin，**不需要 UniMixins**，没有自定义网络包，运行时只依赖 GT5U 本身。
- **一个方块**：`Variable Energy Hatch`，继承 `MTEHatchEnergy`，因此任何接受普通能源仓的多方块都认它。

## 使用方法

右键方块打开 GUI，三项即时生效：

| 项 | 默认 | 可选 | 作用 |
|---|---|---|---|
| 结构等级 Structure tier | LV | ULV..UXV(0..13) | 向结构检查申报的 tier。**只对少数机器有效**（显式读 `getTierForStructure()` 的那几台，见下），EBF/LCR 这类机器根本不看，拨了不会有任何变化 |
| 供电等级 Power tier | LV | ULV..UXV(0..13) | 它向机器申报的电压 = `V[供电等级]`，也就是这台机器的功率预算上限 |
| 电流 Amperage | 2A | {2, 4, 16, 64} | 申报的工作电流（**只在机器有 2 个以上能源仓时进入预算**，见下） |

每一项都是 `[−][只读显示][+]`：显示框里是当前档位（如 `1 · LV` / `2 A`），
**数值只能用两侧的箭头改**，没有输入框、也不吃滚轮。Shift + 点击箭头 = 等级一次调 4 级、
电流直接跳到 2A 或 64A。鼠标悬停在框或箭头上会显示该档位的电压与预算。

关键理解：**机器的功率预算 = 供电等级 × 电流**，而 GT 的超频层数由预算决定
（`log4(预算 / 配方 EU/t)`，每层时间 ÷2、EU/t ×4）。所以"选供电等级"等价于"控制配方的工作电压"。

这一行在**只有一个能源仓**时精确成立。装了多个能源仓时 GT 换用「各仓电压的平均值 × 合计工作电流」，
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
2. **单仓时电流档位不生效**。GT 的 `setProcessingLogicPower` 在"恰好一个能源仓且无 exotic 仓"时
   把可用电流硬写成 1A（`MTEMultiBlockBase.java:1021`）。此时预算 = 供电等级 × 1A，与电流档位无关。
   电流要参与预算，机器上得有 ≥2 个能源仓（或另有 exotic 仓）。
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

工作台 3×3，中心一件 **LV 能源仓**，四周 8 格 **原版拉杆**：

```
拉杆 拉杆 拉杆
拉杆 仓   拉杆
拉杆 拉杆 拉杆
```

成本极低且 LV 就能做 —— 属于"早期就替代全部能源仓选择"的强力工具，这是刻意的设计取舍；
若要加门槛，只改中心件为更高 tier 的能源仓即可（形状不用动）。

## 与 GT-Simple-Wireless-Network 的配合

不需要任何跨模组代码。GTSWN 的无线能源覆盖板是往任意 `BaseMetaTileEntity` 调
`increaseStoredEnergyUnits` 灌电，并且按宿主的 `getEUCapacity()` 决定补多少
（`GTswn_Cover_EnergyWireless.java:121-123`），而本仓的容量随供电等级自动变化。
所以把 GTSWN 覆盖板贴在本仓上，配置电压 = 供电等级、电流足够大，就能**无线**喂饱它
（网络照扣 1+损耗）。

## 构建

- **必须 Java 25**（`.java-version`）。
- 编译目标 `GT5U 5.09.51.476`（= GTNH 2.8.4，实机 .482）。
- `./gradlew build`，产物 `build/libs/gtvariablehatch-<版本>.jar`（只发主 jar，不带 `-dev`/`-sources`）。
- **改版本时三处同改**：`gradle.properties` 的 `RELEASE_VERSION`、`src/main/resources/mcmod.info` 的 `version`、
  `@Mod` 注解（`Tags` 自动）。GTNH 模板**不替换** mcmod.info，游戏 Mods 界面显示的就是它，改完必须重编。

## 当前状态

**编译通过**：`./gradlew build` 成功，产物 `build/libs/gtvariablehatch-1.0.1.jar`，
jar 内 `Tags.VERSION` 与 `mcmod.info` 的版本均为 `1.0.1`（三处版本号已对齐）。

已写：构建脚本、主类、MTE 本体（三个可调项 + 全部覆写 + NBT/描述包 + 信息面板 + GUI）、注册、配方、中英 lang、mcmod.info。

**尚未验证**（`DESIGN.md` 第 7 节有完整清单）：其余游戏内实际行为、runClient 开发环境。

其中四条已从 GT5U 源码确认、可移出待验证清单（见 `DESIGN.md` 第 7 节）：物品与创造栏条目、
改档后的客户端同步与落盘、控件显示值恒等于仓存储值（数值框已改为只读）、GUI 布局不再压背包。
另：信息面板里的 tier 名仍是 `GTValues.VOLTAGE_NAMES` 的英文长名（如 "Ultra Low Voltage"），
中文客户端下这一项不本地化。

## 目录

```
src/main/java/com/lelqwq/gtvh/
├── GTVariableEnergyHatch.java              主类（preInit 构造 MTE，init 注册配方）
├── common/api/enums/MetaTileEntityID.java  MTE ID（BASE 15000，避开 GTSWN 的 14600 段）
├── common/machine/VariableEnergyHatch.java 方块本体
└── loader/{MachineLoader,RecipeLoader}.java
```

工程细节、GT5U 行号依据与未决项都在 [`DESIGN.md`](DESIGN.md)。
