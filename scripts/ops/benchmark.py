#!/usr/bin/env python3
"""Local HTTP timings. Cold request separate from warm p95; optional temporary QA user.
Authenticated mode creates/removes only its own random account in the specified local Compose DB.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import http.cookiejar
import json
from pathlib import Path
import re
import secrets
import statistics
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base', default='http://127.0.0.1:8082/tennis/api')
parser.add_argument('--output', default='.local/verification/performance.json')
parser.add_argument('--authenticated', action='store_true')
parser.add_argument('--measure-only', action='store_true', help='Research suite: record timing target misses without skipping remaining acceptance checks')
parser.add_argument('--research', action='store_true', help='Bounded visualization/research endpoint suite (requires authenticated owned budget container)')
parser.add_argument('--database', default='tennis_candidate')
parser.add_argument('--container')
parser.add_argument('--db-user')
args = parser.parse_args()
if args.measure_only and not args.research:
    raise ValueError('Measure-only applies to research acceptance only')
if args.research and (not args.authenticated or not args.container):
    raise ValueError('Research benchmark requires an isolated authenticated budget container')
if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', args.database):
    raise ValueError('Invalid database identifier')
if args.container and not re.fullmatch(r'tennis_budget_db_[0-9a-f]{8,12}', args.container):
    raise ValueError('Standalone target must be an owned budget test container')
if not args.base.startswith(('http://127.0.0.1:', 'http://localhost:')):
    raise ValueError('Benchmark is limited to local HTTP services')
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
user = None
username = None
qa_path = Path(args.output).with_suffix('.qa.json')

def sql(query):
    return subprocess.run((['docker', 'exec', '-i', args.container] if args.container else ['docker', 'compose', 'exec', '-T', 'db']) + ['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', args.database], cwd=ROOT, input=query, text=True, stdout=subprocess.PIPE, check=True, timeout=30).stdout.strip()

def write(path, fields=None):
    with opener.open(args.base + '/auth/csrf', timeout=15) as response:
        csrf = json.load(response)
    request = urllib.request.Request(args.base + path, data=urllib.parse.urlencode(fields or {}).encode(), headers={csrf['headerName']: csrf['token'], 'Content-Type': 'application/x-www-form-urlencoded'}, method='POST')
    with opener.open(request, timeout=15) as response:
        response.read()

try:
    if args.authenticated:
        if args.container:
            user = args.db_user
            if not user or not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', user):
                raise ValueError('A valid budget DB user is required')
        else:
            config = json.loads(subprocess.run(['docker', 'compose', 'config', '--format', 'json'], cwd=ROOT, check=True, text=True, stdout=subprocess.PIPE, timeout=15).stdout)
            user = config['services']['db']['environment']['POSTGRES_USER']
        username = 'qa_bench_' + uuid.uuid4().hex[:12]
        password = secrets.token_urlsafe(24)
        crypto = sorted((Path.home() / '.m2/repository/org/springframework/security/spring-security-crypto').glob('*/spring-security-crypto-*.jar'))[-1]
        logging = sorted((Path.home() / '.m2/repository/org/springframework/spring-jcl').glob('*/spring-jcl-*.jar'))[-1]
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / 'QaPassword.java'
            source.write_text('import java.io.*; import org.springframework.security.crypto.bcrypt.BCrypt; class QaPassword { public static void main(String[] a) throws Exception { String p=new BufferedReader(new InputStreamReader(System.in)).readLine(); System.out.print(BCrypt.hashpw(p,BCrypt.gensalt(12))); } }')
            encoded = subprocess.run(['java', '-cp', str(crypto) + ':' + str(logging), str(source)], input=password + '\n', text=True, stdout=subprocess.PIPE, check=True, timeout=30).stdout.strip()
        qa_path.parent.mkdir(parents=True, exist_ok=True)
        qa_path.write_text(json.dumps({'username': username, 'database': args.database, 'status': 'RUNNING'}) + '\n')
        sql(f"INSERT INTO app_users(username,password_hash,role) VALUES('{username}','{encoded}','ADVANCED');")
        write('/auth/login', {'username': username, 'password': password})
    paths = ['/v1/health', '/v1/players?q=Federer', '/v1/players/ATP/103819/summary', '/v1/players/ATP/103819/analysis', '/v1/tournaments?tour=ATP', '/v1/rankings?tour=ATP']
    if args.authenticated:
        paths += ['/v1/h2h?tour=ATP&first=103819&second=104745', '/v1/players/ATP/103819/elo']
    if args.research:
        window = 'tour=ATP&fromYear=2017&toYear=2026&surface=ALL'
        paths = ['/v1/visualizations/metadata?tour=ATP',
                 '/v1/visualizations/scatter?tour=ATP&rankingDate=2026-06-08&topN=100&x=height_cm&y=rank&color=hand',
                 '/v1/visualizations/histogram?tour=ATP&rankingDate=2026-06-08&topN=100&metric=height_cm&bins=10',
                 '/v1/visualizations/age-performance?' + window,
                 '/v1/visualizations/profile-performance?' + window + '&limit=1000',
                 '/v1/visualizations/correlations?' + window + '&limit=1000',
                 '/v1/visualizations/age-performance?' + window.replace('ATP', 'WTA'),
                 '/v1/visualizations/profile-performance?' + window.replace('ATP', 'WTA') + '&limit=1000',
                 '/v1/visualizations/profile-performance?' + window + '&limit=500']
    result = {}
    for path in paths:
        samples = []; max_bytes = 0; first_body = None
        for attempt in range(21):
            start = time.perf_counter()
            with opener.open(args.base + path, timeout=15) as response:
                if response.status != 200:
                    raise RuntimeError('Non-success response')
                payload = response.read(); max_bytes = max(max_bytes, len(payload))
            samples.append((time.perf_counter() - start) * 1000)
            if attempt == 0: first_body = json.loads(payload)
        warm = sorted(samples[1:])
        result[path] = {'cold_ms': round(samples[0], 2), 'warm_p50_ms': round(statistics.median(warm), 2), 'warm_p95_ms': round(warm[18], 2), 'warm_mean_ms': round(statistics.mean(warm), 2), 'samples': 20, 'maximum_response_bytes': max_bytes, 'sample': first_body.get('sample'), 'population':first_body.get('population')}
        output = Path(args.output);output.parent.mkdir(parents=True, exist_ok=True);output.write_text(json.dumps(result, indent=2) + '\n')
    if args.research:
        # Warm, same-key concurrent GETs; each worker owns its HTTP connection.
        # Cookies are copied as a header, without sharing a mutable cookie jar.
        cookie = next(handler.cookiejar for handler in opener.handlers if isinstance(handler, urllib.request.HTTPCookieProcessor))
        cookie_header = '; '.join(c.name + '=' + c.value for c in cookie)
        def timed(path):
            start = time.perf_counter()
            request = urllib.request.Request(args.base + path, headers={'Cookie': cookie_header})
            with urllib.request.urlopen(request, timeout=15) as response:
                response.read()
            return (time.perf_counter() - start) * 1000
        for path in [paths[3], paths[5]]:
            with ThreadPoolExecutor(max_workers=4) as pool:
                samples = sorted(pool.map(timed, [path] * 16))
            result[path]['concurrent_4'] = {'requests':16, 'p50_ms':round(statistics.median(samples),2), 'p95_ms':round(samples[-1],2)}
    output = Path(args.output);output.parent.mkdir(parents=True, exist_ok=True);output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    if any(row['warm_p95_ms'] >= (1000 if '/h2h?' in path else 500) for path, row in result.items()):
        if args.measure_only:
            print('MEASURED TARGET MISS: warm p95 exceeds 500ms; this is not a passing latency SLO')
        else:
            raise SystemExit('FAIL: warm p95 exceeds target; cold timings remain separately reported')
finally:
    if username and user:
        try:
            write('/auth/logout')
        finally:
            sql(f"DELETE FROM app_users WHERE username='{username}';")
            qa_path.write_text(json.dumps({"username": username, "database": args.database, "status": "CLEANED"}) + "\n")
