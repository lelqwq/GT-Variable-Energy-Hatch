package com.lelqwq.gtvh.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import com.lelqwq.gtvh.common.api.enums.MetaTileEntityID;
import com.lelqwq.gtvh.common.machine.VariableEnergyHatch;
import com.lelqwq.gtvh.loader.MachineLoader;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class VariableEnergyHatchItemRenderer implements IItemRenderer {

    public static void register() {
        ItemStack hatch = MachineLoader.VARIABLE_ENERGY_HATCH.getStackForm(1);
        MinecraftForgeClient.registerItemRenderer(hatch.getItem(), new VariableEnergyHatchItemRenderer());
    }

    @Override
    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        return stack.getItemDamage() == MetaTileEntityID.VARIABLE_ENERGY_HATCH.ID
            && type != ItemRenderType.FIRST_PERSON_MAP;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        RenderBlocks renderer = (RenderBlocks) data[0];
        Block block = Block.getBlockFromItem(stack.getItem());
        VariableEnergyHatch
            .renderItemWithConfiguration(stack, () -> renderer.renderBlockAsItem(block, stack.getItemDamage(), 1.0F));
    }
}
