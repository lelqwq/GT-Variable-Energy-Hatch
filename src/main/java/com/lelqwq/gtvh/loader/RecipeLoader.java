package com.lelqwq.gtvh.loader;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.lelqwq.gtvh.GTVariableEnergyHatch;
import com.lelqwq.gtvh.common.recipe.VariableHatchResetRecipe;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 配方注册。
 * <p>
 * 工作台 3×3：中心一件对应的 LV 仓，四周 8 格原版拉杆。
 */
public final class RecipeLoader {

    private RecipeLoader() {}

    public static void run() {
        register(MachineLoader.VARIABLE_ENERGY_HATCH, MetaTileEntityIDs.ENERGY_HATCH_LV);
        register(MachineLoader.VARIABLE_DYNAMO_HATCH, MetaTileEntityIDs.DYNAMO_HATCH_LV);
    }

    private static void register(IMetaTileEntity hatch, MetaTileEntityIDs baseHatchId) {
        IMetaTileEntity baseHatch = GregTechAPI.METATILEENTITIES[baseHatchId.ID];
        if (baseHatch == null) {
            GTVariableEnergyHatch.LOG.warn("Missing MTE {}; skipping recipe registration.", baseHatchId);
            return;
        }

        ItemStack result = hatch.getStackForm(1);
        GameRegistry.addShapedRecipe(
            result,
            "LLL",
            "LHL",
            "LLL",
            'L',
            new ItemStack(Blocks.lever),
            'H',
            baseHatch.getStackForm(1));
        GameRegistry.addRecipe(new VariableHatchResetRecipe(result));
    }
}
