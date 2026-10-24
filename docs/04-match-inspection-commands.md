# 比賽日期與數值探索

整理日期：2026-10-24。以下實際結果來自使用者在教學對話執行的檢查；文件整理沒有重新掃描資料。尚未建業務表、匯入或實作品質排除。

資料目錄：`/home/shumin/tennis-sackmann-archive`。
快照：`83733587353df8a41f2fd4f516147d5aa83f5a8d`。
範圍：ATP、WTA 各 59 份 `*_matches_YYYY.csv`，檔名年份 1968–2026；不含 qual_chall、futures。ATP 199389 筆、WTA 162182 筆。年度檔名不代表已符合網站賽事納入範圍，2026 年也不是完整年度。

所有來源位置以「相對檔名＋標頭後從 1 起算的 CSV 紀錄序號」定位，不是文字行號；正式識別使用 tour 加上 tourney_id、round、match_num。

## 1. 日期

| 巡迴 | 有效日期筆數 | 有效日期範圍 | 缺值／非八位 ASCII 數字／不存在日期 |
| --- | ---: | --- | --- |
| ATP | 199389 | 1967-12-28～2026-05-25 | 全部 0 |
| WTA | 162182 | 1967-12-25～2026-05-25 | 全部 0 |

`tourney_date` 可轉 date；它是賽事開賽日，不是每場實際比賽日。1968 年檔案內的前一年日期如下：

| 巡迴 | 賽事 ID | 名稱 | 日期 | 筆數 |
| --- | --- | --- | --- | ---: |
| ATP | 1968-T101 | Bombay | 19671228 | 7 |
| ATP | 1968-T102 | Calcutta | 19671228 | 7 |
| WTA | 1968-1038 | Port Elizabeth | 19671225 | 16 |
| WTA | 1968-1039 | Valencia | 19671228 | 21 |

只確認來源如此記載，未查證跨年原因。不依檔名更改日期，不要求日期年份等於檔名年份。

## 2. 年齡

| 巡迴／欄位 | 空值 | 有限數值 | 範圍 | 非正年齡 |
| --- | ---: | ---: | --- | ---: |
| ATP winner_age | 1316 | 198073 | 14.3～58.7 | 0 |
| ATP loser_age | 4659 | 194730 | 14.2～63.6 | 0 |
| WTA winner_age | 2678 | 159504 | -16.5～57.0 | 9 |
| WTA loser_age | 7619 | 154563 | -28.0～67.4 | 17 |

沒有解析失敗、非有限值、超過 ±999.9 或需要超過一位小數的數值；numeric(4,1) 可原樣保存。型別通過不代表年齡合理。

26 個非正年齡涉及 26 場、5 位球員；生日與比賽時間存在矛盾，尚不能判定生日、身分對應或其他欄位何者錯誤。

| WTA 球員 ID | 姓名 | 主檔生日 | 異常數 | 年度檔案：紀錄序號 |
| --- | --- | --- | ---: | --- |
| 213882 | Lena Reichel | 19970517 | 1 | 1969：581 |
| 223077 | Stephanie Johnson | 19711207 | 13 | 1970：772、781、786、788、956；1971：257、315、322、335、402、414、430、444 |
| 200535 | Jo Anne Faull | 19710113 | 5 | 1970：974、1006；1971：271、360、376 |
| 200287 | Miki Yokobori | 19750513 | 2 | 1973：1779；1974：40 |
| 205863 | Veronika Birioukova | 19900920 | 5 | 1974：765、1813、1824、1829、1832 |

處理方向：空值轉 NULL；非正年齡保留來源並標記，不參與年齡分析，不取絕對值、不補零。是否隔離整場與身分查核待定；高正年齡也未經真實性查核。

## 3. 籤表、種子、賽制與時間

| 巡迴／欄位 | 空值 | 整數值 | 整數範圍 | 無法解析 | 零值 |
| --- | ---: | ---: | --- | ---: | ---: |
| ATP draw_size | 835 | 198554 | 2～128 | 0 | 0 |
| ATP winner_seed | 125299 | 74090 | 1～35 | 0 | 0 |
| ATP loser_seed | 161441 | 37948 | 1～35 | 0 | 0 |
| ATP best_of | 0 | 199389 | 1～5 | 0 | 0 |
| ATP minutes | 100428 | 98961 | 0～1146 | 0 | 59 |
| WTA draw_size | 20428 | 141753 | 2～128 | 1 | 0 |
| WTA winner_seed | 99433 | 62524 | 1～96 | 225 | 0 |
| WTA loser_seed | 131618 | 30417 | 1～96 | 147 | 0 |
| WTA best_of | 0 | 162182 | 1～5 | 0 | 0 |
| WTA minutes | 136225 | 25957 | 0～2475 | 0 | 130 |

沒有非有限值、非零小數、超出 smallint 或負值。文字值無法直接存入 smallint。

### 種子文字值

| 原值 | 勝方筆數 | 敗方筆數 |
| --- | ---: | ---: |
| 1F | 73 | 15 |
| 2F | 51 | 16 |
| 3F | 19 | 13 |
| 4F | 24 | 12 |
| 5F | 8 | 5 |
| 6F | 8 | 3 |
| 7F | 9 | 4 |
| 8F | 8 | 4 |
| ALT | 0 | 3 |
| LL | 0 | 8 |
| Q | 15 | 44 |
| WC | 10 | 20 |

合計勝方 225、敗方 147，共 372 個欄位值；相同角色的 entry 全部為空字串。這不能證明來源放錯欄位。保留文字原值，不刪字母、不自動搬到 entry；代碼含義待查。種子只用 smallint 的草稿不足，文字原值與分析用數值的最終欄位配置待定。

### draw_size 的 exho

`wta/wta_matches_1970.csv` 紀錄 1818，鍵 `1970-1093 / RR / 1`：Rondebosch Exho，級別 E，日期 19700406，Billie Jean King 對 Rosie Casals，比分 `6-1 6-0`，best_of=3。draw_size=`exho`。

規劃將數值 draw_size 設 NULL，另存原值、來源與原因；不猜人數。整場納入與否留待級別範圍確認。

### best_of 分布與比分線索

| 巡迴 | 1 | 3 | 5 |
| --- | ---: | ---: | ---: |
| ATP | 36 | 156016 | 43337 |
| WTA | 102 | 161956 | 124 |

沒有 2 或 4；保留 1，不一律改 3，不先限制只能 3、5。

- ATP 1968 紀錄 3775：1968-T103 / R16，比分 `6-36-3`；3851、3852：1968-T110 / RR，比分 `31-24`、`31-26`。
- WTA 1968 紀錄 1838、1839、1840：1968-1095，比分 `7-Oct`、`3-Oct`、`6-Oct`。

以上是來源原文，尚未確認賽制或文字形成原因，不自行還原比分。

### 分鐘數最大五筆（每個巡迴）

| 巡迴／年份：紀錄 | 比賽鍵 | 分鐘 | 比分 |
| --- | --- | ---: | --- |
| ATP 2016：133 | 2016-M001 / QF / 294 | 1146 | 7-6(5) 6-3 |
| ATP 2017：1258 | 2017-0308 / QF / 297 | 987 | 6-4 3-6 6-2 |
| ATP 2010：1731 | 2010-540 / R128 / 60 | 665 | 6-4 3-6 6-7(7) 7-6(3) 70-68 |
| ATP 2018：1838 | 2018-540 / SF / 224 | 396 | 7-6(6) 6-7(5) 6-7(9) 6-4 26-24 |
| ATP 2004：2105 | 2004-520 / R128 / 61 | 393 | 6-4 6-3 6-7(5) 3-6 16-14 |
| WTA 2018：1263 | 2018-1101 / R32 / 281 | 2475 | 6-4 6-2 |
| WTA 2018：291 | 2018-0804 / SF / 298 | 1507 | 7-6(5) 6-3 |
| WTA 2017：2524 | 2017-M024 / R16 / 293 | 1094 | 6-3 4-6 6-0 |
| WTA 1984：2171 | 1984-W-WT-USA-28A-1984 / R32 / 16 | 391 | 6-4 7-6(11) |
| WTA 2018：2607 | 2018-W-FC-2018-G2-AM-B-B-M-TTO-BAH-01 / RR / 2 | 361 | 4-6 6-2 RET |

ATP 1146、987 與 WTA 2475、1507、1094 優先待查，未查證正確分鐘數；沒有訂自動刪除門檻，也沒有驗證其餘長時間值真實性。

零分鐘：ATP 59 筆全部 W/O；WTA 127 筆 W/O，另有三筆：

| WTA 來源 | 比賽鍵 | 比分 |
| --- | --- | --- |
| 2017：299 | 2017-0804 / R32 / 278 | 7-5 6-1 |
| 2019：1853 | 2019-M001 / R32 / 275 | 3-6 6-3 6-4 |
| 2021：2141 | 2021-2044 / QF / 297 | 7-6(4) 6-3 |

W/O 不納入實際時間分析，其他三筆也不作為有效時間樣本，不用零計算平均；保留來源值。不反推所有 W/O 都是零分鐘。

## 4. 發球統計型別

下表每格為「空值／整數值／零值／整數範圍」。所有非空值可用 smallint 保存；沒有解析失敗、非有限值或非零小數。只有 ATP l_bpSaved 有兩個負值。零與負值包含在整數值計數中。

| 欄位 | ATP | WTA |
| --- | --- | --- |
| w_ace | 97462 / 101927 / 4701 / 0～113 | 114356 / 47826 / 7871 / 0～72 |
| w_df | 97463 / 101926 / 14601 / 0～26 | 114395 / 47787 / 4775 / 0～72 |
| w_svpt | 97463 / 101926 / 26 / 0～491 | 114354 / 47828 / 79 / 0～918 |
| w_1stIn | 97463 / 101926 / 26 / 0～361 | 114354 / 47828 / 81 / 0～702 |
| w_1stWon | 97463 / 101926 / 28 / 0～292 | 114354 / 47828 / 81 / 0～351 |
| w_2ndWon | 97463 / 101926 / 59 / 0～82 | 114354 / 47828 / 99 / 0～161 |
| w_SvGms | 97463 / 101926 / 29 / 0～90 | 134199 / 27983 / 461 / 0～25 |
| w_bpSaved | 97463 / 101926 / 16232 / 0～24 | 114359 / 47823 / 5411 / 0～108 |
| w_bpFaced | 97463 / 101926 / 9249 / 0～34 | 114359 / 47823 / 2096 / 0～180 |
| l_ace | 97463 / 101926 / 10092 / 0～103 | 114361 / 47821 / 14086 / 0～40 |
| l_df | 97462 / 101927 / 8223 / 0～26 | 114395 / 47787 / 2573 / 0～36 |
| l_svpt | 97463 / 101926 / 50 / 0～489 | 114355 / 47827 / 78 / 0～972 |
| l_1stIn | 97463 / 101926 / 52 / 0～328 | 114354 / 47828 / 83 / 0～774 |
| l_1stWon | 97463 / 101926 / 75 / 0～284 | 114354 / 47828 / 95 / 0～369 |
| l_2ndWon | 97463 / 101926 / 159 / 0～101 | 114354 / 47828 / 192 / 0～74 |
| l_SvGms | 97463 / 101926 / 58 / 0～91 | 134199 / 27983 / 463 / 0～24 |
| l_bpSaved | 97463 / 101926 / 5111 / -6～28 | 114356 / 47826 / 1801 / 0～81 |
| l_bpFaced | 97463 / 101926 / 266 / 0～38 | 114356 / 47826 / 109 / 0～180 |

各欄缺值數不同；有效樣本應按指標需要的欄位判斷。零計數可有效，空白不可補零；WTA 大值未逐筆核實。

### 破發點關係

| 巡迴／角色 | 任一欄缺值 | 含負值 | saved > faced | 通過 |
| --- | ---: | ---: | ---: | ---: |
| ATP 勝方 | 97463 | 0 | 0 | 101926 |
| ATP 敗方 | 97463 | 2 | 0 | 101924 |
| WTA 勝方 | 114359 | 0 | 0 | 47823 |
| WTA 敗方 | 114356 | 0 | 0 | 47826 |

ATP 1991 檔的兩筆負值皆為 Tokyo Outdoor，日期 19910408：

| 紀錄 | 比賽鍵 | 勝方／敗方 | 比分 | 勝方 SvGms/saved/faced | 敗方 SvGms/saved/faced |
| --- | --- | --- | --- | --- | --- |
| 1084 | 1991-329 / SF / 54 | Ivan Lendl / Jim Courier | 6-4 6-1 | 8/4/4 | 9/-4/0 |
| 1085 | 1991-329 / F / 55 | Stefan Edberg / Ivan Lendl | 6-1 7-5 6-0 | 13/6/8 | 12/-6/2 |

不因 faced-saved 得到 4、8 就認定可修復；敗方相關破發指標標記無效待查，不取絕對值、不補零。

### 一發、二發關係

檢查 A：1stIn ≤ svpt；B：1stWon ≤ 1stIn；C：2ndWon ≤ svpt-1stIn。各項只使用自身必要欄位，缺值跳過。負值跳過與前提不成立皆為 0。

| 巡迴／角色／規則 | 通過 | 違反 | 缺值跳過 |
| --- | ---: | ---: | ---: |
| ATP 勝方 A | 101926 | 0 | 97463 |
| ATP 勝方 B | 101926 | 0 | 97463 |
| ATP 勝方 C | 101925 | 1 | 97463 |
| ATP 敗方 A、B、C（各項） | 101926 | 0 | 97463 |
| WTA 勝方 A | 47828 | 0 | 114354 |
| WTA 勝方 B、C（各項） | 47827 | 1 | 114354 |
| WTA 敗方 A | 47827 | 0 | 114355 |
| WTA 敗方 B | 47827 | 1 | 114354 |
| WTA 敗方 C | 47826 | 1 | 114355 |

5 項違反集中於 2 場，不是 5 場：

- ATP 2024 紀錄 1359，2024-520 / R64 / 345，Roland Garros，20240527，Tomas Machac 對 Mariano Navone，比分 `6-2 6-1 3-6 1-6 6-1`，best_of=5，minutes 空白。
- WTA 2023 紀錄 2754，2023-W-FC-2023-FLS / RR / 104，BJK Cup Finals，20231107，Marie Bouzkova 對 Viktorija Golubic，比分 `6-4 6-4`，best_of=3，minutes 空白。

| 欄位 | ATP 勝／敗 | WTA 勝／敗 |
| --- | --- | --- |
| ace | 2 / 3 | 1 / 0 |
| df | 5 / 7 | 1 / 2 |
| svpt | 135 / 122 | 46 / 36 |
| 1stIn | 122 / 109 | 34 / 28 |
| 1stWon | 52 / 48 | 57 / 44 |
| 2ndWon | 21 / 10 | 19 / 14 |
| SvGms | 19 / 19 | 10 / 10 |
| bpSaved | 7 / 4 | 5 / 1 |
| bpFaced | 14 / 14 | 7 / 5 |

ATP 勝方二發得分 21 大於機會 13；WTA 雙方一發得分超過進球數，二發得分超過機會數。未確認哪個欄位錯誤，不交換或猜測數字。涉及矛盾欄位的指標排除於分析，保留比賽結果；沒有決定隔離整場。其餘統計關係尚未全部驗證。

## 5. 比賽排名與積分

| 巡迴／欄位 | 空值 | 整數值 | 範圍 |
| --- | ---: | ---: | --- |
| ATP winner_rank | 35801 | 163588 | 1～2101 |
| ATP winner_rank_points | 83024 | 116365 | 0～16950 |
| ATP loser_rank | 44255 | 155134 | 1～2173 |
| ATP loser_rank_points | 84735 | 114654 | 0～16950 |
| WTA winner_rank | 51628 | 110554 | 1～1503 |
| WTA winner_rank_points | 56973 | 105209 | 0～13615 |
| WTA loser_rank | 55309 | 106873 | 1～1556 |
| WTA loser_rank_points | 59490 | 102692 | 0～13615 |

解析失敗、非有限值、非零小數、integer 溢位、非正排名及負積分皆為 0。維持 integer；空白轉 NULL，積分零保留。尚未對照週排名，不能宣稱跨資料集一致。

## 6. 比賽身高

| 巡迴／欄位 | 空值 | 整數值 | 範圍 | 待查 |
| --- | ---: | ---: | --- | ---: |
| ATP winner_ht | 16748 | 182641 | 3～211 | 6 |
| ATP loser_ht | 29369 | 170020 | 3～211 | 6 |
| WTA winner_ht | 42933 | 119249 | 146～190 | 0 |
| WTA loser_ht | 60163 | 102019 | 146～190 | 0 |

無解析失敗、非有限值、小數、smallint 溢位或非正值。低於 100 或高於 250 公分標記待查。

12 個異常值涉及 12 場、3 位 ATP 球員，均與主檔一致：

| 球員 ID／姓名 | 原值 | 年度檔案：紀錄序號 |
| --- | ---: | --- |
| 106410 Jorge Brian Panta Herreros | 3 | 2012：2510；2013：340、1016；2016：692；2018：323 |
| 134087 Johannes Ingildsen | 15 | 2021：2724；2022：2856、2873 |
| 209907 Viacheslav Bielinskyi | 71 | 2022：2839；2023：2923；2024：2917；2025：2902 |

主檔一致不能證明正確；保留原值，未釐清前排除身高分析，不排除整場。未決定正式比賽表是否保存身高。

## 7. 檢查指令與重現方式

本輪按日期、年齡、基本數值、種子與 entry、賽制、時間、發球統計、關係檢查、排名積分、身高逐步執行。使用 Python 標準庫 csv、Decimal、datetime，逐筆讀取，不修改來源。

下列是整理後的通用重現程式，不是原始逐輪程式的逐字抄本；只做語法檢查，未重新全量執行。已驗證結果不必重跑。需要新快照驗證時，先確認快照與欄位，再由使用者執行。

工作目錄：`/home/shumin/tennis-sackmann-archive`。

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PYCODE'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

small = ["draw_size", "winner_seed", "loser_seed", "best_of", "minutes",
         "winner_ht", "loser_ht"]
suffixes = ("ace", "df", "svpt", "1stIn", "1stWon", "2ndWon",
            "SvGms", "bpSaved", "bpFaced")
small += [f"{s}_{f}" for s in ("w", "l") for f in suffixes]
integer = [f"{s}_{f}" for s in ("winner", "loser")
           for f in ("rank", "rank_points")]
ages = ["winner_age", "loser_age"]
fields = small + integer + ages
rules = [("破發", ("bpSaved", "bpFaced")),
         ("一發進球", ("1stIn", "svpt")),
         ("一發得分", ("1stWon", "1stIn")),
         ("二發得分", ("2ndWon", "svpt", "1stIn"))]

for tour in ("atp", "wta"):
    files = sorted(p for p in Path(tour).glob(f"{tour}_matches_*.csv")
                   if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name))
    if not files:
        raise RuntimeError(f"{tour}: 找不到年度檔案")
    counts = defaultdict(Counter)
    samples = defaultdict(list)
    bounds = {}
    dates = []
    relations = defaultdict(Counter)
    best_of = Counter()
    seeds = Counter()
    zero_scores = Counter()
    total = 0

    def issue(field, reason, path, record, raw):
        counts[field][reason] += 1
        key = (field, reason)
        if len(samples[key]) < 3:
            samples[key].append((str(path), record, raw))

    for path in files:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            if not set(fields + ["tourney_date", "winner_entry", "loser_entry", "score"]).issubset(reader.fieldnames or []):
                raise ValueError(f"{path}: 欄位不符")
            for record, row in enumerate(reader, 1):
                total += 1
                if None in row or any(v is None for v in row.values()):
                    raise ValueError(f"{path}:{record}: 欄位數不符")
                raw = row["tourney_date"]
                try:
                    if not re.fullmatch(r"[0-9]{8}", raw):
                        raise ValueError("日期格式")
                    date = datetime.strptime(raw, "%Y%m%d").date()
                except ValueError:
                    issue("tourney_date", "無效日期", path, record, raw)
                else:
                    counts["tourney_date"]["有效"] += 1
                    dates = [min(dates[0], date), max(dates[1], date)] if dates else [date, date]
                values = {}
                for field in fields:
                    raw = row[field]
                    if raw == "":
                        counts[field]["空值"] += 1
                        continue
                    try:
                        n = Decimal(raw)
                    except InvalidOperation:
                        issue(field, "無法解析", path, record, raw)
                        if field.endswith("_seed"):
                            side = field.split("_")[0]
                            seeds[(side, raw, row[f"{side}_entry"])] += 1
                        continue
                    if not n.is_finite():
                        issue(field, "非有限", path, record, raw)
                        continue
                    if field not in ages and n != n.to_integral_value():
                        issue(field, "非整數", path, record, raw)
                        continue
                    values[field] = n
                    counts[field]["有限年齡" if field in ages else "整數值"] += 1
                    lo, hi = bounds.get(field, (n, n))
                    bounds[field] = (min(lo, n), max(hi, n))
                    limit = Decimal("999.9") if field in ages else (32767 if field in small else 2147483647)
                    lower = -limit if field in ages else -limit - 1
                    if not lower <= n <= limit:
                        issue(field, "超出型別範圍", path, record, raw)
                    if field in ages and n * 10 != (n * 10).to_integral_value():
                        issue(field, "超過一位小數", path, record, raw)
                    if n == 0:
                        counts[field]["零值"] += 1
                    if n < 0:
                        issue(field, "負值", path, record, raw)
                    if n <= 0 and (field in ages or field.endswith("_rank")):
                        issue(field, "非正值", path, record, raw)
                    if field.endswith("_ht") and not 100 <= n <= 250:
                        issue(field, "身高待查", path, record, raw)
                if "best_of" in values:
                    best_of[values["best_of"]] += 1
                if values.get("minutes") == 0:
                    zero_scores[row["score"]] += 1
                for side in ("w", "l"):
                    for label, needed in rules:
                        key = (side, label)
                        names = [f"{side}_{f}" for f in needed]
                        if any(row[f] == "" for f in names):
                            relations[key]["缺值"] += 1
                            continue
                        if any(f not in values for f in names):
                            relations[key]["格式不符"] += 1
                            continue
                        nums = [values[f] for f in names]
                        if any(n < 0 for n in nums):
                            relations[key]["負值"] += 1
                            continue
                        if len(nums) == 3 and nums[2] > nums[1]:
                            relations[key]["前提不成立"] += 1
                            continue
                        limit = nums[1] if len(nums) == 2 else nums[1] - nums[2]
                        ok = nums[0] <= limit
                        relations[key]["通過" if ok else "違反"] += 1
                        if not ok:
                            issue(f"{side}:{label}", "關係矛盾", path, record,
                                  {f: row[f] for f in names})
    print(tour.upper(), "檔案", len(files), "筆數", total, "日期範圍", dates)
    for field, result in counts.items():
        print(field, dict(result), "範圍", bounds.get(field))
    print("best_of", dict(best_of))
    print("非數字種子與 entry", dict(seeds))
    print("零分鐘比分", dict(zero_scores))
    for key, result in relations.items():
        print("關係", key, dict(result))
    for key, result in samples.items():
        print("範例", key, result)
PYCODE
```

### 依來源位置取出完整原值

以下以兩場發球矛盾為例；可將 targets 改為本文其他「相對檔名、紀錄序號」，追查年齡、身高、分鐘數或文字值。原始教學另用 heapq 保留各巡迴最大的五筆分鐘數，沒有將完整資料載入記憶體。

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PYCODE'
import csv
from pathlib import Path

targets = [("atp/atp_matches_2024.csv", 1359),
           ("wta/wta_matches_2023.csv", 2754)]
for filename, target in targets:
    found = False
    with Path(filename).open(encoding="utf-8-sig", newline="") as file:
        for record, row in enumerate(csv.DictReader(file), 1):
            if record == target:
                print(filename, record)
                for field, raw in row.items():
                    print(f"{field}: {raw!r}")
                found = True
                break
    if not found:
        raise ValueError(f"{filename}: 找不到紀錄 {target}")
PYCODE
```

## 8. 交接與未完成事項

- 本輪沒有工具執行錯誤需要修復；發現的是來源型別例外與資料品質矛盾，不可用強制轉型掩蓋。
- 日期、主要數值的型別與上述關係已檢查；不代表資料全面正確，也不代表階段 1 完成。
- 下一個教學主題建議為賽事級別分布與納入範圍，再處理未知球員；每次仍只做一個可驗證小步驟。
- 待查：種子代碼含義、生日與身分矛盾、極端時間、比分文字、發球異常與其他尚未驗證關係；主檔與比賽欄位差異也未全面比對。
- 待設計：來源保存、欄位品質標記、有效分析樣本、隔離層級、種子原值與數值欄位、最終 DDL 與欄位對照表。
- 週排名日期完整性、WTA tours 含義與排名衝突來源仍待查；ATP 3419216、WTA 2153811 仍是預期排名筆數，未實際匯入。
- 本輪不進入 Flyway 建表、匯入或前後端串接。既有規格 SQL 是草稿，需完成上述設計後再修訂與執行。
