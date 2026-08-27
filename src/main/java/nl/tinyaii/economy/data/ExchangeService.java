package nl.tinyaii.economy.data;

import nl.tinyaii.economy.EconomyPlugin;
import nl.tinyaii.economy.util.Messages;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 兑换服务：金币 ↔ 点券（配置驱动，默认关闭）。
 * 支持 单向/双向/关闭 三种模式，比率与每日上限可配。
 */
public class ExchangeService {

    private final EconomyPlugin plugin;
    /** 每日兑换记录：uuid(+方向+日期) → 已兑换点券数 */
    private final Map<String, Integer> daily = new ConcurrentHashMap<>();
    private String today;

    public ExchangeService(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.today = java.time.LocalDate.now().toString();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("currency.exchange.enabled", false);
    }

    /** 配置的模式：both/one-way/points-only/off（off 不走到这里） */
    private String mode() {
        return plugin.getConfig().getString("currency.exchange.mode", "both");
    }

    /** 金币 -> 点券 是否允许 */
    public boolean goldToPointsAllowed() {
        if (!isEnabled()) return false;
        String m = mode();
        return m.equals("both") || m.equals("one-way") || m.equals("gold-to-points");
    }

    /** 点券 -> 金币 是否允许 */
    public boolean pointsToGoldAllowed() {
        if (!isEnabled()) return false;
        String m = mode();
        return m.equals("both") || m.equals("points-to-gold");
    }

    /**
     * 金币换点券。
     * @return 成功=换到的点券数；失败=null
     */
    public Integer exchangeGoldToPoints(UUID uuid, double goldAmount) {
        if (!goldToPointsAllowed()) return null;
        AccountManager am = plugin.getAccountManager();
        double rate = plugin.getConfig().getDouble("currency.exchange.rate-gold-to-points", 100.0);
        int limit = plugin.getConfig().getInt("currency.exchange.daily-limit-points", 0);
        int todayUsed = usedToday(uuid);

        if (limit > 0 && todayUsed >= limit) return null;   // 达上限

        int points = (int) Math.floor(goldAmount / rate);
        if (points <= 0) return null;
        if (limit > 0) points = Math.min(points, limit - todayUsed);

        if (!am.withdraw(uuid, goldAmount)) return null;
        am.depositPoints(uuid, points);
        record(uuid, points);
        return points;
    }

    /**
     * 点券换金币。
     * @return 成功=换到的金币；失败=null
     */
    public Double exchangePointsToGold(UUID uuid, int pointsAmount) {
        if (!pointsToGoldAllowed()) return null;
        AccountManager am = plugin.getAccountManager();
        double rate = plugin.getConfig().getDouble("currency.exchange.rate-points-to-gold", 90.0);
        int limit = plugin.getConfig().getInt("currency.exchange.daily-limit-points", 0);
        int todayUsed = usedToday(uuid);

        if (limit > 0 && todayUsed >= limit) return null;

        Object lock = am;
        // 直接调 API 原子操作：先扣点券
        if (!am.withdrawPoints(uuid, pointsAmount)) return null;
        double gold = pointsAmount * rate;
        am.deposit(uuid, gold);
        record(uuid, pointsAmount);
        return gold;
    }

    private int usedToday(UUID uuid) {
        return daily.getOrDefault(uuid + "|" + today, 0);
    }

    private void record(UUID uuid, int points) {
        daily.put(uuid + "|" + today, usedToday(uuid) + points);
    }
}
