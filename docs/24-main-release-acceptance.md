# main 整合與最新版本工程驗收

2026-10-31。使用者授權先完成後續工作順序 3（版本整合）與 4（工程驗收），
沒有要求本輪進行年齡／身高消融或 H2H 新特徵開發。

## 版本整合

已檢查分支與差異，`main` 是 `feat/matchup-probability` 的祖先。
將 main 從 `721bc22` 以 `git merge --ff-only feat/matchup-probability`
更新到 `67d8996`，保留視覺化、介面修正與相遇勝率的各次提交。
沒有 squash、覆寫歷史或刪除功能分支；使用者 `.vscode/settings.json` 保留未追蹤。
工程驗收工具與結果另在 `chore/release-engineering-acceptance` 整理，驗收後提交並合併回 main。

## 驗收範圍

- 沿用已驗收的同一版 Java／前端程式：後端454、前端73、完整瀏覽器21、模型Python3案；lint／build通過。
  合併是 fast-forward，沒有變更這些應用程式碼，不為合併重跑大量相同案例。
- 新增勝率 API 至真實備份的受限堆疊驗收：歷史 Federer／Nadal、近期 Sinner／Alcaraz、Swiatek／Sabalenka。
- 每個路徑區分首次與20筆暖請求；研究 age／correlations 及三種勝率條件各測四worker、合計16次請求。
- 每次勝率回應必須 available=true、兩方機率在0～1且互補，historyBefore等於相遇日期減21日。
- 單路與併發分開標示 target_met；任一未達目標都不能將整體 latency_target_met 標為通過。
- 832MiB／1 CPU 為三服務容器上限之和，不含 OS、Docker、瀏覽器與其他既有網站。
- 只對工具自建的還原容器建立／清理隨機 QA 帳號；不對候選庫建立帳號、匯入或重建資料。
- Apache CSP／自架字型與研究頁桌面／手機瀏覽器檢查，cgroup記憶體峰值、OOM事件及CPU節流。

## 重現指令

工作目錄 `/home/shumin/tennis-analytics`。
需先有已建置的最新 backend JAR／frontend dist，並能使用 Docker。

```bash
timeout 15s docker version
python3 scripts/test/release_report_checks.py
python3 scripts/test/ops_checks.py
python3 scripts/test/visualization_contract_checks.py
python3 scripts/model/test_matchup.py
python3 scripts/ops/budget_stack.py \
  .local/backups/20261029T170402Z-6f9e68/manifest.json \
  --research --matchup
```

本機既有 Chromium 缺 libgbm，實際使用之前解壓的函式庫，沒有新增系統套件：

```bash
LD_LIBRARY_PATH="$PWD/.local/verification/browser-libs/extracted/usr/lib/x86_64-linux-gnu" \
  python3 scripts/ops/budget_stack.py \
  .local/backups/20261029T170402Z-6f9e68/manifest.json \
  --research --matchup
```

`--measure-only` 只代表記錄延遲未達標後仍繼續檢查記憶體及瀏覽器，
不將該次延遲改成通過。工具最終顯示 COMPLETED，是否滿足延遲目標須另看報告。
擴充測試後請求總數超過每分鐘240，因此只在測量批次之間等候窗口，
不關閉或提高正式限流、不把等待算入HTTP延遲，也不重試掩蓋429；實際429仍使驗收失敗。
新增4項驗收門檻測試，防止隱藏併發未達標、忽略OOM／清理失敗或錯誤機率。

## 本輪實際結果

[完整報告](reports/2026-10-31-main-release-acceptance.json)。應用版本67d8996，
JAR SHA-256 `32b457cb59f5af55dae8b522de6241b19c4954ee7e6db5792721082a30acc1b6`。
本輪4項新驗收門檻測試、維運4案、視覺化契約8案、模型契約3案通過；
同一報告再經 `release_report_checks.py --report` 驗證。

13個端點的單路暖p95均低於500ms，約79.02～283.30ms。
以下同key四worker、每組16次請求的p95明確未達500ms目標：

| 條件 | 單路暖 p95 ms | 四路 p95 ms |
| --- | ---: | ---: |
| ATP十年年齡研究 | 100.78 | 1342.16 |
| ATP十年相關矩陣，上限1000 | 89.94 | 2588.29 |
| Federer／Nadal，2019草地五盤 | 185.17 | 1002.39 |
| Sinner／Alcaraz，2026紅土三盤 | 89.18 | 589.37 |
| Swiatek／Sabalenka，2026紅土三盤 | 139.63 | 810.37 |

所有測量請求成功，機率、日期隔離檢查通過；沒有略過503、429或調高逾時。
Apache的研究圖表、自架字型、手機與新勝率延遲載入／訪客權限入口通過，
console及pageerror皆空；登入後勝率操作已由21案實際API端到端驗證。
三容器記憶體峰值為PG320.00、Java309.30、Apache14.68MiB，
峰值不一定同時發生。PG觸及上限並有回收事件，各容器oom及oom_kill皆0。
工具建立的容器與network已清理，cleanup_pending=false，原候選庫及開發程序未改。

**功能／資源檢查通過，併發延遲SLO未達標。** 本輪修正驗收工具，
不再因單路通過就掩蓋併發未達標，也不宣稱已解決所有高負載效能問題。
處置界線：保留預設500／上限1000、十年窗口、有界快取、查詢逾時及限流；
這個資源預算不能承諾四路進階查詢p95<500ms。
若正式上線要求此SLO，需在正式主機評估CPU／記憶體餘裕、來源版本查核及預聚合成本，
再以相同負載驗收，不能只延長逾時或略過來源一致性。

原始證據在 `.local/verification/budget-2dba7733/`，未提交備份或帳號憑證。
備份、容器、應用與測試瀏覽器都在本機；不是正式上線驗收。

## GitHub CI 界線

使用者提供並授權使用 `https://github.com/shuminchang/tennis-analytics.git`，
已設定origin，遠端讀取成功且目前沒有分支。
WSL的HTTPS推送預檢尚缺GitHub登入；已沿用Windows既有Git Credential Manager作本專案helper，
但非互動預檢未能完成，沒有讀取、輸出或要求使用者提供密碼／token。
需操作者完成GitHub瀏覽器登入，才能推送與驗證workflow；本段不是CI通過紀錄。
CI配置保留完整前後端驗證及獨立來源端到端測試，新增release_report_checks。
正式部署、異地備份、正式主機資源及模型個別特徵增益仍不在本輪完成範圍。
