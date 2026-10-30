#!/usr/bin/env python3
"""Phase 11.1 hand-calculated contract oracle; does not implement production APIs."""
import copy
from fractions import Fraction
import json
import math
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = json.loads((ROOT / 'docs/fixtures/visualization-v1-hand-example.json').read_text())


def perspectives(matches):
    return [dict(side, match=m['id'], won=role == 'winner', recordServeBlocked=m.get('serveBlocked', False))
            for m in matches if m['eligible'] for role in ('winner', 'loser') for side in [m[role]]]


def components(rows, numerator, denominator):
    valid = [r for r in rows if not r.get('serveBlocked', False) and not r['recordServeBlocked']
             and r.get(denominator) is not None and r[denominator] > 0
             and r.get(numerator) is not None and 0 <= r[numerator] <= r[denominator]
             and (denominator != 'firstIn' or r.get('svpt') is None or r['firstIn'] <= r['svpt'])]
    return len(valid), sum(r[numerator] for r in valid), sum(r[denominator] for r in valid)


def wilson(wins, n):
    if n == 0:
        return None, None
    z = 1.959963984540054
    p, d = wins / n, 1 + z * z / n
    center = (p + z * z / (2 * n)) / d
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return max(0, center - half), min(1, center + half)


class ContractChecks(unittest.TestCase):
    def setUp(self):
        self.rows = perspectives(FIXTURE['matches'])
        self.a = [r for r in self.rows if r['player'] == 1]

    def test_retirements_included_walkover_excluded_and_two_views_only(self):
        self.assertEqual(6, len(self.rows))
        self.assertEqual({1, 2, 4}, {r['match'] for r in self.rows})
        self.assertEqual(3, sum(r['won'] for r in self.rows))
        self.assertEqual(3, len(self.a))
        self.assertEqual(1, sum(r['won'] for r in self.a))

    def test_weighted_serve_rates_match_independent_hand_totals(self):
        self.assertEqual((3, 5, 60), components(self.a, 'ace', 'svpt'))
        self.assertEqual((3, 3, 60), components(self.a, 'df', 'svpt'))
        self.assertEqual((3, 23, 37), components(self.a, 'firstWon', 'firstIn'))
        self.assertNotEqual(Fraction(5, 60), (Fraction(2, 20) + Fraction(3, 30)) / 3)

    def test_zero_numerator_is_valid_zero_denominator_and_missing_are_not(self):
        zero = next(r for r in self.a if r['match'] == 4)
        self.assertEqual((1, 0, 10), components([zero], 'ace', 'svpt'))
        c = [r for r in self.rows if r['player'] == 3]
        self.assertEqual((0, 0, 0), components(c, 'ace', 'svpt'))
        self.assertEqual((0, 0, 0), components([dict(zero, ace=None)], 'ace', 'svpt'))

    def test_quality_is_role_specific_and_record_flag_affects_both(self):
        changed = copy.deepcopy(FIXTURE['matches'])
        changed[0]['winner']['serveBlocked'] = True
        rows = perspectives(changed)
        self.assertEqual((2, 3, 40), components([r for r in rows if r['player'] == 1], 'ace', 'svpt'))
        self.assertEqual((2, 1, 20), components([r for r in rows if r['player'] == 2], 'ace', 'svpt'))
        changed[0]['serveBlocked'] = True
        rows = perspectives(changed)
        self.assertEqual((1, 0, 10), components([r for r in rows if r['player'] == 2], 'ace', 'svpt'))

    def test_age_flag_does_not_remove_win_or_serve_observation(self):
        valid = [r for r in self.rows if r.get('age') is not None and r['age'] > 0 and not r.get('ageBlocked', False)]
        self.assertEqual(5, len(valid))
        twenties = [r for r in valid if math.floor(r['age']) == 20]
        self.assertEqual((1, 2), (sum(r['won'] for r in twenties), len(twenties)))
        self.assertEqual((3, 5, 60), components(self.a, 'ace', 'svpt'))

    def test_height_quality_precedes_missing_and_range_preserves_population(self):
        reason = lambda p: ('quality' if p.get('heightBlocked') else 'missing' if p['height'] is None else 'range' if not 100 <= p['height'] <= 250 else 'valid')
        reasons = [reason(p) for p in FIXTURE['players']]
        self.assertEqual(['valid', 'missing', 'quality', 'quality'], reasons)
        self.assertEqual('quality', reason({'height': None, 'heightBlocked': True}))
        for height in (100, 250):
            self.assertEqual('valid', reason({'height': height}))

    def test_exact_ranking_date_and_zero_points_remain_distinct(self):
        self.assertEqual([], [r for r in FIXTURE['rankings'] if r['date'] == '2024-01-02'])
        rows = [r for r in FIXTURE['rankings'] if r['date'] == '2024-01-01']
        self.assertEqual(4, len(rows))
        self.assertEqual(0, rows[0]['points'])
        self.assertIsNone(rows[1]['points'])

    def test_wilson_hand_interval_and_empty_sample(self):
        for actual, expected in zip(wilson(1, 3), FIXTURE['expected']['wilsonOneOfThree']):
            self.assertAlmostEqual(expected, actual, places=12)
        self.assertEqual((None, None), wilson(0, 0))


if __name__ == '__main__':
    unittest.main()
