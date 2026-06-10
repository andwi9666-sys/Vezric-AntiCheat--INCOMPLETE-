package com.colin.vezanticheat.engine;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Collisions — vanilla 1.8.8 AABB collision against a {@link BlockProvider}.
 *
 * Ported from the proven block-shape logic in the legacy VelocityPredictionEngine, but
 * generalized to read from the packet-synced {@link CompensatedWorld} (with live-world
 * fallback) instead of the live Bukkit world directly.
 *
 * The {@link #collide} entry point resolves a desired movement vector for a player box,
 * trying both X-then-Z and Z-then-X axis orders (vanilla picks the order yielding the
 * larger horizontal travel) and applying a 0.5-block auto step-up when blocked on the
 * ground, matching 1.8 EntityPlayer.moveEntity().
 */
public final class Collisions {

    public static final double STEP_HEIGHT = 0.50D;

    private Collisions() {}

    /** Result of a single collide() pass. */
    public static final class Result {
        public double x;
        public double y;
        public double z;
        public double movedX;
        public double movedY;
        public double movedZ;
        public boolean collisionX;
        public boolean collisionY;
        public boolean collisionZ;
        public boolean onGround;

        double horizontalSq() {
            return movedX * movedX + movedZ * movedZ;
        }
    }

    public static Result collide(BlockProvider provider, double x, double y, double z, double dx, double dy, double dz, boolean onGround) {
        SimpleCollisionBox box = SimpleCollisionBox.playerBox(x, y, z);
        List<SimpleCollisionBox> boxes = getCollisionBoxes(provider, box.expandTowards(dx, dy, dz).grow(0.001D));

        Result a = collideAxis(box, boxes, dx, dy, dz, true);
        Result b = collideAxis(box, boxes, dx, dy, dz, false);
        Result direct = a.horizontalSq() >= b.horizontalSq() ? a : b;

        boolean blockedHorizontally = direct.collisionX || direct.collisionZ;
        boolean canStep = blockedHorizontally && (onGround || dy < 0.0D);
        if (!canStep) {
            return direct;
        }

        List<SimpleCollisionBox> stepBoxes = getCollisionBoxes(provider, box.expandTowards(dx, STEP_HEIGHT, dz).grow(0.001D));
        Result stepA = collideStep(box, stepBoxes, dx, dy, dz, true);
        Result stepB = collideStep(box, stepBoxes, dx, dy, dz, false);
        Result stepped = stepA.horizontalSq() >= stepB.horizontalSq() ? stepA : stepB;
        return stepped.horizontalSq() > direct.horizontalSq() ? stepped : direct;
    }

    private static Result collideStep(SimpleCollisionBox box, List<SimpleCollisionBox> boxes, double dx, double dy, double dz, boolean xThenZ) {
        double up = STEP_HEIGHT;
        for (SimpleCollisionBox b : boxes) {
            up = b.collideY(box, up);
        }
        SimpleCollisionBox stepped = box.offset(0.0D, up, 0.0D);
        Result horizontal = collideAxis(stepped, boxes, dx, 0.0D, dz, xThenZ);

        double down = dy - up;
        SimpleCollisionBox afterH = SimpleCollisionBox.playerBox(horizontal.x, horizontal.y, horizontal.z);
        double resolvedDown = down;
        for (SimpleCollisionBox b : boxes) {
            resolvedDown = b.collideY(afterH, resolvedDown);
        }
        afterH = afterH.offset(0.0D, resolvedDown, 0.0D);

        Result r = new Result();
        r.x = afterH.centerX();
        r.y = afterH.minY;
        r.z = afterH.centerZ();
        r.movedX = horizontal.movedX;
        r.movedY = up + resolvedDown;
        r.movedZ = horizontal.movedZ;
        r.collisionX = horizontal.collisionX;
        r.collisionZ = horizontal.collisionZ;
        r.collisionY = resolvedDown != down;
        r.onGround = (resolvedDown != down && down < 0.0D) || (up != 0.0D);
        return r;
    }

    private static Result collideAxis(SimpleCollisionBox box, List<SimpleCollisionBox> boxes, double dx, double dy, double dz, boolean xThenZ) {
        double movedX = dx;
        double movedY = dy;
        double movedZ = dz;

        for (SimpleCollisionBox b : boxes) {
            movedY = b.collideY(box, movedY);
        }
        box = box.offset(0.0D, movedY, 0.0D);

        if (xThenZ) {
            for (SimpleCollisionBox b : boxes) {
                movedX = b.collideX(box, movedX);
            }
            box = box.offset(movedX, 0.0D, 0.0D);
            for (SimpleCollisionBox b : boxes) {
                movedZ = b.collideZ(box, movedZ);
            }
            box = box.offset(0.0D, 0.0D, movedZ);
        } else {
            for (SimpleCollisionBox b : boxes) {
                movedZ = b.collideZ(box, movedZ);
            }
            box = box.offset(0.0D, 0.0D, movedZ);
            for (SimpleCollisionBox b : boxes) {
                movedX = b.collideX(box, movedX);
            }
            box = box.offset(movedX, 0.0D, 0.0D);
        }

        Result r = new Result();
        r.x = box.centerX();
        r.y = box.minY;
        r.z = box.centerZ();
        r.movedX = movedX;
        r.movedY = movedY;
        r.movedZ = movedZ;
        r.collisionX = movedX != dx;
        r.collisionY = movedY != dy;
        r.collisionZ = movedZ != dz;
        r.onGround = movedY != dy && dy < 0.0D;
        return r;
    }

    public static boolean isOnGround(BlockProvider provider, double x, double y, double z) {
        SimpleCollisionBox feet = SimpleCollisionBox.playerBox(x, y, z).offset(0.0D, -0.001D, 0.0D);
        List<SimpleCollisionBox> boxes = getCollisionBoxes(provider, feet);
        for (SimpleCollisionBox b : boxes) {
            if (b.intersects(feet)) return true;
        }
        return false;
    }

    public static List<SimpleCollisionBox> getCollisionBoxes(BlockProvider provider, SimpleCollisionBox search) {
        List<SimpleCollisionBox> boxes = new ArrayList<SimpleCollisionBox>();
        if (provider == null) return boxes;

        int minX = floor(search.minX) - 1;
        int maxX = floor(search.maxX) + 1;
        int minY = floor(search.minY) - 1;
        int maxY = floor(search.maxY) + 1;
        int minZ = floor(search.minZ) - 1;
        int maxZ = floor(search.maxZ) + 1;

        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (!provider.isChunkLoaded(bx, bz)) continue;
                for (int by = minY; by <= maxY; by++) {
                    addBlockBoxes(provider, bx, by, bz, boxes, search);
                }
            }
        }
        return boxes;
    }

    private static void addBlockBoxes(BlockProvider provider, int x, int y, int z, List<SimpleCollisionBox> into, SimpleCollisionBox search) {
        Material material = provider.getType(x, y, z);
        if (material == null || isNonCollidable(material)) return;
        byte data = provider.getData(x, y, z);

        List<SimpleCollisionBox> local = new ArrayList<SimpleCollisionBox>(2);

        if (material == Material.SOUL_SAND) {
            local.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 0.875D, z + 1.0D));
        } else if (material == Material.CARPET) {
            local.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 0.0625D, z + 1.0D));
        } else if (material == Material.SNOW) {
            double height = Math.min(1.0D, ((data & 7) + 1) / 8.0D);
            local.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + height, z + 1.0D));
        } else if (material == Material.CACTUS) {
            local.add(new SimpleCollisionBox(x + 0.0625D, y, z + 0.0625D, x + 0.9375D, y + 1.0D, z + 0.9375D));
        } else if (material == Material.BED_BLOCK) {
            local.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 0.5625D, z + 1.0D));
        } else if (isFenceLike(material)) {
            local.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 1.5D, z + 1.0D));
        } else if (material == Material.FENCE_GATE) {
            addFenceGateBoxes(x, y, z, data, local);
        } else if (isPane(material)) {
            addPaneBoxes(provider, x, y, z, local);
        } else if (material == Material.TRAP_DOOR) {
            addTrapDoorBoxes(x, y, z, data, local);
        } else if (isDoor(material)) {
            addDoorBoxes(provider, x, y, z, data, local);
        } else if (isSlab(material)) {
            boolean top = (data & 8) != 0;
            if (isDoubleSlab(material)) {
                local.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
            } else if (top) {
                local.add(new SimpleCollisionBox(x, y + 0.5D, z, x + 1.0D, y + 1.0D, z + 1.0D));
            } else {
                local.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 0.5D, z + 1.0D));
            }
        } else if (isStair(material)) {
            addStairBoxes(x, y, z, data, local);
        } else if (material.isSolid()) {
            local.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
        }

        for (SimpleCollisionBox box : local) {
            if (box.intersects(search)) {
                into.add(box);
            }
        }
    }

    private static void addFenceGateBoxes(int x, int y, int z, byte data, List<SimpleCollisionBox> boxes) {
        boolean open = (data & 4) != 0;
        if (open) return;
        double thickness = 0.25D;
        int orientation = data & 3;
        if (orientation == 0 || orientation == 2) {
            boxes.add(new SimpleCollisionBox(x, y, z + 0.5D - thickness, x + 1.0D, y + 1.5D, z + 0.5D + thickness));
        } else {
            boxes.add(new SimpleCollisionBox(x + 0.5D - thickness, y, z, x + 0.5D + thickness, y + 1.5D, z + 1.0D));
        }
    }

    private static void addPaneBoxes(BlockProvider provider, int x, int y, int z, List<SimpleCollisionBox> boxes) {
        double min = 0.4375D;
        double max = 0.5625D;
        boxes.add(new SimpleCollisionBox(x + min, y, z + min, x + max, y + 1.0D, z + max));
        if (connectsToPane(provider.getType(x + 1, y, z))) {
            boxes.add(new SimpleCollisionBox(x + max, y, z + min, x + 1.0D, y + 1.0D, z + max));
        }
        if (connectsToPane(provider.getType(x - 1, y, z))) {
            boxes.add(new SimpleCollisionBox(x, y, z + min, x + min, y + 1.0D, z + max));
        }
        if (connectsToPane(provider.getType(x, y, z + 1))) {
            boxes.add(new SimpleCollisionBox(x + min, y, z + max, x + max, y + 1.0D, z + 1.0D));
        }
        if (connectsToPane(provider.getType(x, y, z - 1))) {
            boxes.add(new SimpleCollisionBox(x + min, y, z, x + max, y + 1.0D, z + min));
        }
    }

    private static boolean connectsToPane(Material material) {
        return material != null && (material.isSolid() || isPane(material));
    }

    private static void addTrapDoorBoxes(int x, int y, int z, byte data, List<SimpleCollisionBox> boxes) {
        boolean open = (data & 4) != 0;
        boolean top = (data & 8) != 0;
        double thickness = 0.1875D;
        if (!open) {
            if (top) {
                boxes.add(new SimpleCollisionBox(x, y + 1.0D - thickness, z, x + 1.0D, y + 1.0D, z + 1.0D));
            } else {
                boxes.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + thickness, z + 1.0D));
            }
            return;
        }
        switch (data & 3) {
            case 0:
                boxes.add(new SimpleCollisionBox(x, y, z + 1.0D - thickness, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
            case 1:
                boxes.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 1.0D, z + thickness));
                break;
            case 2:
                boxes.add(new SimpleCollisionBox(x + 1.0D - thickness, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
            default:
                boxes.add(new SimpleCollisionBox(x, y, z, x + thickness, y + 1.0D, z + 1.0D));
                break;
        }
    }

    private static void addDoorBoxes(BlockProvider provider, int x, int y, int z, byte data, List<SimpleCollisionBox> boxes) {
        byte baseData = data;
        if ((data & 8) != 0) {
            baseData = provider.getData(x, y - 1, z);
        }
        boolean open = (baseData & 4) != 0;
        int facing = baseData & 3;
        double thickness = 0.1875D;

        int effective = facing;
        if (open) {
            switch (facing) {
                case 0: effective = 1; break;
                case 1: effective = 2; break;
                case 2: effective = 3; break;
                default: effective = 0; break;
            }
        }
        switch (effective) {
            case 0:
                boxes.add(new SimpleCollisionBox(x, y, z, x + thickness, y + 1.0D, z + 1.0D));
                break;
            case 1:
                boxes.add(new SimpleCollisionBox(x, y, z, x + 1.0D, y + 1.0D, z + thickness));
                break;
            case 2:
                boxes.add(new SimpleCollisionBox(x + 1.0D - thickness, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
            default:
                boxes.add(new SimpleCollisionBox(x, y, z + 1.0D - thickness, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
        }
    }

    private static void addStairBoxes(int x, int y, int z, byte data, List<SimpleCollisionBox> boxes) {
        boolean top = (data & 4) != 0;
        int facing = data & 3;
        double lowerMinY = top ? 0.5D : 0.0D;
        double lowerMaxY = top ? 1.0D : 0.5D;
        double upperMinY = top ? 0.0D : 0.5D;
        double upperMaxY = top ? 0.5D : 1.0D;

        boxes.add(new SimpleCollisionBox(x, y + lowerMinY, z, x + 1.0D, y + lowerMaxY, z + 1.0D));
        switch (facing) {
            case 0:
                boxes.add(new SimpleCollisionBox(x + 0.5D, y + upperMinY, z, x + 1.0D, y + upperMaxY, z + 1.0D));
                break;
            case 1:
                boxes.add(new SimpleCollisionBox(x, y + upperMinY, z, x + 0.5D, y + upperMaxY, z + 1.0D));
                break;
            case 2:
                boxes.add(new SimpleCollisionBox(x, y + upperMinY, z + 0.5D, x + 1.0D, y + upperMaxY, z + 1.0D));
                break;
            default:
                boxes.add(new SimpleCollisionBox(x, y + upperMinY, z, x + 1.0D, y + upperMaxY, z + 0.5D));
                break;
        }
    }

    public static boolean isNonCollidable(Material material) {
        if (material == Material.AIR
                || material == Material.WATER || material == Material.STATIONARY_WATER
                || material == Material.LAVA || material == Material.STATIONARY_LAVA
                || material == Material.VINE
                || material == Material.WEB
                || material == Material.LADDER) {
            return true;
        }
        String name = material.name();
        return name.contains("SIGN")
                || name.contains("BUTTON")
                || name.contains("PLATE")
                || name.contains("LEVER")
                || name.contains("TORCH")
                || name.contains("FLOWER")
                || name.contains("MUSHROOM")
                || name.contains("SAPLING")
                || name.contains("BANNER")
                || name.contains("RAIL")
                || name.contains("REDSTONE")
                || name.contains("CROPS")
                || name.equals("CARROT")
                || name.equals("POTATO")
                || name.equals("SUGAR_CANE_BLOCK")
                || name.equals("LONG_GRASS")
                || name.equals("DEAD_BUSH");
    }

    private static boolean isFenceLike(Material material) {
        String name = material.name();
        return name.contains("FENCE") && !name.contains("GATE") || name.contains("WALL") && !name.contains("SIGN");
    }

    private static boolean isSlab(Material material) {
        String name = material.name();
        return name.contains("STEP") || name.contains("SLAB");
    }

    private static boolean isDoubleSlab(Material material) {
        return material.name().contains("DOUBLE");
    }

    private static boolean isStair(Material material) {
        return material.name().contains("STAIRS");
    }

    private static boolean isPane(Material material) {
        String name = material.name();
        return name.contains("PANE") || material == Material.THIN_GLASS || material == Material.IRON_FENCE;
    }

    private static boolean isDoor(Material material) {
        String name = material.name();
        return name.contains("DOOR") && !name.contains("TRAP");
    }

    public static int floor(double value) {
        return (int) Math.floor(value);
    }
}
