# Minecraft Texas Hold'em

在 Minecraft（Paper 伺服器）裡實際跑起來的德州撲克桌，用 Java 寫的原生外掛，不依賴 Skript 或任何第三方外掛。

## 這是什麼

固定一張六人座的實體牌桌：公共牌與每位玩家的底牌都用 Item Display 實體顯示在桌面/座位上，底牌只有本人看得到（用 Paper 原生的 `Player#hideEntity` / `showEntity`，不需要額外外掛）。玩家右鍵座位入座、離座；輪到自己行動時全部靠點擊 GUI 按鈕完成，**不需要打任何斜線指令**。整個下注流程（翻牌前／翻牌／轉牌／河牌／攤牌、邊池、逾時或斷線自動棄牌）全部由外掛自動判定，沒有玩家需要擔任「莊家」手動發牌。

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

外掛啟動時牌桌是空的，op 只需要站在想放牌桌中心的位置設定一次：

1. 站到牌桌中心的位置（公共牌會顯示在這裡）：`/holdemadmin setup center`
2. 6 個座位會自動平均分布在中心點四周並面朝中心（距離由 `config.yml` 的 `table.seat-radius` 控制），連同看得見的椅子／桌面裝飾物與可互動座位一起生成，不用逐一設定座位。
3. 可用 `/holdemadmin setup status` 檢查目前設定狀態。
4. 對想要的位置/朝向不滿意就再站到新的地方重新 `/holdemadmin setup center` 一次即可。

中心點座標存在外掛資料夾的 `table.yml`，重開伺服器會照原樣重新生成整張桌子。

## 玩家怎麼玩——全程不打指令

- 右鍵空座位入座；右鍵自己的座位在非輪到自己時站起來，輪到自己時則重新打開行動選單。
- 輪到你行動時會自動彈出一個 GUI：棄牌／看牌或跟注／最小下注或加注／底池大小下注或加注／全下，點按鈕就完成。
- 需要精確金額時點「自訂金額」，接著**直接在聊天室打數字**送出（不是指令），或輸入 `cancel` 取消。

## 管理員指令

```
/holdemadmin setup center|status
/holdemadmin start                        強制開始新的一局（需要 ≥2 人入座）
/holdemadmin reset                        卡關時強制重置，退還這局已下注的籌碼
/holdemadmin rebuy <AUTO|DISABLED|ADMIN_ONLY>   切換籌碼歸零後的補充規則
/holdemadmin chips <give|set> <玩家> <金額>
```

打 `/holdemadmin` 或任何子指令打錯時都會列出完整用法說明，也支援 Tab 自動完成子指令、玩家名稱等。

## 設定檔（config.yml）

```yaml
table:
  seat-count: 6
  seat-radius: 2.3   # 座位跟中心點的距離（方塊）
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
