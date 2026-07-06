package com.colin.vezanticheat.listeners;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.LagProfileUtil;
import com.colin.vezanticheat.utils.LagrangeUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.*;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class PlayerListener implements Listener {

    private final VezAntiCheat plugin;

    public PlayerListener(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        d.recordPosition(p.getLocation(), p.isSneaking(), now);
        d.markCombatJoin(now);
        // Resolve the Bedrock verdict on the main thread so Netty-thread checks
        // read a warm cache (Floodgate API reflection happens here, once).
        com.colin.vezanticheat.utils.ClientCompatUtil.isBedrock(plugin, p, d);
        if (plugin.prediction() != null) {
            plugin.prediction().onJoin(p, d, p.getLocation(), now);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (plugin.velocity() != null) {
            plugin.velocity().removePlayer(e.getPlayer().getUniqueId());
        }
        if (plugin.combat() != null) {
            plugin.combat().removePlayer(e.getPlayer().getUniqueId());
        }
        com.colin.vezanticheat.tier.prism.PrismInteractionEvaluator.clearPlayer(e.getPlayer().getUniqueId());
        com.colin.vezanticheat.tier.prism.scaffold.ScaffoldEngine.clearPlayer(e.getPlayer().getUniqueId());
        com.colin.vezanticheat.tier.TierCheck.clearPlayer(e.getPlayer().getUniqueId());
        com.colin.vezanticheat.checks.Check.clearPlayer(e.getPlayer().getUniqueId());
        if (plugin.riskScore() != null) {
            plugin.riskScore().remove(e.getPlayer().getUniqueId());
        }
        if (plugin.punish() != null) {
            plugin.punish().clearTransient(e.getPlayer().getUniqueId());
        }
        plugin.data().remove(e.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();

        // Refresh the ping-scaled exemption bonus ~1/s (before the packet-mode early
        // return below — this must run regardless of which path handles movement).
        if (now - d.getLastExemptPingUpdateMs() >= 1000L) {
            d.setLastExemptPingUpdateMs(now);
            d.setExemptPingBonusMs(plugin.cfg().exemptPingBonusMs(p));
        }

        if (plugin.hasProtocolLib()) {
            return;
        }

        if (e.getFrom() != null && e.getTo() != null) {
            double dy = e.getTo().getY() - e.getFrom().getY();
            if (p.isOnGround() || (dy > 0.0 && e.getFrom().getBlockY() <= e.getTo().getBlockY())) {
                if (dy > 0.10 && e.getFrom().getY() + 1.0E-4 < e.getTo().getY()) {
                    d.setLastJumpTime(now);
                }
            }
        }

        d.setLastMoveFrom(e.getFrom());
        d.setLastLoc(e.getTo());
        d.setLastMoveMillis(now);
        d.setLastClientGround(p.isOnGround(), now);
        d.recordPosition(e.getTo(), p.isSneaking(), now);
        LagrangeUtil.observeCombatMovement(plugin, p, d, now);
        if (e.getFrom() != null && e.getTo() != null) {
            float fromYaw = e.getFrom().getYaw();
            float fromPitch = e.getFrom().getPitch();
            float toYaw = e.getTo().getYaw();
            float toPitch = e.getTo().getPitch();
            if (Math.abs(fromYaw - toYaw) > 1.0E-3F || Math.abs(fromPitch - toPitch) > 1.0E-3F) {
                d.recordRotationSample(toYaw, toPitch);
                d.setLastRotationPacket(now);
                plugin.tierChecks().onRotation(p, d, toYaw, toPitch);
            }
        }
        if (d.isScaffoldRotationPending() && e.getTo() != null) {
            float yawDelta = Math.abs(wrapAngleTo180(e.getTo().getYaw() - d.getLastBlockPlacePacketYaw()));
            float pitchDelta = Math.abs(e.getTo().getPitch() - d.getLastBlockPlacePacketPitch());
            d.completeScaffoldRotationSample(yawDelta, pitchDelta);
        }
        long previousFlying = d.getLastFlyingPacket();
        long interval = previousFlying <= 0L ? 0L : Math.max(0L, now - previousFlying);
        d.setLastFlyingIntervalMs(interval);
        if (interval > 0L && interval < 250L) {
            d.getFlyingIntervals().addLast(interval);
            while (d.getFlyingIntervals().size() > 30) d.getFlyingIntervals().removeFirst();
        }
        LagProfileUtil.handleFlyingInterval(plugin, p, d, now, interval);
        d.setLastFlyingPacket(now);

        // ENGINE SOLE AUTHORITY: the prediction engine (PacketListener-driven, or this Bukkit-move
        // fallback when PacketEvents is unavailable) is the movement speed authority. The legacy
        // heuristic PredictionProcessor.handleMovement is only re-enabled as an emergency
        // kill-switch via engine.skip-legacy-movement-prediction=false; otherwise we only bridge
        // the velocity session here.
        if (plugin.prediction() != null) {
            boolean legacyAuthoritative = plugin.engine() == null
                    || !plugin.engine().isEnabled()
                    || !plugin.getConfig().getBoolean("engine.skip-legacy-movement-prediction", true);
            if (legacyAuthoritative) {
                plugin.prediction().handleMovement(p, d, p.isOnGround(), now, true);
            } else {
                if (plugin.engine() != null) {
                    plugin.engine().onMovement(p, d, d.getLastMoveFrom(), d.getLastLoc(), p.isOnGround(), true, now);
                }
                plugin.prediction().tickVelocitySession(p, d, now);
            }
        }
        plugin.tierChecks().onMove(p, d);
        plugin.tierChecks().onFlyingPacket(p, d, now);
        d.resetScaffoldPacketWindow();
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent e) {
        if (plugin.hasProtocolLib()) {
            return;
        }

        Player p = e.getPlayer();
        if (p == null) return;

        PlayerData d = plugin.data().get(p);
        if (d == null) return;

        long now = System.currentTimeMillis();
        long lastSwing = d.getLastArmSwingPacket();
        d.getArmSwings().addLast(now);
        while (d.getArmSwings().size() > 50) d.getArmSwings().removeFirst();
        if (lastSwing > 0L) {
            long swingInterval = now - lastSwing;
            if (swingInterval > 0L && swingInterval < 300L) {
                d.getClickIntervals().addLast(swingInterval);
                while (d.getClickIntervals().size() > 50) d.getClickIntervals().removeFirst();
            }
        }
        d.setLastArmSwingPacket(now);
        plugin.tierChecks().onArmSwing(p, d);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        d.markTeleportExempt(plugin.cfg().teleportExemptMs());
        long now = System.currentTimeMillis();
        d.recordPosition(e.getTo(), p.isSneaking(), now);
        if (plugin.prediction() != null) {
            plugin.prediction().onTeleport(p, d, e.getTo(), now);
        }
        if (plugin.engine() != null) {
            plugin.engine().onTeleport(p, d, e.getTo());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        d.markTeleportExempt(plugin.cfg().teleportExemptMs());
        long now = System.currentTimeMillis();
        d.recordPosition(e.getRespawnLocation(), p.isSneaking(), now);
        if (plugin.prediction() != null) {
            plugin.prediction().onTeleport(p, d, e.getRespawnLocation(), now);
        }
        if (plugin.engine() != null) {
            plugin.engine().onTeleport(p, d, e.getRespawnLocation());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        d.markTeleportExempt(plugin.cfg().teleportExemptMs());
        long now = System.currentTimeMillis();
        d.recordPosition(p.getLocation(), p.isSneaking(), now);
        if (plugin.prediction() != null) {
            plugin.prediction().onTeleport(p, d, p.getLocation(), now);
        }
        if (plugin.engine() != null) {
            plugin.engine().onTeleport(p, d, p.getLocation());
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        long now = System.currentTimeMillis();

        if (e.getDamager() instanceof Player) {
            Player damager = (Player) e.getDamager();
            PlayerData dd = plugin.data().get(damager);
            dd.recordPosition(damager.getLocation(), damager.isSneaking(), now);

            // When ProtocolLib is absent, populate USE_ENTITY fields from the Bukkit event
            // so that combat checks (KillAura, Reach, etc.) can still function.
            // When ProtocolLib IS present, onAttack was already dispatched from the packet;
            // the Bukkit event serves as a secondary dispatch (checks are idempotent via buffers).
            if (!plugin.hasProtocolLib()) {
                Entity target = e.getEntity();
                Location attackEye = com.colin.vezanticheat.utils.HitboxUtil.buildPacketSyncedEye(damager, dd);
                double dist = target != null ? CombatUtil.distanceToHitbox(attackEye, target) : 0.0;
                long swingDelta = dd.getLastArmSwingPacket() <= 0L
                        ? Long.MAX_VALUE
                        : (now - dd.getLastArmSwingPacket());
                dd.setLastUseEntity(target, true, dist, attackEye, swingDelta, now);
                LagProfileUtil.handleAttack(plugin, damager, dd, now);
                plugin.tierChecks().onAttack(damager, dd);
            }
        }

        if (e.getEntity() instanceof Player) {
            Player victim = (Player) e.getEntity();
            PlayerData vd = plugin.data().get(victim);
            vd.recordPosition(victim.getLocation(), victim.isSneaking(), now);
            vd.setLastDamageTime(now);
            vd.setLastDamageCause(e.getCause());
            if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                vd.markVelocityExempt(plugin.cfg().velocityExemptMs());
            }
            if (e.getDamager() instanceof Player) {
                Player attacker = (Player) e.getDamager();
                vd.noteCombatDamager(attacker.getUniqueId(), now);

                // CRITICAL FIX: Capture attacker state for KB validation
                // This allows the prediction system to verify if the server's KB calculation
                // properly accounted for sprint hits, attacker velocity, etc.
                boolean attackerSprinting = attacker.isSprinting();
                Vector attackerVelocity = attacker.getVelocity();
                double attackCooldown = 1.0; // 1.8.8 has no cooldown, always 1.0
                int knockbackLevel = 0;
                ItemStack held = attacker.getItemInHand();
                if (held != null) {
                    knockbackLevel = held.getEnchantmentLevel(Enchantment.KNOCKBACK);
                }
                vd.recordAttackerState(attacker.getUniqueId(), attackerSprinting,
                        attackerVelocity, attackCooldown, knockbackLevel);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                vd.setLastVelocityTime(now);
                vd.markVelocityExempt(plugin.cfg().velocityExemptMs());
                vd.setSpeedLastVelocityExemptMs(now);

                // Phase 2: Estimate explosion velocity for prediction system
                if (e instanceof EntityDamageByEntityEvent) {
                    EntityDamageByEntityEvent ede = (EntityDamageByEntityEvent) e;
                    Entity damager = ede.getDamager();
                    if (damager != null) {
                        Location explosionLoc = damager.getLocation();
                        Location playerLoc = victim.getLocation();
                        Vector direction = playerLoc.toVector().subtract(explosionLoc.toVector());
                        double distance = direction.length();
                        if (distance > 0.001) {
                            direction.normalize();
                            // Estimate explosion strength from damage and distance
                            double damage = e.getFinalDamage();
                            double strength = Math.min(1.5, damage / 10.0) * Math.max(0.1, 1.0 - (distance / 10.0));
                            Vector explosionVelocity = direction.multiply(strength);
                            vd.setLastExplosionVelocity(explosionVelocity);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();

        Vector v = e.getVelocity();
        d.setLastVelocity(v);
        d.setLastVelocityTime(now);
        d.setSpeedLastVelocityExemptMs(now);
        LagProfileUtil.handleVelocity(plugin, p, d, now);

        // Global velocity exempt window (prevents movement false flags right after KB)
        d.markVelocityExempt(plugin.cfg().velocityExemptMs());

        if (plugin.prediction() != null) {
            plugin.prediction().onVelocity(p, d, e);
        }
        if (plugin.velocity() != null) {
            plugin.velocity().onVelocity(p, d, e);
        }

        plugin.tierChecks().onVelocity(p, d);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);

        long now = System.currentTimeMillis();
        d.recordBlockState(
                e.getBlockPlaced().getLocation(),
                e.getBlockReplacedState() == null ? Material.AIR : e.getBlockReplacedState().getType(),
                e.getBlockPlaced().getType(),
                now
        );

        d.setLastBlockPlace(now);
        if (now - d.getLastBlockPlacePacketTime() > 100L) {
            d.setPlaceStreak(d.getPlaceStreak() + 1);
        }

        // block-state exemption for movement checks around placing
        d.markBlockStateExempt(plugin.cfg().blockStateExemptMs());

        // =====================================================
        // SCAFFOLD ONLY: capture placement context + intervals
        // =====================================================
        d.setLastPlacedBlockLoc(e.getBlockPlaced().getLocation());
        d.setLastPlaceAgainstLoc(e.getBlockAgainst().getLocation());
        d.setLastPlaceFace(resolvePlaceFace(e.getBlockPlaced().getLocation(), e.getBlockAgainst().getLocation()));
        d.setLastPlaceYaw(p.getLocation().getYaw());
        d.setLastPlacePitch(p.getLocation().getPitch());
        boolean placeGrounded = p.isOnGround();
        if (!placeGrounded) {
            placeGrounded = com.colin.vezanticheat.utils.ScaffoldUtil.isEffectivelyGroundedForScaffold(
                    plugin, p, d, p.getLocation());
        }
        d.setLastPlaceOnGround(placeGrounded);
        d.setLastPlaceSneaking(p.isSneaking());
        if (!plugin.hasProtocolLib()) {
            d.setLastBlockPlacePacket(
                    e.getBlockAgainst().getLocation(),
                    faceIdFromBlockFace(d.getLastPlaceFace()),
                    Float.NaN,
                    Float.NaN,
                    Float.NaN,
                    p.getLocation().getYaw(),
                    p.getLocation().getPitch(),
                    p.getLocation(),
                    now
            );
        }

        if (now - d.getLastBlockPlacePacketTime() > 100L) {
            long last = d.getLastScaffoldPlaceTime();
            if (last != 0L) {
                long dt = now - last;
                if (dt > 0L && dt < 2000L) {
                    d.getScaffoldIntervals().addLast(dt);
                    while (d.getScaffoldIntervals().size() > 20) d.getScaffoldIntervals().removeFirst();
                }
            }
            d.setLastScaffoldPlaceTime(now);
        }
        // =====================================================

        plugin.tierChecks().onBlockPlace(p, d);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        d.recordBlockBreakSample(
                now,
                plugin.cfg().checkInt("FastBreakB", "sampleSize", 10),
                plugin.cfg().checkInt("NukerA", "maxBreakHistory", 20),
                plugin.cfg().checkLong("FastBreakB", "maxTrackedIntervalMs", 5000L)
        );
        d.recordBlockState(e.getBlock().getLocation(), e.getBlock().getType(), Material.AIR, now);

        // Dispatch to FastBreak checks BEFORE marking blockStateExempt
        plugin.tierChecks().onBlockBreak(p, d, e.getBlock());

        d.markBlockStateExempt(plugin.cfg().blockStateExemptMs());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();

        // Track dig start for FastBreak timing
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            d.recordDigStartSample(now);
            if (!plugin.hasProtocolLib() && e.getClickedBlock() != null && e.getClickedBlock().getType() != Material.AIR) {
                plugin.tierChecks().onDigStart(p, d, e.getClickedBlock());
            }
        }

        if (e.getItem() != null) {
            Material type = e.getItem().getType();
            Action action = e.getAction();
            boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
            if (rightClick && isCombatInteract(type)) {
                d.setLastCombatInteractTime(now);
            }
        }

        if (now - d.getLastBlockPlace() > 2000L) {
            d.setPlaceStreak(0);

            // scaffold-only: clear interval history when streak resets
            d.getScaffoldIntervals().clear();
            d.getScaffoldPitchHistory().clear();
            d.setScaffoldVerboseA(0);
            d.setScaffoldVerboseB(0);
            d.setScaffoldVerboseC(0);
            d.setScaffoldVerboseD(0);
            d.setScaffoldVerboseE(0);
            d.setScaffoldVerboseF(0);
            d.setScaffoldVerboseG(0);
        }

        if (e.getItem() != null) {
            Material type = e.getItem().getType();
            Action action = e.getAction();
            boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
            if (rightClick && type == Material.BOW) {
                d.setBowPullStart(now);
            }
            if (rightClick && type.isEdible()) {
                com.colin.vezanticheat.utils.ItemUseMovementUtil.beginEating(p, d, now);
            }
        }
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        Player p = (Player) e.getEntity();
        PlayerData d = plugin.data().get(p);

        long now = System.currentTimeMillis();
        long pull = now - d.getBowPullStart();
        long previousShot = d.getLastBowShotMs();
        long interval = previousShot > 0L ? (now - previousShot) : Long.MAX_VALUE;
        d.setLastBowShotIntervalMs(interval);
        d.setLastBowShotMs(now);
        plugin.tierChecks().onBowShoot(p, d, pull);
        d.setBowPullStart(0L);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        if (e.getItem() != null && e.getItem().getType() == Material.POTION) {
            d.markPotionExempt(plugin.cfg().potionExemptMs());
        }
        long start = d.getLastEatStart();
        long useMs = start > 0L ? now - start : Long.MAX_VALUE;
        plugin.tierChecks().onConsume(p, d, useMs);
        long graceMs = plugin.getConfig().getLong("movement-analysis.eat-movement-grace-ms", 1400L);
        com.colin.vezanticheat.utils.UseItemTracker.noteConsumeComplete(d, now, graceMs);
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent e) {
        if (e == null) return;
        for (Entity entity : e.getAffectedEntities()) {
            if (!(entity instanceof Player)) continue;
            Player player = (Player) entity;
            PlayerData data = plugin.data().get(player);
            if (data == null) continue;
            data.markPotionExempt(plugin.cfg().potionExemptMs());
        }
    }

    private boolean isCombatInteract(Material type) {
        if (type == null) return false;
        switch (type) {
            case WOOD_SWORD:
            case STONE_SWORD:
            case IRON_SWORD:
            case GOLD_SWORD:
            case DIAMOND_SWORD:
            case FISHING_ROD:
            case BOW:
                return true;
            default:
                return false;
        }
    }

    private int faceIdFromBlockFace(BlockFace face) {
        if (face == null) return -1;
        switch (face) {
            case DOWN: return 0;
            case UP: return 1;
            case NORTH: return 2;
            case SOUTH: return 3;
            case WEST: return 4;
            case EAST: return 5;
            default: return -1;
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent e) {
        PlayerData d = plugin.data().get(e.getPlayer());
        d.setBowPullStart(0L);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        PlayerData d = plugin.data().get((Player) e.getEntity());
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            long nowMs = System.currentTimeMillis();
            d.setVerbose(0);
            d.setLastFallDamageTime(nowMs);
            d.setLastFallDamageAmount(e.getFinalDamage());
            com.colin.vezanticheat.utils.NoFallTracker.onFallDamage(
                    plugin, (Player) e.getEntity(), d, e.getFinalDamage(), nowMs);
        }
    }

    @EventHandler
    public void onInvOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        PlayerData d = plugin.data().get(p);
        d.setInventoryOpen(true);
        d.resetInventoryMoveCount();
        d.setLastInventoryAction(System.currentTimeMillis());

        long momentumExemptMs = plugin.tierCfg().checkLong("PrismInventoryA", "momentumExemptMs", 500L);
        d.markInventoryMomentumExempt(momentumExemptMs);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player p = (Player) e.getWhoClicked();
        PlayerData d = plugin.data().get(p);

        long now = System.currentTimeMillis();
        d.setLastInventoryAction(now);
        d.recordInventoryClick(now, plugin.cfg().checkInt("InventoryB", "historySize", 12));
        plugin.tierChecks().onInventoryAction(p, d);
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        PlayerData d = plugin.data().get((Player) e.getPlayer());
        d.setInventoryOpen(false);
        d.resetInventoryMoveCount();
        d.setLastInventoryAction(System.currentTimeMillis());
    }

    private BlockFace resolvePlaceFace(Location placed, Location against) {
        if (placed == null || against == null) return BlockFace.SELF;
        int dx = placed.getBlockX() - against.getBlockX();
        int dy = placed.getBlockY() - against.getBlockY();
        int dz = placed.getBlockZ() - against.getBlockZ();
        if (dy > 0) return BlockFace.UP;
        if (dy < 0) return BlockFace.DOWN;
        if (dx > 0) return BlockFace.EAST;
        if (dx < 0) return BlockFace.WEST;
        if (dz > 0) return BlockFace.SOUTH;
        if (dz < 0) return BlockFace.NORTH;
        return BlockFace.SELF;
    }

    private float wrapAngleTo180(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }
}
