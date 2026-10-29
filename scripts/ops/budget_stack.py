#!/usr/bin/env python3
"""Restore a verified local backup into an isolated, CPU/memory-bounded PG/Java/Apache stack.
Own containers/network only; source candidate and its Docker volume are never touched.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('manifest', type=Path)
args = parser.parse_args()
manifest = json.loads(args.manifest.read_text())
dump = args.manifest.parent / 'database.dump'
if manifest.get('status') != 'SUCCEEDED' or not manifest.get('restore_verified'):
    raise ValueError('Use a successfully restored backup manifest')
digest = hashlib.sha256()
with dump.open('rb') as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b''):
        digest.update(chunk)
if digest.hexdigest() != manifest['sha256']:
    raise ValueError('Backup checksum mismatch')
run_id = uuid.uuid4().hex[:8]
output = ROOT / '.local/verification' / ('budget-' + run_id)
output.mkdir(parents=True)
network = 'tennis_budget_net_' + run_id
containers = []
network_created = False
password = secrets.token_urlsafe(32)
db_name = 'tennis_budget_db_' + run_id
app_name = 'tennis_budget_app_' + run_id
jar = output / 'application.jar'
shutil.copyfile(ROOT / 'backend/target/tennis-0.0.1-SNAPSHOT.jar', jar)
report = {'source_backup_sha256': manifest['sha256'], 'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'limits_mib': {'postgres': 320, 'java': 448, 'apache': 64}, 'cpu_quotas': {'postgres': .5, 'java': .4, 'apache': .1}, 'status': 'RUNNING'}

def checkpoint():
    report['owned_network'] = network
    report['owned_container_names'] = [db_name, app_name, 'tennis_budget_proxy_' + run_id]
    report['created_container_ids'] = list(containers)
    temporary = output / 'report.json.tmp'
    temporary.write_text(json.dumps(report, indent=2) + '\n')
    temporary.replace(output / 'report.json')

checkpoint()

def run(*command, **kwargs):
    return subprocess.run(command, cwd=ROOT, check=True, timeout=kwargs.pop('timeout', 60), **kwargs)

def start(name, image, memory, cpu, *extra, env=None):
    result = run('docker', 'run', '-d', '--name', name, '--network', network, '--memory', memory, '--memory-swap', memory, '--cpus', str(cpu), *extra, image,
                 env=env, text=True, stdout=subprocess.PIPE)
    container = result.stdout.strip()
    containers.append(container)
    checkpoint()
    return container

try:
    run('docker', 'version', timeout=15, stdout=subprocess.DEVNULL)
    for image in ['postgres:16-bookworm', 'eclipse-temurin:21-jre', 'httpd:2.4']:
        if subprocess.run(['docker', 'image', 'inspect', image], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15).returncode != 0:
            run('docker', 'pull', image, timeout=600)
    run('docker', 'network', 'create', network, stdout=subprocess.DEVNULL);network_created = True
    print('Restoring backup into 320 MiB PostgreSQL test container...', flush=True)
    db = start(db_name, 'postgres:16-bookworm', '320m', .5, '-e', 'POSTGRES_USER=budget_user', '-e', 'POSTGRES_DB=tennis_candidate', '-e', 'POSTGRES_PASSWORD',
               env={**os.environ, 'POSTGRES_PASSWORD': password})
    # Persistent settings in this disposable test container only.
    for _ in range(60):
        ready = subprocess.run(['docker', 'exec', db, 'pg_isready', '-U', 'budget_user', '-d', 'tennis_candidate'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
        if ready.returncode == 0:
            break
        time.sleep(1)
    else:
        raise RuntimeError('Budget PostgreSQL did not start')
    run('docker', 'exec', db, 'psql', '-U', 'budget_user', '-d', 'tennis_candidate', '-c', "ALTER SYSTEM SET shared_buffers='32MB';", stdout=subprocess.DEVNULL)
    for setting in ["work_mem='2MB'", "maintenance_work_mem='16MB'", "max_parallel_maintenance_workers=0", "max_connections=20", "jit=off"]:
        run('docker', 'exec', db, 'psql', '-U', 'budget_user', '-d', 'tennis_candidate', '-c', 'ALTER SYSTEM SET ' + setting + ';', stdout=subprocess.DEVNULL)
    run('docker', 'restart', db, stdout=subprocess.DEVNULL)
    for _ in range(60):
        ready = subprocess.run(['docker', 'exec', db, 'pg_isready', '-U', 'budget_user', '-d', 'tennis_candidate'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
        if ready.returncode == 0:
            break
        time.sleep(1)
    else:
        raise RuntimeError('Budget PostgreSQL did not restart')
    with dump.open('rb') as stream, (output / 'restore.log').open('w') as log:
        run('docker', 'exec', '-i', db, 'pg_restore', '-U', 'budget_user', '-d', 'tennis_candidate', '--no-owner', '--no-acl', '--exit-on-error', stdin=stream, stdout=subprocess.DEVNULL, stderr=log, timeout=1800)
    print('Starting 448 MiB Java and 64 MiB Apache test containers...', flush=True)
    result = run('docker', 'run', '-d', '--name', app_name, '--network', network, '--memory', '448m', '--memory-swap', '448m', '--cpus', '.4',
                 '-v', str(jar) + ':/app/app.jar:ro', '-e', 'DB_URL=jdbc:postgresql://' + db_name + ':5432/tennis_candidate', '-e', 'DB_USERNAME=budget_user', '-e', 'DB_PASSWORD',
                 'eclipse-temurin:21-jre', 'java', '-Xms64m', '-Xmx256m', '-XX:MaxMetaspaceSize=128m', '-Xss512k', '-XX:ActiveProcessorCount=1', '-jar', '/app/app.jar', '--server.address=0.0.0.0', '--server.port=8081', '--logging.level.root=INFO', '--logging.level.org.springframework=INFO',
                 env={**os.environ, 'DB_PASSWORD': password}, text=True, stdout=subprocess.PIPE)
    app = result.stdout.strip();containers.append(app);checkpoint()
    config = run('docker', 'run', '--rm', 'httpd:2.4', 'cat', '/usr/local/apache2/conf/httpd.conf', text=True, stdout=subprocess.PIPE).stdout
    for module in ['proxy', 'proxy_http', 'headers']:
        config = config.replace('#LoadModule ' + module + '_module', 'LoadModule ' + module + '_module')
    config += f'''
ServerName localhost
<IfModule mpm_event_module>
StartServers 1
ServerLimit 1
ThreadsPerChild 10
MaxRequestWorkers 10
MaxConnectionsPerChild 1000
</IfModule>
ProxyPreserveHost On
RequestHeader unset Forwarded
RequestHeader unset X-Forwarded-For
RequestHeader unset X-Forwarded-Proto
RequestHeader set X-Forwarded-Proto "http"
ProxyPass /tennis/api http://{app_name}:8081/tennis/api
ProxyPassReverse /tennis/api http://{app_name}:8081/tennis/api
<Directory "/usr/local/apache2/htdocs/tennis">
Options -Indexes
Require all granted
FallbackResource /tennis/index.html
Header always set X-Content-Type-Options "nosniff"
Header always set Referrer-Policy "strict-origin-when-cross-origin"
Header always set Content-Security-Policy "default-src 'self'; img-src 'self' data:; font-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
</Directory>
'''
    config_path = output / 'httpd.conf';config_path.write_text(config)
    proxy = start('tennis_budget_proxy_' + run_id, 'httpd:2.4', '64m', .1, '-p', '127.0.0.1::80', '-v', str(config_path) + ':/usr/local/apache2/conf/httpd.conf:ro', '-v', str(ROOT / 'frontend/dist') + ':/usr/local/apache2/htdocs/tennis:ro')
    port = json.loads(run('docker', 'inspect', '--format', '{{json .NetworkSettings.Ports}}', proxy, text=True, stdout=subprocess.PIPE).stdout)['80/tcp'][0]['HostPort']
    base = 'http://127.0.0.1:' + port + '/tennis/api'
    for _ in range(120):
        try:
            with urllib.request.urlopen(base + '/v1/health', timeout=2) as response:
                if response.status == 200:
                    break
        except OSError:
            pass
        time.sleep(1)
    else:
        raise RuntimeError('Budget stack readiness timeout')
    print('Benchmarking through Apache with real source data...', flush=True)
    run(sys.executable, str(ROOT / 'scripts/ops/benchmark.py'), '--base', base, '--authenticated', '--container', db_name, '--db-user', 'budget_user', '--output', str(output / 'performance.json'), stdout=(output / 'benchmark.log').open('w'), timeout=600)
    report['performance'] = json.loads((output / 'performance.json').read_text())
    report['container_stats'] = [json.loads(run('docker', 'stats', '--no-stream', '--format', '{{json .}}', c, text=True, stdout=subprocess.PIPE).stdout) for c in containers]
    report['base'] = base
    report['status'] = 'SUCCEEDED'
    print('PASS: CPU/memory-bounded stack. Report: ' + str(output / 'report.json'), flush=True)
except BaseException as error:
    report.update(status='FAILED', failure=type(error).__name__)
    raise
finally:
    cleanup_errors = []
    for container in containers:
        try:
            with (output / (container[:12] + '.log')).open('w') as log:
                subprocess.run(['docker', 'logs', '--tail', '100', container], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=15)
        except (OSError, subprocess.TimeoutExpired):
            pass
        try:
            subprocess.run(['docker', 'rm', '-f', '-v', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30, check=True)
        except (OSError, subprocess.SubprocessError):
            cleanup_errors.append(container)
    if network_created:
        try:
            subprocess.run(['docker', 'network', 'rm', network], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15, check=True)
        except (OSError, subprocess.SubprocessError):
            cleanup_errors.append(network)
    report['cleanup_pending'] = bool(cleanup_errors)
    report['cleanup_resources'] = cleanup_errors
    checkpoint()
