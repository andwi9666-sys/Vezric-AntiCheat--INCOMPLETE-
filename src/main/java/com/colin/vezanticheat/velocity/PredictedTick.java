package com.colin.vezanticheat.velocity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Axis-aligned bounds of valid player position for one post-knockback tick.
 */
public final class PredictedTick {

    public final int tickIndex;
    public final double minX;
    public final double maxX;
    public final double minY;
    public final double maxY;
    public final double minZ;
    public final double maxZ;
    private final List<SamplePoint> samplePoints;

    public PredictedTick(int tickIndex, double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
                         List<SamplePoint> samplePoints) {
        this.tickIndex = tickIndex;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.samplePoints = samplePoints == null ? Collections.<SamplePoint>emptyList() : samplePoints;
    }

    public boolean contains(double x, double y, double z) {
        if (samplePoints.isEmpty()) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
        for (SamplePoint sample : samplePoints) {
            if (sample.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public double distanceOutside(double x, double y, double z) {
        if (!samplePoints.isEmpty()) {
            double best = Double.MAX_VALUE;
            for (SamplePoint sample : samplePoints) {
                best = Math.min(best, sample.distanceOutside(x, y, z));
            }
            return best == Double.MAX_VALUE ? 0.0D : best;
        }

        double dx = 0.0;
        if (x < minX) dx = minX - x;
        else if (x > maxX) dx = x - maxX;

        double dy = 0.0;
        if (y < minY) dy = minY - y;
        else if (y > maxY) dy = y - maxY;

        double dz = 0.0;
        if (z < minZ) dz = minZ - z;
        else if (z > maxZ) dz = z - maxZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double horizontalDistanceOutside(double x, double z) {
        if (!samplePoints.isEmpty()) {
            double best = Double.MAX_VALUE;
            for (SamplePoint sample : samplePoints) {
                best = Math.min(best, sample.horizontalDistanceOutside(x, z));
            }
            return best == Double.MAX_VALUE ? 0.0D : best;
        }

        double dx = 0.0D;
        if (x < minX) dx = minX - x;
        else if (x > maxX) dx = x - maxX;

        double dz = 0.0D;
        if (z < minZ) dz = minZ - z;
        else if (z > maxZ) dz = z - maxZ;

        return Math.sqrt(dx * dx + dz * dz);
    }

    public double verticalDistanceOutside(double y) {
        if (!samplePoints.isEmpty()) {
            double best = Double.MAX_VALUE;
            for (SamplePoint sample : samplePoints) {
                best = Math.min(best, sample.verticalDistanceOutside(y));
            }
            return best == Double.MAX_VALUE ? 0.0D : best;
        }
        if (y < minY) return minY - y;
        if (y > maxY) return y - maxY;
        return 0.0D;
    }

    public PredictedTick union(PredictedTick other) {
        if (other == null) return this;
        List<SamplePoint> merged = new ArrayList<SamplePoint>(samplePoints.size() + other.samplePoints.size());
        merged.addAll(samplePoints);
        merged.addAll(other.samplePoints);
        return new PredictedTick(
                tickIndex,
                Math.min(minX, other.minX),
                Math.max(maxX, other.maxX),
                Math.min(minY, other.minY),
                Math.max(maxY, other.maxY),
                Math.min(minZ, other.minZ),
                Math.max(maxZ, other.maxZ),
                merged
        );
    }

    public static PredictedTick fromPoint(int tick, double x, double y, double z, double tolH, double tolV) {
        List<SamplePoint> samples = new ArrayList<SamplePoint>(1);
        samples.add(new SamplePoint(x, y, z, tolH, tolV));
        return new PredictedTick(
                tick,
                x - tolH, x + tolH,
                y - tolV, y + tolV,
                z - tolH, z + tolH
                , samples
        );
    }

    public LocationPoint closestPoint(double x, double y, double z) {
        if (samplePoints.isEmpty()) {
            return new LocationPoint(centerX(), centerY(), centerZ());
        }
        SamplePoint best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SamplePoint sample : samplePoints) {
            double dx = sample.x - x;
            double dy = sample.y - y;
            double dz = sample.z - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = sample;
            }
        }
        return best == null ? new LocationPoint(centerX(), centerY(), centerZ()) : new LocationPoint(best.x, best.y, best.z);
    }

    public double centerX() { return (minX + maxX) * 0.5; }
    public double centerY() { return (minY + maxY) * 0.5; }
    public double centerZ() { return (minZ + maxZ) * 0.5; }

    public double maxHorizontalDistanceFrom(double x, double z) {
        if (!samplePoints.isEmpty()) {
            double best = 0.0D;
            for (SamplePoint sample : samplePoints) {
                double dx = sample.x - x;
                double dz = sample.z - z;
                best = Math.max(best, Math.sqrt(dx * dx + dz * dz) + sample.tolH);
            }
            return best;
        }

        double dx = Math.max(Math.abs(minX - x), Math.abs(maxX - x));
        double dz = Math.max(Math.abs(minZ - z), Math.abs(maxZ - z));
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static final class LocationPoint {
        public final double x;
        public final double y;
        public final double z;

        public LocationPoint(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class SamplePoint {
        private final double x;
        private final double y;
        private final double z;
        private final double tolH;
        private final double tolV;

        private SamplePoint(double x, double y, double z, double tolH, double tolV) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.tolH = tolH;
            this.tolV = tolV;
        }

        private boolean contains(double px, double py, double pz) {
            return px >= x - tolH && px <= x + tolH
                    && py >= y - tolV && py <= y + tolV
                    && pz >= z - tolH && pz <= z + tolH;
        }

        private double distanceOutside(double px, double py, double pz) {
            double dx = 0.0D;
            if (px < x - tolH) dx = (x - tolH) - px;
            else if (px > x + tolH) dx = px - (x + tolH);

            double dy = 0.0D;
            if (py < y - tolV) dy = (y - tolV) - py;
            else if (py > y + tolV) dy = py - (y + tolV);

            double dz = 0.0D;
            if (pz < z - tolH) dz = (z - tolH) - pz;
            else if (pz > z + tolH) dz = pz - (z + tolH);

            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private double horizontalDistanceOutside(double px, double pz) {
            double dx = 0.0D;
            if (px < x - tolH) dx = (x - tolH) - px;
            else if (px > x + tolH) dx = px - (x + tolH);

            double dz = 0.0D;
            if (pz < z - tolH) dz = (z - tolH) - pz;
            else if (pz > z + tolH) dz = pz - (z + tolH);

            return Math.sqrt(dx * dx + dz * dz);
        }

        private double verticalDistanceOutside(double py) {
            if (py < y - tolV) return (y - tolV) - py;
            if (py > y + tolV) return py - (y + tolV);
            return 0.0D;
        }
    }
}
