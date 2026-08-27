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

import java.io.File;
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
            int pts = am.getPoints(p.getUniqueId());
            int rank = am.rankOf(p.getUniqueId());
            p.sendMessage(msg.raw("balance", "{amount}", Messages.fmt(bal), "{currency}", msg.currencyName()
                    + Messages.color(" &8| &e点券 &f" + pts)
                    + Messages.color(" &7(第" + rank + "名)")));
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
            case "点券": {
                if (!checkUse(sender)) return true;
                Player tp = null; String name;
                if (args.length >= 2) {
                    tp = org.bukkit.Bukkit.getPlayerExact(args[1]);
                    if (tp == null) { sendNotFound(sender, args[1]); return true; }
                    name = args[1];
                } else if (sender instanceof Player) {
                    tp = (Player) sender; name = ((Player) sender).getName();
                } else { sender.sendMessage("控制台请用: /钱包 点券 <玩家>"); return true; }
                int pts = am.getPoints(tp.getUniqueId());
                String bn = plugin.getConfig().getString("currency.points-name", "点券");
                sender.sendMessage(Messages.color("&e" + name + " &a的 &e" + bn + "&a：&f" + pts));
                return true;
            }
            case "兑换": {
                if (!checkUse(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
                Player p2 = (Player) sender;
                nl.tinyaii.economy.data.ExchangeService ex = plugin.getExchangeService();
                if (!ex.isEnabled()) {
                    sender.sendMessage(Messages.color("&c兑换功能未开启（管理员可在 config 配置 currency.exchange）。"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Messages.color("&c用法: /钱包 兑换 购买点券 <金币数量>  或  /钱包 兑换 出售点券 <点券数量>"));
                    return true;
                }
                if (args[1].equals("购买点券")) {
                    if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /钱包 兑换 购买点券 <金币数量>")); return true; }
                    double gold = parseMoney(args[2]);
                    if (gold <= 0 || !ex.goldToPointsAllowed()) { sender.sendMessage(Messages.color("&c该方向不可兑换或金额无效。")); return true; }
                    Integer got = ex.exchangeGoldToPoints(p2.getUniqueId(), gold);
                    if (got == null) { sender.sendMessage(Messages.color("&c兑换失败（余额不足/超每日上限）。")); return true; }
                    String bn = plugin.getConfig().getString("currency.points-name", "点券");
                    sender.sendMessage(Messages.color("&a成功用 &e" + gold + " &a金币兑换 &e" + got + " " + bn + "！"));
                    return true;
                }
                if (args[1].equals("出售点券")) {
                    if (args.length < 3) { sender.sendMessage(Messages.color("&c用法: /钱包 兑换 出售点券 <点券数量>")); return true; }
                    int pts2 = 0;
                    try { pts2 = Integer.parseInt(args[2]); } catch (Exception exx) { pts2 = 0; }
                    if (pts2 <= 0 || !ex.pointsToGoldAllowed()) { sender.sendMessage(Messages.color("&c该方向不可兑换或数量无效。")); return true; }
                    Double got = ex.exchangePointsToGold(p2.getUniqueId(), pts2);
                    if (got == null) { sender.sendMessage(Messages.color("&c兑换失败（点券不足/超每日上限）。")); return true; }
                    sender.sendMessage(Messages.color("&a成功用 &e" + pts2 + " &a点券兑换 &e" + Messages.fmt(got) + " &a金币！"));
                    return true;
                }
                sender.sendMessage(Messages.color("&c用法: /钱包 兑换 购买点券 <金币> | 出售点券 <点券>"));
                return true;
            }
            case "管理": {
                if (!checkAdmin(sender)) return true;
                return doAdminManage(sender, args);
            }
            case "迁移points": {
                if (!checkAdmin(sender)) return true;
                return doImport(sender, args);
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

    /** /钱包 迁移points [确认] —— 从 PlayerPoints 官方导出文件导入余额 */
    /** 神权管理命令（仅 economy.admin） */
    private boolean doAdminManage(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Messages.color("&c用法: /钱包 管理 <查看|给金币|扣金币|给点券|扣点券|清空|全服金币|全服点券|重置全服>")); return true;
        }
        String sub = args[1];
        switch (sub) {
            case "查看": {
                if (args.length < 3) { s.sendMessage(Messages.color("&c用法: /钱包 管理 查看 <玩家>")); return true; }
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
                plugin.getMessages();
                s.sendMessage(Messages.color("&6==== " + args[2] + " 钱包 ===="));
                s.sendMessage(Messages.color("  &a金币: &e" + Messages.fmt(plugin.getAccountManager().getBalance(op.getUniqueId()))));
                s.sendMessage(Messages.color("  &e点券: &f" + plugin.getAccountManager().getPoints(op.getUniqueId())));
                return true;
            }
            case "给金币": {
                if (args.length < 4) { s.sendMessage(Messages.color("&c用法: /钱包 管理 给金币 <玩家> <数量>")); return true; }
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
                double v = parseMoney(args[3]);
                if (v <= 0) { s.sendMessage(Messages.color("&c金额无效。")); return true; }
                plugin.getAccountManager().deposit(op.getUniqueId(), v);
                s.sendMessage(Messages.color("&a已给 &e" + args[2] + " &a发放 &e" + v + " &a金币。"));
                return true;
            }
            case "扣金币": {
                if (args.length < 4) { s.sendMessage(Messages.color("&c用法: /钱包 管理 扣金币 <玩家> <数量>")); return true; }
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
                double v = parseMoney(args[3]);
                if (v <= 0) { s.sendMessage(Messages.color("&c金额无效。")); return true; }
                boolean ok = plugin.getAccountManager().withdraw(op.getUniqueId(), v);
                if (ok) s.sendMessage(Messages.color("&a已从 &e" + args[2] + " &a扣除 &e" + v + " &a金币。"));
                else s.sendMessage(Messages.color("&c扣除失败（余额不足）。"));
                return true;
            }
            case "给点券": {
                if (args.length < 4) { s.sendMessage(Messages.color("&c用法: /钱包 管理 给点券 <玩家> <数量>")); return true; }
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
                int v = 0;
                try { v = Integer.parseInt(args[3]); } catch (Exception ex) { }
                if (v <= 0) { s.sendMessage(Messages.color("&c数量无效。")); return true; }
                plugin.getAccountManager().depositPoints(op.getUniqueId(), v);
                String bn = plugin.getConfig().getString("currency.points-name", "点券");
                s.sendMessage(Messages.color("&a已给 &e" + args[2] + " &a发放 &e" + v + " " + bn + "。"));
                return true;
            }
            case "扣点券": {
                if (args.length < 4) { s.sendMessage(Messages.color("&c用法: /钱包 管理 扣点券 <玩家> <数量>")); return true; }
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
                int v = 0;
                try { v = Integer.parseInt(args[3]); } catch (Exception ex) { }
                if (v <= 0) { s.sendMessage(Messages.color("&c数量无效。")); return true; }
                boolean ok = plugin.getAccountManager().withdrawPoints(op.getUniqueId(), v);
                String bn = plugin.getConfig().getString("currency.points-name", "点券");
                if (ok) s.sendMessage(Messages.color("&a已从 &e" + args[2] + " &a扣除 &e" + v + " " + bn + "。"));
                else s.sendMessage(Messages.color("&c扣除失败（点券不足）。"));
                return true;
            }
            case "清空": {
                if (args.length < 3) { s.sendMessage(Messages.color("&c用法: /钱包 管理 清空 <玩家>")); return true; }
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(args[2]);
                plugin.getAccountManager().setBalance(op.getUniqueId(), 0);
                plugin.getAccountManager().setPoints(op.getUniqueId(), 0);
                s.sendMessage(Messages.color("&c已清空 &e" + args[2] + " &c的钱包。"));
                return true;
            }
            case "全服金币": {
                if (args.length < 3) { s.sendMessage(Messages.color("&c用法: /钱包 管理 全服金币 <数量>")); return true; }
                double v = parseMoney(args[2]);
                if (v <= 0) { s.sendMessage(Messages.color("&c金额无效。")); return true; }
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) plugin.getAccountManager().deposit(p.getUniqueId(), v);
                s.sendMessage(Messages.color("&a已给全服在线玩家发放 &e" + v + " &a金币。"));
                return true;
            }
            case "全服点券": {
                if (args.length < 3) { s.sendMessage(Messages.color("&c用法: /钱包 管理 全服点券 <数量>")); return true; }
                int v = 0;
                try { v = Integer.parseInt(args[2]); } catch (Exception ex) { }
                if (v <= 0) { s.sendMessage(Messages.color("&c数量无效。")); return true; }
                String bn = plugin.getConfig().getString("currency.points-name", "点券");
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) plugin.getAccountManager().depositPoints(p.getUniqueId(), v);
                s.sendMessage(Messages.color("&a已给全服在线玩家发放 &e" + v + " " + bn + "。"));
                return true;
            }
            case "重置全服": {
                if (args.length >= 3 && args[2].equals("确认")) {
                    for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                        plugin.getAccountManager().setBalance(p.getUniqueId(), 0);
                        plugin.getAccountManager().setPoints(p.getUniqueId(), 0);
                    }
                    s.sendMessage(Messages.color("&c已重置全服钱包（新赛季！）"));
                } else {
                    s.sendMessage(Messages.color("&c危险操作！确认请输 &e/钱包 管理 重置全服 确认"));
                }
                return true;
            }
            default:
                s.sendMessage(Messages.color("&c未知管理命令。"));
                return true;
        }
    }

    private double parseMoney(String s) {
        try { return Math.round(Double.parseDouble(s) * 100.0) / 100.0; }
        catch (Exception e) { return -1; }
    }

    private boolean doImport(CommandSender s, String[] args) {
        Messages msg = plugin.getMessages();
        nl.tinyaii.economy.migration.PlayerPointsImporter imp = new nl.tinyaii.economy.migration.PlayerPointsImporter(plugin);

        File f = imp.findStorageFile();
        if (f == null) {
            s.sendMessage(Messages.color("&c未找到 PlayerPoints 导出文件。请先在旧服执行 &e/points export&c，或将 storage.yml 放到 plugins/Economy/playerpoints-storage.yml"));
            return true;
        }

        List<nl.tinyaii.economy.migration.PlayerPointsImporter.Entry> entries = imp.parse(f);
        if (entries.isEmpty()) {
            s.sendMessage(Messages.color("&c导出文件里没有可导入的点数（Points 段为空或全部≤0）。"));
            return true;
        }

        double multiplier = plugin.getConfig().getDouble("migration.point-multiplier", 1.0);
        boolean addMode = plugin.getConfig().getString("migration.mode", "replace").equalsIgnoreCase("add");
        double total = 0;
        for (var e : entries) total += e.points * multiplier;

        if (args.length < 2 || !args[1].equals("确认")) {
            s.sendMessage(Messages.color("&6==== PlayerPoints 迁移预览 ===="));
            s.sendMessage(Messages.color("&7源文件: &f" + f.getPath()));
            s.sendMessage(Messages.color("&7将导入 &e" + entries.size() + " &7名玩家，合计 &e" + Messages.fmt(total) + " &7" + msg.currencyName()));
            s.sendMessage(Messages.color("&7模式: &f" + (addMode ? "累加（余额+点数）" : "覆盖（点数直接设为余额）")
                    + " &7| 换算: &f1点 = " + Messages.fmt(multiplier) + " " + msg.currencyName()));
            s.sendMessage(Messages.color("&e输入 &2/钱包 迁移points 确认 &e开始导入（会自动备份）"));
            return true;
        }

        int count = imp.importEntries(entries, addMode, multiplier);
        if (count < 0) {
            s.sendMessage(Messages.color("&c导入失败，请检查控制台日志。"));
            return true;
        }
        s.sendMessage(Messages.color("&a迁移完成！成功导入 &e" + count + " &a个账户，合计 &e" + Messages.fmt(total)
                + " &a" + msg.currencyName() + "。"));
        return true;
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
                "&e/钱包 迁移points [确认] &7- 从 PlayerPoints 导入",
                "&e/钱包 点券 [玩家] &7- 查点券",
                "&e/钱包 兑换 购买点券 <金币> | 出售点券 <点券> &7- 兑换（默认关）",
                "&c--- 管理（神权）---",
                "&e/钱包 管理 查看 <玩家> / 给金币 / 扣金币 / 给点券 / 扣点券 / 清空",
                "&e/钱包 管理 全服金币 <数量> / 全服点券 <数量> / 重置全服 确认",
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
