package com.colin.vezanticheat.utils;

import java.util.Collection;

public final class MathUtil {
    private MathUtil() {}

    public static double mean(Collection<? extends Number> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Number v : values) sum += v.doubleValue();
        return sum / values.size();
    }

    public static double std(Collection<? extends Number> values) {
        if (values == null || values.size() < 2) return 0.0;
        double avg = mean(values);
        double sum = 0.0;
        for (Number v : values) {
            double d = v.doubleValue() - avg;
            sum += d * d;
        }
        return Math.sqrt(sum / (values.size() - 1));
    }

    public static double cv(Collection<? extends Number> values) {
        double avg = mean(values);
        if (Math.abs(avg) < 1.0E-8) return 999.0;
        return std(values) / Math.abs(avg);
    }

    public static double range(Collection<? extends Number> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (Number v : values) {
            double d = v.doubleValue();
            if (d < min) min = d;
            if (d > max) max = d;
        }
        return max - min;
    }

    public static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
