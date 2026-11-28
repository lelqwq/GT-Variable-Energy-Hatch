# DESIGN — 可变能源仓

> 更新：1.0.4 面向 GTNH 2.9.0 / GT5U 5.09.54.265，客户端同步改用 `writeToStream` / `readFromStream`；NBT 存档键保持不变。MT-3662 兼容补丁使用 UniMixins。下文保留 2.8.4 原始设计，涉及旧描述包和源码行号的内容为历史记录。

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
| 电流档位 | {2, 4, 16, 64}，默认 2 | 对应覆盖层 2A/4A/16A/64A 与原版真实仓型 |
| 默认值 | 结构 LV / 供电 LV / 2A | |
| GUI | 右键打开；窗口 **176×211**；三项只读显示 + 左右箭头调节 | ModularUI 1.x，与 GT 自己的 hatch 同一套（非新增依赖）；高度由布局推出，见下方"GUI 为什么要加高" |
| 存取 | 方块存档、掉落物 NBT 三键 + GT 原生描述包 | 拆除后保留非默认配置；单物品无序合成只清除这三个配置键 |
| 依赖 | GT5U、UniMixins（编译期另需 AE2/ModularUI/ModularUI2/GTNHLib/StructureLib 可见性） | MT-3662 兼容补丁使用 Mixin |

### GUI 为什么要加高（176×166 → 176×211）

GT 默认窗口是 176×166（`IMetaTileEntity.getGUIWidth/getGUIHeight` 的默认值），玩家背包由
`BaseTileEntity.bindPlayerInventoryUI`（`:652-654`）贴在窗口底部：`bindPlayerInventory(player, 7, …)`
→ 落点 `(width/2 - 81, height - 7 - 76)`，即 176 宽时背包占 `y = 83..159`。而"标题 + 控件行"三组
竖排至少要 ~120px，塞进 83px 的剩余空间必然压背包 —— 这正是 0.1.0 用
`y = getGUIHeight()/5 - 7` 起头、每组 `+44` 时出现的症状（后两组落在 y=70/86 与 y=114/130）。

修法：把窗口高度改成**由布局反推**（`CONTENT_BOTTOM + 83`），控件位置全部来自同一组常量，
两边不可能再撞上。背包不用手动摆放 —— GT 按窗口高度自动重定位，`CONTENT_BOTTOM` 取 128 与默认
落点严格相等。背景用的是可拉伸九宫格（`AdaptableUITexture`，`GTUITextures.java:20`），加高不糊贴图；
先例 `MTEHatchInputBusME.getGUIHeight()` 返回 179、`MTEMultiBlockBase` 用 198×192。

### 控件行：`[−][只读数值框][+]`

每行 110px（`18 + 2 + 70 + 2 + 18`）水平居中，其中数值框 70px 恰好也居中，所以标题与框同轴。
数值框是 **`TextWidget` + `setStringSupplier`**（不是 `TextFieldWidget`/`NumericWidget`）：

- **只能靠箭头改**。箭头是 `ButtonWidget` + `GTUITextures.BUTTON_STANDARD` + 大号 `−`/`+` 覆盖层，
  写法与 `MTETeleporter`、`MTEHatchCraftingInputME` 一致。`SyncedWidget` 会自动把点击同步到服务端，
  两侧各跑一次同一段 setter（夹取/吸附只写在 `setStructureTier/setEnergyTier/setAmperage` 一处，
  NBT 读档与箭头共用），所以不存在"控件显示值 ≠ 仓存储值"。
- **显示值跟随仓里的值**，不自己存一份。`TextWidget.setTextSupplier` 的 supplier 只在客户端求值，
  每 tick 在 `onScreenUpdate` 里重读（`ModularWindow.update()`），值由 GT 原生描述包同步到客户端。
  顺带解掉了 0.1.0 的待办：电流档位不再是"数字框 + 吸附 + 对不齐的滚轮步长"，箭头直接沿
  `AMPERAGE_STEPS` 走（2→4→16→64，两端夹住）。
- 框里显示 `1 · LV` / `2 · MV` / `2 A`（等级用 `GTValues.VN` 短名，不带颜色码，白字在灰底上可读）；
  电压与预算（`V[tier] EU/t`、`V × A`）放在 tooltip，避免 `536,870,912` 这类长数字撑破 70px。
- Shift + 点击：等级一次 ±4，电流直接跳到 2A / 64A。箭头按到边界后再点只是重复写同一个值。


## 3. 数值来源（逐个方法）

| 方法 | 覆写为 | 原版依据 |
|---|---|---|
| `maxEUInput()` | `V[energyTier]` | `MTEHatchEnergy.java:70-72` 原本是 `V[mTier]` |
| `maxEUStore()` | `512 + V[energyTier] × 4 × amperage` | 照抄 `MTEHatchEnergyMulti.java:124-126` |
| `maxAmperesIn()` | `amperage + (amperage >> 2)` | 照抄 `MTEHatchEnergyMulti.java:129-131`；这 1/4 是**进线接受能力**的损耗余量，不进配方预算（见下） |
| `maxWorkingAmperesIn()` | `amperage` | 照抄 `MTEHatchEnergyMulti.java:133-136` |
| `getInputTier()` | `energyTier` | `MTETieredMachineBlock.java:75-78` 默认返回 `mTier`；GT 的 `getInputVoltageTier()`、WAILA 的 energyTier 标签、`BaseMetaTileEntity.dischargeItem()` 都读它，不覆写则永远按 LV 上报 |
| `getTierForStructure()` | `structureTier` | `MTEHatch.java:151-153` 默认返回 `mTier`；该 hook 的 javadoc 明说用于"结构检查用自定义 tier，`mTier` 保留作他用" |
| `getTexture()` | 独立放置时将固定 LV 默认外壳替换为 `energyTier` 对应外壳；控制器通过 `updateTexture()` 指定的多方块外壳保持不变 | 避免供电等级变化时只有输入覆盖层变化，也避免破坏多方块机壳贴图 |
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
| `MTEFusionComputer.java:233` | 不用 `getTierForStructure()`，**直接比 `mTier`** | 现在 `mTier = 供电等级`；供电等级达到要求即可进入，结构等级不参与 |
| `MTEWormholeGenerator.java:275` | 只把 `MTEHatchEnergyMulti` 认作能源仓 | 可变能源仓仍然装不进去 |

结论：结构等级对 EBF/LCR 这类机器是空操作；GT 自家聚变族通过 `mTier` 判定，而 `mTier` 现在镜像供电等级，
所以聚变看的是**供电等级**，不是结构等级。

**为什么现在改 `mTier`**：GT 里有多处直接读 `mTier`（聚变、PSS、化工厂、等离子锻炉、电动聚爆等），
只覆写 `maxEUInput()` / `getTierForStructure()` / `getInputTier()` 无法让它们看到配置值。
本模组新增 `MixinMTETieredMachineBlock`，把 `MTETieredMachineBlock.mTier` 变成可写，并让所有可变仓在构造、
NBT 载入、网络同步和 GUI 改档时写入**实际工作等级**：

- 能源仓 / 动力仓：`mTier = 供电 / 输出等级`（电压等级）；
- 液体仓 / 激光仓：`mTier = 结构等级`。

结构等级仍通过 `getTierForStructure()` 单独暴露，因此玻璃、goodgenerator 聚变等结构约束不受影响。
所有 Mixin 只写本模组自己的仓，不影响其他 MTE 的 `mTier`。

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
5. 虫洞发生器只把 `MTEHatchEnergyMulti` 认作能源仓（`MTEWormholeGenerator.java:275`），可变能源仓
   仍然装不进去。GT 聚变 MK1/2/3 直接比较 `mTier`，而 `mTier` 现在跟随供电等级，所以供电等级达到
   聚变要求即可进入；降供电等级会重新触发结构检查并可能退回未成型。

## 6. 刻意不做

- 不改结构等级与供电等级互相独立的语义；`mTier` 仅作为运行等级镜像（见 §3）；不做仓自取电；
  MT-3662 通过 Mixin 兼容；单方块机器的超频不受本模组影响。

## 7. 待办与未验证项

**编译**：`./gradlew build` 已通过（2026-10-16）。踩到的两个坑记在这里：
- 只复制 `gradle/wrapper/` 不够，**必须同时有 `gradle/gradle-daemon-jvm.properties`**（`toolchainVersion=25`），
  否则 Gradle 用 Java 21 起守护进程，GTNH 插件会直接报 "Dependency requires at least JVM runtime version 25"。
- GTNH 启用 Spotless 格式检查：新文件先跑 `./gradlew spotlessApply` 再 build，否则格式违规会让 build 失败。

**编译已验证的 API 假设**
- [x] `GTValues.VOLTAGE_NAMES` / `GTValues.VN` 可访问（`getInfoData` 用长名，GUI 用短名）。
- [x] `getDescriptionData()` / `onDescriptionPacket()` 覆写签名正确；`mDescriptionArray`/`mTextures` 在子类可访问。
- [x] `MetaTileEntityIDs.ENERGY_HATCH_LV.ID` 与 `GameRegistry.addShapedRecipe` 用法可用。
- [x] `IGregTechTileEntity.markDirty()` 可解析（GT 自己的 `MTEBasicTank.java:206` 在同样的静态类型上调用）。
- [x] `getInputTier()` 可覆写（`MTETieredMachineBlock.java:75-78` 的实现不是 final）。
- [x] `ButtonWidget.setOnClick(BiConsumer<ClickData, Widget>)` + `setBackground(BUTTON_STANDARD, OVERLAY_BUTTON_{PLUS,MINUS}_LARGE)`
      可用（照 GT 自己的 `MTETeleporter.java:517-527`）；`ClickData.shift` 会随点击包一起发到服务端。
- [x] `TextWidget.setStringSupplier(Supplier<String>)` 可链式返回 `TextWidget`；`dynamicTooltip(Supplier<List<String>>)`
      与 `setUpdateTooltipEveryTick(true)` 可用（后者必须有：tooltip 结果默认缓存，不设会停在第一次的值上）。
- [x] **别把继承来的 setter 挂在链尾**：`setSize`/`setPos`/`setBackground` 声明返回 `Widget`，
      `setTextAlignment`→`BaseTextFieldWidget`、`setTextColor`→`TextFieldWidget`，串起来会把静态类型降级，
      要么逐句调用，要么把链尾喂给 `builder.widget(...)`（参数类型是 `Widget`）。

**仍待验证（编译管不到）**
- [x] `issueTileUpdate()` **不足以**让 NBT 落盘：它只置 `mNeedsTileUpdate` → `worldObj.markBlockForUpdate`
      （`BaseMetaTileEntity.java:367-370`），是纯客户端同步路径。已在 `onSettingsChanged()` 补 `markDirty()`。
- [x] 描述包链路完整、改档能同步到客户端：`getDescriptionData()` → `getDescriptionPacket()`
      → 客户端 `onDataPacket()` → `onDescriptionPacket()`（`CommonBaseMetaTileEntity.java:254/269/299`）。
- [ ] `usesMixins = false` 下 `runClient` 开发环境是否需要 `forceEnableMixins = true`（生产环境不需要）。

**功能相关**
- [x] 物品图标与 GT 创造栏条目：`BlockMachines.getSubBlocks`（`:671-677`）枚举全部非空 MTE，
      物品会**自动**出现在 GT 主创造栏，无需显式注册；自定义物品渲染器从物品 NBT 读取供电等级和电流，
      使背包、手持与掉落物的外壳和输入覆盖层对应实际配置。
- [x] 电流档位改为真正的循环控件：已改成 `[−][只读显示][+]`，箭头沿 `AMPERAGE_STEPS` 走
      （2→4→16→64），Shift+点击直接到两端。顺带解掉了"数字框 + 吸附 + 滚轮步长对不齐"那一串问题：
      没有输入框就没有显示值与存储值不一致的可能，也没有滚轮。可继续考虑 `CycleButtonWidget`
      （一次点击循环全部档位，省掉 4 次点击），但当前形态与 GT 传送机手感一致，暂不改。
- [ ] 信息面板 / WAILA 的 tier 名本地化：现取 `GTValues.VOLTAGE_NAMES` 的英文长名
      （如 "Ultra Low Voltage"），中文客户端下这一项仍是英文，格式也与其它 GT 机器不一致。
      应改用 GT 的本地化 helper（`GTUtility.getColoredTierNameFromTier` / `GTValues.getLocalizedLongVoltageName`）。
- [x] 物品 tooltip 动态读取掉落物 NBT，显示三项实际配置；说明文字和配置标签通过 lang key 本地化。
      GT 自动追加的固定默认「电压输入 / 容量」由客户端 tooltip 事件精确移除，避免与实际配置冲突。

**实测清单（按顺序）**
1. 单仓 + 供电等级 MV / 电流 2：与原版 MV 能源仓逐项比对（`maxEUInput`/`maxEUStore`/`maxAmperesIn`/预算）。
2. 单仓切 HV/EV：确认机器能选中并跑更高 EU/t 的配方，超频层数符合 `log4(预算/配方EU/t)`。
3. 单仓切高 tier 但不配高 tier 供电 → 应 `POWER_LOSS`（证明"供电必须配套"）。
4. 两个可变仓、**设成同一供电等级**：电流档位生效，预算 = `V × ΣA`（两仓同为 A 档即 `V × 2A`）。
   再把两仓故意设成不同供电等级，确认出现「配方选中但卡住」的失真现象（限制 4）。
5. 结构（一次验完第 3 节的结论）：EBF —— 结构等级拨到任何值都应通过（EBF 不看仓 tier）；
   goodgenerator 大型聚变 —— 结构等级低于它要求的 tier 应被拒，调到要求值应通过；
   GT 聚变 MK1 —— 供电等级低于要求应被拒、调到要求值应通过（mTier 跟随供电等级，结构等级不参与）。
6. 结构重检：在大型流体提取机里把结构等级调到高于玻璃 tier → 结构应失效；调回 → 恢复
   （依赖 GT 自身的周期结构重检：`onPostTick` 里 `mUpdate` 每 50 tick 触发一次 `checkStructure`，
   `MTEMultiBlockBase.java:589-593`）。
7. 存档重启后三个值保持；客户端贴图/信息面板与所选值一致。
8. 设置非默认参数后拆除并重新放置，三个值保持；默认参数的掉落物不带配置 NBT，可与新物品堆叠。
9. 把带配置及覆盖板的仓单独放进任意合成格，输出只清除三个配置键，覆盖板等其它 NBT 保留；
   放置后恢复结构 LV / 供电 LV / 2A。
10. GTSWN 覆盖板贴在本仓上，配 V/A ≥ 预算 → 无线供电成立，网络按 (1+损耗) 扣账。
11. GUI 布局：三组「标题 + 数字框」完整落在窗口上半部，**不压玩家背包**，背包 4 行全部可见可点
   （窗口 176×211，数值推导见第 2 节，仅经源码推算、未实机确认）。

## 8. 可变液体仓（1.0.x）

2.9.0 的两个基类已经把 GUI 迁到 MUI2，因此这两个方块也直接实现 MUI2，不复用能源仓的 MUI1 控件。

| 方块 | 基类 | 模板 ID |
|---|---|---|
| 可变输入仓 | `MTEHatchInput` | `INPUT_HATCH_LV` |
| 可变输出仓 | `MTEHatchOutput` | `OUTPUT_HATCH_LV` |

**一个档位。** `MTEHatchInput/Output.getCapacity()` 是 `8000 * (1 << mTier)`；本模组把同一个可变结构等级
同时喂给 `getTierForStructure()` 和 `getCapacity()`，与原版 `mTier` 的职责一致。容量减小时不裁剪
`mFluid`，只是 `fill()` 因为剩余空间为负而返回 0。

**MUI2 细节（5.09.54.265 源码）**

- 两个基类都覆写 `useMui2() == true`，GUI 分别在
  `gregtech/common/gui/modularui/hatch/MTEHatch{Input,Output}Gui`。
- MUI2 没有"窗口高 + 坐标摆控件"那一套：`MTETieredMachineBlockBaseGui` 用 `Flow` 排版，内容区在
  `createContentSection()`，底部两角在 `createBottomLeft/RightCornerFlow()`，面板高度由
  `getBasePanelHeight()` 决定，玩家背包按 `height - 83` 贴在底部。本模组把档位行追加到底部左角 Flow，
  并加高面板 24px。
- 档位读写用 `IntSyncValue(getter, setter).allowC2S()` 注册到 `PanelSyncManager`；setter 在服务端执行，
  调 `issueTileUpdate()` + `markDirty()`，并调 `GregTechAPI.causeMachineUpdate(...)` 让结构重检
  （容量也随该档位变化，需要同步给客户端）。客户端读到的显示值来自 sync cache，罐容量等实际值
  仍由 `writeToStream/readFromStream` 同步。
- 材质：`MTEHatch.getTexture()` 用 `MACHINE_CASINGS[mTier]` 做底；沿用能源仓的
  `replaceDefaultCasing()`，把 LV 外壳替换为结构等级的颜色。物品渲染继续通过
  `VariableHatchSettings.renderItemWithConfiguration` 的 ThreadLocal 读物品 NBT。

液体仓的 `mTier` 也同步为结构等级（见 §3 的统一处理），所以化工厂等直接读 `mTier` 的机器能看到正确等级。
液体仓没有对应 MT-3662 式的 Mixin 需求（认仓入口只做 `instanceof`，见
`MTEMultiBlockBase.addInputHatchToMachineList` 等）。

**为什么没有可变总线。** `CommonMetaTileEntity.mInventory` 是 `final ItemStack[]`，GUI 槽组、物品栏 handler
与机器端遍历都围绕这个数组建立，运行期改不了长度；2.9.0 的 `MTEHatch.getSlots(tier)` 又是
`(tier + 1)^2`（LV 4、MV 9、HV 16 …… UXV 196），固定容量无法体现档位、动态容量又需要大范围覆写
输入/输出逻辑，因此只保留液体仓。

## 9. 可变激光源仓 / 激光靶仓

2.9.0 的激光仓是 Tectech 的 `MTEHatchDynamoTunnel`（Laser Source）和
`MTEHatchEnergyTunnel`（Laser Target），随 GT5U 5.09.54.265 一起提供。本模组直接继承它们：

| 方块 | 基类 | ID |
|---|---|---|
| 可变激光源仓 | `MTEHatchDynamoTunnel` | 15006 |
| 可变激光靶仓 | `MTEHatchEnergyTunnel` | 15007 |

**档位。** tier 限制在 IV–UXV。原版每个 tier 可用的额定电流是一张固定矩阵（IV 只有 256A；
LuV 256/1024；ZPM 再加 4096；…… UXV 到 1048576）。GUI 的额定电流按钮只遍历当前 tier 的合法值，
切 tier 时自动夹到该 tier 最大值。工作电流沿用原版文本框（`TextFieldWidget.numbersInt(0, 额定)`，
滚轮 1/64/4/16），允许 0–额定之间的任意整数，包括 300A。

**隐藏 mTier 与可变字段 Mixin。** 构造时基类 tier 固定为 IV，`maxAmperes` 固定为 1 048 576（全局上限）；
所有对外方法（`getTierForStructure`、`getInputTier`/`getOutputTier`、`maxEUInput`/`maxEUOutput`、
`maxEUStore`、`getMinimumStoredEU`、材质/描述/名称）都走配置对象。但以下代码直接读 `mTier` 字段：

- `MTEIndustrialLaserEngraver`：UEV 门槛、玻璃检查、最大超频数（其中一处还在匿名 ProcessingLogic 内）；
- `MTEWormholeGenerator`：`V[hatch.mTier]` 计算传输上限（结构同时接受源仓和靶仓）；
- `MTEPreciseAssembler`：exotic energy hatch 的 tier 取最大值；
- GT++ `MTEPowerSubStation`：multi-amp 能量/动力仓的 tier 检查与电压求和；
- `MTEElectricImplosionCompressor`：读取后未使用。

因此新增 `MixinMTETieredMachineBlock`：用 `@Shadow @Final @Mutable` 把
`MTETieredMachineBlock.mTier` 变成可写，并通过 `IMutableTier` 暴露 setter。现在**所有可变仓**都在构造、
NBT 载入、网络同步和 GUI 改档时把实际工作等级写回 `mTier`：能源/动力仓写供电/输出等级，液体/激光仓写结构等级。
上述机器以及聚变、PSS、化工厂、等离子锻炉等都能看到真实等级，不再需要逐机器 Mixin。
这不会改变任何其他 MTE 的 `mTier` 值。

**激光链路。** 完全沿用原版：源仓每 20 tick 沿正面扫描最多 1000 格、同色、可穿激光管/镜子；
源仓电压必须等于靶仓 `maxEUInput` 才传输，高于会炸靶仓，低于则不传输。靶仓仍每秒扣 `Amperes` EU。
源仓/靶仓的 tier 由玩家各自设置，必须一致。

**GUI。** MUI2 自建内容区（不复用原版隧道 GUI 的电流文本框）：结构等级按钮、额定电流按钮、
工作电流文本框。sync 用三个 `IntSyncValue`；改 tier 时把额定/工作电流夹取后一并回写，改额定时夹工作电流；
所有改动在服务端 `issueTileUpdate` + `markDirty` + `causeMachineUpdate`，让雕刻机/蚀刻阵列等缓存的
`laserTier`/`laserAmps` 重新读取。

**物品与配方。** 中心件是对应原版 IV 256A 激光仓，四周 8 拉杆。`setItemNBT` 保存
`structureTier` / `ratedAmperes` / `workingAmperes`，拆下保留、工作台重置；tooltip/渲染按物品 NBT。
传奇激光仓（UXV、约 5.37 亿安培）是独立特殊物品，不纳入常规矩阵。
