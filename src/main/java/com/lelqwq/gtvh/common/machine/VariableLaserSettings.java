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

/** Tier and current configuration for the variable laser hatches. */
final class VariableLaserSettings {

    private static final String NBT_STRUCTURE_TIER = "structureTier";
    private static final String NBT_RATED_AMPERES = "ratedAmperes";
    private static final String NBT_WORKING_AMPERES = "workingAmperes";

    private int structureTier = VariableLaserHatch.BASE_TIER;
    private int ratedAmperes = VariableLaserHatch.DEFAULT_RATED_AMPERES;
    private int workingAmperes = VariableLaserHatch.DEFAULT_WORKING_AMPERES;

    int structureTier() {
        return structureTier;
    }

    void setStructureTier(int tier) {
        structureTier = VariableLaserHatch.clampTier(tier);
        ratedAmperes = VariableLaserHatch.clampRatedAmperes(structureTier, ratedAmperes);
        workingAmperes = VariableLaserHatch.clampWorkingAmperes(ratedAmperes, workingAmperes);
    }

    int ratedAmperes() {
        return ratedAmperes;
    }

    void setRatedAmperes(int amperes) {
        ratedAmperes = VariableLaserHatch.clampRatedAmperes(structureTier, amperes);
        workingAmperes = VariableLaserHatch.clampWorkingAmperes(ratedAmperes, workingAmperes);
    }

    int workingAmperes() {
        return workingAmperes;
    }

    void setWorkingAmperes(int amperes) {
        workingAmperes = VariableLaserHatch.clampWorkingAmperes(ratedAmperes, amperes);
    }

    long throughput() {
        return VariableLaserHatch.throughput(structureTier, workingAmperes);
    }

    void save(NBTTagCompound tag, boolean omitDefaults) {
        if (!omitDefaults || structureTier != VariableLaserHatch.BASE_TIER) {
            tag.setInteger(NBT_STRUCTURE_TIER, structureTier);
        }
        if (!omitDefaults || ratedAmperes != VariableLaserHatch.DEFAULT_RATED_AMPERES) {
            tag.setInteger(NBT_RATED_AMPERES, ratedAmperes);
        }
        if (!omitDefaults || workingAmperes != VariableLaserHatch.DEFAULT_WORKING_AMPERES) {
            tag.setInteger(NBT_WORKING_AMPERES, workingAmperes);
        }
    }

    void load(NBTTagCompound tag) {
        if (tag == null) return;
        structureTier = VariableLaserHatch.clampTier(
            tag.hasKey(NBT_STRUCTURE_TIER) ? tag.getInteger(NBT_STRUCTURE_TIER) : VariableLaserHatch.BASE_TIER);
        ratedAmperes = VariableLaserHatch.clampRatedAmperes(
            structureTier,
            tag.hasKey(NBT_RATED_AMPERES) ? tag.getInteger(NBT_RATED_AMPERES)
                : VariableLaserHatch.DEFAULT_RATED_AMPERES);
        workingAmperes = VariableLaserHatch.clampWorkingAmperes(
            ratedAmperes,
            tag.hasKey(NBT_WORKING_AMPERES) ? tag.getInteger(NBT_WORKING_AMPERES)
                : VariableLaserHatch.DEFAULT_WORKING_AMPERES);
    }

    void write(ByteBuf buffer) {
        buffer.writeInt(structureTier);
        buffer.writeInt(ratedAmperes);
        buffer.writeInt(workingAmperes);
    }

    void read(ByteBuf buffer) {
        structureTier = VariableLaserHatch.clampTier(buffer.readInt());
        ratedAmperes = VariableLaserHatch.clampRatedAmperes(structureTier, buffer.readInt());
        workingAmperes = VariableLaserHatch.clampWorkingAmperes(ratedAmperes, buffer.readInt());
    }

    static void addItemTooltip(ItemStack stack, List<String> tooltip) {
        VariableLaserSettings item = new VariableLaserSettings();
        item.load(stack.getTagCompound());
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtvh.tooltip.configuration"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocalFormatted("gtvh.tooltip.structure_tier", GTValues.VN[item.structureTier]));
        tooltip.add(
            EnumChatFormatting.GRAY
                + StatCollector.translateToLocalFormatted("gtvh.tooltip.laser_rated", formatNumber(item.ratedAmperes)));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocalFormatted("gtvh.tooltip.laser_working", formatNumber(item.workingAmperes)));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocalFormatted("gtvh.tooltip.laser_throughput", formatNumber(item.throughput())));
        tooltip.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtvh.tooltip.laser_warning"));
    }

    String[] info() {
        return new String[] {
            StatCollector.translateToLocalFormatted("gtvh.tooltip.structure_tier", GTValues.VN[structureTier]),
            StatCollector.translateToLocalFormatted("gtvh.tooltip.laser_rated", formatNumber(ratedAmperes)),
            StatCollector.translateToLocalFormatted("gtvh.tooltip.laser_working", formatNumber(workingAmperes)),
            StatCollector.translateToLocalFormatted("gtvh.tooltip.laser_throughput", formatNumber(throughput())) };
    }

    ITexture[] replaceDefaultCasing(ITexture[] textures, int colorIndex) {
        ITexture[][] casings = Textures.BlockIcons.MACHINE_CASINGS;
        int color = colorIndex + 1;
        if (textures.length > 0 && color >= 0
            && color < casings[VariableLaserHatch.BASE_TIER].length
            && textures[0] == casings[VariableLaserHatch.BASE_TIER][color]) {
            textures[0] = casings[displayTier()][color];
        }
        return textures;
    }

    int displayTier() {
        return renderedSettings().structureTier;
    }

    private VariableLaserSettings renderedSettings() {
        ItemStack stack = VariableHatchSettings.renderedItemStack();
        if (stack == null) return this;
        VariableLaserSettings rendered = new VariableLaserSettings();
        rendered.load(stack.getTagCompound());
        return rendered;
    }
}
