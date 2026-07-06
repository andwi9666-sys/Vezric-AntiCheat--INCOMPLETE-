package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.LagProfileUtil;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.utils.PrismCheckLabels;
import com.colin.vezanticheat.verdict.CheckVLStore;
import com.colin.vezanticheat.verdict.CombatMitigationPolicy;
import com.colin.vezanticheat.verdict.MitigationPolicy;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import com.colin.vezanticheat.verdict.TierPunishmentExecutor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base for Polar-tier checks. Each check owns its VL pool and punish threshold.
 */
public abstract class TierCheck {

    protected final VezAntiCheat plugin;
    private final String name;
    private final CheckTier tier;

    private static final ConcurrentHashMap<String, Integer> BUFFERS = new ConcurrentHashMap<String, Integer>();
    private static final ConcurrentHashMap<String, Long> LAST_FLAG_MS = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, Long> LAST_DECAY_MS = new ConcurrentHashMap<String, Long>();

    protected TierCheck(VezAntiCheat plugin, String name, CheckTier tier) {
        this.plugin = plugin;
        this.name = name;
        this.tier = tier;
    }

    public final String name() { return name; }
    public final CheckTier tier() { return tier; }
    protected final VezAntiCheat plugin() { return plugin; }
    public final VezAntiCheat pluginRef() { return plugin; }

    public final String publicName() {
        return PrismCheckLabels.polarCheckName(name, tier);
    }

    public final String vlPoolName() {
        return PrismCheckLabels.vlPoolName(name);
    }

    public boolean enabled() {
        // Aim heuristics (CharAim* / CharSilentAim) default ON — the silent-aim rewrite detects the
        // injected-rotation fingerprint and requires multiple independent signals before flagging.
        // Enforcement is still governed by punish.safety-mode and each check's combatMitigation/shadow
        // flags, so detection-on does NOT auto-ban. Set combat.aim-heuristics-enabled: false to disable.
        if (isAimCharacteristicCheck()
                && !plugin.getConfig().getBoolean("combat.aim-heuristics-enabled", true)) {
            return false;
        }
        return plugin.tierCfg().checkEnabled(name);
    }

    public boolean shadowEnabled() {
        return plugin.tierCfg().checkBoolean(name, "shadow", false);
    }

    public double punishVl() {
        return plugin.tierCfg().checkDouble(name, "punishVl", tier.defaultPunishVl);
    }

    public double failWeight() {
        return plugin.tierCfg().checkDouble(name, "failWeight", tier.defaultFailWeight);
    }

    public int bufferToFlag() {
        return plugin.tierCfg().checkInt(name, "bufferToFlag", defaultBufferToFlag());
    }

    public double decayPerSecond() {
        return plugin.tierCfg().checkDouble(name, "decay", 0.15D);
    }

    public boolean setbackEnabled() {
        return plugin.tierCfg().checkBoolean(name, "setbackEnabled", tier.defaultSetback);
    }

    protected int defaultBufferToFlag() {
        switch (tier) {
            case CHARACTERISTICS: return 4;
            case PRISM: return 3;
            case SIMULATION: return 5;
            case PREDICTION: return 7;
            default: return 3;
        }
    }

    protected CheckVLStore vlStore() { return plugin.tierChecks().vlStore(); }
    protected TierPunishmentExecutor punisher() { return plugin.tierChecks().punisher(); }
    protected MitigationPolicy mitigation() { return plugin.tierChecks().mitigation(); }

    /**
     * Remove all per-check buffer/decay state for one player. Call on quit/reload so the static
     * maps keyed "UUID:checkName" do not grow without bound. Matches keys by the "{uuid}:" prefix
     * so every check's entry for that player is dropped in one pass.
     */
    public static void clearPlayer(UUID id) {
        if (id == null) return;
        String prefix = id.toString() + ":";
        removeByPrefix(BUFFERS, prefix);
        removeByPrefix(LAST_FLAG_MS, prefix);
        removeByPrefix(LAST_DECAY_MS, prefix);
    }

    /** Drop all buffered/decay state for every player (e.g. on /vez reload). */
    public static void clearAll() {
        BUFFERS.clear();
        LAST_FLAG_MS.clear();
        LAST_DECAY_MS.clear();
    }

    private static void removeByPrefix(ConcurrentHashMap<String, ?> map, String prefix) {
        if (map.isEmpty()) return;
        for (java.util.Iterator<String> it = map.keySet().iterator(); it.hasNext();) {
            String key = it.next();
            if (key != null && key.startsWith(prefix)) {
                it.remove();
            }
        }
    }

    private String bufferKey(UUID id) { return id + ":" + name; }

    protected int buffer(UUID id) {
        Integer v = BUFFERS.get(bufferKey(id));
        return v == null ? 0 : v.intValue();
    }

    protected void setBuffer(UUID id, int value) {
        String key = bufferKey(id);
        if (value <= 0) BUFFERS.remove(key);
        else BUFFERS.put(key, value);
    }

    protected boolean incrementBuffer(Player p, int gain) {
        if (p == null) return false;
        UUID id = p.getUniqueId();
        int next = buffer(id) + Math.max(1, gain);
        setBuffer(id, next);
        return next >= bufferToFlag();
    }

    protected void coolBuffer(Player p, int amount) {
        if (p == null) return;
        setBuffer(p.getUniqueId(), Math.max(0, buffer(p.getUniqueId()) - Math.max(1, amount)));
    }

    protected void resetBuffer(Player p) {
        if (p == null) return;
        setBuffer(p.getUniqueId(), 0);
    }

    protected void fail(Player p, PlayerData data, double addVl, String debug) {
        fail(p, data, addVl, debug, true);
    }

    protected void fail(Player p, PlayerData data, double addVl, String debug, boolean applyMitigation) {
        if (p == null || data == null) return;
        if (!plugin.tierCfg().enabled()) return;
        if (!enabled()) return;
        if (PlayerData.bypass(p)) return;

        // Bedrock touch/controller aim breaks Java mouse heuristics (GCD lattice,
        // snap/reset patterns) — drop aim-characteristic flags for Geyser players.
        if (isAimCharacteristicCheck()
                && com.colin.vezanticheat.utils.ClientCompatUtil.isAimExempt(plugin, p, data)) {
            return;
        }

        if (plugin.tierCfg().gateByLag()) {
            double tps = plugin.tps() != null ? plugin.tps().getTps() : 20.0D;
            if (tps < plugin.tierCfg().minTps()) return;
            int ping = PingUtil.getPing(p);
            if (ping > 0 && ping > plugin.tierCfg().maxPing()) return;
        }

        if (data.isTeleportExempt()) return;

        long now = System.currentTimeMillis();
        double add = Math.max(0.25D, addVl * failWeight());
        double lagScore = LagProfileUtil.activeScore(plugin, p, data, now);
        if (lagScore >= plugin.getConfig().getDouble("lag.profile.boost-start-score", 1.25D)) {
            double boostFactor = plugin.getConfig().getDouble("lag.profile.boost-factor", 0.18D);
            double maxBoost = plugin.getConfig().getDouble("lag.profile.max-boost", 0.75D);
            add *= (1.0D + Math.min(maxBoost, lagScore * boostFactor));
            debug = LagProfileUtil.appendDebug(debug, plugin, p, data, now);
        }

        if (plugin.flagStats() != null) {
            plugin.flagStats().record(name, p.getUniqueId(), PingUtil.getPing(p),
                    plugin.tps() != null ? plugin.tps().getTps() : -1.0D, shadowEnabled());
        }

        if (shadowEnabled()) {
            if (plugin.riskScore() != null) {
                plugin.riskScore().recordFlag(p, data, name, tier.name(), add, debug, true);
            }
            return;
        }

        String vlKey = vlPoolName();
        double checkVl = vlStore().addVl(p.getUniqueId(), vlKey, add, now);
        data.addTotalVl((int) Math.max(1, Math.round(add)));
        data.recordPolarFlag(publicName(), PrismCheckLabels.polarDataTier(tier), debug, now);

        if (plugin.riskScore() != null) {
            plugin.riskScore().recordFlag(p, data, vlKey, tier.name(), checkVl, debug, false);
        }
        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), name, "flag", debug);
        }

        // punish.safety-mode decides how far a flag may act: silent records only,
        // alerts-only adds staff chat, mitigation adds setbacks, banwave/instant add bans.
        com.colin.vezanticheat.punishment.PunishmentMode mode = plugin.cfg().punishSafetyMode();

        if (mode.alertsEnabled()) {
            plugin.tierChecks().flagToStaff(p, publicName(), tier, (int) Math.round(checkVl), debug);
            verboseFlag(p, Math.round(checkVl), debug);
        }

        String decayKey = bufferKey(p.getUniqueId());
        LAST_FLAG_MS.put(decayKey, now);
        LAST_DECAY_MS.put(decayKey, now);

        if (applyMitigation && mode.mitigationEnabled()) {
            applyFlagMitigation(p, data, debug, PrismMitigationPolicy.Confidence.MODERATE);
        }

        punisher().evaluateBan(p, data, this, checkVl, debug);
    }

    private boolean isAimCharacteristicCheck() {
        return name.startsWith("CharAim") || name.equals("CharSilentAim");
    }

    /** Bedrock-scaled flag buffer for scaffold checks (compat.bedrock.scaffold-buffer-multiplier). */
    protected int bedrockAdjustedBuffer(Player p, PlayerData data, int base) {
        return com.colin.vezanticheat.utils.ClientCompatUtil.scaledScaffoldBuffer(plugin, p, data, base);
    }

    private void applyFlagMitigation(Player p, PlayerData data, String debug,
                                     PrismMitigationPolicy.Confidence confidence) {
        if (CombatMitigationPolicy.isCombatPlayerHitCheck(plugin, name)
                && plugin.getConfig().getBoolean("combat-mitigation.enabled", true)) {
            Entity target = CombatMitigationPolicy.resolveAttackTarget(p, data);
            CombatMitigationPolicy.apply(plugin, p, data, target, name, debug, confidence);
            return;
        }
        if (!setbackEnabled()) return;
        if (tier == CheckTier.PRISM) {
            PrismMitigationPolicy.apply(plugin, p, data, name, confidence, debug);
        } else {
            mitigation().maybeSetback(plugin, p, data, tier, name, debug);
        }
    }

    protected void decay(Player p, double amount) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        String key = bufferKey(id);
        long now = System.currentTimeMillis();
        long grace = plugin.getConfig().getLong("tier.vl-decay-grace-ms", 15000L);
        Long lastFlag = LAST_FLAG_MS.get(key);
        if (lastFlag != null && now - lastFlag < grace) return;

        Long lastDecay = LAST_DECAY_MS.get(key);
        if (lastDecay == null) lastDecay = now;
        double seconds = Math.max(0.05D, (now - lastDecay) / 1000.0D);
        LAST_DECAY_MS.put(key, now);
        vlStore().decay(id, vlPoolName(), amount * seconds * decayPerSecond(), now);
    }

    protected void verbose(Player p, String msg) {
        plugin.tierChecks().verboseToStaff(p.getName(), name, msg);
    }

    /** Verbose flag line for staff; debug suffix requires staff debug mode. */
    protected void verboseFlag(Player p, long checkVl, String debug) {
        if (p == null) return;
        plugin.tierChecks().verboseFlagToStaff(p, publicName(), tier, checkVl, debug);
    }

    protected void blockAttack(Player p, PlayerData data, String reason) {
        if (p == null || data == null) return;
        data.setBlockCurrentAttackPacket(true);
        data.setBlockedAttackReason(reason);
        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), name, "packet-drop", reason);
        }
    }

    protected void failWithMitigation(Player p, PlayerData data, double addVl, String debug,
                                      PrismMitigationPolicy.Confidence confidence) {
        if (p == null || data == null) return;
        applyFlagMitigation(p, data, debug, confidence);
        fail(p, data, addVl, debug, false);
    }

    protected void blockDig(PlayerData data, String reason) {
        if (data == null) return;
        data.setBlockCurrentDigPacket(true);
    }

    protected void blockPlace(PlayerData data, String reason) {
        if (data == null) return;
        data.setBlockCurrentPlacePacket(true);
    }

    protected void blockMovement(PlayerData data, String reason) {
        mitigation().blockMovement(data, reason);
    }

    protected boolean lagGated(Player p, PlayerData data) {
        if (p == null) return true;
        if (!plugin.tierCfg().gateByLag()) return false;
        double tps = plugin.tps() != null ? plugin.tps().getTps() : 20.0D;
        if (tps < plugin.tierCfg().minTps()) return true;
        int ping = PingUtil.getPing(p);
        return ping > 0 && ping > plugin.tierCfg().maxPing();
    }

    protected static double round3(double v) {
        return Math.round(v * 1000.0D) / 1000.0D;
    }

    // --- Event hooks (override in subclasses) ---
    public void onMove(Player p, PlayerData data) {}
    public void onAttack(Player p, PlayerData data) {}
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {}
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {}
    public void onArmSwing(Player p, PlayerData data) {}
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, Entity target) {}
    public void onDigging(Player p, PlayerData data, com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType action,
                          org.bukkit.block.Block block) {}
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId,
                                   float cursorX, float cursorY, float cursorZ) {}
    public void onWindowConfirmation(Player p, PlayerData data, short actionId, long nowMs) {}
    public void onInventoryAction(Player p, PlayerData data) {}
    public void onBlockBreak(Player p, PlayerData data, org.bukkit.block.Block block) {}
    public void onDigStart(Player p, PlayerData data, org.bukkit.block.Block block) {}
    public void onEntityAction(Player p, PlayerData data, String actionName) {}
    public void onHeldItemChange(Player p, PlayerData data, int slot) {}
    public void onUseItem(Player p, PlayerData data) {}
    public void onCloseInventory(Player p, PlayerData data) {}
    public void onWindowClick(Player p, PlayerData data, int windowId, int slot) {}
    public void onEngineResult(Player p, PlayerData data, com.colin.vezanticheat.engine.EngineResult result, long nowMs) {}
}
