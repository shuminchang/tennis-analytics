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

尚未驗證從主機以密碼連線。

## 後端

使用 Java 21、Spring Boot 3.5.16。
Maven Wrapper 固定使用 Maven 3.10.0。

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

測試會啟動獨立的應用並使用隨機連接埠。
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
尚未完成前後端串接及後端資料庫連線。