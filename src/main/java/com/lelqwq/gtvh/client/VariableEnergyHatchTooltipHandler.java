package com.lelqwq.gtvh.client;

import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.lelqwq.gtvh.loader.MachineLoader;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/** Removes the fixed default energy lines that conflict with the hatch's item NBT configuration. */
public final class VariableEnergyHatchTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!GTUtility.areStacksEqual(event.itemStack, MachineLoader.VARIABLE_ENERGY_HATCH.getStackForm(1), true))
            return;

        IGregTechTileEntity template = MachineLoader.VARIABLE_ENERGY_HATCH.getBaseMetaTileEntity();
        long inputVoltage = template.getInputVoltage();
        event.toolTip.remove(
            StatCollector.translateToLocalFormatted(
                "gt.tileentity.eup_in",
                GTUtility.formatNumbers(inputVoltage),
                GTUtility.getColoredTierNameFromTier(GTUtility.getTier(inputVoltage))));
        event.toolTip.remove(
            StatCollector.translateToLocalFormatted(
                "gt.tileentity.eup_store",
                GTUtility.formatNumbers(template.getEUCapacity())));
    }
}
