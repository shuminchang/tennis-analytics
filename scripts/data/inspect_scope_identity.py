#!/usr/bin/env python3
"""Read-only follow-up inspection; writes a JSON report to stdout, never to the source."""
import argparse
import csv
import hashlib
import json
import re
import subprocess
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

EXPECTED_SNAPSHOT = '83733587353df8a41f2fd4f516147d5aa83f5a8d'


def classify_name(name):
    name = ' '.join(name.lower().split())
    if re.fullmatch(r'(?:u )?unknown(?: unknown)?|x(?: x)?', name):
        return 'placeholder_label'
    if not name:
        return 'empty_name'
    if re.search(r'\bunknown\b', name):
        return 'ambiguous_unknown_label'
    return None


def date_or_none(raw):
    try:
        if not re.fullmatch(r'[0-9]{8}', raw):
            return None
        return datetime.strptime(raw, '%Y%m%d').date()
    except ValueError:
        return None


def parse_scope(path):
    text = path.read_text(encoding='utf-8')
    entries = {}

    def add(tour, event_id, level, status):
        key = (tour, event_id)
        value = (level, status)
        if key in entries and entries[key] != value:
            raise ValueError(f'Conflicting scope entries: {key}')
        entries[key] = value

    section = ''
    for line in text.splitlines():
        if line.startswith('## '):
            section = line[3:].strip()
        if not re.match(r'^\|\s*(ATP|WTA)\s*\|', line):
            continue
        cells = [x.strip() for x in line.strip().strip('|').split('|')]
        if section == '已查證：奧運示範賽':
            add(cells[0], cells[1], 'O', 'EXCLUDE')
        elif section == '已確認納入：正式奧運單打':
            add(cells[0], cells[1], 'O', 'INCLUDE')
        elif section == 'WTA W：已查證的近期賽事':
            add(cells[0], cells[1], 'W', 'EXCLUDE' if '排除' in cells[-1] else 'INCLUDE')
    series = {
        'ATP_FINALS': ('ATP', 'INCLUDE', 35),
        'NEXT_GEN_FINALS': ('ATP', 'EXCLUDE', 6),
        'WTA_FINALS': ('WTA', 'INCLUDE', 25),
        'WTA_TOURNAMENT_OF_CHAMPIONS': ('WTA', 'INCLUDE', 6),
        'WTA_ELITE_TROPHY': ('WTA', 'INCLUDE', 5),
    }
    for label, (tour, status, count) in series.items():
        pattern = rf'^### {label}：[^\n]+\n(?:(?!^###? ).)*?```text\n(.*?)\n```'
        match = re.search(pattern, text, re.M | re.S)
        if not match:
            raise ValueError(f'Missing scope block {label}')
        ids = match.group(1).splitlines()
        if len(ids) != count or len(set(ids)) != count:
            raise ValueError(f'Unexpected ID count: {label}')
        for event_id in ids:
            add(tour, event_id.strip(), 'F', status)
    if '1982-806' not in text:
        raise ValueError('Missing Naples review entry')
    add('ATP', '1982-806', 'F', 'REVIEW')
    if '| ATP | 2023-0605 |' not in text:
        raise ValueError('Missing 2023 ATP Finals A exception')
    add('ATP', '2023-0605', 'A', 'INCLUDE')
    if len(entries) != 104:
        raise ValueError(f'Expected 104 keys, got {len(entries)}')
    return entries


def file_digest(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-root', type=Path, required=True)
    parser.add_argument('--scope', type=Path, required=True)
    parser.add_argument('--skip-rankings', action='store_true', help='Skip previously verified ranking coverage')
    args = parser.parse_args()
    root = args.data_root.resolve()
    snapshot = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    if snapshot != EXPECTED_SNAPSHOT:
        raise ValueError('Unexpected snapshot; review rules before running on a new snapshot')
    dirty = subprocess.check_output(['git', '-C', str(root), 'status', '--porcelain', '--untracked-files=no'], text=True)
    if dirty.strip():
        raise ValueError('Tracked source changes detected; cannot claim snapshot reproducibility')
    exceptions = parse_scope(args.scope)
    report = {'snapshot': snapshot, 'scope_file': args.scope.name, 'scope_policy': '05-tournament-scope explicit exceptions then level defaults',
              'rankings_skipped': args.skip_rankings, 'scope_sha256': file_digest(args.scope),
              'purpose': 'inspection only; no database import or source mutation', 'tours': {}}
    digests = {}

    def rows(path):
        digests[str(path.relative_to(root))] = file_digest(path)
        with path.open(encoding='utf-8-sig', newline='') as stream:
            yield from csv.DictReader(stream)

    for tour in ('ATP', 'WTA'):
        folder = root / tour.lower()
        players = {}
        normalized_counts = Counter()
        candidates = {}
        for row in rows(folder / f'{tour.lower()}_players.csv'):
            pid = str(int(row['player_id']))
            normalized_counts[pid] += 1
            name = ' '.join((row['name_first'] + ' ' + row['name_last']).split())
            players[pid] = {'name': name, 'dob': date_or_none(row['dob'])}
            reason = classify_name(name)
            if reason:
                candidates[pid] = {'name': name, 'reason': reason, 'match_role_appearances': 0}
        findings = {'player_integer_key_collisions': [k for k,v in normalized_counts.items() if v > 1],
                    'name_candidates': candidates}
        variants = defaultdict(set)
        scope_counts = Counter()
        level_scope = Counter()
        identity_counts = Counter()
        identity_scope = Counter()
        identity_rows = []
        cross_year = Counter()
        discovered = set()
        surface = Counter()
        score_markers = Counter()
        files = sorted(p for p in folder.iterdir() if re.fullmatch(rf'{tour.lower()}_matches_[0-9]{{4}}\.csv',p.name))
        if len(files) != 59:
            raise ValueError(f'Expected 59 match files for {tour}')
        for path in files:
            for record, row in enumerate(rows(path), 1):
                if None in row or any(v is None for v in row.values()):
                    raise ValueError(f'Malformed CSV: {path}:{record}')
                event_id, level = row['tourney_id'], row['tourney_level']
                key = (tour, event_id)
                date = date_or_none(row['tourney_date'])
                if date is None:
                    raise ValueError(f'Unexpected invalid match date: {path}:{record}')
                variants[event_id].add((row['tourney_date'], row['tourney_name'], level))
                if key in exceptions:
                    status = exceptions[key][1]
                elif level in ({'A','G','M','D'} if tour=='ATP' else {'G','I','P','PM','T1','T2','T3','T4','T5','D'}):
                    status = 'INCLUDE'
                elif level in {'E','J'} or (tour=='WTA' and level in {'35+H','50+H'}):
                    status = 'EXCLUDE'
                else:
                    status = 'REVIEW'
                scope_counts[status] += 1
                level_scope[f'{level}:{status}'] += 1
                surface[row['surface'] or '(empty)'] += 1
                for marker in ('W/O','RET','DEF','UNK'):
                    if marker in row['score'].upper():
                        score_markers[marker] += 1
                if path.name.endswith('1968.csv') and date.year==1967:
                    cross_year[(event_id,row['tourney_name'],row['tourney_date'])] += 1
                if re.search(r'final|master.*cup|championship|olympic', row['tourney_name'], re.I):
                    if (tour=='ATP' and path.name.endswith('2023.csv')) or (tour=='WTA' and path.name.endswith('2015.csv')):
                        discovered.add((event_id,row['tourney_name'],level,row['tourney_date']))
                reasons = set()
                role_details = []
                for role in ('winner','loser'):
                    pid = str(int(row[f'{role}_id']))
                    master = players.get(pid)
                    if pid in candidates:
                        candidates[pid]['match_role_appearances'] += 1
                        reasons.add('master_'+candidates[pid]['reason'])
                    match_reason = classify_name(row[f'{role}_name'])
                    if match_reason:
                        reasons.add('match_'+match_reason)
                    if master is None:
                        reasons.add('missing_player')
                    elif master['dob'] and master['dob'] > date:
                        reasons.add('birth_after_event')
                    if pid in candidates or match_reason or (master and master['dob'] and master['dob'] > date):
                        role_details.append({'role':role,'player_id':pid,'source_name':row[f'{role}_name'],
                                             'master_name':master['name'] if master else None,
                                             'dob':str(master['dob']) if master and master['dob'] else None})
                if int(row['winner_id'])==int(row['loser_id']):
                    reasons.add('same_player')
                if reasons:
                    identity_counts.update(reasons)
                    identity_scope[status] += 1
                    identity_rows.append({'file':str(path.relative_to(root)), 'record':record,'event_id':event_id,
                        'round':row['round'],'match_num':row['match_num'],'scope':status,
                        'reasons':sorted(reasons),'players':role_details})
        scope_checks=[]
        for (t,event_id),(level,status) in exceptions.items():
            if t!=tour:
                continue
            actual=sorted(variants.get(event_id,set()))
            scope_checks.append({'event_id':event_id,'expected_level':level,'status':status,'variants':actual,
                                 'ok':len(actual)==1 and actual[0][2]==level})
        findings.update({'scope_exception_count':len(scope_checks),'scope_exception_checks':scope_checks,
            'scope_row_counts':dict(scope_counts),'level_scope_counts':dict(sorted(level_scope.items())),
            'all_event_variant_conflicts':[{'event_id':k,'variants':sorted(v)} for k,v in sorted(variants.items()) if len(v)>1],
            'identity_reason_counts':dict(identity_counts),'identity_row_count':len(identity_rows),
            'identity_scope_counts':dict(identity_scope),'identity_rows':identity_rows,
            '1968_file_1967_dates':[{'event_id':k[0],'name':k[1],'date':k[2],'rows':v} for k,v in sorted(cross_year.items())],
            'targeted_event_name_search':sorted(discovered),'surface_counts':dict(surface),'score_marker_counts':dict(score_markers)})
        if args.skip_rankings:
            report['tours'][tour] = findings
            continue
        # New questions only: date coverage and WTA tours. Do not repeat ranking duplicate scan.
        ranking_dates = Counter()
        tours_values = Counter()
        tours_invalid = []
        rank_date_parse_cache = {}
        ranking_count = 0
        for path in sorted(folder.glob(f'{tour.lower()}_rankings_*.csv')):
            for record,row in enumerate(rows(path),1):
                ranking_count += 1
                raw=row['ranking_date']
                if raw not in rank_date_parse_cache:
                    rank_date_parse_cache[raw]=date_or_none(raw)
                date=rank_date_parse_cache[raw]
                if date is None:
                    raise ValueError(f'Unexpected invalid ranking date: {path}:{record}')
                ranking_dates[date] += 1
                if tour=='WTA':
                    value=row['tours']
                    tours_values[value] += 1
                    if value and (not re.fullmatch(r'[0-9]+',value) or int(value)>2147483647):
                        if len(tours_invalid)<20:
                            tours_invalid.append((path.name,record,value))
        dates=sorted(ranking_dates)
        gaps=sorted((( (b-a).days,str(a),str(b)) for a,b in zip(dates,dates[1:]) if (b-a).days>14),reverse=True)
        findings['ranking_coverage']={'source_rows':ranking_count,'distinct_dates':len(dates),
            'first':str(dates[0]),'last':str(dates[-1]),'gaps_over_14_days':len(gaps),'largest_gaps':gaps[:10],
            'dates_by_year':dict(sorted(Counter(d.year for d in dates).items()))}
        if tour=='WTA':
            findings['tours_values']=dict(sorted(tours_values.items()))
            findings['tours_invalid_examples']=tours_invalid
        report['tours'][tour]=findings
    report['input_sha256']=dict(sorted(digests.items()))
    report['all_scope_exceptions_pass']=all(e['ok'] for t in report['tours'].values() for e in t['scope_exception_checks'])
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':
    main()
