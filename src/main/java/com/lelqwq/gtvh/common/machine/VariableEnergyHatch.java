package com.lelqwq.gtvh.common.machine;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.util.GTUtility;

/**
 * 可变能源仓 —— 结构 tier / 电气 tier / 电流三项均可在 GUI 中切换的能源仓。
 * <p>
 * 设计要点（与 GT5U 5.09.51.476 对齐，行号见 DESIGN.md）：
 * <ul>
 * <li>只有一个类，恒为 {@link MTEHatchEnergy} 的子类，因此永远进入多方块的 {@code mEnergyHatches}
 * （结构元素按 {@code instanceof MTEHatchEnergy} 匹配），不会被归入 exotic 列表；</li>
 * <li>结构 tier 与电气 tier 解耦：{@code getTierForStructure()} 返回结构 tier，
 * 而 {@code maxEUInput()} 等返回电气 tier 派生的数值；</li>
 * <li>数值公式照抄 {@code MTEHatchEnergyMulti}（含 buffer 随电流放大，否则多仓场景下
 * 每 tick 抽取会被 buffer 卡住）；</li>
 * <li>{@code mTier} 在 GT5U 是 final 字段，故不反射改写（原版唯一先例 {@code TTUtility.setTier}
 * 自己也挂着"为什么要反射改 final 字段"的 TODO），改为覆写影响行为的读者：{@code maxEUInput()}
 * （全部电气数值由它派生）、{@code getTierForStructure()}、{@code getInputTier()}。
 * {@code getOutputTier()} 与 {@code getTileEntityBaseType()} 仍读到 mTier，但本仓没有输出侧、
 * 扳手等级也无实际影响，故不覆写。</li>
 * </ul>
 * 已知限制：多方块只有一个能源仓且无 exotic 仓时，GT 的 {@code setProcessingLogicPower}
 * 会把可用电流硬写成 1A（{@code useSingleAmp}），此时本仓的电流档位不参与功率预算。
 */
public class VariableEnergyHatch extends MTEHatchEnergy {

    /** 结构 tier / 电气 tier 的可选范围：0=ULV .. 13=UXV（与原版能源仓注册范围一致） */
    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 13;

    /** 电流可选档位（对应原版 2A/4A/16A/64A） */
    public static final int[] AMPERAGE_STEPS = { 2, 4, 16, 64 };

    /** 构造期固定 tier：仅用于未接入多方块时的外壳贴图回退与物品图标 */
    private static final int BASE_TIER = 1;

    // ------------------------------------------------------------------
    // GUI 布局
    //
    // 每组控件两行：标题在上，下面一行是 [−][只读数值框][+]。三组竖排，GT 再把玩家背包贴在
    // 窗口底部。窗口高度由"内容区 + 背包区"反推得出，而不是反过来把控件塞进固定高度里 ——
    // 之前用 getGUIHeight()/5 - 7 起头、每组 +44 的固定偏移，在 176×166 的默认窗口里让后两组
    // 控件正好压在背包格子上（背包从 y=83 起画）。
    // ------------------------------------------------------------------

    /** 只读数值框：宽度与标题同宽，两者同轴 */
    private static final int VALUE_WIDTH = 70;
    private static final int VALUE_HEIGHT = 18;
    /** 两侧箭头按钮：用 GT 标准的 18×18 按钮底图 + 大号 −/+ 覆盖层 */
    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_GAP = 2;
    /** 整行宽度 = 左箭头 + 数值框 + 右箭头。整行水平居中后数值框恰好也居中，标题因此与框同轴 */
    private static final int ROW_WIDTH = BUTTON_SIZE * 2 + BUTTON_GAP * 2 + VALUE_WIDTH;

    /** 结构/供电等级用：Shift + 点击箭头一次调几级 */
    private static final int TIER_SHIFT_STEP = 4;

    private static final int LABEL_HEIGHT = 14;
    /** 标题到控件行之间、以及相邻两组之间的间距 */
    private static final int LABEL_GAP = 2;
    private static final int ROW_SPACING = 6;
    /** 内容区上边距（窗口边框内）与最后一行控件到背包之间的留白 */
    private static final int CONTENT_TOP = 8;
    private static final int CONTENT_BOTTOM_GAP = 6;

    /** 控件组数：必须与 addUIWidgets 里的 addSettingRow 调用次数一致（高度按它算） */
    private static final int CONTROL_ROWS = 3;
    /** 一组「标题 + 控件行」占的高度 */
    private static final int ROW_BLOCK_HEIGHT = LABEL_HEIGHT + LABEL_GAP + VALUE_HEIGHT;
    /** 内容区底边 = 最后一行控件底边 + 留白 */
    private static final int CONTENT_BOTTOM = CONTENT_TOP + CONTROL_ROWS * ROW_BLOCK_HEIGHT
        + (CONTROL_ROWS - 1) * ROW_SPACING
        + CONTENT_BOTTOM_GAP;

    /**
     * 窗口底部必须留给玩家背包的高度：GT 默认的 {@code bindPlayerInventory} 把背包钉在窗口底部，
     * 其顶边 = GUI 高度 - 边距(7) - 4 行格子(3×18 + 4 + 18 = 76)，即 83
     * （{@code BaseTileEntity.java:652-654} → {@code ModularWindow.Builder.bindPlayerInventory}
     * → {@code size.height - marginBottom - 76}）。GUI 高度装不下内容区 + 这 83px 就会压背包。
     */
    private static final int PLAYER_INVENTORY_RESERVED = 76 + 7;

    private static final int GUI_HEIGHT = CONTENT_BOTTOM + PLAYER_INVENTORY_RESERVED;

    private static final String NBT_STRUCTURE_TIER = "structureTier";
    private static final String NBT_ENERGY_TIER = "energyTier";
    private static final String NBT_AMPERAGE = "amperage";

    private static final String[] DESCRIPTION = { "Right-click to configure.", "",
        "WARNING: if the incoming cable voltage exceeds the power tier, this hatch explodes." };

    private int structureTier = 1; // LV
    private int energyTier = 1; // LV
    private int amperage = 1;

    public VariableEnergyHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, BASE_TIER, 0, DESCRIPTION);
    }

    public VariableEnergyHatch(String aName, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, BASE_TIER, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new VariableEnergyHatch(mName, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 电气侧：全部由 energyTier / amperage 派生
    // ------------------------------------------------------------------

    @Override
    public long maxEUInput() {
        return GTValues.V[energyTier];
    }

    /** 照抄 MTEHatchEnergyMulti：buffer 必须随电流放大，否则每 tick 抽取上限 = buffer */
    @Override
    public long maxEUStore() {
        return 512L + GTValues.V[energyTier] * 4L * amperage;
    }

    /** 照抄 MTEHatchEnergyMulti：A + A/4（留损耗余量） */
    @Override
    public long maxAmperesIn() {
        return amperage + (amperage >> 2);
    }

    @Override
    public long maxWorkingAmperesIn() {
        return amperage;
    }

    /**
     * 覆写 {@code MTETieredMachineBlock} 对 mTier 的直读。GT 的
     * {@code MTEMultiBlockBase.getInputVoltageTier()}、WAILA 的 energyTier 标签，以及
     * {@code BaseMetaTileEntity.dischargeItem()} 都走这里；不覆写则不论选了什么供电等级，
     * 上报出去的机器 tier 永远是 BASE_TIER(LV)，与实际电压自相矛盾。
     * 语义与 {@code MetaTileEntity.getInputTier()}（由上报电压反推）一致。
     */
    @Override
    public long getInputTier() {
        return energyTier;
    }

    // ------------------------------------------------------------------
    // 结构侧
    // ------------------------------------------------------------------

    @Override
    public byte getTierForStructure() {
        return (byte) structureTier;
    }

    // ------------------------------------------------------------------
    // 贴图：外壳由 GT 在装机时改写成机器外壳；tier 可视线索在覆盖层
    // ------------------------------------------------------------------

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, getOverlay() };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, getOverlay() };
    }

    private ITexture getOverlay() {
        final ITexture[] overlay;
        if (amperage > 16) {
            overlay = Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_64A;
        } else if (amperage > 4) {
            overlay = Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_16A;
        } else if (amperage > 2) {
            overlay = Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_4A;
        } else {
            overlay = Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_2A;
        }
        return overlay[Math.min(energyTier + 1, overlay.length - 1)];
    }

    // ------------------------------------------------------------------
    // 存取与同步
    // ------------------------------------------------------------------

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger(NBT_STRUCTURE_TIER, structureTier);
        aNBT.setInteger(NBT_ENERGY_TIER, energyTier);
        aNBT.setInteger(NBT_AMPERAGE, amperage);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // 用 hasKey 守卫：缺键时保留构造默认值，而不是被 getInteger 的 0 静默顶成 ULV
        if (aNBT.hasKey(NBT_STRUCTURE_TIER)) structureTier = clampTier(aNBT.getInteger(NBT_STRUCTURE_TIER));
        if (aNBT.hasKey(NBT_ENERGY_TIER)) energyTier = clampTier(aNBT.getInteger(NBT_ENERGY_TIER));
        if (aNBT.hasKey(NBT_AMPERAGE)) amperage = snapAmperage(aNBT.getInteger(NBT_AMPERAGE));
    }

    /** 服务端 → 客户端：走 GT 原生描述包，不需要自定义网络包 */
    @Override
    public NBTTagCompound getDescriptionData() {
        NBTTagCompound data = super.getDescriptionData();
        data.setInteger(NBT_STRUCTURE_TIER, structureTier);
        data.setInteger(NBT_ENERGY_TIER, energyTier);
        data.setInteger(NBT_AMPERAGE, amperage);
        return data;
    }

    @Override
    public void onDescriptionPacket(NBTTagCompound data) {
        super.onDescriptionPacket(data);
        if (data.hasKey(NBT_STRUCTURE_TIER)) structureTier = clampTier(data.getInteger(NBT_STRUCTURE_TIER));
        if (data.hasKey(NBT_ENERGY_TIER)) energyTier = clampTier(data.getInteger(NBT_ENERGY_TIER));
        if (data.hasKey(NBT_AMPERAGE)) amperage = snapAmperage(data.getInteger(NBT_AMPERAGE));
    }

    // ------------------------------------------------------------------
    // 信息显示
    // ------------------------------------------------------------------

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { "Structure tier: " + EnumChatFormatting.YELLOW + tierName(structureTier),
            "Power tier: " + EnumChatFormatting.YELLOW
                + tierName(energyTier)
                + EnumChatFormatting.RESET
                + " ("
                + GTUtility.formatNumbers(GTValues.V[energyTier])
                + " EU/t)",
            "Amperage: " + EnumChatFormatting.YELLOW
                + amperage
                + " A"
                + EnumChatFormatting.RESET
                + " (budget "
                + GTUtility.formatNumbers(GTValues.V[energyTier] * amperage)
                + " EU/t)",
            "Buffer: " + GTUtility.formatNumbers(maxEUStore()) + " EU" };
    }

    // ------------------------------------------------------------------
    // 交互与 GUI
    // ------------------------------------------------------------------

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aBaseMetaTileEntity.isServerSide()) {
            openGui(aPlayer);
        }
        return true;
    }

    /**
     * 窗口高度按"内容区 + 玩家背包"反推（见上方布局常量）。MUI1 的背景是可拉伸的九宫格
     * （{@code BACKGROUND_SINGLEBLOCK_DEFAULT} 是 {@code AdaptableUITexture}），所以加高只影响
     * 窗口尺寸，不会糊贴图；玩家背包由 GT 按窗口高度自动重新定位，无需手动摆放。
     * 先例：{@code MTEHatchInputBusME.getGUIHeight()} 返回 179、{@code MTEMultiBlockBase} 用
     * 198×192（后者还要自己挪背包，本仓不需要，因为默认的 marginBottom 落点正好就是常量里那个 83）。
     */
    @Override
    public int getGUIHeight() {
        return GUI_HEIGHT;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);
        builder.setGuiTint(getGUIColorization());

        int y = CONTENT_TOP;

        addSettingRow(
            builder,
            y,
            "gtvh.gui.structure_tier",
            this::structureTierDisplay,
            this::structureTierTooltip,
            (direction, shift) -> setStructureTier(structureTier + direction * (shift ? TIER_SHIFT_STEP : 1)));

        y += ROW_BLOCK_HEIGHT + ROW_SPACING;

        addSettingRow(
            builder,
            y,
            "gtvh.gui.power_tier",
            this::powerTierDisplay,
            this::powerTierTooltip,
            (direction, shift) -> setEnergyTier(energyTier + direction * (shift ? TIER_SHIFT_STEP : 1)));

        y += ROW_BLOCK_HEIGHT + ROW_SPACING;

        addSettingRow(
            builder,
            y,
            "gtvh.gui.amperage",
            this::amperageDisplay,
            this::amperageTooltip,
            (direction, shift) -> setAmperage(
                shift ? (direction > 0 ? AMPERAGE_STEPS[AMPERAGE_STEPS.length - 1] : AMPERAGE_STEPS[0])
                    : stepAmperage(amperage, direction)));
    }

    /**
     * 一组控件：标题在上，下面一行是 {@code [−][只读数值框][+]}，整行在窗口里水平居中。
     * <p>
     * 数值只能靠箭头改（数值框是 {@link TextWidget}，不是输入框），所以"控件显示的值"与
     * "仓里存的值"不可能不一致 —— 这正是原来数字框 + 吸附要绕的那个坑。
     */
    private void addSettingRow(ModularWindow.Builder builder, int y, String labelKey, Supplier<String> display,
        Supplier<List<String>> tooltip, Stepper stepper) {
        final int rowX = (getGUIWidth() - ROW_WIDTH) / 2;
        final int valueX = rowX + BUTTON_SIZE + BUTTON_GAP;

        builder.widget(
            TextWidget.localised(labelKey)
                .setPos(valueX, y)
                .setSize(VALUE_WIDTH, LABEL_HEIGHT))
            .widget(stepButton(rowX, y, false, stepper, tooltip))
            .widget(valueBox(display, tooltip, valueX, y))
            .widget(stepButton(rowX + ROW_WIDTH - BUTTON_SIZE, y, true, stepper, tooltip));
    }

    /**
     * 只读数值框：GT 文本框底图 + 居中白字。与原来的 {@code NumericWidget} 外观一致，但不可输入。
     * <p>
     * 用 {@link TextWidget#setStringSupplier} 而不是 DynamicTextWidget：前者只在客户端求值
     * （MUI 的 javadoc 明说 supplier 只在客户端调用），值本身由 GT 原生描述包同步到客户端
     * （见 {@code getDescriptionData}/{@code onDescriptionPacket}），所以改档后客户端立刻能显示，
     * 不需要额外的网络包，也不受滚轮影响。
     */
    private static TextWidget valueBox(Supplier<String> display, Supplier<List<String>> tooltip, int x, int y) {
        TextWidget box = new TextWidget().setStringSupplier(display)
            .setDefaultColor(Color.WHITE.normal)
            .setTextAlignment(Alignment.Center);
        // 这几个 setter 声明返回 Widget，会吃掉 TextWidget 的静态类型，故逐句调用
        box.setSize(VALUE_WIDTH, VALUE_HEIGHT);
        box.setBackground(GTUITextures.BACKGROUND_TEXT_FIELD);
        // 提示内容含随档位变化的数值/预算，默认的缓存会让它停在第一次的值上
        box.dynamicTooltip(tooltip)
            .setUpdateTooltipEveryTick(true)
            .setTooltipShowUpDelay(TOOLTIP_DELAY);
        box.setPos(x, y);
        return box;
    }

    /**
     * 单步按钮：点击调一档（语义见 {@link Stepper}）。纹理用 GT 自己那套
     * （{@code BUTTON_STANDARD} + 大号 −/+），与 {@code MTETeleporter}、{@code MTEHatchCraftingInputME}
     * 的写法一致；点击会由 {@code SyncedWidget} 自动同步到服务端（两侧各跑一次同一段 setter）。
     */
    private static ButtonWidget stepButton(int x, int y, boolean up, Stepper stepper, Supplier<List<String>> tooltip) {
        ButtonWidget button = new ButtonWidget()
            .setOnClick((clickData, widget) -> stepper.step(up ? 1 : -1, clickData.shift));
        button.setBackground(
            GTUITextures.BUTTON_STANDARD,
            up ? GTUITextures.OVERLAY_BUTTON_PLUS_LARGE : GTUITextures.OVERLAY_BUTTON_MINUS_LARGE);
        button.setSize(BUTTON_SIZE, BUTTON_SIZE);
        button.dynamicTooltip(tooltip)
            .setUpdateTooltipEveryTick(true)
            .setTooltipShowUpDelay(TOOLTIP_DELAY);
        button.setPos(x, y);
        return button;
    }

    /** 箭头点击语义：{@code direction} 为 +1/−1，{@code shift} 为是否按住 Shift */
    @FunctionalInterface
    private interface Stepper {

        void step(int direction, boolean shift);
    }

    // ------------------------------------------------------------------
    // GUI 文案（只在客户端求值）
    // ------------------------------------------------------------------

    /** 结构等级：「1 · LV」 */
    private String structureTierDisplay() {
        return structureTier + " · " + shortTierName(structureTier);
    }

    /** 供电等级：「2 · MV」 */
    private String powerTierDisplay() {
        return energyTier + " · " + shortTierName(energyTier);
    }

    /** 电流：「1 A」 */
    private String amperageDisplay() {
        return amperage + " A";
    }

    private List<String> structureTierTooltip() {
        return Arrays.asList(
            StatCollector.translateToLocalFormatted("gtvh.gui.structure_tier.tooltip", shortTierName(structureTier)),
            StatCollector.translateToLocal("gtvh.gui.shift_hint.tier"));
    }

    private List<String> powerTierTooltip() {
        return Arrays.asList(
            StatCollector.translateToLocalFormatted(
                "gtvh.gui.power_tier.tooltip",
                shortTierName(energyTier) + " · " + GTUtility.formatNumbers(GTValues.V[energyTier]) + " EU/t"),
            StatCollector.translateToLocal("gtvh.gui.power_tier.tooltip.warn"),
            StatCollector.translateToLocal("gtvh.gui.shift_hint.tier"));
    }

    private List<String> amperageTooltip() {
        return Arrays.asList(
            StatCollector.translateToLocalFormatted("gtvh.gui.amperage.tooltip", amperageDisplay()),
            StatCollector.translateToLocal("gtvh.gui.shift_hint.amperage"));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private void onSettingsChanged() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            // 覆盖层贴图与信息面板依赖这两个值，推一次方块更新让客户端跟上
            base.issueTileUpdate();
            // issueTileUpdate 只走客户端同步（markBlockForUpdate），不标脏。
            // 这三项要落盘（见 saveNBTData），不标脏则改档可能随 chunk 保存一起丢失
            base.markDirty();
        }
    }

    /**
     * 三个 setter：夹取/吸附都在这里做，GUI 与 NBT 读档共用，所以不存在"控件显示的档位"
     * 与"仓里存的档位"两个版本。箭头按到边界后再点只是重复写同一个值（无害：值是幂等的，
     * 不会标脏出问题）。
     */
    private void setStructureTier(int tier) {
        structureTier = clampTier(tier);
        onSettingsChanged();
    }

    private void setEnergyTier(int tier) {
        energyTier = clampTier(tier);
        onSettingsChanged();
    }

    private void setAmperage(int value) {
        amperage = snapAmperage(value);
        onSettingsChanged();
    }

    private static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    /** 电流值在 {@link #AMPERAGE_STEPS} 中的下标；值不在档位里时取最近的档位 */
    private static int amperageIndex(int value) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < AMPERAGE_STEPS.length; i++) {
            int distance = Math.abs(AMPERAGE_STEPS[i] - value);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** 电流吸附到最近的合法档位（取值域始终是 AMPERAGE_STEPS，NBT 读档与描述包也走这里） */
    private static int snapAmperage(int value) {
        return AMPERAGE_STEPS[amperageIndex(value)];
    }

    /** 沿 AMPERAGE_STEPS 走一档（1→2→4→16→64），两端夹住不回绕 */
    private static int stepAmperage(int current, int direction) {
        int index = Math.max(0, Math.min(AMPERAGE_STEPS.length - 1, amperageIndex(current) + direction));
        return AMPERAGE_STEPS[index];
    }

    /** 信息面板用的长名（"Ultra Low Voltage"）；本地化仍是待办，见 DESIGN.md 第 7 节 */
    private static String tierName(int tier) {
        if (tier >= 0 && tier < GTValues.VOLTAGE_NAMES.length) {
            return GTValues.VOLTAGE_NAMES[tier];
        }
        return "Tier " + tier;
    }

    /** GUI 用的短名（ULV/LV/MV/…）。{@code GTValues.VN} 不带颜色码，白字在灰底上可读 */
    private static String shortTierName(int tier) {
        if (tier >= 0 && tier < GTValues.VN.length) {
            return GTValues.VN[tier];
        }
        return "T" + tier;
    }
}
