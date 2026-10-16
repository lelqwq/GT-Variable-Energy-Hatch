package com.lelqwq.gtvh.loader;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 配方注册。
 * <p>
 * 工作台 3×3：中心一件 LV 能源仓，四周 8 格原版拉杆。
 * 中心件通过 GT 的 ID 枚举取（{@code MetaTileEntityIDs.ENERGY_HATCH_LV}）而不是 ItemList 名，
 * 这样不依赖 GT5U 内部的 ItemList 条目命名。
 */
public final class RecipeLoader {

    private RecipeLoader() {}

    public static void run() {
        if (MachineLoader.VARIABLE_ENERGY_HATCH == null) return;

        ItemStack lvEnergyHatch = lvEnergyHatch();
        if (lvEnergyHatch == null) return;

        GameRegistry.addShapedRecipe(
            MachineLoader.VARIABLE_ENERGY_HATCH.getStackForm(1),
            "LLL",
            "LHL",
            "LLL",
            'L',
            new ItemStack(Blocks.lever),
            'H',
            lvEnergyHatch);
    }

    private static ItemStack lvEnergyHatch() {
        int id = MetaTileEntityIDs.ENERGY_HATCH_LV.ID;
        if (id < 0 || id >= GregTechAPI.METATILEENTITIES.length) return null;
        IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[id];
        return mte == null ? null : mte.getStackForm(1);
    }
}
