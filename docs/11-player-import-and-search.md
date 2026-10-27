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
