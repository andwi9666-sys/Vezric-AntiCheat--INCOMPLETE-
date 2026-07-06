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
 * Scaffold — silent placement-legality &amp; line-of-sight detector (registered as PrismScaffoldA).
 *
 * <p>Flags bridging placements where the player is clearly not looking at the block face (raytrace
 * miss / large crosshair-to-face angle) or where the support is illegal (placing against air/liquid,
 * hidden faces, or the exact same relative position every time). Evidence accrues in a buffer with
 * decay so one-off skillful placements never flag.</p>
 *
 * <p><b>Silent only:</b> reports via diagnostics and staff verbose/debug. It never cancels the
 * placement, setbacks, prevents bridging, or raises a public violation.</p>
 */
public final class PrismScaffoldA extends TierCheck {

    public PrismScaffoldA(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldA", CheckTier.PRISM);
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, Block against,
                                   int faceId, float cursorX, float cursorY, float cursorZ) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p) || ScaffoldUtil.shouldSkip(p, data)) return;

        ScaffoldEngine.ScaffoldResult result =
                ScaffoldEngine.observePlacement(plugin, p, data, against, faceId, cursorX, cursorY, cursorZ);
        if (result.scaffoldFlag) {
            emitSilent(p, "scaffold-legality " + result.debug);
        }
    }

    private void emitSilent(Player p, String debug) {
        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(p.getUniqueId(), name(), "silent-flag", debug);
        }
        if (plugin.tierCfg().checkBoolean(name(), "debug", false)) {
            plugin.getLogger().info("[Scaffold] " + p.getName() + " " + debug);
        }
        if (plugin.tierCfg().checkBoolean(name(), "verboseEvidence", true)) {
            verbose(p, debug);
        }
    }
}
