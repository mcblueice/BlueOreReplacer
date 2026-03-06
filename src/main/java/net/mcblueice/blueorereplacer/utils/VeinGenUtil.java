package net.mcblueice.blueorereplacer.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import net.mcblueice.blueorereplacer.BlueOreReplacer;
import net.mcblueice.blueorereplacer.tracker.BlockStateTracker;

public final class VeinGenUtil {

    private VeinGenUtil() {
    }

    public record Vec3(double x, double y, double z) {
    }

    public record IntPos(int x, int y, int z) {
    }

    public record Sphere(Vec3 center, double radius) {
    }

    public record VeinPlan(List<Sphere> spheres, List<IntPos> candidates) {
    }

    public static VeinPlan planVein(int baseX, int baseY, int baseZ, int size, ThreadLocalRandom random) {
        if (size <= 0) {
            return new VeinPlan(List.of(), List.of());
        }

        double angle = random.nextDouble() * Math.PI;
        double spread = size / 8.0D;

        double startX = baseX + Math.sin(angle) * spread;
        double endX = baseX - Math.sin(angle) * spread;
        double startZ = baseZ + Math.cos(angle) * spread;
        double endZ = baseZ - Math.cos(angle) * spread;
        double startY = baseY + random.nextInt(3) - 1;
        double endY = baseY + random.nextInt(3) - 1;

        List<Sphere> raw = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double t = i / (double) size;
            double cx = lerp(startX, endX, t);
            double cy = lerp(startY, endY, t);
            double cz = lerp(startZ, endZ, t);

            double randomRadius = random.nextDouble() * size / 16.0D;
            double radius = ((Math.sin(Math.PI * t) + 1.0D) * randomRadius + 1.0D) / 2.0D;
            if (radius > 0.0D) {
                raw.add(new Sphere(new Vec3(cx, cy, cz), radius));
            }
        }

        List<Sphere> pruned = pruneCoveredSpheres(raw);
        List<IntPos> candidates = voxelize(pruned);
        return new VeinPlan(pruned, candidates);
    }

    public static void growVein(
            Block target,
            Block originBlock,
            int veinSize,
            Set<Material> forbiddenSurfaces,
            BlockFace[] faces,
            BlockStateTracker tracker,
            Predicate<Material> isUnderground,
            Predicate<Material> isOre,
            boolean debugEnabled
    ) {
        Material startMat = target.getType();
        if (!isOre.test(startMat)) return;
        if (veinSize <= 0) return;

        int size = veinSize;
        int maxExtraBlocks = Math.max(0, veinSize - 1);

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World world = target.getWorld();
        int baseX = target.getX();
        int baseY = target.getY();
        int baseZ = target.getZ();

        boolean constrainOppositeCone = false;
        double oppositeCenterAngle = 0.0D;
        final double coneHalfAngle = Math.toRadians(30.0D);
        if (originBlock != null) {
            double vecX = originBlock.getX() - baseX;
            double vecZ = originBlock.getZ() - baseZ;
            double lenSq = vecX * vecX + vecZ * vecZ;
            if (lenSq > 1.0E-6) {
                double avoidAngle = Math.atan2(vecX, vecZ);
                if (Double.isFinite(avoidAngle)) {
                    constrainOppositeCone = true;
                    oppositeCenterAngle = avoidAngle + Math.PI;
                }
            }
        }

        double dirX = 0;
        double dirZ = 0;
        double startY = baseY + rnd.nextInt(3) - 1;
        double endY = baseY + rnd.nextInt(3) - 1;

        boolean foundGoodDirection = false;
        int checkDist = Math.max(3, size / 4);

        for (int attempt = 0; attempt < 5; attempt++) {
            double angle = angleFromOppositeCone(rnd, constrainOppositeCone, oppositeCenterAngle, coneHalfAngle);
            dirX = Math.sin(angle);
            dirZ = Math.cos(angle);
            if (isDirectionSafe(world, baseX, baseY, baseZ, dirX, dirZ, checkDist, originBlock, tracker, forbiddenSurfaces, isUnderground)) {
                foundGoodDirection = true;
                break;
            }
        }

        if (!foundGoodDirection) {
            debug(debugEnabled, "  §6無法找到合適方向生成 使用最後方向生成礦脈");
        }

        Set<Long> processedKeys = new HashSet<>();
        processedKeys.add(packVein(baseX, baseY, baseZ));

        int replaced = 0;

        double spread = size / 8.0D;
        double startX = baseX + dirX * spread;
        double endX = baseX - dirX * spread;
        double startZ = baseZ + dirZ * spread;
        double endZ = baseZ - dirZ * spread;

        long originKey = (originBlock != null)
                ? packVein(originBlock.getX(), originBlock.getY(), originBlock.getZ())
                : Long.MIN_VALUE;

        for (int i = 0; i < size && replaced < maxExtraBlocks; i++) {
            double t = (double) i / (double) size;
            double cx = startX + (endX - startX) * t;
            double cy = startY + (endY - startY) * t;
            double cz = startZ + (endZ - startZ) * t;

            double randomRadius = rnd.nextDouble() * size / 16.0D;
            double radius = ((Math.sin(Math.PI * t) + 1.0D) * randomRadius + 1.0D) / 2.0D;
            if (radius <= 0.0D) {
                debug(debugEnabled, "  §8礦脈步驟 " + i + " 半徑<=0.0 (radius=" + radius + ") 跳過");
                continue;
            }

            int minX = (int) Math.floor(cx - radius);
            int maxX = (int) Math.floor(cx + radius);
            int minY = (int) Math.floor(cy - radius);
            int maxY = (int) Math.floor(cy + radius);
            int minZ = (int) Math.floor(cz - radius);
            int maxZ = (int) Math.floor(cz + radius);

            double rSq = radius * radius;

            for (int x = minX; x <= maxX && replaced < maxExtraBlocks; x++) {
                for (int y = minY; y <= maxY && replaced < maxExtraBlocks; y++) {
                    for (int z = minZ; z <= maxZ && replaced < maxExtraBlocks; z++) {
                        long key = packVein(x, y, z);
                        if (processedKeys.contains(key)) continue;

                        double dx = x + 0.5D - cx;
                        double dy = y + 0.5D - cy;
                        double dz = z + 0.5D - cz;
                        if (dx * dx + dy * dy + dz * dz >= rSq) continue;

                        processedKeys.add(key);
                        if (tryReplaceBlock(world, x, y, z, startMat, originKey, forbiddenSurfaces, faces, tracker, isUnderground)) {
                            replaced++;
                            debug(debugEnabled, "  §a礦脈[" + x + "," + y + "," + z + "] §a生成成功");
                        } else {
                            debug(debugEnabled, "  §a礦脈[" + x + "," + y + "," + z + "] §c生成失敗");
                        }
                    }
                }
            }
        }

        int totalPlaced = Math.min(veinSize, replaced + 1);
        debug(debugEnabled, "  §d礦脈生長完成: size=" + totalPlaced + "/scale=" + size);
    }

    public static List<Sphere> pruneCoveredSpheres(List<Sphere> spheres) {
        if (spheres.isEmpty()) return List.of();

        List<Sphere> result = new ArrayList<>(spheres);
        boolean[] removed = new boolean[spheres.size()];

        for (int i = 0; i < spheres.size(); i++) {
            if (removed[i]) continue;
            Sphere a = spheres.get(i);

            for (int j = 0; j < spheres.size(); j++) {
                if (i == j || removed[j]) continue;
                Sphere b = spheres.get(j);

                double dx = a.center.x - b.center.x;
                double dy = a.center.y - b.center.y;
                double dz = a.center.z - b.center.z;
                double distSq = dx * dx + dy * dy + dz * dz;

                if (a.radius >= b.radius) {
                    double dr = a.radius - b.radius;
                    if (distSq <= dr * dr) {
                        removed[j] = true;
                    }
                }
            }
        }

        List<Sphere> pruned = new ArrayList<>(spheres.size());
        for (int i = 0; i < spheres.size(); i++) {
            if (!removed[i]) pruned.add(spheres.get(i));
        }
        return pruned;
    }

    public static List<IntPos> voxelize(List<Sphere> spheres) {
        if (spheres.isEmpty()) return List.of();

        Set<Long> seen = new HashSet<>();
        List<IntPos> out = new ArrayList<>();

        for (Sphere sphere : spheres) {
            Vec3 c = sphere.center;
            double r = sphere.radius;
            double rSq = r * r;

            int minX = (int) Math.floor(c.x - r);
            int maxX = (int) Math.floor(c.x + r);
            int minY = (int) Math.floor(c.y - r);
            int maxY = (int) Math.floor(c.y + r);
            int minZ = (int) Math.floor(c.z - r);
            int maxZ = (int) Math.floor(c.z + r);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        double dx = x + 0.5D - c.x;
                        double dy = y + 0.5D - c.y;
                        double dz = z + 0.5D - c.z;
                        if (dx * dx + dy * dy + dz * dz > rSq) continue;

                        long key = pack(x, y, z);
                        if (seen.add(key)) {
                            out.add(new IntPos(x, y, z));
                        }
                    }
                }
            }
        }

        return out;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | ((long) y & 0xFFFL);
    }

    private static double angleFromOppositeCone(ThreadLocalRandom rnd, boolean constrain, double centerAngle, double halfAngle) {
        double fullCircle = Math.PI * 2.0D;
        if (!constrain) {
            return rnd.nextDouble() * fullCircle;
        }

        double offset = (rnd.nextDouble() * 2.0D - 1.0D) * halfAngle;
        double angle = centerAngle + offset;
        if (!Double.isFinite(angle)) {
            return rnd.nextDouble() * fullCircle;
        }
        double normalized = angle % fullCircle;
        return normalized < 0.0D ? normalized + fullCircle : normalized;
    }

    private static boolean isDirectionSafe(
            World world,
            int bx,
            int by,
            int bz,
            double dx,
            double dz,
            int dist,
            Block origin,
            BlockStateTracker tracker,
            Set<Material> forbiddenSurfaces,
            Predicate<Material> isUnderground
    ) {
        for (int s = 1; s <= dist; s++) {
            int cx = (int) Math.round(bx + dx * s);
            int cz = (int) Math.round(bz + dz * s);
            for (int yOff = -1; yOff <= 1; yOff++) {
                int cy = by + yOff;
                Block b = world.getBlockAt(cx, cy, cz);
                if (origin != null && b.equals(origin)) continue;
                Material m = b.getType();
                if (tracker.isModified(b) || forbiddenSurfaces.contains(m) || !isUnderground.test(m)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean tryReplaceBlock(
            World world,
            int x,
            int y,
            int z,
            Material targetType,
            long originKey,
            Set<Material> forbiddenSurfaces,
            BlockFace[] faces,
            BlockStateTracker tracker,
            Predicate<Material> isUnderground
    ) {
        Block b = world.getBlockAt(x, y, z);
        Material mat = b.getType();

        if (originKey != Long.MIN_VALUE && packVein(x, y, z) == originKey) return false;
        if (forbiddenSurfaces.contains(mat)) return false;
        if (!isUnderground.test(mat)) return false;
        if (tracker.isExposed(b)) return false;
        if (tracker.isModified(b)) return false;

        for (BlockFace face : faces) {
            int nx = x + face.getModX();
            int ny = y + face.getModY();
            int nz = z + face.getModZ();

            Block neighbor = world.getBlockAt(nx, ny, nz);

            if (originKey != Long.MIN_VALUE && packVein(nx, ny, nz) == originKey) continue;
            if (neighbor.getType() == targetType) continue;

            Material nMat = neighbor.getType();
            if (forbiddenSurfaces.contains(nMat) || !isUnderground.test(nMat)) return false;
            if (tracker.isModified(neighbor)) return false;
        }

        if (OreReplaceUtil.CANT_REPLACE_BLOCKS.contains(mat)) return false;
        b.setType(targetType, false);
        tracker.markModified(b);
        return true;
    }

    private static long packVein(int x, int y, int z) {
        return ((long) x & 0x7FFFFFFL) | (((long) z & 0x7FFFFFFL) << 27) | (((long) y & 0x3FFFL) << 54);
    }

    private static void debug(boolean enabled, String message) {
        if (enabled) {
            BlueOreReplacer.sendDebug(message);
        }
    }
}
