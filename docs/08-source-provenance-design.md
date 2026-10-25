# 來源追溯與品質紀錄：實體設計

日期：2026-10-25。V4 的來源登錄表已在測試容器實作並驗證；其餘匯入與品質流程仍為設計。
先建立來源快照與檔案登錄，再處理匯入批次、來源對照及品質問題。
不更動已提交的 V1～V3，也不直接匯入資料。

## 原值保存決定

第一版保留不可變的 CSV 快照與可恢復備份，不把全部正常排名列再複製成資料庫 JSON。
資料庫記錄快照、檔案及 CSV 資料紀錄序號，能回到原檔取得原值。
紀錄序號從 1 開始，不含標頭；使用 CSV parser 的紀錄序號，不是文字行號（欄位可能含換行）。
Git SHA 識別來源版本，檔案 SHA-256 確認內容；僅記 Git SHA 不代表已備份或驗證原檔。

必須在匯入前驗證快照及檔案雜湊，在發布前驗證備份可還原。
如未來不保留某個舊快照，就不能先刪檔再宣稱仍可追溯；須另行制定保留政策。
隔離問題可另存少量證據 JSON，值以字串保留；證據不取代原始 CSV。
本輪不聲稱已測得磁碟節省比例；來源對照仍會有數百萬列，需在匯入試作量測。

## V4：兩張來源登錄表

### source_snapshots

| 欄位 | 型別方向 | 意義 |
| --- | --- | --- |
| id | bigserial | 內部主鍵 |
| repository_url | text，必填且非空白 | 不含憑證的標準儲存庫 URL |
| commit_sha | varchar(40)，必填 | 本專案來源目前使用的 40 位小寫十六進位 Git SHA |
| archive_location | text，必填且非空白 | 不可變快照的保存位置，不放短期簽名 URL 或密碼 |
| registered_at | timestamptz，必填，預設現在 | 登錄時間，不是來源提交或驗證完成時間 |

唯一鍵為 (repository_url, commit_sha)。匯入器需統一 URL 寫法，資料庫不自動合併不同網址。
40 位 SHA 是目前輸入規格，不宣稱所有 Git 倉庫永遠都是 SHA-1；未來來源格式改變再 migration。
archive_location 指向實際保存位置；欄位非空不代表檔案存在，程式須檢查並記錄驗證結果。

### source_files

| 欄位 | 型別方向 | 意義 |
| --- | --- | --- |
| id | bigserial | 內部主鍵 |
| snapshot_id | bigint，必填 | 外鍵參照 source_snapshots |
| relative_path | text，必填且非空白 | 快照根目錄內的檔案路徑 |
| tour | varchar(3)，必填 | ATP 或 WTA |
| data_kind | text，必填 | PLAYERS、MATCHES、RANKINGS |
| sha256 | varchar(64)，必填 | 原始檔案位元組的 64 位小寫 SHA-256 |
| byte_size | bigint，必填且非負 | 原檔位元組大小 |
| record_count | bigint，必填且非負 | 完成 CSV 解析後的資料紀錄數，不含標頭 |

唯一鍵為 (snapshot_id, relative_path)，檔案外鍵不連帶刪除。
同內容可以出現在不同路徑／快照，因此不將 sha256 設唯一。
相同快照路徑若內容變更，登錄不得靜默覆寫；匯入器比較雜湊並停止查核。
零筆合法資料可記 record_count=0，但零位元組檔案是否為合法 CSV 由標頭檢查決定。
登錄只接受解析完成的檔案；解析失敗記於匯入執行錯誤，不以未知筆數冒充 0。

relative_path 由程式確認為根目錄內的相對路徑，拒絕絕對路徑、.. 路徑越界及符號連結越界。
V4 的非空限制不取代這些檔案系統檢查。
兩張表皆無自動更新時間或可變驗證狀態，登錄後不任意更新來源身分；驗證結果歸屬執行批次。

## 後續表的責任與關聯

下列是後續 migration 的配置方向，不包含在 V4，詳細欄位與限制另行定稿。

| 表／結構 | 關聯及責任 |
| --- | --- |
| import_runs | 參照 snapshot；保存程式版本、轉換／品質／範圍規則版本、狀態、起訖時間與失敗摘要；每次重試為新執行 |
| player_sources | 執行、來源檔案與紀錄序號對應 players 的複合鍵 |
| ranking_sources | 執行、來源位置對應 rankings 的複合鍵；一致重複列都要留對照 |
| match_sources | 執行、來源位置對應 matches.id；來源位置不是跨快照比賽鍵 |
| quality_issues | 執行、來源位置、規則代碼、欄位／角色、原因、影響用途與處理狀態；可在正式列不存在時保存 |
| tournament_decisions | 執行／規則版本、tour/tourney_id、INCLUDE/EXCLUDE/REVIEW、分析系列、命中規則與證據 |

採分開的三種來源對照，讓正式資料的外鍵可被資料庫驗證，不使用無法約束的通用 entity_id。
相同來源位置可在不同規則版本的執行重新處理；同一次執行內不能重複建立相同對照。
對照與品質紀錄的檔案必須屬於該執行快照，紀錄序號須在 1～record_count 內；
跨表一致性如何用複合外鍵與匯入驗證分工，在後續表設計時明確規定。
同名規則的版本不能只留可覆寫文字：保存對應 Git 版本或規則內容雜湊，能還原當時規則。

## 正式表及發布界線

目前三張正式表未包含批次版本，不能同時代表多個快照。
來源對照記錄某次執行的來源，不表示目前正式欄位仍與歷史快照完全一致。
歷史結果須由保留的原檔與規則版本重建；若未來需要直接查詢歷史發布版本，需另設版本化儲存。

首次匯入先在獨立候選資料庫完成驗證，不直接向供 API 使用的資料庫逐列寫入。
品質與範圍規則尚未實作前，不開放統計查詢；沒有問題列不代表已驗證通過。
首次發布前必須完成批次成功條件、隔離數核對與回復測試。
後續更新採候選完整快照驗證，切換／回復的具體方式在匯入教學前定稿，不假設三張表可原地安全覆寫。

## V4 驗收範圍

V4 已建立 source_snapshots、source_files。
已測試快照／路徑唯一性、外鍵、格式及非負值，確認相同雜湊可跨檔案保存。
測試資料只用虛構路徑與雜湊；表限制通過不能證明真實檔案或備份存在。
暫不新增依賴，不掃描原始 CSV，也不進行全量匯入。


## 實際驗收結果

使用者完成 SourceRegistrySchemaTest 的 14 個案例並單獨執行通過。
整套移除 DB_* 變數的 clean verify 共 62 個案例通過，JAR 建置與 repackage 成功。
agent 已核對 migration 與測試內容，未代為修改程式或重跑測試。
未登錄真實來源檔案、未驗證備份還原，也未驗證開發資料庫套用 V4 或執行本次 JAR。
下一步為 import_runs 的狀態與規則版本設計，再安排 migration；不直接全量匯入。


## V5 匯入執行設計與表格實作

import_runs 每列代表一次執行，參照一個 source_snapshots；重試新增列，不覆寫舊結果。
同一快照與相同規則可有多次執行，因此不對這組欄位加唯一限制。
不重複產生正式資料的冪等性仍由後續匯入流程負責，批次表本身不保證冪等。

| 欄位 | 型別方向 | 用途 |
| --- | --- | --- |
| id | bigserial | 執行主鍵 |
| snapshot_id | bigint，必填 | 來源快照外鍵，不連帶刪除 |
| importer_commit_sha | varchar(40)，必填 | 匯入程式的 Git commit，小寫十六進位 |
| transform_rules_sha256 | varchar(64)，必填 | 轉換規則內容版本 |
| quality_rules_sha256 | varchar(64)，必填 | 品質規則內容版本 |
| scope_rules_sha256 | varchar(64)，必填 | 賽事範圍規則內容版本 |
| status | text，必填，預設 RUNNING | 執行狀態 |
| started_at | timestamptz，必填，預設現在 | 執行開始時間 |
| finished_at | timestamptz，可空 | 執行結束時間，不得早於開始 |
| error_summary | text，可空 | FAILED 時必填非空白摘要，不含密碼或完整連線憑證 |

規則雜湊須能對應保留的規則內容，只有雜湊字串不能還原規則。
匯入程式版本只允許對應可還原的程式內容；正式執行前須檢查未提交修改及記錄其他影響輸出的設定。
檔案清單、標頭、規則雜湊計算方式與備份驗證另由匯入器實作，不在 V5 假裝已驗證。

| 狀態 | finished_at | error_summary | 意義 |
| --- | --- | --- | --- |
| RUNNING | NULL | NULL | 執行中，不能當成可發布成果 |
| SUCCEEDED | 必填 | NULL | 完成本次匯入及驗證，不表示無隔離列，也不代表已發布 |
| FAILED | 必填 | 非空白 | 執行失敗，保留摘要供追查 |

RUNNING 可轉為 SUCCEEDED 或 FAILED；終態不可重開，重試另建一筆。
資料庫 CHECK 只保證目前欄位組合一致，不能保證狀態轉移歷史；後續程式以條件更新及交易控制轉移。
程序中斷可能留下 RUNNING，需由後續復原流程確認後標為 FAILED，不憑單一超時直接宣告成功或失敗。
開始、完成與失敗紀錄不能全部被包在可能回滾的業務寫入交易裡；實作時需確保失敗仍可留下紀錄。

不在 V5 加入發布狀態、來源對照或逐列計數。成功所需檔案完整性、隔離數核對與重試恢復，
須在匯入器完成時驗證；單純插入 SUCCEEDED 列不能當成匯入完成證據。
索引規劃 (snapshot_id, started_at)，供依快照查看執行歷史；尚未量測效能，不預先增加其他索引。
V5 migration 與表格限制已由使用者在測試容器驗證；實際狀態轉移及匯入流程尚未實作。


## V5 實際驗收（2026-10-25）

import_runs 已由 V5 建立，18 個專屬案例通過。
使用者執行移除三個 DB_* 變數的 clean verify，整套 80 個案例通過，JAR 建置與 repackage 成功。
agent 已核對 SQL 與測試，未代為重跑測試。
未驗證開發資料庫套用 V5、新 JAR 啟動或實際資料匯入。
CHECK 只保證欄位組合，不保證狀態轉移、重試冪等、程序中斷恢復或發布。
下一步收斂來源對照的跨快照一致性，再安排後續 migration。


## V6 球員來源對照設計與表格實作

player_sources 連結一次匯入執行、一份球員來源檔的一筆 CSV 紀錄，以及正式 players 複合鍵。
主鍵採 (run_id, source_file_id, record_number)；同次執行同來源列只能對應一位球員，
但多個來源列可以對應同一球員，相同位置也可在重試的新 run 再記錄。

| 欄位 | 型別方向 | 意義 |
| --- | --- | --- |
| run_id | bigint，必填 | 匯入執行 |
| snapshot_id | bigint，必填 | run 與檔案必須共用的快照 |
| source_file_id | bigint，必填 | 來源檔案 |
| record_number | bigint，必填且大於零 | 不含標頭的 CSV 紀錄序號 |
| tour | varchar(3)，必填 | 檔案與正式球員共用的巡迴 |
| data_kind | text，必填，預設 PLAYERS | 固定為 PLAYERS，用於複合外鍵 |
| player_id | integer，必填 | 正式球員 ID |

在 import_runs 增加 UNIQUE (id, snapshot_id)，source_files 增加
UNIQUE (id, snapshot_id, tour, data_kind)，供複合外鍵引用。
這兩個限制不增加來源的業務唯一性（id 本已唯一），但讓資料庫可驗證整組關聯；
會增加索引成本，僅在較小的執行／檔案登錄表使用，後續來源對照可重用。
不修改已提交 V4/V5，由 V6 使用 ALTER TABLE 增加限制。

player_sources 的 (run_id, snapshot_id) 必須參照同一 import_runs 列；
(source_file_id, snapshot_id, tour, data_kind) 必須參照同一 source_files 列；
(tour, player_id) 必須參照 players。所有欄位 NOT NULL，避免部分 NULL 跳過複合外鍵檢查。
data_kind CHECK 固定 PLAYERS，因此不能拿排名／比賽檔當球員來源；
共用 snapshot_id 與 tour 可阻擋混用快照及巡迴。

資料庫只限制 record_number > 0；不能用跨表 CHECK 驗證它小於等於 source_files.record_count。
上限、實際 CSV 內容是否對應球員、雜湊及 run 是否仍允許寫入，由後續匯入流程驗證。
來源登錄完成後禁止應用任意修改內容／紀錄數；實際權限與寫入流程另行實作。
品質隔離列可能沒有合法球員，不能硬塞此對照表；留待 quality_issues 保存來源位置。
對照存在也不代表球員已通過所有品質規則或資料已發布。

索引規劃 (tour, player_id) 供球員反查來源，source_file_id 供檔案反查及外鍵維護。
主鍵以 run_id 開頭可支援執行批次查詢，不重複增加相同前綴索引。
V6 與球員來源對照測試已完成；實際 CSV 驗證與來源對照寫入流程尚未實作。


## V6 實際驗收（2026-10-25）

使用者單獨執行 PlayerSourcesSchemaTest，11 個案例通過；
整套移除 DB_* 的 clean verify 共 91 個案例通過，JAR 建置與 repackage 成功。
agent 已核對 SQL 與測試，沒有代改程式或重跑測試。
實際 CSV 內容、紀錄序號上限、來源不可變性及匯入狀態的寫入控制仍待驗證。
未驗證開發資料庫套用 V6 或執行本次新 JAR，也未匯入真實資料。
下一步為排名來源對照，重用 V6 的父表複合唯一限制，保留一致重複排名的全部來源位置。

## V7～V8 來源對照（2026-10-25）

依本輪授權由 agent 實作 ranking_sources、match_sources 與約束測試。
排名參照完整 (tour, ranking_date, player_id)，比賽參照 (match_id, tour)，
V8 新增 matches 的 (id, tour) 唯一限制供複合外鍵使用，不修改 V1～V6。
檔案與 run 必須共用快照，檔案與正式資料共用巡迴；種類分別固定 RANKINGS/MATCHES。
同次執行不能重複來源位置，但合併後的正式資料可保留多個來源，不同執行可重試。
排名衝突隔離列不建立虛構正式排名或來源對照。紀錄上限與內容一致性仍由匯入器檢查。
排名來源 12、比賽來源 11 個專屬測試全通過；此處是單獨測試結果，未宣稱全套驗收。
測試使用人工資料與 Testcontainers；未操作開發資料庫或匯入 CSV。
