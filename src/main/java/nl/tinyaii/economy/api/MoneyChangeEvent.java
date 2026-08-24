package nl.tinyaii.economy.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 玩家余额变动事件：其他插件可监听记账（存入/取出/转账/设定都会触发）。
 */
public class MoneyChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final java.util.UUID uuid;
    private final double before;
    private final double after;

    public MoneyChangeEvent(java.util.UUID uuid, double before, double after) {
        this.uuid = uuid;
        this.before = before;
        this.after = after;
    }

    public java.util.UUID getUuid() { return uuid; }
    /** 变动前余额 */
    public double getBefore() { return before; }
    /** 变动后余额 */
    public double getAfter() { return after; }
    /** 净变动额（after - before） */
    public double getDelta() { return after - before; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
