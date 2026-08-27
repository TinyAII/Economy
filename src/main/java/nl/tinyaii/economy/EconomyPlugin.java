package nl.tinyaii.economy;

import nl.tinyaii.economy.command.MoneyCommand;
import nl.tinyaii.economy.data.AccountManager;
import nl.tinyaii.economy.util.Messages;
import nl.tinyaii.economy.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyPlugin extends JavaPlugin {

    private AccountManager accountManager;
    private Messages messages;
    private VaultHook vaultHook;
    private nl.tinyaii.economy.data.ExchangeService exchangeService;

    @Override
    public void onEnable() {
        // TinyAII 品牌横幅 —— 必须在所有初始化逻辑之前输出（与 AutoBackup 完全一致）
        getLogger().info(" _____ _                _    ___ ___");
        getLogger().info("|_   _(_)_ __  _   _   / \\  |_ _|_ _|");
        getLogger().info("  | | | | '_ \\| | | | / _ \\  | | | |");
        getLogger().info("  | | | | | | | |_| |/ ___ \\ | | | |");
        getLogger().info("  |_| |_|_| |_|\\__, /_/   \\_\\___|___|");
        getLogger().info("               |___/");
        getLogger().info("Economy 经济系统 v" + getDescription().getVersion() + " - TinyAII 出品");

        saveDefaultConfig();
        messages = new Messages(this);
        accountManager = new AccountManager(this);
        accountManager.load();
        exchangeService = new nl.tinyaii.economy.data.ExchangeService(this);

        // 玩家进服自动开户
        Bukkit.getPluginManager().registerEvents(new nl.tinyaii.economy.data.JoinListener(this), this);

        getCommand("钱包").setExecutor(new MoneyCommand(this));
        getCommand("钱包").setTabCompleter(new MoneyCommand(this));

        // Vault 软对接（装了才启用）
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                vaultHook = new VaultHook(this);
                vaultHook.register();
                getLogger().info("已检测到 Vault，经济提供方注册成功，第三方插件可无缝对接。");
            } catch (Throwable t) {
                getLogger().warning("Vault 对接失败（不影响本插件功能）: " + t.getMessage());
            }
        } else {
            getLogger().info("未检测到 Vault，跳过对接（不影响使用）。");
        }

        getLogger().info("经济系统已启用，共加载 " + accountManager.size() + " 个账户。指令: /钱包");
    }

    @Override
    public void onDisable() {
        if (accountManager != null) {
            accountManager.save();
            getLogger().info("账户数据已保存，插件已卸载。");
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
    }

    public AccountManager getAccountManager() { return accountManager; }
    public nl.tinyaii.economy.data.ExchangeService getExchangeService() { return exchangeService; }
    public Messages getMessages() { return messages; }
}
