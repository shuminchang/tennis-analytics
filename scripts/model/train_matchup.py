#!/usr/bin/env python3
"""Fit small antisymmetric matchup models, using read-only export and forward time splits.
Requires NumPy (offline only); production Java does not require Python/NumPy.
"""
import argparse, csv, hashlib, itertools, json, math, os
from collections import deque
os.environ.setdefault('OPENBLAS_NUM_THREADS','1')
os.environ.setdefault('OMP_NUM_THREADS','1')
from datetime import date
from pathlib import Path
import numpy as np

FEATURES=['overall_elo_delta_400','surface_elo_delta_400','age_delta_10','age_squared_delta_100','height_delta_10','best_of_five_elo_delta_400']
BASE=np.array([math.log(10),0,0,0,0,0],dtype=float)
SURFACES={'Hard','Clay','Grass','Carpet'}

def probability(x, beta):
    return 1/(1+np.exp(-np.clip(x@beta,-35,35)))

def fit(x,y,ridge):
    beta=BASE.copy()
    def loss(b):
        z=x@b
        return float(np.mean(np.logaddexp(0,z)-y*z)+ridge*np.dot(b,b)/2)
    for _ in range(40):
        p=probability(x,beta);g=x.T@(p-y)/len(y)+ridge*beta
        h=(x.T*(p*(1-p)))@x/len(y)+ridge*np.eye(6)
        step=np.linalg.solve(h,g);scale=1
        while scale>1e-6 and loss(beta-scale*step)>loss(beta):scale/=2
        beta-=scale*step
        if np.max(np.abs(scale*step))<1e-8:break
    return beta

def metrics(y,p):
    if not len(y):return {'n':0,'brier':None,'logLoss':None,'accuracy':None,'calibration':[]}
    p=np.clip(p,1e-12,1-1e-12)
    bins=[]
    for i in range(10):
        mask=(p>=i/10)&(p<((i+1)/10) if i<9 else p<=1)
        if mask.any():bins.append({'lower':i/10,'upper':(i+1)/10,'n':int(mask.sum()),'predicted':float(p[mask].mean()),'observed':float(y[mask].mean())})
    return {'n':len(y),'brier':float(np.mean((p-y)**2)),'logLoss':float(-np.mean(y*np.log(p)+(1-y)*np.log1p(-p))),'accuracy':float(np.mean((p>=.5)==(y==1))),'calibration':bins}

def improves(y,candidate,reference):
    a,b=metrics(y,candidate),metrics(y,reference)
    return len(y)>0 and a['logLoss']<b['logLoss']-1e-4 and a['brier']<=b['brier']+1e-4

def build_examples(path):
    states,physical,examples={}, {}, {'ATP':[],'WTA':[]}
    totals={'ATP':0,'WTA':0};latest=None;pending=deque();previous_tour=None
    def mature(tour,ordinal,rows):
        for r in rows:
            w,l=int(r['winner_id']),int(r['loser_id']);surface=r['surface']
            for dim in ['OVERALL']+([surface] if surface in SURFACES else []):
                kw,kl=(tour,w,dim),(tour,l,dim);wr,wn,_=states.get(kw,(1500.,0,None));lr,ln,_=states.get(kl,(1500.,0,None));p=1/(1+10**((lr-wr)/400))
                states[kw]=(wr+250/(wn+5)**.4*(1-p),wn+1,ordinal);states[kl]=(lr+250/(ln+5)**.4*(p-1),ln+1,ordinal)
            for role,pid in [('winner',w),('loser',l)]:
                for field,column in [('age',role+'_age'),('height',role+'_height_cm')]:
                    raw=r[column];blocked=r[role+'_'+field+'_blocked'].lower() in ('t','true','1')
                    if raw and not blocked:
                        value=float(raw)
                        if math.isfinite(value) and (12<=value<=60 if field=='age' else 100<=value<=250):physical[(tour,pid,field)]=(value,ordinal)
    with path.open(newline='') as stream:
        reader=csv.DictReader(stream)
        for (tour,ds),group in itertools.groupby(reader,key=lambda r:(r['tour'],r['tourney_date'])):
            day=date.fromisoformat(ds);ordinal=day.toordinal();latest=max(latest or day,day)
            if previous_tour!=tour:
                while pending:mature(*pending.popleft())
            previous_tour=tour
            while pending and pending[0][1]<ordinal-21:mature(*pending.popleft())
            def observed(pid,field):
                item=physical.get((tour,pid,field))
                if item is None or ordinal-item[1]>365:return None
                value=item[0]+((ordinal-item[1])/365.2425 if field=='age' else 0)
                return value if (12<=value<=60 if field=='age' else 100<=value<=250) else None
            batch=list(group)
            for r in batch:
                totals[tour]+=1;w,l=int(r['winner_id']),int(r['loser_id']);a,b=sorted((w,l));surface=r['surface'];best=int(r['best_of'] or 0)
                ra,na,da=states.get((tour,a,'OVERALL'),(1500.,0,None));rb,nb,db=states.get((tour,b,'OVERALL'),(1500.,0,None));sa,nsa,dsa=states.get((tour,a,surface),(1500.,0,None));sb,nsb,dsb=states.get((tour,b,surface),(1500.,0,None))
                if na>=10 and nb>=10 and ordinal-da<=365 and ordinal-db<=365 and best in (3,5):
                    aa,ab=observed(a,'age'),observed(b,'age');ha,hb=observed(a,'height'),observed(b,'height');complete=None not in (aa,ab,ha,hb)
                    delta=(ra-rb)/400;sd=(sa-sb)/400 if surface in SURFACES and min(nsa,nsb)>=5 and ordinal-dsa<=730 and ordinal-dsb<=730 else 0
                    x=[delta,sd,(aa-ab)/10 if aa is not None and ab is not None else math.nan,((aa-27)**2-(ab-27)**2)/100 if aa is not None and ab is not None else math.nan,(ha-hb)/10 if ha is not None and hb is not None else math.nan,delta if best==5 else 0]
                    examples[tour].append([day.year,ordinal,1 if a==w else 0,1 if complete else 0,*x])
            pending.append((tour,ordinal,batch))
    while pending:mature(*pending.popleft())
    return {tour:np.array(rows,dtype=float).reshape((-1,10)) for tour,rows in examples.items()},states,totals,latest

def train(csv_path,metadata_path,output,report=None):
    if output.exists():raise ValueError('Output already exists')
    meta=json.loads(metadata_path.read_text());data,states,totals,latest=build_examples(csv_path)
    replay=0.
    for r in meta.get('eloSamples',[]):
        actual=states[(r['tour'],r['player_id'],r['dimension'])]
        if actual[1]!=r['matches']:raise ValueError('Elo replay count mismatch')
        replay=max(replay,abs(actual[0]-r['rating']))
    if replay>1e-8:raise ValueError('Elo replay differs from existing elo-v1')
    models={}
    for tour,rows in data.items():
        folds=[]
        for year in range(2005,latest.year+1):
            train_mask=(rows[:,0]>=year-8)&(rows[:,1]<=date(year-3,12,10).toordinal());valid_mask=(rows[:,0]>=year-2)&(rows[:,1]<=date(year-1,12,10).toordinal());test_mask=rows[:,0]==year
            x=np.nan_to_num(rows[:,4:],nan=0);y=rows[:,2];complete=rows[:,3]==1;basic=x.copy();basic[:,2:5]=0
            base_beta=BASE.copy();base_name='STANDARD_ELO';base_ridge=None
            if train_mask.sum()>=1000 and valid_mask.sum()>=200:
                candidates=[(fit(basic[train_mask],y[train_mask],lam),lam) for lam in (.0001,.001,.01)]
                candidate,lam=min(candidates,key=lambda item:metrics(y[valid_mask],probability(basic[valid_mask],item[0]))['logLoss'])
                if improves(y[valid_mask],probability(basic[valid_mask],candidate),probability(basic[valid_mask],BASE)):
                    base_beta=candidate;base_name='CALIBRATED_ELO';base_ridge=lam
            aug_beta=None;aug_ridge=None;selected=base_name
            tc=train_mask&complete;vc=valid_mask&complete
            if tc.sum()>=1000 and vc.sum()>=200:
                candidates=[(fit(x[tc],y[tc],lam),lam) for lam in (.0001,.001,.01)]
                aug_beta,aug_ridge=min(candidates,key=lambda item:metrics(y[vc],probability(x[vc],item[0]))['logLoss'])
                candidate=probability(basic[valid_mask],base_beta);candidate[complete[valid_mask]]=probability(x[vc],aug_beta)
                if improves(y[vc],probability(x[vc],aug_beta),probability(basic[vc],base_beta)) and improves(y[valid_mask],candidate,probability(basic[valid_mask],base_beta)):selected='ELO_AGE_HEIGHT'
            def evaluate(mask):
                p=probability(basic[mask],base_beta);selected_p=p.copy()
                if selected=='ELO_AGE_HEIGHT':selected_p[complete[mask]]=probability(x[mask&complete],aug_beta)
                return {'baseline':metrics(y[mask],probability(basic[mask],BASE)),'elo':metrics(y[mask],p),'selected':metrics(y[mask],selected_p),'completeCases':int((mask&complete).sum()),'augmentedCompleteCases':metrics(y[mask&complete],probability(x[mask&complete],aug_beta)) if aug_beta is not None else None}
            fold={'year':year,'availableFrom':f'{year}-01-01','trainFrom':f'{year-8}-01-01','trainThrough':f'{year-3}-12-10','validationFrom':f'{year-2}-01-01','validationThrough':f'{year-1}-12-10','selected':selected,'fallback':base_name,'eloCoefficients':base_beta.tolist(),'augmentedCoefficients':aug_beta.tolist() if aug_beta is not None else None,'eloRidge':base_ridge,'augmentedRidge':aug_ridge,'trainingN':int(train_mask.sum()),'trainingCompleteCases':int(tc.sum()),'validation':evaluate(valid_mask),'heldOut':evaluate(test_mask)}
            folds.append(fold)
            print(tour,year,selected,'train',fold['trainingN'],'validation',fold['validation']['selected']['n'],'test',fold['heldOut']['selected']['n'],flush=True)
        models[tour]=folds
    with csv_path.open(newline='') as stream:
        year_counts={'ATP':{},'WTA':{}}
        for row in csv.DictReader(stream):
            years=year_counts[row['tour']];year=row['tourney_date'][:4];years[year]=years.get(year,0)+1
    artifact={'sourceYearMatches':year_counts,'trainerSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'numpyVersion':np.__version__,'schema':1,'version':'matchup-v1','featureVersion':'prior-date-embargo21-v1','embargoDays':21,'features':FEATURES,'source':meta['source'],'eloInputSha':meta['elo']['input_sha256'],'eloRulesSha':meta['elo']['rules_sha256'],'datasetSha256':hashlib.sha256(csv_path.read_bytes()).hexdigest(),'sourceLastDate':latest.isoformat(),'sourceMatches':totals,'eloReplayMaxError':replay,'orientation':'smaller source player identity first, no intercept; swapping negates every feature','minPriorMatches':10,'maxInactiveDays':365,'maxPhysicalObservationDays':365,'minSurfaceMatches':5,'maxSurfaceInactiveDays':730,'minimumTrainingRows':1000,'minimumValidationRows':200,'models':models}
    if report:
        summary=json.loads(json.dumps(artifact))
        for folds in summary['models'].values():
            for fold in folds:
                for section in ['validation','heldOut']:
                    for measure in fold[section].values():
                        if isinstance(measure,dict) and (section=='validation' or fold['year']<2024):measure.pop('calibration',None)
        report.parent.mkdir(parents=True,exist_ok=True);report.write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    for folds in artifact['models'].values():
        for fold in folds:
            fold.pop('heldOut')
            for measure in fold['validation'].values():
                if isinstance(measure,dict):measure.pop('calibration',None)
    output.parent.mkdir(parents=True,exist_ok=True);tmp=output.with_suffix(output.suffix+'.tmp');tmp.write_text(json.dumps(artifact,ensure_ascii=False,indent=2,allow_nan=False)+'\n');tmp.replace(output)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('csv',type=Path);p.add_argument('metadata',type=Path);p.add_argument('output',type=Path);p.add_argument('--report',type=Path);a=p.parse_args();train(a.csv,a.metadata,a.output,a.report)
