# 資料探索指令附錄

搭配 [完整教學手冊](完整教學步驟與指令.md) 與 [資料建模結論](01-data-model.md) 閱讀。

本附錄整理已教過的六組 Python 檢查。第 3、4 組以同一段程式的 `SAMPLE_YEAR` 切換，減少重複抄寫；其餘保留相同檢查目的，輸出用語略作統一。這些是只讀探索，不會修改 CSV 或資料庫，不需額外套件。

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

## 7. 下一步：排名檔案清單

此步驟已在教學提出，但尚未收到執行輸出：

```bash
printf '%s\n' atp/atp_rankings_*.csv wta/wta_rankings_*.csv
```

後續需檢查排名日期、數值型別、唯一鍵與球員參照；本附錄不將尚未進行的驗證列為完成。
