# 第一版賽事納入範圍

資料快照：83733587353df8a41f2fd4f516147d5aa83f5a8d

目前是分類設計，尚未實作匯入篩選。

## 原則

- 保留來源 tourney_level，不直接改寫為現代級別。
- 納入與否、賽事系列、統計用途分開記錄。
- 例外以 (tour, tourney_id) 指定，不靠名稱關鍵字直接刪除。
- 同一賽事 ID 若存在不同日期或名稱，先查核後再套用規則。
- 未確認分類不等於資料錯誤，也不等於已確認應排除。
- 預設統計只使用已確認納入且符合相關品質規則的紀錄。

## 已查證：奧運示範賽

以下三項不納入預設正式奧運與一般正式賽統計，
但保留原始資料與分類原因。

| tour | tourney_id | 名稱 | 原因 |
| --- | --- | --- | --- |
| WTA | 1968-W-OL-MEX-01A-1968 | Guadalajara Olympics Demo | 示範賽 |
| WTA | 1968-W-OL-MEX-02A-1968 | Mexico City Olympics Exhibition | 表演賽 |
| WTA | 1984-W-OL-USA-01A-1984a | Los Angeles Olympics | 示範賽 |

ITF 說明：1968、1984 年為示範／表演性質，
1988 年恢復正式獎牌項目。

來源：
https://www.itftennis.com/en/events/olympics-la-2028/event-info/

本次列出的 1988 年起正式奧運賽事列為納入對象，
保留獨立的奧運分類，不因此宣稱來源涵蓋完整。

## 已確認納入：正式奧運單打

以下為目前快照已盤點、且屬於正式獎牌項目的賽事。
依 (tour, tourney_id) 套用 INCLUDE，分析分類為 OLYMPICS。

| tour | tourney_id | 來源名稱 | 狀態 |
| --- | --- | --- | --- |
| ATP | 2024-0096 | Paris Olympics | INCLUDE |
| WTA | 1988-W-OL-KOR-01A-1988 | Seoul Olympics | INCLUDE |
| WTA | 1992-W-OL-ESP-01A-1992 | Barcelona Olympics | INCLUDE |
| WTA | 1996-W-OL-USA-01A-1996 | Atlanta | INCLUDE |
| WTA | 2000-W-OL-AUS-01A-2000 | Olympics | INCLUDE |
| WTA | 2004-W-OL-GRE-01A-2004 | Olympics | INCLUDE |
| WTA | 2008-W-OL-CHN-01A-2008 | Olympics | INCLUDE |
| WTA | 2012-W-OL-GBR-01A-2012 | Olympics | INCLUDE |
| WTA | 2016-O16 | Olympics | INCLUDE |
| WTA | 2021-650 | Tokyo Olympics | INCLUDE |
| WTA | 2024-0096 | Paris Olympics | INCLUDE |

查證依據：
https://www.itftennis.com/en/events/olympics-la-2028/event-info/

注意：
- 這是目前已列出的來源紀錄，不代表完整的歷屆奧運資料。
- 東京奧運來源 ID 使用 2021，保留原值，不改成 2020。
- 納入賽事不代表全部比賽都通過品質與統計資格檢查。
- 未列出的 O 紀錄維持 REVIEW。
- ATP 其他年份是否存在於其他來源級別，尚待查找。

## 尚待細分的賽事

- 目前盤點的 ATP、WTA F 已建立系列分類與明確例外，詳見後節；
  ATP 1982-806 及未列出的 F 維持 REVIEW。
- 歷史涵蓋完整性及其他級別中的年終賽仍待確認。
- WTA CC 與未列入已確認例外的 W 維持 REVIEW。
- F 或 O 的年度缺口需跨級別查找，不能直接判為缺資料。

## WTA W：已查證的近期賽事

保留來源 tourney_level=W，另記分析用分類。
以下以 (tour, tourney_id) 指定例外，不改原始 CSV。

| tour | tourney_id | 賽事 | 分析分類 | 預設統計 |
| --- | --- | --- | --- | --- |
| WTA | 2015-W-WT-SIN-01A-2015 | WTA Finals | WTA_FINALS | 納入 |
| WTA | 2021-2033 | Charleston 2 | WTA 250 | 納入 |
| WTA | 2021-2035 | Belgrade | WTA 250 | 納入 |
| WTA | 2021-2041 | Parma | WTA 250 | 納入 |
| WTA | 2021-406 | Strasbourg | WTA 250 | 納入 |
| WTA | 2021-2017 | Bad Homburg | WTA 250 | 納入 |
| WTA | 2021-710 | Eastbourne | WTA 500 | 納入 |
| WTA | 2021-703 | San Jose | WTA 500 | 納入 |
| WTA | 2021-2040 | Cleveland | WTA 250 | 納入 |
| WTA | 2021-2047 | Chicago 1 | WTA 250 | 納入 |
| WTA | 2024-2052 | Buenos Aires 125 | WTA 125 | 第一版排除 |

「納入」只代表賽事範圍符合，仍需通過比賽品質及指標規則。
上述是目前快照的明確例外，不推廣至其他年份的同名賽事。

官方來源：
- https://www.wtatennis.com/tournaments/808/wta-finals/2015
- https://wtafiles.wtatennis.com/pdf/matchnotes/2021/2040_preview.pdf
- https://www.wtatennis.com/tournaments/2047/chicago-250/2021
- https://www.wtatennis.com/tournaments/2052/buenos-aires-125/2024

## 歷史 W 的處理界線

- 1960～1990 年代共有 3045 個賽事紀錄組合。
- 目前代表樣本不足以確認全部賽事層級。
- 保留來源，分類狀態為待確認；不自動對應現代級別。
- 尚未確認者暫不進入預設主巡迴統計，這不代表永久排除。
- 必須揭露統計涵蓋範圍，不能稱為完整生涯統計。
- 2015 WTA Finals 已在 W 級別找到，先前 F 清單的缺口
  不代表整個資料來源缺少該賽事。

## 分類狀態

| 狀態 | 意義 | 是否進入預設統計 |
| --- | --- | --- |
| INCLUDE | 已確認符合第一版範圍 | 是，仍須通過相關品質規則 |
| EXCLUDE | 已確認不符合第一版範圍 | 否，保留來源 |
| REVIEW | 尚未確認 | 否，保留待查，不等同資料錯誤 |

這些是賽事範圍狀態，不代替比賽或欄位品質標記。

## 規則套用順序

1. 先檢查 (tour, tourney_id) 的已確認賽事例外。
2. 沒有例外時，套用已定稿的巡迴與來源級別規則。
3. 沒有已確認規則時，預設 REVIEW，不默默納入或丟棄。

尚未完成的級別草案不能當成已定稿規則。
規則需記錄理由與查證來源；未來修改需重新計算受影響統計。

## 保存來源與判斷依據

後續資料模型需要能保存：
- 原始 tourney_level。
- 分析用賽事分類。
- INCLUDE、EXCLUDE 或 REVIEW 狀態。
- 採用的規則、理由與查證來源。
- 原始資料快照及來源位置。

具體資料表與欄位配置留待 DDL 設計決定。

## 第一版通用規則

這是本專案第一版的統計範圍政策。
適用於已選取的年度單打檔案，不擴及雙打、
資格賽、挑戰賽或其他來源檔案。

先套用已確認的 (tour, tourney_id) 例外，
沒有例外時才使用下表。

| tour | 來源 tourney_level | 狀態 | 第一版處理 |
| --- | --- | --- | --- |
| ATP | A、G、M | INCLUDE | 納入來源所標示的巡迴單打範圍 |
| WTA | G、I、P、PM、T1、T2、T3、T4、T5 | INCLUDE | 納入，保留來源級別，不強行對應現代分類 |
| ATP、WTA | D | INCLUDE | 納入團體賽中的個人單打勝負，獨立標示賽事類型 |
| ATP、WTA | F | REVIEW | 依賽事確認年終賽系列及特殊賽制 |
| ATP、WTA | O | REVIEW | 依已確認的正式奧運賽事清單覆寫 |
| ATP、WTA | E、J | EXCLUDE | 第一版不納入表演賽、青少年賽 |
| WTA | 35+H、50+H | EXCLUDE | 第一版不納入這些 ITF 層級賽事 |
| WTA | CC、W | REVIEW | 已確認的賽事例外優先，其餘待查 |
| 任一 | 空白或未列出的代碼 | REVIEW | 記錄待確認，不自動納入 |

### 統計界線

- D 納入個人單打勝負，不將團體冠軍算為個人單打冠軍。
- INCLUDE 不代表該場比分、時間、身分或發球統計都可信。
- W/O、退賽、身分疑義與數值異常另依指標規則處理。
- 不因來源分類可用，就宣稱所有年份或完整生涯均已涵蓋。
- REVIEW 紀錄保留來源，分類確認後可重新計算統計。

### 例外清單進度與待辦

- 目前盤點的正式奧運賽事已建立 INCLUDE 清單；
  未列出的 O 仍為 REVIEW，歷年涵蓋完整性尚未確認。
- 目前盤點的 ATP、WTA F 已建立系列分類與例外；
  ATP 1982-806 及未列出的 F 維持 REVIEW。
- 歷史涵蓋完整性及其他級別中的年終賽仍待確認。
- 例外 ID 在 CSV 中的存在性，以及同一鍵的日期、名稱與級別一致性，
  已於本輪驗證通過；目前清單不是已執行匯入篩選的結果。
- 繼續處理 CC 與歷史 W，但不要求全部查清才設計資料表。

## 對外顯示

- 說明統計採用的資料日期、賽事範圍與排除規則。
- 未確認歷史完整性前，不標示為完整生涯統計。
- 賽事被納入不代表資料完整，也不代表每個指標都有效。

## ATP F：目前快照的明確例外

以下清單中的每個 ID 都與 tour=ATP 組成完整例外鍵。
清單只涵蓋目前已盤點紀錄，不依 ID 後綴自動納入其他年份。

### ATP_FINALS：INCLUDE

共 35 個賽事 ID：

```text
1990-605
1991-605
1992-605
1993-605
1994-605
1995-605
1996-605
1997-605
1998-605
1999-605
2000-605
2001-605
2002-605
2003-605
2004-605
2005-605
2006-605
2007-605
2008-605
2009-605
2010-605
2011-605
2012-605
2013-605
2014-605
2015-605
2016-0605
2017-0605
2018-0605
2019-0605
2020-0605
2021-0605
2022-0605
2024-0605
2025-0605
```

保留來源名稱與級別，分析系列統一為 ATP_FINALS。
官方沿革：
https://www.nittoatpfinals.com/en/heritage/history

### NEXT_GEN_FINALS：EXCLUDE

共 6 個賽事 ID：

```text
2017-7696
2018-7696
2019-7696
2023-7696
2024-7696
2025-7696
```

第一版不混入預設一般巡迴統計，保留原始資料。
理由是參賽與賽制具有特殊性，不代表來源錯誤。
未來若提供專屬分析，需依各年實際規則處理。

賽制參考：
https://www.nextgenatpfinals.com/en/event/rules-and-innovations

目前公布的規則不能直接套用至全部歷史年份。

### REVIEW

- ATP / 1982-806 / Naples Finals WCT：
  保留待查，不直接歸為 ATP_FINALS。

### 涵蓋界線

- 35 + 6 + 1 = 42，對應本次 ATP F 完整盤點。
- 未列出的 F 仍採通用 REVIEW 規則。
- 2023 年一般 ATP Finals 未出現在本次 F 清單；
  已在 A 級別找到 2023-0605，詳見下方補充；未補造紀錄。
- 這份清單不代表完整歷屆 ATP Finals。

## WTA F：目前快照的明確例外

以下每個 ID 都與 tour=WTA 組成例外鍵。
三個系列皆為 INCLUDE，但分析系列與冠軍統計分開。
保留來源 tourney_level 與名稱，不改寫原始 CSV。

### WTA_FINALS：INCLUDE

目前 F 清單共 25 個賽事 ID：

```text
1975-1006-1975
2000-W-WT-USA-01A-2000
2001-W-WT-GER-01A-2001
2002-W-WT-USA-01A-2002
2003-W-WT-USA-01A-2003
2004-W-WT-USA-02A-2004
2005-W-WT-USA-01A-2005
2006-W-WT-ESP-01A-2006
2007-W-WT-ESP-01A-2007
2008-W-WT-QAT-01A-2008
2009-W-WT-QAT-01A-2009
2010-W-WT-QAT-01A-2010
2011-W-WT-TUR-01A-2011
2012-W-WT-TUR-01A-2012
2013-W-WT-TUR-01A-2013
2014-W-WT-SIN-01A-2014
2016-0808
2017-0808
2018-0808
2019-0808
2021-808
2022-808
2023-808
2024-808
2025-808
```

此外，前節已確認的 W 級別紀錄：
2015-W-WT-SIN-01A-2015
也使用同一分析系列 WTA_FINALS，僅套用一次，不重複匯入。

上述例外合計涵蓋 26 個 WTA_FINALS 賽事紀錄組合，
不代表完整歷屆清單；歷史 W 中仍可能存在同系列賽事。

官方參考：
https://www.wtatennis.com/tournaments/808/wta-finals/1975
https://www.wtatennis.com/tournaments/808/wta-finals/2015

### WTA_TOURNAMENT_OF_CHAMPIONS：INCLUDE

共 6 個賽事 ID：

```text
2009-W-CHA-INA-01A-2009
2010-W-CHA-INA-01A-2010
2011-W-CHA-INA-01A-2011
2012-W-CHA-BUL-01A-2012
2013-W-CHA-BUL-01A-2013
2014-W-CHA-BUL-01A-2014
```

這些冠軍不計為 WTA_FINALS 冠軍。

官方參考：
https://www.wtatennis.com/tournaments/1043/sofia/2014

### WTA_ELITE_TROPHY：INCLUDE

共 5 個賽事 ID：

```text
2016-1081
2017-1081
2018-1081
2019-1081
2023-1081
```

這些冠軍不計為 WTA_FINALS 冠軍。
只列目前已盤點紀錄，不自行補造缺少年份。

官方參考：
https://www.wtatennis.com/tournaments/1081/zhuhai/2023

### 檢查與適用界線

- 25 + 6 + 5 = 36，對應本次 WTA F 完整盤點。
- 2015 WTA Finals 原屬 W，不列入上述 36 個的計數。
- 未列出的 F 仍維持通用 REVIEW。
- 分類確認不代表每場比分、球員身分與統計數值均已查證。
- 官方頁面的日期與 CSV 日期可能不同；
  本次只確認賽事系列，不據此修改來源日期。

## ATP A：2023 年 Finals 補充

跨級別檢查找到以下來源紀錄，納入 ATP_FINALS 系列。
保留原始 A 級別；這是來源分類對照，不修改 CSV。

| tour | tourney_id | 名稱 | 日期 | 來源級別 | 分析系列 | 預設統計 |
| --- | --- | --- | --- | --- | --- | --- |
| ATP | 2023-0605 | Tour Finals | 20231113 | A | ATP_FINALS | 納入 |

35 個 F 加此 A，共 36 個已列出的 ATP_FINALS 賽事；不是完整歷屆宣稱。

## 本輪驗證結果

104 個例外鍵（ATP 44、WTA 60）均存在，且每個鍵僅對應一組日期、名稱與級別。
全體年度比賽檔亦未發現同一 (tour, tourney_id) 的這三欄組合衝突。
分類試算：ATP INCLUDE 199290、EXCLUDE 92、REVIEW 7；
WTA INCLUDE 89882、EXCLUDE 587、REVIEW 71713。
這是套用範圍規則的原始列數，尚未套用品質規則，並非已匯入或有效統計筆數。
重現方式及身分疑點見 [檢查交接](06-data-inspection-handoff.md)。
