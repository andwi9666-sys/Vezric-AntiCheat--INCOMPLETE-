package com.colin.vezanticheat.velocity;

import org.bukkit.util.Vector;

/**
 * Formats velocity debug lines for console/staff.
 */
public final class VelocityDebug {
    private VelocityDebug() {}

    public static String format(VelocitySession session, VelocityEvaluationResult result) {
        if (session == null || session.snapshot == null) return "";

        VelocitySnapshot snap = session.snapshot;
        Vector v = snap.velocity;
        StringBuilder sb = new StringBuilder();
        sb.append("applied=(").append(r(v.getX())).append(",").append(r(v.getY())).append(",").append(r(v.getZ())).append(")");
        sb.append(" expH=").append(r(snap.expectedHorizontal));
        sb.append(" actH=").append(r(session.maxHorizontal));
        if (snap.sprinting && session.earlyTickCount >= 2) {
            sb.append(" earlyH=").append(r(session.earlyMaxHorizontal));
        }
        sb.append(" projH=").append(r(session.projectedHorizontal));
        sb.append(" expV=").append(r(snap.expectedVertical));
        sb.append(" actV=").append(r(session.verticalGain()));
        sb.append(" dot=").append(r(session.directionDot));
        sb.append(" ping=").append(snap.ping);
        sb.append(" tps=").append(r(snap.tps));
        sb.append(" src=").append(snap.source.name());
        sb.append(" sprint=").append(snap.sprinting);
        sb.append(" stack=").append(session.stackCount);

        if (result != null) {
            if (result.isExempt()) {
                sb.append(" exempt=").append(result.exemptReason);
            } else {
                sb.append(" zeroV=").append(result.zeroVertical);
                sb.append(" redH=").append(result.reducedHorizontal);
                sb.append(" weakFwd=").append(result.weakForwardResponse);
                sb.append(" rev=").append(result.reverseKnockback);
                sb.append(" imp=").append(result.impossiblePosition);
                sb.append(" impTicks=").append(result.impossibleTicks);
                sb.append(" outside=").append(r(result.maxOutsideDistance));
            }
        }
        return sb.toString();
    }

    private static double r(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
