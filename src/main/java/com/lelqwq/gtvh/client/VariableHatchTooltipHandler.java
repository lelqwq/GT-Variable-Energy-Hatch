package com.lelqwq.gtvh.client;

import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.lelqwq.gtvh.loader.MachineLoader;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.api.util.tooltip.TooltipHelper;

/** Removes the default capacity line, which GT calculates from the registered hatch template. */
public final class VariableHatchTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        IMetaTileEntity hatch;
        if (GTUtility.areStacksEqual(event.itemStack, MachineLoader.VARIABLE_ENERGY_HATCH.getStackForm(1), true)) {
            hatch = MachineLoader.VARIABLE_ENERGY_HATCH;
        } else
            if (GTUtility.areStacksEqual(event.itemStack, MachineLoader.VARIABLE_DYNAMO_HATCH.getStackForm(1), true)) {
                hatch = MachineLoader.VARIABLE_DYNAMO_HATCH;
            } else {
                return;
            }
        IGregTechTileEntity template = hatch.getBaseMetaTileEntity();
        event.toolTip.remove(
            StatCollector.translateToLocalFormatted(
                "gt.tileentity.eup_store",
                TooltipHelper.euCapacityText(template.getEUCapacity())));
    }
}
