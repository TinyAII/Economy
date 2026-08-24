package nl.tinyaii.economy.command;

import nl.tinyaii.economy.EconomyPlugin;
import nl.tinyaii.economy.data.AccountManager;
import nl.tinyaii.economy.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MoneyCommand implements CommandExecutor, TabCompleter {
    private final EconomyPlugin plugin;

    public MoneyCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();
        AccountManager am = plugin.getAccountManager();

        // /钱包 → 查自己
        if (args.length == 0) {
            if (!(sender instanceof Player)) { sender.sendMessage("控制台请用: /钱包 查看 <玩家>"); return true; }
            if (!sender.hasPermission("economy.use")) { msg.send((Player) sender, "no-permission"); return true; }
            Player p = (Player) sender;
            double bal = am.getBalance(p.getUniqueId());
            int rank = am.rankOf(p.getUniqueId());
            p.sendMessage(msg.raw("balance", "{amount}", Messages.fmt(bal), "{currency}", msg.currencyName())
                    + Messages.color(" &7(第" + rank + "名)"));
            return true;
        }

        switch (args[0]) {
            case "查看": {
                if (!checkUse(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(Messages.color("&c用法: /钱包 查看 <玩家>"));
                    return true;
                }
                UUID target = resolveTarget(args[1]);
                if (target == null) { sendNotFound(sender, args[1]); return true; }
                String name = nameOf(target, args[1]);
                double bal = am.getBalance(target);
                sender.sendMessage(msg.raw("balance-other", "{player}", name,
                        "{amount}", Messages.fmt(bal), "{currency}", msg.currencyName()));
                return true;
            }
            case "排行": {
                if (!checkUse(sender)) return true;
                int page = 1;
                if (args.length >= 2) { try { page = Math.max(1, Integer.parseInt(args[1])); } catch (Exception ignored) {} }
                int pageSize = plugin.getConfig().getInt("settings.top-page-size", 10);
                List<AccountManager.Account> top = am.topBalances(page * pageSize);
                int from = (page - 1) * pageSize;
                if (from >= top.size()) {
                    sender.sendMessage(Messages.color("&7本页没有数据（共 " + top.size() + " 人）。"));
                    return true;
                }
                sender.sendMessage(Messages.color("&6==== 富豪榜 第" + page + "页 ===="));
                for (int i = from; i < Math.min(top.size(), from + pageSize); i++) {
                    AccountManager.Account acc = top.get(i);
                    sender.sendMessage(Messages.color(
                            "&7" + (i + 1) + ". &e" + (acc.name.isEmpty() ? acc.uuid.toString().substring(0, 8) : acc.name)
                                    + " &f- &a" + Messages.fmt(acc.getBalance()) + " " + msg.currencyName()));
                }
                return true;
            }
            case "转账": {
                if (!checkUse(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
                Player p = (Player) sender;
                if (args.length < 3) {
                    p.sendMessage(Messages.color("&c用法: /钱包 转账 <玩家> <金额>"));
                    return true;
                }
                UUID to = resolveTarget(args[1]);
                if (to == null) { sendNotFound(p, args[1]); return true; }
                if (to.equals(p.getUniqueId())) { msg.send(p, "transfer-self"); return true; }
                Double amount = parseAmount(args[2], p);
                if (amount == null) { msg.send(p, "invalid-amount"); return true; }
                String err = am.transfer(p.getUniqueId(), to, amount);
                if (err != null) { msg.send(p, err, "{amount}", Messages.fmt(am.getBalance(p.getUniqueId()))); return true; }
                double feeRate = plugin.getConfig().getDouble("settings.transfer-fee-rate", 0.0);
                String got = Messages.fmt(amount - amount * feeRate);
                msg.send(p, "transfer-success", "{player}", nameOf(to, args[1]), "{amount}", Messages.fmt(amount));
                Player tp = Bukkit.getPlayer(to);
                if (tp != null) msg.send(tp, "transfer-received", "{player}", p.getName(), "{amount}", got);
                return true;
            }

            // ---- 管理 ----
            case "给": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /钱包 给 <玩家> <金额>")); return true; }
                UUID t = resolveTarget(args[1]);
                if (t == null) { sendNotFound(sender, args[1]); return true; }
                Double a = parseAmount(args[2], null);
                if (a == null) { if (sender instanceof Player) msg.send((Player) sender, "invalid-amount"); else sender.sendMessage(msg.raw("invalid-amount")); return true; }
                am.deposit(t, a);
                sender.sendMessage(msg.raw("admin-give", "{player}", nameOf(t, args[1]), "{amount}", Messages.fmt(a), "{currency}", msg.currencyName()));
                Player tp = Bukkit.getPlayer(t);
                if (tp != null) tp.sendMessage(msg.raw("transfer-received", "{player}", "系统", "{amount}", Messages.fmt(a)));
                return true;
            }
            case "扣": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /钱包 扣 <玩家> <金额>")); return true; }
                UUID t = resolveTarget(args[1]);
                if (t == null) { sendNotFound(sender, args[1]); return true; }
                Double a = parseAmount(args[2], null);
                if (a == null) { if (sender instanceof Player) msg.send((Player) sender, "invalid-amount"); else sender.sendMessage(msg.raw("invalid-amount")); return true; }
                boolean ok = am.withdraw(t, a);
                if (ok) {
                    sender.sendMessage(msg.raw("admin-take", "{player}", nameOf(t, args[1]), "{amount}", Messages.fmt(a)));
                } else {
                    sender.sendMessage(Messages.color("&c该玩家余额不足当前仅有 &e" + Messages.fmt(am.getBalance(t))));
                }
                return true;
            }
            case "设定": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /钱包 设定 <玩家> <金额>")); return true; }
                UUID t = resolveTarget(args[1]);
                if (t == null) { sendNotFound(sender, args[1]); return true; }
                Double a = parseAmountAllowZero(args[2], null);
                if (a == null || a < 0) { if (sender instanceof Player) msg.send((Player) sender, "invalid-amount"); else sender.sendMessage(msg.raw("invalid-amount")); return true; }
                am.setBalance(t, a);
                sender.sendMessage(msg.raw("admin-set", "{player}", nameOf(t, args[1]), "{amount}", Messages.fmt(a), "{currency}", msg.currencyName()));
                return true;
            }
            case "重载": {
                if (!checkAdmin(sender)) return true;
                plugin.reloadAll();
                if (sender instanceof Player) msg.send((Player) sender, "reloaded");
                else sender.sendMessage(msg.raw("reloaded"));
                return true;
            }
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender s) {
        String[] lines = {
                "&6===== Economy 经济系统 =====",
                "&e/钱包 &7- 查自己的余额",
                "&e/钱包 查看 <玩家> &7- 查他人",
                "&e/钱包 排行 [页] &7- 富豪榜",
                "&e/钱包 转账 <玩家> <金额>",
                "&c--- 管理 ---",
                "&e/钱包 给 <玩家> <金额>",
                "&e/钱包 扣 <玩家> <金额>",
                "&e/钱包 设定 <玩家> <金额>",
                "&e/钱包 重载"
        };
        for (String l : lines) s.sendMessage(Messages.color(l));
    }

    private boolean checkUse(CommandSender s) {
        if (s.hasPermission("economy.use")) return true;
        if (s instanceof Player) plugin.getMessages().send((Player) s, "no-permission");
        else s.sendMessage(plugin.getMessages().raw("no-permission"));
        return false;
    }

    private boolean checkAdmin(CommandSender s) {
        if (s.hasPermission("economy.admin")) return true;
        if (s instanceof Player) plugin.getMessages().send((Player) s, "no-permission");
        else s.sendMessage(plugin.getMessages().raw("no-permission"));
        return false;
    }

    private UUID resolveTarget(String name) {
        return plugin.getAccountManager().findByName(name);
    }

    private String nameOf(UUID uuid, String fallback) {
        AccountManager.Account acc = plugin.getAccountManager().entrySet().stream()
                .filter(e -> e.getKey().equals(uuid)).map(java.util.Map.Entry::getValue).findFirst().orElse(null);
        return acc == null ? fallback : (acc.name.isEmpty() ? fallback : acc.name);
    }

    private void sendNotFound(CommandSender s, String name) {
        if (s instanceof Player) plugin.getMessages().send((Player) s, "player-not-found", "{player}", name);
        else s.sendMessage(plugin.getMessages().raw("player-not-found", "{player}", name));
    }

    private Double parseAmount(String s, Player errTo) {
        try {
            double v = Double.parseDouble(s);
            if (v <= 0) return null;
            return Math.round(v * 100.0) / 100.0;
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseAmountAllowZero(String s, Player errTo) {
        try {
            double v = Double.parseDouble(s);
            return Math.round(v * 100.0) / 100.0;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Tab 补全 ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("查看", "排行", "转账"));
            if (sender.hasPermission("economy.admin")) subs.addAll(Arrays.asList("给", "扣", "设定", "重载"));
            for (String s : subs) if (s.startsWith(args[0])) out.add(s);
        } else if (args.length == 2 && !args[0].equals("排行")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
