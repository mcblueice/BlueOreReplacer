package net.mcblueice.blueorereplacer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import net.mcblueice.blueorereplacer.utils.GenericUtil;
import net.mcblueice.blueorereplacer.utils.OreSimulateUtil;
import net.mcblueice.blueorereplacer.utils.GenericUtil.BiomeMode;
import net.mcblueice.blueorereplacer.utils.TaskScheduler;

public class Commands implements CommandExecutor, TabCompleter {
    private final BlueOreReplacer plugin;
    private static final String PREFIX = "§7§l[§b§l礦物§7§l]§r";

    public Commands(BlueOreReplacer plugin) {
        this.plugin = plugin;
    }

    // commands
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            World world;
            Player player;
            switch (args[0].toLowerCase()) {
                case "simulate":
                    if (!sender.hasPermission("blueoreplacer.debug")) {
                        sender.sendMessage(PREFIX + "§c你沒有權限使用此指令!");
                        return true;
                    }

                    if (args.length < 2) {
                        sender.sendMessage("§c用法: /blueoreplacer simulate <礦物類型> [Y高度] [生態域]");
                        return true;
                    }

                    String resolvedFeature = OreSimulateUtil.resolveFeatureName(args[1]);
                    if (resolvedFeature == null) {
                        sender.sendMessage(PREFIX + "§c未知礦物特徵: §e" + args[1]);
                        return true;
                    }

                    if (sender instanceof Player) {
                        player = (Player) sender;
                        world = player.getWorld();
                    } else {
                        world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                        if (world == null) { sender.sendMessage(PREFIX + "§c伺服器沒有載入世界"); return true; }
                        player = null;
                    }

                    Integer baseY = (sender instanceof Player p) ? p.getLocation().getBlockY() : 64;
                    Integer ySim = (args.length >= 3) ? parseCoord(args[2], baseY) : baseY;
                    if (ySim == null) {
                        sender.sendMessage(PREFIX + "§c高度必須是整數");
                        return true;
                    }

                    Location Loc;
                    if (sender instanceof Player) {
                        player = (Player) sender;
                        Loc = player.getLocation().clone();
                        Loc.setY(ySim);
                    } else {
                        Loc = new org.bukkit.Location(world, 0, ySim, 0);
                    }

                    BiomeMode parsedBiomeMode = null;
                    if (args.length >= 4) {
                        try { parsedBiomeMode = BiomeMode.valueOf(args[3].toUpperCase()); } catch (IllegalArgumentException ex) {}
                        if (parsedBiomeMode == null) {
                            sender.sendMessage(PREFIX + "§c無效生態域: §e" + args[3]);
                            return true;
                        }
                    } else {
                        parsedBiomeMode = GenericUtil.getBiomeMode(Loc);
                    }

                    final String featureName = resolvedFeature;
                    final BiomeMode biomeMode = parsedBiomeMode;
                    Runnable simulateTask = () -> {
                        Double prob = OreSimulateUtil.calculateFeatureProbability(Loc, featureName, biomeMode);
                        if (prob == null) {
                            sender.sendMessage(PREFIX + "§7該生態域下此礦物特徵無生成機率: §cN/A");
                            return;
                        }
                        double pctEff = Math.max(0, prob) * 100.0;
                        Integer veinSize = OreSimulateUtil.getFeatureVeinSize(featureName);
                        sender.sendMessage(PREFIX + "§b模擬結果 §7| §e特徵: §6" + featureName
                                + " §7| §3世界: §b" + world.getName()
                                + " §7| §d生態域: §5" + biomeMode.name().toLowerCase()
                                + " §7| §9Y: §a" + ySim
                                + (veinSize != null ? " §7| §6礦脈大小: §e" + veinSize : "")
                                + " §7| §6生成機率: §e" + String.format(java.util.Locale.US, "%.3f", pctEff) + "%");
                    };
                    if (player != null) {
                        TaskScheduler.runTask(player, plugin, simulateTask);
                    } else {
                        TaskScheduler.runTask(Loc, plugin, simulateTask);
                    }
                    return true;
                case "reload":
                    if (!sender.hasPermission("blueoreplacer.reload")) {
                        sender.sendMessage(PREFIX + "§c你沒有權限使用此指令!");
                        return true;
                    }
                    plugin.reloadConfig();
                    plugin.getLanguageManager().reload();
                    sender.sendMessage(PREFIX + "§aConfig已重新加載");
                    return true;
                case "check":
                    if (!sender.hasPermission("blueoreplacer.check")) {
                        sender.sendMessage(PREFIX + "§c你沒有權限使用此指令!");
                        return true;
                    }

                    if (args.length == 1) {
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(PREFIX + "§c主控台需提供座標及世界");
                            return true;
                        }
                        player = (Player) sender;
                        boolean on = plugin.toggleCheckMode(player.getUniqueId());
                        sender.sendMessage(PREFIX + "§b檢查模式" + (on ? "§a已開啟 §7(§e右鍵檢查方塊 §6蹲下右鍵顯示區塊資料§7)" : "§c已關閉"));
                        return true;
                    }

                    if (args.length < 4) {
                        sender.sendMessage("§c用法: /blueoreplacer check [x] [y] [z] [world]");
                        return true;
                    }
                    Player refPlayer = sender instanceof Player ? (Player) sender : null;
                    if (args.length >= 5) {
                        world = Bukkit.getWorld(args[4]);
                        if (world == null) {
                            sender.sendMessage(PREFIX + "§c世界不存在: " + args[4]);
                            return true;
                        }
                    } else {
                        if (refPlayer == null) {
                            sender.sendMessage("§c主控台用法: /blueoreplacer check <x> <y> <z> <world>");
                            return true;
                        }
                        world = refPlayer.getWorld();
                    }

                    Integer x = parseCoord(args[1], refPlayer != null ? refPlayer.getLocation().getBlockX() : null);
                    Integer y = parseCoord(args[2], refPlayer != null ? refPlayer.getLocation().getBlockY() : null);
                    Integer z = parseCoord(args[3], refPlayer != null ? refPlayer.getLocation().getBlockZ() : null);
                    if (x == null || y == null || z == null) {
                        sender.sendMessage(PREFIX + "§c座標必須是整數");
                        return true;
                    }
                    int fx = x, fy = y, fz = z;
                    Runnable checkTask = () -> {
                        Block target = world.getBlockAt(fx, fy, fz);
                        boolean modified = plugin.getChunkTracker().isModified(target);
                        sender.sendMessage(PREFIX + "§e世界: §e" + world.getName() + " §9座標: §c" + fx + " §a" + fy + " §b" + fz + " §7狀態: " + (modified ? "§6人工方塊" : "§2自然方塊"));
                    };
                    if (refPlayer != null) {
                        TaskScheduler.runTask(refPlayer, plugin, checkTask);
                    } else {
                        TaskScheduler.runTask(new Location(world, fx, fy, fz), plugin, checkTask);
                    }
                    return true;
                case "clear":
                    if (!sender.hasPermission("blueoreplacer.debug")) {
                        sender.sendMessage(PREFIX + "§c你沒有權限使用此指令!");
                        return true;
                    }
                    if (args.length == 1) {
                        if (!(sender instanceof Player p)) {
                            sender.sendMessage("§c主控台用法: /blueoreplacer clear <x> <y> <z> <world>");
                            return true;
                        }
                        var ch = p.getLocation().getChunk();
                        plugin.getChunkTracker().clear(ch);
                        sender.sendMessage(PREFIX + "§a已清除當前區塊 PDC:" + ch.getX() + "," + ch.getZ());
                        return true;
                    }
                    if (args.length >= 4) {
                        player = sender instanceof Player ? (Player) sender : null;
                        Integer qx = parseCoord(args[1], player != null ? player.getLocation().getBlockX() : null);
                        Integer qy = parseCoord(args[2], player != null ? player.getLocation().getBlockY() : null);
                        Integer qz = parseCoord(args[3], player != null ? player.getLocation().getBlockZ() : null);
                        if (qx == null || qy == null || qz == null) {
                            sender.sendMessage(PREFIX + "§c座標必須是整數 或使用 ~（僅玩家可用）");
                            return true;
                        }
                        if (args.length >= 5) {
                            world = Bukkit.getWorld(args[4]);
                            if (world == null) { sender.sendMessage(PREFIX + "§c世界不存在: " + args[4]); return true; }
                        } else if (player != null) {
                            world = player.getWorld();
                        } else {
                            sender.sendMessage(PREFIX + "§c主控台需提供世界名稱");
                            return true;
                        }
                        int cx = qx >> 4;
                        int cz = qz >> 4;
                        Runnable clearTask = () -> {
                            var ch = world.getChunkAt(cx, cz);
                            plugin.getChunkTracker().clear(ch);
                            sender.sendMessage(PREFIX + "§a已清除區塊 PDC：" + ch.getX() + "," + ch.getZ() + " @ " + world.getName());
                        };
                        if (player != null) {
                            TaskScheduler.runTask(player, plugin, clearTask);
                        } else {
                            TaskScheduler.runTask(new Location(world, (cx << 4) + 8, 0, (cz << 4) + 8), plugin, clearTask);
                        }
                        return true;
                    }
                    sender.sendMessage("§c用法: /blueoreplacer clear [x] [y] [z] [world]");
                    return true;
                case "debug":
                    if (!sender.hasPermission("blueoreplacer.debug")) {
                        sender.sendMessage(PREFIX + "§c你沒有權限使用此指令!");
                        return true;
                    }
                    if (sender instanceof Player) {
                        player = (Player) sender;
                        boolean stat = plugin.toggleDebugMode(player.getUniqueId());
                        sender.sendMessage(PREFIX + "§6Debug模式" + (stat ? "§a已開啟" : "§c已關閉"));
                    } else {
                        boolean stat = plugin.toggleDebugMode(new UUID(0L, 0L));
                        sender.sendMessage(PREFIX + "§6Debug模式" + (stat ? "§a已開啟" : "§c已關閉"));
                    }
                    return true;
                default:
                    sender.sendMessage(PREFIX + "§c用法錯誤 | /blueoreplacer reload|debug|check|clear");
                    return true;
            }
        }
        sender.sendMessage(PREFIX + "§c用法錯誤 | /blueoreplacer reload|debug|check|clear");
        return true;
    }

    // tab complete
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // main command
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("blueoreplacer.reload")) subs.add("reload");
            if (sender.hasPermission("blueoreplacer.check")) subs.add("check");
            if (sender.hasPermission("blueoreplacer.debug")) subs.add("debug");
            if (sender.hasPermission("blueoreplacer.debug")) subs.add("clear");
            if (sender.hasPermission("blueoreplacer.debug")) subs.add("simulate");
            StringUtil.copyPartialMatches(args[0], subs, completions);
            Collections.sort(completions);
            return completions;
        }

        // check
        if (args.length >= 2 && "check".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("blueoreplacer.check")) return Collections.emptyList();

            List<String> coords = new ArrayList<>();

            if (sender instanceof Player) {
                Player player = (Player) sender;
                Block targetBlock = player.getTargetBlockExact(5);
                String targetBlockXStr, targetBlockYStr, targetBlockZStr;
                if (targetBlock != null) {
                    targetBlockXStr = Integer.toString(targetBlock.getX());
                    targetBlockYStr = Integer.toString(targetBlock.getY());
                    targetBlockZStr = Integer.toString(targetBlock.getZ());
                    if (args.length == 2) {
                        coords.add(targetBlockXStr);
                        coords.add(targetBlockXStr + " " + targetBlockYStr);
                        coords.add(targetBlockXStr + " " + targetBlockYStr + " " + targetBlockZStr);
                    }
                    if (args.length == 3) {
                        coords.add(targetBlockYStr);
                        coords.add(targetBlockYStr + " " + targetBlockZStr);
                    }
                    if (args.length == 4) {
                        coords.add(targetBlockZStr);
                    }
                } else {
                    if (args.length == 2) coords.add("~ ~ ~");
                    if (args.length == 3) coords.add("~ ~");
                    if (args.length == 4) coords.add("~");
                }
            }

            if (args.length == 5) {
                for (World world : Bukkit.getWorlds()) coords.add(world.getName());
            }
            String current = args[args.length - 1];
            StringUtil.copyPartialMatches(current, coords, completions);
            return completions;
        }

        // clear
        if (args.length >= 2 && "clear".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("blueoreplacer.debug")) return Collections.emptyList();

            List<String> coords = new ArrayList<>();
            if (sender instanceof Player) {
                Player player = (Player) sender;
                Block targetBlock = player.getTargetBlockExact(5);
                if (targetBlock != null) {
                    String x = Integer.toString(targetBlock.getX());
                    String y = Integer.toString(targetBlock.getY());
                    String z = Integer.toString(targetBlock.getZ());
                    if (args.length == 2) { coords.add(x); coords.add(x + " " + y); coords.add(x + " " + y + " " + z); }
                    if (args.length == 3) { coords.add(y); coords.add(y + " " + z); }
                    if (args.length == 4) { coords.add(z); }
                } else {
                    if (args.length == 2) coords.add("~ ~ ~");
                    if (args.length == 3) coords.add("~ ~");
                    if (args.length == 4) coords.add("~");
                }
            }
            if (args.length == 5) {
                for (World world : Bukkit.getWorlds()) coords.add(world.getName());
            }
            String current = args[args.length - 1];
            StringUtil.copyPartialMatches(current, coords, completions);
            return completions;
        }

        // simulate
        if (args.length >= 2 && "simulate".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("blueoreplacer.debug")) return Collections.emptyList();

            if (args.length == 2) {
                List<String> features = OreSimulateUtil.getFeaturesName();
                StringUtil.copyPartialMatches(args[1], features, completions);
                return completions;
            }

            if (args.length == 3) {
                List<String> ys = new ArrayList<>();
                if (sender instanceof Player p) {
                    ys.add(Integer.toString(p.getLocation().getBlockY()));
                }
                StringUtil.copyPartialMatches(args[2], ys, completions);
                return completions;
            }

            if (args.length == 4) {
                List<String> biomes = List.of("normal","badlands","mountain","dripstone","mountain_dripstone","nether");
                StringUtil.copyPartialMatches(args[3], biomes, completions);
                return completions;
            }
        }

        return Collections.emptyList();
    }

    private Integer parseCoord(String token, Integer base) {
        token = token.trim();
        if (token.equals("~")) {
            return base == null ? null : base;
        }
        if (token.startsWith("~")) {
            if (base == null) return null;
            String off = token.substring(1).trim();
            if (off.isEmpty()) return base;
            try {
                int delta = Integer.parseInt(off);
                return base + delta;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
