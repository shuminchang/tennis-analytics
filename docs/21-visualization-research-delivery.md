# Phase 11.4～11.7 研究工作台交付

目前：11.4 年齡與勝率已實作，11.5～11.7 進行中。使用者授權自主完成剩餘階段並依階段提交；不推送或正式部署，不修改 .vscode/settings.json。

## 11.4 年齡與勝率

路由 `/tennis/visualizations/research`；API `/tennis/api/v1/visualizations/research-metadata?tour=ATP` 與 `/age-performance?tour=ATP&fromYear=2024&toYear=2024&surface=ALL&binWidth=1&group=overall`。
年份必須成對，最多十年，1800～2100；binWidth 僅 1 或 5；group=overall|surface，場地固定白名單。缺資料不補值，年齡為來源賽事年齡，不能以今日生日推算。
重用 live v_stat_matches（非 V18 過時 eligible），每場生成勝／敗方各一個邏輯觀測。AGE_ANALYSIS 的 WINNER／LOSER 只影響該角色，RECORD 影響雙方；先品質再非空／正值。來源、品質版本與結果同一 read-only REPEATABLE READ 交易，SQL 5 秒上限。來源不一致／RUNNING 回 503；公開資料按 source、run、規則、品質 revision、參數快取 60 秒，各研究 cache 16 keys，cache loader 原子化避免同 key 冷啟動重算。
Wilson 95% 沿用既有 z=1.959963984540054；n<20 標低樣本，不隱藏。曲線的無觀測年齡箱為 null／斷線，不能補零或以區間當因果證據。明示來源年齡未獨立查證與球員重複測量限制。
四場 fixture 的 W/O 排除，兩場有比分 RET 納入：6 個雙視角、年齡有效5、品質排除1；20歲箱1勝/2場，Wilson約9.45%～90.55%。解除角色年齡問題可重新納入，不移除勝負或發球樣本。
驗證：ResearchAgeTest 3 案、前端完整55案、lint／build 通過；SQL與UI同一fixture，未寫入真實候選庫。瀏覽器與真實效能留到11.7整合驗收，不提前宣稱完成。

工作目錄 `/home/shumin/tennis-analytics`：

```bash
scripts/test/backend-check.sh -Dtest=ResearchAgeTest test
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
```
