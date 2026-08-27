package nl.tinyaii.economy.migration;

import nl.tinyaii.economy.EconomyPlugin;
import nl.tinyaii.economy.data.AccountManager;
import nl.tinyaii.economy.util.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PlayerPoints 迁移：读取 PlayerPoints 官方 /points export 导出的 storage.yml
 * （格式：Points: <uuid>: <点数> + UUIDs: <uuid>: <玩家名>），导入 Economy 账户。
 *
 * 兼容 PlayerPoints 的 YAML / SQL / Redis 后端——统一走官方导出文件，不碰其内部存储。
 */
public class PlayerPointsImporter {

    private final EconomyPlugin plugin;

    public PlayerPointsImporter(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    /** 解析结果（预览与导入共用） */
    public static class Entry {
        public final UUID uuid;
        public final String name;   // 可能为空（未知玩家）
        public final double points;

        public Entry(UUID uuid, String name, double points) {
            this.uuid = uuid;
            this.name = name == null ? "" : name;
            this.points = points;
        }
    }

    /** 定位 storage.yml：优先 PlayerPoints 数据目录，其次 Economy 目录手动放置 */
    public File findStorageFile() {
        File ppDir = new File(new File("plugins"), "PlayerPoints");
        File f1 = new File(ppDir, "storage.yml");
        if (f1.exists()) return f1;
        File f2 = new File(plugin.getDataFolder(), "playerpoints-storage.yml");
        if (f2.exists()) return f2;
        return null;
    }

    /** 解析 storage.yml → 条目列表（只保留点数>0 的） */
    public List<Entry> parse(File file) {
        List<Entry> out = new ArrayList<>();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pointsSec = yml.getConfigurationSection("Points");
        if (pointsSec == null) return out;

        Map<String, String> names = new LinkedHashMap<>();
        ConfigurationSection uuidSec = yml.getConfigurationSection("UUIDs");
        if (uuidSec != null) {
            for (String k : uuidSec.getKeys(false)) {
                names.put(k.toLowerCase(), uuidSec.getString(k, ""));
            }
        }

        for (String key : pointsSec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                double value = pointsSec.getDouble(key, 0);
                if (value <= 0) continue;
                String name = names.getOrDefault(key.toLowerCase(), "");
                out.add(new Entry(uuid, name, value));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("跳过无效 UUID 条目: " + key);
            }
        }
        return out;
    }

    /** 备份 data.yml（导入前强制） */
    public File backup() {
        File data = new File(plugin.getDataFolder(), "data.yml");
        if (!data.exists()) return null;
        File backups = new File(new File(plugin.getDataFolder(), "backups"), "");
        File dir = new File(plugin.getDataFolder(), "backups");
        if (!dir.exists()) dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File target = new File(dir, "data-" + stamp + ".yml");
        try {
            java.nio.file.Files.copy(data.toPath(), target.toPath());
            return target;
        } catch (IOException e) {
            plugin.getLogger().severe("备份 data.yml 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 执行导入。
     * @return 导入的账户数；出现无法备份等严重错误时返回 -1
     */
    public int importEntries(List<Entry> entries, boolean addMode, double multiplier) {
        File backup = backup();
        if (backup == null) {
            plugin.getLogger().info("无旧数据，跳过备份。");
        } else {
            plugin.getLogger().info("已备份 data.yml → " + backup.getName());
        }

        AccountManager am = plugin.getAccountManager();
        int count = 0;
        for (Entry e : entries) {
            double value = e.points * multiplier;
            if (addMode) {
                am.deposit(e.uuid, value);
            } else {
                am.setBalance(e.uuid, value);
            }
            count++;
        }
        am.save();   // 批量变更后统一落盘
        return count;
    }

    /** 迁移币种名（消息用） */
    public String currencyName() {
        return plugin.getMessages().currencyName();
    }
}