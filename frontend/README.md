# 球員搜尋前端

React + TypeScript + Vite，頁面位於 `/tennis/players`。使用 React Router、TanStack Query/Table 與 react-i18next。

## 本機啟動

工作目錄 `frontend`：

```bash
npm ci
npm run dev
```

後端先以已匯入的候選資料庫啟動。Vite 將 `/tennis/api` 代理到 `http://127.0.0.1:8081`；可用 `API_PROXY_TARGET` 覆寫開發代理目標。不需開放跨網域 CORS。
前端沒有內建假資料；尚未匯入時顯示空結果，API 無法連線時提供重試。
正式環境仍需 Apache 對 `/tennis/api/` 反向代理，並將 `/tennis/players` 等前端路由導回 index.html；Vite 開發代理不會包含在 dist 內。

## 行為

- 姓名不分大小寫搜尋；ATP/WTA 篩選、分頁，條件保留於 URL，可重新整理及使用上一頁／下一頁。
- 切換條件送出時回第一頁；頁碼超出範圍可回到第一頁。
- 繁體中文／英文切換，偏好存 localStorage；無儲存權限時仍可使用。
- 缺少資料顯示「未提供」；未知慣用手 U 與缺值分開。
- IOC 原始代碼不自動翻成國家名稱；不提供尚未完成的排名或勝率；姓名連結可開啟球員詳情。
- 版面為網球記錄冊風格；字型載入失敗時使用本機字型。使用專案 frontend-design skill，頁尾保留該 skill 要求的署名。

## 驗證

```bash
npm run lint
npm test
npm run build
npx playwright install chromium
```

完整端到端測試（需 Docker、Java、Python 3、Git，且 backend 已打包），工作目錄專案根目錄：

```bash
python3 scripts/test/player_search_e2e.py
```

腳本自建可刪除的 PostgreSQL 容器、人工 Git/CSV 封存，經匯入 CLI 寫入測試資料，再啟動實際 API、Vite 及瀏覽器。不讀 `.env`，不使用開發資料庫 volume。結束時清除測試程序與容器，日誌留於 /tmp。

Docker 不可用時可只檢查前端：先以 `npm run dev -- --port 5178 --strictPort` 啟動，再執行：

```bash
E2E_MOCK_API=1 E2E_BASE_URL=http://127.0.0.1:5178 npm run test:e2e
```

此模式攔截 API 回應，不代表後端或資料庫已驗收。Linux 需 Playwright 的系統相依套件；若缺少 libgbm 等，可由管理員執行 `npx playwright install-deps chromium`。本輪測試暫以 /tmp 解壓的 libgbm1、libwayland-server0 及 LD_LIBRARY_PATH 執行，未變更系統套件。

npm 10 安裝新增測試依賴曾發生 Arborist `edgesOut` 錯誤，改用一次性的 `npx --yes npm@11 install ...` 完成。鎖定檔已更新；隨後原 npm 10 的 `npm ci` 可成功重現，無需升級全域 npm。

參考：[TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview)、[react-i18next](https://react.i18next.com/latest/usetranslation-hook)。

## 球員詳情

`/tennis/players/:tour/:playerId` 顯示姓名、巡迴、IOC、生日及慣用手。姓名連結攜帶搜尋 query string，返回可保留篩選與頁碼；直接開啟、重新整理及中英文切換皆可用。404 與無效網址分開顯示，暫時失敗可重試。頁面不顯示尚未驗證的身高、排名或比賽統計。

2026-10-28：前端 23 案、lint／build 通過；獨立資料庫及實際 API 的瀏覽器 3 案通過，含詳情重新整理、返回條件、ATP/WTA 同 ID 與手機版面。測試人工資料不寫入 tennis_candidate。

## 排名歷史

詳情頁新增日期由新到舊的排名表；rankingPage 保存頁碼，返回搜尋會移除該參數。支援載入、未匯入、無紀錄、錯誤重試、越界及中英文。積分缺值與零值分開顯示，不宣稱為完整生涯／目前排名。此功能需要新後端及已成功匯入的排名；尚未匯入時顯示明確空狀態。驗收：前端 29 案、lint／build 與實際測試 API 的瀏覽器 4 案通過。

## 比賽紀錄

詳情頁增加已確認範圍的比賽表格，支援賽事年份、分頁、對手詳情連結、雙語與錯誤重試。日期是賽事日期；W/O 不視為已出賽，RET／DEF 狀態另外標示。僅顯示符合範圍及身分規則的紀錄，未提供完整生涯或勝率。matchYear／matchPage 保留在 URL，返回搜尋時移除。手機表格可左右捲動。驗收：前端 36 案、lint／build、實際測試 API 瀏覽器 5 案通過。
