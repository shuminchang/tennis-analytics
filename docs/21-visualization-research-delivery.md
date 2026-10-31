# Phase 11.4～11.7 研究工作台交付

目前：11.1～11.7 已完成本機交付與驗收；高併發延遲限制如下，正式部署仍待 Phase 10。使用者授權自主完成剩餘階段並依階段提交；不推送或正式部署，不修改 .vscode/settings.json。

## 11.4 年齡與勝率

路由 `/tennis/visualizations/research`；API `/tennis/api/v1/visualizations/research-metadata?tour=ATP` 與 `/age-performance?tour=ATP&fromYear=2024&toYear=2024&surface=ALL&binWidth=1&group=overall`。
年份必須成對，最多十年，1800～2100；binWidth 僅 1 或 5；group=overall|surface，場地固定白名單。缺資料不補值，年齡為來源賽事年齡，不能以今日生日推算。
重用 live v_stat_matches（非 V18 過時 eligible），每場生成勝／敗方各一個邏輯觀測。AGE_ANALYSIS 的 WINNER／LOSER 只影響該角色，RECORD 影響雙方；先品質再非空／正值。來源、品質版本與結果同一 read-only REPEATABLE READ 交易，SQL 5 秒上限。來源不一致／RUNNING 回 503；公開資料按 source、run、規則、品質 revision、參數快取 60 秒，各研究 cache 16 keys，cache loader 原子化避免同 key 冷啟動重算。
Wilson 95% 沿用既有 z=1.959963984540054；n<20 標低樣本，不隱藏。曲線的無觀測年齡箱為 null／斷線，不能補零或以區間當因果證據。明示來源年齡未獨立查證與球員重複測量限制。
四場 fixture 的 W/O 排除，兩場有比分 RET 納入：6 個雙視角、年齡有效5、品質排除1；20歲箱1勝/2場，Wilson約9.45%～90.55%。解除角色年齡問題可重新納入，不移除勝負或發球樣本。
驗證：ResearchAgeTest 3 案、前端完整55案、lint／build 通過；SQL與UI同一fixture，未寫入真實候選庫。瀏覽器與真實效能留到11.7整合驗收，不提前宣稱完成。

工作目錄 `/home/shumin/tennis-analytics`：

```bash
scripts/test/backend-check.sh -Dtest=ResearchAgeTest test
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
```

## 11.5 發球、場地、慣用手與有界觀測

新增 `/profile-performance?tour=ATP&fromYear=2024&toYear=2024&surface=ALL&x=height_cm&y=ace_rate&limit=500`。profile grain 為 player_season_surface，不把週排名 JOIN 成重複球員。
六個固定指標：height_cm、match_win_rate、ace_rate、double_fault_rate、first_serve_points_won、serve_points_won；X/Y 不可相同。每個率獨立回傳 value、有效場次、分子／分母與 qualified。缺值為 null、零分子保留零，不平均百分比。SERVE_STATS 按角色／RECORD 排除，AGE_ANALYSIS 不誤排發球。
繪圖門檻沿11.1：勝率≥10場；發球各自≥5有效場及100發球分，一發得分改為50個進一發。未達門檻仍保留原始比率與分母於表格，圖上不冒充可靠配對。
limit=1..1000、預設500；依合格比賽數DESC、player ID、年、場地固定選樣，回 population／inspected／valid／excluded／omitted，population=valid+excluded+omitted。這不是代表性隨機抽樣，無法用來推論全體因果。
整窗場地／年份熱圖及己方／對手慣用手彙總在profile上限前計算，與選出的散點母體明確分開。未知／U／A分組不猜測；慣用手／身高為球員快照屬性，未控制對手強度與年代。
驗證：年齡＋profile 共6個後端測試，前端57案、lint／build通過。fixture 的A跨場地 ACE合計5/60、一發23/37，Hard／Clay整窗各3/40；角色發球標記只影響該方，另一方及有效零值仍保留。上限1明示省略4個profile。
真實候選庫僅GET初步冷查詢：ATP2017～2026年齡約2.11s、profile約3.70s；profile人口6982、檢視500、省略6482。這不是最終受限環境驗收；11.7另測暖快取、併發、記憶體與極限窗口，不以目前數字宣稱所有冷查詢<500ms。

## 11.6 進階研究工作台與匯出

`/correlations` 受 ADVANCED／ADMIN 保護；未登入 401、MEMBER 403，帳號停用後既有 session 失效。研究頁 workbench 分頁不向無權限者發送資料請求。
六個固定指標組成 6×6 相關矩陣，使用所選上限內 player-season-surface 的 pairwise complete qualified observations；每格都有 n。Pearson、平均名次處理 ties 的 Spearman 可切換；n<3 或任一軸常數回 null，不補零；n<20 顯示低樣本。沒有 p 值或因果主張，未控制重複球員、多重比較、對手及年代混雜。
箱型圖採 Type-7 分位數，Tukey 1.5 IQR、鬚為界內實際觀測，離群點明列。Hexbin 在各軸 min-max 正規化後以 axial/cube rounding 分箱、回傳六頂點；常數軸擴張 ±0.5；格內總數必須等於有效配對數，並非球場座標。
CSV 保留零、缺值、每格樣本數、來源、條件、選樣上限與授權；字串試算表公式加單引號防護。PNG 由 Canvas 匯出，圖內繪入來源 SHA、期間、樣本、版本及 CC BY-NC-SA 4.0；載入完成後才啟用下載。資料表提供相同數值以供鍵盤與輔助技術使用。
驗證：ResearchAge／Profiles／Statistics／Workbench 共 12 案通過；含 Pearson ±1、ties Spearman √3/2、常數／低樣本 null、Type-7／離群點、hex 計數、真實登入權限與撤銷。前端完整 60 案、lint、build 通過。完整瀏覽器與受限資源驗收仍列於 11.7。

```bash
# 工作目錄：/home/shumin/tennis-analytics
scripts/test/backend-check.sh -Dtest=ResearchAgeTest,ResearchProfilesTest,ResearchStatisticsTest,ResearchWorkbenchTest test
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
```

## 使用方式與資料字典

1. 從「資料探索」點「比賽研究 ↗」，或開啟 `/tennis/visualizations/research`。
2. 選巡迴、起迄年（最多十年）與場地，按「套用研究條件」。網址保存條件，可重新整理／分享。
3. 「年齡與勝率」看曲線、Wilson 區間及各箱 n；沒有觀測的箱為斷線，並非 0% 勝率。
4. 「發球與場地」選 X／Y、觀測上限；散點可連回球員，表格保留未達門檻的原始率。熱圖與持拍手表使用上限前整個時間窗，不能拿散點數當其分母。
5. 「進階工作台」需 ADVANCED／ADMIN 登入。切換 Spearman／Pearson、箱型圖指標，展開各格 N、箱型摘要與六角計數；可下載 CSV／PNG。MEMBER／訪客可使用前兩項研究。
6. 展開「研究來源與方法」查看來源、授權與快照。年份只是篩選窗口；來源的部分年度／歷史缺口不代表完整賽季或完整生涯。

| 指標 | 分子 / 分母 | qualified 最低值 | 單位 |
| --- | --- | --- | --- |
| height_cm | 球員快照、品質與 100..250 範圍 | 有效值；不是每季重測 | cm |
| match_win_rate | 勝場 / stats-v1 有效比賽 | 10 場 | 比例 0..1，UI 顯示 % |
| ace_rate | ACE 合計 / 該指標有效場次的發球分合計 | 5 場及 100 分 | 比例 |
| double_fault_rate | 雙誤合計 / 該指標有效場次的發球分合計 | 5 場及 100 分 | 比例 |
| first_serve_points_won | 一發得分合計 / 進一發合計 | 5 場及 50 個進一發 | 比例 |
| serve_points_won | 一發＋二發得分 / 發球分合計 | 5 場及 100 分 | 比例 |

率的分母必須為正；有效分子為零時保留 0。每個指標分開排除缺值／品質問題，不強迫同一個完整案例母體。相關性只使用各格成對且 qualified 的 profile，箱型圖使用該單指標 qualified profile；重複球員、截斷選樣與多重比較限制一併揭露。本次不把週排名與賽季 profile 任意 JOIN；排名 vs 身高的研究沿 11.3 同日快照散點，11.6 矩陣明確限上述六個 profile 指標，避免混用粒度。

### 本機開發與驗收入口

```bash
# 工作目錄：/home/shumin/tennis-analytics
# VS Code 執行 dev: start all；沿用已設定的 tennis_candidate，不建立預設帳密。
timeout 15s docker version
scripts/test/backend-check.sh clean verify
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
python3 scripts/test/player_search_e2e.py
# 單獨研究 UI；仍使用獨立容器與合成資料，不讀 .env：
python3 scripts/test/player_search_e2e.py --spec research.spec.ts
# 真實備份的隔離受限還原，需已建置 JAR/dist、Docker 與 Playwright：
python3 scripts/ops/budget_stack.py .local/backups/20261029T170402Z-6f9e68/manifest.json --research
```

本機缺 libgbm 的既有 WSL 環境使用 `LD_LIBRARY_PATH=/tmp/tennis-browser-libs/extracted/usr/lib/x86_64-linux-gnu` 前綴；正常已安裝 Playwright 系統相依的環境不需要。測試 JAR 先複製成各次不可變檔案，避免另一個 clean/build 刪掉正在執行的 archive；不更改使用者開發程序。Docker 15 秒 preflight 失敗即保留失敗與日誌，不重置來源庫。

## 11.7 整合驗收與實作修正

- 最終後端 `clean verify`：446 案全通過，JAR 建置成功；前端 60 案、lint／build 通過。完整 Playwright 17 案（既有12＋研究5）通過；統一自架中文字型、明示色階後再驗研究5案通過。包含匿名／MEMBER／ADVANCED、CSV／PNG、手機／中英、URL、來源變更及失敗重試；未對真實庫建立測試帳號。
- 新增 ResearchLifecycleTest：真正 Caffeine 命中；品質狀態與賽事納入範圍變更均失效；比賽來源 RUNNING／缺失不能使用暖結果；重複、空白與過寬參數被拒絕。指紋現在同時涵蓋品質與 tournament_decisions 狀態；不是安全用途的雜湊。
- 最大十年窗口初次受限驗收觸發 5 秒逾時，保留失敗。依查詢計畫把有效 sides 先 materialize，以 GROUPING SETS 一次回傳 profile、整窗場地與持拍手，避免重掃三次；workbench 僅取 profile 彙總。沒有延長 SQL 5 秒／交易10秒限制，也沒有把 V18 既存快照誤當目前品質結果。
- 同一真實來源 ATP2017～2026 的唯讀 EXPLAIN ANALYZE：原 profile 查詢約6439.8ms；調整後完整 profile＋71個整窗分組約1180.2ms（回1000個profile、人口6982）。這是帶 BUFFERS 的診斷，不是 HTTP p95；主機其他負載亦可能影響時間。使用現有索引與 live view，未新增索引／migration、重建來源或重匯資料。
- 瀏覽器驗收抓到 GraphicComponent 漏註冊、中文 Canvas 字型及色階標示；均修復。Canvas 可有多層，測試按每張圖是否實際繪製，不假設恰好一層。手機 resize 等 ResizeObserver 完成才斷言沒有水平溢出，不用 force click 或隱藏內容規避。
- 受限 Apache 的 CSP 實測又發現 Vite 小字型內嵌 data: 被 font-src self 阻擋；build.assetsInlineLimit=0 將資產輸出獨立檔，不放寬 CSP。還原後先 ANALYZE 建立合理規劃統計，並以 TCP 檢查 PG 初始化就緒。E2E 的匯入器／伺服器共用各次不可變 JAR，避免並行 clean 刪掉執行中的檔案。

### 來源授權核對

2026-10-31 直接讀取已使用 Git 快照的 LICENSE、atp/UPSTREAM_README.md、wta/UPSTREAM_README.md。授權為 Jeff Sackmann 的 CC BY-NC-SA 4.0；UI／CSV／PNG 保留作者、授權、來源快照與轉換／篩選說明，未挪用研究網站圖片。LICENSE SHA-256 `d128251e9a3725f694c97a6761df96610481f92a6a69ab936174acb0c4be0589`。這是已使用快照的核對，正式對外發佈與上游更新仍需依 Phase 10 再核對用途及當時版本，不代表已部署或已核可商業用途。

### 最終受限環境報告

[完整測量與驗收摘要](reports/2026-10-31-visualization-release.json)；[前後查詢計畫節點](reports/2026-10-31-research-query-plans.json)。使用既有已成功還原的備份（SHA-256 `84f887815df57bc162b70082144bc3cea63e1b29dba6fbdd03c0d8129e835523`），還原至各次獨立容器；原候選庫與開發 volume 沒有寫入。JAR SHA 與實作檔案 SHA 記於報告。
PostgreSQL／Java／Apache 分別限制320／448／64MiB、0.5／0.4／0.1 CPU，合計832MiB／1 CPU。這不含主機 OS、Docker、瀏覽器或現有 Linode 服務，不能據此保證整台 1GB 主機有同等餘裕。

| 路徑／條件 | 冷查詢 ms | 暖 p50 ms | 暖 p95 ms |
| --- | ---: | ---: | ---: |
| ATP metadata | 1105.03 | 112.06 | 267.51 |
| ATP Top100 scatter | 803.54 | 99.81 | 290.18 |
| ATP Top100 histogram | 91.01 | 87.78 | 167.13 |
| ATP十年 age | 1689.49 | 55.75 | 85.06 |
| ATP十年 profile，上限1000 | 2795.62 | 93.14 | 289.82 |
| ATP十年 correlations，上限1000 | 2377.89 | 45.33 | 93.84 |
| WTA十年 age | 898.61 | 66.03 | 81.38 |
| WTA十年 profile，上限1000 | 1587.90 | 39.12 | 63.87 |
| ATP十年 profile，預設500 | 1580.21 | 49.90 | 102.43 |

每個路徑首筆另列冷值，其後20筆為暖樣本；十年為2017～2026、場地ALL。同 key 四 worker 各16次：age p50/p95=774.83/993.68ms，correlations=547.58/909.40ms。沒有非200結果、沒有隱藏429或延長查詢逾時。這輪九個單路暖p95皆<500ms；**先前 ATP1000筆曾為687.35ms，四路併發亦未達500ms，不能宣稱所有負載皆達標**。本輪量測模式只允許延遲未達標時繼續檢查CSP與記憶體，報告仍明列 target_met/misses；一般既有 benchmark 仍維持未達標退出失敗。

處置：介面預設500、硬上限1000／十年、60秒有界快取、同 key 原子 loader、5秒SQL／10秒交易及既有速率限制均保留；結果仍逐次檢查來源，不能為了數字略過新品質狀態。正式 Phase10 若要求併發p95<500ms，需在實際主機重新評估JSON大小、發布版本指紋或預聚合及CPU預算，不能直接沿用本輪為全面延遲保證。

cgroup memory.peak：PG320.00MiB、Java274.27MiB、Apache12.68MiB；峰值相加約606.95MiB，並非同時採樣。PG達到硬上限並有記憶體回收事件；三容器 oom=0、oom_kill=0，未使用 swap。報告保存CPU節流與memory.events，不能把docker stats結尾快照當峰值。
Apache代理上的真實來源研究頁以桌面／390px手機驗Canvas、來源揭露、無水平溢出及自架字型；CSP仍為font-src self，console／pageerror為空。進階權限與PNG／CSV由獨立資料E2E驗證，不在真實庫建立QA帳號。

全部自建容器與network已清理，失敗輪次也已清理；完整17個瀏覽器流程最終重驗通過。報告及指令可供交接；目前保留於feat/visualization-lab，沒有合併main、推送或正式部署，使用者的.vscode/settings.json保持未追蹤。Phase10的正式HTTPS、整台主機資源、異地備份及回滾驗收仍需另行完成。
