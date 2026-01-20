package net.mcblueice.blueorereplacer.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class TaskScheduler {
    public static final boolean isFolia = checkFolia();

    private TaskScheduler() {
        throw new IllegalStateException("Utility class should not be instantiated");
    }

    private static boolean checkFolia() {
        if (Bukkit.getVersion().toLowerCase().contains("folia")) return true;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // region 全域
    public static void runTask(Plugin plugin, Runnable task) { runGlobalTask(plugin, task); }
    public static void runTaskLater(Plugin plugin, Runnable task, long delay) { runGlobalTaskLater(plugin, task, delay); }
    public static void runGlobalTask(Plugin plugin, Runnable task) {
        if (plugin == null || task == null) return;
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    public static void runGlobalTaskLater(Plugin plugin, Runnable task, long delay) {
        if (plugin == null || task == null) return;
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }
    // endregion 全域
    
    // region 區域
    public static void runTask(Location loc, Plugin plugin, Runnable task) { runRegionTask(loc, plugin, task); }
    public static void runTaskLater(Location loc, Plugin plugin, Runnable task, long delay) { runRegionTaskLater(loc, plugin, task, delay); }
    public static void runRegionTask(Location loc, Plugin plugin, Runnable task) {
        if (loc == null || plugin == null || task == null) return;
        if (isFolia) {
            Bukkit.getRegionScheduler().run(plugin, loc, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    public static void runRegionTaskLater(Location loc, Plugin plugin, Runnable task, long delay) {
        if (loc == null || plugin == null || task == null) return;
        if (isFolia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, loc, scheduledTask -> task.run(), delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }
    // endregion 區域

    // region 玩家
    public static void runTask(Player player, Plugin plugin, Runnable task) { runPlayerTask(player, plugin, task); }
    public static void runTaskLater(Player player, Plugin plugin, Runnable task, long delay) { runPlayerTaskLater(player, plugin, task, delay); }
    public static void runPlayerTask(Player player, Plugin plugin, Runnable task) {
        if (player == null || plugin == null || task == null) return;
        if (isFolia) {
            player.getScheduler().run(plugin, scheduledTask -> task.run(), () -> {});
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    public static void runPlayerTaskLater(Player player, Plugin plugin, Runnable task, long delay) {
        if (player == null || plugin == null || task == null) return;
        if (isFolia) {
            player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), () -> {}, delay);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public static void dispatchCommand(Player player, Plugin plugin, String command) {
        runTask(player, plugin, () -> Bukkit.dispatchCommand(player, command));
    }
    // endregion 玩家

    // region 異步
    public static void runAsync(Plugin plugin, Runnable task) {
        if (plugin == null || task == null) return;
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
    // endregion 異步

// region 重複
    // region 全域
    public static RepeatingTaskHandler runRepeatingTask(Plugin plugin, Runnable task, long delay, long interval) { return runGlobalRepeatingTask(plugin, task, delay, interval); }
    public static RepeatingTaskHandler runGlobalRepeatingTask(Plugin plugin, Runnable task, long delay, long interval) {
        delay = Math.max(1L, delay);
        interval = Math.max(1L, interval);
        if (isFolia) {
            ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, st -> task.run(), delay, interval);
            return scheduledTask::cancel;
        } else {
            int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delay, interval);
            return () -> Bukkit.getScheduler().cancelTask(id);
        }
    }
    // endregion 全域

    // region 區域
    public static RepeatingTaskHandler runRepeatingTask(Location loc, Plugin plugin, Runnable task, long delay, long interval) { return runRegionRepeatingTask(loc, plugin, task, delay, interval); }
    public static RepeatingTaskHandler runRegionRepeatingTask(Location loc, Plugin plugin, Runnable task, long delay, long interval) {
        delay = Math.max(1L, delay);
        interval = Math.max(1L, interval);
        if (isFolia) {
            ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, st -> task.run(), delay, interval);
            return scheduledTask::cancel;
        } else {
            int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delay, interval);
            return () -> Bukkit.getScheduler().cancelTask(id);
        }
    }
    // endregion 區域

    // region 玩家
    public static RepeatingTaskHandler runRepeatingTask(Player player, Plugin plugin, Runnable task, long delay, long interval) { return runPlayerRepeatingTask(player, plugin, task, delay, interval); }
    public static RepeatingTaskHandler runPlayerRepeatingTask(Player player, Plugin plugin, Runnable task, long delay, long interval) {
        delay = Math.max(1L, delay);
        interval = Math.max(1L, interval);
        if (isFolia) {
            ScheduledTask scheduledTask = player.getScheduler().runAtFixedRate(plugin, st -> task.run(), () -> {}, delay, interval);
            return scheduledTask::cancel;
        } else {
            int id = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delay, interval);
            return () -> Bukkit.getScheduler().cancelTask(id);
        }
    }
    // endregion 玩家

    // region 異步
    public static RepeatingTaskHandler runAsyncRepeatingTask(Plugin plugin, Runnable task, long delay, long interval) {
        delay = Math.max(1L, delay);
        interval = Math.max(1L, interval);
        if (isFolia) {
            long delayMs = delay * 50L;
            long intervalMs = interval * 50L;
            ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, st -> task.run(), delayMs, intervalMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return scheduledTask::cancel;
        } else {
            int id = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, interval).getTaskId();
            return () -> Bukkit.getScheduler().cancelTask(id);
        }
    }
    // endregion 異步

    @FunctionalInterface
    public static interface RepeatingTaskHandler {
        void cancel();
    }
// endregion 重複
}