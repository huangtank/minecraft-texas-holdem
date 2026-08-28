# Minecraft Texas Hold'em

在 Minecraft（Paper 伺服器）裡實際跑起來的德州撲克桌，用 Java 寫的原生外掛，不依賴 Skript 或任何第三方外掛。

## 這是什麼

支援**同時架設多張**六人座的實體牌桌：公共牌與每位玩家的底牌都用 Item Display 實體顯示在桌面/座位上，底牌只有本人看得到（用 Paper 原生的 `Player#hideEntity` / `showEntity`，不需要額外外掛）。玩家右鍵空座位入座；輪到自己行動時，座位上方會浮現一排只有本人看得到的按鈕（同樣是原生實體做的 hologram），點一點就完成，**不需要打任何斜線指令，也不使用物品欄 GUI**。座位一坐上就會多出一個所有人都看得到的小玩家頭像，一眼看出哪些座位有人；離座則要點懸浮在座位上方的「離開座位」按鈕，不能靠點椅子（避免誤觸）。整個下注流程（翻牌前／翻牌／轉牌／河牌／攤牌、邊池、逾時或斷線自動棄牌）全部由外掛自動判定，沒有玩家需要擔任「莊家」手動發牌。

籌碼是獨立的虛擬籌碼，只在牌桌上流通，不跟任何經濟外掛或真實貨幣掛鉤，存在外掛自己的資料檔裡，重開伺服器不會不見。

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

外掛啟動時沒有任何牌桌，op 站在想放新牌桌中心的位置建立一張：

1. 站到牌桌中心的位置（公共牌會顯示在這裡）：`/holdemadmin table create`
2. 會拿到一個桌子編號，6 個座位自動平均分布在中心點四周並面朝中心（距離由 `config.yml` 的 `table.seat-radius` 控制），連同看得見的椅子／桌面裝飾物與可互動座位一起生成，不用逐一設定座位。
3. 可以重複執行 `table create` 建立更多張桌子；`/holdemadmin table list` 查看所有桌子的編號、座標與目前狀態。
4. 對某張桌子的位置不滿意，直接 `/holdemadmin table delete <編號>` 刪除（會先強制結束該局並退還所有籌碼），再到新位置重新 `table create` 一次。

所有桌子的中心點座標存在外掛資料夾的 `table.yml`（依編號分節），重開伺服器會照原樣重新生成每一張桌子。

> 從單桌版本升級：舊版 `table.yml` 只存一個 `center`，第一次用新版啟動時會自動搬遷成編號 1 號桌，不會遺失。舊版每次重新設定只會記住最新的中心點，之前設過的位置留下的椅子/桌面沒人清，開伺服器後執行一次 `/holdemadmin table cleanup` 即可把這些殘留物件清乾淨（不會動到現存的桌子）。

## 玩家怎麼玩——全程不打指令

- 右鍵空座位入座（一次只能坐一張桌）。
- 輪到你行動時，座位正上方會由上而下浮現一排按鈕：棄牌／看牌或跟注／下注或加注／全下，點按鈕就完成；點「下注／加注」會換成金額選單（`1BB`／`2BB`／`3BB`／底池／自訂金額／返回）。
- 需要精確金額時點「自訂金額」，接著**直接在聊天室打數字**送出（不是指令），或輸入 `cancel` 取消。
- 想離座就點座位上方常駐的「離開座位」按鈕（點椅子本身不會有反應）。

## 管理員指令

```
/holdemadmin table create                       站在要放新牌桌中心的位置，建立一張新桌
/holdemadmin table list                         列出所有牌桌的編號、座標與狀態
/holdemadmin table delete <編號>                 強制結束該桌、退還所有籌碼並徹底刪除
/holdemadmin table cleanup                       清除舊版單桌測試留下的殘留牌桌物件
/holdemadmin start <編號>                        強制開始新的一局（需要 ≥2 人入座）
/holdemadmin reset <編號>                        卡關時強制重置該桌，退還這局已下注的籌碼
/holdemadmin pause <編號>                        暫停該桌（沒人能行動、計時器停止，但這局不會作廢）
/holdemadmin resume <編號>                       恢復被暫停的桌子
/holdemadmin rebuy <AUTO|DISABLED|ADMIN_ONLY>    切換籌碼歸零後的補充規則
/holdemadmin chips <give|set> <玩家> <金額>
```

打 `/holdemadmin` 或任何子指令打錯時都會列出完整用法說明，也支援 Tab 自動完成子指令、玩家名稱等。

## 設定檔（config.yml）

```yaml
table:
  seat-count: 6
  seat-radius: 6.0   # 座位跟中心點的距離（方塊），套用到每一張桌子
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
