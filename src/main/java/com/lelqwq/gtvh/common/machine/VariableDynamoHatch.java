package com.lelqwq.gtvh.common.machine;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.lelqwq.gtvh.common.api.interfaces.IMutableTier;

import gregtech.api.GregTechAPI;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IHideTooltipEnergyInfo;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import io.netty.buffer.ByteBuf;

/** A regular dynamo hatch with independently configurable structure and electrical tiers. */
public class VariableDynamoHatch extends MTEHatchDynamo implements IHideTooltipEnergyInfo {

    private final VariableHatchSettings settings = new VariableHatchSettings();

    public VariableDynamoHatch(int id, String name, String regionalName) {
        super(id, name, regionalName, VariableHatchSettings.BASE_TIER, new String[0]);
        applyPhysicalTier();
    }

    private VariableDynamoHatch(String name, String[] description, ITexture[][][] textures) {
        super(name, VariableHatchSettings.BASE_TIER, description, textures);
        applyPhysicalTier();
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new VariableDynamoHatch(mName, mDescriptionArray, mTextures);
    }

    @Override
    public String getLocalName() {
        return StatCollector.translateToLocal("gt.blockmachines.variable_dynamo_hatch.name");
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtvh.tooltip.configure"),
            StatCollector.translateToLocal("gtvh.tooltip.output_warning") };
    }

    @Override
    public byte getTierForStructure() {
        return settings.structureTier();
    }

    @Override
    public long getOutputTier() {
        return settings.powerTier();
    }

    @Override
    public long maxEUOutput() {
        return settings.voltage();
    }

    @Override
    public long maxEUStore() {
        return settings.capacity();
    }

    @Override
    public long maxAmperesOut() {
        return settings.amperage();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        return settings
            .replaceDefaultCasing(super.getTexture(base, side, facing, colorIndex, active, redstoneLevel), colorIndex);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture baseTexture) {
        return getTexturesInactive(baseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture baseTexture) {
        return new ITexture[] { baseTexture, settings.overlay(true) };
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
        applyPhysicalTier();
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
        applyPhysicalTier();
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return settings.info(true);
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        VariableHatchSettings.addItemTooltip(stack, tooltip, true);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity base, EntityPlayer player) {
        if (base.isServerSide()) openGui(player);
        return true;
    }

    @Override
    public int getGUIHeight() {
        return VariableHatchSettings.GUI_HEIGHT;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.setBackground(GTUITextures.BACKGROUND_SINGLEBLOCK_DEFAULT);
        builder.setGuiTint(getGUIColorization());
        settings.addWidgets(builder, getGUIWidth(), true, this::onSettingsChanged);
    }

    private void applyPhysicalTier() {
        IMutableTier.applyTier(this, settings.powerTier());
    }

    private void onSettingsChanged(boolean structureChanged) {
        applyPhysicalTier();
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        base.issueTileUpdate();
        base.markDirty();
        // Output tier is mirrored into mTier, so all raw-tier consumers must rerun.
        GregTechAPI.causeMachineUpdate(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }
}
