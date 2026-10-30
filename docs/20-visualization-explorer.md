# Phase 11.3：Data Explorer MVP

狀態：**頁面已實作，本機驗收完成**（2026-10-31）。分支 feat/visualization-lab。使用專案 frontend-design 技能，沿用紙色／深綠、既有字型、雙語與 Created By Deerflow 頁尾。
前置：[11.1 契約](18-visualization-contract.md)、[11.2 API](19-visualization-api.md)。本輪沒有 migration、來源匯入或正式部署；11.4 年齡／勝率等擴充未實作。

## 開啟與操作

啟動原有 VS Code `dev: start all`，瀏覽 `/tennis/visualizations`；主導覽新增「資料探索／Explorer」。例如：

```text
http://127.0.0.1:5173/tennis/visualizations?tour=ATP&rankingDate=2026-06-08&topN=100&x=height_cm&y=rank&color=hand&bins=20
```

1. 選 ATP／WTA、精確排名日期與 Top N（名次門檻，保留同名次）。日期留空才使用來源最新日，畫面顯示實際解析日期；指定空日期不補前週。
2. 選 X／Y：身高↔名次或身高↔積分。更換一軸時維持合法配對，可按「交換座標軸」。慣用手分色或單色、直方圖箱數 1..50。
3. 按「套用條件」才查詢；URL 記錄所有條件，重新整理、瀏覽器前後頁會還原。非法／重複／未知 query 拒絕，不送 API，可重設。
4. 散布圖 hover 顯示姓名、巡迴、ID、X/Y、分組與有效 N，點選進入既有球員頁。名次軸反轉，最小名次 1；縮放只改散布圖可視範圍，不改母體／資料表／直方圖。
5. 「複製此快照連結」會把留空日期解析成明確來源日期再分享；沒有 clipboard 權限時仍提供可選取網址。
6. 展開資料表，使用鍵盤開球員連結；散點資料每頁 50 筆，直方圖每箱 count 另列。小螢幕可在標示的捲動區左右移動。

散布圖顯示配對有效數；直方圖獨立使用同母體的有效身高，因此更換積分軸遇到缺值時，兩圖有效數可能不同。兩處各自揭露母體、有效／排除與原因；0 不當成缺值。有效 N<5 顯示低樣本提示。
來源區列快照、規則版本、批次、來源連結與 CC BY-NC-SA 4.0，說明身高不是每個歷史日期的即時量測、巡迴不是已驗證性別、關聯不等於因果或完整生涯。

## 圖表套件 spike 與實際 bundle

只新增 **echarts 6.1.0**（精確版本＋package-lock），未安裝 Recharts 或 echarts-for-react。React 包裝直接管理 init／setOption／resize／dispose，兼容現有 React 19 SPA；不改框架、不新增 SSR。
依 [官方模組匯入文件](https://apache.github.io/echarts-handbook/en/basics/import/) 使用 echarts/core、Scatter／Bar、Grid／Tooltip／Legend／Aria／DataZoomSlider 與 CanvasRenderer；不匯入完整圖表集合。Canvas 適用有界最多 2000 點，DOM 表格提供鍵盤替代；保留舊 SVG LineChart。
Tooltip 採 richText，來源姓名不插入 HTML；依 [官方安全指引](https://echarts.apache.org/handbook/en/best-practices/security/)，不使用 HTML formatter／dataView。外部來源連結僅允許 http(s)，不接受 javascript URL。

可重現 spike：

```bash
# 工作目錄：/home/shumin/tennis-analytics
node scripts/test/chart_bundle_spike.mjs
```

Vite ES library probe：模組版 749,287 bytes／gzip 212,872；全量版 1,512,888／gzip 428,475，約減少一半。這是相同 library probe，不是正式 app chunk。
正式 build 將 ExplorerPage 與 ExplorerCharts 分開 lazy import：頁面 15,829 bytes／gzip 6,273，圖表 553,130 bytes／gzip 184,498；未進探索頁不下載 ECharts。主入口 449,690 bytes／gzip 135,364（gzip 以 node:zlib 預設計算，與 Vite console 的快速估計略異）。精確值見 [機器報告](reports/2026-10-31-visualization-explorer.json)。
Vite 仍對圖表 chunk >500 KB 提示；沒有提高門檻或隱藏警告。此切片接受隔離後的下載成本，11.7 再做實際網路、併發與整體資源驗收，不將本機 build 當成效能預算全部通過。

## 實作結構與一致性

`frontend/src/features/visualizations/`：

| 檔案 | 用途 |
| --- | --- |
| model.ts | API types、URL 白名單、日期／上限、分組／單位、語意來源比較、安全連結 |
| ExplorerPage.tsx | 三個 React Query、來源一致性、分享／錯誤／空資料、lazy 圖表邊界 |
| FilterPanel.tsx | 本地草稿、合法座標配對與套用／重設 |
| ExplorerCharts.tsx | 模組化 Canvas、Tooltip、實際繪製 busy 狀態、縮放、事件與資源釋放 |
| SampleFootnote.tsx | 各圖獨立母體及互斥排除原因、低樣本提醒 |
| DataTable.tsx | 有效點與 bins、鍵盤連結、50 筆分頁、橫向捲動區 |
| explorer.css | 新頁面範圍樣式，保留舊頁風格 |

React Query keys 含來源版本與完整條件，staleTime=30s，consume AbortSignal；切換條件取消舊請求，不以舊點遮掩新查詢的載入。三端點來源／品質 revision、metricVersion／qualityPolicyVersion 或 resolved date 不一致時隱藏結果、提供重試，不混合快照。圖表 chunk 無法載入時仍可使用外層資料表。
ResizeObserver 隨容器調整畫布，字型完成後重畫；unmount 清除 observer／事件並 dispose，不將 ECharts 實例存為全域。尊重 prefers-reduced-motion。Canvas 有簡短替代描述，實際鍵盤操作由原生 details、表格、連結及分頁完成；沒有宣稱做過完整 WCAG 認證。
導覽修正：目前探索頁有 active 標記／頁面標題；手機 header 隨換行增高，修正原固定高度讓語言按鈕被正文擋住的問題。

## 驗證與排錯

- 前端完整 **53 個測試通過**（原 41＋新 12），lint／正式 build 成功。
- 新測試涵蓋深連結還原、X/Y 自動配對與歷史返回、非法 URL 不發請求、零樣本、重試、來源不一致、固定日期分享、雙語、取消被取代的請求、安全來源 URL。
- 真實 Git／CSV → 新建 PostgreSQL 測試容器 → 匯入 → HTTP → 瀏覽器：完整 12 流程通過（既有 9＋新增 3），補強規則版本一致性後另重驗新 3 流程通過，包含真實 Canvas Tooltip／點擊、鍵盤球員連結、分享重載、手機空日期／巡迴／語言及錯誤／混合來源重試。
- 另在現有候選庫唯讀瀏覽：ATP Top 100 有效身高 99，WTA 52；桌機／390px 手機截圖已檢視，無水平頁面溢出及 pageerror。原始資料及帳密不變。
- 後端未修改，沿用 11.2 已驗的 431 案，未重新宣稱本輪重跑完整後端。端到端仍使用實際後端 JAR。

排錯過程不當成通過：ECharts 的 use 與 React Hook lint 名稱衝突，改名 registerCharts；分享網址測試改用字串包含斷言；圖表測試原先假設單點位於中心，改等待字型／繪製後定位可見點並實際滑鼠操作。手機語言按鈕被內容遮住則修 header 高度，沒有 force click 繞過。新增 E2E --spec 後也修正 argparse 變數與既有迴圈名稱衝突。

## 重現指令與交接

工作目錄 `/home/shumin/tennis-analytics`：

```bash
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
# 需 Docker 正常、backend JAR 已依 docs/19 建置，以及 Playwright Chromium 與其系統相依。
python3 scripts/test/player_search_e2e.py
# 只重驗新頁面，避免重跑無關已通過流程：
python3 scripts/test/player_search_e2e.py --spec visualizations.spec.ts
```

本機缺少 libgbm 時，本輪僅把 libgbm1／libwayland-server0 解壓於 /tmp/tennis-browser-libs，透過 LD_LIBRARY_PATH 執行；未改系統套件。CI 原已用 playwright install --with-deps。E2E wrapper 增加 15 秒 Docker preflight；失聯視為失敗，日誌保留 .local/verification，不重置開發資料庫。
本機截圖 .local/verification/explorer-real-desktop.png、explorer-real-mobile.png；人工來源截圖 frontend/test-results/explorer-*.png。截圖／日誌均 Git 忽略，不提交個人帳號或資料庫內容。
上述為 11.3 當時驗收。後續 11.4～11.7 的年齡／發球／進階工作台與整合驗收見 [21](21-visualization-research-delivery.md)；尚未合併 main、推送或正式部署。
