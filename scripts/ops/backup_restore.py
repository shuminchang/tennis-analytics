#!/usr/bin/env python3
"""Local Compose PG backup with exported snapshot, checksum and optional isolated restore.
Never prints Compose environment or changes the source database. No external transfer.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import select
import time
import subprocess
from datetime import datetime, timezone
import uuid

ROOT = Path(__file__).resolve().parents[2]
TABLES = ['flyway_schema_history', 'import_runs', 'source_snapshots', 'source_files', 'quality_issue_impacts', 'analytics_state', 'players', 'rankings', 'matches', 'player_sources', 'ranking_sources', 'match_sources', 'quality_issues', 'tournament_decisions', 'app_users', 'favorites', 'audit_log', 'elo_runs', 'elo_history', 'elo_current', 'analytics_matches', 'analytics_events']

def command(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, timeout=kwargs.pop('timeout', 1800), **kwargs)

def pg(user, database):
    return ['docker', 'compose', 'exec', '-T', 'db', 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database]

def summary_sql():
    fields = ','.join(f"'{name}',(SELECT count(*) FROM {name})" for name in TABLES)
    return f'SELECT json_build_object({fields});'

def snapshot_line(stream, buffered, seconds=60):
    deadline = time.monotonic() + seconds
    while b'\n' not in buffered:
        remaining = deadline - time.monotonic()
        if remaining <= 0 or not select.select([stream], [], [], remaining)[0]:
            raise TimeoutError('Snapshot response timed out')
        chunk = os.read(stream.fileno(), 65536)
        if not chunk:
            raise RuntimeError('Snapshot keeper closed before response')
        buffered.extend(chunk)
    line, _, rest = buffered.partition(b'\n')
    buffered[:] = rest
    return line.decode('utf-8')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', default='tennis_candidate')
    parser.add_argument('--restore-check', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', args.database):
        raise ValueError('Invalid database identifier')
    command(['timeout', '15s', 'docker', 'version'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    config = json.loads(command(['docker', 'compose', 'config', '--format', 'json'], stdout=subprocess.PIPE, text=True).stdout)
    user = config['services']['db']['environment']['POSTGRES_USER']
    output = ROOT / '.local/backups' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:6])
    output.mkdir(parents=True, mode=0o700)
    os.chmod(output.parent, 0o700)
    dump = output / 'database.dump'
    partial = output / 'database.dump.partial'
    manifest = {'database': args.database, 'created_at': datetime.now(timezone.utc).isoformat(), 'status': 'RUNNING', 'external_backup': False}
    def checkpoint(stage):
        manifest['stage'] = stage
        temporary = output / 'manifest.json.tmp'
        temporary.write_text(json.dumps(manifest, indent=2) + '\n')
        os.chmod(temporary, 0o600)
        temporary.replace(output / 'manifest.json')
    checkpoint('EXPORTING')
    keeper = None
    restore = None
    restore_created = False
    try:
        keeper = subprocess.Popen(pg(user, args.database), cwd=ROOT, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=(output / 'snapshot.log').open('w'), text=True)
        keeper.stdin.write('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;\nSELECT pg_export_snapshot();\n' + summary_sql() + '\n')
        keeper.stdin.flush()
        buffered = bytearray()
        snapshot = snapshot_line(keeper.stdout, buffered).strip()
        if not re.fullmatch(r'[0-9A-Fa-f-]+', snapshot):
            raise RuntimeError('Cannot export snapshot; inspect snapshot.log')
        counts = json.loads(snapshot_line(keeper.stdout, buffered))
        print('Backing up consistent snapshot...', flush=True)
        with partial.open('wb') as stream:
            os.chmod(partial, 0o600)
            command(['docker', 'compose', 'exec', '-T', 'db', 'pg_dump', '-U', user, '-d', args.database, '-Fc', '--no-owner', '--no-acl', '--snapshot=' + snapshot], stdout=stream, stderr=(output / 'dump.log').open('w'))
        keeper.stdin.write('COMMIT;\n\\q\n');keeper.stdin.flush();keeper.wait(timeout=30)
        if keeper.returncode:
            raise RuntimeError('Snapshot keeper failed')
        partial.rename(dump)
        digest = hashlib.sha256()
        with dump.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(chunk)
        manifest.update(sha256=digest.hexdigest(), bytes=dump.stat().st_size, counts=counts,
                        code_commit=command(['git', 'rev-parse', 'HEAD'], stdout=subprocess.PIPE, text=True).stdout.strip())
        if args.restore_check:
            restore = 'tennis_restore_' + uuid.uuid4().hex[:12]
            command(['docker', 'compose', 'exec', '-T', 'db', 'createdb', '-U', user, '-T', 'template0', restore])
            restore_created = True
            manifest['restore_database'] = restore
            checkpoint('RESTORING')
            print('Restoring to isolated database ' + restore + '...', flush=True)
            with dump.open('rb') as stream:
                command(['docker', 'compose', 'exec', '-T', 'db', 'pg_restore', '-U', user, '-d', restore, '--no-owner', '--no-acl', '--exit-on-error'], stdin=stream, stdout=subprocess.DEVNULL, stderr=(output / 'restore.log').open('w'))
            restored = json.loads(command(pg(user, restore) + ['-c', summary_sql()], stdout=subprocess.PIPE, text=True).stdout)
            if restored != counts:
                raise RuntimeError('Restored row counts do not match exported snapshot')
            checks = command(pg(user, restore) + ['-c', "SELECT count(*) FROM pg_constraint WHERE convalidated=false;"], stdout=subprocess.PIPE, text=True).stdout.strip()
            if checks != '0':
                raise RuntimeError('Unvalidated constraints in restored database')
            manifest.update(restore_verified=True, restored_counts=restored)
        manifest['status'] = 'SUCCEEDED'
        checkpoint('COMPLETE')
        print('PASS: backup checksum' + (' and real restore verified' if args.restore_check else '') + '. Manifest: ' + str(output / 'manifest.json'), flush=True)
    except BaseException as error:
        manifest.update(status='FAILED', failure=type(error).__name__)
        raise
    finally:
        if keeper and keeper.poll() is None:
            keeper.terminate()
            try:
                keeper.wait(timeout=15)
            except subprocess.TimeoutExpired:
                keeper.kill();keeper.wait(timeout=15)
        if restore_created:
            try:
                cleanup = subprocess.run(['docker', 'compose', 'exec', '-T', 'db', 'dropdb', '-U', user, '--if-exists', restore], cwd=ROOT, timeout=15, stdout=subprocess.DEVNULL)
                manifest['cleanup_pending'] = cleanup.returncode != 0
            except (OSError, subprocess.TimeoutExpired):
                manifest['cleanup_pending'] = True
        checkpoint(manifest['stage'])
        for path in output.iterdir():
            if path.is_file():
                os.chmod(path, 0o600)

if __name__ == '__main__':
    main()
