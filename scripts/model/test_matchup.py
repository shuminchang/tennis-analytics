import csv,importlib.util,tempfile,unittest
from pathlib import Path
import numpy as np
spec=importlib.util.spec_from_file_location('trainer',Path(__file__).with_name('train_matchup.py'));trainer=importlib.util.module_from_spec(spec);spec.loader.exec_module(trainer)
HEADERS=['id','tour','tourney_date','tourney_id','round','match_num','winner_id','loser_id','surface','best_of','winner_age','loser_age','winner_height_cm','loser_height_cm','winner_age_blocked','loser_age_blocked','winner_height_blocked','loser_height_blocked']
class TrainingContract(unittest.TestCase):
 def dataset(self,path,flip=False):
  with path.open('w',newline='') as f:
   writer=csv.DictWriter(f,fieldnames=HEADERS);writer.writeheader()
   for i in range(10):writer.writerow(dict(zip(HEADERS,[i+1,'ATP','2020-01-01','event','R32',i+1,1,2,'Hard',3,20,30,180,170,'f','f','f','f'])))
   writer.writerow(dict(zip(HEADERS,[11,'ATP','2020-01-10','event','R16',11,1,2,'Hard',3,40,40,250,100,'t','t','t','t'])))
   writer.writerow(dict(zip(HEADERS,[12,'ATP','2020-02-01','next','F',1,2 if flip else 1,1 if flip else 2,'Hard',3,50,18,250,100,'f','f','f','f'])))
 def test_embargo_and_current_match_measurements_do_not_leak(self):
  with tempfile.TemporaryDirectory() as d:
   a,b=Path(d)/'a.csv',Path(d)/'b.csv';self.dataset(a);self.dataset(b,True)
   first=trainer.build_examples(a)[0]['ATP'];second=trainer.build_examples(b)[0]['ATP']
   self.assertEqual(len(first),1);np.testing.assert_allclose(first[0,4:],second[0,4:]);self.assertEqual(first[0,2],1);self.assertEqual(second[0,2],0)
   self.assertAlmostEqual(first[0,8],1);self.assertAlmostEqual(first[0,6],-1)
 def test_logit_swap_symmetry_and_probability_metrics(self):
  x=np.array([[1,.5,.1,-.1,1,0],[-1,-.5,-.1,.1,-1,0]])
  p=trainer.probability(x,trainer.BASE);self.assertAlmostEqual(p[0]+p[1],1);self.assertAlmostEqual(p[0],10/11)
  m=trainer.metrics(np.array([1.,0.]),np.array([.5,.5]));self.assertAlmostEqual(m['brier'],.25);self.assertAlmostEqual(m['logLoss'],np.log(2))
 def test_fitting_uses_an_antisymmetric_no_intercept_model(self):
  x=np.array([[1,0,0,0,0,0],[-1,0,0,0,0,0],[.5,0,0,0,0,0],[-.5,0,0,0,0,0]])
  beta=trainer.fit(x,np.array([1.,0.,1.,0.]),.01);p=trainer.probability(x,beta)
  self.assertTrue(np.all(np.isfinite(beta)));self.assertAlmostEqual(p[0]+p[1],1)
if __name__=='__main__':unittest.main()
