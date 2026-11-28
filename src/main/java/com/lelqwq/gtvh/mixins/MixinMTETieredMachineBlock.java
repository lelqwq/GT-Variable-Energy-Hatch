package com.lelqwq.gtvh.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import com.lelqwq.gtvh.common.api.interfaces.IMutableTier;

import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;

/**
 * Lets the variable laser hatches keep GT's {@code mTier} field in sync with their configurable tier. Several
 * multiblocks (industrial laser engraver, wormhole generator, precise assembler, GT++ power substation) read
 * {@code mTier} directly, so overriding the tier getters alone is not enough.
 */
@Mixin(value = MTETieredMachineBlock.class, remap = false)
public abstract class MixinMTETieredMachineBlock implements IMutableTier {

    @Shadow
    @Final
    @Mutable
    public byte mTier;

    @Override
    public void gtvh$setTier(byte tier) {
        this.mTier = tier;
    }
}
