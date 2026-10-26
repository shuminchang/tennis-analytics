# 快照與檔案清單驗證設計

設計開始日期：2026-10-25；2026-10-26 已完成明確清單的批次檔案檢查，尚未建立完整快照驗證或真實匯入流程。
已有單檔檢查工具不代表整份快照身分及完整性已驗證。

## 驗證分層

1. **清單合法性**：來源相對路徑非空集合、每個路徑符合 SourceFileType、不重複。
   本步 SourceFilePlan 只負責此層，不聲稱驗證檔案存在或完整範圍。
2. **快照身分**：來源 repository URL 與完整 commit SHA 必須明確指定，
   後續檔案清單須由該 commit 的受版控樹及版本化選檔規則取得。
   只讀工作目錄 HEAD 不足以證明檔案內容屬於該 commit。
3. **快照保存**：從指定 commit 建立獨立保存副本，核對選定檔案內容與 commit，
   保留原始位元組，不改寫換行。匯入期間禁止其他程序修改來源；保存及核對機制尚待實作。
4. **清單完整性**：比對預期清單與保存副本；預期檔案缺少即失敗。
   額外的支援來源檔案須查明，不能默默擴大範圍；README、授權文件與明確排除資料
   依選檔規則區分，不要求儲存庫所有檔案都屬於本次匯入資料。
5. **內容檢查**：清單內每檔經 SourceFileInspector 驗證，才產生整批結果。
   任一失敗不產生成功批次；結果附來源身分及規則版本才可用於後續登錄。

完整性指「相對於指定 commit 與選檔規則完整」，不是網球歷史資料完整。
明確的小型清單可用於測試或部分驗證，不能因此標示完整快照驗收通過。
先不執行來源匯出、權限修改、備份或資料庫寫入；各步在教學中分別驗證。

## 第一個程式單位：SourceFilePlan

不可變 record，僅包含 List<Path> relativePaths。
建構時複製清單、拒絕空清單、以 SourceFileType 驗證各項、拒絕重複，再依路徑字串排序。
不先 normalize，不以去重方式隱藏呼叫端錯誤，不從磁碟自動補齊或推斷檔案。
排序讓相同輸入集合得到固定處理順序；不代表匯入正式表的外鍵依賴順序。
SourceFileType、清單選擇、Git 身分及內容核對仍是不同責任。

## 實際寫入之前

候選資料庫必須與 API 使用的資料庫分開；失敗批次不發布。
發布／回復的實際命令、備份可恢復性、權限及批次成功條件仍待設計與驗證。
本文件不宣稱已有不可變封存、備份還原、快照驗證或資料發布功能。

## 清單合法性驗收（2026-10-26）

使用者完成 SourceFilePlan 與 10 個單元測試；完整 clean verify 共 248 個案例全通過，
JAR 建置與 repackage 成功。agent 核對程式與測試，未重跑驗收。
已驗證排序、清單不可修改、拒絕空清單／重複／不支援路徑與 null；
尚未實作批次檢查、Git 快照身分驗證、清單完整性或真實資料匯入。
下一步以逐步教學串接清單與單檔檢查工具；詳細指令及檔案放錯目錄的排錯見教學手冊。

## 第二個程式單位：SourceFileBatchInspector（已驗收）

接收根目錄與已驗證格式的 SourceFilePlan，逐檔檢查後回傳不可修改的
List<SourceFileInspector.InspectionResult>。任一檔案失敗即停止並附相對路徑及 cause，
不回傳部分成功結果。方法沒有資料庫寫入，因此這不是資料庫交易或發布保證。
此階段不核對 Git commit、不從磁碟補齊清單、不判定額外檔案或完整性。
程式與測試已完成，驗收見下節；完整性及快照身分驗證仍待後續實作。

## 批次檔案檢查驗收（2026-10-26）

使用者完成 SourceFileBatchInspector 與 7 個測試；整套 clean verify 共 255 個案例全通過，
JAR 建置與 repackage 成功。agent 核對程式及測試，未重跑驗收。
明確清單內全部檔案通過才回傳不可修改結果；失敗附相對路徑及原始 cause，重試不累積舊結果。
尚未核對 Git 快照身分、選檔完整性或真實資料匯入，也不保證批次期間來源不可變。
下一步定義來源快照身分，再逐步串接指定 commit 的清單與內容核對。
操作及驗收見 [完整教學手冊](完整教學步驟與指令.md)。

## 第三個程式單位：SourceSnapshotIdentity（格式已驗收）

record 保存 URI repositoryUrl 與 String commitSha；不包含本機根目錄，
同一來源快照移動保存位置不應改變來源身分。
第一版來源網址限定 HTTPS、有 host 及非空且非單獨 / 的路徑，
拒絕 user-info、query、fragment；這是本專案來源識別政策，不宣稱是全部 Git URL 格式。
網址以明確設定的標準寫法傳入；不自動合併大小寫、.git 或結尾斜線等別名，
本專案使用 https://github.com/Aneeshers/tennis-sackmann-archive。
URI 的字串形式日後寫入 repository_url；本輪沒有資料庫寫入。
commitSha 限 40 位小寫十六進位，與 V4 一致；不接受分支、標籤、短 SHA，
不自動 trim 或改大小寫。未來使用其他 Git 雜湊格式需同步調整模型與 migration。
格式通過不代表遠端存在、commit 屬於該儲存庫或本機檔案吻合，後續另行查核。
實作與格式測試已完成，驗收見下節；真實 Git 查核尚未實作。

## 快照身分格式驗收（2026-10-26）

使用者完成 SourceSnapshotIdentity 與 20 個單元測試；完整 clean verify
共 275 個案例全通過，JAR 建置及 repackage 成功。agent 核對檔案，未重跑驗收。
已驗證網址政策與完整 SHA 格式，尚未查核儲存庫、commit 或來源內容的真實對應，
未建立封存或匯入資料。下一步為本機 Git 查核與指定 commit 檔案清單，維持逐步教學。
詳細指令及驗收界線見 [完整教學手冊](完整教學步驟與指令.md)。

## Git 查核實作（2026-10-26）

依最新授權，agent 自主實作 GitSourceRepository。以參數陣列呼叫本機 Git，
不經 shell；排除繼承的 GIT_*、全域／系統設定，禁用 replace objects，設定命令逾時。
origin 必須等於指定 URL 或其加上 .git 的形式；這是本機設定一致性，不能證明遠端真實性。
來源 repository 與 commit 是操作者信任的輸入，不自動 fetch 或驗證上游簽章。
拒絕 partial clone，要求本機已有物件。cat-file -t 確認 SHA 本身是 commit，
ls-tree -r -z 取得該提交的樹，不讀工作目錄內容。選中檔案只接受一般 blob，拒絕連結及 submodule。
已知 qual_chall/futures/doubles 與 ATP amateur 不選入；新的 players/rankings/matches CSV 命名停止待查。
保留排除路徑清單，另列 LICENSE、README 及已有的上游說明／字典供封存。
cat-file blob 串流至新檔，保留原始位元組，不使用 archive 的 export-ignore 或 checkout 換行轉換。
輸出既存時拒絕覆寫。metadata 限 16 MiB、Git 命令限兩分鐘，錯誤不回顯可能含憑證的 stderr。
6 個人工 Git 測試通過；尚未做本輪全套驗收或正式資料封存。
