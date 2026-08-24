package nl.tinyaii.economy.vault;

import net.milkbowl.vault.economy.Economy;
import nl.tinyaii.economy.EconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

/**
 * Vault 对接：把我们的经济实现注册为 Vault Economy 提供方。
 * 本类仅在检测到 Vault 时才被加载（EconomyPlugin 里已做隔离）。
 */
public class VaultHook {

    private final EconomyPlugin plugin;

    public VaultHook(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (Bukkit.getServicesManager().getRegistration(Economy.class) != null) {
            // 已有其他经济插件注册——不抢占，保持和平
            plugin.getLogger().warning("检测到已有 Vault 经济提供方，未覆盖。");
            return;
        }
        Bukkit.getServicesManager().register(Economy.class, new EconImpl(), plugin, ServicePriority.Normal);
    }

    /**
     * Vault Economy 接口实现：全部转发到 AccountManager 单入口。
     */
    private class EconImpl implements Economy {
        private final nl.tinyaii.economy.data.AccountManager am = plugin.getAccountManager();
        private final nl.tinyaii.economy.util.Messages msg = plugin.getMessages();

        @Override
        public boolean isEnabled() { return plugin.isEnabled(); }

        @Override
        public String getName() { return "Economy (TinyAII)"; }

        @Override
        public boolean hasBankSupport() { return false; }

        @Override
        public int fractionalDigits() { return 2; }

        @Override
        public String format(double amount) {
            return nl.tinyaii.economy.util.Messages.fmt(amount) + " " + msg.currencyName();
        }

        @Override
        public String currencyNamePlural() { return msg.currencyName(); }

        @Override
        public String currencyNameSingular() { return msg.currencyName(); }

        @Deprecated
        @Override
        public boolean hasAccount(String playerName) {
            return findUuid(playerName) != null;
        }

        @Override
        public boolean hasAccount(OfflinePlayer player) {
            return findUuid(player.getName()) != null || player.isOnline();
        }

        @Override
        public boolean hasAccount(String worldName, String playerName) { return hasAccount(playerName); }

        @Override
        public boolean hasAccount(org.bukkit.OfflinePlayer player, String worldName) { return hasAccount(player); }

        @Deprecated
        @Override
        public double getBalance(String playerName) {
            java.util.UUID u = findUuid(playerName);
            return u == null ? 0 : am.getBalance(u);
        }

        @Override
        public double getBalance(OfflinePlayer player) {
            return am.getBalance(player.getUniqueId());
        }

        @Override
        public double getBalance(String worldName, String playerName) { return getBalance(playerName); }

        @Override
        public double getBalance(org.bukkit.OfflinePlayer player, String worldName) { return getBalance(player); }

        @Deprecated
        @Override
        public boolean has(String playerName, double amount) {
            java.util.UUID u = findUuid(playerName);
            return u != null && am.has(u, amount);
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return am.has(player.getUniqueId(), amount);
        }

        @Override
        public boolean has(String worldName, String playerName, double amount) { return has(playerName, amount); }

        @Override
        public boolean has(org.bukkit.OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse withdrawPlayer(String playerName, double amount) {
            java.util.UUID u = findUuid(playerName);
            if (u == null) return fail();
            return withdrawByUuid(u, amount);
        }

        @Override
        public net.milkbowl.vault.economy.EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            return withdrawByUuid(player.getUniqueId(), amount);
        }

        private net.milkbowl.vault.economy.EconomyResponse withdrawByUuid(java.util.UUID u, double amount) {
            if (amount < 0) return new net.milkbowl.vault.economy.EconomyResponse(0, am.getBalance(u), net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE, "金额不能为负");
            boolean ok = am.withdraw(u, amount);
            return ok
                    ? new net.milkbowl.vault.economy.EconomyResponse(amount, am.getBalance(u), net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, "")
                    : new net.milkbowl.vault.economy.EconomyResponse(0, am.getBalance(u), net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE, "余额不足");
        }

        @Override
        public net.milkbowl.vault.economy.EconomyResponse withdrawPlayer(String worldName, String playerName, double amount) { return withdrawPlayer(playerName, amount); }

        @Override
        public net.milkbowl.vault.economy.EconomyResponse withdrawPlayer(org.bukkit.OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }

        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse depositPlayer(String playerName, double amount) {
            java.util.UUID u = ensureUuid(playerName);
            return depositByUuid(u, amount);
        }

        @Override
        public net.milkbowl.vault.economy.EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            return depositByUuid(player.getUniqueId(), amount);
        }

        private net.milkbowl.vault.economy.EconomyResponse depositByUuid(java.util.UUID u, double amount) {
            if (amount < 0) return new net.milkbowl.vault.economy.EconomyResponse(0, am.getBalance(u), net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE, "金额不能为负");
            double after = am.deposit(u, amount);
            return new net.milkbowl.vault.economy.EconomyResponse(amount, after, net.milkbowl.vault.economy.EconomyResponse.ResponseType.SUCCESS, "");
        }

        @Override
        public net.milkbowl.vault.economy.EconomyResponse depositPlayer(String worldName, String playerName, double amount) { return depositPlayer(playerName, amount); }

        @Override
        public net.milkbowl.vault.economy.EconomyResponse depositPlayer(org.bukkit.OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }

        // ---- 不支持的银行 API ----

        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse createBank(String name, String player) { return bankUnsupported(); }
        @Override
        public net.milkbowl.vault.economy.EconomyResponse createBank(String name, OfflinePlayer player) { return bankUnsupported(); }
        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse deleteBank(String name) { return bankUnsupported(); }
        @Override
        public net.milkbowl.vault.economy.EconomyResponse bankBalance(String name) { return bankUnsupported(); }
        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse bankHas(String name, double amount) { return bankUnsupported(); }
        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse bankWithdraw(String name, double amount) { return bankUnsupported(); }
        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse bankDeposit(String name, double amount) { return bankUnsupported(); }
        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse isBankOwner(String name, String playerName) { return bankUnsupported(); }
        @Override
        public net.milkbowl.vault.economy.EconomyResponse isBankOwner(String name, OfflinePlayer player) { return bankUnsupported(); }
        @Deprecated
        @Override
        public net.milkbowl.vault.economy.EconomyResponse isBankMember(String name, String playerName) { return bankUnsupported(); }
        @Override
        public net.milkbowl.vault.economy.EconomyResponse isBankMember(String name, OfflinePlayer player) { return bankUnsupported(); }
        @Override
        public java.util.List<String> getBanks() { return java.util.Collections.emptyList(); }

        // ---- 玩家账户创建 ----

        @Deprecated
        @Override
        public boolean createPlayerAccount(String playerName) {
            java.util.UUID u = findUuid(playerName);
            if (u == null) return false;
            am.getOrCreate(u, playerName);
            return true;
        }

        @Override
        public boolean createPlayerAccount(OfflinePlayer player) {
            am.getOrCreate(player.getUniqueId(), player.getName());
            return true;
        }

        @Override
        public boolean createPlayerAccount(String worldName, String playerName) { return createPlayerAccount(playerName); }

        @Override
        public boolean createPlayerAccount(org.bukkit.OfflinePlayer player, String worldName) { return createPlayerAccount(player); }

        // ---- 工具 ----

        private net.milkbowl.vault.economy.EconomyResponse fail() {
            return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.FAILURE, "玩家不存在");
        }

        private net.milkbowl.vault.economy.EconomyResponse bankUnsupported() {
            return new net.milkbowl.vault.economy.EconomyResponse(0, 0, net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED, "不支持银行");
        }

        private java.util.UUID findUuid(String name) {
            OfflinePlayer op = Bukkit.getPlayerExact(name);
            if (op != null) return op.getUniqueId();
            return am.findByName(name);
        }

        private java.util.UUID ensureUuid(String name) {
            OfflinePlayer op = Bukkit.getPlayerExact(name);
            if (op != null) {
                am.getOrCreate(op.getUniqueId(), name);
                return op.getUniqueId();
            }
            // 尝试 Bukkit 离线档案（可能触发阻塞，仅管理场景用）
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            am.getOrCreate(offline.getUniqueId(), name);
            return offline.getUniqueId();
        }
    }
}
