package com.colin.vezanticheat.utils;

import java.util.Locale;

/**
 * Maps raw check names/categories to the public labels shown to staff and punishment systems.
 */
public final class CheckAliasUtil {

    private CheckAliasUtil() {}

    public static String displayName(String checkName, String category) {
        if (isAntiKbPrediction(checkName)) {
            return "AntiKB";
        }
        if (isTimerCheck(checkName, category)) {
            return "Timer";
        }
        if (isKillAuraCheck(checkName, category)) {
            return "KillAura";
        }
        if (isAutoBlockCheck(checkName, category)) {
            return "AutoBlock";
        }
        if (isSimulationCheck(checkName, category)) {
            return "Simulation";
        }
        return checkName == null ? "Unknown" : checkName;
    }

    public static String displayCategory(String checkName, String category) {
        if (isAntiKbPrediction(checkName)) {
            return "ANTIKB";
        }
        if (isTimerCheck(checkName, category)) {
            return "TIMER";
        }
        if (isSimulationCheck(checkName, category)) {
            return "SIMULATION";
        }
        return normalize(category == null ? "MISC" : category);
    }

    private static boolean isAntiKbPrediction(String checkName) {
        return "VelocityPrediction".equalsIgnoreCase(checkName);
    }

    private static boolean isSimulationCheck(String checkName, String category) {
        String normalizedCategory = normalize(category);
        if (normalizedCategory.equals("SPEED")
                || normalizedCategory.equals("STEP")
                || normalizedCategory.equals("FLY")
                || normalizedCategory.equals("GROUNDSPOOF")
                || normalizedCategory.equals("PHASE")
                || normalizedCategory.equals("NOSLOW")
                || normalizedCategory.equals("SIMULATION")) {
            return true;
        }

        String normalizedName = normalize(checkName);
        return normalizedName.startsWith("SPEED")
                || normalizedName.startsWith("STEP")
                || normalizedName.startsWith("FLY")
                || normalizedName.startsWith("GROUNDSPOOF")
                || normalizedName.startsWith("PHASE")
                || normalizedName.startsWith("NOSLOW")
                || normalizedName.startsWith("OFFSET")
                || normalizedName.startsWith("SIMULATION");
    }

    private static boolean isTimerCheck(String checkName, String category) {
        String normalizedCategory = normalize(category);
        if (normalizedCategory.equals("TIMER")) {
            return true;
        }

        String normalizedName = normalize(checkName);
        return normalizedName.startsWith("TIMER");
    }

    private static boolean isKillAuraCheck(String checkName, String category) {
        String normalizedCategory = normalize(category);
        if (normalizedCategory.equals("KILLAURA")) {
            return true;
        }

        String normalizedName = normalize(checkName);
        return normalizedName.startsWith("KILLAURA");
    }

    private static boolean isAutoBlockCheck(String checkName, String category) {
        String normalizedCategory = normalize(category);
        if (normalizedCategory.equals("AUTOBLOCK")) {
            return true;
        }

        String normalizedName = normalize(checkName);
        return normalizedName.startsWith("AUTOBLOCK");
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
