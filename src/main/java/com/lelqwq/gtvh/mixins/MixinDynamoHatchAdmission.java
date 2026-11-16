package com.lelqwq.gtvh.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.lelqwq.gtvh.common.machine.VariableDynamoHatch;

import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MixinDynamoHatchAdmission {

    @Redirect(
        method = "addDynamoToMachineList",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatchDynamo;maxAmperesOut()J"),
        require = 1)
    private long gtvh$amperageForAdmission(MTEHatchDynamo hatch) {
        // Admit the variable dynamo at every setting while keeping GT's check for other hatches.
        return hatch instanceof VariableDynamoHatch ? 4L : hatch.maxAmperesOut();
    }
}
