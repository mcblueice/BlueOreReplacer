package net.mcblueice.blueorereplacer.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigManager {
    private final JavaPlugin plugin;
    private Map<String, Object> langData = new HashMap<>();
    private File langFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        // 語言文件
        langFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langFile.exists()) plugin.saveResource("lang.yml", false);
        YamlConfiguration langYml = YamlConfiguration.loadConfiguration(langFile);
        langData.clear();
        for (String key : langYml.getKeys(true)) {
            langData.put(key, langYml.get(key));
        }
    }

    public void reload() {
        load();
    }

    public String get(String key) {
        Object value = langData.get(key);
        String text = value != null ? value.toString() : key;
        return text.replace('&', '§');
    }

    public String get(String key, Object... args) {
        String text = get(key);
        if (text == null) return "";
        
        // 使用正則表達式匹配 %{數字} 格式的佔位符
        Pattern pattern = Pattern.compile("%\\{(\\d+)}");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1; // 轉換為數組索引
            String replacement = (index >= 0 && index < args.length)
                    ? String.valueOf(args[index])
                    : matcher.group(); // 索引無效時保留原佔位符
            
            // 轉義特殊字符後替換
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
