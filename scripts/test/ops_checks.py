#!/usr/bin/env python3
"""Failure-path checks for local backups; no Docker or real database required."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('backup_restore', Path(__file__).resolve().parents[1] / 'ops/backup_restore.py')
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)


class BackupChecks(unittest.TestCase):
    def test_snapshot_lines_preserve_buffered_second_response(self):
        read_fd, write_fd = os.pipe()
        with os.fdopen(read_fd, 'rb') as stream:
            os.write(write_fd, b'0001-0002-1\n{"players":1}\n')
            os.close(write_fd)
            buffered = bytearray()
            self.assertEqual('0001-0002-1', backup.snapshot_line(stream, buffered))
            self.assertEqual({'players': 1}, json.loads(backup.snapshot_line(stream, buffered)))

    def test_snapshot_timeout_is_bounded(self):
        read_fd, write_fd = os.pipe()
        try:
            with os.fdopen(read_fd, 'rb') as stream:
                with self.assertRaises(TimeoutError):
                    backup.snapshot_line(stream, bytearray(), seconds=.01)
        finally:
            os.close(write_fd)

    def test_dump_failure_keeps_failed_manifest_without_verified_backup(self):
        with tempfile.TemporaryDirectory() as temp:
            keeper = Mock()
            keeper.poll.return_value = None
            def command(args, **kwargs):
                if 'config' in args:
                    return subprocess.CompletedProcess(args, 0, json.dumps({'services': {'db': {'environment': {'POSTGRES_USER': 'test'}}}}))
                if 'pg_dump' in args:
                    raise subprocess.CalledProcessError(1, args)
                return subprocess.CompletedProcess(args, 0, '')
            with patch.object(backup, 'ROOT', Path(temp)), patch.object(backup, 'command', side_effect=command), patch.object(backup.subprocess, 'Popen', return_value=keeper), patch.object(backup, 'snapshot_line', side_effect=['0001-0002-1', '{"players":1}']), patch.object(sys, 'argv', ['backup_restore', '--restore-check']):
                with self.assertRaises(subprocess.CalledProcessError):
                    backup.main()
            manifest_path = next(Path(temp).glob('.local/backups/*/manifest.json'))
            manifest = json.loads(manifest_path.read_text())
            self.assertEqual('FAILED', manifest['status'])
            self.assertFalse(manifest.get('restore_verified', False))
            self.assertFalse((manifest_path.parent / 'database.dump').exists())
            self.assertEqual(0o600, manifest_path.stat().st_mode & 0o777)
            keeper.terminate.assert_called_once()

    def test_restore_cleanup_timeout_preserves_verified_manifest(self):
        with tempfile.TemporaryDirectory() as temp:
            keeper = Mock(returncode=0)
            keeper.poll.return_value = 0
            def command(args, **kwargs):
                value = ''
                if 'config' in args:
                    value = json.dumps({'services': {'db': {'environment': {'POSTGRES_USER': 'test'}}}})
                elif 'pg_dump' in args:
                    kwargs['stdout'].write(b'test archive')
                elif 'rev-parse' in args:
                    value = 'test-commit'
                elif 'psql' in args:
                    value = '0' if 'convalidated=false' in args[-1] else '{"players":1}'
                return subprocess.CompletedProcess(args, 0, value)
            with patch.object(backup, 'ROOT', Path(temp)), patch.object(backup, 'command', side_effect=command), patch.object(backup.subprocess, 'Popen', return_value=keeper), patch.object(backup, 'snapshot_line', side_effect=['0001-0002-1', '{"players":1}']), patch.object(backup.subprocess, 'run', side_effect=subprocess.TimeoutExpired('docker', 15)), patch.object(sys, 'argv', ['backup_restore', '--restore-check']):
                backup.main()
            manifest_path = next(Path(temp).glob('.local/backups/*/manifest.json'))
            manifest = json.loads(manifest_path.read_text())
            self.assertEqual('SUCCEEDED', manifest['status'])
            self.assertTrue(manifest['restore_verified'])
            self.assertTrue(manifest['cleanup_pending'])
            self.assertEqual(manifest['counts'], manifest['restored_counts'])


if __name__ == '__main__':
    unittest.main()
