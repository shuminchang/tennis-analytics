# 排名資料探索：步驟、指令與結果

整理日期：2026-10-24。接續 [資料探索指令附錄](02-data-inspection-commands.md)，結論同步記在 [資料建模筆記](01-data-model.md)。

本文件整理已教過且使用者已執行的排名檢查。CSV 不會被修改；尚未建立正式排名表、實作隔離機制或匯入資料。已驗證的結果不需要為了更新文件重新跑一次。

資料根目錄：`/home/shumin/tennis-sackmann-archive`。
快照：`83733587353df8a41f2fd4f516147d5aa83f5a8d`。

## 1. 列出檔案

```bash
cd /home/shumin/tennis-sackmann-archive
printf '%s\n' atp/atp_rankings_*.csv wta/wta_rankings_*.csv
```

ATP 有 00s、10s、20s、70s、80s、90s、current 共 7 份；WTA 沒有 70s，共 6 份。檔名通常按年代分類，但不能據檔名假設沒有跨檔案重複。

## 2. 查看標頭與第一筆資料

```bash
python3 - <<'PY'
import csv
from pathlib import Path

for tour in ("atp", "wta"):
    for path in sorted(Path(tour).glob(f"{tour}_rankings_*.csv")):
        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.reader(file)
            header = next(reader, None)
            first_row = next(reader, None)
        print(f"\n{path}")
        print(f"標頭：{header}")
        print(f"第一筆：{first_row}")
PY
```

一次只讀標頭與一筆資料，不載入完整檔案。

| CSV 欄位 | 資料庫／處理方式 |
| --- | --- |
| ranking_date | YYYYMMDD 轉 date |
| rank | 正整數排名，不作為唯一鍵，可能有並列 |
| player | 對應 player_id，與 tour 一起參照球員 |
| points | 積分，空值轉 NULL；先檢查是否適合 integer |
| tours | 只有 WTA 有，含義與有效值尚待確認，保留來源 |

第一筆資料中的日期不代表完整檔案日期範圍。早期積分空白不能補成 0。

## 3. 全檔格式、日期範圍與球員參照

這段使用 Python 標準函式庫，排名逐筆讀取；記憶體主要用於球員 ID 集合及少量異常範例。Decimal 用來識別小數，不是直接截掉小數。

```bash
python3 - <<'PY'
import csv
import re
from collections import Counter
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path


def positive_integer(value):
    return (
        re.fullmatch(r"[0-9]+", value) is not None
        and 1 <= int(value) <= 2147483647
    )


for tour in ("atp", "wta"):
    with (Path(tour) / f"{tour}_players.csv").open(
        encoding="utf-8-sig", newline=""
    ) as file:
        player_ids = {int(row["player_id"]) for row in csv.DictReader(file)}

    expected = ["ranking_date", "rank", "player", "points"]
    if tour == "wta":
        expected.append("tours")

    for path in sorted(Path(tour).glob(f"{tour}_rankings_*.csv")):
        counts = Counter()
        examples = {}
        first_date = None
        last_date = None

        def issue(label, record, value):
            counts[label] += 1
            samples = examples.setdefault(label, [])
            if len(samples) < 3:
                samples.append((record, value))

        with path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            if reader.fieldnames != expected:
                raise ValueError(f"{path}: 標頭不符：{reader.fieldnames}")
            for record, row in enumerate(reader, start=1):
                counts["總筆數"] += 1
                if None in row or any(value is None for value in row.values()):
                    issue("欄位數不符", record, row)
                    continue

                raw_date = row["ranking_date"]
                try:
                    if not re.fullmatch(r"[0-9]{8}", raw_date):
                        raise ValueError("Expected YYYYMMDD")
                    date = datetime.strptime(raw_date, "%Y%m%d").date()
                except ValueError:
                    issue("無效日期", record, raw_date)
                else:
                    first_date = date if first_date is None else min(first_date, date)
                    last_date = date if last_date is None else max(last_date, date)

                if not positive_integer(row["rank"]):
                    issue("排名不是有效正整數", record, row["rank"])

                player = row["player"]
                if not positive_integer(player):
                    issue("球員 ID 不是有效正整數", record, player)
                elif int(player) not in player_ids:
                    issue("找不到球員參照", record, player)

                points = row["points"]
                if points == "":
                    counts["缺少積分"] += 1
                else:
                    try:
                        number = Decimal(points)
                    except InvalidOperation:
                        issue("積分無法解析", record, points)
                    else:
                        if not number.is_finite():
                            issue("積分不是有限數值", record, points)
                        elif number < 0:
                            issue("負積分", record, points)
                        elif number != number.to_integral_value():
                            issue("積分含小數", record, points)
                        elif number > 2147483647:
                            issue("積分超過 integer 上限", record, points)

                if tour == "wta" and row["tours"] == "":
                    counts["缺少 tours"] += 1

        print(f"\n{path}")
        print(f"總筆數：{counts['總筆數']}")
        print(f"有效日期範圍：{first_date} ～ {last_date}")
        print(f"缺少積分：{counts['缺少積分']}")
        if tour == "wta":
            print(f"缺少 tours：{counts['缺少 tours']}")
        if not examples:
            print("本次檢查未發現格式或參照異常")
        for label, samples in examples.items():
            print(f"{label}：{counts[label]} 筆，範例：{samples}")
PY
```

### 已取得的結果

| 巡迴／檔案後綴 | 筆數 | 有效日期範圍 | 缺少積分 | 缺少 tours |
| --- | ---: | --- | ---: | ---: |
| ATP 00s | 920907 | 2000-01-10 ～ 2009-12-28 | 516 | 不適用 |
| ATP 10s | 915618 | 2010-01-04 ～ 2019-12-30 | 233 | 不適用 |
| ATP 20s | 516461 | 2020-01-06 ～ 2025-12-29 | 0 | 不適用 |
| ATP 70s | 20726 | 1973-08-27 ～ 1979-12-26 | 20726 | 不適用 |
| ATP 80s | 284809 | 1980-01-07 ～ 1989-12-18 | 284809 | 不適用 |
| ATP 90s | 725606 | 1990-01-01 ～ 1999-12-27 | 69796 | 不適用 |
| ATP current | 36468 | 2026-01-05 ～ 2026-06-08 | 0 | 不適用 |
| WTA 00s | 585927 | 2000-01-01 ～ 2009-12-28 | 0 | 413028 |
| WTA 10s | 621308 | 2010-01-01 ～ 2019-12-30 | 4 | 175981 |
| WTA 20s | 412744 | 2020-01-06 ～ 2025-12-29 | 0 | 0 |
| WTA 80s | 76364 | 1984-01-02 ～ 1989-12-25 | 18984 | 76045 |
| WTA 90s | 422830 | 1990-01-01 ～ 1999-12-27 | 42 | 409655 |
| WTA current | 35145 | 2026-01-05 ～ 2026-06-08 | 0 | 0 |

全部檔案未發現本次檢查所涵蓋的格式或參照異常。非空積分可用 integer，缺值仍為 NULL。20s 與 current 日期不重疊。最小與最大日期不代表每一期都齊全；tours 只統計缺值，未驗證其含義與數值。

## 4. 磁碟暫存檢查重複鍵與衝突分類

對話中先跑「唯一鍵檢查」，再跑「衝突分類」。下方將兩次相同的 CSV 載入流程合併成一段，保留兩次的統計與取樣結果，避免為了重做報告重複載入數百萬筆。整理後版本僅檢查語法，未重新跑完整資料；下列實際數字來自使用者已執行的兩次原始程式。

SQLite 是 Python 內建的本機資料庫，只供探索暫存；網站仍用 PostgreSQL。每批 5000 筆寫入磁碟，按每個巡迴建立索引。建立索引時可能需要等待與暫存磁碟空間。來源紀錄序號從標頭後第一筆算起，不是實體文字行號。

執行前應已通過第 3 節格式檢查；這不是可處理所有未驗證新資料的正式匯入器。

```bash
python3 - <<'PY'
import csv
import sqlite3
import tempfile
from collections import Counter
from decimal import Decimal
from pathlib import Path

for tour in ("atp", "wta"):
    with tempfile.TemporaryDirectory(prefix="tennis-ranking-check-") as temp:
        connection = sqlite3.connect(str(Path(temp) / "check.sqlite"))
        connection.execute("""
            CREATE TABLE rankings (
                ranking_date TEXT NOT NULL,
                player_id INTEGER NOT NULL,
                rank INTEGER NOT NULL,
                points INTEGER,
                tours TEXT,
                source_file TEXT NOT NULL,
                record_number INTEGER NOT NULL
            )
        """)
        total = 0
        files = sorted(Path(tour).glob(f"{tour}_rankings_*.csv"))
        if not files:
            raise RuntimeError(f"{tour}: 找不到排名檔案，請確認工作目錄")

        for path in files:
            batch = []
            with path.open(encoding="utf-8-sig", newline="") as file:
                for record, row in enumerate(csv.DictReader(file), start=1):
                    points = None
                    if row["points"] != "":
                        number = Decimal(row["points"])
                        if (
                            not number.is_finite()
                            or number != number.to_integral_value()
                            or not 0 <= number <= 2147483647
                        ):
                            raise ValueError(f"{path}:{record} 積分不符合先前驗證")
                        points = int(number)
                    batch.append((
                        row["ranking_date"], int(row["player"]),
                        int(row["rank"]), points, row.get("tours"),
                        path.name, record,
                    ))
                    total += 1
                    if len(batch) == 5000:
                        connection.executemany(
                            "INSERT INTO rankings VALUES (?, ?, ?, ?, ?, ?, ?)",
                            batch,
                        )
                        batch.clear()
                if batch:
                    connection.executemany(
                        "INSERT INTO rankings VALUES (?, ?, ?, ?, ?, ?, ?)", batch
                    )
            connection.commit()
            print(f"已讀取：{path}", flush=True)

        connection.execute("""
            CREATE INDEX ix_key ON rankings (ranking_date, player_id)
        """)
        categories = Counter()
        difference_types = Counter()
        conflict_dates = Counter()
        shown = Counter()
        duplicate_groups = 0
        extra_rows = 0
        conflict_rows = 0
        matching_extra_rows = 0

        duplicates = connection.execute("""
            SELECT ranking_date, player_id, COUNT(*)
            FROM rankings
            GROUP BY ranking_date, player_id
            HAVING COUNT(*) > 1
            ORDER BY ranking_date, player_id
        """)
        for date, player, count in duplicates:
            duplicate_groups += 1
            extra_rows += count - 1
            rows = connection.execute("""
                SELECT rank, points, tours, source_file, record_number
                FROM rankings
                WHERE ranking_date = ? AND player_id = ?
                ORDER BY source_file, record_number
            """, (date, player)).fetchall()
            changed = [
                name for index, name in enumerate(("rank", "points", "tours"))
                if len({row[index] for row in rows}) > 1
            ]
            if "rank" in changed or "points" in changed:
                category = "排名或積分衝突"
                conflict_dates[date] += 1
                conflict_rows += count
            elif "tours" in changed:
                category = "只有 tours 不同"
            else:
                category = "內容一致"
                matching_extra_rows += count - 1
            categories[category] += 1
            difference_types[", ".join(changed) or "無"] += 1

            # 各類最多列五組，避免一致重複遮住真正衝突的範例。
            if shown[category] < 5:
                shown[category] += 1
                print(f"\n{tour.upper()}｜{category}｜日期={date}，球員={player}")
                print(f"差異欄位：{changed}")
                print("內容：排名、積分、tours、來源檔、紀錄序號")
                for row in rows[:5]:
                    print(row)

        print(f"\n=== {tour.upper()} 結果 ===")
        print(f"總筆數：{total}")
        print(f"重複鍵組數：{duplicate_groups}")
        print(f"每組保留一筆後的多餘筆數：{extra_rows}")
        for category in ("內容一致", "排名或積分衝突", "只有 tours 不同"):
            print(f"{category}：{categories[category]} 組")
        print(f"差異欄位組合：{dict(difference_types)}")
        print(f"排名或積分衝突最多的日期：{conflict_dates.most_common(10)}")
        print(f"衝突原始紀錄數：{conflict_rows}")
        if categories["只有 tours 不同"] == 0:
            print(f"依目前規則預期正式筆數：{total - matching_extra_rows - conflict_rows}")
        else:
            print("只有 tours 不同的組別需另定規則，暫不計算預期正式筆數")
        connection.close()
PY
```

這段沒有刪除來源、沒有選擇勝出紀錄，也沒有建立正式排名表。「多餘筆數」僅是計數。每個巡迴分開處理，因此雖然暫存鍵只有日期與球員，對應的是完整的 `(tour, ranking_date, player_id)`。

## 5. 已確認的唯一鍵與衝突結果

| 項目 | ATP | WTA |
| --- | ---: | ---: |
| 原始筆數 | 3420595 | 2154318 |
| 重複鍵組數 | 780 | 281 |
| 每組保留一筆後的多餘筆數 | 780 | 281 |
| 轉換後內容一致的組數 | 181 | 55 |
| 排名或積分衝突組數 | 599 | 226 |
| 只有 tours 不同 | 0 | 0 |

重複組數與多餘筆數相同，表示每組恰好兩筆。

| 差異欄位 | ATP 組數 | WTA 組數 |
| --- | ---: | ---: |
| rank | 46 | 12 |
| points | 19 | 0 |
| rank, points | 534 | 63 |
| rank, tours | 0 | 2 |
| rank, points, tours | 0 | 149 |

範例：

- ATP 1973-08-27、球員 100054：兩筆排名皆 106、積分皆缺少，是一致重複。
- ATP 1979-12-26、球員 110788：排名 454 與 339，積分皆缺少，是排名衝突。
- ATP 球員 100905 在 1989 年多個日期重複出現不同排名，原因仍待查，不能直接判定為哪種來源錯誤。
- WTA 2004-05-10、球員 201469：排名 523／486，積分 23／28，tours 15／0，是內容衝突。

衝突最多的日期（以重複鍵組為單位）：

- ATP：2019-08-26 與 2020-09-21 各 5 組；2019-08-05、08-12、08-19、09-09、09-16、09-23、09-30、10-07 各 4 組。
- WTA：2024-04-29 有 12 組、2004-05-10 有 8 組、2024-06-03 與 2025-03-10 各 7 組、2023-10-09 有 6 組；2022-06-13、06-27、07-04、09-12、10-03 各 2 組。

集中日期是查核線索，不代表哪筆正確。不能用更高排名、更大積分、檔案先後順序決定保留哪筆。

## 6. 正式表規則與預期筆數

排名表的意義是「某巡迴的一位球員在某天的排名」，主鍵維持 `(tour, ranking_date, player_id)`。不能像比賽加入 round 那樣，把 rank 加入排名主鍵以容納矛盾紀錄。

規則（規劃中，尚未實作）：

1. 一致重複合併成一筆，保留所有來源位置。
2. rank 或 points 衝突時整組隔離，兩筆均不進正式表。
3. 保留原始 CSV；隔離紀錄保留快照、來源檔、紀錄序號、原值及原因。
4. 本次沒有 tours-only 衝突；未來若出現，先另定處理規則。
5. 隔離某天排名不刪除球員、不影響其他日期；缺口不自動填成 0 或前一期值。

目前每組都兩筆，因此：

```text
預期正式筆數 = 原始筆數 − 一致重複的多餘筆數 − 衝突組數 × 2
ATP = 3420595 − 181 − 599 × 2 = 3419216
WTA = 2154318 − 55 − 226 × 2 = 2153811
```

這些是目前快照、僅套用上述規則的預期值，不是實際匯入成果。若增加篩選條件或來源更新，必須重算。

## 7. 更新筆記與提交指令

教學中先後更新的內容已整理在 [資料建模筆記](01-data-model.md)，包括格式、唯一鍵、衝突分類與預期筆數。

原教學的單檔提交方式（本次多文件更新請改用下一段）：

```bash
cd /home/shumin/tennis-analytics
nano docs/01-data-model.md
git diff -- docs/01-data-model.md
git add docs/01-data-model.md
git diff --cached --check
git commit -m "docs: record ranking validation and conflict handling"
```

本次文件整理涉及五份文件，可自行檢查後一次提交：

```bash
git status --short
git diff --stat
git add docs/README.md docs/完整教學步驟與指令.md docs/01-data-model.md docs/02-data-inspection-commands.md docs/03-ranking-inspection-commands.md
git diff --cached --stat
git diff --cached --check
git commit -m "docs: document ranking inspection and conflict handling"
```

新文件在 git add 前不顯示於普通 git diff，因此搭配 git status 檢查。本次助手只更新文件，不執行暫存或提交。

## 8. 接下來的工作

- 比賽日期及其他數值型別驗證，包括 smallint、numeric(4,1)。
- 賽事級別納入範圍與未知球員處理。
- WTA tours 含義、有效值與排名日期完整性。
- 排名衝突來源查核、來源追溯與隔離資料表設計。
- 之後才完成 DDL、Flyway 與 Java 匯入器。
