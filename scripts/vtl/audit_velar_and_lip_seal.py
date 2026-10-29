"""Independent sampled geometric screen; no phonetic validation or asset mutation."""
from pathlib import Path
import ast,sys,math,json,hashlib
import numpy as np
R=Path(__file__).resolve().parents[2];B=R/'design/bilateral-articulation-candidate';sys.path.insert(0,str(R/'scripts/vtl'))
source=(R/'scripts/vtl/generate_articulation_candidates.py').read_text();ns={'np':np,'math':math}
for n in ast.parse(source).body:
 if isinstance(n,ast.FunctionDef) and n.name=='deform' or isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='controls' for t in n.targets):exec(compile(ast.Module(body=[n],type_ignores=[]),'controls','exec'),ns)
audit=ast.parse((R/'scripts/vtl/audit_articulation_contacts.py').read_text());n=next(n for n in audit.body if isinstance(n,ast.FunctionDef) and n.name=='intercepts');exec(compile(ast.Module(body=[n],type_ignores=[]),'ray','exec'),ns)
ray=ns['intercepts'];topo=json.loads((B/'bilateral-topology.json').read_text())
def shape(name,key):
 o=next(o for o in topo['objects'] if o['name']==name);return o,ns['deform'](o['vertices'],name,o.get('jaw'),ns['controls'][key])
velar=[]
for key in ['oral-neutral','velar-closure','goose','w-onset']:
 to,t=shape('Refined tongue with connected root',key);vo,v=shape('Velum and uvula',key);samples=[]
 for x in np.arange(-4.5,-2,.025):
  for y in [0,-.4,.4,-.8,.8,-1.2,1.2]:
   tz=ray(t,to['triangles'],x,y);vz=ray(v,vo['triangles'],x,y)
   if not len(tz) or not len(vz):continue
   samples.append({'x':round(float(x),4),'y':y,'gapCm':float(min(vz)-max(tz)),'tongueTopZ':float(max(tz)),'velumUndersideZ':float(min(vz))})
 velar.append({'key':key,'sampleCount':len(samples),'minimumGap':min(samples,key=lambda p:p['gapCm']) if samples else None,'perLateralSlice':[{'y':y,'minimumGap':min([s for s in samples if s['y']==y],key=lambda p:p['gapCm'],default=None)} for y in [0,-.4,.4,-.8,.8,-1.2,1.2]],'penetrationSamples':[s for s in samples if s['gapCm']<-.01],'nearContactSamples':[s for s in samples if abs(s['gapCm'])<=.005]})
lips=[]
for key in ['oral-neutral','bilabial-closure']:
 o,v=shape('Bilateral atlas skin',key);v=v[:,[2,1,0]]
 for y in [0,-.4,.4,-.8,.8,-1.2,1.2,-1.6,1.6]:
  opened=[]
  for z in np.arange(-3,-.5,.005):
   xs=ray(v,o['triangles'],z,y)
   if not len(xs) or max(xs)<1:opened.append(float(z))
  lips.append({'key':key,'y':y,'openRayCount':len(opened),'openZRangeCm':[min(opened),max(opened)] if opened else None,'openSampledHeightCm':len(opened)*.005})
report={'basisSHA256':hashlib.sha256((B/'bilateral-topology.json').read_bytes()).hexdigest(),'controlsSHA256':hashlib.sha256(source.encode()).hexdigest(),'controls':ns['controls'],'velumScreen':velar,'lipScreen':lips,'limitations':['Sparse triangle-ray sampling does not establish a continuous seal or airflow.','Velum gap uses actual deformed velum underside, not bone or gingiva.','Front skin ray closure may reflect overlapping lips and is not a tissue collision/manifold test.','Only listed lateral samples evaluated; no clinical validation.']}
(B/'velar-lip-seal-audit.json').write_text(json.dumps(report,indent=2)+'\n')
for r in velar:print(r['key'],'min',r['minimumGap'],'penetrations',len(r['penetrationSamples']),'near',len(r['nearContactSamples']))
for r in lips:print('LIP',r)
