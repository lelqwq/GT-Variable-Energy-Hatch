package com.lelqwq.gtvh.common.machine;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.lelqwq.gtvh.common.api.interfaces.IMutableTier;
import com.lelqwq.gtvh.common.gui.VariableLaserTargetGui;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import io.netty.buffer.ByteBuf;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyTunnel;

/** Laser target hatch with a configurable tier and vanilla-legal rated current. */
public class VariableLaserTargetHatch extends MTEHatchEnergyTunnel implements VariableLaserHatch {

    private final VariableLaserSettings settings = new VariableLaserSettings();

    public VariableLaserTargetHatch(int id, String name, String regionalName) {
        super(id, name, regionalName, VariableLaserHatch.BASE_TIER, VariableLaserHatch.MAX_AMPERES);
        applySettingsSilently();
    }

    private VariableLaserTargetHatch(String name, int tier, int amperes, String[] description,
        ITexture[][][] textures) {
        super(name, tier, amperes, description, textures);
        applySettingsSilently();
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new VariableLaserTargetHatch(
            mName,
            VariableLaserHatch.BASE_TIER,
            VariableLaserHatch.MAX_AMPERES,
            mDescriptionArray,
            mTextures);
    }

    @Override
    public String getLocalName() {
        return StatCollector.translateToLocal(getLocalNameKey());
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gtvh.description.laser_target"),
            StatCollector.translateToLocal("gtvh.tooltip.configure"),
            StatCollector.translateToLocal("gtvh.tooltip.laser_warning") };
    }

    @Override
    public byte getTierForStructure() {
        return (byte) settings.structureTier();
    }

    @Override
    public long getInputTier() {
        return settings.structureTier();
    }

    @Override
    public long getOutputTier() {
        return settings.structureTier();
    }

    @Override
    public long maxEUInput() {
        return GTValues.V[settings.structureTier()];
    }

    @Override
    public long maxEUStore() {
        return GTValues.V[settings.structureTier()] * 24L * settings.workingAmperes();
    }

    @Override
    public long getMinimumStoredEU() {
        return GTValues.V[settings.structureTier()];
    }

    @Override
    public ITexture[] getTexturesActive(ITexture baseTexture) {
        return new ITexture[] { baseTexture,
            Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_LASER[settings.displayTier() + 1] };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture baseTexture) {
        return getTexturesActive(baseTexture);
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
        applySettingsSilently();
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
        applySettingsSilently();
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
        VariableLaserSettings.addItemTooltip(stack, tooltip);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity base, EntityPlayer player) {
        if (base.isServerSide()) openGui(player);
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new VariableLaserTargetGui(this).build(guiData, syncManager, uiSettings);
    }

    @Override
    public int getStructureTier() {
        return settings.structureTier();
    }

    @Override
    public void setStructureTier(int tier) {
        settings.setStructureTier(tier);
        applyPhysicalTier();
        if (Amperes != settings.workingAmperes()) super.setAmperes(settings.workingAmperes());
        onSettingsChanged();
    }

    @Override
    public int getRatedAmperes() {
        return settings.ratedAmperes();
    }

    @Override
    public void setRatedAmperes(int amperes) {
        settings.setRatedAmperes(amperes);
        if (Amperes != settings.workingAmperes()) super.setAmperes(settings.workingAmperes());
        onSettingsChanged();
    }

    @Override
    public int getWorkingAmperes() {
        return settings.workingAmperes();
    }

    @Override
    public void setWorkingAmperes(int amperes) {
        settings.setWorkingAmperes(amperes);
        if (Amperes != settings.workingAmperes()) super.setAmperes(settings.workingAmperes());
        onSettingsChanged();
    }

    @Override
    public void setAmperes(int amperes) {
        setWorkingAmperes(amperes);
    }

    private void applySettingsSilently() {
        Amperes = settings.workingAmperes();
        applyPhysicalTier();
    }

    private void applyPhysicalTier() {
        if (this instanceof IMutableTier mutable) mutable.gtvh$setTier((byte) settings.structureTier());
    }

    private void onSettingsChanged() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return;
        base.issueTileUpdate();
        base.markDirty();
        GregTechAPI.causeMachineUpdate(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

}
