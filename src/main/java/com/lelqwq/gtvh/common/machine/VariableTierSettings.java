package com.lelqwq.gtvh.common.machine;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import io.netty.buffer.ByteBuf;

/**
 * Shared tier configuration for the variable fluid input/output hatches.
 * <p>
 * One tier drives both the structure check and the tank capacity ({@code 8000L << tier}, same formula as GT).
 */
final class VariableTierSettings {

    private static final int MAX_TIER = 13;

    private static final String NBT_STRUCTURE_TIER = "structureTier";

    private int structureTier = VariableHatchSettings.BASE_TIER;

    byte structureTier() {
        return (byte) structureTier;
    }

    int structureTierValue() {
        return structureTier;
    }

    void setStructureTier(int tier) {
        structureTier = clampTier(tier);
    }

    int fluidCapacity() {
        return VariableTieredHatch.capacityForTier(structureTier);
    }

    void save(NBTTagCompound tag, boolean omitDefaults) {
        if (!omitDefaults || structureTier != VariableHatchSettings.BASE_TIER) {
            tag.setInteger(NBT_STRUCTURE_TIER, structureTier);
        }
    }

    void load(NBTTagCompound tag) {
        if (tag == null) return;
        if (tag.hasKey(NBT_STRUCTURE_TIER)) structureTier = clampTier(tag.getInteger(NBT_STRUCTURE_TIER));
    }

    void write(ByteBuf buffer) {
        buffer.writeInt(structureTier);
    }

    void read(ByteBuf buffer) {
        structureTier = clampTier(buffer.readInt());
    }

    static void addItemTooltip(ItemStack stack, List<String> tooltip) {
        VariableTierSettings item = new VariableTierSettings();
        item.load(stack.getTagCompound());
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtvh.tooltip.configuration"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "gtvh.tooltip.fluid_tier",
                GTValues.VN[item.structureTier],
                formatNumber(item.fluidCapacity())));
    }

    String[] info() {
        return new String[] {
            StatCollector.translateToLocalFormatted("gtvh.tooltip.structure_tier", GTValues.VN[structureTier]),
            StatCollector.translateToLocalFormatted("gtvh.tooltip.fluid_capacity", formatNumber(fluidCapacity())) };
    }

    ITexture[] replaceDefaultCasing(ITexture[] textures, int colorIndex) {
        ITexture[][] casings = Textures.BlockIcons.MACHINE_CASINGS;
        int color = colorIndex + 1;
        if (textures.length > 0 && color >= 0
            && color < casings[VariableHatchSettings.BASE_TIER].length
            && textures[0] == casings[VariableHatchSettings.BASE_TIER][color]) {
            textures[0] = casings[renderedSettings().structureTier][color];
        }
        return textures;
    }

    private VariableTierSettings renderedSettings() {
        ItemStack stack = VariableHatchSettings.renderedItemStack();
        if (stack == null) return this;
        VariableTierSettings rendered = new VariableTierSettings();
        rendered.load(stack.getTagCompound());
        return rendered;
    }

    private static int clampTier(int tier) {
        return Math.max(0, Math.min(MAX_TIER, tier));
    }
}
