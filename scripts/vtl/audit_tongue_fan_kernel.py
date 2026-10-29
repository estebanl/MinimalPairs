"""Diagnostic fan-kernel feasibility for current endpoint controls; no asset writes."""
from pathlib import Path
import os
os.chdir(Path(__file__).resolve().parents[2])
import json,collections,numpy as np,ast,math,sys,hashlib
sys.path.insert(0,'scripts/vtl');ns={'np':np,'math':math}
for n in ast.parse(open('scripts/vtl/generate_articulation_candidates.py').read()).body:
 if isinstance(n,ast.FunctionDef) and n.name=='deform' or isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='controls' for t in n.targets):exec(compile(ast.Module(body=[n],type_ignores=[]),'x','exec'),ns)
j=json.load(open('design/bilateral-articulation-candidate/bilateral-topology.json'));o=next(o for o in j['objects'] if o['name']=='Refined tongue with connected root display section');c=collections.Counter(tuple(sorted((a,b))) for t in o['triangles'] for a,b in zip(t,t[1:]+t[:1]));adj=collections.defaultdict(list)
for (a,b),n in c.items():
 if n==1:adj[a].append(b);adj[b].append(a)
start=min(adj);loop=[start];prev=None;cur=start
while True:
 nxt=next(v for v in adj[cur] if v!=prev)
 if nxt==start:break
 loop.append(nxt);prev,cur=cur,nxt

def cross(a,b):return a[0]*b[1]-a[1]*b[0]
def kernel(points):
 if sum(cross(a,b) for a,b in zip(points,np.roll(points,-1,axis=0)))<0:points=points[::-1]
 poly=[np.array(p,float) for p in [(-20,-20),(20,-20),(20,20),(-20,20)]]
 for a,b in zip(points,np.roll(points,-1,axis=0)):
  out=[]
  for p,q in zip(poly,poly[1:]+poly[:1]):
   dp=cross(b-a,p-a);dq=cross(b-a,q-a)
   if dp>=-1e-10:out.append(p)
   if (dp>=0)!=(dq>=0):out.append(p+(q-p)*dp/(dp-dq))
  poly=out
  if not poly:break
 return poly
endpoint_records=[];transition_records=[]
for k,p in ns['controls'].items():
 v=ns['deform'](o['vertices'],o['parent_surface'],o.get('jaw'),p)[loop][:,[0,2]];ker=kernel(v);print(k,'kernel_vertices',len(ker),'anchor',np.mean(ker,axis=0).tolist() if len(ker) else None);endpoint_records.append({'key':k,'kernelVertexCount':len(ker),'anchor':np.mean(ker,axis=0).tolist() if len(ker) else None})
for ka,kb in [('bilabial-closure','oral-neutral'),('alveolar-closure','oral-neutral'),('velar-closure','oral-neutral'),('postalveolar-closure','postalveolar-fricative'),('w-onset','oral-neutral')]:
 vs=[ns['deform'](o['vertices'],o['parent_surface'],o.get('jaw'),ns['controls'][k])[loop][:,[0,2]] for k in [ka,kb]];anchors=[np.mean(kernel(v),axis=0) for v in vs];minimum=1e9;bad=0
 sign=np.sign(sum(cross(a,b) for a,b in zip(vs[0],np.roll(vs[0],-1,axis=0))))
 for f in np.linspace(0,1,101):
  v=vs[0]*(1-f)+vs[1]*f;anchor=anchors[0]*(1-f)+anchors[1]*f
  vals=[sign*cross(b-a,anchor-a) for a,b in zip(v,np.roll(v,-1,axis=0))];minimum=min(minimum,min(vals));bad+=sum(x<=0 for x in vals)
 print('FAN_TRANSITION',ka,kb,'min_signed_area2',minimum,'bad',bad)
 transition_records.append({'from':ka,'to':kb,'samples':101,'minimumSignedDoubleAreaCm2':minimum,'invalidFacesAcrossSamples':int(bad)})

report={'boundaryCount':len(loop),'basisSHA256':j['vtlm_sha256'],'controlsSourceSHA256':hashlib.sha256(open('scripts/vtl/generate_articulation_candidates.py','rb').read()).hexdigest(),'endpoints':endpoint_records,'transitions':transition_records,'anatomicallyValidated':False}
open('design/bilateral-articulation-candidate/fan-kernel-audit.json','w').write(json.dumps(report,indent=2)+'\n')
assert all(r['kernelVertexCount']>0 for r in endpoint_records) and all(r['invalidFacesAcrossSamples']==0 for r in transition_records)
