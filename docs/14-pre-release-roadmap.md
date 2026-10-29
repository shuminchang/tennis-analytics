# 後續九階段交付追蹤

使用者於 2026-10-30 授權自主完成第 1～9 階段，依功能分階段 commit，保留現有排版及 .vscode/settings.json，不部署或推送。
工作分支 feat/pre-release-features，基於比賽紀錄 9cb34a1。前端沿用專案 frontend-design 技能。

| 階段 | 功能 | 狀態 |
| --- | --- | --- |
| 1 | 統計資格與球員摘要 | 已驗收 |
| 2 | 賽事列表與歷屆決賽勝方 | 已驗收 |
| 3 | 分場地／年份及發球指標 | 已驗收 |
| 4 | 排名圖表／指定日期榜單／首頁 | 已驗收 |
| 5 | 登入、角色、CSRF、帳號管理 | 已驗收 |
| 6 | 球員比較與個人收藏 | 已驗收 |
| 7 | H2H 與 Wilson 信賴區間 | 已驗收 |
| 8 | 版本化 Elo 預計算與圖表 | 已驗收 |
| 9 | 效能／CI／備份還原／更新流程 | 本機驗收完成；外部備份待定 |

Docker 中斷時先保存檔案與日誌，不將逾時當成通過。恢復後確認程序、批次與資料庫狀態；不自動刪除或重置真實資料。每個階段使用獨立測試容器驗證，已通過的案例除程式／規則改變外不重跑。
真實資料操作只對既有本機候選庫；正式部署留待第 10 階段。使用者指定先完成本機備份及還原，外部目的地待定；本輪未建立外部備份。

## 階段 1：stats-v1

新增 V12 的 v_display_matches／v_stat_matches，將展示與統計樣本資格分開。排除 W/O、DEF、UNK、空白／不符合第一版分數語法的比分與 OPEN 個人／比分品質問題；帶有實際得分的 RET 納入。沒有樣本回傳 null 勝率，不冒稱完整生涯。新增摘要 Repository／Service／Controller 與詳情卡片。
命令：`env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml -Dtest=StatsTest test`；人工 PostgreSQL 案例通過。前端 lint／build 及 37 案通過。當時尚未對真實庫套用；第 9 階段已完成 V12～V18 本機套用。

## 階段 2：賽事與來源決賽勝方

V13 v_event_final_winners 僅對單一合格 F 決賽且非 D 事件提供來源勝方；不猜測缺少或多個決賽、不混用團體冠軍。新增賽事列表／年份名稱篩選、賽果 API 與雙語頁面。TournamentTest 在獨立 PostgreSQL 驗證一般決賽與 D 排除，通過；前端 lint／build 通過。
使用者已確認第 9 階段先做本機備份及實際還原，外部目的地待定。

## 階段 3：表現分組

依 stats-v1 提供場地／年份樣本、勝場與發球分得分率、ACE／雙誤率。每項分開檢查必要欄位及 SERVE_STATS 角色影響，按分數總和加權，回應各指標樣本數；零分母為 null。AnalysisTest 用 72/110 手算案例與缺值／零分母驗證通過；前端 lint／build 通過。指令：`./backend/mvnw -f backend/pom.xml -Dtest=AnalysisTest test`。

## 階段 4：排名與首頁

新增公開排名觀測 view、指定日期榜單、最多 5,000 筆的球員圖表與首頁匯入時間摘要。未指定日期使用來源最新日期，指定缺少日期不借用前一期；積分缺值斷線、名次軸反轉。RankingBrowseTest 精確日期／缺值通過，前端 39 案、lint／build 通過。

## 階段 5：Session 與角色

新增 V15 app_users／audit_log、BCrypt(12)、Session、CSRF、MEMBER／ADVANCED／ADMIN 與匿名訪客。5 次錯誤鎖定 15 分鐘，登入成功重設；停用及角色變更逐請求生效。密碼不放 URL／日誌／localStorage，不提供預設帳號或自由註冊。登入／登出後重新載入清除私人查詢快取。
GET /auth/csrf 取得遮罩 token，每次寫入前重取；POST /auth/login 為表單、POST /auth/logout；GET /auth/me；管理員可建立／停用帳號。CLI backend/account-admin 只接受互動式終端輸入密碼，需另設 DB_*，不會自動建立真實帳號。Cookie HttpOnly、SameSite=Lax，正式環境須 COOKIE_SECURE=true。
驗證：AccountTest 的 CSRF／權限／鎖定／停用流程與既有 Controller／Health 共 8 案通過；前端 41 案、lint／build 通過。既有 MVC slice 測試專測 Controller 而停用 filters；安全整合測試保留完整 filter chain。採用 [Spring Security CSRF 官方流程](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)。限流與正式設定留在階段 9。

## 階段 6：比較與收藏

會員可比較同巡迴 2～4 個不同 ID，使用相同年份／場地與 stats-v1。收藏以 user_id 作主鍵的一部分，所有讀寫都由 Session 身分取得，不接受他人帳號參數；最多 200 筆，重複新增冪等。新增比較頁、收藏頁及詳情收藏按鈕，寫入保留 CSRF。MemberTest／StatsTest 共 3 案通過，驗證跨帳號隔離、重複收藏、相同篩選與重複 ID 拒絕；前端 41 案、lint／build 通過。

## 階段 7：H2H

ADVANCED／ADMIN 可查同巡迴不同球員，以 stats-v1、含頭尾的賽事日期／場地條件計算總樣本、雙方勝場、分場地／年份及 Wilson 95% 區間；未達 5 場提示樣本過少，零場比例／區間為 null。H2HTest 手算 1/2 的區間 0.094531～0.905469、訪客／會員拒絕與進階允許共 2 案通過；前端 lint／build 通過。

## 階段 8：elo-v1

V17 保存模型批次、每場前後評分與各場地目前觀測。初始 1500，期望勝率採 400 分 logistic，各球員 K=250/(自身該分類已賽場數+5)^0.4；不同經驗值的更新不必零和。OVERALL 與四場地獨立計算，不加權混合。輸入採 stats-v1，排序為 tour、賽事日期、賽事 ID、語意輪次、來源 match_num、id；日期是近似，不捏造實際時間。
逐批串流與有界 JDBC batch，不將所有比賽載入 Java。模型規則及有序輸入各保存 SHA-256，同輸入重用舊成功模型；失敗回滾資料另存 FAILED。與所有匯入器共用鎖，RUNNING 模型期間拒絕新匯入。ADVANCED／ADMIN 查詢圖表，ADMIN 才可重建；另有 backend/rebuild-elo CLI（明確 IMPORT_TARGET=candidate）。
EloTest 與三種匯入器及 StatsTest 共 15 案通過，包括手算、排除樣本、重用、同 ID 失敗回滾與舊流程。補齊新功能測試的清理，避免跨類別人工資料殘留；前端 41 案、lint／build 通過。第 9 階段已對真實候選庫建立模型：286,774 場、1,140,032 筆歷史。

## 階段 9：工程工具與自動驗證

V18 分析讀模型／索引、Caffeine 有界快取、查詢／連線／JVM 預算、IP 限流、管理狀態頁、CI／Dependabot、來源／隱私說明與自託管字型已完成。全套 413 案、前端 41 案、lint／build、真實測試資料端到端 9 案通過。日誌保存 .local/verification/，GitHub workflow 尚未推送執行。
備份工具使用同步快照、SHA-256、隨機空還原庫與表計數／約束核對；只清理自己新建的還原庫。外部備份依使用者決定待定。真實備份約 108 MB，22 項計數與約束實際還原核對通過。832 MiB／1 CPU 服務容器限制下，經 Apache 的一般暖 p95 <500ms、H2H <1000ms；Elo 首次約 1.59 秒如實保留，不當成所有冷查詢通過。操作與限制見 docs/15-local-release-readiness.md。
