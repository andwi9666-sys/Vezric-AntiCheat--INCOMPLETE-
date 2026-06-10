package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared short-lived corroboration layer for kill aura signals.
 */
public final class KillAuraAggregateUtil {

    private static final ConcurrentHashMap<UUID, Integer> BUFFER = new ConcurrentHashMap<UUID, Integer>();
    private static final ConcurrentHashMap<UUID, Long> LAST_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> FAMILY_SCORES = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>>();
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> FAMILY_LAST_MS = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>();
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> FAMILY_CAP_STATE_MS = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>();
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>> FAMILY_COOLDOWN_STATE_MS = new ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>();
    private static final ConcurrentHashMap<UUID, Long> SILENT_ESCALATION_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> SILENT_ESCALATION_STATE_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> SILENT_REARM_SUPPRESS_STATE_MS = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> SILENT_EXISTING_DROP_SUPPRESS_STATE_MS = new ConcurrentHashMap<UUID, Long>();

    private KillAuraAggregateUtil() {}

    public static void recordSignal(VezAntiCheat plugin, Player player, String source, String family, int gain, String detail) {
        if (plugin == null || player == null) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = plugin.cfg().checkLong("KillAuraE", "aggregateWindowMs", 2500L);
        long familyCooldownMs = plugin.cfg().checkLong("KillAuraE", "aggregateFamilyCooldownMs", 300L);
        int familyCap = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateFamilyMaxContribution", 4));
        int maxBuffer = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateMaxBuffer", 8));
        int candidateBuffer = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateCandidateBuffer", 2));
        long stateGapMs = Math.max(0L, plugin.cfg().checkLong("KillAuraE", "aggregateStateMinGapMs", 450L));
        long cooldownStateGapMs = Math.max(0L, plugin.cfg().checkLong("KillAuraE", "aggregateCooldownStateMinGapMs", 225L));

        Long last = LAST_MS.get(id);
        boolean withinWindow = last != null && (now - last.longValue()) <= windowMs;
        if (!withinWindow) {
            clear(player);
        }

        ConcurrentHashMap<String, Integer> scores = FAMILY_SCORES.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Integer>());
        ConcurrentHashMap<String, Long> familyLast = FAMILY_LAST_MS.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Long>());
        ConcurrentHashMap<String, Long> familyCapState = FAMILY_CAP_STATE_MS.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Long>());
        ConcurrentHashMap<String, Long> familyCooldownState = FAMILY_COOLDOWN_STATE_MS.computeIfAbsent(id, key -> new ConcurrentHashMap<String, Long>());

        String normalizedFamily = normalizeFamily(family);
        int previousBuffer = sum(scores);
        int previousFamily = get(scores, normalizedFamily);
        Long familyLastTime = familyLast.get(normalizedFamily);
        boolean familyCooldown = familyLastTime != null && (now - familyLastTime.longValue()) < familyCooldownMs;
        int appliedGain = 0;

        if (!familyCooldown) {
            appliedGain = Math.min(Math.max(1, gain), Math.max(0, familyCap - previousFamily));
            if (appliedGain > 0) {
                int nextFamily = previousFamily + appliedGain;
                scores.put(normalizedFamily, nextFamily);
                familyLast.put(normalizedFamily, now);
                if (previousFamily < familyCap
                        && nextFamily >= familyCap
                        && shouldRecordFamilyState(familyCapState, normalizedFamily, now, stateGapMs)) {
                    int total = sum(scores);
                    recordState(plugin, id,
                            "family-cap family=" + normalizedFamily
                                    + " score=" + nextFamily + "/" + familyCap
                                    + " total=" + total
                                    + " source=" + safe(source));
                }
            }
        } else if (shouldRecordFamilyState(familyCooldownState, normalizedFamily, now, cooldownStateGapMs)) {
            recordState(plugin, id,
                    "family-cooldown-suppressed family=" + normalizedFamily
                            + " cooldownLeft=" + Math.max(0L, familyCooldownMs - (now - familyLastTime.longValue()))
                            + "ms source=" + safe(source)
                            + " " + describe(plugin, id, now));
        }

        int nextBuffer = Math.min(maxBuffer, sum(scores));
        if (nextBuffer > 0) {
            BUFFER.put(id, nextBuffer);
            LAST_MS.put(id, now);
        }

        Snapshot snapshot = snapshot(plugin, id, now);
        if (snapshot == null) return;

        if (plugin.diagnostics() != null
                && previousBuffer < candidateBuffer
                && snapshot.getBuffer() >= candidateBuffer) {
            plugin.diagnostics().record(id, "KillAura", "candidate",
                    "source=" + safe(source)
                            + " family=" + normalizedFamily
                            + " gain=" + gain
                            + " applied=" + appliedGain
                            + " cooldown=" + familyCooldown
                            + " " + snapshot.describe(plugin, now)
                            + " " + sanitizeDetail(detail));
        }
    }

    public static Snapshot snapshot(VezAntiCheat plugin, UUID id, long now) {
        if (plugin == null || id == null) return null;

        long windowMs = plugin.cfg().checkLong("KillAuraE", "aggregateWindowMs", 2500L);
        Long last = LAST_MS.get(id);
        if (last == null || (now - last.longValue()) > windowMs) {
            if (last != null) {
                recordState(plugin, id, "expired " + describe(plugin, id, now));
            }
            clear(id);
            return null;
        }

        ConcurrentHashMap<String, Integer> scores = FAMILY_SCORES.get(id);
        if (scores == null || scores.isEmpty()) {
            clear(id);
            return null;
        }

        ConcurrentHashMap<String, Long> familyLast = FAMILY_LAST_MS.get(id);
        Map<String, Integer> orderedScores = new LinkedHashMap<String, Integer>();
        Map<String, Long> orderedTimes = new LinkedHashMap<String, Long>();
        List<String> families = new ArrayList<String>(scores.keySet());
        Collections.sort(families);
        for (String family : families) {
            int score = get(scores, family);
            if (score > 0) {
                orderedScores.put(family, score);
                if (familyLast != null && familyLast.containsKey(family)) {
                    orderedTimes.put(family, familyLast.get(family));
                }
            }
        }
        if (orderedScores.isEmpty()) {
            clear(id);
            return null;
        }

        return new Snapshot(sum(scores), last.longValue(),
                silentEscalationRearmLeft(plugin, id, now), orderedScores, orderedTimes);
    }

    public static boolean shouldPressure(VezAntiCheat plugin, Snapshot snapshot) {
        if (plugin == null || snapshot == null) return false;
        int minBuffer = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateBufferToPressure", 3));
        int minFamilies = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateMinFamilies", 2));
        return snapshot.getBuffer() >= minBuffer && snapshot.getFamilyScores().size() >= minFamilies;
    }

    public static boolean shouldFlagPublic(VezAntiCheat plugin, Snapshot snapshot) {
        if (plugin == null || snapshot == null) return false;
        int minBuffer = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateBufferToFlag",
                plugin.cfg().checkInt("KillAuraE", "aggregateBufferToPressure", 3)));
        int minFamilies = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateMinFamiliesToFlag",
                plugin.cfg().checkInt("KillAuraE", "aggregateMinFamilies", 2)));
        int soloBuffer = Math.max(minBuffer, plugin.cfg().checkInt("KillAuraE", "aggregateSoloBufferToFlag", minBuffer + 1));
        return (snapshot.getBuffer() >= minBuffer && snapshot.getFamilyScores().size() >= minFamilies)
                || snapshot.getBuffer() >= soloBuffer;
    }

    public static boolean shouldEscalateSilentAura(VezAntiCheat plugin, Snapshot snapshot) {
        if (plugin == null || snapshot == null) return false;
        int minBuffer = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateSilentMinBuffer", 4));
        int minFamilies = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateSilentMinFamilies", 2));
        int minAimScore = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateSilentMinAimScore", 1));
        int minSnapScore = Math.max(1, plugin.cfg().checkInt("KillAuraE", "aggregateSilentMinSnapScore", 1));
        return snapshot.getBuffer() >= minBuffer
                && snapshot.getFamilyScores().size() >= minFamilies
                && snapshot.familyScore("AIM") >= minAimScore
                && snapshot.familyScore("SNAP") >= minSnapScore;
    }

    public static SilentEscalationDecision evaluateSilentEscalation(VezAntiCheat plugin, UUID id,
                                                                    Snapshot snapshot, String existingDropReason,
                                                                    long now) {
        if (!shouldEscalateSilentAura(plugin, snapshot)) {
            return SilentEscalationDecision.notEligible();
        }
        if (plugin == null || id == null || snapshot == null) {
            return SilentEscalationDecision.notEligible();
        }

        long rearmMs = Math.max(0L, plugin.cfg().checkLong("KillAuraE", "aggregateSilentRearmMs", 325L));
        long stateGapMs = Math.max(0L, plugin.cfg().checkLong("KillAuraE", "aggregateStateMinGapMs", 450L));
        long existingDropStateGapMs = Math.max(0L, plugin.cfg().checkLong("KillAuraE", "aggregateSilentExistingDropStateMinGapMs", 225L));

        String snapshotDetail = snapshot.describe(plugin, now);
        Long lastEscalation = SILENT_ESCALATION_MS.get(id);
        long rearmLeft = 0L;
        if (lastEscalation != null) {
            rearmLeft = Math.max(0L, rearmMs - (now - lastEscalation.longValue()));
        }
        if (rearmLeft > 0L) {
            if (shouldRecordState(SILENT_REARM_SUPPRESS_STATE_MS, id, now, stateGapMs)) {
                recordState(plugin, id,
                        "silent-escalation-suppressed reason=rearm"
                                + " rearmLeft=" + rearmLeft + "ms "
                                + snapshotDetail);
            }
            return SilentEscalationDecision.suppressed("rearm", rearmLeft, snapshotDetail);
        }

        String normalizedExistingDrop = sanitizeDetail(existingDropReason);
        if (!normalizedExistingDrop.isEmpty()) {
            if (shouldRecordState(SILENT_EXISTING_DROP_SUPPRESS_STATE_MS, id, now, existingDropStateGapMs)) {
                recordState(plugin, id,
                        "silent-escalation-suppressed reason=existing-drop"
                                + " owner=" + normalizedExistingDrop + " "
                                + snapshotDetail);
            }
            return SilentEscalationDecision.suppressed("existing-drop", 0L,
                    "owner=" + normalizedExistingDrop + " " + snapshotDetail);
        }

        if (shouldRecordState(SILENT_ESCALATION_STATE_MS, id, now, stateGapMs)) {
            recordState(plugin, id, "silent-escalation " + snapshotDetail);
        }
        SILENT_ESCALATION_MS.put(id, now);
        return SilentEscalationDecision.ready(snapshotDetail);
    }

    public static long silentEscalationRearmLeft(VezAntiCheat plugin, UUID id, long now) {
        if (plugin == null || id == null) return 0L;
        Long lastEscalation = SILENT_ESCALATION_MS.get(id);
        if (lastEscalation == null) return 0L;
        long rearmMs = Math.max(0L, plugin.cfg().checkLong("KillAuraE", "aggregateSilentRearmMs", 325L));
        return Math.max(0L, rearmMs - (now - lastEscalation.longValue()));
    }

    public static String describe(VezAntiCheat plugin, UUID id, long now) {
        Snapshot snapshot = snapshot(plugin, id, now);
        return snapshot == null ? "buf=0" : snapshot.describe(plugin, now);
    }

    public static void clear(Player player) {
        if (player != null) {
            clear(player.getUniqueId());
        }
    }

    private static void clear(UUID id) {
        if (id == null) return;
        BUFFER.remove(id);
        LAST_MS.remove(id);
        FAMILY_SCORES.remove(id);
        FAMILY_LAST_MS.remove(id);
        FAMILY_CAP_STATE_MS.remove(id);
        FAMILY_COOLDOWN_STATE_MS.remove(id);
    }

    private static int sum(ConcurrentHashMap<String, Integer> scores) {
        int total = 0;
        for (Integer value : scores.values()) {
            if (value != null && value.intValue() > 0) total += value.intValue();
        }
        return total;
    }

    private static int get(ConcurrentHashMap<String, Integer> scores, String family) {
        Integer value = scores.get(family);
        return value == null ? 0 : value.intValue();
    }

    private static String normalizeFamily(String family) {
        if (family == null) return "MISC";
        String trimmed = family.trim().toUpperCase();
        return trimmed.isEmpty() ? "MISC" : trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sanitizeDetail(String detail) {
        if (detail == null) return "";
        return detail.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static void recordState(VezAntiCheat plugin, UUID id, String detail) {
        if (plugin == null || id == null || plugin.diagnostics() == null) return;
        plugin.diagnostics().record(id, "KillAura", "state", sanitizeDetail(detail));
    }

    private static boolean shouldRecordFamilyState(ConcurrentHashMap<String, Long> tracker, String family, long now, long minGapMs) {
        if (tracker == null || family == null) return false;
        Long last = tracker.get(family);
        if (last != null && (now - last.longValue()) < Math.max(0L, minGapMs)) {
            return false;
        }
        tracker.put(family, now);
        return true;
    }

    public static final class Snapshot {
        private final int buffer;
        private final long lastSignalMs;
        private final long silentRearmLeftMs;
        private final Map<String, Integer> familyScores;
        private final Map<String, Long> familyLastSignalMs;

        private Snapshot(int buffer, long lastSignalMs, long silentRearmLeftMs,
                         Map<String, Integer> familyScores,
                         Map<String, Long> familyLastSignalMs) {
            this.buffer = buffer;
            this.lastSignalMs = lastSignalMs;
            this.silentRearmLeftMs = Math.max(0L, silentRearmLeftMs);
            this.familyScores = familyScores;
            this.familyLastSignalMs = familyLastSignalMs;
        }

        public int getBuffer() { return buffer; }
        public long getLastSignalMs() { return lastSignalMs; }
        public long getSilentRearmLeftMs() { return silentRearmLeftMs; }
        public Map<String, Integer> getFamilyScores() { return familyScores; }
        public Map<String, Long> getFamilyLastSignalMs() { return familyLastSignalMs; }

        public int familyScore(String family) {
            if (family == null) return 0;
            Integer value = familyScores.get(family.trim().toUpperCase());
            return value == null ? 0 : value.intValue();
        }

        public String describe(long nowMs) {
            return describe(null, nowMs);
        }

        public String describe(VezAntiCheat plugin, long nowMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("buf=").append(buffer);
            if (lastSignalMs > 0L && nowMs >= lastSignalMs) {
                sb.append(" age=").append(nowMs - lastSignalMs).append("ms");
            }
            if (plugin != null) {
                sb.append(" pressure=").append(shouldPressure(plugin, this) ? "Y" : "N");
                sb.append(" flag=").append(shouldFlagPublic(plugin, this) ? "Y" : "N");
                sb.append(" silent=").append(shouldEscalateSilentAura(plugin, this) ? "Y" : "N");
                if (silentRearmLeftMs > 0L) {
                    sb.append(" silentRearmLeft=").append(silentRearmLeftMs).append("ms");
                }
            }
            sb.append(" families=");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : familyScores.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(entry.getKey()).append(":").append(entry.getValue());
                Long familyMs = familyLastSignalMs.get(entry.getKey());
                if (familyMs != null && nowMs >= familyMs.longValue()) {
                    sb.append("@").append(nowMs - familyMs.longValue()).append("ms");
                }
            }
            return sb.toString();
        }
    }

    private static boolean shouldRecordState(ConcurrentHashMap<UUID, Long> tracker, UUID id, long now, long minGapMs) {
        if (tracker == null || id == null) return false;
        Long last = tracker.get(id);
        if (last != null && (now - last.longValue()) < Math.max(0L, minGapMs)) {
            return false;
        }
        tracker.put(id, now);
        return true;
    }

    public static final class SilentEscalationDecision {
        private final boolean shouldBlock;
        private final boolean suppressed;
        private final String reason;
        private final long rearmLeftMs;
        private final String detail;

        private SilentEscalationDecision(boolean shouldBlock, boolean suppressed, String reason,
                                         long rearmLeftMs, String detail) {
            this.shouldBlock = shouldBlock;
            this.suppressed = suppressed;
            this.reason = reason;
            this.rearmLeftMs = rearmLeftMs;
            this.detail = detail;
        }

        public static SilentEscalationDecision ready(String detail) {
            return new SilentEscalationDecision(true, false, "", 0L, detail);
        }

        public static SilentEscalationDecision suppressed(String reason, long rearmLeftMs, String detail) {
            return new SilentEscalationDecision(false, true, reason, rearmLeftMs, detail);
        }

        public static SilentEscalationDecision notEligible() {
            return new SilentEscalationDecision(false, false, "", 0L, "");
        }

        public boolean shouldBlock() { return shouldBlock; }
        public boolean isSuppressed() { return suppressed; }
        public String getReason() { return reason; }
        public long getRearmLeftMs() { return rearmLeftMs; }
        public String getDetail() { return detail; }
    }
}
