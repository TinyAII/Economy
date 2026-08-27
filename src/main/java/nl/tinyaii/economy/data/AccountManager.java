package nl.tinyaii.economy.data;

import nl.tinyaii.economy.EconomyPlugin;
import nl.tinyaii.economy.api.MoneyChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账户管理：内存 Map + YAML 持久化。所有余额变动唯一入口，synchronized 防双花。
 */
public class AccountManager {

    public static class Account {
        public final UUID uuid;
        public String name;        // 最后一次进服的名字（离线查询用）
        private double balance;    // 金币（主币）
        private int points;        // 点券（第二币，整数）

        Account(UUID uuid, String name, double balance, int points) {
            this.uuid = uuid;
            this.name = name;
            this.balance = balance;
            this.points = points;
        }

        public double getBalance() { return balance; }
        public int getPoints() { return points; }
    }

    private final EconomyPlugin plugin;
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private File file;
    /** 全局锁：所有余额写操作串行化 */
    private final Object lock = new Object();

    public AccountManager(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- 持久化 ----------

    public void load() {
        accounts.clear();
        file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("accounts");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                double bal = s.contains("balance") ? s.getDouble("balance") : s.getDouble("gold", 0);
                int pts = s.getInt("points", 0);
                Account acc = new Account(uuid, s.getString("name", ""), bal, pts);
                accounts.put(uuid, acc);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Account acc : accounts.values()) {
            String base = "accounts." + acc.uuid + ".";
            yml.set(base + "name", acc.name);
            yml.set(base + "gold", round(acc.getBalance()));
            yml.set(base + "points", acc.getPoints());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("保存 data.yml 失败: " + e.getMessage());
        }
    }

    // ---------- 开户 ----------

    /** 不存在则开户，返回账户（一定非空） */
    public Account getOrCreate(UUID uuid, String name) {
        synchronized (lock) {
            Account acc = accounts.get(uuid);
            if (acc != null) {
                if (name != null && !name.isEmpty()) acc.name = name;
                return acc;
            }
            double start = plugin.getConfig().getDouble("settings.starting-balance", 100.0);
            acc = new Account(uuid, name == null ? "" : name, start, 0);
            accounts.put(uuid, acc);
            save();
            return acc;
        }
    }

    /** 按名字查（在线或历史进服玩家） */
    public UUID findByName(String name) {
        // 先查在线
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        // 再查离线记录
        for (Account acc : accounts.values()) {
            if (acc.name != null && acc.name.equalsIgnoreCase(name)) return acc.uuid;
        }
        return null;
    }

    // ---------- 余额操作（全部走锁） ----------

    public double getBalance(UUID uuid) {
        Account acc = accounts.get(uuid);
        return acc == null ? 0 : round(acc.balance);
    }

    /** 存入，返回新余额 */
    public double deposit(UUID uuid, double amount) {
        synchronized (lock) {
            Account acc = getOrCreateNoSave(uuid);
            double before = acc.balance;
            acc.balance = capBalance(acc.balance + amount);
            fireChange(uuid, before, acc.balance);
            save();
            return round(acc.balance);
        }
    }

    /**
     * 取出。
     * @return true=成功；false=余额不足
     */
    public boolean withdraw(UUID uuid, double amount) {
        synchronized (lock) {
            Account acc = getOrCreateNoSave(uuid);
            if (round(acc.balance) < round(amount)) return false;
            double before = acc.balance;
            acc.balance -= amount;
            fireChange(uuid, before, acc.balance);
            save();
            return true;
        }
    }

    /** 设定余额（可为任意 >=0 值），返回实际设定值 */
    public double setBalance(UUID uuid, double amount) {
        synchronized (lock) {
            Account acc = getOrCreateNoSave(uuid);
            double before = acc.balance;
            acc.balance = Math.max(0, amount);
            fireChange(uuid, before, acc.balance);
            save();
            return round(acc.balance);
        }
    }

    // ---------- 点券（第二币） ----------

    public int getPoints(UUID uuid) {
        Account acc = accounts.get(uuid);
        return acc == null ? 0 : acc.points;
    }

    /** 发点券，返回新余额 */
    public int depositPoints(UUID uuid, int amount) {
        synchronized (lock) {
            Account acc = getOrCreateNoSave(uuid);
            acc.points = Math.max(0, acc.points + amount);
            save();
            return acc.points;
        }
    }

    /** 扣点券，返回 true=成功 */
    public boolean withdrawPoints(UUID uuid, int amount) {
        synchronized (lock) {
            Account acc = getOrCreateNoSave(uuid);
            if (acc.points < amount) return false;
            acc.points -= amount;
            save();
            return true;
        }
    }

    /** 设定点券余额 */
    public int setPoints(UUID uuid, int amount) {
        synchronized (lock) {
            Account acc = getOrCreateNoSave(uuid);
            acc.points = Math.max(0, amount);
            save();
            return acc.points;
        }
    }

    /**
     * 转账（原子：扣款+入账同一临界区；手续费可配）。
     * @return 成功=null；失败返回原因 key
     */
    public String transfer(UUID from, UUID to, double amount) {
        synchronized (lock) {
            double feeRate = plugin.getConfig().getDouble("settings.transfer-fee-rate", 0.0);
            double total = round(amount * (1 + feeRate));
            Account fromAcc = getOrCreateNoSave(from);
            if (round(fromAcc.balance) < total) return "transfer-insufficient";
            Account toAcc = getOrCreateNoSave(to);
            double fromBefore = fromAcc.balance;
            double toBefore = toAcc.balance;
            fromAcc.balance -= total;
            toAcc.balance = capBalance(toAcc.balance + amount);
            fireChange(from, fromBefore, fromAcc.balance);
            fireChange(to, toBefore, toAcc.balance);
            save();
            return null;
        }
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= round(amount);
    }

    // ---------- 排行 ----------

    public List<Account> topBalances(int limit) {
        List<Account> list = new ArrayList<>(accounts.values());
        list.sort(Comparator.comparingDouble(Account::getBalance).reversed());
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    /** 我的排名（余额比我多的人数+1） */
    public int rankOf(UUID uuid) {
        double mine = getBalance(uuid);
        int n = 1;
        for (Account acc : accounts.values()) {
            if (!acc.uuid.equals(uuid) && acc.balance > mine) n++;
        }
        return n;
    }

    public int size() { return accounts.size(); }

    public java.util.Set<Map.Entry<UUID, Account>> entrySet() { return accounts.entrySet(); }

    // ---------- 内部 ----------

    private Account getOrCreateNoSave(UUID uuid) {
        Account acc = accounts.get(uuid);
        if (acc == null) {
            double start = plugin.getConfig().getDouble("settings.starting-balance", 100.0);
            acc = new Account(uuid, "", start, 0);
            accounts.put(uuid, acc);
        }
        return acc;
    }

    private double capBalance(double value) {
        double max = plugin.getConfig().getDouble("settings.max-balance", -1);
        if (max > 0 && value > max) value = max;
        return Math.max(0, value);
    }

    private void fireChange(UUID uuid, double before, double after) {
        if (round(before) == round(after)) return;
        Runnable r = () -> Bukkit.getPluginManager().callEvent(new MoneyChangeEvent(uuid, before, after));
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r); // 异步调用时回主线程触发事件
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
