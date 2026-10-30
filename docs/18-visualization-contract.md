# Phase 11.1：視覺化指標、品質與 API 契約

狀態：**11.1 設計與本機唯讀驗收完成；11.2 API 與 11.3 UI 已本機驗收**。版本 `visualization-v1`，定版日期 2026-10-30。
依據：[研究](16-visualization-research.md)、[Roadmap](17-visualization-roadmap.md)、V12 stats-v1／V14 公開排名／V18 讀模型，以及既有來源與品質保存規則。Phase 10 正式部署未完成。

## 1. 本輪決策與開發邊界

- MVP 使用同巡迴、精確同排名日的「球員橫斷面」，一人一點；先做身高×排名／積分散布圖與同批身高直方圖。
- 100～250 cm（含端點）沿用 player-rules-v1 的品質界線；非空且型別正確仍須無 OPEN HEIGHT_ANALYSIS。140～230 只是前次研究過濾，正式規則不採用。132 cm 等範圍內來源值保留，不因此宣稱已驗證為成年身高。
- 可見球員重用 PlayerRepository 的成功來源及 PLAYER_SEARCH 排除。排名重用 v_public_rankings 的成功來源，不從原始排名衝突隔離列補回資料。
- 比賽資格重用 analytics_matches.eligible（stats-v1）；W/O、DEF、空白／不明比分排除，有比分的 RET 納入。不改既有勝率、H2H、Elo 或舊 SVG 圖表。
- 11.1 當時不新增資料表、migration、套件或正式 API；11.2 已依此完成三個端點，未新增 migration。先以 V18＋players／rankings 的有界查詢設計；需要新索引時先提供 EXPLAIN，再用 V19+，不能回改既有 migration。
- 相關矩陣、逐拍、性別與人氣不在 MVP；不存在的欄位不能由 ATP/WTA、姓名或賽果推測。

## 2. 觀測粒度與可配對規則

| grain | 一列代表 | 日期語意 | 禁止的操作 |
| --- | --- | --- | --- |
| ranking_snapshot | (tour, player_id, ranking_date) | 精確排名觀測日；不補前一期 | 把同一球員多週當成不同人 |
| player_season | (tour, player_id, season) | tourney_date 的年份 | 將當期排名直接冒充整季排名 |
| player_season_surface | 上列再加 surface | 指定年份及場地 | 混用全年分母與單場地分子 |
| player_match | (tour, match_id, role) | 來源賽事日，非每場實際比賽日 | 直接把一場勝方列當成所有參賽者 |
| player_age_bin | tour＋年齡箱＋時間／場地條件 | floor(age/binWidth)*binWidth | 用今日年齡套歷史比賽 |
| elo_observation | 成功 run＋tour＋player＋dimension＋sequence | elo-v1 的近似來源排序 | 混用不同 run、把賽後 Elo 當賽前預測特徵 |

MVP 散布圖只接受 `(height_cm,rank)`、`(rank,height_cm)`、`(height_cm,ranking_points)`、`(ranking_points,height_cm)`；都使用 ranking_snapshot。直方圖只接受 height_cm。其他維度即使已列入字典，也不能因此宣稱 MVP API 支援。
後續身高×ACE 使用 player_season 或 player_season_surface：每個球員／群組先彙總再配對，不將同一人的身高與每週排名／每場發球做笛卡兒 JOIN。多巡迴同 ID 必須分開。

## 3. 指標與維度字典

狀態「可用」指來源與規則足以支援設計，不表示新增 API 已存在。「部分」須處理缺值／品質／時間限制。「後續」非 11.2 MVP 的可選軸。

| key | 型別／單位 | 粒度、來源及公式 | NULL／零及品質資格 | 狀態／用途 |
| --- | --- | --- | --- | --- |
| tour | enum ATP/WTA | 全部粒度的識別維度 | 必填；不是個人性別 | 可用；單次必選 |
| player_id | int | players／複合識別 | 正值，須可見；不按姓名合併 | 可用；球員連結 |
| height_cm | int／cm | players.height_cm，快照屬性 | NULL 缺值；100..250 且無 HEIGHT_ANALYSIS；不推斷歷史身高 | 部分；MVP 軸／直方圖 |
| rank | int／名次 | 指定日 rankings.rank | >0；排名軸反轉；不跨日補值 | 可用；MVP 軸 |
| ranking_points | int／分 | 指定日 rankings.points | 0 有效，NULL 缺值；不跨年代當等尺度實力 | 部分；MVP 軸 |
| hand | enum R/L/U/A/UNKNOWN | players.hand | NULL→UNKNOWN，U 與 A 保留獨立類；不併入左右手 | 部分；MVP 顏色 |
| ranking_band | category | 同日 rank：1–10、11–50、51–100、101+ | 缺排名不分組；不能套到歷史比賽 | 可用；後續顏色 |
| season | int／年 | extract(year from tourney_date) | 來源賽事年，不從檔名推日期 | 可用；後續篩選 |
| surface | enum Hard/Clay/Grass/Carpet/Unknown | matches.surface | NULL／非已知類別顯示 Unknown，不默默當 Hard | 部分；後續分組 |
| match_count | int／場 | 同組合格 player-match 計數 | 0 有效，不含 W/O | 可用；後續樣本標示 |
| match_win_rate | double／比例 0..1 | wins/(wins+losses)，同一群組 | 無場次 null；PERSONAL_STATS／SCORE_ANALYSIS 已由 stats-v1 處理 | 可用；後續軸 |
| age_at_match | decimal／歲 | 對應角色 winner_age／loser_age | 正值且無該角色 AGE_ANALYSIS；不由 dob 自動補值 | 部分；11.4 |
| ace_rate | double／比例 | SUM(角色 ace)/SUM(角色 svpt) | svpt>0，0<=ace<=svpt；該角色無 SERVE_STATS；0 ACE 有效 | 部分；後續軸 |
| double_fault_rate | double／比例 | SUM(df)/SUM(svpt) | svpt>0，0<=df<=svpt；無 SERVE_STATS；0 DF 有效 | 部分；11.5 |
| first_serve_points_won | double／比例 | SUM(1st_won)/SUM(1st_in) | 1st_in>0，0<=1st_won<=1st_in；svpt 非空時 1st_in<=svpt；無 SERVE_STATS | 部分；11.5 |
| serve_points_won | double／比例 | SUM(1st_won+2nd_won)/SUM(svpt) | 三項皆非空、svpt>0、各 won>=0、總 won<=svpt；無 SERVE_STATS | 部分；11.5 |
| serve_points_n | bigint／分 | 各指標有效樣本的 SUM(svpt) | 不共用另一指標的有效分母；無樣本 0，比例為 null | 可用；後續分母 |
| elo | double／評分 | 指定成功 run／dimension 的 rating_after | 無觀測 null；初始值不當成實際觀測；不可跨 run 混合 | 部分；ADVANCED/ADMIN，後續 |
| gender / ball_coordinates | 未提供 | 缺少已驗證來源 | 不以巡迴或比分代填 | 需新來源；不實作 |

所有率以 0..1 傳輸，UI 才乘 100；分子、分母與有效場數一併回傳。SQL 整數加總升型 bigint，避免 smallint 相加或 integer 除法；不平均逐場百分比。不同指標獨立保留有效樣本。

### 3.1 品質作用範圍

只使用 SUCCEEDED 來源；問題須以 run_id、source_file_id、record_number 連回來源，再以 tour/player_id 或 tour/match_id 對應，不靠姓名或裸 player_id。

- OPEN PLAYER_SEARCH：球員不進公開母體；PERSONAL_STATS／MATCH_HISTORY／SCORE_ANALYSIS 按既有 stats-v1，不另發明隱性分母。
- HEIGHT_ANALYSIS：只使該球員身高不可用，不移除其排名、勝負或別人的身高。
- MATCHES 的 SERVE_STATS／AGE_ANALYSIS：RECORD 影響雙方，WINNER／LOSER 只影響對應角色；先排品質再檢查該指標必要欄位。既有 SERVE_STATS 是整個角色的粗粒度標記，不能宣稱已細分到單一發球欄位。
- PLAYERS 的 dob AGE_ANALYSIS 不等於來源 match age 有問題；本階段年齡採 match age，不依生日回推或將生日問題擴大為整場排除。
- RESOLVED／DISMISSED 問題不再以 OPEN 排除；來源／問題狀態更新後必須刷新衍生模型並清快取。V18 的 eligible 是物化結果，未刷新不能混用新問題與舊讀模型對外服務。

年齡正值與格式通過仍不代表真實年齡已查證。本機範圍 ATP 14.2～63.6、WTA 7.6～67.4，後續年齡圖必須附 `SOURCE_AGE_NOT_INDEPENDENTLY_VERIFIED` 警示，保留來源異常待查；不在 11.1 暗中新增年齡上下限或改寫來源。

### 3.2 最小樣本決策

MVP 身高／排名不按比賽數剔除，一個有效配對即可顯示；不足 5 點顯示 LOW_SAMPLE，不能推論相關性。
後續球員賽季率：可計算的原始比例仍可供資料表查看，但散點預設須 match_win_rate 至少 10 場，發球指標至少 5 個各自有效場次、ACE／DF 至少 100 發球分、一發得分至少 50 個進一發。未達者列 INSUFFICIENT_SAMPLE 並揭露門檻，不把低樣本率改成 0。這是產品展示決策，不是統計顯著性的保證。
年齡箱勝率 n<20 顯示低樣本標記，不隱藏箱；Wilson 95% 使用既有 z=1.959963984540054，n=0 的比例與上下界均為 null。
Wilson 是描述性二項區間；同球員重複出賽、對手、年代及場地的混雜未消除，不當成因果或獨立球員群體的正式推論。未實作 cluster bootstrap。

## 4. 母體、排除原因與直方圖

先固定 tour／rankingDate／rank<=topN 的可見排名母體，再判斷 X、Y。**Top N 指名次門檻**，同名次全納入，population 可大於 N；不是先丟掉缺身高者再補足 N 人。排序 rank ASC、player_id ASC，回應點數最多 2000，超過則明確拒絕而非截掉同名次。

`sample.population = sample.valid + sample.excluded`。每人只指定一個主要排除原因（primaryReasons 的 count 加總須等於 excluded）：
1. 依固定 metric key 字典序處理 required metrics（不受 X/Y 互換影響）。
2. 同一 metric 依 QUALITY_BLOCKED → MISSING → OUT_OF_RANGE → INSUFFICIENT_SAMPLE 判斷。
3. primaryReasons 是互斥計數；另可提供 overlappingReasons，必須標明可重複，不可加總成 excluded。

例如 POINTS 缺值不排除「身高×排名」，只影響「身高×積分」；未知持拍手僅使用 Unknown 色，不排除點。直方圖單獨以同母體 height_cm 資格計數，不承接另一張散布圖因 Y 缺值而少掉的點。

直方圖採等寬 bins：使用有效 height 的 min/max，不依無效值決定邊界；[left,right)，最後一箱含右端點。min=max 時回傳單箱 [value-0.5,value+0.5]，避免零寬。無有效值時 domain=null、bins=[]；每箱 count 合計等於 valid，空箱保留，不能把零計數當缺值。

## 5. 11.2 API 契約（已實作，見 [API 驗收](19-visualization-api.md)）

路由共用 `/tennis/api` context path。Java 使用明確 DTO；不可將 JDBC 原始 map／內部檔案路徑直接序列化。

| GET 路由 | 參數與預設 | 回應 |
| --- | --- | --- |
| /v1/visualizations/metadata | tour 必填 ATP/WTA | MetadataResponse：metricVersion、grains、允許 axisPairs／color／histogramMetrics、日期範圍／latestRankingDate、limits、source、warnings |
| /v1/visualizations/scatter | tour 必填；rankingDate 可省略；topN=100；x=height_cm；y=rank；color=hand | ScatterResponse：context、axes、sample、items、warnings |
| /v1/visualizations/histogram | tour 必填；rankingDate 可省略；topN=100；metric=height_cm；bins=20 | HistogramResponse：context、metric、sample、domain、bins、warnings |

省略 rankingDate 才解析該巡迴最新可公開觀測日並回傳 resolved 日期；指定日期無資料回 200 空母體，**不補前週、不回 404**。metadata 的日期 min/max 只是可用觀測範圍，不表示每天都有資料。

### 5.1 DTO 欄位

- Context：grain="ranking_snapshot"、tour、rankingDate（無任何排名時 null）、topN、source、metricVersion、statRuleVersion="stats-v1"、qualityPolicyVersion="visualization-v1"、generatedAt。日期一律 ISO YYYY-MM-DD；generatedAt 為帶時區 ISO timestamp。
- Source：repositoryUrl、snapshotSha、playerRunId、rankingRunId、matchRunId（MVP 不使用比賽時可 null）、各使用 run 的 transform/quality/scope rules SHA；需要 V18 時加 analyticsRefreshedAt／sourceRunId；需要 Elo 時加 eloRunId／rulesSha／inputSha。不要輸出 archive_location、密碼或使用者資訊。
- Axis：key、unit、direction="ascending"|"descending"、valueType；rank 為 descending（名次 1 顯示在上方）。hand 圖例固定 R、L、U、A、UNKNOWN。
- Sample：population、valid、excluded、primaryReasons[{metric,code,count}]；散布圖是配對有效數，直方圖是該 metric 有效數。
- Point：tour、playerId、displayName、x:number、y:number、group:hand code|"ALL"。NULL 軸值不進 items；不以 NaN／Infinity／空字串代替數字。名稱只作呈現，以複合 ID 連球員詳情。
- Bin：lower:number、upper:number、upperInclusive:boolean、count:int；另有 domain:{min,max}|null。count=0 有效。
- 後續 rate 點擴充 samples、numerator、denominator，不能只給百分比；勝率使用 matches denominator，發球率另保留有效場次和發球分。

### 5.2 回應示意（人工值，不是真實統計）

```json
{
  "context": {"grain":"ranking_snapshot","tour":"ATP","rankingDate":"2024-01-01","topN":100,"metricVersion":"visualization-v1","statRuleVersion":"stats-v1","qualityPolicyVersion":"visualization-v1","generatedAt":"2026-10-30T15:00:00Z","source":{"repositoryUrl":"https://github.com/Aneeshers/tennis-sackmann-archive","snapshotSha":"83733587353df8a41f2fd4f516147d5aa83f5a8d","playerRunId":1,"rankingRunId":3,"matchRunId":null}}},
  "axes":{"x":{"key":"height_cm","unit":"cm","direction":"ascending","valueType":"integer"},"y":{"key":"rank","unit":"rank","direction":"descending","valueType":"integer"}},
  "sample":{"population":2,"valid":1,"excluded":1,"primaryReasons":[{"metric":"height_cm","code":"MISSING","count":1}]},
  "items":[{"tour":"ATP","playerId":1,"displayName":"Fixture A","x":180,"y":1,"group":"R"}],
  "warnings":["LOW_SAMPLE"]
}
```

示意 source 省略各規則 SHA，只為可讀性；正式 DTO 必須帶齊本次使用 run 的規則雜湊，不能只回一個版本名稱。

### 5.3 白名單、權限與成本上限

- MVP 三端點訪客可讀（MEMBER／ADVANCED／ADMIN 相同公開母體）；不共享私人收藏資料。Elo 軸保留 ADVANCED／ADMIN，相關矩陣未開放，之後需獨立權限契約。
- topN 為 1..1000 整數；bins 為 1..50 整數；最大 2000 點；color=hand|none。拒絕不支援的 metric／axisPair、重複／未知 query 參數、混用 grain／任意排序與 SQL；所有值參數綁定，SQL 表達式只由程式白名單選擇。
- 不開放未限制全歷史 scatter。後續年度查詢 fromYear／toYear 必須成對、<=10 個來源年份；surface 用固定白名單，先聚合再限點，不能直接返回全部 player-match。
- 400：參數格式／白名單／範圍錯誤或同名次點數超上限；401／403：未來需權限的功能；404：未定義路由／球員資源；429：沿用既有限流＋Retry-After；503：查詢逾時、來源未就緒、混合快照或讀模型過期。錯誤 DTO 固定 `{code,message}`，不含 SQL、stack trace 或來源路徑。既有 API 不在本階段改回應格式。
- 每查詢 SQL statement_timeout<=5000ms，Web 禁用 JIT；只做有索引的指定日小母體 JOIN，不在每次互動掃 557 萬排名。11.2 必須實測 EXPLAIN、取消／逾時與空資料。
- Caffeine 60 秒、每個新公開查詢 cache<=128 keys，沿用固定可控 cache 名稱；key 含 sourceVersion、qualityPolicyVersion、run IDs、grain、resolved date、tour、axisPair、color、topN／bins，分開 scatter／histogram，不依密碼或 Session 分公開快取。
- 更新來源／品質後必須原子刷新並清快取。v1 候選庫只允許單一已發布快照：依成功來源核對 player/ranking（後續加 match）一致；同種來源取相容的最新成功 run，不能在多快照並存時猜測目前版本；不相容就 503、另建候選庫。查詢與版本 metadata 以同一 read-only REPEATABLE READ snapshot 取得，禁止拼接新舊來源。

## 6. 人工驗收與真實結果

人工 fixture：[4 場比賽](fixtures/visualization-v1-hand-example.json)。這是規格 oracle，未匯入真實資料庫，亦不是 11.2 的 SQL／API 測試。

- 第 1／4 場為帶比分 RET，納入；第 3 場 W/O 排除。剩 3 場產生 6 個 player-match、3 勝／3 負。
- 球員 A：1 勝 2 負，勝率 1/3；ACE=(2+3+0)/(20+30+10)=5/60；DF=3/60；一發得分=23/37。逐場百分比平均 1/15，不等於 ACE 加權 1/12。
- A 的第 4 場 age 被標記，故年齡樣本只去掉這個角色；勝負與 ACE 的 0/10 仍保留。年齡有效共 5 個；20 歲箱為 1 勝／2 場。
- 身高母體 4 人，valid=1、missing=1、quality=2，excluded=3；範圍內 140 cm 但已標品質者仍排除。NULL 與 0、未知持拍手分別處理。
- Wilson 1/3 的 95% 區間約 [0.06149194472039621,0.7923403991979523]；n=0 回 null／null。
- 8 個契約測試通過：雙視角、RET/W/O、加權分母、零／缺值、角色與 RECORD 品質、年齡作用、身高資格、精確日期、Wilson（部分案例同測）。11.2 必須以 Testcontainers 對同一 fixture 驗 SQL 與 HTTP 契約，不能把此 Python oracle 當成已實作 API。

### 6.1 本機唯讀報告

[完整報告](reports/2026-10-30-visualization-contract.json)，`transaction_read_only=on`，同一快照；來源 SHA 83733587353df8a41f2fd4f516147d5aa83f5a8d，成功來源批次 PLAYERS=1、RANKINGS=3、MATCHES=4。

| 項目 | ATP | WTA |
| --- | ---: | ---: |
| 可見球員（不是所有原始 players） | 66,909 | 70,570 |
| 身高有效（100..250＋品質） | 4,154 | 1,343 |
| 身高缺值 | 62,748 | 69,227 |
| 身高品質／範圍排除 | 7 | 0 |
| 2026-06-08 Top 100 身高有效／母體 | 99/100 | 52/100 |
| Top 100 身高缺值 | 1 | 48 |
| stats-v1 比賽 | 197,404 | 89,370 |
| 雙視角母體 | 394,808 | 178,740 |
| ACE 有效角色觀測 | 203,458 | 94,964 |
| DF 有效角色觀測 | 203,458 | 94,891 |
| 一發得分有效角色觀測 | 203,456 | 94,967 |
| 年齡有效角色觀測 | 389,236 | 178,386 |
| SERVE_STATS 品質影響角色數 | 3 | 8 |

原始 players／rankings／matches 合計仍為 137,483／5,573,027／361,571，對照 docs/16 無變更。
ATP 身高比研究的 140..230 多 1 人：來源 player_id=211522、高 132 cm；不是多匯入球員。可見母體另排除 PLAYER_SEARCH 佔位紀錄，不能直接拿原始玩家數當分母。
docs/16 的發球初篩要求同場雙方皆有值，本報告各角色獨立計數且加品質條件，因此不是將原初篩場數乘二，也不是匯入數變動。真實全時段計數只用於資格評估；不表示未達年度最低樣本的所有球員都能進未來散布圖。

## 7. 重現與交接

工作目錄 `/home/shumin/tennis-analytics`，不用提供 DB 密碼：

```bash
python3 scripts/test/visualization_contract_checks.py
timeout 15s docker version
# PostgreSQL 內部明確 BEGIN READ ONLY；逾時視為失敗，不重置資料庫。
timeout 120s docker compose exec -T db psql -XqAt -v ON_ERROR_STOP=1 \
  -U tennis_admin -d tennis_candidate \
  < scripts/data/visualization_contract_inspection.sql \
  > /tmp/visualization-contract-inspection.json
python3 -m json.tool /tmp/visualization-contract-inspection.json
```

SQL 限時 90 秒、輸出無密碼與來源本機封存路徑；用公開來源資料計數，不重跑 CSV 或匯入。Docker 失聯時保留錯誤日誌，不以空檔當報告。
11.1 的指標、MVP DTO、權限與上限在本文件定版；來源真實性與 Phase 10 仍有明確限制。11.2 已依此契約實作三個公開端點與 fixture parity、品質、日期、快取及逾時驗證；11.3 UI 也已交付（見 [20](20-visualization-explorer.md)），11.4～11.7 交付、研究資料字典與驗收見 [21](21-visualization-research-delivery.md)。
