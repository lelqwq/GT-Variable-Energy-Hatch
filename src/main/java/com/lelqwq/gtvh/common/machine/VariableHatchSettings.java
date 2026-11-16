package com.lelqwq.gtvh.common.machine;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import io.netty.buffer.ByteBuf;

/** Shared configuration for the input and output hatches, independent of their GT base classes. */
public final class VariableHatchSettings {

    static final int BASE_TIER = 1;
    private static final int MAX_TIER = 13;
    private static final int[] AMPERAGE_STEPS = { 2, 4, 16, 64 };
    private static final int DEFAULT_AMPERAGE = AMPERAGE_STEPS[0];

    private static final int VALUE_WIDTH = 70;
    private static final int VALUE_HEIGHT = 18;
    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_GAP = 2;
    private static final int ROW_WIDTH = BUTTON_SIZE * 2 + BUTTON_GAP * 2 + VALUE_WIDTH;

    private static final int TIER_SHIFT_STEP = 4;

    private static final int LABEL_HEIGHT = 14;
    private static final int LABEL_GAP = 2;
    private static final int ROW_SPACING = 6;
    private static final int CONTENT_TOP = 8;
    private static final int CONTENT_BOTTOM_GAP = 6;

    private static final int CONTROL_ROWS = 3;
    private static final int ROW_BLOCK_HEIGHT = LABEL_HEIGHT + LABEL_GAP + VALUE_HEIGHT;
    private static final int CONTENT_BOTTOM = CONTENT_TOP + CONTROL_ROWS * ROW_BLOCK_HEIGHT
        + (CONTROL_ROWS - 1) * ROW_SPACING
        + CONTENT_BOTTOM_GAP;

    private static final int PLAYER_INVENTORY_RESERVED = 76 + 7;

    static final int GUI_HEIGHT = CONTENT_BOTTOM + PLAYER_INVENTORY_RESERVED;

    // Keep the original energy hatch's keys and packet order for existing saves.
    private static final String NBT_STRUCTURE_TIER = "structureTier";
    private static final String NBT_ENERGY_TIER = "energyTier";
    private static final String NBT_AMPERAGE = "amperage";
    private static final ThreadLocal<ItemStack> RENDERED_ITEM = new ThreadLocal<>();

    private int structureTier = BASE_TIER;
    private int powerTier = BASE_TIER;
    private int amperage = DEFAULT_AMPERAGE;

    byte structureTier() {
        return (byte) structureTier;
    }

    int powerTier() {
        return powerTier;
    }

    int amperage() {
        return amperage;
    }

    long voltage() {
        return GTValues.V[powerTier];
    }

    long capacity() {
        return 512L + voltage() * 4L * amperage;
    }

    void save(NBTTagCompound tag, boolean omitDefaults) {
        if (!omitDefaults || structureTier != BASE_TIER) tag.setInteger(NBT_STRUCTURE_TIER, structureTier);
        if (!omitDefaults || powerTier != BASE_TIER) tag.setInteger(NBT_ENERGY_TIER, powerTier);
        if (!omitDefaults || amperage != DEFAULT_AMPERAGE) tag.setInteger(NBT_AMPERAGE, amperage);
    }

    void load(NBTTagCompound tag) {
        if (tag == null) return;
        if (tag.hasKey(NBT_STRUCTURE_TIER)) structureTier = clampTier(tag.getInteger(NBT_STRUCTURE_TIER));
        if (tag.hasKey(NBT_ENERGY_TIER)) powerTier = clampTier(tag.getInteger(NBT_ENERGY_TIER));
        if (tag.hasKey(NBT_AMPERAGE)) amperage = AMPERAGE_STEPS[amperageIndex(tag.getInteger(NBT_AMPERAGE))];
    }

    void write(ByteBuf buffer) {
        buffer.writeInt(structureTier);
        buffer.writeInt(powerTier);
        buffer.writeInt(amperage);
    }

    void read(ByteBuf buffer) {
        structureTier = clampTier(buffer.readInt());
        powerTier = clampTier(buffer.readInt());
        amperage = AMPERAGE_STEPS[amperageIndex(buffer.readInt())];
    }

    public static void clearItemConfiguration(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        tag.removeTag(NBT_STRUCTURE_TIER);
        tag.removeTag(NBT_ENERGY_TIER);
        tag.removeTag(NBT_AMPERAGE);
        if (tag.hasNoTags()) stack.setTagCompound(null);
    }

    public static void renderItemWithConfiguration(ItemStack stack, Runnable render) {
        ItemStack previous = RENDERED_ITEM.get();
        RENDERED_ITEM.set(stack);
        try {
            render.run();
        } finally {
            if (previous == null) RENDERED_ITEM.remove();
            else RENDERED_ITEM.set(previous);
        }
    }

    private static VariableHatchSettings fromItem(ItemStack stack) {
        VariableHatchSettings settings = new VariableHatchSettings();
        settings.load(stack.getTagCompound());
        return settings;
    }

    private VariableHatchSettings renderedSettings() {
        ItemStack stack = RENDERED_ITEM.get();
        return stack == null ? this : fromItem(stack);
    }

    ITexture[] replaceDefaultCasing(ITexture[] textures, int colorIndex) {
        ITexture[][] casings = Textures.BlockIcons.MACHINE_CASINGS;
        int color = colorIndex + 1;
        if (textures.length > 0 && color >= 0
            && color < casings[BASE_TIER].length
            && textures[0] == casings[BASE_TIER][color]) {
            textures[0] = casings[renderedSettings().powerTier][color];
        }
        return textures;
    }

    ITexture overlay(boolean output) {
        VariableHatchSettings rendered = renderedSettings();
        ITexture[] overlays = switch (rendered.amperage) {
            case 64 -> output ? Textures.BlockIcons.OVERLAYS_ENERGY_OUT_MULTI_64A
                : Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_64A;
            case 16 -> output ? Textures.BlockIcons.OVERLAYS_ENERGY_OUT_MULTI_16A
                : Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_16A;
            case 4 -> output ? Textures.BlockIcons.OVERLAYS_ENERGY_OUT_MULTI_4A
                : Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_4A;
            default -> output ? Textures.BlockIcons.OVERLAYS_ENERGY_OUT_MULTI_2A
                : Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_2A;
        };
        return overlays[Math.min(rendered.powerTier + 1, overlays.length - 1)];
    }

    static void addItemTooltip(ItemStack stack, List<String> tooltip, boolean output) {
        VariableHatchSettings item = fromItem(stack);
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtvh.tooltip.configuration"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocalFormatted("gtvh.tooltip.structure_tier", GTValues.VN[item.structureTier]));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                output ? "gtvh.tooltip.output_tier" : "gtvh.tooltip.power_tier",
                GTValues.VN[item.powerTier],
                formatNumber(item.voltage())));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("gtvh.tooltip.amperage", item.amperage));
    }

    String[] info(boolean output) {
        return new String[] {
            StatCollector.translateToLocalFormatted("gtvh.tooltip.structure_tier", GTValues.VN[structureTier]),
            StatCollector.translateToLocalFormatted(
                output ? "gtvh.tooltip.output_tier" : "gtvh.tooltip.power_tier",
                GTValues.VN[powerTier],
                formatNumber(voltage())),
            StatCollector.translateToLocalFormatted("gtvh.tooltip.amperage", amperage),
            StatCollector.translateToLocalFormatted("gtvh.info.throughput", formatNumber(voltage() * amperage)),
            StatCollector.translateToLocalFormatted("gtvh.info.buffer", formatNumber(capacity())) };
    }

    void addWidgets(ModularWindow.Builder builder, int width, boolean output, Consumer<Boolean> changed) {
        String powerKey = output ? "gtvh.gui.output_tier" : "gtvh.gui.power_tier";
        String shiftTier = "gtvh.gui.shift_hint.tier";
        addSettingRow(
            builder,
            width,
            CONTENT_TOP,
            "gtvh.gui.structure_tier",
            () -> structureTier + " · " + GTValues.VN[structureTier],
            () -> Arrays.asList(
                StatCollector.translateToLocalFormatted("gtvh.gui.structure_tier.tooltip", GTValues.VN[structureTier]),
                StatCollector.translateToLocal(shiftTier)),
            (direction, shift) -> {
                int next = stepTier(structureTier, direction, shift);
                if (next != structureTier) {
                    structureTier = next;
                    changed.accept(true);
                }
            });

        addSettingRow(
            builder,
            width,
            CONTENT_TOP + ROW_BLOCK_HEIGHT + ROW_SPACING,
            powerKey,
            () -> powerTier + " · " + GTValues.VN[powerTier],
            () -> Arrays.asList(
                StatCollector.translateToLocalFormatted(
                    powerKey + ".tooltip",
                    GTValues.VN[powerTier] + " · " + formatNumber(voltage()) + " EU/t"),
                StatCollector.translateToLocal(powerKey + ".tooltip.warn"),
                StatCollector.translateToLocal(shiftTier)),
            (direction, shift) -> {
                int next = stepTier(powerTier, direction, shift);
                if (next != powerTier) {
                    powerTier = next;
                    changed.accept(false);
                }
            });

        addSettingRow(
            builder,
            width,
            CONTENT_TOP + 2 * (ROW_BLOCK_HEIGHT + ROW_SPACING),
            "gtvh.gui.amperage",
            () -> amperage + " A",
            () -> output
                ? Arrays.asList(
                    StatCollector.translateToLocalFormatted("gtvh.gui.amperage.tooltip", amperage + " A"),
                    StatCollector.translateToLocal(powerKey + ".tooltip.warn"),
                    StatCollector.translateToLocal("gtvh.gui.shift_hint.amperage"))
                : Arrays.asList(
                    StatCollector.translateToLocalFormatted("gtvh.gui.amperage.tooltip", amperage + " A"),
                    StatCollector.translateToLocal("gtvh.gui.shift_hint.amperage")),
            (direction, shift) -> {
                int last = AMPERAGE_STEPS.length - 1;
                int index = shift ? (direction > 0 ? last : 0)
                    : Math.max(0, Math.min(last, amperageIndex(amperage) + direction));
                int next = AMPERAGE_STEPS[index];
                if (next != amperage) {
                    amperage = next;
                    changed.accept(false);
                }
            });
    }

    private static void addSettingRow(ModularWindow.Builder builder, int width, int y, String labelKey,
        Supplier<String> display, Supplier<List<String>> tooltip, Stepper stepper) {
        final int rowX = (width - ROW_WIDTH) / 2;
        final int valueX = rowX + BUTTON_SIZE + BUTTON_GAP;

        builder.widget(
            TextWidget.localised(labelKey)
                .setPos(valueX, y)
                .setSize(VALUE_WIDTH, LABEL_HEIGHT))
            .widget(stepButton(rowX, y, false, stepper, tooltip))
            .widget(valueBox(display, tooltip, valueX, y))
            .widget(stepButton(rowX + ROW_WIDTH - BUTTON_SIZE, y, true, stepper, tooltip));
    }

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

    @FunctionalInterface
    private interface Stepper {

        void step(int direction, boolean shift);
    }

    private static int clampTier(int tier) {
        return Math.max(0, Math.min(MAX_TIER, tier));
    }

    private static int stepTier(int tier, int direction, boolean shift) {
        return clampTier(tier + direction * (shift ? TIER_SHIFT_STEP : 1));
    }

    private static int amperageIndex(int amperage) {
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < AMPERAGE_STEPS.length; i++) {
            long distance = Math.abs((long) AMPERAGE_STEPS[i] - amperage);
            if (distance < bestDistance) {
                bestIndex = i;
                bestDistance = distance;
            }
        }
        return bestIndex;
    }
}
