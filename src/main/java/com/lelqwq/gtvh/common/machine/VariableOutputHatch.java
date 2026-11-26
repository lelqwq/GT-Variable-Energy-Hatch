package com.lelqwq.gtvh.common.machine;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.lelqwq.gtvh.common.gui.VariableOutputHatchGui;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import io.netty.buffer.ByteBuf;

/**
 * A fluid output hatch whose configurable tier feeds both structure checks and tank capacity.
 * <p>
 * GT derives both from {@code mTier}; here one configurable tier feeds {@link #getTierForStructure()} and
 * {@link #getCapacity()}.
 */
public class VariableOutputHatch extends MTEHatchOutput implements VariableTieredHatch {

    private final VariableTierSettings settings = new VariableTierSettings();

    public VariableOutputHatch(int id, String name, String regionalName) {
        super(id, name, regionalName, VariableHatchSettings.BASE_TIER, new String[0], 4);
    }

    private VariableOutputHatch(String name, String[] description, ITexture[][][] textures) {
        super(name, VariableHatchSettings.BASE_TIER, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new VariableOutputHatch(mName, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtvh.description.output_hatch"),
            StatCollector.translateToLocal("gtvh.tooltip.configure") };
    }

    @Override
    public byte getTierForStructure() {
        return settings.structureTier();
    }

    @Override
    public int getStructureTier() {
        return settings.structureTierValue();
    }

    @Override
    public void setStructureTier(int tier) {
        settings.setStructureTier(tier);
        onSettingsChanged(true);
    }

    @Override
    public int getCapacity() {
        return settings.fluidCapacity();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        return settings
            .replaceDefaultCasing(super.getTexture(base, side, facing, colorIndex, active, redstoneLevel), colorIndex);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        settings.save(tag, false);
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        settings.save(tag, true);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        settings.load(tag);
    }

    @Override
    public void writeToStream(ByteBuf buffer) {
        super.writeToStream(buffer);
        settings.write(buffer);
    }

    @Override
    public void readFromStream(ByteBuf buffer) {
        super.readFromStream(buffer);
        settings.read(buffer);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return settings.info();
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        VariableTierSettings.addItemTooltip(stack, tooltip);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new VariableOutputHatchGui(this).build(guiData, syncManager, uiSettings);
    }

    private void onSettingsChanged(boolean structureChanged) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        base.issueTileUpdate();
        base.markDirty();
        if (structureChanged) {
            GregTechAPI.causeMachineUpdate(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
        }
    }
}
