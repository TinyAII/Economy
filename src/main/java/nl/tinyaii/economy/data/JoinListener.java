package nl.tinyaii.economy.data;

import nl.tinyaii.economy.EconomyPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 玩家进服自动开户 + 刷新名字记录（供离线按名查询）。
 */
public class JoinListener implements Listener {

    private final EconomyPlugin plugin;

    public JoinListener(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getAccountManager().getOrCreate(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }
}
