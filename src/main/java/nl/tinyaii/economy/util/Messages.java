package nl.tinyaii.economy.util;

import nl.tinyaii.economy.EconomyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * 消息工具：统一前缀 + 占位符替换。
 */
public class Messages {
    private final EconomyPlugin plugin;
    private YamlConfiguration cfg;
    private String prefix;

    public Messages(EconomyPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "config.yml");
        if (!f.exists()) plugin.saveDefaultConfig();
        cfg = YamlConfiguration.loadConfiguration(f);
        prefix = color(cfg.getString("messages.prefix", ""));
    }

    public String raw(String key, String... repl) {
        String msg = cfg.getString("messages." + key, key);
        for (int i = 0; i + 1 < repl.length; i += 2) {
            msg = msg.replace(repl[i], repl[i + 1]);
        }
        return prefix + color(msg);
    }

    public void send(Player p, String key, String... repl) {
        if (p != null) p.sendMessage(raw(key, repl));
    }

    public String currencyName() {
        return cfg.getString("settings.currency-name", "金币");
    }

    /** 金额格式化：去尾零（100.00 → 100，12.50 → 12.5） */
    public static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
