package com.lelqwq.gtvh.common.machine;

import gregtech.api.enums.GTValues;

/** Shared configuration surface for the variable laser source and target hatches. */
public interface VariableLaserHatch {

    int MIN_TIER = 5; // IV
    int MAX_TIER = 13; // UXV
    int BASE_TIER = 5;
    int MAX_AMPERES = 1_048_576;
    int DEFAULT_RATED_AMPERES = 256;
    int DEFAULT_WORKING_AMPERES = 256;

    /**
     * Vanilla laser hatch matrix: the rated maximum current available at each tier.
     */
    int[][] ALLOWED_AMPERES = { { 256 }, { 256, 1024 }, { 256, 1024, 4096 }, { 256, 1024, 4096, 16384 },
        { 256, 1024, 4096, 16384, 65536 }, { 256, 1024, 4096, 16384, 65536, 262144 },
        { 256, 1024, 4096, 16384, 65536, 262144, 1048576 }, { 256, 1024, 4096, 16384, 65536, 262144, 1048576 },
        { 256, 1024, 4096, 16384, 65536, 262144, 1048576 } };

    static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    static int[] allowedAmperes(int tier) {
        return ALLOWED_AMPERES[clampTier(tier) - MIN_TIER].clone();
    }

    /** Largest vanilla rated current not above {@code amps}. */
    static int clampRatedAmperes(int tier, int amps) {
        int[] allowed = ALLOWED_AMPERES[clampTier(tier) - MIN_TIER];
        int best = allowed[0];
        for (int value : allowed) {
            if (value > amps) break;
            best = value;
        }
        return best;
    }

    static int clampWorkingAmperes(int rated, int amps) {
        return Math.max(0, Math.min(rated, amps));
    }

    static long throughput(int tier, int workingAmperes) {
        return GTValues.V[clampTier(tier)] * (long) workingAmperes;
    }

    int getStructureTier();

    void setStructureTier(int tier);

    int getRatedAmperes();

    void setRatedAmperes(int amperes);

    int getWorkingAmperes();

    void setWorkingAmperes(int amperes);
}
