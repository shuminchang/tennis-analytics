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
