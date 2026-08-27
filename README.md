# Economy 经济系统

轻量级经济内核插件：账户管理、转账、富豪排行、公开 EconomyAPI、Vault 软对接。零硬依赖，开箱即用，是其他插件的钱袋子地基。

![Version](https://img.shields.io/badge/version-1.0.0-blue) ![License](https://img.shields.io/badge/license-MIT-green) ![API](https://img.shields.io/badge/API-1.16%2B-orange)

## 功能特性

- **账户体系**：UUID 维度存储，进服自动开户，离线玩家同样支持管理操作
- **转账**：玩家间转账原子化处理（扣款+入账同一临界区，杜绝并发双花），手续费率可配（默认免费）
- **富豪榜**：聊天分页排行，每页条数可配，显示自己的排名
- **余额保护**：不可为负；上限可配（默认不封顶）；金额两位小数精度
- **EconomyAPI**：静态公开 API（金币 + 点券双通道），其他插件一行代码接入
- **双货币**：金币（主币）+ 点券（第二币，更贵更稀缺）；兑换默认关闭、点券默认无获取途径——由服主生态决定供给防通胀；兑换支持单向/双向/比率/每日上限全配置
- **管理员神权**：`/钱包 管理` 全套——给/扣金币、给/扣点券、清空、全服活动、新赛季重置，普通玩家用不了
- **MoneyChangeEvent**：余额变动事件（含变动前后值），供其他插件监听记账
- **Vault 软对接**：检测到 Vault 自动注册为经济提供方，领地/商店等第三方插件无缝识别；没装 Vault 照常运行
- **中文命令**：全套 `/钱包` 中文子命令 + Tab 补全

## 命令

| 命令 | 说明 | 权限 |
|---|---|---|
| `/钱包` | 查自己余额和排名 | economy.use |
| `/钱包 查看 <玩家>` | 查他人余额 | economy.use |
| `/钱包 排行 [页码]` | 富豪榜分页 | economy.use |
| `/钱包 转账 <玩家> <金额>` | 转账 | economy.use |
| `/钱包 给 <玩家> <金额>` | 发钱 | economy.admin |
| `/钱包 扣 <玩家> <金额>` | 扣钱 | economy.admin |
| `/钱包 设定 <玩家> <金额>` | 设定余额 | economy.admin |
| `/钱包 点券 [玩家]` | 查点券 | economy.use |
| `/钱包 兑换 购买点券 <金币> / 出售点券 <点券>` | 兑换（默认关闭） | economy.use |
| `/钱包 管理 给金币/扣金币/给点券/扣点券 <玩家> <数量>` | 神权发/扣双币 | economy.admin |
| `/钱包 管理 查看 <玩家>/清空 <玩家>` | 神权查看/洗号 | economy.admin |
| `/钱包 管理 全服金币 <数>/全服点券 <数>/重置全服 确认` | 全服活动/新赛季 | economy.admin |
| `/钱包 重载` | 重载配置 | economy.admin |

权限默认值：`economy.use` 所有人、`economy.admin` OP。

## 其他插件接入 API

```java
double bal = EconomyAPI.getBalance(uuid);      // 查余额
boolean ok = EconomyAPI.has(uuid, 100);        // 够不够付
EconomyAPI.deposit(uuid, 50);                  // 存入
boolean done = EconomyAPI.withdraw(uuid, 30);  // 取出（不足返回 false）
EconomyAPI.setBalance(uuid, 0);                // 设定

// 监听变动事件
@EventHandler
public void onMoney(MoneyChangeEvent e) {
    // e.getUuid() / e.getBefore() / e.getAfter() / e.getDelta()
}
```

## 配置示例

```yaml
settings:
  starting-balance: 100.0    # 初始余额
currency:
  points-name: "点券"
  exchange:
    enabled: false     # 兑换默认关闭
    mode: both
    rate-gold-to-points: 100
    rate-points-to-gold: 90
    daily-limit-points: 0
  currency-name: "金币"       # 货币名
  max-balance: -1            # 上限（-1 不封顶）
  top-page-size: 10          # 排行每页人数
  transfer-fee-rate: 0.0     # 手续费率（0=免费）
```

## PlayerPoints 余额迁移

不再用 PlayerPoints 点券插件？一键把玩家余额搬到 Economy（玩家余额无损转移）。

1. 旧服执行 `/points export` → 生成 `plugins/PlayerPoints/storage.yml`
2. 将 storage.yml 复制到本插件目录 `plugins/Economy/playerpoints-storage.yml`
3. 执行 `/钱包 迁移points` 预览 → `/钱包 迁移points 确认` 导入（导入前自动备份 data.yml）
   - 模式：`migration.mode`（replace=覆盖 / add=累加）
   - 换算：`migration.point-multiplier`（1 点 = N 金币，默认 1）

## 安装

1. 下载 `economy-1.0.0.jar` 放入服务器 `plugins/` 目录
2. 重启服务器
3. 编辑 `plugins/Economy/config.yml` 自定义参数

## 兼容性

- 支持核心：Spigot / Paper / Purpur / Leaves
- API 版本：1.16+（spigot-api 1.16.5 编译）
- Java：17+
- 前置依赖：无（Vault 可选）

## 开源协议

MIT License

---

# Economy (English)

Lightweight economy core plugin: account management, transfers, balance leaderboard, public EconomyAPI, and optional Vault integration. Zero hard dependencies.

## Features

- **Accounts**: UUID-based, auto-created on join, offline players supported for admin ops
- **Atomic transfers**: debit + credit in one critical section — no double-spend under concurrency; configurable fee rate (free by default)
- **Leaderboard**: paginated chat ranking with your own rank display
- **Balance protection**: never negative; configurable cap; 2-decimal precision
- **EconomyAPI**: static public API for other plugins (`getBalance / has / deposit / withdraw / setBalance`)
- **MoneyChangeEvent**: balance change event with before/after values
- **Vault soft-hook**: auto-registers as economy provider when Vault is present; runs fine without it
- **Chinese commands**: full `/钱包` subcommands with tab completion

## Compatibility

- Server: Spigot / Paper / Purpur / Leaves
- API version: 1.16+
- Java 17+
- Dependencies: none (optional Vault)

## License

MIT License

## Author

**TinyAII**
