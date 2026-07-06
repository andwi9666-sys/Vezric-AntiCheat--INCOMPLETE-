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
 * Eagle — silent automated sneak-at-edge detector (registered as PrismScaffoldB).
 *
 * <p>Normal players who speed-bridge, ninja-bridge, shift-tap, or briefly sneak at an edge vary
 * naturally. This check looks for AUTOMATION: across many bridge cycles the player starts sneaking at
 * nearly the same distance from the block edge and releases sneak with repeated millisecond precision
 * relative to placement. It needs many consistent cycles (eagleMinCycles) and at least minSignals of
 * {edge-distance, sneak-start-timing, sneak-release-timing} to be robotically tight before flagging.</p>
 *
 * <p><b>Silent only:</b> diagnostics + staff verbose/debug; never cancels, setbacks, or prevents.</p>
 */
public final class PrismScaffoldB extends TierCheck {

    public PrismScaffoldB(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldB", CheckTier.PRISM);
    }

    @Override
    public void onEntityAction(Player p, PlayerData data, String actionName) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p) || ScaffoldUtil.shouldSkip(p, data)) return;
        ScaffoldEngine.observeSneak(plugin, p, data, actionName);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, Block against,
                                   int faceId, float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p) || ScaffoldUtil.shouldSkip(p, data)) return;

        ScaffoldEngine.ScaffoldResult result =
                ScaffoldEngine.observePlacement(plugin, p, data, against, faceId, cursorX, cursorY, cursorZ);
        if (result.eagleFlag) {
            emitSilent(p, "eagle " + result.debug);
        }
    }

    private void emitSilent(Player p, String debug) {
        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), name(), "silent-flag", debug);
        }
        if (plugin.tierCfg().checkBoolean(name(), "debug", false)) {
            plugin.getLogger().info("[Eagle] " + p.getName() + " " + debug);
        }
        if (plugin.tierCfg().checkBoolean(name(), "verboseEvidence", true)) {
            verbose(p, debug);
        }
    }
}
