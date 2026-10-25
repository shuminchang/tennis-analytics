# 階段 0：本機開發環境

## 工具

- Java 21
- Node.js 22.22.2（記錄於根目錄 .nvmrc）
- npm 10.9.7
- PostgreSQL 16（Docker Compose）

## 前端

在 frontend 目錄執行：

```bash
npm ci
npm run dev
```

開發網址：http://localhost:5173/tennis/
若連接埠已被占用，以終端機顯示的網址為準。

檢查與建置：

```bash
npm run lint
npm run build
```

建置產物位於 frontend/dist/。

## 本機資料庫

首次設定：將 .env.example 複製成 .env，填入本機密碼。
.env 不提交到 Git。

以下指令在專案根目錄執行。

啟動並等待健康檢查：

```bash
docker compose up -d --wait
```

查看狀態：

```bash
docker compose ps
```

停止服務並保留容器與資料：

```bash
docker compose stop
```

再次啟動可使用 docker compose up -d --wait。

連線資訊：

- 主機：127.0.0.1
- 連接埠：5433
- 資料庫：tennis
- 初始化管理員：tennis_admin
- 密碼：見本機 .env

應用程式專用帳號尚未建立。

資料存放於具名 volume postgres_data。
docker compose down 會移除容器與網路，預設保留此 volume。
不要隨意使用 docker compose down -v，它會刪除資料 volume。

.env 中的初始化帳密只在資料目錄為空時生效；
資料庫建立後，修改 .env 不會自動修改資料庫密碼。

## 已完成驗證

- ESLint 與前端建置通過。
- /tennis/ 範例頁面與計數按鈕正常。
- PostgreSQL 容器健康檢查通過。
- 容器內 SQL 查詢成功，版本為 16.15。

2026-10-25：使用者執行 Java 資料庫整合測試通過，已驗證從 WSL 主機以帳密連線至 tennis，執行 current_database() 與 SELECT 1。

## 後端

使用 Java 21、Spring Boot 3.5.16。
Maven Wrapper 固定使用 Maven 3.10.0。

啟動開發應用前，先確認 Docker Desktop 可用及 Compose 的 db 容器健康。
測試改用 Testcontainers，只需 Docker 可用，不需 Compose 的 db 或下列開發連線變數。
在要執行後端的同一個 Bash 終端機設定：

```bash
cd /home/shumin/tennis-analytics/backend
export DB_URL='jdbc:postgresql://127.0.0.1:5433/tennis'
export DB_USERNAME='tennis_admin'
read -r -s -p '請輸入本機 PostgreSQL 密碼：' DB_PASSWORD
printf '\n'
export DB_PASSWORD
```

輸入目前資料庫的密碼，不把實際密碼寫入指令或 Git。
根目錄 .env 供 Compose 使用，Spring Boot 不會自動載入它。
環境變數只供目前 shell 與其子程序使用；新終端機須重新設定。
以下指令在 backend 目錄執行。

啟動：

```bash
./mvnw spring-boot:run
```

本機預設使用 127.0.0.1:8081。
可透過環境變數 SERVER_PORT 覆寫連接埠；
正式部署規劃使用 8080。

驗證 API：

```bash
curl -i http://127.0.0.1:8081/tennis/api/v1/health
```

預期 HTTP 200，內容為 {"status":"UP"}。
此端點只檢查應用回應，尚未檢查資料庫。

執行測試：

```bash
./mvnw test
```

目前共兩個測試，使用者已驗證 2 tests、0 failures、0 errors。
HealthControllerTest 啟動獨立應用並使用隨機連接埠；
DatabaseConnectionTest 不啟動 HTTP，透過 JdbcTemplate 實際查詢 Testcontainers 的 tennis_test。
兩個測試都以 @Import 載入 TestDatabaseConfiguration，容器由 Spring 管理生命週期，
@ServiceConnection 提供動態連接埠與帳密，不使用開發資料 volume。
單獨執行連線測試：

```bash
./mvnw -Dtest=DatabaseConnectionTest test
```

2026-10-25 使用者以以下指令驗證，未提供三個 DB_* 變數仍為 2 tests、0 failures、0 errors：

```bash
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw test
```

env -u 只影響該次子程序，不清除目前 shell 的變數。
測試仍需可用的 Docker；初次可能下載 PostgreSQL 與輔助映像。
兩個測試的 WebEnvironment 不同，不能假設共用同一 Spring context 或同一個容器。
目前未另做停止 Compose db 後的驗證，也未驗證 CI 環境。
應用啟動成功不等於資料庫連線成功；連線測試才實際驗證查詢。
注入的 TestRestTemplate 已包含 context path，
因此測試請求使用 /v1/health。

設定檔位置為 src/main/resources/application.properties，
注意 resources 結尾有 s。

## 後端打包與執行

在 backend 目錄執行完整建置：

```bash
./mvnw clean verify
```

直接執行產出的 JAR：

```bash
java -jar target/tennis-0.0.1-SNAPSHOT.jar
```

啟動前先停止占用相同連接埠的本專案開發服務。
按 Ctrl + C 停止應用。

已驗證：從 JAR 啟動後，GET /tennis/api/v1/health
回傳 HTTP 200 與 {"status":"UP"}。

目前前端、後端、資料庫均已分別啟動驗證；
後端資料庫連線測試已通過，尚未完成前後端串接、業務表或資料匯入。
加入 JDBC 後尚未重新驗證 JAR 打包執行；前述 JAR 成功紀錄為階段 0。

## WSL Docker 連線排錯紀錄（2026-10-25）

本次 Java 測試發生 Connection refused，位址為 127.0.0.1:5433；
當時 TCP 連線尚未建立，不是已確認密碼錯誤。
WSL 隨後顯示 docker 指令不可用。Ubuntu 的 WSL Integration 已開啟，
退出並重新啟動 Windows Docker Desktop 後，docker version 恢復 Client 與 Server。
未重新安裝 Docker，也未重建資料 volume。

在專案根目錄依序確認（依結果決定下一步，不必每次全部重做）：

```bash
cd /home/shumin/tennis-analytics
docker version
docker compose ps -a
```

當時 db 顯示 Exited (255)，原因尚未查明；連接埠仍為 127.0.0.1:5433->5432/tcp。
使用下列指令恢復 Healthy：

```bash
docker compose up -d --wait db
```

接著在原先保有 DB_* 變數的終端機，單獨連線測試通過，再執行全部兩個測試亦通過。
若重啟後仍異常，依實際錯誤查日誌，不直接刪除容器資料或重新初始化。


## Flyway 與球員表驗收（2026-10-25）

已加入 Flyway core/PostgreSQL 模組 11.7.2。
V1__create_players.sql 已在 Testcontainers 中成功建立 players；
尚未驗證套用至 Compose 開發資料庫，未匯入來源資料。
啟動正式應用現在會自動執行尚未套用的 migration，不再只是驗證連線。

目前測試共 14 個案例（連線 1、健康檢查 1、球員表 12），
使用者已執行以下完整建置成功：

```bash
cd /home/shumin/tennis-analytics/backend
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw clean verify
```

已確認新 JAR 打包與 repackage 成功，尚未實際啟動該 JAR。
前文兩個測試為早期階段紀錄，當前案例數以本節為準。
後續已統一 JUnit 5 匯入與 COUNT 的 Long 型別，並重驗球員表 12 個案例通過。
14 個案例與 JAR 建置紀錄為修正前驗收；修正後只重跑受影響的球員表測試。


## V2 排名表驗收（2026-10-25）

V2__create_rankings.sql 已在 Testcontainers 中與 V1 依序套用成功。
目前整套為 24 個案例（球員 12、排名 10、連線 1、健康檢查 1）。
使用者執行移除三個 DB_* 變數的 ./mvnw clean verify 全數通過，並完成 JAR repackage。
前文 14 個案例為 V1 階段紀錄；本次未驗證新 JAR 啟動或開發資料庫套用 V2。
單獨驗證排名表可在 backend 執行：

```bash
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw -Dtest=RankingsSchemaTest test
```

預期 10 個案例通過。測試資料隨交易回滾，未匯入來源排名資料。


## V3 比賽表驗收（2026-10-25）

V1～V3 已在 Testcontainers 中依序套用成功。目前測試共 48 個案例：
球員 12、排名 10、比賽 24、連線 1、健康檢查 1。
使用者執行移除 DB_* 的 ./mvnw clean verify 全數通過，完成 JAR 打包及 repackage。
較早的 24 個案例紀錄為 V2 階段；目前數量以本節為準。
單獨驗證比賽表，在 backend 執行：

```bash
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw -Dtest=MatchesSchemaTest test
```

預期 24 個案例通過。這些為測試容器中的人工測試資料，並非原始資料匯入。
尚未驗證開發資料庫套用 V3 或執行本次新 JAR；品質排除與匯入流程尚未實作。


## V4 來源登錄表驗收（2026-10-25）

V1～V4 已在 Testcontainers 依序套用成功。
最新整套測試共 62 個案例（球員 12、排名 10、比賽 24、來源登錄 14、連線 1、健康檢查 1）。
使用者在 backend 執行移除 DB_* 的 ./mvnw clean verify 通過，完成 JAR 建置與 repackage。
較早 48 個案例為 V3 階段結果，本節為最新驗收。
單獨驗證來源登錄：

```bash
cd /home/shumin/tennis-analytics/backend
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw -Dtest=SourceRegistrySchemaTest test
```

預期 14 個案例通過。尚未驗證開發資料庫套用 V4、新 JAR 啟動或真實檔案備份。
測試使用人工資料，不表示已完成來源登錄程式或正式資料匯入。


## V5 匯入執行表驗收（2026-10-25）

V1～V5 已在測試容器套用成功，最新整套共 80 個案例：
球員 12、排名 10、比賽 24、來源登錄 14、匯入執行 18、連線 1、健康檢查 1。
使用者執行移除 DB_* 的 clean verify 通過，完成 JAR 打包及 repackage；前文 62 為 V4 階段。
單獨驗證匯入執行表：

```bash
cd /home/shumin/tennis-analytics/backend
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw -Dtest=ImportRunsSchemaTest test
```

預期 18 個案例通過。此測試驗證欄位組合，沒有執行實際匯入或驗證狀態轉移流程。
未驗證開發資料庫套用 V5 或啟動本次新 JAR。


## V6 球員來源對照驗收（2026-10-25）

V1～V6 已在測試容器套用，最新整套共 91 個案例（前階段 80 加球員來源對照 11）。
使用者執行移除 DB_* 的 clean verify 通過，JAR 打包與 repackage 成功。
單獨驗證球員來源對照：

```bash
cd /home/shumin/tennis-analytics/backend
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw -Dtest=PlayerSourcesSchemaTest test
```

預期 11 個案例通過。未驗證真實 CSV 內容、紀錄序號上限或執行匯入。
未驗證開發資料庫套用 V6 或本次新 JAR 啟動；前段 80 個案例為 V5 歷史進度。

## 本輪 migration 驗收與交接（2026-10-25）

依使用者最新授權，agent 已完成 V7 排名來源、V8 比賽來源、V9 品質問題與影響用途、
V10 賽事判斷；只在獨立 Testcontainers 驗證，未套用開發資料庫或匯入真實資料。
完整 clean verify：158 tests、0 failures、0 errors、0 skipped，JAR 建置及 repackage 成功。
包含新建資料庫套用 V1～V10，以及有人工球員／比賽資料的 V6→V10 升級、
Flyway validate 和再次 migrate 無變更驗證。這不是正式資料量的效能或備份還原測試。

目前資料保存結構已完成本輪 migration 範圍；下一步為匯入程式，恢復使用者操作的教學模式。
先定稿候選資料庫、發布／回復邊界及版本化規則，再以來源檔案驗證／登錄的一個完整變更開始。
CSV 解析、雜湊驗證、隔離／分類規則執行、統計資格、重試及發布仍未實作。
帳號與 Elo 等功能表留待相關功能需求定稿，並非本輪來源保存 migration 的未完成項。

## 檔案 SHA-256 工具驗收（2026-10-25）

使用者完成 SourceFileHasher 與 4 個單元測試；整套移除 DB_* 的 clean verify
共 162 個案例通過，JAR 建置與 repackage 成功。agent 核對檔案，未重跑測試。
工具以串流計算原始位元組雜湊，尚未整合來源登錄、路徑驗證或真實資料匯入。
完整指令與範圍見 [教學手冊](完整教學步驟與指令.md) 文末。下一步為快照內來源路徑驗證。

## 來源路徑驗證驗收（2026-10-25）

使用者完成 SourcePathResolver 與 12 個單元測試；整套移除 DB_* 的 clean verify
共 174 個案例全通過，JAR 建置與 repackage 成功。agent 核對程式及測試，未重跑驗收。
可拒絕絕對路徑、..、符號連結越界與非一般檔案；不保證檢查後檔案不被修改。
未實作來源驗證整合、真實資料登錄或匯入。下一步準備 CSV 解析套件與輸入規格，
仍維持使用者操作的教學節奏；指令及限制見 [完整教學手冊](完整教學步驟與指令.md)。

## CSV 結構檢查驗收（2026-10-25）

使用者完成 Commons CSV 1.14.1 依賴、CsvFileInspector 與 22 個單元測試。
整套移除 DB_* 的 clean verify 共 196 個案例全通過，JAR 建置與 repackage 成功。
agent 核對檔案，未重跑驗收。工具檢查 UTF-8 CSV 標頭、欄位數與不含標頭的紀錄數，
尚未整合路徑／雜湊流程，也未驗證或匯入真實資料。
下一步整合來源檔案檢查結果，維持使用者操作的教學節奏；
指令及限制見 [完整教學手冊](完整教學步驟與指令.md)。

## 來源檔案檢查整合驗收（2026-10-25）

使用者完成 SourceFileInspector 與 9 個測試；整套移除 DB_* 的 clean verify
共 205 個案例全通過，JAR 建置與 repackage 成功。agent 核對檔案，未重跑驗收。
可回傳單一檔案的相對路徑、SHA-256、位元組大小及 CSV 紀錄數。
仍以不可變快照為前提，未驗證並行修改偵測分支、整份快照身分或實際資料匯入。
下一步定義來源檔案規格（巡迴、種類、允許檔名與標頭）；繼續逐步教學。
完整指令與限制見 [教學手冊](完整教學步驟與指令.md)。

## 來源檔案規格與 Docker 恢復（2026-10-25）

SourceFileType 的 24 個測試通過；最終完整 clean verify 為 229 個案例全通過，
JAR 建置與 repackage 成功，由使用者執行。尚未接入 SourceFileInspector 或匯入資料。

本次驗收曾停在 Testcontainers 連線。WSL 執行 `timeout 15s docker version`
回傳 124；Windows PowerShell 的 `docker version` 回報 Internal Server Error。
儲存工作並停止測試後，從系統匣退出 Docker Desktop，在 Windows PowerShell 執行
`wsl --shutdown`，再重新啟動 Docker Desktop；Windows 與 WSL 均恢復完整 Client/Server，
WSL 檢查結束代碼為 0，再重跑驗收成功。未確定原始根因，也未修改 API 版本。
注意 shutdown 會停止所有 WSL 程序與中斷 VS Code 連線；未清除任何 volume。
完整步驟、指令與證據界線見 [教學手冊](完整教學步驟與指令.md) 文末。

## 來源規格整合驗收（2026-10-25）

使用者完成 SourceFileInspector 的兩參數介面與來源類型結果，依檔名自動選擇標頭。
新版專屬測試 18 個案例通過；完整 clean verify 共 238 個案例全通過，JAR 建置及 repackage 成功。
agent 核對程式與測試，未重跑驗收。未進行真實資料登錄或匯入。
下一步設計快照識別、檔案清單與驗證邊界，再以逐步教學實作批次檢查；
單檔通過不代表快照完整。詳細指令及限制見 [教學手冊](完整教學步驟與指令.md)。
