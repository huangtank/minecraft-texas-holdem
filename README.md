# Minecraft Texas Hold'em

在 Minecraft（Paper 伺服器）裡實際跑起來的德州撲克桌，用 Java 寫的原生外掛，不依賴 Skript 或任何第三方外掛。

## 這是什麼

固定一張六人座的實體牌桌：公共牌與每位玩家的底牌都用 Item Display 實體顯示在桌面/座位上，底牌只有本人看得到（用 Paper 原生的 `Player#hideEntity` / `showEntity`，不需要額外外掛）。玩家右鍵座位入座、離座，整個下注流程（翻牌前／翻牌／轉牌／河牌／攤牌、邊池、逾時或斷線自動棄牌）全部由外掛自動判定，沒有玩家需要擔任「莊家」手動發牌。

籌碼是獨立的虛擬籌碼，只在這張桌上流通，不跟任何經濟外掛或真實貨幣掛鉤，存在外掛自己的資料檔裡，重開伺服器不會不見。

## 需求

- Paper 伺服器（開發時對應版本：`26.2`）
- Java 25
- Gradle（專案內附 wrapper，不需要自己另外安裝）

## 建置

```
./gradlew build
```

建出來的 jar 在 `build/libs/TexasHoldem-<version>.jar`，丟進伺服器的 `plugins/` 資料夾即可。

## 架設牌桌（管理員）

外掛啟動時牌桌是空的，要由 op 站在遊戲裡實際想放桌子的位置設定：

1. 站到要放公共牌的位置：`/holdemadmin setup center`
2. 依序站到 6 個座位的位置：`/holdemadmin setup seat 1` ～ `/holdemadmin setup seat 6`
3. 全部設定完成後會自動生成牌桌上的所有顯示物件與可互動座位。
4. 可用 `/holdemadmin setup status` 檢查目前設定進度。

座位與公共牌座標存在外掛資料夾的 `table.yml`，重開伺服器會照原樣重新生成。

## 玩家指令

右鍵座位入座／離座；輪到自己時用聊天指令行動：

```
/holdem fold                看牌 (check)
/holdem check
/holdem call                跟注
/holdem bet <總金額>         下注（目前沒人下注時）
/holdem raise <總金額>       加注到指定的總下注金額
/holdem allin               全下
/holdem leave                離座
```

## 管理員指令

```
/holdemadmin setup center|seat <n>|status
/holdemadmin start                        強制開始新的一局（需要 ≥2 人入座）
/holdemadmin reset                        卡關時強制重置，退還這局已下注的籌碼
/holdemadmin rebuy <AUTO|DISABLED|ADMIN_ONLY>   切換籌碼歸零後的補充規則
/holdemadmin chips <give|set> <玩家> <金額>
```

## 設定檔（config.yml）

```yaml
table:
  seat-count: 6
blinds:
  small: 50
  big: 100
chips:
  starting-stack: 10000
  rebuy-mode: AUTO   # AUTO / DISABLED / ADMIN_ONLY
turn:
  timeout-seconds: 60
```

## 規則細節

完整的規則依據與這個伺服器版本的客製化限制記錄在專案內部的德州撲克規則文件裡，實作邏輯以那份文件為準。
