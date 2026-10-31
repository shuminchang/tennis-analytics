#!/usr/bin/env python3
"""Validate acceptance evidence; functional completion and latency SLO are independent.
No DB writes. With --report, validates a real owned-stack report; otherwise tests the gate.
"""
import argparse
import copy
from datetime import date, timedelta
import json
import math
from pathlib import Path
import unittest


def check(report):
    if report['status']!='SUCCEEDED' or report.get('cleanup_pending') is not False:
        raise ValueError('Acceptance did not finish or owned resources need cleanup')
    if sum(report['limits_mib'].values())!=832 or not math.isclose(sum(report['cpu_quotas'].values()),1):
        raise ValueError('Unexpected resource budget')
    if report['browser']['status']!='PASSED' or report['browser']['errors']:
        raise ValueError('Browser/CSP acceptance incomplete')
    if len(report['cgroup'])!=3:
        raise ValueError('All three cgroups must be measured')
    for group in report['cgroup']:
        lines=group['events_and_cpu']
        if 'oom 0' not in lines or 'oom_kill 0' not in lines or group['memory_peak_bytes']<=0:
            raise ValueError('Missing cgroup evidence or OOM event')
    misses=[];matchups=0
    for path,row in report['performance'].items():
        for metric in ['cold_ms','warm_p50_ms','warm_p95_ms']:
            if not math.isfinite(row[metric]) or row[metric]<0:
                raise ValueError('Invalid timing')
        if row['samples']<20 or row['maximum_response_bytes']<=0:
            raise ValueError('Insufficient HTTP measurements')
        if not row['single_target_met'] or not row.get('concurrent_4',{}).get('target_met',True):
            misses.append(path)
        if '/matchup-prediction?' in path:
            matchups+=1;p=row['prediction_sample'];prediction=p['prediction']
            if p['available'] is not True or not math.isclose(prediction['firstProbability']+prediction['secondProbability'],1,abs_tol=1e-12):
                raise ValueError('Invalid matchup probabilities')
            if not all(0<=prediction[k]<=1 for k in ['firstProbability','secondProbability']):
                raise ValueError('Probabilities out of range')
            if date.fromisoformat(p['historyBefore'])!=date.fromisoformat(p['asOf'])-timedelta(days=21):
                raise ValueError('Incorrect date isolation')
            if row.get('concurrent_4',{}).get('requests',0)<16:
                raise ValueError('Matchup concurrency evidence missing')
    if matchups<3 or not report['performance']:
        raise ValueError('Historical ATP, current ATP and WTA samples required')
    if report['latency_target_met']!= (not misses) or sorted(report['latency_target_misses'])!=sorted(misses):
        raise ValueError('Latency summary hides or misclassifies target misses')
    return {'functional_resource_acceptance':'PASSED','latency_slo':'PASSED' if not misses else 'NOT_MET','misses':misses}


def fixture():
    row={'cold_ms':1500,'warm_p50_ms':100,'warm_p95_ms':200,'samples':20,'maximum_response_bytes':100,'single_target_met':True,'concurrent_4':{'requests':16,'target_met':True},'prediction_sample':{'available':True,'asOf':'2026-06-08','historyBefore':'2026-05-18','prediction':{'firstProbability':.6,'secondProbability':.4}}}
    return {'status':'SUCCEEDED','cleanup_pending':False,'limits_mib':{'postgres':320,'java':448,'apache':64},'cpu_quotas':{'postgres':.5,'java':.4,'apache':.1},'browser':{'status':'PASSED','errors':[]},'cgroup':[{'events_and_cpu':['oom 0','oom_kill 0'],'memory_peak_bytes':100} for _ in range(3)],'performance':{f'/matchup-prediction?fixture={i}':copy.deepcopy(row) for i in range(3)},'latency_target_met':True,'latency_target_misses':[]}

class AcceptanceGate(unittest.TestCase):
    def test_cold_timing_does_not_falsely_fail_warm_slo(self):
        self.assertEqual(check(fixture())['latency_slo'],'PASSED')
    def test_concurrent_miss_cannot_be_hidden_by_passing_single_requests(self):
        r=fixture();path=next(iter(r['performance']));r['performance'][path]['concurrent_4']['target_met']=False
        with self.assertRaises(ValueError):check(r)
        r.update(latency_target_met=False,latency_target_misses=[path]);self.assertEqual(check(r)['latency_slo'],'NOT_MET')
    def test_incomplete_cleanup_or_oom_is_not_a_pass(self):
        for field in ['cleanup','oom']:
            r=fixture()
            if field=='cleanup':r['cleanup_pending']=True
            else:r['cgroup'][0]['events_and_cpu']=['oom 1','oom_kill 0']
            with self.assertRaises(ValueError):check(r)
    def test_invalid_probabilities_or_history_are_rejected(self):
        for field in ['probability','date']:
            r=fixture();p=next(iter(r['performance'].values()))['prediction_sample']
            if field=='probability':p['prediction']['firstProbability']=.7
            else:p['historyBefore']='2026-06-08'
            with self.assertRaises(ValueError):check(r)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--report',type=Path);args=parser.parse_args()
    if args.report:print(json.dumps(check(json.loads(args.report.read_text())),indent=2))
    else:unittest.main(argv=['release_report_checks'])
