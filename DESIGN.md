# DESIGN — 可变能源仓

本文记录**为什么这么做**与**依据在哪**（GT5U 均指 `GT5-Unofficial 5.09.51.476`，行号对应该版本源码）。
使用说明与构建方式见 [`README.md`](README.md)。

## 1. 目标

多方块只装**一个**能源仓时，也能跑任意等级的配方。原版做不到：结构对能源仓的 tier 有区间限制，
且电气 tier 就是结构 tier。本模组把两者解耦。

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
| `maxAmperesIn()` | `amperage + (amperage >> 2)` | 照抄 `MTEHatchEnergyMulti.java:129-131` |
| `maxWorkingAmperesIn()` | `amperage` | 照抄 `MTEHatchEnergyMulti.java:133-136` |
| `getTierForStructure()` | `structureTier` | `MTEHatch.java:151-153` 默认返回 `mTier`；该 hook 的 javadoc 明说用于"结构检查用自定义 tier，`mTier` 保留作他用" |
| `getTexturesActive/Inactive()` | 按 `amperage` 选 `OVERLAYS_ENERGY_IN_MULTI_{2A,4A,16A,64A}`，索引 `energyTier + 1` | `MTEHatchEnergy.java:39-47` 硬编码 2A；选法照 `MTEHatchEnergyMulti.java:63-91` |

**buffer 为什么必须含电流**：多方块每 tick 只调用一次 `drainEnergyInput`，而它取
`min(mEUVar, maxEUStore)`（`MTEMultiBlockBase.java:1511-1530`），`getStoredEU()` 又被
`Math.min(mEUVar, capacity)` 钳制（`BaseMetaTileEntity.java:1134`）。原版 `MTEHatchEnergy` 的
`512 + V×8` 不含安培数，所以电流档位一旦超过约 8~12，预算再大也拿不到 —— 这正是
`MTEHatchEnergyMulti` 把 buffer 写成 `512 + V×4×A` 的原因。

**为什么不用反射改 `mTier`**：`mTier` 是 final 字段，上游唯一先例是
`tectech/util/TTUtility.java:50-68` 的反射改写，且那段代码自带
`// TODO why is it using reflection to change a final field from GREGTECH ?`。
本模组改为覆写所有读 `mTier` 的方法，不碰 final 字段。

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
3. 结构等级设错 → 机器解散/停机（方块保留）；"要求 ≥ 某 tier"的机器需相应调高结构等级。

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

**仍待验证（编译管不到）**
- [ ] `IGregTechTileEntity.issueTileUpdate()` 是否足以让 NBT 落盘；若不足，在 `onSettingsChanged()` 补 `markDirty()`。
- [ ] `usesMixins = false` 下 `runClient` 开发环境是否需要 `forceEnableMixins = true`（生产环境不需要）。

**功能相关**
- [ ] 物品图标与 GT 创造栏条目：`ItemMachines` 的图标取法需确认；若创造栏看不到，需显式加入 GT 创造栏。
- [ ] 电流档位改为真正的循环/下拉控件（当前是数字框 + 吸附到最近档位）。可考虑 ModularUI 的
      `CycleButtonWidget`（API 需确认）。
- [ ] tooltip 中文本地化：当前 `DESCRIPTION` 是构造期英文字面量（避免 lang 加载时机问题）；
      若要中文，改为 lang key + 在 preInit 用 `GTLanguageManager.addStringLocalization` 注册，并验证 NEI 显示时机。

**实测清单（按顺序）**
1. 单仓 + 供电等级 MV / 电流 2：与原版 MV 能源仓逐项比对（`maxEUInput`/`maxEUStore`/`maxAmperesIn`/预算）。
2. 单仓切 HV/EV：确认机器能选中并跑更高 EU/t 的配方，超频层数符合 `log4(预算/配方EU/t)`。
3. 单仓切高 tier 但不配高 tier 供电 → 应 `POWER_LOSS`（证明"供电必须配套"）。
4. 两个可变仓：电流档位生效，预算 = `V × ΣA`。
5. 结构：塞进 EBF（结构等级 LV 应通过）；塞进大型聚变（应被拒）→ 把结构等级调到它要求的值（应通过）。
6. 切档后结构重检：调低结构等级 → 机器解散；调回 → 恢复（依赖 GT 自身的周期结构重检）。
7. 存档重启后三个值保持；客户端贴图/信息面板与所选值一致。
8. GTSWN 覆盖板贴在本仓上，配 V/A ≥ 预算 → 无线供电成立，网络按 (1+损耗) 扣账。
