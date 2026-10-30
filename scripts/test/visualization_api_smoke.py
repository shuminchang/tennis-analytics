#!/usr/bin/env python3
"""Read-only local candidate API smoke/timings, compared to the Phase 11.1 report."""
import argparse
import json
from pathlib import Path
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base', default='http://127.0.0.1:8082/tennis/api')
parser.add_argument('--output', default='.local/verification/visualization-api-smoke.json')
args = parser.parse_args()
url = urllib.parse.urlparse(args.base)
if url.scheme != 'http' or url.hostname not in ('127.0.0.1', 'localhost'):
    raise ValueError('Use a local HTTP verification service')
baseline = json.loads((ROOT / 'docs/reports/2026-10-30-visualization-contract.json').read_text())
report = {'sourceSha': baseline['snapshots'][0]['commit_sha'], 'tests': [], 'sequential': True, 'writes': False}
for expected in baseline['latest_top100']:
    tour = expected['tour']
    for kind in ('metadata', 'scatter', 'histogram'):
        timings = []
        for _ in range(21):
            start = time.perf_counter()
            with urllib.request.urlopen(args.base + '/v1/visualizations/' + kind + '?tour=' + tour, timeout=15) as response:
                body = json.load(response)
            timings.append((time.perf_counter() - start) * 1000)
            context = body if kind == 'metadata' else body['context']
            assert context['source']['snapshotSha'] == report['sourceSha']
            if kind == 'metadata':
                assert body['latestRankingDate'] == expected['ranking_date']
            else:
                assert body['sample']['population'] == expected['population']
                assert body['sample']['valid'] == expected['height_valid']
                assert body['sample']['excluded'] == expected['population'] - expected['height_valid']
                assert sum(r['count'] for r in body['sample']['primaryReasons']) == body['sample']['excluded']
                if kind == 'scatter':
                    assert len(body['items']) == body['sample']['valid']
                else:
                    assert sum(b['count'] for b in body['bins']) == body['sample']['valid']
        report['tests'].append({'tour': tour, 'endpoint': kind, 'coldMs': round(timings[0], 3), 'warmP95Ms': round(sorted(timings[1:])[18], 3), 'warmCalls': 20})
report['warmTargetPassed'] = all(r['warmP95Ms'] < 500 for r in report['tests'])
output = Path(args.output);output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
if not report['warmTargetPassed']:
    raise SystemExit('Warm p95 target failed; cold results retained separately')
