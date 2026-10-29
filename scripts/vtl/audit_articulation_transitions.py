"""Fixed-topology sampled numerical transition audit. Not contact/airflow proof."""
from pathlib import Path
import json,struct,hashlib
import numpy as np
ROOT=Path(__file__).resolve().parents[2];D=ROOT/'design/bilateral-articulation-candidate';P=D/'poses'
def load(path):
 b=path.read_bytes();off=12;groups=[]
 for _ in range(struct.unpack_from('<I',b,8)[0]):
  mat,nv,nt=struct.unpack_from('<III',b,off);off+=12
  v=np.frombuffer(b,dtype='<f4',count=nv*3,offset=off).reshape(-1,3).copy();off+=nv*12
  records=np.dtype([('i','<u4',(3,)),('n','<f4',(9,))]);rows=np.frombuffer(b,dtype=records,count=nt,offset=off);off+=nt*48
  groups.append((mat,v,rows['i'].copy(),rows['n'].copy()))
 return groups
m=json.loads((P/'manifest.json').read_text());pairs=set()
for target in m['targets']:
 keys=[Path(x['assetPath']).stem for x in target['phases']]
 pairs.update(zip(keys,keys[1:]))
reports=[]
for start,end in sorted(pairs):
 a=load(P/(start+'.vtlm'));b=load(P/(end+'.vtlm'));bad=[];minimum=1e9
 assert len(a)==len(b)
 for sample in range(51):
  f=np.float32(sample/50)
  for (ma,va,ia,na),(mb,vb,ib,nb) in zip(a,b):
   assert ma==mb and np.array_equal(ia,ib) and va.shape==vb.shape
   v=va+(vb-va)*f
   r=np.stack((v[:,2]*np.float32(.23),(v[:,1]+np.float32(3.7))*np.float32(.23),(v[:,0]-np.float32(2))*np.float32(.23)),axis=1)
   c=np.cross(r[ia[:,1]]-r[ia[:,0]],r[ia[:,2]]-r[ia[:,0]]);areas=(c*c).sum(axis=1);minimum=min(minimum,float(areas.min()))
   n=na+(nb-na)*f;norms=(n.reshape(-1,3,3)**2).sum(axis=2)
   invalid=np.flatnonzero((areas<=1e-12)|(~np.isfinite(areas))|(norms.min(axis=1)<.01))
   if len(invalid):bad.append({'fraction':float(f),'material':ma,'triangles':invalid.tolist()})
 reports.append({'sourceSHA256':hashlib.sha256((P/(start+'.vtlm')).read_bytes()).hexdigest(),'destinationSHA256':hashlib.sha256((P/(end+'.vtlm')).read_bytes()).hexdigest(),'from':start,'to':end,'sampleCount':51,'minimumCrossAreaSquared':minimum,'invalidSamples':bad,'passesSampledNumericGuard':not bad,'contactAndAirwayValidated':False})
(D/'transition-numeric-audit.json').write_text(json.dumps(reports,indent=2)+'\n')
print([(x['from'],x['to'],len(x['invalidSamples'])) for x in reports])
raise SystemExit(0 if all(x['passesSampledNumericGuard'] for x in reports) else 1)
