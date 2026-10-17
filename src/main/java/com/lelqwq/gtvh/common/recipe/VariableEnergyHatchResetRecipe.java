package com.lelqwq.gtvh.common.recipe;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import com.lelqwq.gtvh.common.machine.VariableEnergyHatch;

import gregtech.api.util.GTShapelessRecipe;

/** Keeps machine item data such as covers while resetting the variable hatch configuration. */
public final class VariableEnergyHatchResetRecipe extends GTShapelessRecipe {

    public VariableEnergyHatchResetRecipe(ItemStack hatch) {
        super(hatch.copy(), false, true, false, null, null, hatch.copy());
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack result = super.getCraftingResult(grid);
        VariableEnergyHatch.clearItemConfiguration(result);
        return result;
    }
}
