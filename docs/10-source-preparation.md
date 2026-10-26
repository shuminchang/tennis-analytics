# 來源快照工具與交接

2026-10-26：來源檔案處理已實作；下一階段恢復教學。
此工具不啟動 Spring、不連資料庫、不登錄 source_snapshots/source_files，亦不轉換或匯入業務資料。

## 範圍

- `plan`：核對本機 origin、指定 commit、選檔與排除清單。
- `prepare`：由 Git blob 匯出原始內容，檢查 CSV 結構，產生 JSON 清單與唯讀封存。
- `verify`：以可信任的本機 Git commit 重新核對清單、檔案、SHA-256、Git blob ID、大小與紀錄數。
- 保留現有 LICENSE、README、上游說明與比賽資料字典。排除清單指不選為資料輸入，部分說明文件仍另外保存。
- 封存不是完整 Git 備份；驗證需要本機來源 Git。唯讀權限防止誤改，不能抵擋擁有者刻意修改。
- 僅支援目前 Ubuntu/POSIX 環境、完整本機 Git 物件庫及 SHA-1 Git 物件格式。拒絕 partial clone，不自動下載缺少物件。
- 不覆寫既有封存；父目錄須先存在，目的地不得位於來源庫內。
- 選檔規則為 main-tour-singles-v1；未知候選 CSV 名稱須先查核，不能靜默略過。

## 操作

工作目錄 `/home/shumin/tennis-analytics/backend`，先建置：

```bash
./mvnw package
```

工作目錄 `/home/shumin/tennis-analytics`：

```bash
mkdir -p .local/source-snapshots
backend/source-snapshot plan \
  /home/shumin/tennis-sackmann-archive \
  https://github.com/Aneeshers/tennis-sackmann-archive \
  83733587353df8a41f2fd4f516147d5aa83f5a8d
```

確認來源是完整本機 Git 庫後，才執行以下指令；目前來源為 partial clone，會被拒絕。

```bash
backend/source-snapshot prepare \
  /home/shumin/tennis-sackmann-archive \
  https://github.com/Aneeshers/tennis-sackmann-archive \
  83733587353df8a41f2fd4f516147d5aa83f5a8d \
  .local/source-snapshots/83733587353df8a41f2fd4f516147d5aa83f5a8d

backend/source-snapshot verify \
  /home/shumin/tennis-sackmann-archive \
  https://github.com/Aneeshers/tennis-sackmann-archive \
  83733587353df8a41f2fd4f516147d5aa83f5a8d \
  .local/source-snapshots/83733587353df8a41f2fd4f516147d5aa83f5a8d
```

成功輸出 JSON，結束碼 0；處理失敗為 1，用法錯誤為 2。錯誤輸出避免回顯可能含憑證的參數。
CLI 由已打包 JAR 執行，修改 Java 後須重新打包。`.local/source-snapshots/` 已排除於 Git。

## 本輪驗收與待辦

- Git 讀取、封存／搬移驗證及 CLI 使用人工 Git／CSV 測試，不代表真實資料品質檢查或匯入成果。
- 真實庫 plan/prepare 因 partial clone 被拒絕，尚無真實封存。沒有修改原始庫或自動 fetch。
- `env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./mvnw clean verify` 停在 Docker 連線；`timeout 15s docker version` 為 124，停止本次等待中的測試 JVM。不是整套驗收通過。
- Docker 恢復後補跑 clean verify；先確認 Windows Docker Desktop 及 WSL integration，避免重裝資料庫或刪除 volume。
- 下一教學步驟：確認完整本機來源庫的取得方式，再實際 plan → prepare → verify；之後設計來源登錄交易，才進入逐列轉換、品質處理與正式匯入。
- 外部備份及真實封存還原演練尚未完成。

本輪最終結果：來源處理 139 個測試通過（Failures/Errors/Skipped 均為 0），package 成功；另以暫存人工 Git 庫實際執行已打包 CLI 的 plan、prepare、verify，三步皆成功。此結果不替代待補的全套資料庫測試。
