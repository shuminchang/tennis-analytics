# 發布前工程驗收

本文件只記錄本機準備與驗收，不表示已部署 Linode，也不表示 GitHub 上的 CI 已執行。
使用者已指定先完成本機備份還原，外部目的地待定。

## 已實作的工程界線

- V18 保存 analytics_matches／analytics_events 讀模型與索引，將大範圍品質／分類判斷移至更新時；MatchImportService 成功發布時同交易刷新。
- 手動品質修正後用 backend/refresh-analytics 或管理員「刷新分析」，再重建 Elo。快取在刷新交易提交後清除，其他 CLI 程序中的快取最多 60 秒過期；更新資料後重啟 Web 應用確保立即切換。
- Caffeine 只快取共用摘要、分析、賽事、榜單與 H2H，每個快取最多 256 筆、60 秒有效；帳號／收藏／Session 不作共用快取。
- Hikari 最多 5、最少 1 連線，Web SQL 5 秒，Tomcat 20 threads。匯入／模型 CLI 明確採無查詢時間上限，不能拿 Web 的 5 秒直接執行全量批次。
- 每 IP 每分鐘 240 次一般請求、20 次登入，記憶體狀態最多 10,000 個鍵；健康檢查不計入。登入帳號仍另有 5 次錯誤鎖定 15 分鐘。
- production profile 強制 Secure Session Cookie；應用只綁 localhost。轉送標頭只應由可信 Apache 寫入，正式反向代理須清除外來 Forwarded／X-Forwarded-* 後重設。
- 字型改成本機打包的 DM Sans／Noto Sans TC（OFL 授權附於 /tennis/font-licenses.txt），移除外部字型服務，補齊 /about 的來源／方法／帳號資料說明。
- 帳號管理頁可檢查批次狀態、清快取、刷新分析與重建模型，僅 ADMIN 能操作。

## Docker 中斷與驗證日誌

工作目錄專案根目錄：

```bash
scripts/test/backend-check.sh clean verify
```

先以 15 秒上限檢查 Docker；無連線回傳 75 且不執行測試。Maven 最多 20 分鐘，日誌保存 .local/verification/。
端到端程式也將日誌保存在此目錄；原始人工 repo／archive 仍會清理。
Docker／WSL 重新啟動後，不能把 RUNNING 視為成功，也不能直接再開另一個匯入器。先確認沒有舊 Java 程序與資料庫連線，再核對來源對照／正式資料是否已回滾，依已有復原紀錄處理失敗批次。Elo 同理，不自動接管 RUNNING。

## CI 與本機對應

.github/workflows/ci.yml 配置 Java 21、.nvmrc Node、Maven Wrapper verify、npm ci／lint／test／build、Playwright Chromium 與完整人工來源端到端驗證。
參考官方 [setup-java](https://github.com/actions/setup-java)、[setup-node](https://github.com/actions/setup-node)；Dependabot 以每週 PR 追蹤 Maven／npm／Actions，未啟用自動合併。
本輪只執行本機對應檢查；遠端 workflow 需使用者之後推送才會執行，不能標成 GitHub CI 已通過。

## 有界 JVM 與效能

先由操作者設定 DB_URL／DB_USERNAME／DB_PASSWORD，再執行：

```bash
SERVER_PORT=8082 scripts/ops/run-local-budget.sh
python3 scripts/ops/benchmark.py --authenticated
```

JVM -Xms64m -Xmx256m、Metaspace 上限 128m、Xss512k、ActiveProcessorCount=1。
ActiveProcessorCount 是 JVM 單核心設定，不是整台主機的 cgroup CPU 上限；本機 PostgreSQL／OS／Apache 資源與 Linode 不同，不得據此宣稱完整 1 GB 正式主機已驗收。
benchmark 對每個端點記冷查詢及 20 次暖查詢，暖 p95 目標一般 <500ms、H2H <1000ms；不隱藏冷查詢延遲。
--authenticated 僅在指定本機 Compose 庫建立隨機 QA 進階帳號，密碼只存在程序記憶體／stdin，不列於命令參數或輸出；完成後登出並刪除帳號。QA metadata 檔保存名稱與清理狀態，若中斷須人工核對後刪除此工具建立的帳號，勿刪真實帳號。登入稽核保留。

## 備份與實際還原

```bash
python3 scripts/ops/backup_restore.py --database tennis_candidate --restore-check
```

- 以匯出的 PostgreSQL REPEATABLE READ snapshot 同時取得來源計數與 pg_dump -Fc，避免備份期間有寫入導致計數不一致。
- 備份與 manifest 保存於 .local/backups/<UTC時間-隨機值>/，目錄 700、檔案 600，Git 忽略；包含密碼雜湊的備份仍須保密。
- 計算 SHA-256，還原到工具新建的隨機 tennis_restore_... 空資料庫，核對所有應用表／讀模型的筆數及約束驗證狀態。
- 只刪除此工具確實建立的還原庫，從不覆寫或刪除來源候選庫。manifest 記錄 RUNNING／FAILED／SUCCEEDED 與階段；若 Docker 中斷，partial 檔不能當成已驗證備份。
- 這是本機還原演練；同機備份無法取代異地備份。外部目的地依使用者要求保留待定，不自動上傳。
- 後續建議每日排程備份、保留至少 7 個每日與 4 個每週版本，刪除舊備份前確認有可還原的外部副本；本輪未自動刪除歷史備份。

依 [PostgreSQL 16 pg_dump](https://www.postgresql.org/docs/16/app-pgdump.html) 的同步快照及 custom archive 機制實作。

## 更新候選資料庫

```bash
scripts/ops/update-candidate.sh tennis_candidate_new_snapshot \
  /home/shumin/tennis-sackmann-archive-full \
  https://github.com/Aneeshers/tennis-sackmann-archive \
  <完整來源commit> <該快照封存路徑>
```

要求程式已提交，從該版本打包並保存 importer SHA。新建資料庫若已存在就失敗，不覆寫；依序 players→matches→rankings→刷新分析→Elo。
此腳本不切換正在運行的 Web 連線。完成後核對數量、品質、API，再將開發連線改成新候選庫並重啟；原候選保留以便回復。失敗保留候選與 FAILED 紀錄供查核。
本輪沒有新快照，因此不重跑真實 CSV 探索或重新匯入五百萬筆排名。

## 第 10 階段仍需驗收

正式 Apache／HTTPS、轉送標頭與 CSP、systemd／日誌輪替、獨立 app／import DB 權限與備份排程，及整台 Nanode 1 GB 記憶體／磁碟實測。
本輪不安裝正式系統設定、不推送或部署；下方只補本機已執行結果。


## 2026-10-30 本機實際結果

本輪以既有來源快照 83733587353df8a41f2fd4f516147d5aa83f5a8d 與本機 tennis_candidate 驗收，不重新匯入或改寫原始 CSV。

- V12～V18 已套用；球員 137,483、排名 5,573,027、比賽 361,571、展示讀模型 288,940 筆。
- 真實 elo-v1 批次 1 成功：合格比賽 286,774、歷史評分 1,140,032、各球員／分類目前評分 34,631 筆。計算使用 256 MiB Java heap，約 33 秒；不是完整生涯或實際逐場日期的宣稱。
- 全套後端 413 案、前端 41 案、lint／build、人工來源完整端到端 9 案通過。後續關閉 Web SQL JIT 與刷新後 ANALYZE 的修正，再驗 AnalysisTest／AccountTest／RateLimitTest，共 4 案通過並重新打包。
- 真實備份大小 107,707,958 bytes，SHA-256：84f887815df57bc162b70082144bc3cea63e1b29dba6fbdd03c0d8129e835523。
- 已實際還原隨機空資料庫，保存表／讀模型的筆數與已驗證約束核對通過；連同 Flyway 歷史共 22 項（詳見報告計數），還原庫已清除，來源庫保留。備份仍在 .local/backups/20261029T170402Z-6f9e68/，未提交資料檔。
- 備份中的 app_users／favorites 為 0；效能工具之後建立的隨機 QA 帳號已刪除，登入稽核保留。因此後續 audit_log 計數可以與備份當下不同。

可提交的摘要見 [驗證報告](reports/2026-10-30-local-verification.json)；原始日誌、備份及畫面保存在 Git 忽略的 .local/verification/ 與 .local/backups/。遠端 CI 只完成配置，未推送執行。

### 冷查詢修正及測量界線

原本在單核心 JVM、未限制 PostgreSQL 的本機測量，分析冷查詢約 944 ms、暖 p95 約 6 ms；EXPLAIN 顯示短查詢承擔 JIT 編譯成本，因此 Web 連線關閉 jit，分析讀模型刷新後立即 ANALYZE。
該次暖 p95：搜尋 141 ms、摘要 9 ms、分析 7 ms、賽事 11 ms、排名 12 ms、H2H 9 ms、Elo 47 ms（取整上捨入）；此測量並行還原且不是正式主機容量證明。

為避免將 Java heap 上限誤當整台主機驗收，另提供獨立測試：

```bash
# 工作目錄：/home/shumin/tennis-analytics
python3 scripts/ops/budget_stack.py \
  .local/backups/20261029T170402Z-6f9e68/manifest.json
```

驗證備份雜湊後，還原至工具新建的 PostgreSQL 容器，連同 Java 與 Apache 分別限制 320／448／64 MiB、0.5／0.4／0.1 CPU，總服務上限 832 MiB／1 CPU，透過 Apache 執行真實資料的匿名及進階 API 測量。PostgreSQL 使用 shared_buffers=32MB、work_mem=2MB、maintenance_work_mem=16MB；不改原 Compose 設定。報告保留冷查詢與 20 次暖查詢 p95，最後僅清理自己的容器與網路。
主機 OS、Docker daemon 與 Linode 其他網站的資源不在容器上限內；仍須第 10 階段在目標主機驗收。這是本機 HTTP 測試，不代表 HTTPS／Secure cookie／正式 Apache 設定已部署。

打包前停止正在使用 target JAR 的暫時服務，或先複製 JAR 至固定驗證路徑，避免 Maven 重寫運行中的檔案造成延遲載入類別失敗。


### 受限服務堆疊結果

2026-10-30 執行成功，真實備份在限制內還原，經 Apache 測量（毫秒）：

| 端點 | 首次 | 暖 p95（20 次） |
| --- | ---: | ---: |
| 健康 | 67.26 | 83.56 |
| 球員搜尋 | 384.58 | 378.97 |
| 摘要 | 58.21 | 86.24 |
| 場地分析 | 177.83 | 75.96 |
| 賽事列表 | 98.65 | 87.90 |
| 排名榜 | 409.66 | 82.79 |
| H2H | 119.38 | 82.61 |
| Elo 圖表 | 1593.25 | 200.19 |

一般暖 p95 <500ms、H2H <1000ms 均通過；Elo 首次載入約 1.59 秒，不能宣稱所有冷查詢小於 500ms。這是順序請求，不是多使用者併發容量測試；正式主機負載與 TLS 仍待第 10 階段。
測量後記憶體快照為 PostgreSQL 約 177.7 MiB、Java 198.1 MiB、Apache 3.047 MiB；不是全程峰值，實際硬上限以容器設定為準。測試容器／網路與 QA 帳號已清理，原候選庫未改動。

真實瀏覽器另驗 Roger Federer 頁面：1,523 場有效樣本／1,248 勝／275 負、排名圖及來源比賽紀錄可顯示，無 pageerror。畫面 .local/verification/real-federer.png。
本輪第 1～9 階段在上述本機範圍完成；外部備份保留待定。部署、推送與合併不在此次自動執行範圍。


### Docker 中斷時的備份狀態

快照回應以 60 秒上限讀取；Docker 清理逾時仍保存 manifest 的結果與 cleanup_pending，不會因 dropdb 失聯遺失已驗證結果。cleanup_pending=true 只表示還有工具建立的還原庫／容器需查核，不能直接刪除其他庫。
`python3 scripts/test/ops_checks.py` 的 4 案通過：雙行快照緩衝、回應逾時、dump 失敗不產生已驗證檔、還原成功但清理失聯仍保留結果。這些是無 Docker 的失敗模擬；另已對真實 PostgreSQL 驗證有界快照讀取（players=137483）。CI 已加入這 4 案。
