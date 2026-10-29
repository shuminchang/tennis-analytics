# 球員比賽紀錄

使用者授權自主完成此階段。已將 feat/player-ranking-history 快轉合併 main（305192b），再從 main 建立 feat/player-match-history。
目前完成匯入器、API、前端及測試；真實匯入結果另列於文末。本功能不計算勝率、冠軍、H2H 或 Elo。

## 範圍與來源

處理已驗證快照內的 ATP/WTA 年度單打檔，不擴及雙打、資格賽、挑戰賽及其他檔案。
規則依 docs/05-tournament-scope.md：104 個已確認例外優先，其次套用巡迴／級別通用規則，其餘 REVIEW。
例外已轉成版本化 tournament-exceptions-v1.csv，保存巡迴、完整賽事 ID、預期來源級別、狀態與分析分類；不依賽事名稱猜分類。
例外的來源級別若改變、或同賽事 ID 出現多組日期／名稱／級別，整批停止供查核，不默默套用舊規則。

- ATP A/G/M、WTA G/I/P/PM/T1～T5 及兩巡迴 D 納入候選，D 標為 TEAM_SINGLES。
- F/O 以已確認例外處理；歷史 W/CC 等未查明者 REVIEW。
- E/J、WTA 35+H/50+H 及明確 Next Gen／示範賽等例外 EXCLUDE。
- 所有可建立識別與球員參照的來源紀錄都可保存至 matches，包含 REVIEW／EXCLUDE。每個賽事保存 tournament_decisions。
- 預設 API 只讀 INCLUDE 且無 OPEN MATCH_HISTORY 品質影響的比賽，涵蓋不代表完整生涯。

## 轉換與品質

實作 MatchConverter，保存原始 CSV，不在匯入時修改來源。
必要欄位 (tour,tourney_id,round,integer match_num)、有效賽事日期、正整數勝敗 ID 必須可解析；無效來源隔離。
球員須已從同快照成功匯入，缺少參照時隔離，不自建占位球員。

- 同鍵完全一致的來源合併，所有位置寫入 match_sources；同鍵原始欄位不同則整組隔離，不任取一筆。
- 文字種子、entry、來源級別與比分原樣保存；比賽來源身高和主檔身高分開。
- 選填數值無法表示時轉 NULL 並留品質問題及 raw JSON；有效零與缺值分開。
- 保留可表示的負發球統計，但以 SERVE_STATS 排除相關指標；檢查一發／二發／破發關係，不因此排除有效比賽結果。
- 年齡須精確表示為 numeric(4,1)，不自動四捨五入；非正值僅標記 AGE_ANALYSIS。
- 異常身高與已查明的分鐘數、比分保留並標記，未發明通用極端值刪除門檻。
- 未知輪次、場地標記待查，保留來源文字。
- 勝敗同 ID、明確占位姓名、主檔生日晚於賽事日期等，保存比賽及來源，但加 MATCH_HISTORY／PERSONAL_STATS 影響，預設不顯示。
- 部分姓名含 unknown 不自動判為占位，不因文字匹配抹除整位球員生涯。

原始列以不可變封存、來源檔雜湊及資料紀錄序號追溯；品質問題另外保存原值證據。
match-rules-v1.txt 的 SHA-256 記為轉換／品質規則；tournament-policy-v1.txt 加例外 CSV 的內容雜湊記為賽事範圍規則。

## 匯入交易與入口

MatchImportService／MatchImportCli 與 backend/import-matches 提供可操作入口，正常啟動網站不會觸發匯入。
要求 IMPORT_TARGET=candidate 與 DB_* 明確設定；只向本機候選資料庫執行，不操作部署網站。

1. 驗證封存並登錄來源，檢查同快照成功球員批次與無其他 RUNNING。
2. 先提交 MATCHES／RUNNING 批次。
3. 串流 CSV，每 500 列批次送入 PostgreSQL 暫存表；CLI 啟用 reWriteBatchedInserts。
4. 在同一筆交易內彙整重複、參照、身分、欄位品質與賽事分類，寫正式資料／對照並設 SUCCEEDED。
5. 失敗全數回滾資料，另記 FAILED。相同版本與快照成功重試回傳舊批次；不同版本不覆寫，需新候選資料庫。

與球員／排名匯入共用 advisory lock。中斷 RUNNING 要先確認程序停止及回滾，不能自動接管或標成功。
分批傳輸不代表逐批發布；資料庫需足夠暫存磁碟及 WAL 空間。正常來源 raw JSON 僅存在暫存表，品質問題才另存 raw；不可變封存仍需保存。

## API 與列表語意

`GET /tennis/api/v1/players/{tour}/{playerId}/matches?year=2024&page=0&size=20`

- year 可省略；有值為 1～9998。以 tourney_date 的年篩選，不以 CSV 檔名判年。
- page 0～10000、size 1～100；依賽事日期降冪及來源鍵穩定排序，不宣稱同賽事內的真實時間順序。
- 回應 items／total／page／size／available；available=false 為尚無成功比賽匯入，空 items 則為這些條件沒有可顯示紀錄。
- 回傳賽事日期、名稱、來源級別、場地、輪次、來源比分、結果狀態及對手。
- 球員不可見／不存在為 404；無效參數 400／INVALID_MATCH_QUERY；資料存取失敗 503／MATCHES_UNAVAILABLE，無 SQL／堆疊外洩。
- Service 以唯讀 REPEATABLE_READ 交易保持球員可見性、總數與列表一致，逾時 10 秒。

日期是來源賽事日期，不是每場實際比賽日期。對手姓名來自球員主檔，不宣稱歷史姓名完整。
W/O 顯示「不戰晉級／未出賽」，RET 與 DEF 顯示來源勝負及退賽／判負結束狀態。
UNK／缺少比分顯示來源勝敗方，不推斷一般完賽；可疑比分另顯示待查。尚未實作完整比分解析器。

前端沿用 frontend-design 技能與既有排版；加入年份篩選、分頁、重新整理保留條件、雙語、載入／未匯入／空結果／重試及對手詳情連結。
matchYear／matchPage 存於 URL；返回搜尋移除這兩者與 rankingPage，保留原搜尋 q／tour／size／page。手機表格可橫向捲動並提供提示。

## 自動驗收

專案根目錄：

```bash
env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml clean verify
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
LD_LIBRARY_PATH=/tmp/tennis-browser-libs/extracted/usr/lib/x86_64-linux-gnu python3 scripts/test/player_search_e2e.py
```

後端全套 399 案、前端 36 案通過，lint、前端建置及 JAR 打包成功；瀏覽器 5 案通過。
涵蓋文字種子、負統計、精確年齡、零／缺值、欄位關係、重複合併／衝突隔離、賽事例外、生日／占位／同 ID 排除、W/O／退賽／未知結果、年份／分頁、404／400／503、失敗回滾與既有功能回歸。
端到端經人工 Git／封存、三種匯入 CLI、獨立 PostgreSQL、實際 API 至頁面；手機版面已目視檢查。測試資料不寫入 tennis_candidate。
