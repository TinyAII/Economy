package nl.tinyaii.economy.api;

import nl.tinyaii.economy.EconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Economy 公开 API（静态入口，供其他插件调用）。
 *
 * 用法：EconomyAPI.getBalance(uuid)
 */
public final class EconomyAPI {

    private EconomyAPI() {}

    private static EconomyPlugin plugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("Economy");
        if (!(p instanceof EconomyPlugin) || !p.isEnabled()) {
            throw new IllegalStateException("Economy 插件未启用");
        }
        return (EconomyPlugin) p;
    }

    /** 查余额（未开户玩家返回 0） */
    public static double getBalance(UUID uuid) {
        return plugin().getAccountManager().getBalance(uuid);
    }

    /** 是否够付 */
    public static boolean has(UUID uuid, double amount) {
        return plugin().getAccountManager().has(uuid, amount);
    }

    /** 存入（自动开户），返回新余额 */
    public static double deposit(UUID uuid, double amount) {
        if (amount <= 0) return getBalance(uuid);
        return plugin().getAccountManager().deposit(uuid, amount);
    }

    /**
     * 取出（不足不扣）。
     * @return true=成功；false=余额不足
     */
    public static boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0) return true;
        return plugin().getAccountManager().withdraw(uuid, amount);
    }

    /** 设定余额（>=0），返回实际设定值 */
    public static double setBalance(UUID uuid, double amount) {
        return plugin().getAccountManager().setBalance(uuid, Math.max(0, amount));
    }

    // ---------- 点券（第二币） ----------

    /** 查点券余额 */
    public static int getPoints(UUID uuid) {
        return plugin().getAccountManager().getPoints(uuid);
    }

    /** 发点券，返回新余额 */
    public static int depositPoints(UUID uuid, int amount) {
        if (amount <= 0) return getPoints(uuid);
        return plugin().getAccountManager().depositPoints(uuid, amount);
    }

    /** 扣点券，返回 true=成功（不足 false） */
    public static boolean withdrawPoints(UUID uuid, int amount) {
        if (amount <= 0) return true;
        return plugin().getAccountManager().withdrawPoints(uuid, amount);
    }

    /** 设定点券余额 */
    public static int setPoints(UUID uuid, int amount) {
        return plugin().getAccountManager().setPoints(uuid, Math.max(0, amount));
    }
}
