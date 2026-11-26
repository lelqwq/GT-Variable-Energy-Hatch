package com.lelqwq.gtvh.common.machine;

/** Common accessors for the variable fluid input/output hatches. */
public interface VariableTieredHatch {

    int MAX_TIER = 13;

    /** GT's per-tier fluid capacity: {@code MTEHatchInput.getCapacity()} / {@code MTEHatchOutput.getCapacity()}. */
    int CAPACITY_PER_TIER = 8000;

    static int capacityForTier(int tier) {
        return CAPACITY_PER_TIER << tier;
    }

    static int stepTier(int tier, int direction, boolean shift) {
        int step = direction * (shift ? 4 : 1);
        return Math.max(0, Math.min(MAX_TIER, tier + step));
    }

    int getStructureTier();

    void setStructureTier(int tier);
}
