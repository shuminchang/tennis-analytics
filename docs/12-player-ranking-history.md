# 球員排名歷史

目前分支：feat/player-ranking-history，由已提交球員詳情的 de26344 建立。
狀態：排名匯入器、API 與前端已實作；本輪使用人工來源及獨立測試容器驗收。
**尚未向 tennis_candidate 匯入真實排名，尚未提交本功能。**

## 資料與交易

- V11 為 import_runs 增加 data_kind，既有批次預設 PLAYERS；排名明確寫入 RANKINGS。
- 球員重試只比對 PLAYERS 成功批次，不受後續排名批次影響。
- 排名入口先驗證不可變封存並登錄全部來源，要求同快照球員已成功匯入；兩巡迴排名檔均須存在。
- 本次快照的所有排名檔皆會處理；不只選 current，也不憑檔名當作最新日期。
- Java 串流讀取，每 1,000 列以 JDBC batch 寫入 PostgreSQL 暫存表；CLI 啟用 reWriteBatchedInserts，避免每列單獨傳輸。
- 正常列在暫存表保存轉換後的欄位與來源位置；解析異常列另保留 raw JSON。永久原值仍由不可變 CSV 與雜湊追溯。
- 在資料庫依 (tour, ranking_date, player_id) 分組，跨檔檢查重複；不將五百多萬列載入 Java 集合。
- 正常重複只寫一筆 rankings，但每個來源位置都寫入 ranking_sources。
- RUNNING 狀態先提交；暫存、隔離紀錄、正式排名、來源對照與 SUCCEEDED 為同一筆交易。失敗全數回滾，再記 FAILED。
- 暫存表 ON COMMIT DROP，回滾時也會移除。本設計是「分批送入、一筆交易發布」，不是每 1,000 列分別提交；資料庫仍需足夠暫存磁碟、WAL 與儲存空間。
- 同快照、同規則雜湊及同 importer SHA 成功重試回傳既有結果；不覆寫不同版本／快照。已存在排名但無相符成功批次時拒絕。
- 與球員匯入共用 advisory lock；有 RUNNING 批次時拒絕，程序中斷須先查核，不自動接管或標成成功。

## 轉換與隔離

規則版本位於 backend/src/main/resources/ingestion/ranking-rules-v1.txt，雜湊寫入批次三個規則欄位。

- 日期必須為八位 ASCII 數字及有效正年份日期。
- player、rank 必須為 PostgreSQL integer 範圍內的正整數。
- points、WTA tours 可空，但有值時必須是非負 integer；0 與 NULL 分開。ATP 不保存 tours。
- 無法解析的列隔離；若仍有合法鍵，同鍵其他列也隔離，避免略過壞值而誤判內容一致。
- rank、points、tours 任一不同即整組隔離，包含 NULL 對有效值、只有 tours 不同等情況。
- 找不到同巡迴、同快照已發布球員時隔離，不虛構外鍵。
- 每筆隔離來源都有 quality_issues 與 RANKING_HISTORY impact；保留原始來源位置及證據。
- 不插補缺少的日期，不以較高名次或檔案順序選值。

球員是否可對外顯示仍依既有 PLAYER_SEARCH 規則；占位球員的排名 API 回應 404，不外露個人資料。

## API

`GET /tennis/api/v1/players/{tour}/{playerId}/rankings?page=0&size=20`

回應：

```json
{
  "items": [{"date": "2024-01-15", "rank": 3, "points": null}],
  "total": 1,
  "page": 0,
  "size": 20,
  "available": true
}
```

- 依來源日期由新到舊排序，頁碼 0～10000、每頁 1～100。
- available=false 表示尚無成功排名匯入；available=true 且 total=0 表示這位球員沒有可顯示紀錄。兩者都不能推論為「從未獲得排名」。
- 球員不存在或不可見為 404；參數無效為 400／INVALID_RANKING_QUERY，資料存取錯誤為 503／RANKINGS_UNAVAILABLE。
- Repository 只讀有成功排名來源批次對照的正式列；Service 在唯讀 REPEATABLE_READ 交易中檢查球員可見性、總數與當頁資料，逾時 10 秒。
- 目前不提供生涯最高排名、目前世界排名或插補後曲線，避免超出來源涵蓋範圍。

## 頁面

詳情頁增加排名歷史表格，支援雙語、分頁、重新整理、空狀態、尚未匯入、失敗重試及越界復原。
`rankingPage` 保存於 URL；返回搜尋會移除該參數，保留原搜尋 q／tour／size／page。
積分 0 顯示為 0，NULL 顯示未提供；WTA tours 不顯示為完整參賽次數。
沿用專案 frontend-design 技能與既有配色、來源授權及頁尾署名。

## 驗收

專案根目錄：

```bash
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml clean verify
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
LD_LIBRARY_PATH=/tmp/tennis-browser-libs/extracted/usr/lib/x86_64-linux-gnu python3 scripts/test/player_search_e2e.py
```

後端 382 案、前端 29 案通過；lint、前端正式建置與 JAR 打包成功。瀏覽器 4 案通過，人工 Git/CSV 經封存、球員與排名 CLI、真實 PostgreSQL/API 至頁面完整串接。手機截圖已目視檢查。
測試包括跨檔一致重複、rank／tours 衝突、缺值／零值、壞列同鍵整組隔離、缺少球員、失敗回滾重試、版本拒絕、超過 2,000 列跨 batch、V6 舊資料升級保留及既有球員重試。
測試結果不代表五百多萬筆真實排名已匯入，也不代表已完成全量效能測量。

## 真實資料匯入的下一步

先提交本功能，再從該 commit 打包；不能將尚未包含此匯入器的舊 HEAD 當成 importer SHA。
在已完成同快照球員匯入的本機候選資料庫執行，其他資料庫不在本輪操作範圍。
專案根目錄，以下為後續操作說明，**尚未執行**：

```bash
./backend/mvnw -f backend/pom.xml -DskipTests package
(
  set -euo pipefail
  source .env
  export DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5433}/tennis_candidate"
  export DB_USERNAME="${POSTGRES_USER:?請設定 POSTGRES_USER}"
  export DB_PASSWORD="${POSTGRES_PASSWORD:?請設定 POSTGRES_PASSWORD}"
  export IMPORT_TARGET=candidate
  backend/import-rankings \
    /home/shumin/tennis-sackmann-archive-full \
    https://github.com/Aneeshers/tennis-sackmann-archive \
    83733587353df8a41f2fd4f516147d5aa83f5a8d \
    .local/source-snapshots/83733587353df8a41f2fd4f516147d5aa83f5a8d \
    "$(git rev-parse HEAD)"
)
```

會自動套用 V11；不要停止執行中的匯入、不要同時啟動另一個匯入器。
完成後保存 run、imported（正式鍵數）、sourceRecords（接受來源列數）、quarantined（隔離來源列數）與 reused。
以 SQL 核對批次、兩巡迴筆數、衝突隔離與 Federer 排名，再重啟開發後端驗證頁面。歷史探索的預期筆數不能當作此次匯入結果。
