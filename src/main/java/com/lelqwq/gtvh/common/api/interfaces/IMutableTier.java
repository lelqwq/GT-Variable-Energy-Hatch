package com.lelqwq.gtvh.common.api.interfaces;

/** Implemented on GT's tiered MTE base by {@code MixinMTETieredMachineBlock}. */
public interface IMutableTier {

    void gtvh$setTier(byte tier);

    /** Writes the runtime tier into GT's {@code mTier} if the mixin is applied. */
    static void applyTier(Object hatch, int tier) {
        if (hatch instanceof IMutableTier mutable) {
            mutable.gtvh$setTier((byte) tier);
        }
    }
}
