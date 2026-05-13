package com.tetris.mab.nuke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Soft validator for {@link NukeDesign} instances. Returns a list of
 * human-readable messages prefixed with {@code ERROR:} or
 * {@code WARNING:}; never throws unless the design itself is null.
 */
public class NukeDesignValidator {

    public List<String> validate(NukeDesign design) {
        List<String> out = new ArrayList<>();
        if (design == null) {
            out.add("ERROR: design is null");
            return out;
        }

        // Basic identity
        if (design.getId() == null || design.getId().isBlank())
            out.add("ERROR: id must not be blank");
        if (design.getDisplayName() == null || design.getDisplayName().isBlank())
            out.add("ERROR: displayName must not be blank");
        if (design.getDoctrineType() == null)
            out.add("ERROR: doctrineType must not be null");
        if (design.getSizeCategory() == null)
            out.add("ERROR: sizeCategory must not be null");

        // Numeric ranges
        if (design.getBaseBuildChargeRequired() <= 0)
            out.add("ERROR: baseBuildChargeRequired must be > 0");
        if (design.getBlastRating() < 0)
            out.add("ERROR: blastRating must be >= 0");
        if (design.getRadiationRating() < 0)
            out.add("ERROR: radiationRating must be >= 0");
        if (design.getDisarmRating() < 0)
            out.add("ERROR: disarmRating must be >= 0");
        if (design.getSiloDamageRating() < 0)
            out.add("ERROR: siloDamageRating must be >= 0");
        if (design.getBaseLaunchTimePieces() <= 0)
            out.add("ERROR: baseLaunchTimePieces must be > 0");
        if (design.getBaseWarningTimePieces() <= 0)
            out.add("ERROR: baseWarningTimePieces must be > 0");
        if (design.getDetectionProfile() < 0)
            out.add("ERROR: detectionProfile must be >= 0");

        // Launch code
        List<Integer> base = design.getBaseLaunchCode();
        if (base == null || base.isEmpty()) {
            out.add("ERROR: baseLaunchCode must not be empty");
        } else {
            for (int i = 0; i < base.size(); i++) {
                Integer v = base.get(i);
                if (v == null || v < 1 || v > 4) {
                    out.add("ERROR: baseLaunchCode[" + i + "]=" + v + " must be in 1..4");
                }
            }
        }

        // DEFCON keys + per-DEFCON launch codes
        for (Integer k : design.getBuildChargeByDefcon().keySet())
            if (k == null || k < 1 || k > 5)
                out.add("ERROR: buildChargeByDefcon key " + k + " must be in 1..5");
        for (Integer k : design.getLaunchTimeByDefcon().keySet())
            if (k == null || k < 1 || k > 5)
                out.add("ERROR: launchTimeByDefcon key " + k + " must be in 1..5");
        for (Integer k : design.getWarningTimeByDefcon().keySet())
            if (k == null || k < 1 || k > 5)
                out.add("ERROR: warningTimeByDefcon key " + k + " must be in 1..5");
        for (Map.Entry<Integer, List<Integer>> e : design.getLaunchCodeByDefcon().entrySet()) {
            Integer k = e.getKey();
            if (k == null || k < 1 || k > 5)
                out.add("ERROR: launchCodeByDefcon key " + k + " must be in 1..5");
            List<Integer> code = e.getValue();
            if (code == null || code.isEmpty()) {
                out.add("ERROR: launchCodeByDefcon[" + k + "] must not be empty");
            } else {
                for (int i = 0; i < code.size(); i++) {
                    Integer v = code.get(i);
                    if (v == null || v < 1 || v > 4) {
                        out.add("ERROR: launchCodeByDefcon[" + k + "][" + i + "]=" + v + " must be in 1..4");
                    }
                }
            }
        }

        // Profiles
        if (design.getGarbageProfile() == null)
            out.add("ERROR: garbageProfile must not be null");
        if (design.getDisarmProfile() == null)
            out.add("ERROR: disarmProfile must not be null");
        if (design.getSiloDamageProfile() == null)
            out.add("ERROR: siloDamageProfile must not be null");

        // Doctrine sanity warnings
        NukeDoctrineType d = design.getDoctrineType();
        int blast = design.getBlastRating();
        int rad = design.getRadiationRating();
        int disarm = design.getDisarmRating();
        int silo = design.getSiloDamageRating();

        if (d == NukeDoctrineType.DIRTY_BOMB && rad < blast)
            out.add("WARNING: DIRTY_BOMB usually has radiationRating >= blastRating");
        if (d == NukeDoctrineType.CLEAN_FUSION && blast < rad)
            out.add("WARNING: CLEAN_FUSION usually has blastRating >= radiationRating");
        if (d == NukeDoctrineType.CONCRETE_BLASTER && disarm <= blast && silo <= blast)
            out.add("WARNING: CONCRETE_BLASTER usually has disarmRating or siloDamageRating > blastRating");
        if (d == NukeDoctrineType.DECOY_PACKAGE && blast > 1)
            out.add("WARNING: DECOY_PACKAGE usually has blastRating <= 1");
        if (d == NukeDoctrineType.DOOMSDAY) {
            if (design.getBaseBuildChargeRequired() < 80)
                out.add("WARNING: DOOMSDAY usually has baseBuildChargeRequired >= 80");
            if (base != null && base.size() < 3)
                out.add("WARNING: DOOMSDAY usually has baseLaunchCode length >= 3");
        }
        if (rad >= 5 && blast >= 5 && d != NukeDoctrineType.DOOMSDAY)
            out.add("WARNING: high blast+radiation outside DOOMSDAY is unusual");

        return out;
    }
}
