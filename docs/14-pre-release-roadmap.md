# 後續九階段交付追蹤

使用者於 2026-10-30 授權自主完成第 1～9 階段，依功能分階段 commit，保留現有排版及 .vscode/settings.json，不部署或推送。
工作分支 feat/pre-release-features，基於比賽紀錄 9cb34a1。前端沿用專案 frontend-design 技能。

| 階段 | 功能 | 狀態 |
| --- | --- | --- |
| 1 | 統計資格與球員摘要 | 已驗收 |
| 2 | 賽事列表與歷屆決賽勝方 | 已驗收 |
| 3 | 分場地／年份及發球指標 | 已驗收 |
| 4 | 排名圖表／指定日期榜單／首頁 | 已驗收 |
| 5 | 登入、角色、CSRF、帳號管理 | 待實作 |
| 6 | 球員比較與個人收藏 | 待實作 |
| 7 | H2H 與 Wilson 信賴區間 | 待實作 |
| 8 | 版本化 Elo 預計算與圖表 | 待實作 |
| 9 | 效能／CI／備份還原／更新流程 | 待實作 |

Docker 中斷時先保存檔案與日誌，不將逾時當成通過。恢復後確認程序、批次與資料庫狀態；不自動刪除或重置真實資料。每個階段使用獨立測試容器驗證，已通過的案例除程式／規則改變外不重跑。
真實資料操作只對既有本機候選庫；正式部署留待第 10 階段。外部備份目的地已向使用者詢問，未提供前不宣稱完成外部備份。

## 階段 1：stats-v1

新增 V12 的 v_display_matches／v_stat_matches，將展示與統計樣本資格分開。排除 W/O、DEF、UNK、空白／不符合第一版分數語法的比分與 OPEN 個人／比分品質問題；帶有實際得分的 RET 納入。沒有樣本回傳 null 勝率，不冒稱完整生涯。新增摘要 Repository／Service／Controller 與詳情卡片。
命令：`env -u DB_URL -u DB_USERNAME -u DB_PASSWORD ./backend/mvnw -f backend/pom.xml -Dtest=StatsTest test`；人工 PostgreSQL 案例通過。前端 lint／build 及 37 案通過。尚未對真實庫套用 V12，留待最終本機驗收。

## 階段 2：賽事與來源決賽勝方

V13 v_event_final_winners 僅對單一合格 F 決賽且非 D 事件提供來源勝方；不猜測缺少或多個決賽、不混用團體冠軍。新增賽事列表／年份名稱篩選、賽果 API 與雙語頁面。TournamentTest 在獨立 PostgreSQL 驗證一般決賽與 D 排除，通過；前端 lint／build 通過。
使用者已確認第 9 階段先做本機備份及實際還原，外部目的地待定。

## 階段 3：表現分組

依 stats-v1 提供場地／年份樣本、勝場與發球分得分率、ACE／雙誤率。每項分開檢查必要欄位及 SERVE_STATS 角色影響，按分數總和加權，回應各指標樣本數；零分母為 null。AnalysisTest 用 72/110 手算案例與缺值／零分母驗證通過；前端 lint／build 通過。指令：`./backend/mvnw -f backend/pom.xml -Dtest=AnalysisTest test`。

## 階段 4：排名與首頁

新增公開排名觀測 view、指定日期榜單、最多 5,000 筆的球員圖表與首頁匯入時間摘要。未指定日期使用來源最新日期，指定缺少日期不借用前一期；積分缺值斷線、名次軸反轉。RankingBrowseTest 精確日期／缺值通過，前端 39 案、lint／build 通過。
