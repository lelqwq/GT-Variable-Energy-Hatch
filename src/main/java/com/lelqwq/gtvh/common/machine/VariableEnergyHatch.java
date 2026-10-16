package com.lelqwq.gtvh.common.machine;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.gtnewhorizons.modularui.common.widget.textfield.NumericWidget;

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
 * 自己也挂着"为什么要反射改 final 字段"的 TODO），改为覆写所有读 mTier 的方法。</li>
 * </ul>
 * 已知限制：多方块只有一个能源仓且无 exotic 仓时，GT 的 {@code setProcessingLogicPower}
 * 会把可用电流硬写成 1A（{@code useSingleAmp}），此时本仓的电流档位不参与功率预算。
 */
public class VariableEnergyHatch extends MTEHatchEnergy {

    /** 结构 tier / 电气 tier 的可选范围：0=ULV .. 13=UXV（与原版能源仓注册范围一致） */
    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 13;

    /** 电流可选档位（对应原版 2A/4A/16A/64A 覆盖层与 1A 默认档） */
    public static final int[] AMPERAGE_STEPS = { 1, 2, 4, 16, 64 };

    /** 构造期固定 tier：仅用于未接入多方块时的外壳贴图回退与物品图标 */
    private static final int BASE_TIER = 1;

    private static final String NBT_STRUCTURE_TIER = "structureTier";
    private static final String NBT_ENERGY_TIER = "energyTier";
    private static final String NBT_AMPERAGE = "amperage";

    private static final String[] DESCRIPTION = { "An energy hatch whose tiers are switchable in its GUI.",
        "Structure tier decides which multiblocks accept it.",
        "Power tier is the voltage budget it reports to the machine.",
        "Amperage only joins the budget when the multiblock has 2+ energy hatches.", "Right-click to configure.", "",
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

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);
        builder.setGuiTint(getGUIColorization());

        final int x = getGUIWidth() / 2 - 37;
        int y = getGUIHeight() / 5 - 7;

        builder.widget(
            TextWidget.localised("gtvh.gui.structure_tier")
                .setPos(x, y)
                .setSize(74, 14))
            .widget(new NumericWidget().setSetter(val -> {
                structureTier = clampTier((int) val);
                onSettingsChanged();
            })
                .setGetter(() -> (double) structureTier)
                .setBounds(MIN_TIER, MAX_TIER)
                .setScrollValues(1, 4, 64)
                .setTextAlignment(Alignment.Center)
                .setTextColor(Color.WHITE.normal)
                .setSize(70, 18)
                .setPos(x, y + 16)
                .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD));

        y += 44;

        builder.widget(
            TextWidget.localised("gtvh.gui.power_tier")
                .setPos(x, y)
                .setSize(74, 14))
            .widget(new NumericWidget().setSetter(val -> {
                energyTier = clampTier((int) val);
                onSettingsChanged();
            })
                .setGetter(() -> (double) energyTier)
                .setBounds(MIN_TIER, MAX_TIER)
                .setScrollValues(1, 4, 64)
                .setTextAlignment(Alignment.Center)
                .setTextColor(Color.WHITE.normal)
                .setSize(70, 18)
                .setPos(x, y + 16)
                .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD));

        y += 44;

        builder.widget(
            TextWidget.localised("gtvh.gui.amperage")
                .setPos(x, y)
                .setSize(74, 14))
            .widget(new NumericWidget().setSetter(val -> {
                amperage = snapAmperage((int) val);
                onSettingsChanged();
            })
                .setGetter(() -> (double) amperage)
                .setBounds(AMPERAGE_STEPS[0], AMPERAGE_STEPS[AMPERAGE_STEPS.length - 1])
                .setScrollValues(1, 4, 16)
                .setTextAlignment(Alignment.Center)
                .setTextColor(Color.WHITE.normal)
                .setSize(70, 18)
                .setPos(x, y + 16)
                .setBackground(GTUITextures.BACKGROUND_TEXT_FIELD));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private void onSettingsChanged() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            // 覆盖层贴图与信息面板依赖这两个值，推一次方块更新让客户端跟上
            base.issueTileUpdate();
        }
    }

    private static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    /** 电流吸附到最近的合法档位（GUI 用数字框，但取值域始终是 AMPERAGE_STEPS） */
    private static int snapAmperage(int value) {
        int best = AMPERAGE_STEPS[0];
        int bestDistance = Integer.MAX_VALUE;
        for (int step : AMPERAGE_STEPS) {
            int distance = Math.abs(step - value);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = step;
            }
        }
        return best;
    }

    private static String tierName(int tier) {
        if (tier >= 0 && tier < GTValues.VOLTAGE_NAMES.length) {
            return GTValues.VOLTAGE_NAMES[tier];
        }
        return "Tier " + tier;
    }
}
