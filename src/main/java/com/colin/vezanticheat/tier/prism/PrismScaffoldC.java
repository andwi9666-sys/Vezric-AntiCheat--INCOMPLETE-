package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.tier.prism.scaffold.ScaffoldEngine;
import com.colin.vezanticheat.utils.ScaffoldUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * LegitScaffold — silent bridge-consistency detector (registered as PrismScaffoldC).
 *
 * <p>The premise: real human bridging is never perfectly consistent for long. Even excellent players
 * have small timing errors, rotation drift, aim correction, and imperfect placement rhythm. This
 * check accumulates suspicion when a player bridges with robotically consistent placement timing,
 * pitch locked in a narrow band, unnaturally smooth/repeated yaw steps, and difficult
 * movement-placement coordination — across many placements, not from any single perfect one.</p>
 *
 * <p>It requires at least minCategoriesToFlag independent consistency categories to be active and
 * tolerates perfectPlacementAllowance lucky-perfect placements before evidence starts to build, so
 * speed/ninja/god/diagonal/stair bridging and one-off skillful timing do not flag.</p>
 *
 * <p><b>Silent only:</b> diagnostics + staff verbose/debug; never cancels, setbacks, or prevents.</p>
 */
public final class PrismScaffoldC extends TierCheck {

    public PrismScaffoldC(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldC", CheckTier.PRISM);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, Block against,
                                   int faceId, float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p) || ScaffoldUtil.shouldSkip(p, data)) return;

        ScaffoldEngine.ScaffoldResult result =
                ScaffoldEngine.observePlacement(plugin, p, data, against, faceId, cursorX, cursorY, cursorZ);
        if (result.legitFlag) {
            emitSilent(p, "legit-scaffold " + result.debug);
        }
    }

    private void emitSilent(Player p, String debug) {
        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), name(), "silent-flag", debug);
        }
        if (plugin.tierCfg().checkBoolean(name(), "debug", false)) {
            plugin.getLogger().info("[LegitScaffold] " + p.getName() + " " + debug);
        }
        if (plugin.tierCfg().checkBoolean(name(), "verboseEvidence", true)) {
            verbose(p, debug);
        }
    }
}
