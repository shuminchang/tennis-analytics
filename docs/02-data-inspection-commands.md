# 資料探索指令附錄

搭配 [完整教學手冊](完整教學步驟與指令.md) 與 [資料建模結論](01-data-model.md) 閱讀。

本附錄第 1～6 節整理早期的六組 Python 檢查，第 8 節補上本輪比賽日期與數值的逐步指令。第 3、4 組以同一段程式的 `SAMPLE_YEAR` 切換，減少重複抄寫；其餘保留相同檢查目的，輸出用語略作統一。這些是只讀探索，不會修改 CSV 或資料庫，不需額外套件。

每次執行前確認工作目錄：

```bash
cd /home/shumin/tennis-sackmann-archive
git rev-parse HEAD
```

本次結果對應快照 `83733587353df8a41f2fd4f516147d5aa83f5a8d`。若版本不同，數字不一定相同。以下 `python3 - <<'PY'` 到最後一行 `PY` 必須整段貼入；引號使 shell 不對 Python 內容進行變數展開。

## 1. 球員基本檢查

檢查筆數、原始 ID 重複、慣用手分布、日期有效性與身高缺值。

```bash
python3 - <<'PY'
import csv
from collections import Counter
from datetime import datetime
from pathlib import Path

for tour in ("atp", "wta"):
    path = Path(tour) / f"{tour}_players.csv"
    ids = Counter()
    hands = Counter()
    missing_dob = 0
    missing_height = 0
    invalid_dates = []
    total = 0

    with path.open(encoding="utf-8-sig", newline="") as file:
        for row in csv.DictReader(file):
            total += 1
            ids[row["player_id"]] += 1
            hands[row["hand"]] += 1
            missing_height += row["height"] == ""
            dob = row["dob"]
            if not dob:
                missing_dob += 1
            else:
                try:
                    if len(dob) != 8 or not dob.isascii() or not dob.isdigit():
                        raise ValueError("Expected YYYYMMDD")
                    datetime.strptime(dob, "%Y%m%d")
                except ValueError:
                    invalid_dates.append((row["player_id"], dob))

    print(f"\n{tour.upper()}")
    print(f"總筆數：{total}")
    print(f"重複 ID 數量：{sum(count > 1 for count in ids.values())}")
    print(f"慣用手分布：{dict(hands)}")
    print(f"缺少生日：{missing_dob}")
    print(f"非空但無效的生日：{len(invalid_dates)}")
    print(f"無效生日範例：{invalid_dates[:10]}")
    print(f"缺少身高：{missing_height}")
PY
```

`DictReader` 以標頭欄位名稱讀資料；`Counter` 計數；`strptime` 檢查日期。空值與非空但無效的日期分開計算。

## 2. 球員型別與代碼補查

100–250 公分只是人工查核範圍，不是硬性排除標準。IOC 和 Wikidata 只檢查格式，不驗證實際存在。

```bash
python3 - <<'PY'
import csv
import re
from pathlib import Path

ids_by_tour = {}
for tour in ("atp", "wta"):
    issues = {
        "無效 ID": [],
        "身高格式或 smallint 範圍錯誤": [],
        "身高待查（低於 100 或高於 250 公分）": [],
        "非空 IOC 格式異常": [],
        "非空 Wikidata ID 格式異常": [],
    }
    ids = set()
    heights = []
    missing_ioc = 0
    missing_wikidata = 0

    with (Path(tour) / f"{tour}_players.csv").open(
        encoding="utf-8-sig", newline=""
    ) as file:
        for row in csv.DictReader(file):
            player_id = row["player_id"]
            if not re.fullmatch(r"[0-9]+", player_id):
                issues["無效 ID"].append(player_id)
            elif not 1 <= int(player_id) <= 2147483647:
                issues["無效 ID"].append(player_id)
            else:
                ids.add(int(player_id))

            height = row["height"]
            if height:
                if not re.fullmatch(r"[0-9]+", height):
                    issues["身高格式或 smallint 範圍錯誤"].append((player_id, height))
                elif not 1 <= int(height) <= 32767:
                    issues["身高格式或 smallint 範圍錯誤"].append((player_id, height))
                else:
                    value = int(height)
                    heights.append(value)
                    if not 100 <= value <= 250:
                        issues["身高待查（低於 100 或高於 250 公分）"].append(
                            (player_id, height)
                        )

            ioc = row["ioc"]
            if not ioc:
                missing_ioc += 1
            elif not re.fullmatch(r"[A-Z]{3}", ioc):
                issues["非空 IOC 格式異常"].append((player_id, ioc))

            wikidata = row["wikidata_id"]
            if not wikidata:
                missing_wikidata += 1
            elif not re.fullmatch(r"Q[1-9][0-9]*", wikidata):
                issues["非空 Wikidata ID 格式異常"].append((player_id, wikidata))

    ids_by_tour[tour] = ids
    print(f"\n{tour.upper()}")
    print(f"缺少 IOC：{missing_ioc}")
    print(f"缺少 Wikidata ID：{missing_wikidata}")
    if heights:
        print(f"可解析的正整數身高範圍：{min(heights)}–{max(heights)} cm")
    for label, examples in issues.items():
        print(f"{label}：{len(examples)} 筆，範例：{examples[:5]}")

overlap = ids_by_tour["atp"] & ids_by_tour["wta"]
print(f"\nATP／WTA 交疊 ID 數量：{len(overlap)}")
print(f"交疊 ID 範例：{sorted(overlap)[:10]}")
PY
```

注意：第 1 組檢查的是原始文字 ID 的重複；第 2 組驗證可轉整數並比較兩個巡迴的交集，沒有額外驗證同一巡迴的 ID 在整數轉換後是否碰撞。正式匯入前仍需涵蓋轉換後唯一性。

## 3. 2024 年比賽樣本檢查

先查看字典和範例：

```bash
cat atp/matches_data_dictionary.txt
head -n 2 atp/atp_matches_2024.csv
head -n 2 wta/wta_matches_2024.csv
```

下列程式 `SAMPLE_YEAR = 2024` 對應當時樣本檢查。keys 在每個巡迴重新建立，所以 tour 雖未放在 tuple 裡，兩個巡迴仍分開比較。

```bash
python3 - <<'PY'
import csv
import re
from collections import Counter
from pathlib import Path

SAMPLE_YEAR = 2024  # 第 4 組全年份檢查時改為 None

for tour in ("atp", "wta"):
    folder = Path(tour)
    if SAMPLE_YEAR is None:
        files = sorted(
            path for path in folder.iterdir()
            if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", path.name)
        )
    else:
        files = [folder / f"{tour}_matches_{SAMPLE_YEAR}.csv"]
    if not files:
        raise RuntimeError(f"{tour}: 找不到符合條件的年度檔案")

    with (folder / f"{tour}_players.csv").open(
        encoding="utf-8-sig", newline=""
    ) as file:
        player_ids = {row["player_id"] for row in csv.DictReader(file)}

    keys = Counter()
    levels = Counter()
    missing_players = set()
    missing_key_count = 0
    same_player_count = 0
    total = 0
    expected_header = None
    header_mismatches = []

    for path in files:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            if expected_header is None:
                expected_header = reader.fieldnames
            elif reader.fieldnames != expected_header:
                header_mismatches.append(path.name)
            for row in reader:
                total += 1
                key = (row["tourney_id"], row["match_num"])
                keys[key] += 1
                levels[row["tourney_level"]] += 1
                if not all(key):
                    missing_key_count += 1
                for field in ("winner_id", "loser_id"):
                    if row[field] not in player_ids:
                        missing_players.add(row[field])
                if row["winner_id"] == row["loser_id"]:
                    same_player_count += 1

    duplicates = [(key, count) for key, count in keys.items() if count > 1]
    print(f"\n{tour.upper()}，範圍：{SAMPLE_YEAR or '全年份'}")
    print(f"檔案數：{len(files)}")
    print(f"首尾檔案：{files[0].name} / {files[-1].name}")
    print(f"欄位名稱或順序不同的檔案：{header_mismatches}")
    print(f"總筆數：{total}")
    print(f"賽事級別分布：{dict(levels)}")
    print(f"缺少唯一鍵欄位的筆數：{missing_key_count}")
    print(f"重複鍵組數：{len(duplicates)}，範例：{duplicates[:10]}")
    print(f"找不到的球員 ID 數：{len(missing_players)}")
    print(f"找不到的球員 ID 範例：{sorted(missing_players)[:10]}")
    print(f"勝敗方 ID 相同的筆數：{same_player_count}")
PY
```

2024 年 ATP 3076 筆、WTA 2689 筆，舊鍵重複、缺少參照、勝敗同 ID 皆為 0。

## 4. 全年份比賽檢查

重新執行第 3 節整段程式，只把指定年份改成：

```python
SAMPLE_YEAR = None
```

檔名規則只取 `atp_matches_YYYY.csv`、`wta_matches_YYYY.csv`；排除雙打、qual_chall、futures 等命名。這不等於每筆賽事都符合主巡迴範圍。

本次 ATP 199389 筆、WTA 162182 筆，各 59 檔。ATP 舊鍵無重複，WTA 16 組重複；勝敗同 ID 分別 3、1 筆；無缺少參照。

## 5. 展開重複鍵與異常紀錄

這段會把每個巡迴的資料存入記憶體以便比較，適合本次本機探索，不是規劃中的低記憶體串流匯入器。CSV 紀錄序號從標頭後第一筆開始，不等於文字實體行號。

```bash
python3 - <<'PY'
import csv
import re
from collections import defaultdict
from pathlib import Path

for tour in ("atp", "wta"):
    pattern = re.compile(rf"{tour}_matches_[0-9]{{4}}\.csv")
    groups = defaultdict(list)
    same_player = []

    for path in sorted(Path(tour).iterdir()):
        if not pattern.fullmatch(path.name):
            continue
        with path.open(encoding="utf-8-sig", newline="") as file:
            for record, row in enumerate(csv.DictReader(file), start=1):
                entry = (path.name, record, row)
                groups[(row["tourney_id"], row["match_num"])].append(entry)
                if row["winner_id"] == row["loser_id"]:
                    same_player.append(entry)

    print(f"\n=== {tour.upper()} 重複鍵 ===")
    for key, entries in groups.items():
        if len(entries) < 2:
            continue
        first = entries[0][2]
        changed = [
            field for field in first
            if any(entry[2][field] != first[field] for entry in entries[1:])
        ]
        print(f"\n鍵：{key}，筆數：{len(entries)}")
        print(f"差異欄位：{changed or '無，整筆內容相同'}")
        for filename, record, row in entries:
            print(f"  來源：{filename}，資料紀錄序號：{record}")
            print(
                f"  {row['tourney_name']} | {row['tourney_date']} | "
                f"{row['round']} | "
                f"{row['winner_id']} {row['winner_name']} → "
                f"{row['loser_id']} {row['loser_name']} | {row['score']}"
            )
            if changed:
                print(f"  差異值：{ {field: row[field] for field in changed} }")

    print(f"\n=== {tour.upper()} 勝敗方 ID 相同 ===")
    for filename, record, row in same_player:
        print(f"\n來源：{filename}，資料紀錄序號：{record}")
        for field in (
            "tourney_id", "match_num", "tourney_name", "tourney_date",
            "round", "winner_id", "winner_name",
            "loser_id", "loser_name", "score"
        ):
            print(f"  {field}: {row[field]}")
PY
```

本次 16 組衝突皆內容不同且 round 不同，不能去重。詳細異常處理規則見資料建模筆記。

## 6. 驗證加入 round 後的新候選鍵

轉換為整數後再檢查 match_num，避免文字 01、1 匯入後碰撞。每個巡迴分別驗證 `(tourney_id, round, match_num)`，對應完整鍵 `(tour, tourney_id, round, match_num)`。

```bash
python3 - <<'PY'
import csv
import re
from collections import Counter
from pathlib import Path

for tour in ("atp", "wta"):
    keys = Counter()
    rounds = Counter()
    missing = []
    invalid_numbers = []

    for path in sorted(Path(tour).iterdir()):
        if not re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", path.name):
            continue
        with path.open(encoding="utf-8-sig", newline="") as file:
            for record, row in enumerate(csv.DictReader(file), start=1):
                tournament = row["tourney_id"].strip()
                round_code = row["round"].strip()
                number = row["match_num"].strip()
                if not tournament or not round_code or not number:
                    missing.append((path.name, record))
                    continue
                if not re.fullmatch(r"[+-]?[0-9]+", number):
                    invalid_numbers.append((path.name, record, number))
                    continue
                number = int(number)
                if not -2147483648 <= number <= 2147483647:
                    invalid_numbers.append((path.name, record, number))
                    continue
                keys[(tournament, round_code, number)] += 1
                rounds[round_code] += 1

    duplicates = [(key, count) for key, count in keys.items() if count > 1]
    print(f"\n{tour.upper()}")
    print(f"候選鍵欄位缺值：{len(missing)}，範例：{missing[:5]}")
    print(f"match_num 無法轉為 PostgreSQL integer："
          f"{len(invalid_numbers)}，範例：{invalid_numbers[:5]}")
    print(f"轉換後的重複鍵組數：{len(duplicates)}")
    print(f"重複範例：{duplicates[:10]}")
    print(f"輪次分布：{dict(rounds)}")
PY
```

本次兩個巡迴全部通過。ATP 輪次有 R32、R16、QF、SF、F、R64、R128、RR、BR、ER；WTA 無 ER，其餘相同。代碼含義未核對者保留原值。

## 7. 接續：排名資料探索

```bash
printf '%s\n' atp/atp_rankings_*.csv wta/wta_rankings_*.csv
```

排名檔名、標頭、日期與數值、球員參照、跨檔案唯一鍵及衝突分類已完成探索。為了讓本附錄仍聚焦球員與比賽，新增排名教學收錄在 [排名資料探索指令](03-ranking-inspection-commands.md)。

排名有真實衝突，正式表保留原主鍵，規劃一致重複合併、衝突整組隔離。尚未實作匯入、隔離或正式資料表。

## 8. 比賽日期與數值探索：逐步指令

本節記錄本輪實際教學順序：每次只執行一小步、貼回輸出、判讀後才繼續。已完成的檢查不用為了補文件再執行。

所有資料指令的工作目錄是 `/home/shumin/tennis-sackmann-archive`；來源快照為 `83733587353df8a41f2fd4f516147d5aa83f5a8d`。只讀年度 CSV，不改來源、不安裝套件、不建表、不匯入。

以下保留每個步驟的檢查目的與可獨立執行指令；共用邏輯和輸出格式經整理，並非逐字抄錄聊天。整理版只驗證 Python 語法，沒有重新全量執行。實際結果來自使用者原先執行的逐步版本，完整表格與來源位置見 [比賽探索結果](04-match-inspection-commands.md)。

`Decimal` 避免把 1.5 截成 1；空值與零分開。程式印出的 Counter 若省略某個異常類別，代表該類別計數為零。負值、零值是數值中的子集合，不可把所有計數相加。後續取樣／關係檢查以本快照已通過的格式檢查為前提，不是正式匯入驗證器。

### 8.1 檢查比賽日期

檢查 tourney_date 是否缺值、為八位 ASCII 數字及有效日曆日期。它是賽事開賽日，不是每場實際比賽日。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

for tour in ("atp", "wta"):
    paths = annual_files(tour)
    counts = Counter()
    samples = []
    first = last = None
    for path in paths:
        with path.open(encoding="utf-8-sig", newline="") as file:
            for record, row in enumerate(csv.DictReader(file), 1):
                counts["總筆數"] += 1
                raw = row["tourney_date"]
                reason = None
                if raw is None or raw == "":
                    reason = "缺少日期"
                elif re.fullmatch(r"[0-9]{8}", raw) is None:
                    reason = "不是八位數字"
                else:
                    try:
                        date = datetime.strptime(raw, "%Y%m%d").date()
                    except ValueError:
                        reason = "不存在的日期"
                    else:
                        counts["有效日期"] += 1
                        first = date if first is None else min(first, date)
                        last = date if last is None else max(last, date)
                if reason:
                    counts[reason] += 1
                    if len(samples) < 5:
                        samples.append((str(path), record, raw, reason))
    print(tour.upper(), "檔案數", len(paths))
    for label in ("總筆數", "有效日期", "缺少日期", "不是八位數字", "不存在的日期"):
        print(label, counts[label])
    print("有效日期範圍", first, last)
    print("異常範例", samples)
PY
```

**本次已取得的結果與判讀：** 各 59 檔；ATP 199389、WTA 162182 筆全部有效。日期範圍分別為 1967-12-28～2026-05-25、1967-12-25～2026-05-25。格式合法不代表來源日期已查證。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.2 追查 1968 年檔案的前一年日期

先看日期邊界，不因檔名年份不同就修改日期。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

for tour in ("atp", "wta"):
    path = Path(tour) / f"{tour}_matches_1968.csv"
    counts = Counter()
    with path.open(encoding="utf-8-sig", newline="") as file:
        for row in csv.DictReader(file):
            if row["tourney_date"].startswith("1967"):
                counts[(row["tourney_id"], row["tourney_name"], row["tourney_date"])] += 1
    print(path)
    for key, count in sorted(counts.items()):
        print(key, count)
    print("合計", sum(counts.values()))
PY
```

**本次已取得的結果與判讀：** ATP Bombay、Calcutta 各 7 筆；WTA Port Elizabeth 16 筆、Valencia 5 筆。僅確認來源如此記載，跨年原因未查證。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.3 檢查年齡與 numeric(4,1)

numeric(4,1) 最多四位數、一位小數；本步檢查能否原樣保存，不靠資料庫四捨五入。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

fields = ['winner_age', 'loser_age']
mode = 'age'

for tour in ("atp", "wta"):
    paths = annual_files(tour)
    stats = {f: {"counts": Counter(), "min": None, "max": None,
                 "samples": defaultdict(list)} for f in fields}
    for path in paths:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            if not set(fields).issubset(reader.fieldnames or []):
                raise ValueError(f"{path}: 缺少欄位")
            for record, row in enumerate(reader, 1):
                for field, state in stats.items():
                    c = state["counts"]
                    c["總筆數"] += 1
                    raw = row[field]
                    if raw is None:
                        raise ValueError(f"{path}:{record}: 缺少欄位")
                    if raw == "":
                        c["空值"] += 1
                        continue
                    reasons = []
                    try:
                        n = Decimal(raw)
                    except InvalidOperation:
                        reasons.append("無法解析")
                    else:
                        if not n.is_finite():
                            reasons.append("非有限數值")
                        elif mode != "age" and n != n.to_integral_value():
                            reasons.append("含非零小數")
                        else:
                            c["有限數值" if mode == "age" else "整數值"] += 1
                            state["min"] = n if state["min"] is None else min(state["min"], n)
                            state["max"] = n if state["max"] is None else max(state["max"], n)
                            if n == 0:
                                c["零值"] += 1
                            if n < 0:
                                reasons.append("負值")
                            if mode == "age":
                                if abs(n) > Decimal("999.9"):
                                    reasons.append("超出 numeric(4,1) 原樣保存範圍")
                                if n * 10 != (n * 10).to_integral_value():
                                    reasons.append("需超過一位小數")
                                if n <= 0:
                                    reasons.append("非正年齡")
                            else:
                                lo, hi = (-2147483648, 2147483647) if mode == "rank" else (-32768, 32767)
                                if not lo <= n <= hi:
                                    reasons.append("超出型別範圍")
                                if mode == "rank" and field.endswith("_rank") and n <= 0:
                                    reasons.append("排名不是正整數")
                                if mode == "height" and not 100 <= n <= 250:
                                    reasons.append("身高待查")
                    for reason in reasons:
                        c[reason] += 1
                        if len(state["samples"][reason]) < 3:
                            state["samples"][reason].append((str(path), record, raw))
    print(tour.upper(), "檔案數", len(paths))
    for field, state in stats.items():
        print(field, dict(state["counts"]), "範圍", state["min"], state["max"])
        for reason, samples in state["samples"].items():
            print(reason, samples)
PY
```

**本次已取得的結果與判讀：** 沒有超出範圍或需超過一位小數的值。ATP 無非正年齡；WTA 勝方 9、敗方 17 個。缺值與範圍詳見結果附錄第 2 節。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.4 對照非正年齡與球員生日

只追查上一輪異常，確認生日與參賽時間的矛盾；姓名一致不能證明身分對應正確。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

tour = 'wta'
suffix = 'age'
masterfield = 'dob'

with (Path(tour) / f"{tour}_players.csv").open(encoding="utf-8-sig", newline="") as file:
    players = {int(row["player_id"]): row for row in csv.DictReader(file)}
value_count = match_count = 0
ids = set()
for path in annual_files(tour):
    with path.open(encoding="utf-8-sig", newline="") as file:
        for record, row in enumerate(csv.DictReader(file), 1):
            affected = False
            for side in ("winner", "loser"):
                raw = row[f"{side}_{suffix}"]
                if raw == "":
                    continue
                n = Decimal(raw)
                if not (n <= 0):
                    continue
                affected = True
                value_count += 1
                player_id = int(row[f"{side}_id"])
                ids.add(player_id)
                player = players.get(player_id)
                print(str(path), record, row["tourney_id"], row["round"], row["match_num"])
                print("日期", row["tourney_date"], "角色", side, "球員", player_id,
                      row[f"{side}_name"], "原值", repr(raw))
                print("主檔", None if player is None else
                      (player["name_first"], player["name_last"], player[masterfield]))
            match_count += affected
print("異常欄位值", value_count, "比賽數", match_count, "球員數", len(ids))
PY
```

**本次已取得的結果與判讀：** 26 個值、26 場、5 位球員。主檔生日與參賽時間矛盾；不猜生日、不取絕對值，非正年齡排除年齡分析的規則尚未實作。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.5 檢查五個基本數值欄位

檢查 draw_size、雙方種子、best_of、minutes；smallint 範圍為 -32768～32767。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

fields = ['draw_size', 'winner_seed', 'loser_seed', 'best_of', 'minutes']
mode = 'small'

for tour in ("atp", "wta"):
    paths = annual_files(tour)
    stats = {f: {"counts": Counter(), "min": None, "max": None,
                 "samples": defaultdict(list)} for f in fields}
    for path in paths:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            if not set(fields).issubset(reader.fieldnames or []):
                raise ValueError(f"{path}: 缺少欄位")
            for record, row in enumerate(reader, 1):
                for field, state in stats.items():
                    c = state["counts"]
                    c["總筆數"] += 1
                    raw = row[field]
                    if raw is None:
                        raise ValueError(f"{path}:{record}: 缺少欄位")
                    if raw == "":
                        c["空值"] += 1
                        continue
                    reasons = []
                    try:
                        n = Decimal(raw)
                    except InvalidOperation:
                        reasons.append("無法解析")
                    else:
                        if not n.is_finite():
                            reasons.append("非有限數值")
                        elif mode != "age" and n != n.to_integral_value():
                            reasons.append("含非零小數")
                        else:
                            c["有限數值" if mode == "age" else "整數值"] += 1
                            state["min"] = n if state["min"] is None else min(state["min"], n)
                            state["max"] = n if state["max"] is None else max(state["max"], n)
                            if n == 0:
                                c["零值"] += 1
                            if n < 0:
                                reasons.append("負值")
                            if mode == "age":
                                if abs(n) > Decimal("999.9"):
                                    reasons.append("超出 numeric(4,1) 原樣保存範圍")
                                if n * 10 != (n * 10).to_integral_value():
                                    reasons.append("需超過一位小數")
                                if n <= 0:
                                    reasons.append("非正年齡")
                            else:
                                lo, hi = (-2147483648, 2147483647) if mode == "rank" else (-32768, 32767)
                                if not lo <= n <= hi:
                                    reasons.append("超出型別範圍")
                                if mode == "rank" and field.endswith("_rank") and n <= 0:
                                    reasons.append("排名不是正整數")
                                if mode == "height" and not 100 <= n <= 250:
                                    reasons.append("身高待查")
                    for reason in reasons:
                        c[reason] += 1
                        if len(state["samples"][reason]) < 3:
                            state["samples"][reason].append((str(path), record, raw))
    print(tour.upper(), "檔案數", len(paths))
    for field, state in stats.items():
        print(field, dict(state["counts"]), "範圍", state["min"], state["max"])
        for reason, samples in state["samples"].items():
            print(reason, samples)
PY
```

**本次已取得的結果與判讀：** 整數皆在 smallint 範圍。WTA draw_size 有 exho 一筆，種子文字勝方 225、敗方 147。零分鐘 ATP 59、WTA 130；最大分鐘 1146、2475。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.6 列出所有非數字種子原值

按角色與原值統計，每種保留首筆來源；不能只看最早三筆範例。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

counts = Counter()
examples = {}
for path in annual_files("wta"):
    with path.open(encoding="utf-8-sig", newline="") as file:
        for record, row in enumerate(csv.DictReader(file), 1):
            for side in ("winner", "loser"):
                raw = row[f"{side}_seed"]
                if raw == "":
                    continue
                try:
                    Decimal(raw)
                except InvalidOperation:
                    key = (side, raw)
                    counts[key] += 1
                    examples.setdefault(key, (str(path), record, row["tourney_id"],
                                              row["tourney_name"], row["tourney_level"], row["round"]))
for key, count in sorted(counts.items()):
    print(key, count, "首筆", examples[key])
print("合計", sum(counts.values()))
PY
```

**本次已取得的結果與判讀：** 勝方 10 種共 225 個，敗方 12 種共 147 個。含 1F～8F、ALT、LL、Q、WC；不刪字母。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.7 對照文字種子與 entry

新增同一角色的 entry 作為分組欄位，確認兩欄實際搭配。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

counts = Counter()
examples = {}
for path in annual_files("wta"):
    with path.open(encoding="utf-8-sig", newline="") as file:
        for record, row in enumerate(csv.DictReader(file), 1):
            for side in ("winner", "loser"):
                raw = row[f"{side}_seed"]
                if raw == "":
                    continue
                try:
                    Decimal(raw)
                except InvalidOperation:
                    key = (side, raw, row[f"{side}_entry"])
                    counts[key] += 1
                    examples.setdefault(key, (str(path), record, row["tourney_id"],
                                              row["tourney_name"], row["tourney_level"], row["round"]))
for key, count in sorted(counts.items()):
    print(key, count, "首筆", examples[key])
print("合計", sum(counts.values()))
PY
```

**本次已取得的結果與判讀：** 372 個文字種子對應 entry 全空白；尚不能證明錯置，不自動搬移。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.8 查看 draw_size=exho 的完整資料

只讀一份檔案，確認賽事、級別、比分與其他原值。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

path = Path("wta/wta_matches_1970.csv")
found = 0
with path.open(encoding="utf-8-sig", newline="") as file:
    for record, row in enumerate(csv.DictReader(file), 1):
        if row["draw_size"] == "exho":
            found += 1
            print(path, record)
            for field, raw in row.items():
                print(f"{field}: {raw!r}")
print("符合筆數", found)
PY
```

**本次已取得的結果與判讀：** 紀錄 1818，Rondebosch Exho，級別 E，鍵 1970-1093 / RR / 1。數值欄規劃 NULL 並保存原值與原因；賽事是否納入待定。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.9 列出 best_of 分布

最小最大值不能告訴我們中間有哪些值；分別計數，對 3、5 以外值取樣。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

for tour in ("atp", "wta"):
    counts = Counter()
    examples = defaultdict(list)
    for path in annual_files(tour):
        with path.open(encoding="utf-8-sig", newline="") as file:
            for record, row in enumerate(csv.DictReader(file), 1):
                n = Decimal(row["best_of"])
                if not n.is_finite() or n != n.to_integral_value():
                    raise ValueError(f"{path}:{record}: best_of 非整數")
                value = int(n)
                counts[value] += 1
                if value not in (3, 5) and len(examples[value]) < 3:
                    examples[value].append((str(path), record, row["tourney_id"],
                        row["tourney_name"], row["tourney_level"], row["round"], row["score"]))
    print(tour.upper(), sorted(counts.items()), "合計", sum(counts.values()))
    print(dict(examples))
PY
```

**本次已取得的結果與判讀：** ATP 1/3/5 為 36/156016/43337；WTA 為 102/161956/124。出現 6-36-3、7-Oct 等比分原文，未查證原因，不猜還原。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.10 查看分鐘數最大的五筆

用 heapq 只保留五筆，逐筆讀取，不將全檔存入記憶體。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

import heapq
for tour in ("atp", "wta"):
    longest = []
    for path in annual_files(tour):
        with path.open(encoding="utf-8-sig", newline="") as file:
            for record, row in enumerate(csv.DictReader(file), 1):
                if row["minutes"] == "":
                    continue
                n = Decimal(row["minutes"])
                if not n.is_finite() or n != n.to_integral_value():
                    raise ValueError(f"{path}:{record}: minutes 非整數")
                item = (int(n), str(path), record, row["tourney_id"],
                        row["tourney_name"], row["tourney_date"], row["round"],
                        row["match_num"], row["winner_name"], row["loser_name"],
                        row["score"], row["best_of"])
                heapq.heappush(longest, item)
                if len(longest) > 5:
                    heapq.heappop(longest)
    print(tour.upper(), "分鐘、檔案、序號、賽事ID、名稱、日期、輪次、場次、勝方、敗方、比分、best_of")
    for item in sorted(longest, reverse=True):
        print(item)
PY
```

**本次已取得的結果與判讀：** ATP 1146、987、665、396、393；WTA 2475、1507、1094、391、361。前述極端值待查，不設武斷刪除門檻。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.11 檢查零分鐘的比分分布

依完整比分原值分組，不先猜零是缺值或未開賽。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

for tour in ("atp", "wta"):
    counts = Counter()
    examples = {}
    for path in annual_files(tour):
        with path.open(encoding="utf-8-sig", newline="") as file:
            for record, row in enumerate(csv.DictReader(file), 1):
                raw = row["minutes"]
                if raw == "" or Decimal(raw) != 0:
                    continue
                score = row["score"]
                counts[score] += 1
                examples.setdefault(score, (str(path), record, row["tourney_id"],
                                           row["round"], row["match_num"]))
    print(tour.upper(), "總筆數", sum(counts.values()), "比分種類", len(counts))
    for score, count in counts.most_common():
        print(repr(score), count, examples[score])
PY
```

**本次已取得的結果與判讀：** ATP 59 筆全為 W/O；WTA 127 筆 W/O，另外三筆有一般比分。排除於時間分析的方向尚未實作，不反推所有 W/O 都是零分鐘。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.12 檢查 18 個發球統計欄位

統計值為計數；零可以有效，空值不可補零。各欄缺值數可能不同。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

fields = ['w_ace', 'w_df', 'w_svpt', 'w_1stIn', 'w_1stWon', 'w_2ndWon', 'w_SvGms', 'w_bpSaved', 'w_bpFaced', 'l_ace', 'l_df', 'l_svpt', 'l_1stIn', 'l_1stWon', 'l_2ndWon', 'l_SvGms', 'l_bpSaved', 'l_bpFaced']
mode = 'small'

for tour in ("atp", "wta"):
    paths = annual_files(tour)
    stats = {f: {"counts": Counter(), "min": None, "max": None,
                 "samples": defaultdict(list)} for f in fields}
    for path in paths:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            if not set(fields).issubset(reader.fieldnames or []):
                raise ValueError(f"{path}: 缺少欄位")
            for record, row in enumerate(reader, 1):
                for field, state in stats.items():
                    c = state["counts"]
                    c["總筆數"] += 1
                    raw = row[field]
                    if raw is None:
                        raise ValueError(f"{path}:{record}: 缺少欄位")
                    if raw == "":
                        c["空值"] += 1
                        continue
                    reasons = []
                    try:
                        n = Decimal(raw)
                    except InvalidOperation:
                        reasons.append("無法解析")
                    else:
                        if not n.is_finite():
                            reasons.append("非有限數值")
                        elif mode != "age" and n != n.to_integral_value():
                            reasons.append("含非零小數")
                        else:
                            c["有限數值" if mode == "age" else "整數值"] += 1
                            state["min"] = n if state["min"] is None else min(state["min"], n)
                            state["max"] = n if state["max"] is None else max(state["max"], n)
                            if n == 0:
                                c["零值"] += 1
                            if n < 0:
                                reasons.append("負值")
                            if mode == "age":
                                if abs(n) > Decimal("999.9"):
                                    reasons.append("超出 numeric(4,1) 原樣保存範圍")
                                if n * 10 != (n * 10).to_integral_value():
                                    reasons.append("需超過一位小數")
                                if n <= 0:
                                    reasons.append("非正年齡")
                            else:
                                lo, hi = (-2147483648, 2147483647) if mode == "rank" else (-32768, 32767)
                                if not lo <= n <= hi:
                                    reasons.append("超出型別範圍")
                                if mode == "rank" and field.endswith("_rank") and n <= 0:
                                    reasons.append("排名不是正整數")
                                if mode == "height" and not 100 <= n <= 250:
                                    reasons.append("身高待查")
                    for reason in reasons:
                        c[reason] += 1
                        if len(state["samples"][reason]) < 3:
                            state["samples"][reason].append((str(path), record, raw))
    print(tour.upper(), "檔案數", len(paths))
    for field, state in stats.items():
        print(field, dict(state["counts"]), "範圍", state["min"], state["max"])
        for reason, samples in state["samples"].items():
            print(reason, samples)
PY
```

**本次已取得的結果與判讀：** 全部非空值為 smallint 範圍內整數；ATP l_bpSaved 兩筆負值 -4、-6。完整 18 欄分布見結果附錄第 4 節。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.13 追查兩筆負破發點救回數

查看同筆比分、發球局數及雙方破發點數，不用取絕對值修復。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

path = Path("atp/atp_matches_1991.csv")
found = 0
with path.open(encoding="utf-8-sig", newline="") as file:
    for record, row in enumerate(csv.DictReader(file), 1):
        raw = row["l_bpSaved"]
        if raw == "" or Decimal(raw) >= 0:
            continue
        found += 1
        print(path, record)
        for field in ("tourney_id", "round", "match_num", "tourney_name", "tourney_date",
                      "winner_name", "loser_name", "score", "w_SvGms", "w_bpSaved",
                      "w_bpFaced", "l_SvGms", "l_bpSaved", "l_bpFaced"):
            print(field, repr(row[field]))
print("符合筆數", found)
PY
```

**本次已取得的結果與判讀：** 紀錄 1084、1085，Tokyo Outdoor。敗方 saved/faced 為 -4/0、-6/2；不能因相減得到 4、8 就認定可修復。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.14 驗證救回數不大於面對數

每個角色各自檢查 bpSaved ≤ bpFaced，缺值及負值分開跳過。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

rules = [("破發點", ("bpSaved", "bpFaced"))]

for tour in ("atp", "wta"):
    counts = defaultdict(Counter)
    samples = defaultdict(list)
    for path in annual_files(tour):
        with path.open(encoding="utf-8-sig", newline="") as file:
            for record, row in enumerate(csv.DictReader(file), 1):
                for side in ("w", "l"):
                    for label, suffixes in rules:
                        key = (side, label)
                        raw = [row[f"{side}_{s}"] for s in suffixes]
                        if "" in raw:
                            counts[key]["缺值跳過"] += 1
                            continue
                        nums = [Decimal(v) for v in raw]
                        if any(n < 0 for n in nums):
                            counts[key]["負值跳過"] += 1
                            continue
                        if len(nums) == 3 and nums[2] > nums[1]:
                            counts[key]["前提不成立"] += 1
                            continue
                        limit = nums[1] if len(nums) == 2 else nums[1] - nums[2]
                        if nums[0] <= limit:
                            counts[key]["通過"] += 1
                        else:
                            counts[key]["違反"] += 1
                            if len(samples[key]) < 3:
                                samples[key].append((str(path), record, row["tourney_id"],
                                    row["round"], row["match_num"], dict(zip(suffixes, raw))))
    print(tour.upper())
    for side in ("w", "l"):
        for label, _ in rules:
            key = (side, label)
            print(key)
            for category in ("通過", "違反", "缺值跳過", "負值跳過", "前提不成立"):
                print(category, counts[key][category])
            print("異常範例", samples[key])
PY
```

**本次已取得的結果與判讀：** 沒有新違反；ATP 勝方通過 101926、敗方通過 101924 並跳過兩筆負值；WTA 通過 47823、47826。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.15 驗證一發與二發的欄位關係

逐項檢查 1stIn ≤ svpt、1stWon ≤ 1stIn、2ndWon ≤ svpt-1stIn；每項按所需欄位判斷缺值。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

rules = [("一發進球", ("1stIn", "svpt")), ("一發得分", ("1stWon", "1stIn")),
         ("二發得分", ("2ndWon", "svpt", "1stIn"))]

for tour in ("atp", "wta"):
    counts = defaultdict(Counter)
    samples = defaultdict(list)
    for path in annual_files(tour):
        with path.open(encoding="utf-8-sig", newline="") as file:
            for record, row in enumerate(csv.DictReader(file), 1):
                for side in ("w", "l"):
                    for label, suffixes in rules:
                        key = (side, label)
                        raw = [row[f"{side}_{s}"] for s in suffixes]
                        if "" in raw:
                            counts[key]["缺值跳過"] += 1
                            continue
                        nums = [Decimal(v) for v in raw]
                        if any(n < 0 for n in nums):
                            counts[key]["負值跳過"] += 1
                            continue
                        if len(nums) == 3 and nums[2] > nums[1]:
                            counts[key]["前提不成立"] += 1
                            continue
                        limit = nums[1] if len(nums) == 2 else nums[1] - nums[2]
                        if nums[0] <= limit:
                            counts[key]["通過"] += 1
                        else:
                            counts[key]["違反"] += 1
                            if len(samples[key]) < 3:
                                samples[key].append((str(path), record, row["tourney_id"],
                                    row["round"], row["match_num"], dict(zip(suffixes, raw))))
    print(tour.upper())
    for side in ("w", "l"):
        for label, _ in rules:
            key = (side, label)
            print(key)
            for category in ("通過", "違反", "缺值跳過", "負值跳過", "前提不成立"):
                print(category, counts[key][category])
            print("異常範例", samples[key])
PY
```

**本次已取得的結果與判讀：** 5 項違反集中於兩場：ATP 2024 紀錄 1359 一項；WTA 2023 紀錄 2754 四項。不能把違反項數直接當比賽數。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.16 以比賽鍵追查兩場完整發球統計

以 tourney_id、round、match_num 定位，檢視比分與雙方 18 個統計值。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

targets = [("atp/atp_matches_2024.csv", ("2024-520", "R64", "345")),
           ("wta/wta_matches_2023.csv", ("2023-W-FC-2023-FLS", "RR", "104"))]
suffixes = ("ace", "df", "svpt", "1stIn", "1stWon", "2ndWon", "SvGms", "bpSaved", "bpFaced")
for filename, target in targets:
    found = 0
    with Path(filename).open(encoding="utf-8-sig", newline="") as file:
        for record, row in enumerate(csv.DictReader(file), 1):
            if (row["tourney_id"], row["round"], row["match_num"]) != target:
                continue
            found += 1
            print(filename, record, target)
            for field in ("tourney_name", "tourney_date", "winner_name", "loser_name", "score", "best_of", "minutes"):
                print(field, repr(row[field]))
            for suffix in suffixes:
                print(suffix, "勝方", row[f"w_{suffix}"], "敗方", row[f"l_{suffix}"])
    print("符合筆數", found)
    if found != 1:
        raise ValueError(f"{filename}: 預期一筆，實際 {found}")
PY
```

**本次已取得的結果與判讀：** 各找到一筆。ATP 勝方二發機會 13、得分 21；WTA 雙方一發與二發關係矛盾。不猜哪欄錯誤；相關指標排除方向尚未實作。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.17 檢查比賽排名與積分

這是比賽檔的四欄，不是已檢查過的週排名；排名須正整數，積分允許零。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

fields = ['winner_rank', 'winner_rank_points', 'loser_rank', 'loser_rank_points']
mode = 'rank'

for tour in ("atp", "wta"):
    paths = annual_files(tour)
    stats = {f: {"counts": Counter(), "min": None, "max": None,
                 "samples": defaultdict(list)} for f in fields}
    for path in paths:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            if not set(fields).issubset(reader.fieldnames or []):
                raise ValueError(f"{path}: 缺少欄位")
            for record, row in enumerate(reader, 1):
                for field, state in stats.items():
                    c = state["counts"]
                    c["總筆數"] += 1
                    raw = row[field]
                    if raw is None:
                        raise ValueError(f"{path}:{record}: 缺少欄位")
                    if raw == "":
                        c["空值"] += 1
                        continue
                    reasons = []
                    try:
                        n = Decimal(raw)
                    except InvalidOperation:
                        reasons.append("無法解析")
                    else:
                        if not n.is_finite():
                            reasons.append("非有限數值")
                        elif mode != "age" and n != n.to_integral_value():
                            reasons.append("含非零小數")
                        else:
                            c["有限數值" if mode == "age" else "整數值"] += 1
                            state["min"] = n if state["min"] is None else min(state["min"], n)
                            state["max"] = n if state["max"] is None else max(state["max"], n)
                            if n == 0:
                                c["零值"] += 1
                            if n < 0:
                                reasons.append("負值")
                            if mode == "age":
                                if abs(n) > Decimal("999.9"):
                                    reasons.append("超出 numeric(4,1) 原樣保存範圍")
                                if n * 10 != (n * 10).to_integral_value():
                                    reasons.append("需超過一位小數")
                                if n <= 0:
                                    reasons.append("非正年齡")
                            else:
                                lo, hi = (-2147483648, 2147483647) if mode == "rank" else (-32768, 32767)
                                if not lo <= n <= hi:
                                    reasons.append("超出型別範圍")
                                if mode == "rank" and field.endswith("_rank") and n <= 0:
                                    reasons.append("排名不是正整數")
                                if mode == "height" and not 100 <= n <= 250:
                                    reasons.append("身高待查")
                    for reason in reasons:
                        c[reason] += 1
                        if len(state["samples"][reason]) < 3:
                            state["samples"][reason].append((str(path), record, raw))
    print(tour.upper(), "檔案數", len(paths))
    for field, state in stats.items():
        print(field, dict(state["counts"]), "範圍", state["min"], state["max"])
        for reason, samples in state["samples"].items():
            print(reason, samples)
PY
```

**本次已取得的結果與判讀：** 四欄全通過 integer 與基本值域檢查，ATP 最高積分 16950、WTA 13615。空值仍為 NULL，未對照週排名。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.18 檢查比賽檔身高

比賽檔的 winner_ht、loser_ht 與球員主檔分開驗證；100～250 僅為查核門檻。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

fields = ['winner_ht', 'loser_ht']
mode = 'height'

for tour in ("atp", "wta"):
    paths = annual_files(tour)
    stats = {f: {"counts": Counter(), "min": None, "max": None,
                 "samples": defaultdict(list)} for f in fields}
    for path in paths:
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            if not set(fields).issubset(reader.fieldnames or []):
                raise ValueError(f"{path}: 缺少欄位")
            for record, row in enumerate(reader, 1):
                for field, state in stats.items():
                    c = state["counts"]
                    c["總筆數"] += 1
                    raw = row[field]
                    if raw is None:
                        raise ValueError(f"{path}:{record}: 缺少欄位")
                    if raw == "":
                        c["空值"] += 1
                        continue
                    reasons = []
                    try:
                        n = Decimal(raw)
                    except InvalidOperation:
                        reasons.append("無法解析")
                    else:
                        if not n.is_finite():
                            reasons.append("非有限數值")
                        elif mode != "age" and n != n.to_integral_value():
                            reasons.append("含非零小數")
                        else:
                            c["有限數值" if mode == "age" else "整數值"] += 1
                            state["min"] = n if state["min"] is None else min(state["min"], n)
                            state["max"] = n if state["max"] is None else max(state["max"], n)
                            if n == 0:
                                c["零值"] += 1
                            if n < 0:
                                reasons.append("負值")
                            if mode == "age":
                                if abs(n) > Decimal("999.9"):
                                    reasons.append("超出 numeric(4,1) 原樣保存範圍")
                                if n * 10 != (n * 10).to_integral_value():
                                    reasons.append("需超過一位小數")
                                if n <= 0:
                                    reasons.append("非正年齡")
                            else:
                                lo, hi = (-2147483648, 2147483647) if mode == "rank" else (-32768, 32767)
                                if not lo <= n <= hi:
                                    reasons.append("超出型別範圍")
                                if mode == "rank" and field.endswith("_rank") and n <= 0:
                                    reasons.append("排名不是正整數")
                                if mode == "height" and not 100 <= n <= 250:
                                    reasons.append("身高待查")
                    for reason in reasons:
                        c[reason] += 1
                        if len(state["samples"][reason]) < 3:
                            state["samples"][reason].append((str(path), record, raw))
    print(tour.upper(), "檔案數", len(paths))
    for field, state in stats.items():
        print(field, dict(state["counts"]), "範圍", state["min"], state["max"])
        for reason, samples in state["samples"].items():
            print(reason, samples)
PY
```

**本次已取得的結果與判讀：** 非空值均可用 smallint。ATP 勝方、敗方各 6 個身高待查；WTA 無觸發門檻。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.19 對照異常身高與球員主檔

確認異常是否也存在主檔，但不以來源一致當作正確證明。

工作目錄：`/home/shumin/tennis-sackmann-archive`。整段執行：

```bash
cd /home/shumin/tennis-sackmann-archive
python3 - <<'PY'
import csv
import re
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path

def annual_files(tour):
    paths = sorted(
        p for p in Path(tour).glob(f"{tour}_matches_*.csv")
        if re.fullmatch(rf"{tour}_matches_[0-9]{{4}}\.csv", p.name)
    )
    if not paths:
        raise RuntimeError(f"{tour}: 找不到年度檔案，請確認工作目錄")
    return paths

tour = 'atp'
suffix = 'ht'
masterfield = 'height'

with (Path(tour) / f"{tour}_players.csv").open(encoding="utf-8-sig", newline="") as file:
    players = {int(row["player_id"]): row for row in csv.DictReader(file)}
value_count = match_count = 0
ids = set()
for path in annual_files(tour):
    with path.open(encoding="utf-8-sig", newline="") as file:
        for record, row in enumerate(csv.DictReader(file), 1):
            affected = False
            for side in ("winner", "loser"):
                raw = row[f"{side}_{suffix}"]
                if raw == "":
                    continue
                n = Decimal(raw)
                if not (n < 100 or n > 250):
                    continue
                affected = True
                value_count += 1
                player_id = int(row[f"{side}_id"])
                ids.add(player_id)
                player = players.get(player_id)
                print(str(path), record, row["tourney_id"], row["round"], row["match_num"])
                print("日期", row["tourney_date"], "角色", side, "球員", player_id,
                      row[f"{side}_name"], "原值", repr(raw))
                print("主檔", None if player is None else
                      (player["name_first"], player["name_last"], player[masterfield]))
            match_count += affected
print("異常欄位值", value_count, "比賽數", match_count, "球員數", len(ids))
PY
```

**本次已取得的結果與判讀：** 12 個值、12 場、3 位球員：106410 身高 3（5 場）、134087 身高 15（3 場）、209907 身高 71（4 場）。皆與主檔一致；排除身高分析方向尚未實作。

教學驗收：檢查輸出是否與上述快照結果相符；若不符先確認檔案與原值，再進下一步。

### 8.20 更新文件並檢查差異

工作目錄：`/home/shumin/tennis-analytics`。本輪先教使用者新增身高筆記，之後依使用者要求改為完整整理日期與數值探索。不能只記最後一項；需保留已驗證結果、處理方向與未完成事項。

```bash
cd /home/shumin/tennis-analytics
git diff --check
git diff -- docs/01-data-model.md docs/02-data-inspection-commands.md docs/完整教學步驟與指令.md docs/README.md
git status --short
```

`git diff --check` 正常時沒有輸出。新增且未追蹤的 `docs/04-match-inspection-commands.md` 不會出現在一般 git diff，需直接開啟檢視。這些指令不暫存或提交。

階段 1 尚未完成；接下來確認賽事納入範圍、未知球員、品質儲存與最終欄位對照／DDL。若既有探索已留有級別分布，先讀既有結果，只有缺少結果或需要新分類才再掃描。排名預期筆數仍非實際匯入成果。

## 賽事範圍、球員身分與排名涵蓋補充檢查（2026-10-24）

本輪由使用者授權 agent 執行並提交。前後端及資料庫工作仍恢復逐步教學。
完整結果、品質處理方向與待辦見 [檢查交接](06-data-inspection-handoff.md)。
原始 CSV 未修改，未建立資料表或匯入資料。

### 重現指令

工作目錄：`/home/shumin/tennis-analytics`。以下是重現方式，已驗證的資料不需再次執行。

```bash
cd /home/shumin/tennis-analytics
python3 scripts/data/inspect_scope_identity.py \
  --data-root /home/shumin/tennis-sackmann-archive \
  --scope docs/05-tournament-scope.md > /tmp/tennis-inspection.json
```

腳本僅使用 Python 標準函式庫；檢查快照、來源追蹤檔修改狀態與檔案雜湊，
再檢查賽事例外、名稱候選、生日對照、排名日期涵蓋與 WTA tours。
不修改 CSV，也不連線資料庫。遇到快照不符或規則清單不符會停止，
應先審查新版本差異，不要為了通過而直接改預期數字。

本輪先完成全量補充檢查，再新增 2023 A 級別 Finals 例外；
規則變動後只重驗受影響的比賽與身分報告，不重掃數百萬排名列：

```bash
python3 scripts/data/inspect_scope_identity.py \
  --data-root /home/shumin/tennis-sackmann-archive \
  --scope docs/05-tournament-scope.md \
  --skip-rankings > /tmp/tennis-scope-identity.json
```

最終保存 [賽事身分報告](reports/scope-identity.json) 與先前完成的
[排名涵蓋報告](reports/ranking-coverage.json)。完整執行時後者內容位於各巡迴的
`ranking_coverage`、`tours_values`、`tours_invalid_examples` 欄位，
輸入雜湊位於 `input_sha256`；兩份報告的分開保存不代表排名重新掃描。

### 結果與排錯

- 104 個賽事例外鍵全部通過；2023 ATP Finals 存在於 A，而非 F。
- ATP 分類 INCLUDE/EXCLUDE/REVIEW 為 199290/92/7；WTA 為 89882/587/71713。
- 主檔生日晚於賽事日期：ATP 141 場、WTA 24 場，不自行修改來源身分。
- ATP Unknown 占位 ID 涉及 90 場、93 次角色出現；角色數不等於場數。
- WTA 1968 檔中的 Valencia 是 5 場，與 Port Elizabeth 16 場合計 21，已更正附錄表格。
- 排名不同日期數 ATP 2333、WTA 2179；WTA tours 缺值 1074709，零值 111434。
- 全部數字是探索結果；尚未套用所有品質規則，不可寫成已匯入成果。

### 提交檢查方式

```bash
git diff --check
git diff --stat
git status --short
```

未追蹤的新檔不會出現在一般 `git diff`，需讀取檔案並納入暫存後，
用 `git diff --cached` 審查將提交的內容；它與 `git diff` 的比較範圍不同。
本輪由 agent 依授權完成暫存與 commit；沒有推送或部署。
下一步為欄位保存與品質標記設計，開始資料庫工作時回到每次一個可驗證小步驟。

## 手冊與附錄的分工修訂

主手冊第 14 節保留各項檢查的目的、實際結果與判讀，19 段完整 Python 程式
集中保留於本附錄 8.1～8.19；主手冊逐項連結，不重複維護程式碼。
主手冊第 15 節補齊賽事規則形成、身分疑點、排名涵蓋、報告驗收及未完成事項。
本次只整理文件，未重新執行資料掃描或變更檢查結果。
