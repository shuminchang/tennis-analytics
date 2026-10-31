# 兩位球員相遇勝率：研究、實作與驗收

2026-10-31。實驗功能 `matchup-v1`，分支 `feat/matchup-probability`，
從 `19a6ff5`（feat/visualization-lab）建立；尚未合併 main、推送或部署。
本輪不修改候選資料庫內容，模型輸入以唯讀交易匯出。

## 研究後的選擇

歷史 H2H 的「勝場／交手場數」描述已發生的交手；本功能回答給定巡迴、
日期、場地及三盤／五盤賽制，兩人相遇時的模型機率。兩者分開呈現。

- [Kovalchik（2016）](https://vuir.vu.edu.au/34652/) 比較多種網球預測方式，
  支持把 Elo 作為有競爭力的基準；加入額外變數不能直接當成必然改善。
- [Sackmann 的 Tennis Elo 說明](https://www.tennisabstract.com/blog/2019/12/03/an-introduction-to-tennis-elo/)
  說明 Elo、場地與賽制的重要性。場地混合權重應以驗證選擇，不直接把某個權重當成定律。
  本站沿用自己的 elo-v1，沒有宣稱重製 Tennis Abstract 的完整模型或傷停修正。
- [Buhamra 與 Groll（2025）](https://arxiv.org/abs/2502.01613)
  提供 Elo 搭配額外特徵的研究方向。本次採小型邏輯斯模型，保留可追溯驗證；
  沒有直接套用該論文的係數、準確率或複雜模型。

因此比較三種方法：標準 Elo、校準 Elo（含場地與五盤交互項）、
Elo 加年齡／身高。由較早驗證資料決定採用方法，不人工指定「高一公分加幾％」。

## H2H 與個別特徵的證據界線

本版沒有獨立 H2H 特徵。兩人先前交手只透過各自 Elo 更新間接反映，
不代表模型已學習特定配對的相剋關係。
Kovalchik（2016）的 Table 2 列出 Prize Probit 使用 H2H 勝場與敗場；
這是納入 H2H 作為候選的研究依據，但該研究未證明多變數模型普遍優於簡單模型。
Tennis Abstract 的上述 Elo 說明及 Buhamra／Groll（2025）列出的變數，
沒有另設獨立 H2H 勝負特徵。不能將三份來源一概說成皆使用或皆不使用 H2H。

年齡有上述研究作為候選依據；身高在 Kovalchik 比較的 Probit Plus 中也曾使用，
但這不證明它在本站 Elo 模型中有額外增益。目前只驗證「年齡＋身高」整組，
沒有分別訓練「只加年齡」「只加身高」的消融比較，不能歸因改善來自哪一項。
後續待驗證：在一致時間切分與測試集合比較個別特徵，
再比較經小樣本修正的 H2H；目前尚未實作，不納入本次交付成果。

## 計算方式與資料時間

標準 Elo：`P(A) = 1 / (1 + 10^((RB - RA) / 400))`。
校準模型使用 `sigmoid(β · x)`，不含截距，六項特徵為：

1. 綜合 Elo 差／400。
2. 場地 Elo 差／400；兩人該場地至少 5 場，最近該場地來源日期距相遇日不超過 730 日才使用。
3. 年齡差／10。
4. `((ageA - 27)^2 - (ageB - 27)^2) / 100`。
5. 身高差／10 公分。
6. 五盤賽制時的綜合 Elo 差／400；三盤為零。

第 4 項的 27 只是二次基底的固定中心，線性與二次項都由資料估計，
**不代表強制認定 27 歲最佳**。係數是相關性參數，不是身高或年齡的因果效果。
所有特徵交換兩人都反號，因此兩人機率互補。綜合 Elo 相同時，標準基準為 50%；
資料不足時則不產生機率，不能以 50% 代替缺值。

來源只有賽事日期，沒有可靠的每場開賽時間。對相遇日 D，僅使用
**賽事日期嚴格早於 D − 21 日**的紀錄；同一賽事日期整組延後才更新特徵。
21 日為保守隔離的設計假設，不能保證所有歷史賽事均在 21 日內結束。
這是來源快照的回溯近似，不是精確歷史賽前資訊重播；來源修正與目前品質判定也可能是事後資訊。

- 只使用 v_stat_matches 及目前成功匯入來源，排除規則沿用既有統計品質設計。
- 年齡與身高來自截止前的比賽欄位，不使用目標比賽本身的年齡／身高，
  也不使用目前球員檔身高或完整生涯資訊回填早期觀測。
- 年齡由先前觀測加日期差／365.2425 推算，12～60 歲；身高 100～250 公分。
- 觀測超過 365 日或被 AGE_ANALYSIS／HEIGHT_ANALYSIS 品質規則封鎖，不使用。
- 兩人各至少有 10 場截止時可用的歷史比賽，最近一場來源日期距相遇日不超過 365 日。
  這不是「最近一年至少 10 場」，也不是官方復出／傷停模型。
- 年齡或身高不足時退回選定的 Elo 模型，不把缺值補零公分／零歲。
- 不含傷勢、當日體能、天氣、賠率或即時資料；WTA 僅提供三盤賽制。

預設相遇日是該巡迴最後來源日期 +22 日，以讓最後一批來源通過隔離；
這是明示的假設日期，不是即時賽程。超過最後來源日期 +366 日拒絕計算。
退役球員若當前資料不合格，需選擇其有近期合格紀錄的歷史日期，不能拿多年以前的分數假裝現況。

## 訓練與防止資料洩漏

ATP／WTA 分開，年度 Y 模型在當年 1 月 1 日可使用，2005 年起：

- 訓練：Y−8 年 1 月 1 日至 Y−3 年 12 月 10 日。
- 驗證：Y−2 年 1 月 1 日至 Y−1 年 12 月 10 日。
- 21 日標籤隔離確保前一區間末端賽事不直接跨入下一區間。
- 測試：Y 年所有當前快照可用的合格紀錄；不參與當年度係數或方法選擇。
- 使用來源身分大小固定配對方向，勝敗為標籤，ID 本身不進入特徵。
- Ridge 候選 0.0001、0.001、0.01，以驗證 log loss 選擇。
- 至少 1000 筆訓練、200 筆驗證才訓練／採用候選；否則保留標準 Elo 並顯示未校準提醒。
- 校準 Elo 須驗證 log loss 比基準改善超過 0.0001，Brier 不劣於基準 +0.0001。
- 年齡／身高候選除了完整觀測子集，也須在包含缺值退回 Elo 的整體驗證集合通過同一門檻。
- 2005 年以前沒有年度學得模型，僅能提供未校準標準 Elo。

先前訓練／驗證年度會出現在下一年度的訓練資料中，屬逐年向前驗證。
沒有把所有年份隨機切分；沒有用較晚測試年決定較早年度模型。
本次研究設計仍使用目前歷史快照，不宣稱不存在任何選樣偏差。

## 真實快照結果

快照 `83733587353df8a41f2fd4f516147d5aa83f5a8d`。
匯出 286774 筆合格來源比賽（ATP 197404、WTA 89370），
來源序列摘要與既有 Elo input SHA 一致；抽樣重播 Elo 最大絕對誤差 `2.27e-13`。
模型係數、訓練 SHA、資料 SHA、NumPy 版本與每年來源筆數均可追溯。
完整機器報告見 [回測 JSON](reports/2026-10-31-matchup-evaluation.json)。

下表 Brier／log loss 均越低越好。來源筆數依**賽事日期年**統計，
不是檔案年份，也不是全部原始列數。N 是通過近期歷史、賽制等條件的測試筆數；
生理完整是其中年齡及身高皆可用的筆數。選定策略對缺值個案使用 Elo。

| 巡迴／年 | 來源／測試 N／生理完整 | 標準 Elo Brier | 校準 Elo Brier | 選定策略 Brier | 標準／選定 log loss |
| --- | --- | --- | --- | --- | --- |
| ATP 2024 | 3111／2573／2573 | 0.2217 | 0.2166 | 0.2143 | 0.6354／0.6150 |
| ATP 2025 | 2819／2347／2345 | 0.2308 | 0.2222 | 0.2196 | 0.6603／0.6271 |
| ATP 2026 部分 | 1432／1198／1178 | 0.2265 | 0.2190 | 0.2133 | 0.6542／0.6145 |
| WTA 2024 | 2742／2386／1428 | 0.2225 | 0.2178 | 0.2172 | 0.6357／0.6217 |
| WTA 2025 | 2459／2110／1007 | 0.2235 | 0.2197 | 0.2191 | 0.6424／0.6282 |
| WTA 2026 部分 | 1286／1108／431 | 0.2193 | 0.2144 | 0.2147 | 0.6303／0.6166 |

2026 僅為目前來源快照涵蓋的部分年度。綜合策略在這些年份優於標準 Elo，
但 **WTA 2026 部分年度不如校準 Elo**；沒有依此回頭挑選該年測試最佳模型。
WTA 生理觀測缺值多且非隨機，完整觀測子集不能代表全體球員。
報告保留 2024 年起測試集的校準分箱，但未宣稱已通過獨立前瞻試驗、
個別球員分組公平性、信賴區間或所有級別的校準驗收。
Brier 0.21 不等於「79% 準確率」，單場 60% 也不是該預測正確性的信賴度。

實際候選庫唯讀煙霧測試：假設 2019-07-01、草地、五盤 Federer 對 Nadal，
採用 ELO_AGE_HEIGHT，Federer 約 54.91%、Nadal 約 45.09%；交換兩人機率互補。
歷史截止為嚴格早於 2019-06-10，生理觀測來自 2019-05-27。
這是模型近似例子，不是該日真實排定賽程或當年官方賽前預測。

## 使用方式

重新啟動後端開發工作，讓新 API 與模型資源載入。前端 Vite 通常會自動更新。
登入 ADVANCED 或 ADMIN，開啟 `/tennis/matchup`（導覽「相遇勝率」）。
以姓名選取同巡迴兩位球員，設定日期、場地與賽制，按「計算相遇勝率」。
結果顯示實際方法、兩方機率、截止與觀測日期、模型對照及限制；表格重要欄位可排序。
不在頁面顯示球員 ID，URL／連結內部仍保留穩定識別值。

API：

- `GET /tennis/api/v1/matchup-prediction/metadata?tour=ATP`
- `GET /tennis/api/v1/matchup-prediction?tour=ATP&first=103819&second=104745&asOf=2019-07-01&surface=Grass&bestOf=5`

匿名 401、MEMBER 403、條件錯誤 400、不存在球員 404、來源或模型版本不符 503。
模型綁定來源匯入版本、品質摘要、Elo 規則與輸入摘要；更新來源後須重新驗證及訓練，
不能靜默用舊模型套新資料。資料不足回傳 available=false，不是 HTTP 失敗。

## 重現與維護

工作目錄：`/home/shumin/tennis-analytics`。
訓練是離線維護工具，正式 Java 服務只讀取約 74 KiB 係數 JSON，不需 Python 或 NumPy。
先使用已通過驗證的 JAR；下列匯出關閉 Flyway，在 REPEATABLE READ 唯讀交易檢查來源。
輸出檔必須尚不存在，避免覆寫既有研究證據。

```bash
./backend/mvnw -f backend/pom.xml -DskipTests package
mkdir -p .local/model-training/rebuild
(
  source .env
  export DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5433}/tennis_candidate"
  export DB_USERNAME="$POSTGRES_USER"
  export DB_PASSWORD="$POSTGRES_PASSWORD"
  backend/export-matchup \
    .local/model-training/rebuild/input.csv \
    .local/model-training/rebuild/metadata.json
)
python3 -m venv .local/model-training/venv
.local/model-training/venv/bin/pip install -r scripts/model/requirements.txt
OPENBLAS_NUM_THREADS=1 .local/model-training/venv/bin/python scripts/model/train_matchup.py \
  .local/model-training/rebuild/input.csv \
  .local/model-training/rebuild/metadata.json \
  .local/model-training/rebuild/matchup-v1.json \
  --report .local/model-training/rebuild/evaluation.json
```

本次環境已有 NumPy 2.0.1，實際訓練直接使用 python3，未新增系統套件。
重建後先評估報告，確認來源、回測、係數及契約；經驗收才更新
`backend/src/main/resources/prediction/matchup-v1.json` 與 docs 報告，重新打包／重啟。
也可用 `APP_MATCHUP_MODEL=file:/絕對路徑/matchup-v1.json` 載入經驗收的外部係數檔。
`.local/model-training/` 已忽略，不提交原始匯出或秘密值。

## 驗證指令

```bash
python3 scripts/model/test_matchup.py
scripts/test/backend-check.sh clean verify
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
python3 scripts/test/player_search_e2e.py
```

Python 驗證隔離期、目標比賽生理特徵不洩漏、交換對稱及指標手算。
後端驗證 SQL 截止、權限、來源失配、缺值退回、無近期歷史、交換互補與係數資源。
瀏覽器以一次性 Git／CSV、獨立 PostgreSQL、唯讀匯出產生自己的小樣本基準模型，
不使用本機候選庫，也不假造可用的訓練樣本。前端驗證姓名選取、書籤、日期、
手機寬度、缺值與錯誤狀態。最終實際執行結果記於本文件驗收段落。

### 本輪驗收結果

- 後端 `clean verify`：454 案通過，JAR 建置成功。
- Python 模型契約：3 案通過；既有維運／視覺化契約檢查 4／8 案通過。
- 前端 Vitest：73 案通過；lint、TypeScript 與 production build 通過。
- 真實隔離瀏覽器：21 案全通過，含新相遇勝率與既有功能回歸。
- 候選庫唯讀真實例子與交換對稱通過；沒有匯入或重建候選庫資料。
- `git diff --check` 通過；`.vscode/settings.json` 保留未追蹤，未納入本輪變更。

瀏覽器初次因本機 `libgbm.so.1` 缺少而無法啟動，未計為通過；
依先前環境記錄使用已解壓的函式庫後重驗成功，沒有安裝系統套件：

```bash
LD_LIBRARY_PATH="$PWD/.local/verification/browser-libs/extracted/usr/lib/x86_64-linux-gnu" \
  python3 scripts/test/player_search_e2e.py
```

本機證據：`.local/verification/matchup-final-backend.log`、
`matchup-frontend-tests.log`、`matchup-browser.log`、`matchup-real-smoke.json`；
手機截圖 `frontend/test-results/matchup-mobile.png`（忽略的測試產物）。
CI 新增固定 NumPy 離線測試依賴，既有 Docker／Playwright 流程沿用。
本輪已驗收本機執行；尚未宣稱遠端 CI、正式部署或預測效能前瞻驗收完成。
