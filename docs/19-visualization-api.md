# Phase 11.2：視覺化聚合 API

狀態：**已實作、測試與本機候選資料驗收**（2026-10-31）。分支 feat/visualization-lab；前端 11.3 已完成，見 [20 UI 驗收](20-visualization-explorer.md)，Phase 10 正式部署未完成。
契約：[18 visualization-v1](18-visualization-contract.md)。此切片以同日排名小母體 JOIN 球員，不新增 migration／資料預計算表，不安裝圖表套件。

## 提供的端點

共用 context path `/tennis/api`，三個 GET 均可匿名存取，沿用既有限流。參數名稱區分大小寫：

```http
GET /tennis/api/v1/visualizations/metadata?tour=ATP
GET /tennis/api/v1/visualizations/scatter?tour=ATP&rankingDate=2026-06-08&topN=100&x=height_cm&y=rank&color=hand
GET /tennis/api/v1/visualizations/histogram?tour=WTA&rankingDate=2026-06-08&topN=100&metric=height_cm&bins=20
```

- tour 必填 ATP/WTA；topN=1..1000、bins=1..50，結果母體上限 2000。Top N 指 rank<=N，保留所有同名次；超上限拒絕，不靜默截斷。
- 散布圖四組軸：身高↔排名、身高↔積分；color=hand|none，未知手為 UNKNOWN、來源 U／A 各自保留。rank 回傳 descending。
- 省略日期才解析最新可公開日期；指定空日期回 200 空母體、不補前週。無任何可見排名時 resolved date=null。
- 直方圖只處理身高；有效值 min/max 等寬分箱，[lower,upper)，最後一箱含上界。值相同則單箱 ±0.5，無有效值 domain=null、bins=[]，空箱的 count=0 保留。
- 身高 100..250 且無選定成功來源的 OPEN HEIGHT_ANALYSIS；公開母體沿用 V14 的 PLAYER_SEARCH 排除。NULL 積分不等於 0，亦不影響不使用積分的圖表。
- pair 有效數及互斥主要原因依 18 契約；先品質、再缺值、再範圍，required metric 固定排序，交換 X／Y 不改排除理由。
- 率、年齡、場地、Elo 與相關矩陣端點尚未新增；此 MVP 沒有把 player-match 資料或賽後指標混入 ranking_snapshot。

## 程式結構與來源一致性

`backend/src/main/java/com/shuminapp/tennis/visualization/`：

- VisualizationController：完整保留 query MultiValueMap，拒絕未知、重複、空白、錯誤型別與不允許的參數。
- VisualizationRequest：固定軸／指標白名單，嚴格 ISO 日期與數值邊界；不接受任意 SQL／排序／grain。
- VisualizationService：read-only REPEATABLE READ、10 秒交易上限；型別化 DTO、配對資格、直方圖與快取。
- VisualizationRepository：SQL 使用綁定參數，固定排序；SQL statement_timeout=5s、jit=off。資料最多讀 2001 筆，以偵測 2000 上限。
- VisualizationDtos：context、source、規則 SHA、axes、sample、items、domain、bins、metadata 皆為明確 Java record。
- VisualizationAdvice：只作用於新 package，400／503 固定 code/message，不回 SQL、stack trace、密碼或封存路徑。未更動舊 API 錯誤契約。

來源須有成功 PLAYERS／RANKINGS，且相關成功批次共用唯一快照；同類歷次成功批次的規則 SHA 必須相容，取最新成功 run 並要求資料對照屬於該 run。不相容、缺少來源、相關匯入 RUNNING 時回 503。FAILED 批次不當成可發布來源。
公開排名沿用 v_public_rankings，另要求本次選定來源對照；只讀來源，不依原始檔名或姓名 JOIN。metadata 與結果在同一交易快照，回傳 snapshot SHA、run IDs 及各 run 的 transform／quality／scope SHA。MVP 不讀 V18 比賽讀模型，所以 matchRunId=null，也不冒稱此端點已驗證比賽模型刷新狀態。

### 快取

CacheConfiguration 新增固定名稱 vizMetadata／vizScatter／vizHistogram，各最多 128 keys、60 秒；既有 cache 不改容量。key 包含 source／run／規則、qualityRevision、visualization-v1、grain、resolved date 及正規化參數。
每次請求先在同一 read-only 交易重新核對來源與品質 revision，再查快取；故已快取結果不能繞過來源失效。qualityRevision 是成功球員／排名批次的 issue ID、status 與 impact code 變動摘要（MD5 只作快取版本 token，不是資料完整性或安全雜湊）；來源規則本身仍使用既有 SHA-256。
管理員既有「清快取／刷新分析」會清除新增固定 cache。直接改 players／rankings 正式值而不更新來源，並非支援的發布方式；沿用另建候選庫流程。

## 驗證

完整後端 `clean verify` **431 案通過、0 skipped**；其中新 13 個 PostgreSQL／MockMvc 測試及 2 個實際 Caffeine 測試。沒有前端變更，因此未重跑既有前端與瀏覽器套件。

| 測試主題 | 證據 |
| --- | --- |
| 11.1 同一人工 fixture | 4 名球員、同日排名、身高品質：population=4、valid=1、excluded=3；單值直方圖 179.5..180.5 |
| 日期 | 精確空日期 200、不補前週；未發布較新日期不取用；無該巡迴排名回 null 日期 |
| 分母與 NULL／0 | 積分 0 保留、NULL 僅排相關軸；散布配對與直方圖各有分母，X/Y 對調理由不變 |
| 品質與識別 | PLAYER_SEARCH 先排母體、HEIGHT_ANALYSIS 及解決狀態、ATP/WTA 交疊 ID 分開 |
| 分箱 | 100／250 合法端點、內部邊界歸右箱、最大值歸末箱、空箱與單值 |
| 上限與輸入 | 同名次全入、2001 筆明確拒絕；重複／未知參數、非法日期、0 年、注入字串、非法軸與 bins 拒絕 |
| 來源 | 缺來源、混合快照、不相容規則、RUNNING 回 503；不洩漏內部路徑 |
| 真實 SQL 逾時／取消 | 另一連線鎖住 rankings，HTTP 查詢約 5 秒取消並回 503，解除鎖後可恢復；不是 mock 逾時 |
| 快取 | 實際 Caffeine 命中、容量 128／60 秒、管理清除、來源失效不能回舊值；真實 DB 品質狀態改變導致 revision 與結果更新 |

11.1 的 Python 規格 oracle 仍獨立保留，CI 加入執行。發球／年齡的手算不是此 MVP 新 API：沒有將未實作路由標為測試通過。

## 真實候選庫與效能

[本機報告](reports/2026-10-31-visualization-api.json)。同來源 SHA 83733587353df8a41f2fd4f516147d5aa83f5a8d，透過真實 HTTP 連既有 tennis_candidate：

- 2026-06-08 Top 100：ATP 母體 100、有效身高 99；WTA 母體 100、有效身高 52；散點數與直方圖 count 加總均符合 11.1 報告，排除原因加總正確。
- 每巡迴三端點，各 1 次首次＋20 次暖請求；一般暖 p95 <500ms 目標均達標。

| 端點 | ATP 首次／暖 p95 ms | WTA 首次／暖 p95 ms |
| --- | ---: | ---: |
| metadata | 338.353 / 12.167 | 21.066 / 12.903 |
| scatter | 39.890 / 14.857 | 42.017 / 10.368 |
| histogram | 32.229 / 11.582 | 14.365 / 10.082 |

SQL EXPLAIN ANALYZE BUFFERS：Top 100 查詢使用 pk_rankings、ix_ranking_sources_ranking、ix_player_sources_player、pk_players 等既有索引；100 筆、執行約 4.606 ms、規劃 3.372 ms，因此本輪無新增索引或 migration。
測量為本機循序 HTTP、Java heap 256 MiB／單處理器設定；PostgreSQL 未在這輪額外套 cgroup 上限，不代表 Apache／TLS／併發或正式 1 GB 主機驗收。11.7 仍須完成擴充後整體資源測試。

### 重現指令

工作目錄 `/home/shumin/tennis-analytics`：

```bash
scripts/test/backend-check.sh clean verify
python3 scripts/test/visualization_contract_checks.py
# 已設定 DB_* 後，以 8082 的本機驗證服務讀候選庫；避免佔用 8081。
# 本輪使用固定複製 JAR，關閉驗證程序的 Flyway，未操作使用者開發服務。
python3 scripts/test/visualization_api_smoke.py --base http://127.0.0.1:8082/tennis/api
# SQL 只針對目前已知快照的 PLAYERS=1、RANKINGS=3、2026-06-08 基準。
timeout 20s docker compose exec -T db psql -XqAt -v ON_ERROR_STOP=1 \
  -U tennis_admin -d tennis_candidate < scripts/data/visualization_api_explain.sql \
  > /tmp/visualization-api-plan.json
```

若要重現本輪臨時服務，先完成上面的 verify，再於獨立終端執行（工作目錄同上，Ctrl+C 停止）：

```bash
mkdir -p .local/verification
cp backend/target/tennis-0.0.1-SNAPSHOT.jar .local/verification/visualization-api.jar
(
  source .env
  export DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5433}/tennis_candidate"
  export DB_USERNAME="$POSTGRES_USER" DB_PASSWORD="$POSTGRES_PASSWORD"
  java -Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k \
    -XX:ActiveProcessorCount=1 -jar .local/verification/visualization-api.jar \
    --server.port=8082 --spring.flyway.enabled=false
)
```

關閉 Flyway 只適用已套用 V18 的驗證庫；正常開發仍保留 Flyway，不以此跳過新 migration。

smoke 工具限制 localhost、只發 GET、比對既有 11.1 報告；來源更新後需先更新獨立基準，不將新資料與舊數字混比。EXPLAIN SQL 強制 READ ONLY／REPEATABLE READ、statement_timeout=5s。Docker 失聯或錯誤視為未通過，不自動重置資料庫。
完整日誌保存在 .local/verification/。本輪無候選資料寫入、原始資料重匯、正式部署或推送；.vscode/settings.json 保留。
後續 **11.3 Data Explorer MVP** 已完成，見 [20 UI 驗收](20-visualization-explorer.md)；下一步為 11.4 年齡與勝率擴充。
