# 網球數據視覺化研究與資料可行性評估

版本：研究草案 v0.1｜2026-10-30｜狀態：**研究與規劃已整理；功能尚未實作**

本文針對使用者提供的 8 個參考網站、額外案例與本機 `tennis_candidate` 資料，評估互動式網球統計圖的可行性；實作任務、API 與驗收切分見 [後續視覺化交付計畫](17-visualization-roadmap.md)。

**Roadmap 編號注意**：原始 [教學大綱第 14 節](../doc/網球統計網站規格與開發文件.md) 的「階段 11」是 Elo；2026-10-30 [後續九階段交付追蹤](14-pre-release-roadmap.md) 為另一套交付編號，完成 1～9 後，10 為正式部署，擬新增的 **後續交付 Phase 11 = 視覺化探索實驗室**。不得把它解讀為原教學階段 11 尚未完成。

## 1. 研究目標與產品定位

- 由球員資訊查詢擴充為 Tennis Data Explorer：可調 X、Y、顏色、樣本範圍與分組的統計探索工具。
- 第一個切入點是「身高 vs 排名」、「身高 vs ACE 率」、「比賽當時年齡 vs 勝率」；後續擴充左／右手持拍、場地、年代、Elo 與不同強度對手。
- 保留既有球員頁、排名圖、H2H、Elo，不重寫舊功能；每張圖要顯示來源快照、計算規則、有效 N、缺值／排除數、時間範圍。
- 研究目的是描述資料中的關係，不宣稱因果或精確賽前預測。

### 1.1 資料來源與設計參考的界線

**唯一已匯入、可用於本站計算統計值的基礎**為 [tennis-sackmann-archive](https://github.com/Aneeshers/tennis-sackmann-archive) 的本機驗證快照 `83733587353df8a41f2fd4f516147d5aa83f5a8d`，經本站的來源、賽事與品質規則轉換為 PostgreSQL。其他網站是設計／方法參考，**其圖片與數值均未匯入本站**。原始資料授權與實際使用條件，正式上線前仍須核對原倉庫 LICENSE、上游說明及本站 /about 聲明。

## 2. 參考網站調查（2026-10-30）

下表「適用度」針對**目前已匯入的 match-level / ranking 資料**，不是原網站的品質評分。

| 來源 | 可借鏡的圖表／分析問題 | 本站適用度 | 可落地範圍／缺口 |
| --- | --- | --- | --- |
| [National Geographic：Using Maps and Data Vis to Understand Tennis](https://www.nationalgeographic.com/culture/article/150906-data-points-tennis-tracking) | 球落點、擊球軌跡、球場空間映射；文章介紹 Damien Saunder 對特定比賽的追蹤視覺化 | 低 | 本站沒有逐球空間座標與軌跡，僅能作未來研究；不能用 match-level 賽果捏造落點 |
| [The Tennis Notebook：Tennis Data Visualization](https://medium.com/the-tennis-notebook/tennis-data-visualization-48a5b47e0bdd) | 彙整 FiveThirtyEight、Financial Times、Jeff Sackmann 等敘事型網球視覺化作品 | 中高 | 借鏡「一張圖回答一個問題」與文章／互動圖結合；它是參考索引，不是本站可直接下載的統計資料 |
| [DataScience+：Visualizing Tennis Grand Slam Winners Performances](https://datascienceplus.com/visualizing-tennis-grand-slam-winners-performances/) | 大滿貫冠軍長條圖、球員／賽事熱圖、時間軸、弦圖、雷達圖 | 高（部分） | 本站可依已確認、合格、唯一決賽建立歷屆勝方；缺漏決賽與團體賽不得當作冠軍，外部舊年份數字不可直接照搬 |
| [Reddit：The Reign of the King of Clay](https://www.reddit.com/r/tennis/comments/1cj5fcv/the_reign_of_the_king_of_clay_data_visualization/) | Nadal 紅土成就與單球員敘事式作品 | 高 | 利用紅土勝率、年度比賽數、Elo 和法網決賽設計專題；原貼文只作設計靈感，不是可信數值來源 |
| [Reddit：At What Age Do Male Players Win the Most?](https://www.reddit.com/r/tennis/comments/1r5fp1a/at_what_age_do_male_players_win_the_most_i/) | 50 位球員的賽季勝率／大滿貫績效相對個人巔峰分數 | 高 | 重新計算年齡、賽季勝率及有效樣本數；不可照搬原作者結論，其抽樣偏重曾進 Top 20 且長期參賽者 |
| [Untangling Tennis / Kim Albrecht](https://tennis.kimalbrecht.com/) | 多維散布圖、職涯趨勢、排名與人氣（Wikipedia 瀏覽）連動 | 高（績效）、低（人氣） | 可實作排名／比賽／Elo 互動比較；人氣需額外資料來源、識別連結及授權核對 |
| [CMU：Serving Up Visualizations: Examining Tennis Data](https://www.stat.cmu.edu/capstoneresearch/spring2021/315files/team13.html) | 2016 ATP 3,004 場／49 欄；身高、年齡、左右手、ACE、場地與競爭程度研究 | 很高 | 最適合做第一版散布圖、場地／發球研究；本站會改為 ATP + WTA、跨年份與分母標準化 |
| [Tennis Abstract](https://www.tennisabstract.com/) | 球員檔案、Elo、Match Charting Project、發球／回球、逐拍統計 | 高（match-level）、低（逐拍） | 先擴充本站已存在的 Elo、H2H、ACE、一發得分率；逐拍項目需另取資料和核對授權 |

### 2.1 額外搜尋的案例

| 來源 | 參考重點 | 本站決策 |
| --- | --- | --- |
| [sorukumar / tennis-analytics](https://github.com/sorukumar/tennis-analytics) | D3.js、ECharts 的互動敘事、長期成就比較、Rivalry Network | 作資料故事與視覺元件參考；不直接引用其研究排名結果 |
| [Indiana University：Tennis Prestige](https://tennisprestige.luddy.indiana.edu/about) | 以球員對戰網路計算 Prestige/PageRank 及職涯趨勢 | 網路圖可列後期；目前只有 Elo，不應把 Prestige 與 Elo 混稱 |
| [jaygshah / Interactive-Visualizations-Tennis-Data](https://github.com/jaygshah/Interactive-Visualizations-Tennis-Data) | 氣泡 Hover 與雷達圖互動連動 | 可借鏡聯動交互設計；雷達維度與比例需明確定義 |
| [Tennis Abstract：MCP Return Leaders](https://www.tennisabstract.com/reports/mcp_leaders_return_men_last52.html) | 指標字典、最低已標記場數、資料覆蓋與對手選擇偏差警示 | 每張本站圖至少揭露有效 N、樣本資格及局限；MCP 逐拍數據本站尚未匯入 |
| [Jeff Sackmann：Match Charting Project](https://github.com/JeffSackmann/tennis_MatchChartingProject) | 使用者逐分／逐拍標記資料 | 未納入本階段；未取得網球場落點的可靠幾何座標，不做假 3D 軌跡 |

**推薦採用順序**：CMU（研究題目）→ Untangling Tennis（互動）→ DataScience+（圖表選型）→ Tennis Abstract（指標嚴謹性）。這是專案工程優先順序，不是外站客觀排名。

## 3. 本機資料可行性：已驗證的數字

### 3.1 本輪只讀查詢的觀測結果

- 查詢時間：2026-10-30；目標：本機 PostgreSQL 16 `tennis_candidate`，不是正式線上環境。
- 快照版本：`83733587353df8a41f2fd4f516147d5aa83f5a8d`；ranking 最後觀測日：**2026-06-08**，不代表 2026 全年完整。
- 已保存球員 137,483；rankings 5,573,027；matches 361,571；`analytics_matches` 可展示 288,940；其中 `eligible=true` 286,774。
- 「樣本資格」基於既有 stats-v1（docs/14、V12）排除未出賽、判負、不合理比分及影響個人統計的 OPEN 品質問題，已實際比賽且帶得分的 RET 可以納入；**不是完整職涯勝率樣本**。

| 指標 | ATP | WTA | 查詢語意 |
| --- | ---: | ---: | --- |
| 球員總數 | 66,912 | 70,571 | `players` |
| 身高 140～230 cm | 4,153 | 1,343 | 此處僅為本輪探索性範圍；**不是**正式資料品質規則 |
| 非空生日 | 48,171 | 26,408 | `dob IS NOT NULL`，未逐筆對照比賽 |
| 明確 R／L 手 | 18,263 | 7,344 | `hand IN ('R','L')`；其他代碼不擅自歸類 |
| 可展示比賽 | 199,059 | 89,881 | `analytics_matches` |
| stats-v1 合格比賽 | 197,404 | 89,370 | `eligible=true` |
| 合格且雙方 ACE／發球分非空、正分母 | 101,713 | 47,479 | **初步欄位完整性篩查**，未再套 SERVE_STATS 品質排除 |
| 合格且雙方年齡為正 | 192,394 | 89,045 | **初步欄位完整性篩查**，未再套 AGE_ANALYSIS 品質排除 |
| 最新一期排名 Top 100 身高 140～230 cm | 99/100 | 52/100 | 2026-06-08 的排名觀測 |

**關鍵結論**：不適合直接對 137,483 名球員宣稱身高與勝率的整體關係；應先用指定排名日期的 Top N／樣本完整群體，明確展示母體、缺值與篩選造成的選擇偏差。身高值 140～230 cm 只是探索查詢門檻，正式實作必須引用現有品質旗標並經資料規格確認，不能私自修改既有資料。

### 3.2 現有程式碼與可重用資產

| 資產 | 已有實作 | Phase 11 策略 |
| --- | --- | --- |
| `players`、`rankings`、`matches` | V1～V3 Migration，ATP/WTA 複合識別 | 避免姓名 Join、禁止把 ATP/WTA 視為個人已證實性別 |
| `v_display_matches`、`v_stat_matches` | V12 | 繼承 stats-v1 預設資格，不重算另一套隱性勝率 |
| `analytics_matches`、`analytics_events` | V18 讀模型 | 聚合查詢的起點；新欄位／索引再以新 Migration 加 |
| Elo 與排名歷史 | 已有 API 與 SVG 圖表 | 保留既有版本／缺值與反轉排名軸規則 |
| `frontend/src/features/LineChart.tsx` | 手寫簡單 SVG 折線 | 保留既有頁面，新建共用互動圖表模組 |
| React Query、i18n、角色／速率限制 | 已存在 | 沿用查詢取消、快取、雙語、權限界線 |

## 4. 圖表功能優先級與資料單位

| 視覺化 | 圖形與軸 | 正確觀測單位 | 優先級 | 主要注意事項 |
| --- | --- | --- | --- | --- |
| 身高 vs 指定日排名 | Scatter：X 身高、Y 名次（反轉） | 同巡迴同日期的一人一點 | P0 | Top N、有效身高、不可將週排名當獨立人 |
| 身高／年齡分布 | Histogram：連續變數分箱 | 球員或指定窗口的一人一觀測 | P0 | 等距分箱、空值、樣本數及選樣範圍 |
| 年齡 vs 勝率 | Line + Wilson CI：X 比賽當時年齡、Y 勝率 | 合格 player-match 兩視角，依年齡分組聚合 | P0 | 年齡來源品質、球員重複測量、年齡與年代偏差 |
| 身高 vs ACE 率 | Scatter：X 身高、Y ACE / 發球分 | 球員×年份／場地聚合 | P0 | 雙方投球次數分母、SERVE_STATS、最小發球分數 |
| 場地／年份績效 | Heatmap：球員×場地、年份×場地 | 分組聚合 | P1 | 樣本不足標示、配色可辨識 |
| 左／右手與表現 | Box plot / Grouped dot / Scatter color | 球員或比賽分組 | P1 | R/L/U/A/NULL、不同實力對手混雜 |
| 多維氣泡圖 | X 身高、Y ACE 率、color 巡迴、size 樣本量 | 球員聚合 | P1 | 大小採樣本量而非原始勝場、遮蓋與圖例 |
| 指標相關矩陣 | Correlation matrix，附 pairwise N | 球員×賽季聚合 | P2 | 不同分母缺失與重複球員、探索性多重檢定 |
| 大量歷史散點 | Hexbin / Density | 需先明確定義球員週、球員賽季或比賽 | P2 | 重疊、聚合造成的資訊損失 |
| 3D 球場與球路 | 球場落點、回合路徑 | 單次擊球空間座標 | 延後 | 目前不存在可用軌跡資料，禁止推測 |

## 5. 優先研究問題與統計規格

| 研究問題 | 建議分析 | 限制與可能偏差 |
| --- | --- | --- |
| 身高是否與排名相關？ | 同一排名日期 Top N，Spearman 相關；名次數值愈低代表表現較佳 | 不能用同一球員多週排名膨脹 N，避免缺身高球員被默默丟棄 |
| 身高是否與發球表現相關？ | 同球員×年度×場地 `SUM(ace)/SUM(svpt)`，展示總發球分 | 高個子球員可能有不同對手與賽事樣本；ACE/發球分不等同「每個發球局 ACE」 |
| 哪個年齡層觀察勝率較高？ | `winner_age/loser_age` 形成正／負結果，逐年齡分組，Wilson 95% CI | 資料只涵蓋已納入賽事，不能稱為全生涯巔峰；年齡為賽事日期近似值 |
| 左手是否有優勢？ | R/L × 對手 R/L 的對戰組合，控制年代、場地、對手排名／賽前 Elo | 球員數／出賽數都不均，未知持拍手不可推測 |
| 發球與取勝的關係？ | 勝負雙視角，依 ACE、雙誤、一發得分率分層；先做描述性分析 | 比賽內發球數據是賽後資料，不能冒充賽前可得預測特徵 |
| 場地是否影響特定球員？ | 同一球員跨場地勝率、Elo 及樣本數；分年份 | 場地混雜賽事級別及對手強度，原始比賽日期不是每場實際開打日 |

### 5.1 指標公式與觀測語意（擬議，開發前須定版）

- `match_win_rate = won / (won + lost)`：以同一 stats-v1 有效條件、同一巡迴與時間窗的 player-match 觀測為分母；零場則為 null。
- `ace_rate = SUM(ace) / SUM(svpt)`；`double_fault_rate = SUM(df) / SUM(svpt)`；`first_serve_points_won = SUM(1st_won) / SUM(1st_in)`，只採各欄位必要關係有效且無 SERVE_STATS 影響的樣本。逐樣本百分比的平均值**不等於**分子分母加總後的比率。
- 勝率的 95% Wilson 信賴區間可沿用現有 H2H 定義；對球員重複比賽做整體推論時另評估 cluster bootstrap，不能把普通比賽級 Wilson CI 當成獨立球員的因果證據。
- 比賽整理須由每場生成 winner/loser 各一筆**邏輯視角**；不因此在原資料表新增重複的比賽。
- 指定日期排名只用**精確該日期**的來源觀測，無資料就回空，不使用未揭露的 forward-fill。排名 Y 軸反轉、名次小為佳，積分空值不得當 0。
- 性別：資料只有 `tour=ATP/WTA`，不是球員性別欄位。UI 標「巡迴」，不得標示為已驗證個人性別；若未來要做性別分析，先定義獨立來源。
- 年齡：優先使用比賽來源的 winner_age／loser_age，依 AGE_ANALYSIS 過濾；不直接用今日年齡套歷史勝率。
- 缺值和品質：missing、invalid、not applicable 分開記錄；樣本數、排除原因、品質版本應與每次 API 回應綁定。

## 6. 可重現查核方式（唯讀）

查詢基準與結果來自 2026-10-30 的既有本機資料檢查。下列為可重現的**唯讀**摘要 SQL，不包含資料匯入或任何 DDL；操作前由開發者核對候選 DB 連線。

~~~sql
SELECT tour,
  COUNT(*) AS players,
  COUNT(*) FILTER (WHERE height_cm BETWEEN 140 AND 230) AS exploratory_height,
  COUNT(*) FILTER (WHERE dob IS NOT NULL) AS dob_known,
  COUNT(*) FILTER (WHERE hand IN ('R','L')) AS hand_known
FROM players GROUP BY tour ORDER BY tour;

SELECT tour,
  COUNT(*) AS displayed,
  COUNT(*) FILTER (WHERE eligible) AS stats_eligible,
  COUNT(*) FILTER (WHERE eligible AND w_svpt > 0 AND l_svpt > 0
    AND w_ace IS NOT NULL AND l_ace IS NOT NULL) AS ace_columns_present,
  COUNT(*) FILTER (WHERE eligible AND winner_age > 0 AND loser_age > 0) AS ages_present
FROM analytics_matches GROUP BY tour ORDER BY tour;

SELECT tour, MAX(ranking_date) AS latest, COUNT(*) AS observations
FROM rankings GROUP BY tour ORDER BY tour;
~~~

注意：第一段的 140～230 僅供核對先前探索數字，不能作為產品正式過濾規則；第二段的 ACE 和年齡欄位只是完整性初篩，**尚未**核算該指標完整的 quality_issue_impacts 排除與雙視角分母。

## 7. 資料品質、效能、合規與交付原則

1. **資料單位明確**：分清 player、player-season、player-match、ranking observation；禁止把數百萬筆排名週紀錄誤認為數百萬名球員。
2. **來源可回溯**：回應附 snapshot SHA、stat eligibility / derived metric version、分析涵蓋日；來源更新後作版本化重建，沿用 docs/15 更新候選庫與回滾策略。
3. **母體與時間完整性**：2026 只到快照最新日期，WTA 部分歷史級別 REVIEW 不納入預設比賽分析；圖表顯示「納入範圍內」而非「完整生涯」。
4. **觀察與推論分開**：趨勢線、相關係數、CI 不等於因果；對手強度、年代、巡迴、場地是潛在混雜；多重比較與樣本篩選需要揭露。
5. **1 GB 部署預算**：重用 V18 讀模型、預先彙總並限制結果筆數；禁止每次拖動篩選器都全表掃描 557 萬筆排名。新資料模型只在 Phase 11.1 指標定版後以 V19+ migration 增加。
6. **使用外部參考需正確標示**：只借鑑問題與互動模式，自行重繪；來源網站圖表與外部數值未取得重製授權，不複製圖片、品牌風格或他人結果。
7. **開發狀態**：本文件只有需求、研究和既有資料查詢結果；尚未新增 API、圖表套件、migration、UI 或部署。詳細工作包與 DoD 見 docs/17。

## 8. 下一步

先依 [17-visualization-roadmap.md](17-visualization-roadmap.md) 完成 **Phase 11.1 資料契約與指標規格**，確認身高品質範圍、觀測單位、賽前／賽後分離及樣本資格，再交付 P0 散布圖和直方圖。正式對外部署仍屬未完成的 **後續交付 Phase 10**，不可因視覺化文件就視為已上線。
