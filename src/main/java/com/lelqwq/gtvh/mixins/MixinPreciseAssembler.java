package com.lelqwq.gtvh.mixins;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lelqwq.gtvh.common.machine.VariableEnergyHatch;

import goodgenerator.blocks.tileEntity.MTEPreciseAssembler;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

@Mixin(value = MTEPreciseAssembler.class, remap = false)
public abstract class MixinPreciseAssembler {

    @Shadow
    protected int energyHatchTier;

    @Shadow
    private int checkEnergyHatchTier() {
        throw new AssertionError();
    }

    @Redirect(
        method = "checkEnergyHatchTier",
        at = @At(value = "FIELD", target = "Lgregtech/api/metatileentity/implementations/MTEHatchEnergy;mTier:B"))
    private byte gtvh$currentTier(MTEHatchEnergy hatch) {
        return hatch instanceof VariableEnergyHatch ? (byte) hatch.getInputTier() : hatch.mTier;
    }

    @Inject(method = "getMachineVoltageLimit", at = @At("HEAD"))
    private void gtvh$refreshTier(CallbackInfoReturnable<Long> callback) {
        energyHatchTier = checkEnergyHatchTier();
    }

    @Redirect(method = "setProcessingLogicPower", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;size()I"))
    private int gtvh$useConfiguredAmperage(ArrayList<MTEHatchEnergy> hatches) {
        int size = hatches.size();
        return size == 1 && hatches.get(0) instanceof VariableEnergyHatch ? 2 : size;
    }
}
