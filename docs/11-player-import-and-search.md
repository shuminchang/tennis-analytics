# 球員匯入與搜尋：功能交付紀錄

2026-10-27 使用者授權三個功能 commit。本文只列本輪實作與驗收，歷史記錄見完整教學手冊。

## 1. 整批來源登錄

SourceRegistrationService 公開入口先以 SourceSnapshotStore 驗證來源封存，解析實際位置，再開始資料庫交易。
在同一交易中登錄 snapshot 與全部 source_files；利用快照列鎖序列化同一快照的登錄，驗證既有檔案清單沒有多餘項目。
空清單、重複路徑、內容衝突或後續檔案失敗會回滾本次新增。既有登錄不覆寫。
登錄僅保存來源資訊，不匯入球員；輸入封存在驗證與後續使用期間必須保持不變。

## 第一步驗收

Docker 恢復後執行（工作目錄 backend）：

```bash
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw -Dtest=SourceRegistrationServiceTest test
```

6 案全部通過：冪等、後續檔案失敗回滾、內容衝突保留既有紀錄、拒絕遺漏已登錄檔案、併發登錄同 ID、來源不存在拒絕。尚未向真實資料庫登錄。第二步球員匯入實作與整套驗收接續進行。

## 2. 球員轉換與匯入

只對獨立候選資料庫執行初次完整 ATP/WTA 球員匯入，不向現有網站資料庫直接更新。
`player-rules-v1.txt` 是本階段的版本化規則說明，SHA-256 寫入 import_runs 的三種規則欄位；三者目前共用同一份球員專用規則文件。
操作者提供 importer commit SHA，作為程式版本記錄；執行前須確認 JAR 從該提交建立，工具不自行證明建置來源。

- 先驗證／登錄封存；不存在或遭竄改的封存不建立匯入批次。
- 要求 ATP/WTA 兩份球員檔；串流讀 CSV，必要 ID 無效時隔離，品質證據保留原始欄位字串。
- 選填欄位依規則轉換；異常身高保留可解析值並標記，無效日期不補假日期。
- 明確占位姓名不進入預設球員搜尋；其他含 unknown 姓名只標記待查。
- import_runs RUNNING 先獨立提交；球員、來源對照、品質問題及 SUCCEEDED 狀態同交易完成。
- 失敗回滾所有球員與品質寫入，另存 FAILED 狀態；沒有部分成功資料可供查詢。
- 同版本、同快照成功批次重試回傳既有 run；跨版本／快照替換拒絕，須新建候選資料庫。
- 重複球員 ID 使整批失敗，不任意取第一筆或覆寫。相同數字 ID 可分屬 ATP/WTA。
- 中斷留下 RUNNING 時拒絕重試；須先確認舊程序已停止，再由操作者查明／處理該批次，未提供自動接管。

候選資料庫完成後，API 才能以設定指向該資料庫；切換及備份還原屬部署操作，本輪不自動執行。


### 第二步驗收與操作

工作目錄 backend：

```bash
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw -Dtest=PlayerConverterTest,PlayerImportServiceTest test
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw clean verify
```

專屬 7 案通過；完整 325 案全部通過，JAR 打包成功。測試以人工 Git、唯讀封存及獨立 PostgreSQL 驗證兩巡迴同數字 ID、原值證據、隔離、重試、拒絕版本替換、重複 ID 回滾與遭竄改的來源。未向真實資料庫匯入。

實際使用時先建立獨立候選資料庫，設定 DB_URL、DB_USERNAME、DB_PASSWORD（不要提交秘密值），再在專案根目錄執行：

```bash
IMPORT_TARGET=candidate backend/import-players \
  /home/shumin/tennis-sackmann-archive-full \
  https://github.com/Aneeshers/tennis-sackmann-archive \
  83733587353df8a41f2fd4f516147d5aa83f5a8d \
  .local/source-snapshots/83733587353df8a41f2fd4f516147d5aa83f5a8d \
  "$(git rev-parse HEAD)"
```

先從對應 commit 執行 `./mvnw package`；本段為操作說明，不表示已對真實資料執行。CLI 不開 HTTP 服務，成功顯示 run/imported/quarantined/reused。候選限制是程式防呆，不替代操作者確認 DB_URL。

## 3. 球員搜尋（feat/player-search，已驗收）

前兩個功能 commit 為 4cb892b、59ade66，已在 feat/data-model 提交並快轉合併 main；球員搜尋分支從 main 的 59ade66 建立。

- `GET /tennis/api/v1/players?q=&tour=&page=&size=`；空 q 可瀏覽，tour 可空／ATP／WTA，page 0～10000、size 1～100、q 最多 100 字元。
- Controller → PlayerService → PlayerRepository；沿用 JdbcTemplate，不額外引入 JPA。Service 正規化及驗證參數，以唯讀 REPEATABLE_READ 交易保持總數與結果一致，交易逾時 10 秒。
- Repository 使用參數綁定，%／_／! 作字面搜尋；依姓名、巡迴與 ID 穩定排序。只讀成功匯入且沒有 OPEN PLAYER_SEARCH 品質排除的球員，未發布或占位資料不顯示。
- 回應 `{items,total,page,size}`；錯誤 `{code,message}`，不公開 SQL 或堆疊。
- 前端 `/tennis/players` 支援篩選、分頁、雙語、URL 條件保存、載入／空結果／錯誤重試、手機橫向表格及頁碼越界復原。
- 不提供尚未實作的球員詳情、排名或生涯統計；保留來源與授權說明。

### Docker 中斷時的交接紀錄（已由最終驗收解除）

目前前端與不依賴 Docker 的後端測試可執行。API 整合測試曾因 Docker 再次逾時而中止，不能記為通過。依使用者要求先完成其他部分，再等待 Docker 重啟。

恢復後依序執行（專案根目錄）：

```bash
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml -Dtest=PlayerSearchApiTest test
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml clean verify
python3 scripts/test/player_search_e2e.py
```

最後一項需 Chromium 系統相依套件；本機暫存函式庫仍存在時，可在命令前加 `LD_LIBRARY_PATH=/tmp/tennis-browser-libs/extracted/usr/lib/x86_64-linux-gnu`。真實端到端測試不得設定 E2E_MOCK_API。通過後更新驗收結果，再將第三步所有前後端檔案、測試與文件一起 commit；不合併第三步到 main、不推送或部署。

## 球員搜尋：Docker 重啟前交接（2026-10-28）

前兩步已提交 4cb892b、59ade66，快轉合併 main；目前分支 feat/player-search，第三步尚未提交。
前端頁面、Controller、Service、Repository、測試與操作文件均已存檔；不包含使用者的 .vscode/ 修改。

本次不依賴 Docker 的驗收：

- frontend：`npm run lint`、`npm test`（14 案）、`npm run build` 通過。
- backend：`env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw -Dtest=PlayerServiceTest,PlayerControllerTest test`，8 案通過。
- frontend：啟動 `npm run dev -- --host 127.0.0.1 --port 5178 --strictPort` 後，以 `E2E_MOCK_API=1 E2E_BASE_URL=http://127.0.0.1:5178 npm run test:e2e` 驗證瀏覽器；2 案通過。此為模擬回應，不代表資料庫或實際 API 通過。
- 本機 Chromium 缺 libgbm，暫以下載並解壓至 /tmp 的 libgbm1、libwayland-server0 配合 `LD_LIBRARY_PATH=/tmp/tennis-browser-libs/extracted/usr/lib/x86_64-linux-gnu` 執行；未安裝系統套件。重啟後暫存路徑可能消失。

桌面／手機版面已檢查；搜尋、分頁、語言保存、空結果、錯誤重試與載入狀態已有前端驗證。截圖在 frontend/test-results/，不納入 Git。
完整端到端腳本會清除繼承的 E2E_MOCK_API，避免將模擬回應誤當實際 API 驗收。

使用者要求先完成其他工作再重啟 Docker；本次不重試 Docker、不提交尚未完成整合驗收的第三步。
恢復後先執行 PlayerSearchApiTest，再 clean verify 與 scripts/test/player_search_e2e.py；具體指令見 [功能交付紀錄](11-player-import-and-search.md)。
第三步全部通過後更新文件並在 feat/player-search 一次提交；不推送、部署或合併第三步到 main。

## 第三步最終驗收（2026-10-28）

Docker 恢復後已完成下列驗收，解除前述待驗收事項：

1. 專案根目錄：`env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml -Dtest=PlayerSearchApiTest test`，9 案通過。
2. 專案根目錄：`env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml clean verify`，342 案全部通過，無失敗、錯誤或跳過，JAR 打包成功。
3. 專案根目錄：`LD_LIBRARY_PATH=/tmp/tennis-browser-libs/extracted/usr/lib/x86_64-linux-gnu python3 scripts/test/player_search_e2e.py`，2 個瀏覽器案例通過；人工 Git/CSV → 封存 → PostgreSQL 匯入 → HTTP API → 頁面已串接驗證。錯誤與延遲情境刻意攔截回應，其餘搜尋使用實際 API。

API 初次測試出現 404：Spring Boot 注入的 TestRestTemplate 已帶 context path，測試又加上 /tennis/api。已改成相對 `/v1/players`，與既有 HealthControllerTest 一致；公開網址仍為 `/tennis/api/v1/players`。
前次前端 lint、14 個測試、正式建置及模擬瀏覽器 2 案結果仍有效；此次未修改前端產品程式。
端到端測試容器與程序已由腳本清理。未匯入真實資料、未推送或部署；.vscode/ 未修改或納入提交。

第三個功能 commit 集中包含頁面、Controller、Service、Repository、測試與文件，保留在 feat/player-search，未合併 main。前兩步已合併 main。
下一階段恢復教學節奏：先檢視球員搜尋頁面與端到端資料流程，再決定後續功能；正式候選資料匯入及部署需另行安排。

## 真實球員資料本機驗收（2026-10-28）

使用者建立獨立 tennis_candidate 資料庫，從 a44e941877f6f944ae0ed5e98b13c123e1fa666f 打包匯入工具，對已驗證封存 83733587353df8a41f2fd4f516147d5aa83f5a8d 執行 backend/import-players。
依使用者貼回日誌與唯讀 SQL 結果確認：

- Flyway V1～V10 套用成功，run=1、status=SUCCEEDED。
- imported=137483、quarantined=0、reused=false。
- ATP 66,912 筆、WTA 70,571 筆；quality_issues 170 筆。問題筆數不等於球員數；沒有隔離紀錄不代表全部欄位均無疑義。
- Federer：ATP／103819／Roger Federer／1981-08-08／SUI。

使用者將 .vscode/tasks.json 的 dev: backend 連線資料庫改成 tennis_candidate，停止並重新啟動後端；.env 的 POSTGRES_DB 仍保留 tennis。
頁面搜尋 Roger Federer 已顯示 ATP、#103819、SUI、1981/8/8、右手，完成真實來源 → 候選資料庫 → API → 前端的本機人工驗收。
這次只匯入球員，未匯入比賽或排名，未部署或推送；前文「未匯入真實資料」為此前階段紀錄。

重現流程及命令：

```bash
# 工作目錄：專案根目錄；建立資料庫只需執行一次
 docker compose exec db createdb -U tennis_admin -O tennis_admin tennis_candidate
 ./backend/mvnw -f backend/pom.xml -DskipTests package

# 子 Shell 只為本次匯入設定連線；不輸出密碼
(
  set -euo pipefail
  source .env
  export DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5433}/tennis_candidate"
  export DB_USERNAME="${POSTGRES_USER:?請設定 POSTGRES_USER}"
  export DB_PASSWORD="${POSTGRES_PASSWORD:?請設定 POSTGRES_PASSWORD}"
  export IMPORT_TARGET=candidate
  backend/import-players \
    /home/shumin/tennis-sackmann-archive-full \
    https://github.com/Aneeshers/tennis-sackmann-archive \
    83733587353df8a41f2fd4f516147d5aa83f5a8d \
    .local/source-snapshots/83733587353df8a41f2fd4f516147d5aa83f5a8d \
    a44e941877f6f944ae0ed5e98b13c123e1fa666f
)
```

以上 importer SHA 對應本次已驗收的建置；未來重建其他版本時須核對實際程式版本，不直接沿用此 SHA。
資料庫已建立且匯入成功，無需為確認頁面再次建立或匯入。

## 球員詳情頁完整驗收（2026-10-28）

依使用者授權完成前端姓名連結與詳情頁，沿用 frontend-design 技能及現有排版，未全檔格式化。
路由 `/tennis/players/:tour/:playerId` 呼叫詳情 API；搜尋條件透過 query string 保留，返回不依賴瀏覽器歷史，因此重新整理及新分頁仍可使用。支援雙語、缺值、載入、無效網址、404 與錯誤重試。
僅顯示基本欄位，不增加未匯入的排名／比賽或有品質疑慮的身高。保留來源授權與既有頁尾署名。

驗收指令（專案根目錄）：

```bash
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml clean verify
LD_LIBRARY_PATH=/tmp/tennis-browser-libs/extracted/usr/lib/x86_64-linux-gnu python3 scripts/test/player_search_e2e.py
```

結果：前端 23 案、後端 365 案全通過；lint、正式建置及 JAR 打包成功。瀏覽器 3 案通過，人工 Git → 封存 → 獨立 PostgreSQL → API → 前端完整串接；詳情重新整理、返回篩選、跨巡迴同 ID、占位 404 與手機版面已驗證。桌面及手機截圖已目視檢查，存在 frontend/test-results/，不納入 Git。
未改動 tennis_candidate，尚未對使用者正在執行的開發後端進行人工詳情驗收；需重啟 dev: backend 以載入新 Controller。可在原搜尋頁點 Federer，或開啟 http://127.0.0.1:5173/tennis/players/ATP/103819。
目前 feat/player-detail 的前後端變更尚未提交，待使用者要求時一起提交。保留 .vscode/settings.json 與 PROJECT_SUMMARY.md，不納入本功能修改。
