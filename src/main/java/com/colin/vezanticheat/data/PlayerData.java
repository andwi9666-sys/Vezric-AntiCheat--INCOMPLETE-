package com.colin.vezanticheat.data;

import com.colin.vezanticheat.prediction.PredictionState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerData — Per-player state container.
 *
 * Every online player has exactly one PlayerData instance, managed by PlayerDataManager.
 * It stores ALL state needed by checks to make detection decisions:
 *
 * Movement State:
 *   - lastMoveFrom/lastLoc: Previous and current position (from packets)
 *   - lastClientGround: Whether client claims to be on ground
 *   - lastFlyingPacket/lastFlyingIntervalMs: Packet timing for timer detection
 *   - flyingIntervals: Deque of recent packet intervals for jitter analysis
 *
 * Combat State:
 *   - lastTargetEntityId/lastTargetUuid: Entity being attacked
 *   - lastUseEntityTime/lastUseEntityDistance: Attack timing and reach
 *   - lastAttackEyeLocation: Eye position at attack time (for reach calculation)
 *   - lastAttackSwingDeltaMs: Time between swing and attack (for no-swing detection)
 *   - attackTimestamps/attackIntervals: History for timing analysis (KillAuraC)
 *
 * Velocity/KB State:
 *   - lastVelocity/lastVelocityTime: Most recent server-applied velocity
 *   - lastExplosionVelocity: Estimated explosion vector for prediction
 *   - lastAttackerSprinting/lastAttackerVelocity: Attacker state for KB validation
 *   - predictionState: Dedicated container for simulation engine state
 *
 * Rotation State:
 *   - lastYaw/lastPitch: Previous rotation values
 *   - lastRotationYawDelta/lastRotationPitchDelta: Frame-to-frame rotation change
 *   - yawDeltas/pitchDeltas: History for GCD analysis (KillAuraG)
 *
 * Exemption Windows:
 *   - teleportExemptUntilMs: Skip checks after teleport (position desync)
 *   - velocityExemptUntilMs: Skip checks after KB (motion in progress)
 *   - blockStateExemptUntilMs: Skip checks after nearby block changes
 *   - potionExemptUntilMs: Skip checks after potion applied
 *
 * Per-Check Buffers:
 *   - Each check has its own verbose/buffer counter (e.g., speedAGroundVerbose)
 *   - These track how close a player is to being flagged
 *   - Reset on clean movement, increment on suspicious movement
 *
 * Why One Class:
 * ==============
 * All state is in one object so checks can access any data they need without
 * cross-referencing multiple containers. The tradeoff is a large class, but
 * this is intentional — checks need fast access to correlated state.
 */
public class PlayerData {
    // Inner class for multi-peak tracking in NoFallA
    public static class PeakRecord {
        public final double y;
        public final long timestamp;
        public PeakRecord(double y, long timestamp) {
            this.y = y;
            this.timestamp = timestamp;
        }
    }

    public static final class RotationSample {
        public final float yaw;
        public final float pitch;
        public final long timeMs;
        public RotationSample(float yaw, float pitch, long timeMs) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.timeMs = timeMs;
        }
    }

    private final UUID uuid;
    private final PredictionState predictionState = new PredictionState();

    // Grim-style movement engine state (lazily initialized on first packet).
    private final com.colin.vezanticheat.engine.MovementPlayer movementPlayer = new com.colin.vezanticheat.engine.MovementPlayer();
    private final com.colin.vezanticheat.engine.CompensatedWorld compensatedWorld = new com.colin.vezanticheat.engine.CompensatedWorld();
    private com.colin.vezanticheat.engine.EngineResult lastEngineResult;
    /** Accumulated movement offset advantage (Grim-style); decays each tick, triggers setback at threshold. */
    private double engineOffsetAdvantage;
    private boolean pendingSetback;
    private long pendingSetbackSinceMs;
    private org.bukkit.Location pendingSetbackTarget;
    private boolean engineInitialized;

    // Grim-style combat engine state: transaction ping rewind + packet-synced entity positions.
    private final com.colin.vezanticheat.engine.TransactionState transactionState = new com.colin.vezanticheat.engine.TransactionState();
    private final com.colin.vezanticheat.engine.PlayerClock.PlayerClockState playerClockState =
            new com.colin.vezanticheat.engine.PlayerClock.PlayerClockState();
    private final com.colin.vezanticheat.engine.CompensatedEntities compensatedEntities = new com.colin.vezanticheat.engine.CompensatedEntities();
    private com.colin.vezanticheat.engine.CombatResult lastCombatResult;

    private boolean flagsEnabled = true;
    private boolean verboseMode = false;
    private boolean debugMode = false;

    private Location lastMoveFrom;
    private Location lastLoc;
    private long lastMoveMillis;
    private long combatJoinTimeMs;
    private boolean lastClientGround;
    private long lastClientGroundUpdateMs;

    // Phase 2A engine-authority field contract (read by movement/prediction tier workers).
    // serverGround is the engine collision verdict at the start-of-tick position; it is published
    // alongside the raw client claim so ground-spoof checks can compare the two truths.
    private boolean serverGround;
    // Chunk-unload "unverified" mode: how many consecutive ticks the engine could not validate
    // because surrounding chunks were not loaded. Used to force a resync setback if it persists.
    private int engineUnverifiedTicks;

    // GLOBAL VL (optional)
    private int totalVl;

    // PER-CHECK VL
    private final Map<String, Double> checkVl = new ConcurrentHashMap<>();

    // For velocity checks (keep)
    private Vector lastVelocity;
    private long lastVelocityTime;

    // For click checks
    private final Deque<Long> armSwings = new ArrayDeque<>();
    private long lastArmSwingPacket;

    // Rotations
    private float lastYaw, lastPitch;
    private float packetYaw, packetPitch;
    private float cameraYaw, cameraPitch;
    private float priorYaw, priorPitch;
    private float lastRotationYawDelta;
    private float lastRotationPitchDelta;
    private long lastRotationPacket;
    private org.bukkit.entity.Entity lastTargetEntity;
    private AttackRayContext attackRayContext;
    private double killAuraASnapRatio;
    private double partialKbRatio;
    private int inventoryMoveCount;
    private int vehicleSpeedViolationStreak;
    private int engineBlockChangeLenienceUsed;
    private long engineBlockChangeLenienceWindowStart;
    private int engineCombatGraceTicksUsed;
    private long engineCombatGraceWindowStart;
    private double peakAngularVelocityDegPerSec;
    private int scaffoldPlaceCount;
    private final Deque<RotationSample> rotationRingBuffer = new ArrayDeque<RotationSample>(20);

    // Timer / packet cadence
    private long lastFlyingPacket;
    private long lastFlyingIntervalMs;
    private final Deque<Long> flyingIntervals = new ArrayDeque<Long>();
    private final Deque<Integer> recentPingSamples = new ArrayDeque<Integer>();
    private final Deque<Long> recentLagGapIntervals = new ArrayDeque<Long>();
    private double lagProfileScore;
    private long lastLagSpikeTime;
    private long lastLagBurstTime;
    private long lastLagEvidenceTime;
    private int lagBurstPackets;
    private int suspiciousLagBursts;
    private int suspiciousLagAttacks;
    private long lastLagAttackTime;
    private UUID lagCombatFocusTargetUuid;
    private long lagCombatFocusTime;
    private UUID lagCombatDamagerUuid;
    private long lagCombatDamagerTime;
    private final Deque<CombatTeleportSample> lagrangeTeleportSamples = new ArrayDeque<CombatTeleportSample>();
    private double lagrangeTeleportScore;
    private long lastLagrangeTeleportTime;
    private long lastLagrangeTeleportFlagTime;

    // Scaffold / placing
    private long lastBlockPlace;
    private int placeStreak;

    // Bow
    private long bowPullStart;
    private long lastBowShotMs;
    private long lastBowShotIntervalMs;

    // Inventory
    private boolean inventoryOpen;
    private long lastInventoryAction;

    // --- Protocol USE_ENTITY ---
    private int lastTargetEntityId = -1;
    private UUID lastTargetUuid = null;
    private long lastUseEntityTime = 0L;
    private double lastUseEntityDistance = 0.0;
    private boolean lastUseEntityAttack = false;
    private Location lastAttackEyeLocation;
    private long lastAttackSwingDeltaMs = Long.MAX_VALUE;
    private boolean blockCurrentAttackPacket;
    private String blockedAttackReason;
    private boolean blockCurrentMovementPacket;
    private String blockedMovementReason;

    // --- Extra states ---
    private long lastEatStart = 0L;
    private long lastBlockDig = 0L;
    private long lastHeldItemSwitch = 0L;
    private long lastCombatInteractTime = 0L;
    private long lastJumpTime = 0L;

    // Multi-block fall arc (ledge walk-off / jump-down landing grace for prediction).
    private double fallArcPeakY;
    private double fallArcMinY;
    private long fallArcStartMs;
    private long fallArcLandMs;
    private boolean fallArcActive;

    // FastBreak fields
    private long lastDigStartMs = 0L;
    private long lastBreakMs = 0L;
    private int fastBreakAVerbose = 0;
    private int fastBreakBVerbose = 0;
    private final java.util.Deque<Long> breakIntervals = new java.util.ArrayDeque<Long>();

    // Nuker fields
    private final java.util.Deque<Long> breakTimestamps = new java.util.ArrayDeque<Long>();
    private int nukerAVerbose = 0;
    private int nukerBVerbose = 0;
    private int nukerCVerbose = 0;
    private int nukerDVerbose = 0;
    private int bedBreakReachBuffer = 0;
    private int bedAuraBuffer = 0;
    private int digStartCount = 0;
    private int digBreakCount = 0;

    private int verbose;

    // combat timestamp for velocity correlation
    private long lastDamageTime;
    private org.bukkit.event.entity.EntityDamageEvent.DamageCause lastDamageCause;
    private long lastFallDamageTime;
    private double lastFallDamageAmount;

    // Explosion velocity tracking (Phase 2: prediction refactor)
    private Vector lastExplosionVelocity;

    // Attacker state at KB time (for validation)
    private UUID lastAttackerUuid;
    private boolean lastAttackerSprinting;
    private Vector lastAttackerVelocity;
    private double lastAttackCooldown;
    private int lastAttackerKnockbackLevel;

    // Knockback response window
    private boolean kbWindow;
    private long kbWindowStart;
    private Location kbStartLoc;
    private double kbMovedH;
    private double kbExpectedH;
    private int kbVerbose;

    // Aura probe state
    private boolean auraProbeActive;
    private long auraProbeStart;
    private UUID auraProbeNpcUuid;
    private int auraProbeHits;
    private int auraProbeLockSamples;
    private int auraProbeSamples;
    private long auraProbeLastSample;

    // ---------------------------
    // Exemption windows
    // ---------------------------
    private long teleportExemptUntilMs;
    private long velocityExemptUntilMs;
    private long blockStateExemptUntilMs;
    private long potionExemptUntilMs;
    private long inventoryMomentumExemptUntilMs;
    private long eatMovementGraceUntilMs;

    // ---------------------------
    // packet/badpackets state
    // ---------------------------
    private long lastClientPacketTime;
    /** @deprecated use {@link #getBadPacketsVerbose(char)} with letter 'A'–'Z' */
    private int badPacketVerbose;
    private final int[] badPacketsVerbose = new int[26];
    private int badPacketsTransactionAVerbose;
    private int badPacketsTransactionBVerbose;
    private int setbackAcceptAVerbose;
    private int positionPacketsThisTick;
    private long positionPacketTickId;
    private boolean useItemActive;
    private long useItemStartMs;
    private int multiActionsCVerbose;
    private int multiActionsEVerbose;
    private int autoBlockCVerbose;
    private long lastMicroYOffsetMs;
    private final com.colin.vezanticheat.utils.BadPacketTracker badPacketTracker =
            new com.colin.vezanticheat.utils.BadPacketTracker();
    private boolean blockCurrentDigPacket;
    private boolean blockCurrentPlacePacket;
    private boolean blockCurrentWindowPacket;
    private String blockedDigReason;
    private String blockedPlaceReason;
    private String blockedWindowReason;
    private int lastPacketInteractEntityId = -1;
    private boolean lastPacketInteractWasAttack;

    // =========================================================
    // SCAFFOLD CONTEXT
    // =========================================================
    private long lastScaffoldPlaceTime;
    private Location lastPlacedBlockLoc;
    private Location lastPlaceAgainstLoc;
    private BlockFace lastPlaceFace;
    private float lastPlaceYaw;
    private float lastPlacePitch;
    private boolean lastPlaceOnGround;
    private boolean lastPlaceSneaking;

    // last N place intervals in ms (for consistency detection)
    private final Deque<Long> scaffoldIntervals = new ArrayDeque<Long>();

    // last N placement pitch values (for pitch-lock detection)
    private final Deque<Float> scaffoldPitchHistory = new ArrayDeque<Float>();

    // small per-scaffold counters (so we don't reuse global "verbose")
    private int scaffoldVerboseA;
    private int scaffoldVerboseB;
    private int scaffoldVerboseC;
    private int scaffoldVerboseD;
    private int scaffoldVerboseE;
    private int scaffoldVerboseF;
    private int scaffoldVerboseG;
    private int prismPerfectPlacementStreak;

    // Raw block-place packet data for scaffold validity checks.
    private long lastBlockPlacePacketTime;
    private Location lastBlockPlacePacketAgainstLoc;
    private int lastBlockPlacePacketFaceId = -1;
    private float lastBlockPlacePacketCursorX = Float.NaN;
    private float lastBlockPlacePacketCursorY = Float.NaN;
    private float lastBlockPlacePacketCursorZ = Float.NaN;
    private float lastBlockPlacePacketYaw;
    private float lastBlockPlacePacketPitch;
    private Location lastBlockPlacePacketLoc;
    private int scaffoldPacketCountThisFlying;
    private boolean scaffoldPacketMismatchThisFlying;
    private String lastBlockPlacePacketSignature;
    private boolean scaffoldRotationPending;
    private boolean scaffoldRotationSampleReady;
    private float scaffoldPostPlaceYawDelta;
    private float scaffoldPostPlacePitchDelta;

    // Short support/block history for scaffold support compensation.
    private final Deque<BlockStateSample> recentBlockStateHistory = new ArrayDeque<BlockStateSample>();

    // =========================================================
    // Per-check verbose counters (avoid sharing global "verbose")
    // =========================================================
    private int aimAssistAVerbose;
    private int aimAssistBVerbose;
    private int aimAssistCVerbose;
    private int autoClickAVerbose;
    private int autoClickBVerbose;
    private int autoClickCVerbose;
    private int criticalsBVerbose;
    private int criticalsCVerbose;

    // =========================================================
    // AimAssist data: rotation delta history for GCD analysis
    // =========================================================
    private final Deque<Float> yawDeltas = new ArrayDeque<Float>();
    private final Deque<Float> pitchDeltas = new ArrayDeque<Float>();
    private final Deque<Double> aimCenterErrors = new ArrayDeque<Double>();
    private final Deque<Double> aimCenterMargins = new ArrayDeque<Double>();
    private long lastAimSampleRotationPacket;

    // =========================================================
    // Criticals data: Y-position tracking for mini-jump detection
    // =========================================================
    private final Deque<Double> recentYDeltas = new ArrayDeque<Double>();
    private double lastTrackY;
    private boolean hasLastTrackY;
    private int criticalHits;
    private int totalAttacks;
    private long lastCritResetTime;

    // =========================================================
    // Click pattern entropy tracking (AutoClickB/C)
    // =========================================================
    private final Deque<Long> clickIntervals = new ArrayDeque<Long>();

    // =========================================================
    // Velocity vertical tracking
    // =========================================================
    private double kbExpectedV;
    private double kbMovedV;

    // =========================================================
    // Attack timing pattern (KillAura improvements)
    // =========================================================
    private final Deque<Long> attackTimestamps = new ArrayDeque<Long>();
    private final Deque<Float> attackYawDeltas = new ArrayDeque<Float>();
    private final Deque<Long> attackIntervals = new ArrayDeque<Long>();
    private UUID killAuraALastTargetUuid;
    private long killAuraALastHitMs;
    private int killAuraASwitchBuffer;
    private double killAuraAPrevCv;
    private boolean killAuraAHasPrevCv;
    private int killAuraACleanStreak;

    // KillAuraA signal 3: post-attack reset tracking
    private long killAuraAPostResetStartMs;
    private float killAuraAPostResetBaseYaw;
    private float killAuraAPostResetBasePitch;
    private float killAuraAPostResetAttackYaw;
    private float killAuraAPostResetAttackPitch;
    private boolean killAuraAPostResetActive;
    private int killAuraAPostResetConfirmed;

    // KillAuraA signal 4: movement mismatch history
    private final Deque<Double> killAuraAMismatchAngles = new ArrayDeque<Double>();

    // KillAuraA signal 5: center bias at attack time
    private final Deque<Double> killAuraACenterErrors = new ArrayDeque<Double>();

    // KillAuraA signal 6: attack-tick rotation correlation
    private int killAuraARotOnAttackTicks;
    private int killAuraARotOnNonAttackTicks;
    private int killAuraANoRotOnNonAttackTicks;
    private long killAuraACorrelationWindowStart;
    // AutoBlockA — timing pattern state
    private long autoBlockALastBlockStartMs;
    private final Deque<Long> autoBlockABlockAttackIntervals = new ArrayDeque<Long>();
    private int autoBlockACyclesInWindow;
    private long autoBlockAWindowStart;
    private int autoBlockABuffer;

    // AutoBlockB — lag pattern state
    private int autoBlockBGapsDuringBlock;
    private int autoBlockBTotalCombatTicks;
    private int autoBlockBBlockedCombatTicks;
    private int autoBlockBAttacksDuringBurst;
    private int autoBlockBTotalAttacks;
    private long autoBlockBWindowStart;
    private int autoBlockBBuffer;

    private int killAuraBBadBuffer;
    private long killAuraBLastBadMs;
    private int killAuraCBuffer;
    private long killAuraCLastFlagStateMs;
    private long killAuraCLastSnapMs;
    private int killAuraDBuffer;
    private long killAuraDLastStateMs;
    private int killAuraDConsecutiveWindows;
    private long killAuraDLastWindowMs;
    private UUID killAuraFLastTargetUuid;
    private Location killAuraFLastTargetLoc;
    private float killAuraFLastYaw;
    private float killAuraFLastPitch;
    private long killAuraFLastAttackMs;
    private int killAuraFLockBuffer;
    private int killAuraGBuffer;

    // KillAuraH — rotation GCD fields
    private float kauraHLastDeltaYaw;
    private float kauraHLastDeltaPitch;
    private final Deque<Float> kauraHYawGcds = new ArrayDeque<Float>();
    private final Deque<Float> kauraHPitchGcds = new ArrayDeque<Float>();
    private float kauraHLearnedYawGcd;
    private float kauraHLearnedPitchGcd;
    private boolean kauraHYawGcdReady;
    private boolean kauraHPitchGcdReady;
    private int kauraHBuffer;

    private long speedLastVelocityExemptMs;
    private int speedAGroundVerbose;
    private int speedAAirVerbose;
    private double noFallAPeakY;
    private boolean noFallAHasPeakY;
    private boolean noFallAWasOnGround;
    private long noFallALandAtMs;
    private double noFallALandFall;
    private long noFallAExpectedDamageAfterMs;
    private int noFallABuffer;
    private double noFallCPeakY;
    private boolean noFallCHasPeakY;
    private float noFallCMaxReportedFall;
    private int noFallCResetCount;
    private int noFallCBuffer;

    // Shared NoFall state for cross-check correlation and aggregate confidence
    private final Deque<Double> noFallYDeltaHistory = new ArrayDeque<>();
    private final Deque<Boolean> noFallGroundHistory = new ArrayDeque<>();
    private final Deque<Double> noFallServerFallHistory = new ArrayDeque<>();
    private final Deque<Double> noFallClientFallHistory = new ArrayDeque<>();
    private final Deque<Long> noFallSampleTimestamps = new ArrayDeque<>();
    private final List<PeakRecord> noFallAPeaks = new ArrayList<>();
    private double noFallAggregateConfidence = 0.0;
    private long lastNoFallAggregateUpdate = 0L;
    private final Deque<Boolean> recentOnGroundPackets = new ArrayDeque<>();
    private long lastOnGroundPacketTime = 0L;
    private double noFallLastDy = 0.0;
    private int noFallTicksSinceLastSample = 0;

    private int flyAAirTicks;
    private int flyABuffer;
    private int flyBAirTicks;
    private int flyBBuffer;
    private int flyCAirTicks;
    private int flyCBuffer;
    private int flyDAirTicks;
    private int flyDUpTicks;
    private int flyDBuffer;
    private long flyDLaunchExemptUntilMs;
    private int flyEAirTicks;
    private int flyEBuffer;
    private int flyFAirTicks;
    private int flyFBuffer;
    private int simulationBuffer;
    private long blinkAFreezeStartMs;
    private Location blinkAFreezeLoc;
    private long blinkAFreezeDurationMs;
    private int blinkABurstCount;
    private int blinkABuffer;
    private final Deque<Long> blinkBPacketTimes = new ArrayDeque<Long>();
    private long blinkBDeadZoneEndMs;
    private long blinkBDeadZoneDurationMs;
    private int blinkBBuffer;
    private long stepCLastStepMs;
    private int stepCBuffer;
    private int jesusBBuffer;
    private int speedDBuffer;
    private long noRotationALastProcessedAttackMs;
    private long noRotationBLastProcessedAttackMs;
    private int reachABuffer;
    private int reachBBuffer;
    private final Deque<Double> reachCSamples = new ArrayDeque<Double>();
    private final Deque<Long> reachCSampleTimes = new ArrayDeque<Long>();
    private int backTrackABuffer;
    private long backTrackALastProcessedAttackMs;
    private final Deque<Boolean> backTrackAStaleHistory = new ArrayDeque<Boolean>();
    private final Deque<Boolean> backTrackATimingHistory = new ArrayDeque<Boolean>();
    private final Deque<Boolean> backTrackAOrderHistory = new ArrayDeque<Boolean>();
    private final Deque<Boolean> backTrackAPingHistory = new ArrayDeque<Boolean>();
    private final Deque<Long> backTrackAStaleAgeHistory = new ArrayDeque<Long>();
    private int backTrackALastPing;
    private boolean backTrackAHasLastPing;

    // =========================================================
    // Per-check verbose counters (fixes shared counter bugs)
    // =========================================================
    private int jesusAVerbose;
    private int phaseAVerbose;
    private int groundSpoofAVerbose;
    private int groundSpoofBVerbose;
    private int inventoryAVerbose;
    private long inventoryALastFlagTime;
    private int inventoryAEscalation;
    private int inventoryBVerbose;
    private int inventoryCVerbose;
    private int timerAVerbose;
    private int timerBVerbose;
    private int timerPredictionBuffer;
    private int noRotationAVerbose;
    private int noRotationBVerbose;
    private int badPacketsBVerbose;
    private int speedBVerbose;
    private int speedBAirTicks;    // how many consecutive ticks spent airborne (for sprint-jump landing grace)
    private int speedBLastAirRunTicks;
    private int speedCVerbose;
    private int noFallBVerbose;
    private int noSlowAVerbose;
    private int noSlowBVerbose;
    private int fastEatAVerbose;
    private int fastBowAVerbose;
    private int fastBowBVerbose;

    // =========================================================
    // Timer balance-based detection (TimerB)
    // =========================================================
    private long timerBalanceStart;
    private int timerPacketCount;
    private long timerLastReset;
    private long timerDebtMs;

    // =========================================================
    // Negative timer detection (TimerC - timer speed < 1.0)
    // =========================================================
    private long timerCStart;
    private int timerCPacketCount;
    private long timerCLastReset;
    private int timerCVerbose;
    private long timerCBalance;
    private long timerCThreshold;

    // =========================================================
    // TimerA balance-based fast timer
    // =========================================================
    private long timerABalance;
    private long timerAThreshold;

    // =========================================================
    // Gravity tracking (FlyE)
    // =========================================================
    private final Deque<Double> recentYMotions = new ArrayDeque<Double>();

    // =========================================================
    // Inventory click tracking (InventoryB)
    // =========================================================
    private final Deque<Long> inventoryClickTimes = new ArrayDeque<Long>();

    // =========================================================
    // Ground spoof: server-side Y tracking
    // =========================================================
    private int groundSpoofAirTicks;
    private double groundSpoofLastY;
    private long groundDescentSessionStartMs;
    private double groundDescentCumulativeDy;

    // NoFall blink capture
    private double preBlinkPeakY;
    private long preBlinkMs;
    private boolean noFallDamageResolved;
    private long noFallBlinkLandMs;
    private double noFallBlinkDrop;
    private long lastVelocityAnimationMs;

    // =========================================================
    // Combat history for reach/backtrack/snap analysis
    // =========================================================
    private final Deque<PositionSample> positionHistory = new ArrayDeque<PositionSample>();

    // Polar-style last flag metadata (staff alerts / verbose)
    private String lastPolarFlagCheck;
    private String lastPolarFlagTier;
    private String lastPolarFlagDebug;
    private long lastPolarFlagMs;
    private String clientBrand = "Unknown";
    private String clientVersion = "Unknown";

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }
    public PredictionState getPredictionState() { return predictionState; }

    public com.colin.vezanticheat.engine.MovementPlayer getMovementPlayer() { return movementPlayer; }
    public com.colin.vezanticheat.engine.CompensatedWorld getCompensatedWorld() { return compensatedWorld; }
    public com.colin.vezanticheat.engine.EngineResult getLastEngineResult() { return lastEngineResult; }
    public void setLastEngineResult(com.colin.vezanticheat.engine.EngineResult result) { this.lastEngineResult = result; }
    public double getEngineOffsetAdvantage() { return engineOffsetAdvantage; }
    public void setEngineOffsetAdvantage(double engineOffsetAdvantage) { this.engineOffsetAdvantage = engineOffsetAdvantage; }

    // ---- Phase 2A field contract for prediction/movement tier workers (2B/2C/2D) ----

    /** Long-window accumulated prediction offset advantage (Grim-style, slow-decaying). */
    public double getOffsetAdvantage() { return engineOffsetAdvantage; }

    /** Engine collision-derived ground truth at the start-of-tick position. */
    public boolean isServerGround() { return serverGround; }
    public void setServerGround(boolean serverGround) { this.serverGround = serverGround; }

    /** Raw client ground claim from the most recent flying packet. */
    public boolean getClientGround() { return lastClientGround; }

    /** Persistent cumulative clock-drift ledger in ms (timer-cheat farm-and-reset proof). */
    public long getCumulativeDriftMs() { return playerClockState.cumulativeDriftMs; }

    public int getEngineUnverifiedTicks() { return engineUnverifiedTicks; }
    public void setEngineUnverifiedTicks(int engineUnverifiedTicks) { this.engineUnverifiedTicks = engineUnverifiedTicks; }
    public int incrementEngineUnverifiedTicks() { return ++engineUnverifiedTicks; }
    public void resetEngineUnverifiedTicks() { this.engineUnverifiedTicks = 0; }
    public boolean isPendingSetback() { return pendingSetback; }
    public void setPendingSetback(boolean pendingSetback) { this.pendingSetback = pendingSetback; }
    public long getPendingSetbackSinceMs() { return pendingSetbackSinceMs; }
    public void setPendingSetbackSinceMs(long pendingSetbackSinceMs) { this.pendingSetbackSinceMs = pendingSetbackSinceMs; }
    public org.bukkit.Location getPendingSetbackTarget() { return pendingSetbackTarget; }
    public void setPendingSetbackTarget(org.bukkit.Location pendingSetbackTarget) { this.pendingSetbackTarget = pendingSetbackTarget; }
    public void clearPendingSetback() {
        this.pendingSetback = false;
        this.pendingSetbackSinceMs = 0L;
        this.pendingSetbackTarget = null;
    }
    public boolean isEngineInitialized() { return engineInitialized; }
    public void setEngineInitialized(boolean v) { this.engineInitialized = v; }

    public com.colin.vezanticheat.engine.TransactionState getTransactionState() { return transactionState; }
    public com.colin.vezanticheat.engine.PlayerClock.PlayerClockState getPlayerClockState() { return playerClockState; }
    public com.colin.vezanticheat.engine.CompensatedEntities getCompensatedEntities() { return compensatedEntities; }
    public com.colin.vezanticheat.engine.CombatResult getLastCombatResult() { return lastCombatResult; }
    public void setLastCombatResult(com.colin.vezanticheat.engine.CombatResult result) { this.lastCombatResult = result; }

    public boolean isFlagsEnabled() { return flagsEnabled; }
    public void setFlagsEnabled(boolean flagsEnabled) { this.flagsEnabled = flagsEnabled; }
    public boolean isVerboseMode() { return verboseMode; }
    public void setVerboseMode(boolean verboseMode) { this.verboseMode = verboseMode; }
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

    public Location getLastMoveFrom() { return lastMoveFrom; }
    public void setLastMoveFrom(Location lastMoveFrom) { this.lastMoveFrom = lastMoveFrom; }

    public Location getLastLoc() { return lastLoc; }
    public void setLastLoc(Location lastLoc) { this.lastLoc = lastLoc; }

    public long getLastMoveMillis() { return lastMoveMillis; }
    public void setLastMoveMillis(long lastMoveMillis) { this.lastMoveMillis = lastMoveMillis; }
    public boolean wasLastClientGround() { return lastClientGround; }
    public long getLastClientGroundUpdateMs() { return lastClientGroundUpdateMs; }
    public void setLastClientGround(boolean lastClientGround, long lastClientGroundUpdateMs) {
        this.lastClientGround = lastClientGround;
        this.lastClientGroundUpdateMs = lastClientGroundUpdateMs;
    }

    public int getVerbose() { return verbose; }

    public void recordPolarFlag(String check, String tier, String debug, long nowMs) {
        this.lastPolarFlagCheck = check;
        this.lastPolarFlagTier = tier;
        this.lastPolarFlagDebug = debug;
        this.lastPolarFlagMs = nowMs;
    }

    public String getLastPolarFlagCheck() { return lastPolarFlagCheck; }
    public String getLastPolarFlagTier() { return lastPolarFlagTier; }
    public String getLastPolarFlagDebug() { return lastPolarFlagDebug; }
    public long getLastPolarFlagMs() { return lastPolarFlagMs; }

    public String getClientBrand() { return clientBrand == null ? "Unknown" : clientBrand; }
    public void setClientBrand(String clientBrand) {
        this.clientBrand = (clientBrand == null || clientBrand.trim().isEmpty()) ? "Unknown" : clientBrand.trim();
    }

    public String getClientVersion() { return clientVersion == null ? "Unknown" : clientVersion; }
    public void setClientVersion(String clientVersion) {
        this.clientVersion = (clientVersion == null || clientVersion.trim().isEmpty()) ? "Unknown" : clientVersion.trim();
    }
    public void setVerbose(int verbose) { this.verbose = verbose; }

    public int getTotalVl() { return totalVl; }
    public void addTotalVl(int amount) { this.totalVl += amount; }
    public void reduceTotalVl(int amount) { this.totalVl = Math.max(0, this.totalVl - amount); }

    public double getCheckVl(String check) {
        Double v = checkVl.get(check);
        return v == null ? 0.0 : v;
    }

    public double addCheckVl(String check, double add) {
        double nv = getCheckVl(check) + add;
        checkVl.put(check, nv);
        return nv;
    }

    public void reduceCheckVl(String check, double amount) {
        double nv = Math.max(0.0, getCheckVl(check) - amount);
        checkVl.put(check, nv);
    }

    public Map<String, Double> snapshotCheckVl() {
        return new HashMap<String, Double>(checkVl);
    }

    public Vector getLastVelocity() { return lastVelocity; }
    public void setLastVelocity(Vector lastVelocity) { this.lastVelocity = lastVelocity; }

    public long getLastVelocityTime() { return lastVelocityTime; }
    public void setLastVelocityTime(long lastVelocityTime) { this.lastVelocityTime = lastVelocityTime; }

    public Vector getLastExplosionVelocity() { return lastExplosionVelocity; }
    public void setLastExplosionVelocity(Vector lastExplosionVelocity) { this.lastExplosionVelocity = lastExplosionVelocity; }

    public Deque<Long> getArmSwings() { return armSwings; }
    public long getLastArmSwingPacket() { return lastArmSwingPacket; }
    public void setLastArmSwingPacket(long lastArmSwingPacket) { this.lastArmSwingPacket = lastArmSwingPacket; }

    public float getLastYaw() { return lastYaw; }
    public float getLastPitch() { return lastPitch; }
    public float getPacketYaw() { return packetYaw == 0.0F && lastYaw != 0.0F ? lastYaw : packetYaw; }
    public float getPacketPitch() { return packetPitch == 0.0F && lastPitch != 0.0F ? lastPitch : packetPitch; }
    public void setPacketRotation(float yaw, float pitch) {
        this.packetYaw = yaw;
        this.packetPitch = pitch;
    }
    public void setCameraRotation(float yaw, float pitch) {
        this.cameraYaw = yaw;
        this.cameraPitch = pitch;
    }
    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }
    public AttackRayContext getAttackRayContext() { return attackRayContext; }
    public void setAttackRayContext(AttackRayContext attackRayContext) { this.attackRayContext = attackRayContext; }

    /**
     * Build and store packet-synced attack ray context for prism silent-aim checks.
     */
    public void captureAttackRayContext(org.bukkit.entity.Player player, org.bukkit.entity.Entity target,
                                        org.bukkit.Location eye, long nowMs) {
        if (player == null || target == null || eye == null) {
            this.attackRayContext = null;
            return;
        }
        double width = com.colin.vezanticheat.utils.CombatUtil.entityWidth(target);
        double height = com.colin.vezanticheat.utils.CombatUtil.entityHeight(target);
        org.bukkit.Location base = target.getLocation();
        org.bukkit.Location packetEye = eye.clone();
        packetEye.setYaw(packetYaw);
        packetEye.setPitch(packetPitch);
        double distance = com.colin.vezanticheat.utils.CombatUtil.distanceToHitbox(packetEye, base, width, height);
        double angle = com.colin.vezanticheat.utils.CombatUtil.angularError(packetEye, base, width, height);
        double lookDot = com.colin.vezanticheat.utils.CombatUtil.lookDotToHitbox(packetEye, base, width, height);
        org.bukkit.util.Vector toTarget = base.clone().add(0.0D, height * 0.5D, 0.0D).toVector().subtract(eye.toVector());
        this.attackRayContext = new AttackRayContext(nowMs, eye, packetYaw, packetPitch,
                cameraYaw, cameraPitch, toTarget, angle, lookDot, distance, target.getEntityId());
    }
    public org.bukkit.entity.Entity getLastTargetEntity() { return lastTargetEntity; }
    public double getKillAuraASnapRatio() { return killAuraASnapRatio; }
    public void setKillAuraASnapRatio(double killAuraASnapRatio) { this.killAuraASnapRatio = killAuraASnapRatio; }
    public double getPartialKbRatio() { return partialKbRatio; }
    public void setPartialKbRatio(double partialKbRatio) { this.partialKbRatio = partialKbRatio; }
    public int getInventoryMoveCount() { return inventoryMoveCount; }
    public void setInventoryMoveCount(int inventoryMoveCount) { this.inventoryMoveCount = inventoryMoveCount; }

    /** Counts horizontal movement ticks while the inventory GUI is open (silent-aim inventory signal). */
    public void noteInventoryMoveTick(double horizontalDelta) {
        if (!inventoryOpen || horizontalDelta < 0.04D) return;
        inventoryMoveCount++;
    }

    public void resetInventoryMoveCount() {
        inventoryMoveCount = 0;
    }

    public int getVehicleSpeedViolationStreak() { return vehicleSpeedViolationStreak; }
    public void setVehicleSpeedViolationStreak(int vehicleSpeedViolationStreak) {
        this.vehicleSpeedViolationStreak = Math.max(0, vehicleSpeedViolationStreak);
    }

    public boolean tryConsumeBlockChangeLenience(long nowMs, int maxPerWindow, long windowMs) {
        if (engineBlockChangeLenienceWindowStart <= 0L
                || nowMs - engineBlockChangeLenienceWindowStart > windowMs) {
            engineBlockChangeLenienceWindowStart = nowMs;
            engineBlockChangeLenienceUsed = 0;
        }
        if (engineBlockChangeLenienceUsed >= maxPerWindow) return false;
        engineBlockChangeLenienceUsed++;
        return true;
    }

    public boolean tryConsumeCombatGraceTick(long nowMs, int maxPerWindow, long windowMs) {
        if (engineCombatGraceWindowStart <= 0L || nowMs - engineCombatGraceWindowStart > windowMs) {
            engineCombatGraceWindowStart = nowMs;
            engineCombatGraceTicksUsed = 0;
        }
        if (engineCombatGraceTicksUsed >= maxPerWindow) return false;
        engineCombatGraceTicksUsed++;
        return true;
    }
    public double getPeakAngularVelocityDegPerSec() { return peakAngularVelocityDegPerSec; }
    public void setPeakAngularVelocityDegPerSec(double peakAngularVelocityDegPerSec) {
        this.peakAngularVelocityDegPerSec = Math.max(0.0D, peakAngularVelocityDegPerSec);
    }
    public Deque<com.colin.vezanticheat.utils.GcdLatticeAnalysis.AngularSample> getAngularVelocitySamples() {
        return angularVelocitySamples;
    }
    public int getScaffoldPlaceCount() { return scaffoldPlaceCount; }
    public void incrementScaffoldPlaceCount() { this.scaffoldPlaceCount++; }
    public Deque<RotationSample> getRotationRingBuffer() { return rotationRingBuffer; }
    public long getLastDamageTakenMs() { return lastDamageTime; }
    public float getPriorYaw() { return priorYaw; }
    public float getPriorPitch() { return priorPitch; }
    public float getLastRotationYawDelta() { return lastRotationYawDelta; }
    public float getLastRotationPitchDelta() { return lastRotationPitchDelta; }

    public void setLastRot(float yaw, float pitch) {
        this.lastYaw = yaw;
        this.lastPitch = pitch;
    }

    public void recordRotationSample(float yaw, float pitch) {
        this.priorYaw = this.lastYaw;
        this.priorPitch = this.lastPitch;
        float yawDelta = com.colin.vezanticheat.utils.CombatUtil.angleDiff(yaw, this.lastYaw);
        float pitchDelta = Math.abs(pitch - this.lastPitch);

        this.lastRotationYawDelta = yawDelta;
        this.lastRotationPitchDelta = pitchDelta;
        this.packetYaw = yaw;
        this.packetPitch = pitch;

        this.yawDeltas.addLast(yawDelta);
        while (this.yawDeltas.size() > 40) this.yawDeltas.removeFirst();

        this.pitchDeltas.addLast(pitchDelta);
        while (this.pitchDeltas.size() > 40) this.pitchDeltas.removeFirst();

        this.lastYaw = yaw;
        this.lastPitch = pitch;

        rotationRingBuffer.addLast(new RotationSample(yaw, pitch, System.currentTimeMillis()));
        while (rotationRingBuffer.size() > 20) rotationRingBuffer.removeFirst();
    }

    public long getLastRotationPacket() { return lastRotationPacket; }
    public void setLastRotationPacket(long lastRotationPacket) { this.lastRotationPacket = lastRotationPacket; }
    public long getLastAimSampleRotationPacket() { return lastAimSampleRotationPacket; }
    public void setLastAimSampleRotationPacket(long lastAimSampleRotationPacket) {
        this.lastAimSampleRotationPacket = lastAimSampleRotationPacket;
    }

    public long getLastFlyingPacket() { return lastFlyingPacket; }
    public void setLastFlyingPacket(long lastFlyingPacket) { this.lastFlyingPacket = lastFlyingPacket; }
    public long getLastFlyingIntervalMs() { return lastFlyingIntervalMs; }
    public void setLastFlyingIntervalMs(long lastFlyingIntervalMs) { this.lastFlyingIntervalMs = lastFlyingIntervalMs; }
    public Deque<Long> getFlyingIntervals() { return flyingIntervals; }
    public Deque<Integer> getRecentPingSamples() { return recentPingSamples; }
    public Deque<Long> getRecentLagGapIntervals() { return recentLagGapIntervals; }
    public double getLagProfileScore() { return lagProfileScore; }
    public void setLagProfileScore(double lagProfileScore) { this.lagProfileScore = lagProfileScore; }
    public long getLastLagSpikeTime() { return lastLagSpikeTime; }
    public void setLastLagSpikeTime(long lastLagSpikeTime) { this.lastLagSpikeTime = lastLagSpikeTime; }
    public long getLastLagBurstTime() { return lastLagBurstTime; }
    public void setLastLagBurstTime(long lastLagBurstTime) { this.lastLagBurstTime = lastLagBurstTime; }
    public long getLastLagEvidenceTime() { return lastLagEvidenceTime; }
    public void setLastLagEvidenceTime(long lastLagEvidenceTime) { this.lastLagEvidenceTime = lastLagEvidenceTime; }
    public int getLagBurstPackets() { return lagBurstPackets; }
    public void setLagBurstPackets(int lagBurstPackets) { this.lagBurstPackets = lagBurstPackets; }
    public int getSuspiciousLagBursts() { return suspiciousLagBursts; }
    public void setSuspiciousLagBursts(int suspiciousLagBursts) { this.suspiciousLagBursts = suspiciousLagBursts; }
    public int getSuspiciousLagAttacks() { return suspiciousLagAttacks; }
    public void setSuspiciousLagAttacks(int suspiciousLagAttacks) { this.suspiciousLagAttacks = suspiciousLagAttacks; }
    public long getLastLagAttackTime() { return lastLagAttackTime; }
    public void setLastLagAttackTime(long lastLagAttackTime) { this.lastLagAttackTime = lastLagAttackTime; }
    public UUID getLagCombatFocusTargetUuid() { return lagCombatFocusTargetUuid; }
    public long getLagCombatFocusTime() { return lagCombatFocusTime; }
    public UUID getLagCombatDamagerUuid() { return lagCombatDamagerUuid; }
    public long getLagCombatDamagerTime() { return lagCombatDamagerTime; }
    public Deque<CombatTeleportSample> getLagrangeTeleportSamples() { return lagrangeTeleportSamples; }
    public double getLagrangeTeleportScore() { return lagrangeTeleportScore; }
    public void setLagrangeTeleportScore(double lagrangeTeleportScore) { this.lagrangeTeleportScore = lagrangeTeleportScore; }
    public long getLastLagrangeTeleportTime() { return lastLagrangeTeleportTime; }
    public void setLastLagrangeTeleportTime(long lastLagrangeTeleportTime) { this.lastLagrangeTeleportTime = lastLagrangeTeleportTime; }
    public long getLastLagrangeTeleportFlagTime() { return lastLagrangeTeleportFlagTime; }
    public void setLastLagrangeTeleportFlagTime(long lastLagrangeTeleportFlagTime) { this.lastLagrangeTeleportFlagTime = lastLagrangeTeleportFlagTime; }

    public void noteCombatTarget(UUID targetUuid, long now) {
        if (targetUuid == null) return;
        this.lagCombatFocusTargetUuid = targetUuid;
        this.lagCombatFocusTime = now;
    }

    public void noteCombatDamager(UUID attackerUuid, long now) {
        if (attackerUuid == null) return;
        this.lagCombatDamagerUuid = attackerUuid;
        this.lagCombatDamagerTime = now;
    }

    public long getLastBlockPlace() { return lastBlockPlace; }
    public void setLastBlockPlace(long lastBlockPlace) { this.lastBlockPlace = lastBlockPlace; }

    public int getPlaceStreak() { return placeStreak; }
    public void setPlaceStreak(int placeStreak) { this.placeStreak = placeStreak; }

    public long getBowPullStart() { return bowPullStart; }
    public void setBowPullStart(long bowPullStart) { this.bowPullStart = bowPullStart; }
    public long getLastBowShotMs() { return lastBowShotMs; }
    public void setLastBowShotMs(long lastBowShotMs) { this.lastBowShotMs = lastBowShotMs; }
    public long getLastBowShotIntervalMs() { return lastBowShotIntervalMs; }
    public void setLastBowShotIntervalMs(long lastBowShotIntervalMs) { this.lastBowShotIntervalMs = lastBowShotIntervalMs; }

    public boolean isInventoryOpen() { return inventoryOpen; }
    public void setInventoryOpen(boolean inventoryOpen) { this.inventoryOpen = inventoryOpen; }

    public long getLastInventoryAction() { return lastInventoryAction; }
    public void setLastInventoryAction(long lastInventoryAction) { this.lastInventoryAction = lastInventoryAction; }

    public int getLastTargetEntityId() { return lastTargetEntityId; }
    public UUID getLastTargetUuid() { return lastTargetUuid; }
    public long getLastUseEntityTime() { return lastUseEntityTime; }
    public double getLastUseEntityDistance() { return lastUseEntityDistance; }
    public boolean wasLastUseEntityAttack() { return lastUseEntityAttack; }
    public boolean isBlockCurrentAttackPacket() { return blockCurrentAttackPacket; }
    public String getBlockedAttackReason() { return blockedAttackReason; }
    public Location getLastAttackEyeLocation() {
        return lastAttackEyeLocation == null ? null : lastAttackEyeLocation.clone();
    }
    public long getLastAttackSwingDeltaMs() { return lastAttackSwingDeltaMs; }

    public void setLastUseEntity(Entity target, boolean attack, double distance, Location attackEyeLocation,
                                 long swingDeltaMs, long now) {
        long previousUseEntityTime = this.lastUseEntityTime;
        if (target != null) {
            this.lastTargetEntityId = target.getEntityId();
            this.lastTargetUuid = target.getUniqueId();
            this.lastTargetEntity = target;
        } else {
            this.lastTargetEntityId = -1;
            this.lastTargetUuid = null;
            this.lastTargetEntity = null;
        }
        this.lastUseEntityTime = now;
        this.lastUseEntityAttack = attack;
        this.lastUseEntityDistance = distance;
        this.lastAttackEyeLocation = attackEyeLocation == null ? null : attackEyeLocation.clone();
        this.lastAttackSwingDeltaMs = swingDeltaMs;
        if (attack && previousUseEntityTime > 0L) {
            long interval = now - previousUseEntityTime;
            if (interval > 0L && interval < 1000L) {
                attackIntervals.addLast(interval);
                while (attackIntervals.size() > 20) attackIntervals.removeFirst();
            }
        }
        if (attack) {
            if (target != null) {
                noteCombatTarget(target.getUniqueId(), now);
            }
            recordAttackSample(now);
        }
    }

    public void recordAttackSample(long now) {
        attackTimestamps.addLast(now);
        while (attackTimestamps.size() > 40) attackTimestamps.removeFirst();

        float yawDelta = Math.abs(lastRotationYawDelta);
        if (yawDelta > 0.0F) {
            attackYawDeltas.addLast(yawDelta);
            while (attackYawDeltas.size() > 40) attackYawDeltas.removeFirst();
        }
    }

    public void recordInventoryClick(long now, int maxHistorySize) {
        if (now <= 0L) return;
        inventoryClickTimes.addLast(now);
        while (inventoryClickTimes.size() > Math.max(1, maxHistorySize)) {
            inventoryClickTimes.removeFirst();
        }
    }

    public void recordBlockBreakSample(long now, int maxBreakIntervals, int maxBreakTimestamps, long maxTrackedIntervalMs) {
        if (now <= 0L) return;

        if (lastBreakMs > 0L) {
            long interval = now - lastBreakMs;
            if (interval > 0L && interval < maxTrackedIntervalMs) {
                breakIntervals.addLast(interval);
                while (breakIntervals.size() > Math.max(1, maxBreakIntervals)) {
                    breakIntervals.removeFirst();
                }
            } else if (interval >= maxTrackedIntervalMs) {
                breakIntervals.clear();
            }
        }

        breakTimestamps.addLast(now);
        while (breakTimestamps.size() > Math.max(1, maxBreakTimestamps)) {
            breakTimestamps.removeFirst();
        }

        lastBreakMs = now;
    }

    public void recordDigStartSample(long now) {
        if (now <= 0L) return;
        lastDigStartMs = now;
    }

    public void setBlockCurrentAttackPacket(boolean blockCurrentAttackPacket) {
        this.blockCurrentAttackPacket = blockCurrentAttackPacket;
    }

    public void setBlockedAttackReason(String blockedAttackReason) {
        this.blockedAttackReason = blockedAttackReason;
    }

    public void clearCurrentAttackBlock() {
        this.blockCurrentAttackPacket = false;
        this.blockedAttackReason = null;
    }

    public boolean isBlockCurrentMovementPacket() { return blockCurrentMovementPacket; }
    public String getBlockedMovementReason() { return blockedMovementReason; }

    public void setBlockCurrentMovementPacket(boolean blockCurrentMovementPacket) {
        this.blockCurrentMovementPacket = blockCurrentMovementPacket;
    }

    public void setBlockedMovementReason(String blockedMovementReason) {
        this.blockedMovementReason = blockedMovementReason;
    }

    public void clearCurrentMovementBlock() {
        this.blockCurrentMovementPacket = false;
        this.blockedMovementReason = null;
    }

    public long getLastEatStart() { return lastEatStart; }
    public void setLastEatStart(long lastEatStart) { this.lastEatStart = lastEatStart; }

    public long getLastBlockDig() { return lastBlockDig; }
    public void setLastBlockDig(long lastBlockDig) { this.lastBlockDig = lastBlockDig; }

    public long getLastCombatInteractTime() { return lastCombatInteractTime; }
    public void setLastCombatInteractTime(long lastCombatInteractTime) { this.lastCombatInteractTime = lastCombatInteractTime; }

    public long getLastJumpTime() { return lastJumpTime; }
    public void setLastJumpTime(long lastJumpTime) { this.lastJumpTime = lastJumpTime; }

    public double getFallArcPeakY() { return fallArcPeakY; }
    public void setFallArcPeakY(double fallArcPeakY) { this.fallArcPeakY = fallArcPeakY; }
    public double getFallArcMinY() { return fallArcMinY; }
    public void setFallArcMinY(double fallArcMinY) { this.fallArcMinY = fallArcMinY; }
    public long getFallArcStartMs() { return fallArcStartMs; }
    public void setFallArcStartMs(long fallArcStartMs) { this.fallArcStartMs = fallArcStartMs; }
    public long getFallArcLandMs() { return fallArcLandMs; }
    public void setFallArcLandMs(long fallArcLandMs) { this.fallArcLandMs = fallArcLandMs; }
    public boolean isFallArcActive() { return fallArcActive; }
    public void setFallArcActive(boolean fallArcActive) { this.fallArcActive = fallArcActive; }
    public void clearFallArc() {
        fallArcPeakY = 0.0D;
        fallArcMinY = 0.0D;
        fallArcStartMs = 0L;
        fallArcLandMs = 0L;
        fallArcActive = false;
    }

    private int engineAirborneTicks;
    private int engineHoverTicks;
    private final Deque<Boolean> engineHoverDyWindow = new ArrayDeque<Boolean>(12);
    private final Deque<com.colin.vezanticheat.utils.GcdLatticeAnalysis.AngularSample> angularVelocitySamples =
            new ArrayDeque<com.colin.vezanticheat.utils.GcdLatticeAnalysis.AngularSample>(16);
    private int engineSpeedOvershootTicks;
    private double lastMoveDy;
    private int engineYPortStreak;
    private int engineMicroRatioStreak;
    private int engineHorizontalGainStreak;
    private int spiderAscendStreak;
    private int engineGlideTicks;
    private long airborneSessionStartMs;
    private double sessionMinY;
    private double sessionMaxY;
    private double sessionCumulativeDy;
    private int yReversalCount;
    private int monotonicFallTicks;
    private int creepUpTicks;
    private double flySessionLastDy;
    private boolean flyBobEvidenceActive;
    private long flyLastBobHitMs;
    private long lastReleaseUseItemMs;
    private long lastReleaseUseItemTickMs;
    private int releaseUseItemStreak;

    public int getEngineAirborneTicks() { return engineAirborneTicks; }
    public int getEngineHoverTicks() { return engineHoverTicks; }
    public int getEngineSpeedOvershootTicks() { return engineSpeedOvershootTicks; }
    public void setEngineAirborneTicks(int engineAirborneTicks) { this.engineAirborneTicks = Math.max(0, engineAirborneTicks); }
    public void setEngineHoverTicks(int engineHoverTicks) { this.engineHoverTicks = Math.max(0, engineHoverTicks); }
    public Deque<Boolean> getEngineHoverDyWindow() { return engineHoverDyWindow; }
    public void recordHoverDySample(boolean hoverLike) {
        engineHoverDyWindow.addLast(hoverLike);
        while (engineHoverDyWindow.size() > 12) {
            engineHoverDyWindow.removeFirst();
        }
    }
    public int countHoverDyInWindow() {
        int count = 0;
        for (Boolean sample : engineHoverDyWindow) {
            if (Boolean.TRUE.equals(sample)) count++;
        }
        return count;
    }
    public void setEngineSpeedOvershootTicks(int engineSpeedOvershootTicks) {
        this.engineSpeedOvershootTicks = Math.max(0, engineSpeedOvershootTicks);
    }
    public void resetEngineAirState() {
        engineAirborneTicks = 0;
        engineHoverTicks = 0;
        engineHoverDyWindow.clear();
        engineSpeedOvershootTicks = 0;
        engineYPortStreak = 0;
        engineMicroRatioStreak = 0;
        engineHorizontalGainStreak = 0;
        engineGlideTicks = 0;
        resetFlyPhysicsSession();
    }

    public void resetFlyPhysicsSession() {
        airborneSessionStartMs = 0L;
        sessionMinY = 0.0D;
        sessionMaxY = 0.0D;
        sessionCumulativeDy = 0.0D;
        yReversalCount = 0;
        monotonicFallTicks = 0;
        creepUpTicks = 0;
        flySessionLastDy = 0.0D;
        flyBobEvidenceActive = false;
    }

    public int getEngineGlideTicks() { return engineGlideTicks; }
    public void setEngineGlideTicks(int engineGlideTicks) { this.engineGlideTicks = Math.max(0, engineGlideTicks); }

    public long getAirborneSessionStartMs() { return airborneSessionStartMs; }
    public void setAirborneSessionStartMs(long airborneSessionStartMs) { this.airborneSessionStartMs = airborneSessionStartMs; }
    public double getSessionMinY() { return sessionMinY; }
    public void setSessionMinY(double sessionMinY) { this.sessionMinY = sessionMinY; }
    public double getSessionMaxY() { return sessionMaxY; }
    public void setSessionMaxY(double sessionMaxY) { this.sessionMaxY = sessionMaxY; }
    public double getSessionCumulativeDy() { return sessionCumulativeDy; }
    public void setSessionCumulativeDy(double sessionCumulativeDy) { this.sessionCumulativeDy = sessionCumulativeDy; }
    public int getYReversalCount() { return yReversalCount; }
    public void setYReversalCount(int yReversalCount) { this.yReversalCount = Math.max(0, yReversalCount); }
    public int getMonotonicFallTicks() { return monotonicFallTicks; }
    public void setMonotonicFallTicks(int monotonicFallTicks) { this.monotonicFallTicks = Math.max(0, monotonicFallTicks); }
    public int getCreepUpTicks() { return creepUpTicks; }
    public void setCreepUpTicks(int creepUpTicks) { this.creepUpTicks = Math.max(0, creepUpTicks); }
    public double getFlySessionLastDy() { return flySessionLastDy; }
    public void setFlySessionLastDy(double flySessionLastDy) { this.flySessionLastDy = flySessionLastDy; }
    public boolean isFlyBobEvidenceActive() { return flyBobEvidenceActive; }
    public void setFlyBobEvidenceActive(boolean flyBobEvidenceActive) { this.flyBobEvidenceActive = flyBobEvidenceActive; }
    public long getFlyLastBobHitMs() { return flyLastBobHitMs; }
    public void setFlyLastBobHitMs(long flyLastBobHitMs) { this.flyLastBobHitMs = flyLastBobHitMs; }

    public double getLastMoveDy() { return lastMoveDy; }
    public void setLastMoveDy(double lastMoveDy) { this.lastMoveDy = lastMoveDy; }
    public int getEngineYPortStreak() { return engineYPortStreak; }
    public void setEngineYPortStreak(int engineYPortStreak) { this.engineYPortStreak = Math.max(0, engineYPortStreak); }
    public int getEngineMicroRatioStreak() { return engineMicroRatioStreak; }
    public void setEngineMicroRatioStreak(int v) { this.engineMicroRatioStreak = Math.max(0, v); }
    public int getEngineHorizontalGainStreak() { return engineHorizontalGainStreak; }
    public void setEngineHorizontalGainStreak(int v) { this.engineHorizontalGainStreak = Math.max(0, v); }
    public int getSpiderAscendStreak() { return spiderAscendStreak; }
    public void setSpiderAscendStreak(int v) { this.spiderAscendStreak = Math.max(0, v); }
    public long getLastReleaseUseItemMs() { return lastReleaseUseItemMs; }
    public void setLastReleaseUseItemMs(long lastReleaseUseItemMs) { this.lastReleaseUseItemMs = lastReleaseUseItemMs; }
    public long getLastReleaseUseItemTickMs() { return lastReleaseUseItemTickMs; }
    public void setLastReleaseUseItemTickMs(long lastReleaseUseItemTickMs) { this.lastReleaseUseItemTickMs = lastReleaseUseItemTickMs; }
    public int getReleaseUseItemStreak() { return releaseUseItemStreak; }
    public void setReleaseUseItemStreak(int releaseUseItemStreak) { this.releaseUseItemStreak = Math.max(0, releaseUseItemStreak); }

    // FastBreak accessors
    public long getLastDigStartMs() { return lastDigStartMs; }
    public void setLastDigStartMs(long v) { this.lastDigStartMs = v; }
    public long getLastBreakMs() { return lastBreakMs; }
    public void setLastBreakMs(long v) { this.lastBreakMs = v; }
    public int getFastBreakAVerbose() { return fastBreakAVerbose; }
    public void setFastBreakAVerbose(int v) { this.fastBreakAVerbose = v; }
    public int getFastBreakBVerbose() { return fastBreakBVerbose; }
    public void setFastBreakBVerbose(int v) { this.fastBreakBVerbose = v; }
    public java.util.Deque<Long> getBreakIntervals() { return breakIntervals; }

    // Nuker accessors
    public java.util.Deque<Long> getBreakTimestamps() { return breakTimestamps; }
    public int getNukerAVerbose() { return nukerAVerbose; }
    public void setNukerAVerbose(int v) { this.nukerAVerbose = v; }
    public int getNukerBVerbose() { return nukerBVerbose; }
    public void setNukerBVerbose(int v) { this.nukerBVerbose = v; }
    public int getNukerCVerbose() { return nukerCVerbose; }
    public void setNukerCVerbose(int v) { this.nukerCVerbose = v; }
    public int getNukerDVerbose() { return nukerDVerbose; }
    public void setNukerDVerbose(int v) { this.nukerDVerbose = v; }
    public int getBedBreakReachBuffer() { return bedBreakReachBuffer; }
    public void setBedBreakReachBuffer(int v) { this.bedBreakReachBuffer = v; }
    public int getBedAuraBuffer() { return bedAuraBuffer; }
    public void setBedAuraBuffer(int v) { this.bedAuraBuffer = v; }
    public int getDigStartCount() { return digStartCount; }
    public void setDigStartCount(int v) { this.digStartCount = v; }
    public int getDigBreakCount() { return digBreakCount; }
    public void setDigBreakCount(int v) { this.digBreakCount = v; }

    public long getLastHeldItemSwitch() { return lastHeldItemSwitch; }
    public void setLastHeldItemSwitch(long lastHeldItemSwitch) { this.lastHeldItemSwitch = lastHeldItemSwitch; }

    public long getLastDamageTime() { return lastDamageTime; }
    public void setLastDamageTime(long lastDamageTime) { this.lastDamageTime = lastDamageTime; }
    public org.bukkit.event.entity.EntityDamageEvent.DamageCause getLastDamageCause() { return lastDamageCause; }
    public void setLastDamageCause(org.bukkit.event.entity.EntityDamageEvent.DamageCause lastDamageCause) {
        this.lastDamageCause = lastDamageCause;
    }
    public long getLastFallDamageTime() { return lastFallDamageTime; }
    public void setLastFallDamageTime(long lastFallDamageTime) { this.lastFallDamageTime = lastFallDamageTime; }
    public double getLastFallDamageAmount() { return lastFallDamageAmount; }
    public void setLastFallDamageAmount(double lastFallDamageAmount) { this.lastFallDamageAmount = lastFallDamageAmount; }

    public boolean isKbWindow() { return kbWindow; }
    public void setKbWindow(boolean kbWindow) { this.kbWindow = kbWindow; }

    public long getKbWindowStart() { return kbWindowStart; }
    public void setKbWindowStart(long kbWindowStart) { this.kbWindowStart = kbWindowStart; }

    public Location getKbStartLoc() { return kbStartLoc; }
    public void setKbStartLoc(Location kbStartLoc) { this.kbStartLoc = kbStartLoc; }

    public double getKbMovedH() { return kbMovedH; }
    public void setKbMovedH(double kbMovedH) { this.kbMovedH = kbMovedH; }

    public double getKbExpectedH() { return kbExpectedH; }
    public void setKbExpectedH(double kbExpectedH) { this.kbExpectedH = kbExpectedH; }

    public int getKbVerbose() { return kbVerbose; }
    public void setKbVerbose(int kbVerbose) { this.kbVerbose = kbVerbose; }

    public boolean isAuraProbeActive() { return auraProbeActive; }
    public void setAuraProbeActive(boolean auraProbeActive) { this.auraProbeActive = auraProbeActive; }

    public long getAuraProbeStart() { return auraProbeStart; }
    public void setAuraProbeStart(long auraProbeStart) { this.auraProbeStart = auraProbeStart; }

    public UUID getAuraProbeNpcUuid() { return auraProbeNpcUuid; }
    public void setAuraProbeNpcUuid(UUID auraProbeNpcUuid) { this.auraProbeNpcUuid = auraProbeNpcUuid; }

    public int getAuraProbeHits() { return auraProbeHits; }
    public void setAuraProbeHits(int auraProbeHits) { this.auraProbeHits = auraProbeHits; }

    public int getAuraProbeLockSamples() { return auraProbeLockSamples; }
    public void setAuraProbeLockSamples(int auraProbeLockSamples) { this.auraProbeLockSamples = auraProbeLockSamples; }

    public int getAuraProbeSamples() { return auraProbeSamples; }
    public void setAuraProbeSamples(int auraProbeSamples) { this.auraProbeSamples = auraProbeSamples; }

    public long getAuraProbeLastSample() { return auraProbeLastSample; }
    public void setAuraProbeLastSample(long auraProbeLastSample) { this.auraProbeLastSample = auraProbeLastSample; }

    public void markTeleportExempt(long ms) {
        long now = System.currentTimeMillis();
        teleportExemptUntilMs = Math.max(teleportExemptUntilMs, now + Math.max(0L, ms));
    }
    public void markVelocityExempt(long ms) {
        long now = System.currentTimeMillis();
        velocityExemptUntilMs = Math.max(velocityExemptUntilMs, now + Math.max(0L, ms));
    }
    public void markBlockStateExempt(long ms) {
        long now = System.currentTimeMillis();
        blockStateExemptUntilMs = Math.max(blockStateExemptUntilMs, now + Math.max(0L, ms));
    }
    public void markPotionExempt(long ms) {
        long now = System.currentTimeMillis();
        potionExemptUntilMs = Math.max(potionExemptUntilMs, now + Math.max(0L, ms));
    }
    public void markInventoryMomentumExempt(long ms) {
        long now = System.currentTimeMillis();
        inventoryMomentumExemptUntilMs = Math.max(inventoryMomentumExemptUntilMs, now + Math.max(0L, ms));
    }

    public void markEatMovementGrace(long ms) {
        long now = System.currentTimeMillis();
        eatMovementGraceUntilMs = Math.max(eatMovementGraceUntilMs, now + Math.max(0L, ms));
    }

    public boolean isEatMovementGrace() {
        return System.currentTimeMillis() < eatMovementGraceUntilMs;
    }

    public boolean isActivelyEating() {
        return lastEatStart > 0L;
    }

    public void clearReleaseUseItemState() {
        lastReleaseUseItemMs = 0L;
        lastReleaseUseItemTickMs = 0L;
        releaseUseItemStreak = 0;
    }

    /** Reset movement prediction debt after legit consume / item-use completion. */
    public void resetPostConsumeMovementState() {
        engineOffsetAdvantage = 0.0D;
        resetEngineAirState();
        clearReleaseUseItemState();
        useItemActive = false;
        useItemStartMs = 0L;
    }

    public boolean isTeleportExempt() { return System.currentTimeMillis() < teleportExemptUntilMs; }
    public boolean isVelocityExempt() { return System.currentTimeMillis() < velocityExemptUntilMs; }
    public boolean isBlockStateExempt() { return System.currentTimeMillis() < blockStateExemptUntilMs; }
    public boolean isPotionExempt() { return System.currentTimeMillis() < potionExemptUntilMs; }
    public boolean isInventoryMomentumExempt() { return System.currentTimeMillis() < inventoryMomentumExemptUntilMs; }

    public long getEatMovementGraceUntilMs() { return eatMovementGraceUntilMs; }

    public long getTeleportExemptUntilMs() { return teleportExemptUntilMs; }
    public long getVelocityExemptUntilMs() { return velocityExemptUntilMs; }
    public long getBlockStateExemptUntilMs() { return blockStateExemptUntilMs; }
    public long getPotionExemptUntilMs() { return potionExemptUntilMs; }

    public long getLastClientPacketTime() { return lastClientPacketTime; }
    public void setLastClientPacketTime(long lastClientPacketTime) { this.lastClientPacketTime = lastClientPacketTime; }

    public int getBadPacketVerbose() { return getBadPacketsVerbose('A'); }
    public void setBadPacketVerbose(int badPacketVerbose) { setBadPacketsVerbose('A', badPacketVerbose); }

    public com.colin.vezanticheat.utils.BadPacketTracker badPackets() { return badPacketTracker; }

    public int getBadPacketsVerbose(char letter) {
        int idx = letterIndex(letter);
        return idx < 0 ? 0 : badPacketsVerbose[idx];
    }

    public void setBadPacketsVerbose(char letter, int value) {
        int idx = letterIndex(letter);
        if (idx >= 0) badPacketsVerbose[idx] = Math.max(0, value);
    }

    private static int letterIndex(char letter) {
        char c = Character.toUpperCase(letter);
        if (c < 'A' || c > 'Z') return -1;
        return c - 'A';
    }

    public int getBadPacketsTransactionAVerbose() { return badPacketsTransactionAVerbose; }
    public void setBadPacketsTransactionAVerbose(int v) { this.badPacketsTransactionAVerbose = Math.max(0, v); }
    public int getBadPacketsTransactionBVerbose() { return badPacketsTransactionBVerbose; }
    public void setBadPacketsTransactionBVerbose(int v) { this.badPacketsTransactionBVerbose = Math.max(0, v); }
    public int getSetbackAcceptAVerbose() { return setbackAcceptAVerbose; }
    public void setSetbackAcceptAVerbose(int v) { this.setbackAcceptAVerbose = Math.max(0, v); }
    public int getPositionPacketsThisTick() { return positionPacketsThisTick; }
    public void incrementPositionPacketsThisTick(long serverTick) {
        if (positionPacketTickId != serverTick) {
            positionPacketTickId = serverTick;
            positionPacketsThisTick = 0;
        }
        positionPacketsThisTick++;
    }
    public void resetPositionPacketsThisTick() {
        positionPacketsThisTick = 0;
    }
    public boolean isUseItemActive() { return useItemActive; }
    public void setUseItemActive(boolean useItemActive) { this.useItemActive = useItemActive; }
    public long getUseItemStartMs() { return useItemStartMs; }
    public void setUseItemStartMs(long useItemStartMs) { this.useItemStartMs = useItemStartMs; }
    public int getMultiActionsCVerbose() { return multiActionsCVerbose; }
    public void setMultiActionsCVerbose(int v) { this.multiActionsCVerbose = Math.max(0, v); }
    public int getMultiActionsEVerbose() { return multiActionsEVerbose; }
    public void setMultiActionsEVerbose(int v) { this.multiActionsEVerbose = Math.max(0, v); }
    public int getAutoBlockCVerbose() { return autoBlockCVerbose; }
    public void setAutoBlockCVerbose(int v) { this.autoBlockCVerbose = Math.max(0, v); }
    public long getLastMicroYOffsetMs() { return lastMicroYOffsetMs; }
    public void setLastMicroYOffsetMs(long lastMicroYOffsetMs) { this.lastMicroYOffsetMs = lastMicroYOffsetMs; }

    public boolean isBlockCurrentDigPacket() { return blockCurrentDigPacket; }
    public String getBlockedDigReason() { return blockedDigReason; }
    public void setBlockCurrentDigPacket(boolean blockCurrentDigPacket) { this.blockCurrentDigPacket = blockCurrentDigPacket; }
    public void setBlockedDigReason(String blockedDigReason) { this.blockedDigReason = blockedDigReason; }
    public void clearCurrentDigBlock() {
        this.blockCurrentDigPacket = false;
        this.blockedDigReason = null;
    }

    public boolean isBlockCurrentPlacePacket() { return blockCurrentPlacePacket; }
    public String getBlockedPlaceReason() { return blockedPlaceReason; }
    public void setBlockCurrentPlacePacket(boolean blockCurrentPlacePacket) { this.blockCurrentPlacePacket = blockCurrentPlacePacket; }
    public void setBlockedPlaceReason(String blockedPlaceReason) { this.blockedPlaceReason = blockedPlaceReason; }
    public void clearCurrentPlaceBlock() {
        this.blockCurrentPlacePacket = false;
        this.blockedPlaceReason = null;
    }

    public boolean isBlockCurrentWindowPacket() { return blockCurrentWindowPacket; }
    public String getBlockedWindowReason() { return blockedWindowReason; }
    public void setBlockCurrentWindowPacket(boolean blockCurrentWindowPacket) {
        this.blockCurrentWindowPacket = blockCurrentWindowPacket;
    }
    public void setBlockedWindowReason(String blockedWindowReason) { this.blockedWindowReason = blockedWindowReason; }
    public void clearCurrentWindowBlock() {
        this.blockCurrentWindowPacket = false;
        this.blockedWindowReason = null;
    }

    public int getLastPacketInteractEntityId() { return lastPacketInteractEntityId; }
    public boolean wasLastPacketInteractAttack() { return lastPacketInteractWasAttack; }
    public void setLastPacketInteract(int entityId, boolean attack) {
        this.lastPacketInteractEntityId = entityId;
        this.lastPacketInteractWasAttack = attack;
    }

    public long getLastScaffoldPlaceTime() { return lastScaffoldPlaceTime; }
    public void setLastScaffoldPlaceTime(long lastScaffoldPlaceTime) { this.lastScaffoldPlaceTime = lastScaffoldPlaceTime; }

    public Location getLastPlacedBlockLoc() { return lastPlacedBlockLoc; }
    public void setLastPlacedBlockLoc(Location lastPlacedBlockLoc) { this.lastPlacedBlockLoc = lastPlacedBlockLoc; }

    public Location getLastPlaceAgainstLoc() { return lastPlaceAgainstLoc; }
    public void setLastPlaceAgainstLoc(Location lastPlaceAgainstLoc) { this.lastPlaceAgainstLoc = lastPlaceAgainstLoc; }

    public BlockFace getLastPlaceFace() { return lastPlaceFace; }
    public void setLastPlaceFace(BlockFace lastPlaceFace) { this.lastPlaceFace = lastPlaceFace; }

    public float getLastPlaceYaw() { return lastPlaceYaw; }
    public void setLastPlaceYaw(float lastPlaceYaw) { this.lastPlaceYaw = lastPlaceYaw; }

    public float getLastPlacePitch() { return lastPlacePitch; }
    public void setLastPlacePitch(float lastPlacePitch) { this.lastPlacePitch = lastPlacePitch; }

    public boolean isLastPlaceOnGround() { return lastPlaceOnGround; }
    public void setLastPlaceOnGround(boolean lastPlaceOnGround) { this.lastPlaceOnGround = lastPlaceOnGround; }

    public boolean isLastPlaceSneaking() { return lastPlaceSneaking; }
    public void setLastPlaceSneaking(boolean lastPlaceSneaking) { this.lastPlaceSneaking = lastPlaceSneaking; }

    public Deque<Long> getScaffoldIntervals() { return scaffoldIntervals; }
    public Deque<Float> getScaffoldPitchHistory() { return scaffoldPitchHistory; }

    public int getScaffoldVerboseA() { return scaffoldVerboseA; }
    public void setScaffoldVerboseA(int scaffoldVerboseA) { this.scaffoldVerboseA = scaffoldVerboseA; }

    public int getScaffoldVerboseB() { return scaffoldVerboseB; }
    public void setScaffoldVerboseB(int scaffoldVerboseB) { this.scaffoldVerboseB = scaffoldVerboseB; }

    public int getScaffoldVerboseC() { return scaffoldVerboseC; }
    public void setScaffoldVerboseC(int scaffoldVerboseC) { this.scaffoldVerboseC = scaffoldVerboseC; }

    public int getScaffoldVerboseD() { return scaffoldVerboseD; }
    public void setScaffoldVerboseD(int scaffoldVerboseD) { this.scaffoldVerboseD = scaffoldVerboseD; }
    public int getScaffoldVerboseE() { return scaffoldVerboseE; }
    public void setScaffoldVerboseE(int scaffoldVerboseE) { this.scaffoldVerboseE = scaffoldVerboseE; }
    public int getScaffoldVerboseF() { return scaffoldVerboseF; }
    public void setScaffoldVerboseF(int scaffoldVerboseF) { this.scaffoldVerboseF = scaffoldVerboseF; }
    public int getScaffoldVerboseG() { return scaffoldVerboseG; }
    public void setScaffoldVerboseG(int scaffoldVerboseG) { this.scaffoldVerboseG = scaffoldVerboseG; }

    public int getPrismPerfectPlacementStreak() { return prismPerfectPlacementStreak; }
    public void setPrismPerfectPlacementStreak(int prismPerfectPlacementStreak) {
        this.prismPerfectPlacementStreak = Math.max(0, prismPerfectPlacementStreak);
    }

    public long getLastBlockPlacePacketTime() { return lastBlockPlacePacketTime; }
    public Location getLastBlockPlacePacketAgainstLoc() {
        return lastBlockPlacePacketAgainstLoc == null ? null : lastBlockPlacePacketAgainstLoc.clone();
    }
    public int getLastBlockPlacePacketFaceId() { return lastBlockPlacePacketFaceId; }
    public float getLastBlockPlacePacketCursorX() { return lastBlockPlacePacketCursorX; }
    public float getLastBlockPlacePacketCursorY() { return lastBlockPlacePacketCursorY; }
    public float getLastBlockPlacePacketCursorZ() { return lastBlockPlacePacketCursorZ; }
    public float getLastBlockPlacePacketYaw() { return lastBlockPlacePacketYaw; }
    public float getLastBlockPlacePacketPitch() { return lastBlockPlacePacketPitch; }
    public Location getLastBlockPlacePacketLoc() {
        return lastBlockPlacePacketLoc == null ? null : lastBlockPlacePacketLoc.clone();
    }
    public int getScaffoldPacketCountThisFlying() { return scaffoldPacketCountThisFlying; }
    public void setScaffoldPacketCountThisFlying(int scaffoldPacketCountThisFlying) { this.scaffoldPacketCountThisFlying = scaffoldPacketCountThisFlying; }
    public boolean isScaffoldPacketMismatchThisFlying() { return scaffoldPacketMismatchThisFlying; }
    public void setScaffoldPacketMismatchThisFlying(boolean scaffoldPacketMismatchThisFlying) { this.scaffoldPacketMismatchThisFlying = scaffoldPacketMismatchThisFlying; }
    public boolean isScaffoldRotationPending() { return scaffoldRotationPending; }
    public void setScaffoldRotationPending(boolean scaffoldRotationPending) { this.scaffoldRotationPending = scaffoldRotationPending; }
    public boolean isScaffoldRotationSampleReady() { return scaffoldRotationSampleReady; }
    public void setScaffoldRotationSampleReady(boolean scaffoldRotationSampleReady) { this.scaffoldRotationSampleReady = scaffoldRotationSampleReady; }
    public float getScaffoldPostPlaceYawDelta() { return scaffoldPostPlaceYawDelta; }
    public void setScaffoldPostPlaceYawDelta(float scaffoldPostPlaceYawDelta) { this.scaffoldPostPlaceYawDelta = scaffoldPostPlaceYawDelta; }
    public float getScaffoldPostPlacePitchDelta() { return scaffoldPostPlacePitchDelta; }
    public void setScaffoldPostPlacePitchDelta(float scaffoldPostPlacePitchDelta) { this.scaffoldPostPlacePitchDelta = scaffoldPostPlacePitchDelta; }
    public Deque<BlockStateSample> getRecentBlockStateHistory() { return recentBlockStateHistory; }

    public void setLastBlockPlacePacket(Location againstLoc, int faceId, float cursorX, float cursorY, float cursorZ,
                                        float yaw, float pitch, Location packetLoc, long now) {
        this.lastBlockPlacePacketTime = now;
        this.lastBlockPlacePacketAgainstLoc = againstLoc == null ? null : againstLoc.clone();
        this.lastBlockPlacePacketFaceId = faceId;
        this.lastBlockPlacePacketCursorX = cursorX;
        this.lastBlockPlacePacketCursorY = cursorY;
        this.lastBlockPlacePacketCursorZ = cursorZ;
        this.lastBlockPlacePacketYaw = yaw;
        this.lastBlockPlacePacketPitch = pitch;
        this.lastBlockPlacePacketLoc = packetLoc == null ? null : packetLoc.clone();

        String signature = buildScaffoldPacketSignature(againstLoc, faceId, cursorX, cursorY, cursorZ);
        if (scaffoldPacketCountThisFlying > 0 && lastBlockPlacePacketSignature != null
                && !lastBlockPlacePacketSignature.equals(signature)) {
            this.scaffoldPacketMismatchThisFlying = true;
        }
        this.lastBlockPlacePacketSignature = signature;
        this.scaffoldPacketCountThisFlying++;
        this.scaffoldRotationPending = true;
        this.scaffoldRotationSampleReady = false;
    }

    public void resetScaffoldPacketWindow() {
        this.scaffoldPacketCountThisFlying = 0;
        this.scaffoldPacketMismatchThisFlying = false;
        this.lastBlockPlacePacketSignature = null;
    }

    public void completeScaffoldRotationSample(float yawDelta, float pitchDelta) {
        this.scaffoldPostPlaceYawDelta = yawDelta;
        this.scaffoldPostPlacePitchDelta = pitchDelta;
        this.scaffoldRotationPending = false;
        this.scaffoldRotationSampleReady = true;
    }

    public void recordBlockState(Location loc, Material oldType, Material newType, long now) {
        if (loc == null || loc.getWorld() == null) return;
        recentBlockStateHistory.addLast(new BlockStateSample(
                now,
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                oldType,
                newType
        ));
        long cutoff = now - 500L;
        while (recentBlockStateHistory.size() > 20) {
            recentBlockStateHistory.removeFirst();
        }
        while (!recentBlockStateHistory.isEmpty() && recentBlockStateHistory.peekFirst().getTime() < cutoff) {
            recentBlockStateHistory.removeFirst();
        }
    }

    private String buildScaffoldPacketSignature(Location loc, int faceId, float cursorX, float cursorY, float cursorZ) {
        if (loc == null || loc.getWorld() == null) {
            return "null:" + faceId + ":" + cursorX + ":" + cursorY + ":" + cursorZ;
        }
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ()
                + ":" + faceId + ":" + cursorX + ":" + cursorY + ":" + cursorZ;
    }

    // =========================================================
    // Per-check verbose accessors
    // =========================================================
    public int getAimAssistAVerbose() { return aimAssistAVerbose; }
    public void setAimAssistAVerbose(int v) { this.aimAssistAVerbose = v; }
    public int getAimAssistBVerbose() { return aimAssistBVerbose; }
    public void setAimAssistBVerbose(int v) { this.aimAssistBVerbose = v; }
    public int getAimAssistCVerbose() { return aimAssistCVerbose; }
    public void setAimAssistCVerbose(int v) { this.aimAssistCVerbose = v; }
    public int getAutoClickAVerbose() { return autoClickAVerbose; }
    public void setAutoClickAVerbose(int v) { this.autoClickAVerbose = v; }
    public int getAutoClickBVerbose() { return autoClickBVerbose; }
    public void setAutoClickBVerbose(int v) { this.autoClickBVerbose = v; }
    public int getAutoClickCVerbose() { return autoClickCVerbose; }
    public void setAutoClickCVerbose(int v) { this.autoClickCVerbose = v; }
    public int getCriticalsBVerbose() { return criticalsBVerbose; }
    public void setCriticalsBVerbose(int v) { this.criticalsBVerbose = v; }
    public int getCriticalsCVerbose() { return criticalsCVerbose; }
    public void setCriticalsCVerbose(int v) { this.criticalsCVerbose = v; }

    // =========================================================
    // Rotation delta history accessors
    // =========================================================
    public Deque<Float> getYawDeltas() { return yawDeltas; }
    public Deque<Float> getPitchDeltas() { return pitchDeltas; }
    public Deque<Double> getAimCenterErrors() { return aimCenterErrors; }
    public Deque<Double> getAimCenterMargins() { return aimCenterMargins; }

    // =========================================================
    // Criticals Y tracking accessors
    // =========================================================
    public Deque<Double> getRecentYDeltas() { return recentYDeltas; }
    public double getLastTrackY() { return lastTrackY; }
    public void setLastTrackY(double y) { this.lastTrackY = y; }
    public boolean hasLastTrackY() { return hasLastTrackY; }
    public void setHasLastTrackY(boolean b) { this.hasLastTrackY = b; }
    public int getCriticalHits() { return criticalHits; }
    public void setCriticalHits(int c) { this.criticalHits = c; }
    public int getTotalAttacks() { return totalAttacks; }
    public void setTotalAttacks(int t) { this.totalAttacks = t; }
    public long getLastCritResetTime() { return lastCritResetTime; }
    public void setLastCritResetTime(long t) { this.lastCritResetTime = t; }

    // =========================================================
    // Click interval accessors
    // =========================================================
    public Deque<Long> getClickIntervals() { return clickIntervals; }

    // =========================================================
    // Velocity vertical accessors
    // =========================================================
    public double getKbExpectedV() { return kbExpectedV; }
    public void setKbExpectedV(double v) { this.kbExpectedV = v; }
    public double getKbMovedV() { return kbMovedV; }
    public void setKbMovedV(double v) { this.kbMovedV = v; }

    // =========================================================
    // Attack timing accessors
    // =========================================================
    public Deque<Long> getAttackTimestamps() { return attackTimestamps; }
    public Deque<Float> getAttackYawDeltas() { return attackYawDeltas; }
    public Deque<Long> getAttackIntervals() { return attackIntervals; }
    public UUID getKillAuraALastTargetUuid() { return killAuraALastTargetUuid; }
    public void setKillAuraALastTargetUuid(UUID killAuraALastTargetUuid) { this.killAuraALastTargetUuid = killAuraALastTargetUuid; }
    public long getKillAuraALastHitMs() { return killAuraALastHitMs; }
    public void setKillAuraALastHitMs(long killAuraALastHitMs) { this.killAuraALastHitMs = killAuraALastHitMs; }
    public int getKillAuraASwitchBuffer() { return killAuraASwitchBuffer; }
    public void setKillAuraASwitchBuffer(int killAuraASwitchBuffer) { this.killAuraASwitchBuffer = killAuraASwitchBuffer; }
    public double getKillAuraAPrevCv() { return killAuraAPrevCv; }
    public void setKillAuraAPrevCv(double killAuraAPrevCv) { this.killAuraAPrevCv = killAuraAPrevCv; }
    public boolean hasKillAuraAPrevCv() { return killAuraAHasPrevCv; }
    public void setKillAuraAHasPrevCv(boolean killAuraAHasPrevCv) { this.killAuraAHasPrevCv = killAuraAHasPrevCv; }
    public int getKillAuraACleanStreak() { return killAuraACleanStreak; }
    public void setKillAuraACleanStreak(int v) { this.killAuraACleanStreak = v; }
    public long getKillAuraAPostResetStartMs() { return killAuraAPostResetStartMs; }
    public void setKillAuraAPostResetStartMs(long v) { this.killAuraAPostResetStartMs = v; }
    public float getKillAuraAPostResetBaseYaw() { return killAuraAPostResetBaseYaw; }
    public void setKillAuraAPostResetBaseYaw(float v) { this.killAuraAPostResetBaseYaw = v; }
    public float getKillAuraAPostResetBasePitch() { return killAuraAPostResetBasePitch; }
    public void setKillAuraAPostResetBasePitch(float v) { this.killAuraAPostResetBasePitch = v; }
    public float getKillAuraAPostResetAttackYaw() { return killAuraAPostResetAttackYaw; }
    public void setKillAuraAPostResetAttackYaw(float v) { this.killAuraAPostResetAttackYaw = v; }
    public float getKillAuraAPostResetAttackPitch() { return killAuraAPostResetAttackPitch; }
    public void setKillAuraAPostResetAttackPitch(float v) { this.killAuraAPostResetAttackPitch = v; }
    public boolean isKillAuraAPostResetActive() { return killAuraAPostResetActive; }
    public void setKillAuraAPostResetActive(boolean v) { this.killAuraAPostResetActive = v; }
    public int getKillAuraAPostResetConfirmed() { return killAuraAPostResetConfirmed; }
    public void setKillAuraAPostResetConfirmed(int v) { this.killAuraAPostResetConfirmed = v; }
    public Deque<Double> getKillAuraAMismatchAngles() { return killAuraAMismatchAngles; }
    public Deque<Double> getKillAuraACenterErrors() { return killAuraACenterErrors; }
    public int getKillAuraARotOnAttackTicks() { return killAuraARotOnAttackTicks; }
    public void setKillAuraARotOnAttackTicks(int v) { this.killAuraARotOnAttackTicks = v; }
    public int getKillAuraARotOnNonAttackTicks() { return killAuraARotOnNonAttackTicks; }
    public void setKillAuraARotOnNonAttackTicks(int v) { this.killAuraARotOnNonAttackTicks = v; }
    public int getKillAuraANoRotOnNonAttackTicks() { return killAuraANoRotOnNonAttackTicks; }
    public void setKillAuraANoRotOnNonAttackTicks(int v) { this.killAuraANoRotOnNonAttackTicks = v; }
    public long getKillAuraACorrelationWindowStart() { return killAuraACorrelationWindowStart; }
    public void setKillAuraACorrelationWindowStart(long v) { this.killAuraACorrelationWindowStart = v; }

    // AutoBlock accessors
    public long getAutoBlockALastBlockStartMs() { return autoBlockALastBlockStartMs; }
    public void setAutoBlockALastBlockStartMs(long v) { this.autoBlockALastBlockStartMs = v; }
    public Deque<Long> getAutoBlockABlockAttackIntervals() { return autoBlockABlockAttackIntervals; }
    public int getAutoBlockACyclesInWindow() { return autoBlockACyclesInWindow; }
    public void setAutoBlockACyclesInWindow(int v) { this.autoBlockACyclesInWindow = v; }
    public long getAutoBlockAWindowStart() { return autoBlockAWindowStart; }
    public void setAutoBlockAWindowStart(long v) { this.autoBlockAWindowStart = v; }
    public int getAutoBlockABuffer() { return autoBlockABuffer; }
    public void setAutoBlockABuffer(int v) { this.autoBlockABuffer = v; }
    public int getAutoBlockBGapsDuringBlock() { return autoBlockBGapsDuringBlock; }
    public void setAutoBlockBGapsDuringBlock(int v) { this.autoBlockBGapsDuringBlock = v; }
    public int getAutoBlockBTotalCombatTicks() { return autoBlockBTotalCombatTicks; }
    public void setAutoBlockBTotalCombatTicks(int v) { this.autoBlockBTotalCombatTicks = v; }
    public int getAutoBlockBBlockedCombatTicks() { return autoBlockBBlockedCombatTicks; }
    public void setAutoBlockBBlockedCombatTicks(int v) { this.autoBlockBBlockedCombatTicks = v; }
    public int getAutoBlockBAttacksDuringBurst() { return autoBlockBAttacksDuringBurst; }
    public void setAutoBlockBAttacksDuringBurst(int v) { this.autoBlockBAttacksDuringBurst = v; }
    public int getAutoBlockBTotalAttacks() { return autoBlockBTotalAttacks; }
    public void setAutoBlockBTotalAttacks(int v) { this.autoBlockBTotalAttacks = v; }
    public long getAutoBlockBWindowStart() { return autoBlockBWindowStart; }
    public void setAutoBlockBWindowStart(long v) { this.autoBlockBWindowStart = v; }
    public int getAutoBlockBBuffer() { return autoBlockBBuffer; }
    public void setAutoBlockBBuffer(int v) { this.autoBlockBBuffer = v; }

    public int getKillAuraBBadBuffer() { return killAuraBBadBuffer; }
    public void setKillAuraBBadBuffer(int killAuraBBadBuffer) { this.killAuraBBadBuffer = killAuraBBadBuffer; }
    public long getKillAuraBLastBadMs() { return killAuraBLastBadMs; }
    public void setKillAuraBLastBadMs(long killAuraBLastBadMs) { this.killAuraBLastBadMs = killAuraBLastBadMs; }
    public int getKillAuraCBuffer() { return killAuraCBuffer; }
    public void setKillAuraCBuffer(int killAuraCBuffer) { this.killAuraCBuffer = killAuraCBuffer; }
    public long getKillAuraCLastFlagStateMs() { return killAuraCLastFlagStateMs; }
    public void setKillAuraCLastFlagStateMs(long killAuraCLastFlagStateMs) { this.killAuraCLastFlagStateMs = killAuraCLastFlagStateMs; }
    public long getKillAuraCLastSnapMs() { return killAuraCLastSnapMs; }
    public void setKillAuraCLastSnapMs(long killAuraCLastSnapMs) { this.killAuraCLastSnapMs = killAuraCLastSnapMs; }
    public int getKillAuraDBuffer() { return killAuraDBuffer; }
    public void setKillAuraDBuffer(int killAuraDBuffer) { this.killAuraDBuffer = killAuraDBuffer; }
    public long getKillAuraDLastStateMs() { return killAuraDLastStateMs; }
    public void setKillAuraDLastStateMs(long killAuraDLastStateMs) { this.killAuraDLastStateMs = killAuraDLastStateMs; }
    public int getKillAuraDConsecutiveWindows() { return killAuraDConsecutiveWindows; }
    public void setKillAuraDConsecutiveWindows(int killAuraDConsecutiveWindows) { this.killAuraDConsecutiveWindows = killAuraDConsecutiveWindows; }
    public long getKillAuraDLastWindowMs() { return killAuraDLastWindowMs; }
    public void setKillAuraDLastWindowMs(long killAuraDLastWindowMs) { this.killAuraDLastWindowMs = killAuraDLastWindowMs; }
    public UUID getKillAuraFLastTargetUuid() { return killAuraFLastTargetUuid; }
    public void setKillAuraFLastTargetUuid(UUID killAuraFLastTargetUuid) { this.killAuraFLastTargetUuid = killAuraFLastTargetUuid; }
    public Location getKillAuraFLastTargetLoc() { return killAuraFLastTargetLoc; }
    public void setKillAuraFLastTargetLoc(Location killAuraFLastTargetLoc) { this.killAuraFLastTargetLoc = killAuraFLastTargetLoc; }
    public float getKillAuraFLastYaw() { return killAuraFLastYaw; }
    public void setKillAuraFLastYaw(float killAuraFLastYaw) { this.killAuraFLastYaw = killAuraFLastYaw; }
    public float getKillAuraFLastPitch() { return killAuraFLastPitch; }
    public void setKillAuraFLastPitch(float killAuraFLastPitch) { this.killAuraFLastPitch = killAuraFLastPitch; }
    public long getKillAuraFLastAttackMs() { return killAuraFLastAttackMs; }
    public void setKillAuraFLastAttackMs(long killAuraFLastAttackMs) { this.killAuraFLastAttackMs = killAuraFLastAttackMs; }
    public int getKillAuraFLockBuffer() { return killAuraFLockBuffer; }
    public void setKillAuraFLockBuffer(int killAuraFLockBuffer) { this.killAuraFLockBuffer = killAuraFLockBuffer; }
    public int getKillAuraGBuffer() { return killAuraGBuffer; }
    public void setKillAuraGBuffer(int killAuraGBuffer) { this.killAuraGBuffer = killAuraGBuffer; }

    // KillAuraH accessors
    public float getKauraHLastDeltaYaw() { return kauraHLastDeltaYaw; }
    public void setKauraHLastDeltaYaw(float v) { this.kauraHLastDeltaYaw = v; }
    public float getKauraHLastDeltaPitch() { return kauraHLastDeltaPitch; }
    public void setKauraHLastDeltaPitch(float v) { this.kauraHLastDeltaPitch = v; }
    public Deque<Float> getKauraHYawGcds() { return kauraHYawGcds; }
    public Deque<Float> getKauraHPitchGcds() { return kauraHPitchGcds; }
    public float getKauraHLearnedYawGcd() { return kauraHLearnedYawGcd; }
    public void setKauraHLearnedYawGcd(float v) { this.kauraHLearnedYawGcd = v; }
    public float getKauraHLearnedPitchGcd() { return kauraHLearnedPitchGcd; }
    public void setKauraHLearnedPitchGcd(float v) { this.kauraHLearnedPitchGcd = v; }
    public boolean isKauraHYawGcdReady() { return kauraHYawGcdReady; }
    public void setKauraHYawGcdReady(boolean v) { this.kauraHYawGcdReady = v; }
    public boolean isKauraHPitchGcdReady() { return kauraHPitchGcdReady; }
    public void setKauraHPitchGcdReady(boolean v) { this.kauraHPitchGcdReady = v; }
    public int getKauraHBuffer() { return kauraHBuffer; }
    public void setKauraHBuffer(int v) { this.kauraHBuffer = v; }

    public long getSpeedLastVelocityExemptMs() { return speedLastVelocityExemptMs; }
    public void setSpeedLastVelocityExemptMs(long speedLastVelocityExemptMs) { this.speedLastVelocityExemptMs = speedLastVelocityExemptMs; }
    public int getSpeedAGroundVerbose() { return speedAGroundVerbose; }
    public void setSpeedAGroundVerbose(int speedAGroundVerbose) { this.speedAGroundVerbose = speedAGroundVerbose; }
    public int getSpeedAAirVerbose() { return speedAAirVerbose; }
    public void setSpeedAAirVerbose(int speedAAirVerbose) { this.speedAAirVerbose = speedAAirVerbose; }
    public double getNoFallAPeakY() { return noFallAPeakY; }
    public void setNoFallAPeakY(double noFallAPeakY) { this.noFallAPeakY = noFallAPeakY; }
    public boolean hasNoFallAPeakY() { return noFallAHasPeakY; }
    public void setNoFallAHasPeakY(boolean noFallAHasPeakY) { this.noFallAHasPeakY = noFallAHasPeakY; }
    public boolean isNoFallAWasOnGround() { return noFallAWasOnGround; }
    public void setNoFallAWasOnGround(boolean noFallAWasOnGround) { this.noFallAWasOnGround = noFallAWasOnGround; }
    public long getNoFallALandAtMs() { return noFallALandAtMs; }
    public void setNoFallALandAtMs(long noFallALandAtMs) { this.noFallALandAtMs = noFallALandAtMs; }
    public double getNoFallALandFall() { return noFallALandFall; }
    public void setNoFallALandFall(double noFallALandFall) { this.noFallALandFall = noFallALandFall; }
    public long getNoFallAExpectedDamageAfterMs() { return noFallAExpectedDamageAfterMs; }
    public void setNoFallAExpectedDamageAfterMs(long noFallAExpectedDamageAfterMs) { this.noFallAExpectedDamageAfterMs = noFallAExpectedDamageAfterMs; }
    public int getNoFallABuffer() { return noFallABuffer; }
    public void setNoFallABuffer(int noFallABuffer) { this.noFallABuffer = noFallABuffer; }
    public double getNoFallCPeakY() { return noFallCPeakY; }
    public void setNoFallCPeakY(double noFallCPeakY) { this.noFallCPeakY = noFallCPeakY; }
    public boolean hasNoFallCPeakY() { return noFallCHasPeakY; }
    public void setNoFallCHasPeakY(boolean noFallCHasPeakY) { this.noFallCHasPeakY = noFallCHasPeakY; }
    public float getNoFallCMaxReportedFall() { return noFallCMaxReportedFall; }
    public void setNoFallCMaxReportedFall(float noFallCMaxReportedFall) { this.noFallCMaxReportedFall = noFallCMaxReportedFall; }
    public int getNoFallCResetCount() { return noFallCResetCount; }
    public void setNoFallCResetCount(int noFallCResetCount) { this.noFallCResetCount = noFallCResetCount; }
    public int getNoFallCBuffer() { return noFallCBuffer; }
    public void setNoFallCBuffer(int noFallCBuffer) { this.noFallCBuffer = noFallCBuffer; }
    public int getFlyAAirTicks() { return flyAAirTicks; }
    public void setFlyAAirTicks(int flyAAirTicks) { this.flyAAirTicks = flyAAirTicks; }
    public int getFlyABuffer() { return flyABuffer; }
    public void setFlyABuffer(int flyABuffer) { this.flyABuffer = flyABuffer; }
    public int getFlyBAirTicks() { return flyBAirTicks; }
    public void setFlyBAirTicks(int flyBAirTicks) { this.flyBAirTicks = flyBAirTicks; }
    public int getFlyBBuffer() { return flyBBuffer; }
    public void setFlyBBuffer(int flyBBuffer) { this.flyBBuffer = flyBBuffer; }
    public int getFlyCAirTicks() { return flyCAirTicks; }
    public void setFlyCAirTicks(int flyCAirTicks) { this.flyCAirTicks = flyCAirTicks; }
    public int getFlyCBuffer() { return flyCBuffer; }
    public void setFlyCBuffer(int flyCBuffer) { this.flyCBuffer = flyCBuffer; }
    public int getFlyDAirTicks() { return flyDAirTicks; }
    public void setFlyDAirTicks(int flyDAirTicks) { this.flyDAirTicks = flyDAirTicks; }
    public int getFlyDUpTicks() { return flyDUpTicks; }
    public void setFlyDUpTicks(int flyDUpTicks) { this.flyDUpTicks = flyDUpTicks; }
    public int getFlyDBuffer() { return flyDBuffer; }
    public void setFlyDBuffer(int flyDBuffer) { this.flyDBuffer = flyDBuffer; }
    public long getFlyDLaunchExemptUntilMs() { return flyDLaunchExemptUntilMs; }
    public void setFlyDLaunchExemptUntilMs(long flyDLaunchExemptUntilMs) { this.flyDLaunchExemptUntilMs = flyDLaunchExemptUntilMs; }
    public int getFlyEAirTicks() { return flyEAirTicks; }
    public void setFlyEAirTicks(int flyEAirTicks) { this.flyEAirTicks = flyEAirTicks; }
    public int getFlyEBuffer() { return flyEBuffer; }
    public void setFlyEBuffer(int flyEBuffer) { this.flyEBuffer = flyEBuffer; }
    public int getFlyFAirTicks() { return flyFAirTicks; }
    public void setFlyFAirTicks(int flyFAirTicks) { this.flyFAirTicks = flyFAirTicks; }
    public int getFlyFBuffer() { return flyFBuffer; }
    public void setFlyFBuffer(int flyFBuffer) { this.flyFBuffer = flyFBuffer; }
    public int getSimulationBuffer() { return simulationBuffer; }
    public void setSimulationBuffer(int simulationBuffer) { this.simulationBuffer = simulationBuffer; }
    public long getBlinkAFreezeStartMs() { return blinkAFreezeStartMs; }
    public void setBlinkAFreezeStartMs(long blinkAFreezeStartMs) { this.blinkAFreezeStartMs = blinkAFreezeStartMs; }
    public Location getBlinkAFreezeLoc() { return blinkAFreezeLoc; }
    public void setBlinkAFreezeLoc(Location blinkAFreezeLoc) { this.blinkAFreezeLoc = blinkAFreezeLoc; }
    public long getBlinkAFreezeDurationMs() { return blinkAFreezeDurationMs; }
    public void setBlinkAFreezeDurationMs(long blinkAFreezeDurationMs) { this.blinkAFreezeDurationMs = blinkAFreezeDurationMs; }
    public int getBlinkABurstCount() { return blinkABurstCount; }
    public void setBlinkABurstCount(int blinkABurstCount) { this.blinkABurstCount = blinkABurstCount; }
    public int getBlinkABuffer() { return blinkABuffer; }
    public void setBlinkABuffer(int blinkABuffer) { this.blinkABuffer = blinkABuffer; }
    public Deque<Long> getBlinkBPacketTimes() { return blinkBPacketTimes; }
    public long getBlinkBDeadZoneEndMs() { return blinkBDeadZoneEndMs; }
    public void setBlinkBDeadZoneEndMs(long blinkBDeadZoneEndMs) { this.blinkBDeadZoneEndMs = blinkBDeadZoneEndMs; }
    public long getBlinkBDeadZoneDurationMs() { return blinkBDeadZoneDurationMs; }
    public void setBlinkBDeadZoneDurationMs(long blinkBDeadZoneDurationMs) { this.blinkBDeadZoneDurationMs = blinkBDeadZoneDurationMs; }
    public int getBlinkBBuffer() { return blinkBBuffer; }
    public void setBlinkBBuffer(int blinkBBuffer) { this.blinkBBuffer = blinkBBuffer; }
    public long getStepCLastStepMs() { return stepCLastStepMs; }
    public void setStepCLastStepMs(long stepCLastStepMs) { this.stepCLastStepMs = stepCLastStepMs; }
    public int getStepCBuffer() { return stepCBuffer; }
    public void setStepCBuffer(int stepCBuffer) { this.stepCBuffer = stepCBuffer; }
    public int getJesusBBuffer() { return jesusBBuffer; }
    public void setJesusBBuffer(int jesusBBuffer) { this.jesusBBuffer = jesusBBuffer; }
    public int getSpeedDBuffer() { return speedDBuffer; }
    public void setSpeedDBuffer(int speedDBuffer) { this.speedDBuffer = speedDBuffer; }
    public long getNoRotationALastProcessedAttackMs() { return noRotationALastProcessedAttackMs; }
    public void setNoRotationALastProcessedAttackMs(long noRotationALastProcessedAttackMs) { this.noRotationALastProcessedAttackMs = noRotationALastProcessedAttackMs; }
    public long getNoRotationBLastProcessedAttackMs() { return noRotationBLastProcessedAttackMs; }
    public void setNoRotationBLastProcessedAttackMs(long noRotationBLastProcessedAttackMs) { this.noRotationBLastProcessedAttackMs = noRotationBLastProcessedAttackMs; }
    public int getReachABuffer() { return reachABuffer; }
    public void setReachABuffer(int reachABuffer) { this.reachABuffer = reachABuffer; }
    public int getReachBBuffer() { return reachBBuffer; }
    public void setReachBBuffer(int reachBBuffer) { this.reachBBuffer = reachBBuffer; }
    public Deque<Double> getReachCSamples() { return reachCSamples; }
    public Deque<Long> getReachCSampleTimes() { return reachCSampleTimes; }
    public int getBackTrackABuffer() { return backTrackABuffer; }
    public void setBackTrackABuffer(int backTrackABuffer) { this.backTrackABuffer = backTrackABuffer; }
    public long getBackTrackALastProcessedAttackMs() { return backTrackALastProcessedAttackMs; }
    public void setBackTrackALastProcessedAttackMs(long backTrackALastProcessedAttackMs) { this.backTrackALastProcessedAttackMs = backTrackALastProcessedAttackMs; }
    public Deque<Boolean> getBackTrackAStaleHistory() { return backTrackAStaleHistory; }
    public Deque<Boolean> getBackTrackATimingHistory() { return backTrackATimingHistory; }
    public Deque<Boolean> getBackTrackAOrderHistory() { return backTrackAOrderHistory; }
    public Deque<Boolean> getBackTrackAPingHistory() { return backTrackAPingHistory; }
    public Deque<Long> getBackTrackAStaleAgeHistory() { return backTrackAStaleAgeHistory; }
    public int getBackTrackALastPing() { return backTrackALastPing; }
    public void setBackTrackALastPing(int backTrackALastPing) { this.backTrackALastPing = backTrackALastPing; }
    public boolean hasBackTrackALastPing() { return backTrackAHasLastPing; }
    public void setBackTrackAHasLastPing(boolean backTrackAHasLastPing) { this.backTrackAHasLastPing = backTrackAHasLastPing; }

    // =========================================================
    // Per-check verbose accessors (fixes shared counter bugs)
    // =========================================================
    public int getJesusAVerbose() { return jesusAVerbose; }
    public void setJesusAVerbose(int v) { this.jesusAVerbose = v; }
    public int getPhaseAVerbose() { return phaseAVerbose; }
    public void setPhaseAVerbose(int v) { this.phaseAVerbose = v; }
    public int getGroundSpoofAVerbose() { return groundSpoofAVerbose; }
    public void setGroundSpoofAVerbose(int v) { this.groundSpoofAVerbose = v; }
    public int getGroundSpoofBVerbose() { return groundSpoofBVerbose; }
    public void setGroundSpoofBVerbose(int v) { this.groundSpoofBVerbose = v; }
    public int getInventoryAVerbose() { return inventoryAVerbose; }
    public void setInventoryAVerbose(int v) { this.inventoryAVerbose = v; }
    public long getInventoryALastFlagTime() { return inventoryALastFlagTime; }
    public void setInventoryALastFlagTime(long v) { this.inventoryALastFlagTime = v; }
    public int getInventoryAEscalation() { return inventoryAEscalation; }
    public void setInventoryAEscalation(int v) { this.inventoryAEscalation = v; }
    public int getInventoryBVerbose() { return inventoryBVerbose; }
    public void setInventoryBVerbose(int v) { this.inventoryBVerbose = v; }
    public int getInventoryCVerbose() { return inventoryCVerbose; }
    public void setInventoryCVerbose(int v) { this.inventoryCVerbose = v; }
    public int getTimerAVerbose() { return timerAVerbose; }
    public void setTimerAVerbose(int v) { this.timerAVerbose = v; }
    public int getTimerBVerbose() { return timerBVerbose; }
    public void setTimerBVerbose(int v) { this.timerBVerbose = v; }
    public int getTimerPredictionBuffer() { return timerPredictionBuffer; }
    public void setTimerPredictionBuffer(int v) { this.timerPredictionBuffer = v; }
    public int getNoRotationAVerbose() { return noRotationAVerbose; }
    public void setNoRotationAVerbose(int v) { this.noRotationAVerbose = v; }
    public int getNoRotationBVerbose() { return noRotationBVerbose; }
    public void setNoRotationBVerbose(int v) { this.noRotationBVerbose = v; }
    public int getBadPacketsBVerbose() { return badPacketsBVerbose; }
    public void setBadPacketsBVerbose(int v) { this.badPacketsBVerbose = v; }
    public int getSpeedBVerbose() { return speedBVerbose; }
    public void setSpeedBVerbose(int v) { this.speedBVerbose = v; }
    public int getSpeedBAirTicks() { return speedBAirTicks; }
    public void setSpeedBAirTicks(int v) { this.speedBAirTicks = v; }
    public int getSpeedBLastAirRunTicks() { return speedBLastAirRunTicks; }
    public void setSpeedBLastAirRunTicks(int v) { this.speedBLastAirRunTicks = v; }
    public int getSpeedCVerbose() { return speedCVerbose; }
    public void setSpeedCVerbose(int v) { this.speedCVerbose = v; }
    public int getNoFallBVerbose() { return noFallBVerbose; }
    public void setNoFallBVerbose(int v) { this.noFallBVerbose = v; }
    public int getNoSlowAVerbose() { return noSlowAVerbose; }
    public void setNoSlowAVerbose(int v) { this.noSlowAVerbose = v; }
    public int getNoSlowBVerbose() { return noSlowBVerbose; }
    public void setNoSlowBVerbose(int v) { this.noSlowBVerbose = v; }
    public int getFastEatAVerbose() { return fastEatAVerbose; }
    public void setFastEatAVerbose(int v) { this.fastEatAVerbose = v; }
    public int getFastBowAVerbose() { return fastBowAVerbose; }
    public void setFastBowAVerbose(int v) { this.fastBowAVerbose = v; }
    public int getFastBowBVerbose() { return fastBowBVerbose; }
    public void setFastBowBVerbose(int v) { this.fastBowBVerbose = v; }

    // Timer balance accessors
    public long getTimerBalanceStart() { return timerBalanceStart; }
    public void setTimerBalanceStart(long v) { this.timerBalanceStart = v; }
    public int getTimerPacketCount() { return timerPacketCount; }
    public void setTimerPacketCount(int v) { this.timerPacketCount = v; }
    public long getTimerLastReset() { return timerLastReset; }
    public void setTimerLastReset(long v) { this.timerLastReset = v; }
    public long getTimerDebtMs() { return timerDebtMs; }
    public void setTimerDebtMs(long v) { this.timerDebtMs = v; }

    // TimerC negative timer accessors
    public long getTimerCStart() { return timerCStart; }
    public void setTimerCStart(long v) { this.timerCStart = v; }
    public int getTimerCPacketCount() { return timerCPacketCount; }
    public void setTimerCPacketCount(int v) { this.timerCPacketCount = v; }
    public long getTimerCLastReset() { return timerCLastReset; }
    public void setTimerCLastReset(long v) { this.timerCLastReset = v; }
    public int getTimerCVerbose() { return timerCVerbose; }
    public void setTimerCVerbose(int v) { this.timerCVerbose = v; }
    public long getTimerCBalance() { return timerCBalance; }
    public void setTimerCBalance(long v) { this.timerCBalance = v; }
    public long getTimerCThreshold() { return timerCThreshold; }
    public void setTimerCThreshold(long v) { this.timerCThreshold = v; }

    // TimerA balance accessors
    public long getTimerABalance() { return timerABalance; }
    public void setTimerABalance(long v) { this.timerABalance = v; }
    public long getTimerAThreshold() { return timerAThreshold; }
    public void setTimerAThreshold(long v) { this.timerAThreshold = v; }

    // Gravity tracking accessors
    public Deque<Double> getRecentYMotions() { return recentYMotions; }

    // Inventory click tracking accessors
    public Deque<Long> getInventoryClickTimes() { return inventoryClickTimes; }

    // Ground spoof tracking accessors
    public int getGroundSpoofAirTicks() { return groundSpoofAirTicks; }
    public void setGroundSpoofAirTicks(int v) { this.groundSpoofAirTicks = Math.max(0, v); }
    public double getGroundSpoofLastY() { return groundSpoofLastY; }
    public void setGroundSpoofLastY(double v) { this.groundSpoofLastY = v; }
    public long getGroundDescentSessionStartMs() { return groundDescentSessionStartMs; }
    public void setGroundDescentSessionStartMs(long groundDescentSessionStartMs) {
        this.groundDescentSessionStartMs = groundDescentSessionStartMs;
    }
    public double getGroundDescentCumulativeDy() { return groundDescentCumulativeDy; }
    public void setGroundDescentCumulativeDy(double groundDescentCumulativeDy) {
        this.groundDescentCumulativeDy = groundDescentCumulativeDy;
    }
    public double getPreBlinkPeakY() { return preBlinkPeakY; }
    public void setPreBlinkPeakY(double preBlinkPeakY) { this.preBlinkPeakY = preBlinkPeakY; }
    public long getPreBlinkMs() { return preBlinkMs; }
    public void setPreBlinkMs(long preBlinkMs) { this.preBlinkMs = preBlinkMs; }
    public boolean isNoFallDamageResolved() { return noFallDamageResolved; }
    public void setNoFallDamageResolved(boolean noFallDamageResolved) { this.noFallDamageResolved = noFallDamageResolved; }
    public long getNoFallBlinkLandMs() { return noFallBlinkLandMs; }
    public void setNoFallBlinkLandMs(long noFallBlinkLandMs) { this.noFallBlinkLandMs = noFallBlinkLandMs; }
    public double getNoFallBlinkDrop() { return noFallBlinkDrop; }
    public void setNoFallBlinkDrop(double noFallBlinkDrop) { this.noFallBlinkDrop = noFallBlinkDrop; }
    public long getLastVelocityAnimationMs() { return lastVelocityAnimationMs; }
    public void setLastVelocityAnimationMs(long lastVelocityAnimationMs) {
        this.lastVelocityAnimationMs = lastVelocityAnimationMs;
    }

    public Deque<PositionSample> getPositionHistory() { return positionHistory; }

    public void recordPosition(Location loc, boolean sneaking, long now) {
        if (loc == null || loc.getWorld() == null) return;

        PositionSample last = positionHistory.peekLast();
        if (last != null
                && last.matchesWorld(loc.getWorld())
                && Math.abs(last.getX() - loc.getX()) < 1.0E-4
                && Math.abs(last.getY() - loc.getY()) < 1.0E-4
                && Math.abs(last.getZ() - loc.getZ()) < 1.0E-4
                && Math.abs(last.getYaw() - loc.getYaw()) < 1.0E-3f
                && Math.abs(last.getPitch() - loc.getPitch()) < 1.0E-3f
                && (now - last.getTime()) < 25L) {
            return;
        }

        positionHistory.addLast(new PositionSample(
                now,
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                sneaking
        ));

        long cutoff = now - 1500L;
        while (positionHistory.size() > 60) {
            positionHistory.removeFirst();
        }
        while (!positionHistory.isEmpty() && positionHistory.peekFirst().getTime() < cutoff) {
            positionHistory.removeFirst();
        }
    }

    public void recordLagrangeTeleportSample(long now, UUID targetUuid, long gapMs, double moveH, double closeDelta,
                                             double endDistance, double meanPing, double jitter,
                                             int maxSamples, long windowMs) {
        lagrangeTeleportSamples.addLast(new CombatTeleportSample(
                now, targetUuid, gapMs, moveH, closeDelta, endDistance, meanPing, jitter
        ));
        while (lagrangeTeleportSamples.size() > Math.max(1, maxSamples)) {
            lagrangeTeleportSamples.removeFirst();
        }
        long cutoff = now - Math.max(1000L, windowMs);
        while (!lagrangeTeleportSamples.isEmpty() && lagrangeTeleportSamples.peekFirst().getTime() < cutoff) {
            lagrangeTeleportSamples.removeFirst();
        }
        this.lastLagrangeTeleportTime = now;
    }

    public static final class CombatTeleportSample {
        private final long time;
        private final UUID targetUuid;
        private final long gapMs;
        private final double moveH;
        private final double closeDelta;
        private final double endDistance;
        private final double meanPing;
        private final double jitter;

        public CombatTeleportSample(long time, UUID targetUuid, long gapMs, double moveH, double closeDelta,
                                    double endDistance, double meanPing, double jitter) {
            this.time = time;
            this.targetUuid = targetUuid;
            this.gapMs = gapMs;
            this.moveH = moveH;
            this.closeDelta = closeDelta;
            this.endDistance = endDistance;
            this.meanPing = meanPing;
            this.jitter = jitter;
        }

        public long getTime() { return time; }
        public UUID getTargetUuid() { return targetUuid; }
        public long getGapMs() { return gapMs; }
        public double getMoveH() { return moveH; }
        public double getCloseDelta() { return closeDelta; }
        public double getEndDistance() { return endDistance; }
        public double getMeanPing() { return meanPing; }
        public double getJitter() { return jitter; }
    }

    public static final class PositionSample {
        private final long time;
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final boolean sneaking;

        public PositionSample(long time, String worldName, double x, double y, double z,
                              float yaw, float pitch, boolean sneaking) {
            this.time = time;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.sneaking = sneaking;
        }

        public long getTime() { return time; }
        public String getWorldName() { return worldName; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public boolean isSneaking() { return sneaking; }

        public boolean matchesWorld(World world) {
            return world != null && world.getName().equals(worldName);
        }

        public Location toLocation(World world) {
            if (!matchesWorld(world)) return null;
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public static final class BlockStateSample {
        private final long time;
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;
        private final Material oldType;
        private final Material newType;

        public BlockStateSample(long time, String worldName, int x, int y, int z, Material oldType, Material newType) {
            this.time = time;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.oldType = oldType;
            this.newType = newType;
        }

        public long getTime() { return time; }
        public String getWorldName() { return worldName; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public Material getOldType() { return oldType; }
        public Material getNewType() { return newType; }

        public boolean matches(Location loc) {
            return loc != null && loc.getWorld() != null
                    && worldName.equals(loc.getWorld().getName())
                    && x == loc.getBlockX()
                    && y == loc.getBlockY()
                    && z == loc.getBlockZ();
        }
    }

    public static boolean bypass(Player p) {
        return p != null && (p.hasPermission("vez.bypass") || p.hasPermission("watchdog.bypass"));
    }

    public void markCombatJoin(long nowMs) {
        this.combatJoinTimeMs = nowMs;
    }

    public boolean isWithinCombatJoinGrace(long nowMs, long graceMs) {
        if (graceMs <= 0L || combatJoinTimeMs <= 0L) {
            return false;
        }
        return nowMs - combatJoinTimeMs < graceMs;
    }

    // ==================== NoFall Shared State Methods ====================

    /**
     * Record a NoFall sample for cross-check analysis
     */
    public void recordNoFallSample(double yDelta, boolean onGround, double serverFall,
                                    double clientFall, long timestamp, int maxHistory) {
        noFallYDeltaHistory.addLast(yDelta);
        noFallGroundHistory.addLast(onGround);
        noFallServerFallHistory.addLast(serverFall);
        noFallClientFallHistory.addLast(clientFall);
        noFallSampleTimestamps.addLast(timestamp);

        while (noFallYDeltaHistory.size() > maxHistory) noFallYDeltaHistory.removeFirst();
        while (noFallGroundHistory.size() > maxHistory) noFallGroundHistory.removeFirst();
        while (noFallServerFallHistory.size() > maxHistory) noFallServerFallHistory.removeFirst();
        while (noFallClientFallHistory.size() > maxHistory) noFallClientFallHistory.removeFirst();
        while (noFallSampleTimestamps.size() > maxHistory) noFallSampleTimestamps.removeFirst();
    }

    /**
     * Record a peak Y position for multi-peak tracking
     */
    public void recordPeak(double y, long timestamp, int maxPeaks) {
        noFallAPeaks.add(new PeakRecord(y, timestamp));
        while (noFallAPeaks.size() > maxPeaks) {
            noFallAPeaks.remove(0);
        }
    }

    /**
     * Calculate cumulative discrepancy between server and client fall distance
     */
    public double calculateCumulativeDiscrepancy() {
        if (noFallServerFallHistory.isEmpty() || noFallClientFallHistory.isEmpty()) return 0.0;

        Double[] serverArr = noFallServerFallHistory.toArray(new Double[0]);
        Double[] clientArr = noFallClientFallHistory.toArray(new Double[0]);

        int minSize = Math.min(serverArr.length, clientArr.length);
        double cumulative = 0.0;

        for (int i = 0; i < minSize; i++) {
            cumulative += (serverArr[i] - clientArr[i]);
        }

        return cumulative;
    }

    /**
     * Record an onGround packet for frequency analysis
     */
    public void recordOnGroundPacket(boolean onGround, long timestamp, int maxHistory) {
        recentOnGroundPackets.addLast(onGround);
        lastOnGroundPacketTime = timestamp;

        while (recentOnGroundPackets.size() > maxHistory) {
            recentOnGroundPackets.removeFirst();
        }
    }

    /**
     * Calculate onGround packet rate within a time window
     */
    public double calculateOnGroundRate(long windowMs) {
        if (recentOnGroundPackets.isEmpty()) return 0.0;

        int totalPackets = recentOnGroundPackets.size();
        int onGroundCount = 0;

        for (Boolean isOnGround : recentOnGroundPackets) {
            if (isOnGround) onGroundCount++;
        }

        return (double) onGroundCount / totalPackets;
    }

    /**
     * Clear all NoFall tracking state
     */
    public void clearNoFallTracking() {
        noFallYDeltaHistory.clear();
        noFallGroundHistory.clear();
        noFallServerFallHistory.clear();
        noFallClientFallHistory.clear();
        noFallSampleTimestamps.clear();
        noFallAPeaks.clear();
        recentOnGroundPackets.clear();
        noFallAggregateConfidence = 0.0;
        lastNoFallAggregateUpdate = 0L;
        noFallLastDy = 0.0;
        noFallTicksSinceLastSample = 0;
    }

    // Getters and setters for new fields
    public Deque<Double> getNoFallYDeltaHistory() { return noFallYDeltaHistory; }
    public Deque<Boolean> getNoFallGroundHistory() { return noFallGroundHistory; }
    public Deque<Double> getNoFallServerFallHistory() { return noFallServerFallHistory; }
    public Deque<Double> getNoFallClientFallHistory() { return noFallClientFallHistory; }
    public Deque<Long> getNoFallSampleTimestamps() { return noFallSampleTimestamps; }
    public List<PeakRecord> getNoFallAPeaks() { return noFallAPeaks; }

    public double getNoFallAggregateConfidence() { return noFallAggregateConfidence; }
    public void setNoFallAggregateConfidence(double v) { this.noFallAggregateConfidence = v; }

    public long getLastNoFallAggregateUpdate() { return lastNoFallAggregateUpdate; }
    public void setLastNoFallAggregateUpdate(long v) { this.lastNoFallAggregateUpdate = v; }

    public Deque<Boolean> getRecentOnGroundPackets() { return recentOnGroundPackets; }
    public long getLastOnGroundPacketTime() { return lastOnGroundPacketTime; }

    public double getNoFallLastDy() { return noFallLastDy; }
    public void setNoFallLastDy(double v) { this.noFallLastDy = v; }

    public int getNoFallTicksSinceLastSample() { return noFallTicksSinceLastSample; }
    public void setNoFallTicksSinceLastSample(int v) { this.noFallTicksSinceLastSample = v; }

    // Attacker state getters/setters
    public UUID getLastAttackerUuid() { return lastAttackerUuid; }
    public void setLastAttackerUuid(UUID uuid) { this.lastAttackerUuid = uuid; }

    public boolean wasLastAttackerSprinting() { return lastAttackerSprinting; }
    public void setLastAttackerSprinting(boolean sprinting) { this.lastAttackerSprinting = sprinting; }

    public Vector getLastAttackerVelocity() { return lastAttackerVelocity; }
    public void setLastAttackerVelocity(Vector velocity) {
        this.lastAttackerVelocity = velocity == null ? null : velocity.clone();
    }

    public double getLastAttackCooldown() { return lastAttackCooldown; }
    public void setLastAttackCooldown(double cooldown) { this.lastAttackCooldown = cooldown; }

    public int getLastAttackerKnockbackLevel() { return lastAttackerKnockbackLevel; }
    public void setLastAttackerKnockbackLevel(int level) { this.lastAttackerKnockbackLevel = level; }

    /**
     * Record full attacker state for KB validation
     */
    public void recordAttackerState(UUID attackerUuid, boolean sprinting, Vector velocity,
                                     double cooldown, int knockbackLevel) {
        this.lastAttackerUuid = attackerUuid;
        this.lastAttackerSprinting = sprinting;
        this.lastAttackerVelocity = velocity == null ? null : velocity.clone();
        this.lastAttackCooldown = cooldown;
        this.lastAttackerKnockbackLevel = knockbackLevel;
    }
}
