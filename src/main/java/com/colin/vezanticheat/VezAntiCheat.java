package com.colin.vezanticheat;

import com.colin.vezanticheat.banwave.BanwaveManager;
import com.colin.vezanticheat.tier.TierCheckManager;
import com.colin.vezanticheat.utils.TierConfigManager;
import com.colin.vezanticheat.commands.AlertsCommand;
import com.colin.vezanticheat.commands.BanwaveCommand;
import com.colin.vezanticheat.commands.FlagsCommand;
import com.colin.vezanticheat.ai.EvidenceManager;
import com.colin.vezanticheat.ai.RiskScoreManager;
import com.colin.vezanticheat.commands.CommandRegistrar;
import com.colin.vezanticheat.commands.VezCommand;
import com.colin.vezanticheat.integrations.VulcanCompat;
import com.colin.vezanticheat.listeners.ClientBrandListener;
import com.colin.vezanticheat.listeners.CommandFallbackListener;
import com.colin.vezanticheat.listeners.FlagsGuiListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.colin.vezanticheat.data.PlayerDataManager;
import com.colin.vezanticheat.listeners.PacketListener;
import com.colin.vezanticheat.listeners.PlayerListener;
import com.colin.vezanticheat.prediction.PredictionProcessor;
import com.colin.vezanticheat.punishment.PunishmentManager;
import com.colin.vezanticheat.staff.PolarFlagHistory;
import com.colin.vezanticheat.utils.ConfigManager;
import com.colin.vezanticheat.utils.DiagnosticsTracker;
import com.colin.vezanticheat.utils.LegacyKbBridge;
import com.colin.vezanticheat.utils.TpsMonitor;
import com.colin.vezanticheat.velocity.VelocityProcessor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * VezAntiCheat — Main plugin class and dependency injection root.
 *
 * Architecture Overview:
 * =====================
 * This anticheat uses a Grim-style prediction-engine approach. As of Phase 2A the engine is THE
 * movement speed authority:
 * 1. PacketEvents intercepts raw client packets (position, rotation, interaction)
 * 2. Per-player state is tracked in PlayerData (velocity, position history, potion effects,
 *    serverGround truth, offset-advantage accumulator, persistent clock-drift ledger)
 * 3. MovementCheckRunner (engine) simulates the legal next velocity and computes a reduced offset
 * 4. Actual movement is compared against the prediction; sub-threshold offset accrues into a
 *    slow-decaying long-window advantage so sustained tiny speed cannot hide
 * 5. Deviations accumulate through a 3-layer detection system (prefilter → buffer → mitigation)
 *    and enforce via utils/MovementEnforcement (engine-aware setback + circuit breaker)
 *
 * The legacy heuristic PredictionProcessor.handleMovement is DEMOTED to a dead-by-default
 * emergency kill-switch (engine.skip-legacy-movement-prediction=false re-enables it). When the
 * engine is authoritative, PredictionProcessor only bridges the velocity (knockback) session.
 *
 * Component Hierarchy:
 * ====================
 * VezAntiCheat (this class) — initializes and wires all components
 *  ├── PacketListener — intercepts packets via PacketEvents API, dispatches to checks + engine
 *  ├── PlayerListener — handles Bukkit events (join, quit, teleport, damage, inventory)
 *  ├── PlayerDataManager — creates/destroys per-player state containers
 *  ├── TierCheckManager — Polar four-tier check registration and dispatch
 *  ├── MovementCheckRunner (engine) — Grim-style offset prediction; SOLE speed authority
 *  ├── PredictionProcessor — velocity-session bridge + dead-by-default kill-switch fallback
 *  ├── SimulationCheck — 3rd-gen envelope-based movement prediction
 *  ├── ConfigManager — typed access to config.yml values
 *  ├── TpsMonitor — tracks server TPS for lag compensation
 *  ├── PunishmentManager — VL-based punishment ladder (warn → kick → ban)
 *  ├── BanwaveManager — deferred batch bans
 *  └── DiagnosticsTracker — performance metrics
 *
 * Packet Flow:
 * ============
 * Client sends packet → PacketEvents intercepts → PacketListener.onPacketReceive()
 *   → Updates PlayerData (position, rotation, timing, serverGround, clock ledger)
 *   → Runs MovementCheckRunner.onMovement (engine — authoritative speed validation)
 *   → Calls TierCheckManager dispatch (Characteristics → Prism → Simulation → Prediction)
 *   → Individual checks read PlayerData + EngineResult and flag violations
 *   → Violations enforce via utils/MovementEnforcement and accumulate VL → PunishmentManager
 *
 * Why PacketEvents (not ProtocolLib):
 * ===================================
 * - More reliable packet interception on modern server software
 * - Better API for reading/writing packet fields (typed wrappers)
 * - Active development and 1.8-1.21 support
 * - Proper Netty pipeline injection for async packet processing
 */
public class VezAntiCheat extends JavaPlugin {

    // Core managers — each handles one responsibility
    private PlayerDataManager dataManager;       // Per-player state lifecycle
    private TierConfigManager tierConfigManager; // Polar tier YAML configs
    private TierCheckManager tierCheckManager;   // Tier check registration and dispatch
    private PunishmentManager punishmentManager; // VL thresholds → actions (kick/ban)
    private ConfigManager configManager;         // Typed config access with defaults
    private BanwaveManager banwaveManager;       // Deferred batch ban queue
    private TpsMonitor tpsMonitor;               // Server TPS tracking for lag gates
    private PacketListener packetListener;       // PacketEvents listener (packet → check pipeline)
    private PredictionProcessor predictionProcessor; // Heuristic + simulation movement check
    private VelocityProcessor velocityProcessor;     // Dedicated knockback validation
    private DiagnosticsTracker diagnosticsTracker;   // Performance metrics
    private EvidenceManager evidenceManager;
    private RiskScoreManager riskScoreManager;
    private com.colin.vezanticheat.engine.MovementCheckRunner movementEngine; // Grim-style offset prediction engine
    private com.colin.vezanticheat.engine.PacketWorldReader packetWorldReader; // packet block-change overlay
    private com.colin.vezanticheat.engine.KnockbackHandler knockbackHandler;   // packet knockback capture
    private com.colin.vezanticheat.engine.TransactionTracker transactionTracker;   // transaction ping rewind
    private com.colin.vezanticheat.engine.EntityTrackerListener entityTracker;     // packet-synced entity positions
    private PolarFlagHistory polarFlagHistory;
    private com.colin.vezanticheat.combat.CombatAnalyzer combatAnalyzer;
    private com.colin.vezanticheat.combat.CombatAnalysisSettings combatSettings;
    private com.colin.vezanticheat.combat.CombatStaffAlerter combatAlerter;
    private ClientBrandListener clientBrandListener;
    private volatile boolean packetHooksRegistered;
    private int vlDecayTaskId = -1; // repeating scheduled global VL decay sweep
    private com.colin.vezanticheat.utils.PerfSampler perfSampler;
    private com.colin.vezanticheat.utils.ConfigProfileManager configProfileManager;
    private com.colin.vezanticheat.license.LicenseManager licenseManager;
    private com.colin.vezanticheat.license.UpdateChecker updateChecker;

    // Whether packet interception is active (always true with PacketEvents)
    private boolean protocolLib;
    private VulcanCompat.Settings vulcanCompat;

    /**
     * Main initialization. Order matters:
     * 1. Verify external PacketEvents plugin (we do not init/terminate it — see ensurePacketEventsReady)
     * 2. Config (needed by all other components)
     * 3. TPS monitor (needed for lag compensation in checks)
     * 4. Data manager (must exist before packet listener creates PlayerData)
     * 5. Punishment/banwave (independent systems)
     * 6. Check manager (registers all checks)
     * 7. Prediction/simulation (physics engines)
     * 8. Listeners (start receiving events only after everything is ready)
     * 9. Commands
     */
    @Override
    public void onEnable() {
        if (!ensurePacketEventsReady()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Config management with auto-regeneration on version bump
        saveDefaultConfig();
        int currentConfigVersion = 17; // v17: v1.1.0 license/perf/profile keys
        if (getConfig().getInt("config-version", 0) < currentConfigVersion) {
            getLogger().info("Config outdated (version " + getConfig().getInt("config-version", 0)
                    + " < " + currentConfigVersion + "). Regenerating with new defaults.");
            java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                configFile.renameTo(new java.io.File(getDataFolder(), "config.yml.old"));
            }
            java.io.File checksFile = new java.io.File(getDataFolder(), "checks.yml");
            if (checksFile.exists()) {
                checksFile.renameTo(new java.io.File(getDataFolder(), "checks.yml.legacy"));
            }
            saveDefaultConfig();
            saveResource("checks.yml.legacy", false);
            saveTierResources();
            reloadConfig();
        } else {
            java.io.File tiersDir = new java.io.File(getDataFolder(), "tiers");
            if (!tiersDir.exists()) {
                saveTierResources();
            }
        }

        // Initialize core systems in dependency order
        this.configManager = new ConfigManager(this);
        this.perfSampler = new com.colin.vezanticheat.utils.PerfSampler(
                getConfig().getBoolean("diagnostics.perf-sampling-enabled", false));
        this.configProfileManager = new com.colin.vezanticheat.utils.ConfigProfileManager(this);
        this.licenseManager = new com.colin.vezanticheat.license.LicenseManager(this);
        this.updateChecker = new com.colin.vezanticheat.license.UpdateChecker(this);
        this.tierConfigManager = new TierConfigManager(this);
        this.tpsMonitor = new TpsMonitor();
        this.tpsMonitor.start(this);
        this.dataManager = new PlayerDataManager(this);
        this.punishmentManager = new PunishmentManager(this);
        this.punishmentManager.startAnnouncementTask();
        this.banwaveManager = new BanwaveManager(this);
        this.banwaveManager.startAutoTask();
        this.tierCheckManager = new TierCheckManager(this);
        this.licenseManager.initialize();
        this.updateChecker.checkAsyncIfEnabled();
        this.polarFlagHistory = new PolarFlagHistory();
        this.combatAnalyzer = new com.colin.vezanticheat.combat.CombatAnalyzer();
        this.combatAnalyzer.getConfig().loadFrom(getConfig());
        this.combatSettings = com.colin.vezanticheat.combat.CombatAnalysisSettings.fromPlugin(this);
        this.combatSettings.applyTo(this.combatAnalyzer, getConfig());
        this.combatAlerter = new com.colin.vezanticheat.combat.CombatStaffAlerter(this);

        VezCommand vezCommand = new VezCommand(this);
        AlertsCommand alertsCommand = new AlertsCommand(this);
        FlagsCommand flagsCommand = new FlagsCommand(this);
        BanwaveCommand banwaveCommand = new BanwaveCommand(this);
        CommandRegistrar.bind(this, vezCommand, alertsCommand, flagsCommand, banwaveCommand);
        Bukkit.getPluginManager().registerEvents(
                new CommandFallbackListener(vezCommand, alertsCommand, banwaveCommand, flagsCommand),
                this
        );

        // Physics engines: PredictionProcessor is the hybrid heuristic+simulation system,
        // SimulationCheck is the new 3rd-gen pure envelope-based predictor
        this.predictionProcessor = new PredictionProcessor(this);
        this.velocityProcessor = new VelocityProcessor(this);
        this.diagnosticsTracker = new DiagnosticsTracker(24);
        this.evidenceManager = new EvidenceManager(this);
        this.riskScoreManager = new RiskScoreManager(this, evidenceManager);

        // Grim-style offset prediction engine (primary movement validator).
        this.movementEngine = new com.colin.vezanticheat.engine.MovementCheckRunner(this);

        // Register Bukkit event listener (handles events PacketEvents doesn't cover:
        // join/quit, teleport, respawn, world change, block place/break, inventory, damage)
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.colin.vezanticheat.combat.CombatListener(combatAnalyzer), this);
        this.clientBrandListener = new ClientBrandListener(this);
        Bukkit.getPluginManager().registerEvents(clientBrandListener, this);
        clientBrandListener.registerChannels();
        Bukkit.getPluginManager().registerEvents(new FlagsGuiListener(), this);

        this.vulcanCompat = VulcanCompat.resolve(this);
        if (vulcanCompat.active) {
            getLogger().info("Vulcan coexistence active — PacketEvents hooks deferred, "
                    + "receive priority=" + vulcanCompat.receivePriority.name()
                    + ", combat transactions="
                    + (vulcanCompat.disableTransactions ? "off" : "on")
                    + ", transaction listener="
                    + (vulcanCompat.skipTransactionListener ? "skipped" : "on") + ".");
            if (vulcanCompat.disableTransactions) {
                getConfig().set("combat-engine.transactions", false);
            }
        }

        this.protocolLib = true;
        PacketListenerPriority receivePriority = vulcanCompat.active
                ? vulcanCompat.receivePriority
                : PacketListenerPriority.NORMAL;

        try {
            this.packetListener = new PacketListener(this, dataManager, tierCheckManager, receivePriority);
            this.packetWorldReader = new com.colin.vezanticheat.engine.PacketWorldReader(this, dataManager);
            this.knockbackHandler = new com.colin.vezanticheat.engine.KnockbackHandler(this, dataManager);
            this.transactionTracker = new com.colin.vezanticheat.engine.TransactionTracker(this, dataManager);
            this.entityTracker = new com.colin.vezanticheat.engine.EntityTrackerListener(this, dataManager);

            Runnable registerPacketHooks = this::registerPacketListeners;
            if (vulcanCompat.active && vulcanCompat.hookDelayTicks > 0L) {
                Bukkit.getScheduler().runTaskLater(this, registerPacketHooks, vulcanCompat.hookDelayTicks);
            } else {
                registerPacketHooks.run();
            }
        } catch (LinkageError e) {
            logPacketInitFailure(e, true);
        } catch (Throwable t) {
            logPacketInitFailure(t, false);
        }

        // LegacyKB bridge: reads custom knockback profile from spigot.yml or paper config
        // if the server uses modified KB values (common on practice servers)
        if (LegacyKbBridge.isAvailable()) {
            LegacyKbBridge.KnockbackProfile profile = LegacyKbBridge.readProfile();
            if (profile != null) {
                getLogger().info("Hooked LegacyKB directly: h=" + profile.horizontal
                        + " v=" + profile.vertical
                        + " vLimit=" + profile.verticalLimit
                        + " extraH=" + profile.extraHorizontal
                        + " extraV=" + profile.extraVertical);
            }
        } else {
            getLogger().info("LegacyKB not detected. Using local knockback-profile fallback.");
        }

        startDecayTask();

        getLogger().info("VezAntiCheat enabled. PacketEvents=true tierChecks="
                + tierCheckManager.count()
                + " packetHooks=" + packetHooksRegistered
                + (vulcanCompat.active ? " vulcanCoexist=true" : ""));
        if (!packetHooksRegistered) {
            getLogger().warning("Anticheat checks are inactive until packet hooks register. "
                    + "Run /vez status and perform a full server restart (not /plugman reload).");
        }
    }

    private void logPacketInitFailure(Throwable t, boolean likelyReload) {
        this.packetHooksRegistered = false;
        if (likelyReload) {
            getLogger().severe("Packet hook init failed — likely caused by /plugman reload or /reload.");
            getLogger().severe("Stop the server completely and start it again. Do NOT hot-reload VezAntiCheat.");
        } else {
            getLogger().severe("Packet hook init failed — anticheat checks will not run until this is fixed.");
        }
        getLogger().log(java.util.logging.Level.SEVERE,
                "Packet init error: " + t.getClass().getSimpleName() + " - " + t.getMessage(), t);
    }

    private void saveTierResources() {
        saveResource("tiers/characteristics.yml", false);
        saveResource("tiers/prism.yml", false);
        saveResource("tiers/simulation.yml", false);
        saveResource("tiers/prediction.yml", false);
    }

    private void registerPacketListeners() {
        if (!isEnabled()) return;

        try {
            this.packetListener.hook();
            getLogger().info("PacketEvents packet listener registered (priority="
                    + (vulcanCompat != null && vulcanCompat.active
                    ? vulcanCompat.receivePriority.name() : PacketListenerPriority.NORMAL.name()) + ").");

            this.packetWorldReader.hook();
            this.knockbackHandler.hook();

            boolean skipTransactionListener = vulcanCompat != null && vulcanCompat.active
                    && vulcanCompat.skipTransactionListener;
            if (!skipTransactionListener) {
                this.transactionTracker.hook();
            } else {
                getLogger().info("TransactionTracker receive hook skipped (Vulcan owns window confirmations).");
            }
            this.transactionTracker.start();

            this.entityTracker.hook();
            this.packetHooksRegistered = true;
            getLogger().info("Packet hooks registered: true");
        } catch (LinkageError e) {
            logPacketInitFailure(e, true);
        } catch (Throwable t) {
            logPacketInitFailure(t, false);
        }
    }

    /**
     * Cleanup: unhook our listeners and save data. The PacketEvents plugin owns API lifecycle.
     */
    @Override
    public void onDisable() {
        packetHooksRegistered = false;
        stopDecayTask();
        if (packetListener != null) packetListener.unhook();
        if (packetWorldReader != null) packetWorldReader.unhook();
        if (knockbackHandler != null) knockbackHandler.unhook();
        if (transactionTracker != null) transactionTracker.unhook();
        if (entityTracker != null) entityTracker.unhook();
        if (clientBrandListener != null) clientBrandListener.unregisterChannels();
        if (dataManager != null) dataManager.shutdown();
    }

    /**
     * When {@code packetevents} is a separate server plugin (plugin.yml {@code depend}), only that
     * plugin may call {@code setAPI}/{@code load}/{@code init}/{@code terminate}. A second init here
     * causes "Failed to inject into a channel" kicks on join.
     */
    private boolean ensurePacketEventsReady() {
        org.bukkit.plugin.Plugin packetEventsPlugin = getServer().getPluginManager().getPlugin("packetevents");
        if (packetEventsPlugin == null || !packetEventsPlugin.isEnabled()) {
            getLogger().severe("PacketEvents is required. Install the packetevents-spigot plugin on the server.");
            return false;
        }
        if (com.github.retrooper.packetevents.PacketEvents.getAPI() == null) {
            getLogger().severe("PacketEvents API is not ready. Ensure packetevents loads before VezAntiCheat.");
            return false;
        }
        return true;
    }

    // --- Accessor methods ---
    // Full-name accessors (for external API consumers)
    public PlayerDataManager getDataManager() { return dataManager; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public TierConfigManager getTierConfigManager() { return tierConfigManager; }
    public TierCheckManager getTierCheckManager() { return tierCheckManager; }
    public PolarFlagHistory polarFlags() { return polarFlagHistory; }
    public BanwaveManager getBanwaveManager() { return banwaveManager; }
    public boolean hasProtocolLib() { return protocolLib; }
    public boolean packetHooksRegistered() { return packetHooksRegistered; }

    // Short accessors (for internal use — reduces boilerplate in checks)
    public PlayerDataManager data() { return dataManager; }
    public TierCheckManager tierChecks() { return tierCheckManager; }
    public TierConfigManager tierCfg() { return tierConfigManager; }
    public PunishmentManager punish() { return punishmentManager; }
    public ConfigManager cfg() { return configManager; }
    public TpsMonitor tps() { return tpsMonitor; }
    public PredictionProcessor prediction() { return predictionProcessor; }
    public com.colin.vezanticheat.engine.MovementCheckRunner engine() { return movementEngine; }
    public com.colin.vezanticheat.combat.CombatAnalyzer combat() { return combatAnalyzer; }
    public com.colin.vezanticheat.combat.CombatAnalysisSettings combatSettings() { return combatSettings; }
    public com.colin.vezanticheat.combat.CombatStaffAlerter combatAlerter() { return combatAlerter; }

    public void reloadCombatSettings() {
        this.combatSettings = com.colin.vezanticheat.combat.CombatAnalysisSettings.fromPlugin(this);
        if (combatAnalyzer != null) {
            combatSettings.applyTo(combatAnalyzer, getConfig());
        }
    }

    /**
     * Starts (or restarts) the repeating scheduled VL decay sweep. Every {@code interval-ticks}
     * (default 20 = 1s) it sweeps every online player x every registered tier check and decays
     * their per-check VL pool. Without this, VL only decayed inside the per-check {@code decay()}
     * hooks (which most checks never call), so VL never fell globally.
     */
    public void startDecayTask() {
        stopDecayTask();
        if (tierCheckManager == null) return;
        if (!getConfig().getBoolean("tier.vl-decay-task.enabled", true)) return;
        long interval = Math.max(1L, getConfig().getLong("tier.vl-decay-task.interval-ticks", 20L));
        final com.colin.vezanticheat.verdict.TierPunishmentExecutor punisher = tierCheckManager.punisher();
        final java.util.List<com.colin.vezanticheat.tier.TierCheck> checks = tierCheckManager.registry().all();
        this.vlDecayTaskId = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if (getConfig().getBoolean("lag.enable-gates", true)) {
                    double tps = tps() != null ? tps().getTps() : 20.0D;
                    if (tps < getConfig().getDouble("lag.min-tps", 18.5D)) {
                        return;
                    }
                }
                long now = System.currentTimeMillis();
                int maxPing = getConfig().getInt("lag.max-ping", 250);
                for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                    if (getConfig().getBoolean("lag.enable-gates", true)) {
                        int ping = com.colin.vezanticheat.utils.PingUtil.getPing(player);
                        if (ping > 0 && ping > maxPing) {
                            continue;
                        }
                    }
                    for (com.colin.vezanticheat.tier.TierCheck check : checks) {
                        punisher.tickDecay(player, check, now);
                    }
                }
            }
        }, interval, interval).getTaskId();
    }

    /** Cancels the repeating VL decay sweep if running. */
    public void stopDecayTask() {
        if (vlDecayTaskId != -1) {
            Bukkit.getScheduler().cancelTask(vlDecayTaskId);
            vlDecayTaskId = -1;
        }
    }

    /** Authoritative gate for PacketEvents combat history + hit scoring (honors /vez reload). */
    public boolean isCombatAnalysisEnabled() {
        return combatSettings != null && combatSettings.isEnabled();
    }
    public VelocityProcessor velocity() { return velocityProcessor; }
    public DiagnosticsTracker diagnostics() { return diagnosticsTracker; }
    public EvidenceManager evidence() { return evidenceManager; }
    public RiskScoreManager riskScore() { return riskScoreManager; }
    public com.colin.vezanticheat.utils.PerfSampler perf() { return perfSampler; }
    public com.colin.vezanticheat.utils.ConfigProfileManager profiles() { return configProfileManager; }
    public com.colin.vezanticheat.license.LicenseManager license() { return licenseManager; }
    public com.colin.vezanticheat.license.UpdateChecker updates() { return updateChecker; }
}
