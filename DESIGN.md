# DESIGN — 可变能源仓

本文记录**为什么这么做**与**依据在哪**（GT5U 均指 `GT5-Unofficial 5.09.51.476`，行号对应该版本源码）。
使用说明与构建方式见 [`README.md`](README.md)。

## 1. 目标

多方块只装**一个**能源仓时，也能跑任意等级的配方，并在 GUI 里随时调整供电 tier 与电流。

**早期假设需要修正**：原先写的"原版做不到：结构对能源仓的 tier 有区间限制，且电气 tier 就是结构 tier"
只对少数机器成立 —— GT5U 绝大多数多方块的成型判定**不检查**能源仓 tier（见第 3 节"结构检查到底看什么"）。
本模组的实际价值因此落在：

1. **一个物品覆盖全部 tier × 电流档位**（原版要按 tier × 安培备几十个能源仓）；
2. 在少数显式读 `getTierForStructure()` 的机器上（goodgenerator 大型聚变、大型流体提取机）
   把结构 tier 与电气 tier 解耦；
3. `getInputTier()` 随供电等级上报，WAILA/TOP 的机器读数与实际电压一致
   （原版仓不存在这个问题，因为它们本来就不解耦）。

一句话要求：**让"控制配方的工作电压来改变执行速度"这件事有一个方块可做。**

## 2. 冻结规格

| 项 | 值 | 决策来源 |
|---|---|---|
| 方块 | `VariableEnergyHatch extends MTEHatchEnergy` | 恒进 `mEnergyHatches`，不被归入 exotic |
| 可调项 | 结构等级 / 供电等级 / 电流 | 三项都即时生效 |
| tier 范围 | 0..13（ULV..UXV） | 与原版能源仓注册范围一致（`LoaderMetaTileEntities.java:8947-8976`） |
| 上限 | `V[13] = 536,870,912 EU/t` | `GTValues.java:99-102`；14 是 `Integer.MAX_VALUE-7` 的防越界假 tier、15 是错误 tier，都不进选择器 |
| 电流档位 | {1, 2, 4, 16, 64}，默认 1 | 对应覆盖层 2A/4A/16A/64A 与原版真实仓型 |
| 默认值 | 结构 LV / 供电 LV / 1A | |
| GUI | 右键打开 | ModularUI 1.x，与 GT 自己的 hatch 同一套（非新增依赖） |
| 存取 | NBT 三键 + GT 原生描述包 | 无自定义网络包 |
| 依赖 | 仅 GT5U（编译期另需 AE2/ModularUI/ModularUI2/GTNHLib/StructureLib 可见性） | 无 Mixin、无 UniMixins |

## 3. 数值来源（逐个方法）

| 方法 | 覆写为 | 原版依据 |
|---|---|---|
| `maxEUInput()` | `V[energyTier]` | `MTEHatchEnergy.java:70-72` 原本是 `V[mTier]` |
| `maxEUStore()` | `512 + V[energyTier] × 4 × amperage` | 照抄 `MTEHatchEnergyMulti.java:124-126` |
| `maxAmperesIn()` | `amperage + (amperage >> 2)` | 照抄 `MTEHatchEnergyMulti.java:129-131`；这 1/4 是**进线接受能力**的损耗余量，不进配方预算（见下） |
| `maxWorkingAmperesIn()` | `amperage` | 照抄 `MTEHatchEnergyMulti.java:133-136` |
| `getInputTier()` | `energyTier` | `MTETieredMachineBlock.java:75-78` 默认返回 `mTier`；GT 的 `getInputVoltageTier()`、WAILA 的 energyTier 标签、`BaseMetaTileEntity.dischargeItem()` 都读它，不覆写则永远按 LV 上报 |
| `getTierForStructure()` | `structureTier` | `MTEHatch.java:151-153` 默认返回 `mTier`；该 hook 的 javadoc 明说用于"结构检查用自定义 tier，`mTier` 保留作他用" |
| `getTexturesActive/Inactive()` | 按 `amperage` 选 `OVERLAYS_ENERGY_IN_MULTI_{2A,4A,16A,64A}`，索引 `energyTier + 1` | `MTEHatchEnergy.java:39-47` 硬编码 2A；选法照 `MTEHatchEnergyMulti.java:63-91` |

**buffer 为什么必须含电流**：多方块每 tick 只调用一次 `drainEnergyInput`，而它取
`min(mEUVar, maxEUStore)`（`MTEMultiBlockBase.java:1511-1530`），`getStoredEU()` 又被
`Math.min(mEUVar, capacity)` 钳制（`BaseMetaTileEntity.java:1134`）。原版 `MTEHatchEnergy` 的
`512 + V×8` 不含安培数，所以电流档位一旦超过约 8~12，预算再大也拿不到 —— 这正是
`MTEHatchEnergyMulti` 把 buffer 写成 `512 + V×4×A` 的原因。

**预算到底怎么算（与「V × A」的出入）**：`setProcessingLogicPower`（`MTEMultiBlockBase.java:1020-1025`）
把 `availableVoltage = getAverageInputVoltage()`（各能源仓 `getInputVoltage()` 的**平均**）与
`availableAmperage = getMaxInputAmps()` 交给 `ProcessingLogic`。而 `getMaxInputAmps()`
（`MTEMultiBlockBase.java:1474-1476`）走 `ExoticEnergyInputHelper.getMaxWorkingInputAmpsMulti`，
即各仓 `maxWorkingAmperesIn()` —— **电流档位本身**。于是：

- 单仓：预算 = `V[energyTier]` × 1A（`useSingleAmp` 硬写，与电流档位无关）；
- 多仓：预算 = 各仓电压**平均值** × ΣA（各仓同 tier 同档位时就是 `V × nA`）。

混 tier 时这个值会高于任何线缆实际能供的电，`ProcessingLogic` 据此选中一个永远喂不饱的配方，
机器从此卡住不动（README 限制 4）。本模组不修正该算法 —— 修正它需要 Mixin，与零依赖取向冲突。

**别搞混的两个近似同名方法**：`ExoticEnergyInputHelper` 里另有
`getMaxInputAmpsMulti`（走 `getBaseMetaTileEntity().getInputAmperage()` = `maxAmperesIn()` = A + A/4）。
它**不进**配方预算，只被 `MTEMultiBlockBase.getMaxInputPower()`（`:1485-1492`）等路径使用 ——
所以「16A 档在预算里按 20A 算」是错的，16A 就是 16A。

**结构检查到底看什么（本模组最大的一个认知修正）**：成型判定走 StructureLib 的 `IStructureElement.check()`，
而 GT 的仓室元素（`HatchElementBuilder.build()`，`HatchElementBuilder.java:429-438`）只做一件事：
把方块交给 `mAdder`，即 `MTEMultiBlockBase.addEnergyInputToMachineList`（`:2006-2009`），
它只判断 `instanceof MTEHatchEnergy` —— **成型路径上没有任何 tier 判断**。
`GTStructureUtility.filterByMTETier`（`:751-762`，那个"区间检查"）挂在 `hatchItemFilter` 上，
只影响 `couldBeValid()`（结构提示粒子、自动放置）与 `getBlocksToPlace()`，不参与成型。
所以 `getTierForStructure()` 的调用点，就是本项功能的**全部**适用范围：

| 调用点 | 行为 | 对本仓 |
|---|---|---|
| `goodgenerator/.../MTELargeFusionComputer.java:483/487` | 仓 tier < 要求 → 结构不成立 | **生效**（唯一要求"≥ 某 tier"的机器） |
| `MTELargeFluidExtractor.java:177` | 玻璃 tier < UEV 时，仓 tier > 玻璃 tier → 结构不成立 | **生效**（上界约束） |
| `GTStructureUtility.java:756`（经 item filter） | 只影响提示 / 自动放置 | 不参与成型 |
| gtpp 化工厂 / 精炼厂 / 核反应堆 | 同上；`:179-196` 查的是 dynamo 与输入/输出仓，不是能源仓 | 不参与成型 |
| `MTEFusionComputer.java:233` | 不用 `getTierForStructure()`，**直接比 `mTier`** | **无条件被拒**（本仓 `mTier` 恒为 LV） |
| `MTEWormholeGenerator.java:275` | 只把 `MTEHatchEnergyMulti` 认作能源仓 | 根本装不进去 |

结论：结构等级对 EBF/LCR 这类机器是空操作；对 GT 自家聚变族则完全无能为力 —— 那三台读 `mTier`，
而 `mTier` 是 final 字段，正是本模组刻意不改的东西。这是"不反射改 final 字段"取舍的代价。

**为什么不用反射改 `mTier`**：`mTier` 是 final 字段，上游唯一先例是
`tectech/util/TTUtility.java:50-68` 的反射改写，且那段代码自带
`// TODO why is it using reflection to change a final field from GREGTECH ?`。
本模组改为覆写影响行为的读者（`maxEUInput()`、`getTierForStructure()`、`getInputTier()`），不碰 final 字段。
`getOutputTier()` 与 `getTileEntityBaseType()` 仍读到 `mTier`（恒为 LV），但本仓没有输出侧
（`isEnetOutput()` 为 false）、扳手等级也无实际影响，故不覆写 —— 覆写它们只是让注释更好看，
不改变任何行为。

**外壳贴图**：装入多方块时 `addEnergyInputToMachineList` 会调 `hatch.updateTexture(aBaseCasingIndex)`
（`MTEMultiBlockBase.java:2006-2009`），把外壳换成**机器**的外壳（`MTEHatch.java:59-80` 走
texturePage 分支）。所以成型后 tier 的可视线索是**覆盖层配色**，不是外壳 —— 这是原版行为。

## 4. ID 与注册

- 全局 ID = `BASE(15000) + relativeId`；BASE 刻意避开 GTSWN 的 14600 段。
- 数组上界：`GregTechAPI.METATILEENTITIES = new IMetaTileEntity[MAXIMUM_METATILE_IDS]`，
  `MAXIMUM_METATILE_IDS = Short.MAX_VALUE - 1 = 32766`（`GregTechAPI.java:81/95`）。
- **撞号不是静默覆盖**：`CommonMetaTileEntity` 构造器（`:79-94`）发现 ID 已占用会抛
  `IllegalArgumentException("MetaTileEntity id X is already occupied! ...")` → 启动崩溃 + 明确提示。
  查空号用 JVM 参数 `-Dgt.debug=true`。
- **构造时机**：`CommonMetaTileEntity` 构造器要求 `sPreloadStarted && !sPostloadStarted`
  （`GTProxy.java:786` 置位 preload 开始）。本模组声明 `required-after:gregtech`，在 preInit 构造即可。

## 5. 已知限制（都写进 tooltip/README，不在代码里额外补偿）

1. 往下切供电等级 → 进线电压高于申报电压即爆炸（`BaseMetaTileEntity.java:1758-1762`）。
2. 单能源仓且无 exotic 仓时，GT 把可用电流硬写成 1A（`useSingleAmp`，`MTEMultiBlockBase.java:1021`），
   电流档位不参与预算。这是原版设计，本模组不绕过（绕过需要 Mixin，与"零依赖"取向冲突）。
3. 结构等级只在少数机器上生效（见第 3 节"结构检查到底看什么"）：goodgenerator 大型聚变要求"仓 tier ≥ 某值"、
   大型流体提取机要求"仓 tier ≤ 玻璃 tier"。设错 → 结构检查失败、回到未成型（方块保留），调回去即恢复。
   其余机器（含 EBF/LCR）拨它没有任何效果 —— 那不是 bug。
4. 多个可变仓混用不同供电等级 → 预算按各仓电压的**平均值** × ΣA 算（不是各仓之和），
   可能远高于线缆实际能供的电；机器选中跑不动的配方后**卡住不动且无任何诊断**。
   多个可变仓应设成同一供电等级（见第 3 节"预算到底怎么算"）。
5. GT 自家聚变 MK1/2/3 与虫洞发生器**不接受本仓**：前者直接比较 `mTier`（`MTEFusionComputer.java:233`），
   本仓 `mTier` 恒为 LV（final，刻意不改）→ 无条件被拒，调结构等级无效；后者只把
   `MTEHatchEnergyMulti` 认作能源仓（`MTEWormholeGenerator.java:275`）。这两处请用原版/tectech 仓。

## 6. 刻意不做

- 不做 Mixin（因此不需要 UniMixins）；不改 `mTier`；不做动力仓/输出侧；不做仓自取电（保持"纯 GT 扩展"）；
  单方块机器的超频不受本模组影响。

## 7. 待办与未验证项

**编译**：`./gradlew build` 已通过（2026-10-16）。踩到的两个坑记在这里：
- 只复制 `gradle/wrapper/` 不够，**必须同时有 `gradle/gradle-daemon-jvm.properties`**（`toolchainVersion=25`），
  否则 Gradle 用 Java 21 起守护进程，GTNH 插件会直接报 "Dependency requires at least JVM runtime version 25"。
- GTNH 启用 Spotless 格式检查：新文件先跑 `./gradlew spotlessApply` 再 build，否则格式违规会让 build 失败。

**编译已验证的 API 假设**
- [x] `GTValues.VOLTAGE_NAMES` 可访问（`getInfoData`/`tierName` 用到）。
- [x] `NumericWidget.setGetter(() -> (double) x)` 类型匹配。
- [x] `getDescriptionData()` / `onDescriptionPacket()` 覆写签名正确；`mDescriptionArray`/`mTextures` 在子类可访问。
- [x] `MetaTileEntityIDs.ENERGY_HATCH_LV.ID` 与 `GameRegistry.addShapedRecipe` 用法可用。
- [x] `NumericWidget.setValidator(DoubleUnaryOperator)` 存在且返回 `NumericWidget`（可继续串链）。
- [x] `IGregTechTileEntity.markDirty()` 可解析（GT 自己的 `MTEBasicTank.java:206` 在同样的静态类型上调用）。
- [x] `getInputTier()` 可覆写（`MTETieredMachineBlock.java:75-78` 的实现不是 final）。

**仍待验证（编译管不到）**
- [x] `issueTileUpdate()` **不足以**让 NBT 落盘：它只置 `mNeedsTileUpdate` → `worldObj.markBlockForUpdate`
      （`BaseMetaTileEntity.java:367-370`），是纯客户端同步路径。已在 `onSettingsChanged()` 补 `markDirty()`。
- [x] 描述包链路完整、改档能同步到客户端：`getDescriptionData()` → `getDescriptionPacket()`
      → 客户端 `onDataPacket()` → `onDescriptionPacket()`（`CommonBaseMetaTileEntity.java:254/269/299`）。
- [ ] `usesMixins = false` 下 `runClient` 开发环境是否需要 `forceEnableMixins = true`（生产环境不需要）。

**功能相关**
- [x] 物品图标与 GT 创造栏条目：`BlockMachines.getSubBlocks`（`:671-677`）枚举全部非空 MTE，
      物品会**自动**出现在 GT 主创造栏，无需显式注册；图标取原型实例（`mTier = BASE_TIER = 1`）的外壳，
      即 LV 机壳。
- [ ] 电流档位改为真正的循环/下拉控件（当前是数字框 + 吸附）。可考虑 ModularUI 的
      `CycleButtonWidget`（API 需确认）。**吸附本身已修**：控件改用 `setValidator` 吸附，
      `validateAndSetValue` 会先跑 validator 再写回文本框，所以显示值与 `getInfoData` 不再互相矛盾；
      但滚轮步长 1/4/16 与档位 {1,2,4,16,64} 仍对不齐（多数滚动看着像没反应），归入本待办。
- [ ] 信息面板 / WAILA 的 tier 名本地化：现取 `GTValues.VOLTAGE_NAMES` 的英文长名
      （如 "Ultra Low Voltage"），中文客户端下这一项仍是英文，格式也与其它 GT 机器不一致。
      应改用 GT 的本地化 helper（`GTUtility.getColoredTierNameFromTier` / `GTValues.getLocalizedLongVoltageName`）。
- [ ] tooltip 中文本地化：当前 `DESCRIPTION` 是构造期英文字面量（避免 lang 加载时机问题）；
      若要中文，改为 lang key + 在 preInit 用 `GTLanguageManager.addStringLocalization` 注册，并验证 NEI 显示时机。

**实测清单（按顺序）**
1. 单仓 + 供电等级 MV / 电流 2：与原版 MV 能源仓逐项比对（`maxEUInput`/`maxEUStore`/`maxAmperesIn`/预算）。
2. 单仓切 HV/EV：确认机器能选中并跑更高 EU/t 的配方，超频层数符合 `log4(预算/配方EU/t)`。
3. 单仓切高 tier 但不配高 tier 供电 → 应 `POWER_LOSS`（证明"供电必须配套"）。
4. 两个可变仓、**设成同一供电等级**：电流档位生效，预算 = `V × ΣA`（两仓同为 A 档即 `V × 2A`）。
   再把两仓故意设成不同供电等级，确认出现「配方选中但卡住」的失真现象（限制 4）。
5. 结构（一次验完第 3 节的结论）：EBF —— 结构等级拨到任何值都应通过（EBF 不看仓 tier）；
   goodgenerator 大型聚变 —— 低于它要求的 tier 应被拒，调到要求值应通过；GT 聚变 MK1 —— 任何值都应被拒。
6. 结构重检：在大型流体提取机里把结构等级调到高于玻璃 tier → 结构应失效；调回 → 恢复
   （依赖 GT 自身的周期结构重检：`onPostTick` 里 `mUpdate` 每 50 tick 触发一次 `checkStructure`，
   `MTEMultiBlockBase.java:589-593`）。
7. 存档重启后三个值保持；客户端贴图/信息面板与所选值一致。
8. GTSWN 覆盖板贴在本仓上，配 V/A ≥ 预算 → 无线供电成立，网络按 (1+损耗) 扣账。
