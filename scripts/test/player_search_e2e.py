#!/usr/bin/env python3
"""Build-independent browser acceptance: isolated Docker PG + Git/CSV -> importer -> API -> Vite.
Prerequisites: backend JAR built, npm ci, npx playwright install chromium.
Never reads .env or uses the development PostgreSQL volume.
"""
import json
from datetime import date, timedelta
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
FRONTEND = ROOT / 'frontend'
JAR = ROOT / 'backend/target/tennis-0.0.1-SNAPSHOT.jar'

def run(*args, cwd=ROOT, env=None, output=None):
    return subprocess.run(args, cwd=cwd, env=env, check=True, text=True,
                          stdout=output or subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)

def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]

def wait_url(url, process):
    for _ in range(120):
        if process.poll() is not None:
            raise RuntimeError('Server exited before readiness: ' + url)
        try:
            with urllib.request.urlopen(url, timeout=1) as response:
                if response.status == 200:
                    return
        except (OSError, TimeoutError):
            pass
        time.sleep(.5)
    raise RuntimeError('Server readiness timeout: ' + url)

def main():
    if not JAR.exists():
        raise RuntimeError('Build backend JAR before running this script')
    temp = Path(tempfile.mkdtemp(prefix='tennis-search-e2e-'))
    container = None
    processes = []
    logs = []
    try:
        password = uuid.uuid4().hex
        container = run('docker', 'run', '--rm', '-d', '-p', '127.0.0.1::5432',
                        '-e', 'POSTGRES_DB=tennis_e2e', '-e', 'POSTGRES_USER=tennis_test',
                        '-e', 'POSTGRES_PASSWORD=' + password, 'postgres:16-bookworm').stdout.strip()
        port = json.loads(run('docker', 'inspect', container).stdout)[0]['NetworkSettings']['Ports']['5432/tcp'][0]['HostPort']
        for _ in range(60):
            result = subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'tennis_test', '-d', 'tennis_e2e'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
            if result.returncode == 0:
                break
            time.sleep(.5)
        else:
            raise RuntimeError('Postgres readiness timeout')
        repo = temp / 'repo'
        repo.mkdir()
        for args in [('init', '-q'), ('config', 'user.name', 'Fixture'), ('config', 'user.email', 'fixture@example.com'), ('config', 'commit.gpgsign', 'false'), ('remote', 'add', 'origin', 'https://example.com/tennis')]:
            run('git', *args, cwd=repo)
        header = 'player_id,name_first,name_last,hand,dob,ioc,height,wikidata_id\n'
        (repo / 'atp').mkdir(); (repo / 'wta').mkdir()
        atp = '1,Roger,Federer,R,19810808,SUI,185,Q1\n2,U,Unknown,U,,,,\n'
        atp += ''.join(f'{n},Alex,Player{n:02},R,19900101,USA,180,\n' for n in range(10,30))
        (repo / 'atp/atp_players.csv').write_text(header + atp)
        (repo / 'wta/wta_players.csv').write_text(header + '1,Serena,Williams,R,19810926,USA,175,Q2\n')
        ranking_rows = ''.join(f'{(date(2024, 1, 1) + timedelta(days=n)).strftime("%Y%m%d")},{n+1},1,{0 if n == 24 else ""}\n' for n in range(25))
        (repo / 'atp/atp_rankings_20s.csv').write_text('ranking_date,rank,player,points\n' + ranking_rows)
        (repo / 'wta/wta_rankings_20s.csv').write_text('ranking_date,rank,player,points,tours\n20240101,2,1,100,0\n')
        run('git', 'add', '.', cwd=repo); run('git', 'commit', '-qm', 'synthetic player fixture', cwd=repo)
        sha = run('git', 'rev-parse', 'HEAD', cwd=repo).stdout.strip()
        archive = temp / 'archive'
        run(str(ROOT / 'backend/source-snapshot'), 'prepare', str(repo), 'https://example.com/tennis', sha, str(archive))
        api_port, ui_port = free_port(), free_port()
        while ui_port == api_port:
            ui_port = free_port()
        env = {**os.environ, 'DB_URL': f'jdbc:postgresql://127.0.0.1:{port}/tennis_e2e',
               'DB_USERNAME': 'tennis_test', 'DB_PASSWORD': password, 'IMPORT_TARGET': 'candidate',
               'SERVER_PORT': str(api_port), 'LOGGING_LEVEL_ROOT': 'WARN'}
        with (temp / 'import.log').open('w') as log:
            run(str(ROOT / 'backend/import-players'), str(repo), 'https://example.com/tennis', sha, str(archive), 'a' * 40, env=env, output=log)
        with (temp / 'rankings.log').open('w') as log:
            run(str(ROOT / 'backend/import-rankings'), str(repo), 'https://example.com/tennis', sha, str(archive), 'a' * 40, env=env, output=log)
        backend_log = (temp / 'backend.log').open('w'); logs.append(backend_log)
        backend = subprocess.Popen(['java', '-jar', str(JAR)], env=env, stdout=backend_log, stderr=subprocess.STDOUT)
        processes.append(backend)
        wait_url(f'http://127.0.0.1:{api_port}/tennis/api/v1/health', backend)
        ui_log = (temp / 'frontend.log').open('w'); logs.append(ui_log)
        ui = subprocess.Popen(['npm', 'run', 'dev', '--', '--host', '127.0.0.1', '--port', str(ui_port), '--strictPort'], cwd=FRONTEND,
                              env={**os.environ, 'API_PROXY_TARGET': f'http://127.0.0.1:{api_port}'}, stdout=ui_log, stderr=subprocess.STDOUT, start_new_session=True)
        processes.append(ui)
        wait_url(f'http://127.0.0.1:{ui_port}/tennis/', ui)
        browser_env = {**os.environ, 'E2E_BASE_URL': f'http://127.0.0.1:{ui_port}'}
        browser_env.pop('E2E_MOCK_API', None)
        subprocess.run(['npm', 'run', 'test:e2e'], cwd=FRONTEND,
                       env=browser_env, check=True, timeout=180)
        print('PASS: synthetic Git -> archive -> PostgreSQL import -> HTTP API -> browser')
    except Exception:
        print('Failure logs retained at:', temp)
        raise
    finally:
        import signal
        for process in reversed(processes):
            if process.poll() is None:
                if process is processes[-1] and len(processes) > 1:
                    os.killpg(process.pid, signal.SIGTERM)
                else:
                    process.terminate()
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
        for log in logs: log.close()
        if container:
            subprocess.run(['docker', 'rm', '-f', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
        # Keep test logs for diagnosis; never leave containers, processes or readonly fixture trees.
        for path, _, _ in os.walk(temp): os.chmod(path, 0o700)
        for name in ('repo', 'archive'): shutil.rmtree(temp / name, ignore_errors=True)

if __name__ == '__main__':
    main()
