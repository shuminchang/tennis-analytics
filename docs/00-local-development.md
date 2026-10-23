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