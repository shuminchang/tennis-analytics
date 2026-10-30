# 後續交付 Phase 11：互動式網球統計視覺化 Roadmap

狀態：**11.1～11.7 本機交付完成；效能邊界與正式部署前置見 21**｜建檔：2026-10-30｜提案名稱：Tennis Data Explorer

相關文件：
- [研究與資料可行性評估](16-visualization-research.md)：外部 8 + 額外參考、現有 DB 計數、統計分析陷阱與來源。
- [後續九階段交付追蹤](14-pre-release-roadmap.md)：**後續交付** 1～9 已完成；**Phase 10 正式部署尚未驗收**（參閱 [15](15-local-release-readiness.md)）。
- [專案總規格第 14 節](../doc/網球統計網站規格與開發文件.md)：**歷史教學** 0～13 階段，其中歷史「11」是 Elo；本文「後續交付 Phase 11」不是同一編號序列。

## 1. 範圍與不變條件

### 1.1 目標

在既有 React 19 + TypeScript + Vite、Spring Boot/Java 21、PostgreSQL 16、Apache /tennis 的架構上，建立多參數 Data Explorer，讓使用者自選 X／Y／顏色／排名日／場地／時間區間，探索排名、身高、持拍手、年齡、勝率、ACE、Elo 等資料。

第一個可展示切片：
1. 同一排名觀測日 Top N 身高 × 排名散布圖，名次軸反轉，點選連到球員詳情。
2. 同批球員的身高直方圖，明確揭露身高缺值與資料品質排除。
3. 球員年份／場地 ACE 率與身高的散布圖，附分母、樣本數和篩選條件。

### 1.2 非目標

- 不在本階段新增真實性別欄位或以 ATP/WTA 虛構球員性別。
- 不展示虛構球場落點、Hawk-Eye 3D 軌跡或逐拍資料。
- 不做即時比分、賭盤預測、自由 SQL、任意使用者自訂運算程式。
- 不為此階段改寫既有 Elo、H2H、排名圖／舊 SVG LineChart。
- 不把設計稿或本地通過直接視為 Linode 正式環境通過；Phase 10 仍須獨立完成。

## 2. Roadmap 與交付界線

以下各階段是**預計工作包**，並非已完成項目；拆分依「一階段一個可驗證交付」及 [AGENTS.md](../AGENTS.md) 的教學、Git 與品質規範。

| 階段 | 工作包 | 核心成果 | 依賴 | 狀態 |
| --- | --- | --- | --- | --- |
| 11.1 | 指標契約、品質資格、資料探勘 SQL | 指標字典、母體、排除、分母、日期與授權邊界 | V12/V18；研究文件 | 已完成，見 [18 契約](18-visualization-contract.md) |
| 11.2 | 後端聚合與 API | metadata、scatter、histogram；索引／快取／契約測試 | 11.1 | 已完成，見 [19 API 驗收](19-visualization-api.md) |
| 11.3 | Data Explorer MVP | React 散布圖＋直方圖、X/Y/color／巡迴／日期／Top N、工具提示 | 11.2 | 已完成，見 [20 UI 驗收](20-visualization-explorer.md) |
| 11.4 | 年齡－勝率與信賴區間 | 以 player-match 雙視角聚合，分組曲線與 Wilson CI | 11.1–11.3 | 已本機驗收，限制見 21 |
| 11.5 | 發球、持拍手與場地 | ACE／雙誤／一發得分、分場地熱圖、左右手分組比較 | 11.4 指標與樣本共用 | 已本機驗收，限制見 21 |
| 11.6 | 進階統計工作台 | Box Plot、相關性矩陣、Pairwise N、Hexbin、匯出 | 11.5；效能實測 | 已本機驗收，限制見 21 |
| 11.7 | 整合測試、資源預算與發布候選 | 端到端測試、無障礙、來源揭露、效能與交接文件 | 11.1–11.6；正式部署需 Phase 10 | 已本機驗收，限制見 21 |

**與 Phase 10 的關係**：Phase 11 可先在本機獨立開發與驗收；正式上線必須先完成 Phase 10 的 HTTPS、資源、權限、備份與回滾驗收，不因編號順序隱含已部署。

## 3. 11.1｜指標／資料資格規格（先做）

已交付：[visualization-v1 定版契約](18-visualization-contract.md)、8 個人工契約測試、真實候選庫唯讀報告。API／UI 尚未實作。

### 交付
- 定義可選維度白名單及資料型別：X / Y = height_cm、rank、ranking_points、age_at_match、ace_rate、double_fault_rate、first_serve_points_won、match_win_rate、elo 等；色彩分組 = tour、hand、surface、ranking_band。
- 每個指標記錄：意義、觀測單位、時間語意、來源欄位、公式、0／NULL 行為、品質影響碼、最小分母、可配對的軸及所需權限。
- 明確分離「球員橫斷面」、「球員×年份」、「球員×場地」、「player-match」和「週排名觀測」；不同粒度不能直接無差別 JOIN。
- 調查身高品質門檻：先前 140～230 cm 只是探索查詢用的臨時過濾，不是已批准的產品規則；以來源品質標記為準並明確決策。
- 參照既有 V12 stats-v1、V18 analytics read models、SERVE_STATS、AGE_ANALYSIS、PLAYER_SEARCH 等品質用途決定可用樣本。訪客能否使用全部圖表、進階角色的成本限額需定版。

### 驗收
- 至少 12 個指標／維度的資料字典，並標識可用、部分可用、需新來源。
- 以小型人工 2～4 場資料手算勝／敗雙視角、ACE／發球分、空值、退賽與 Wilson 95% CI。
- 至少一份真實候選庫唯讀計數報告，對照 docs/16 的 2026-10-30 基準；不必重新匯入來源。
- 輸出最終 API DTO 與查詢限額的決策紀錄；完成前不得直接新增 migration 或長期預計算表。

## 4. 11.2｜後端聚合與 API

已交付 metadata／scatter／histogram、型別化 DTO、來源／品質一致性與有界快取；完整後端 431 案、候選庫 HTTP 與 EXPLAIN 通過。詳細見 [19 API 驗收](19-visualization-api.md)；其餘路由仍為後續工作。

### 路由草案（最後名稱以 11.1 API 契約為準）

所有端點均掛在既有 Spring Boot context path `/tennis/api` 下：

~~~http
GET /tennis/api/v1/visualizations/metadata
GET /tennis/api/v1/visualizations/scatter?tour=ATP&x=height_cm&y=rank&color=hand&rankingDate=2026-06-08&topN=100
GET /tennis/api/v1/visualizations/histogram?tour=WTA&metric=height_cm&rankingDate=2026-06-08&topN=100&bins=20
GET /tennis/api/v1/visualizations/age-performance?tour=ATP&surface=Clay&fromYear=2000&toYear=2025
GET /tennis/api/v1/visualizations/correlations?tour=ATP&fromYear=2010&toYear=2025
~~~

前 3 個是 MVP；後 2 個列在 11.4／11.6，不能在 11.2 尚未實作時宣稱存在。

### API 回應範例（示意，不是實際球員資料）

~~~json
{
  "x": "height_cm",
  "y": "rank",
  "tour": "ATP",
  "rankingDate": "2026-06-08",
  "sample": { "population": 100, "valid": 99, "excluded": 1 },
  "items": [{ "playerId": 12345, "x": 188, "y": 24, "group": "R" }],
  "sourceVersion": "83733587353df8a41f2fd4f516147d5aa83f5a8d",
  "metricVersion": "visualization-v1"
}
~~~

### 實作邊界
- Controller → Service → JDBC Repository：沿用 `JdbcTemplate`、參數綁定、固定 metric-to-SQL 白名單；不接受原始 SQL、任意函式名稱、未限制的年份／日期／Top N。
- 重用 V18 `analytics_matches`、`analytics_events`、指定日 `rankings`、`players`，新表與索引只在成本證據充分後，以 **V19+** 新 migration 加入，不能回改 V1～V18。
- 優先在資料更新後產生有界、可版本化的 player-season / player-surface 聚合；沿用候選庫發布、原子性刷新、Caffeine 60 秒等既有策略，更新模型後讓快取及新舊版本一致。
- 限制回應點數、查詢時間與分頁；對長期序列使用後端聚合或抽樣，且揭露抽樣規則。不得任由 1 vCPU / 1 GB 伺服器動態全表掃描。
- 403/400/404/503 與空資料回應契約明確；不同身分沿用現有安全策略；對昂貴相關矩陣可限制為 ADVANCED/ADMIN（待 11.1 定案）。
- 把 `population`、`valid`、`excluded`、逐原因數及 `sourceVersion` 與圖表結果綁定，確保使用者知道自己看到的是哪個分母。

### 驗收
- Testcontainers：Top 100 同日人數、空排名日、不補前週資料、跨巡迴重 ID、身高異常、NULL vs 0、雙視角不重複計數、品質影響碼排除、注入字串拒絕。
- 指標手算與 SQL 回應一致；限定最大筆數、超時、錯誤狀態、越權與快取失效測試通過。

## 5. 11.3｜前端 Data Explorer MVP

已交付 `/tennis/visualizations`、模組化 ECharts spike、散布圖／直方圖、URL／雙語／資料表、53 個前端測試與完整 12 個瀏覽器流程驗證。詳細及 bundle 成本見 [20 UI 驗收](20-visualization-explorer.md)。

建議新增：
- `frontend/src/features/visualizations/`：ExplorerPage、FilterPanel、ChartFrame、ScatterPlot、Histogram、SampleFootnote、API types 與 i18n 文案。
- React Router 新增 `/tennis/visualizations`，沿用專案 route basename、雙語、現有色彩／間距。不得改動舊圖表的行為以便回歸。
- 圖表技術：**優先評估 Apache ECharts + React 封裝**，散布、直方、Heatmap、Brush、DataZoom、Canvas 可共用；先做一個 spike 驗證套件大小、SSR 無關性、可及性與實際 bundle。Recharts 作為替代方案；**不預設兩套都安裝**。
- 互動：X/Y 軸更換、巡迴 ATP/WTA、排名日期、Top N、持拍手顏色、Tooltip（球員 ID／姓名／N）、連到既有詳情、URL query 可分享、載入／無資料／品質排除提示。
- Tooltip 與視覺顏色不能是唯一資訊來源；提供可鍵盤操作的資料表／下載表格輔助模式。
- 年齡與性別用詞嚴謹：ATP/WTA 顯示「巡迴」，不是已驗證性別；排名軸反轉、缺值及資料更新日清楚標示。

### 驗收
- Vitest／RTL：X/Y/color、URL 重整、空結果、錯誤重試、雙語與無障礙文字。
- Playwright：真實 Testcontainers → HTTP API → browser → Tooltip／篩選／球員連結；桌機及手機截圖檢視。
- 正式 build、lint 及原有 41 個前端測試加新案例；舊頁面回歸。

## 6. 11.4～11.6｜研究主題擴充

| 階段 | 指標／互動 | 應驗證的統計邊界 |
| --- | --- | --- |
| 11.4 年齡與勝率 | 每年齡／年齡分箱、場地分組、勝率趨勢、Wilson 95% CI、最低有效場數 | age quality、觀測單位、分組樣本數、年代／球員重複測量 |
| 11.5 發球與場地 | ACE/總發球分、雙誤/總發球分、一發得分/進一發、場地熱圖、左／右手對戰組合 | 各指標缺值獨立排除、SERVE_STATS、對手排名／Elo 混雜；不得混用平均百分比與加權比例 |
| 11.6 進階研究 | 箱型圖、Spearman／Pearson（六個同粒度賽季場地指標；排名 vs 身高另用同日快照散點）、相關矩陣、pairwise N、Hexbin、CSV/PNG 匯出 | 多重比較提示、效果大小、不確定性、資料授權、匯出上限及使用者可見來源聲明 |

對發球與勝率的關係先提供**賽後描述分析**；若後續擴充賽前預測，特徵只能取當場開賽前可得的排名、Elo、歷史滑動窗口等，避免資料洩漏。

## 7. 11.7｜效能、品質與 Release Candidate

### Definition of Done（全部必要）
1. 圖表有標題、X/Y 單位、顏色圖例、篩選條件、來源版本、時間範圍、有效樣本／排除原因、統計方法與缺值提示。
2. 資料 SQL／Service 與獨立人工 fixture 對得上；用現有已認可的測試容器驗證，不把錯誤逾時記為通過。
3. 1 GB 目標主機約束：本機受限堆疊以 `docs/15-local-release-readiness.md` 為測試基準，測 P50/P95、冷／暖、併發、查詢計畫、記憶體峰值；一般暖 p95 <500ms 為**目標非已通過**，未達標記錄結果和處置。
4. API 上限、授權、快取隔離、速率限制、負載與資料更新後一致性完成回歸；不可暴露帳號或來源封存私有路徑。
5. 前端保留既有風格，支援中英、手機、鍵盤可用性、無資料／錯誤、深連結與連回球員頁。
6. 附上 README／使用指南、指標資料字典、來源授權與開發限制，正式發佈前再核對上游 LICENSE；不因研究參考而直接抓圖使用。

## 8. 工作方式與 Git 建議

- 建議由最新 `main` 建立 `feat/visualization-lab`，各子階段小型 commit，使用者本輪已授權先提交研究文件並完成 11.1，已建立該分支；後續 11.2 開發與推送／部署不因文件存在自動授權。
- 11.1 完成後才能批准資料模型及 API 契約；11.2、11.3 以小型可驗收切片推出，避免一次引進 8 種圖表造成回歸風險。
- 文件狀態務必由「規劃中」改成「已實作」、「已測試」、「已在本機驗收」或「正式部署」的真實狀態，不能因文件存在就標完成。
- 既有 `.vscode/settings.json` 為使用者未追蹤檔案，不修改、不加入 commit；不得接觸真實資料寫入、部署、推送。
- Phase 10 部署及異地備份位置仍待獨立決策；Phase 11 本機開發不會自動解除此限制。

## 2026-10-31 最終交接

使用者已授權並完成後續全部階段。11.4～11.7 的API、統計定義、完整測試、832MiB／1 CPU測量與CSP修正見 [21交付紀錄](21-visualization-research-delivery.md)。最後單路暖p95達500ms目標，但前次1000筆及四路併發有未達標數字，已保留；不把目標說成所有負載的保證。未合併main、推送、正式部署或改動既有.vscode/settings.json；Phase10仍待正式主機驗收。
