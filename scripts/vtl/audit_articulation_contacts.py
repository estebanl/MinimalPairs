"""Geometric screening only: no phonetic/clinical validity claim; no asset writes."""
from pathlib import Path
import ast,json,math,hashlib
import numpy as np
R=Path(__file__).resolve().parents[2]; B=R/'design/bilateral-articulation-candidate'
controls_source=(R/'scripts/vtl/generate_articulation_candidates.py').read_text();src=ast.parse(controls_source)
ns={'np':np,'math':math}
for node in src.body:
 if isinstance(node,ast.FunctionDef) and node.name=='deform' or isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='controls' for t in node.targets):
  exec(compile(ast.Module(body=[node],type_ignores=[]),'<controls>','exec'),ns)
topo=json.loads((B/'bilateral-topology.json').read_text())
def intercepts(v,t,x,y):
 a=v[np.array(t)];p=a[:,:,0:2];v0=p[:,1]-p[:,0];v1=p[:,2]-p[:,0];d=np.array([x,y])-p[:,0]
 det=v0[:,0]*v1[:,1]-v0[:,1]*v1[:,0];ok=abs(det)>1e-10
 u=np.divide(d[:,0]*v1[:,1]-d[:,1]*v1[:,0],det,out=np.zeros_like(det),where=ok)
 w=np.divide(v0[:,0]*d[:,1]-v0[:,1]*d[:,0],det,out=np.zeros_like(det),where=ok)
 ok &= (u>=-1e-6)&(w>=-1e-6)&(u+w<=1.000001)
 return (a[:,0,2]+u*(a[:,1,2]-a[:,0,2])+w*(a[:,2,2]-a[:,0,2]))[ok]
records=[]
for key,p in ns['controls'].items():
 objs={o['name']:(o,ns['deform'](o['vertices'],o.get('parent_surface') or o['name'],o.get('jaw'),p)) for o in topo['objects'] if not o.get('parent_surface')}
 samples=[]
 for x in np.arange(-3.8,2.51,.1):
  for y in [0,.4,-.4]:
   o,v=objs['Refined tongue with connected root'];ts=intercepts(v,o['triangles'],x,y)
   if not len(ts):continue
   top=float(max(ts)); roof=[]
   for name in ['Hard palate bone section','Upper lip and alveolar tissue','Velum and uvula']:
    o,v=objs[name];zs=intercepts(v,o['triangles'],x,y)
    zs=zs[zs>-.7] # roof surfaces, not lower lip exterior; absolute known sagittal region
    if len(zs):roof.append((float(min(zs)),name))
   if roof:
    z,n=min(roof);samples.append({'x':round(float(x),2),'y':y,'tongueTopZ':top,'roofZ':z,'gapCm':z-top,'roof':n})
 def region(lo,hi):
  a=[s for s in samples if lo<=s['x']<=hi]
  return min(a,key=lambda s:s['gapCm']) if a else None
 records.append({'key':key,'anteriorMinimum':region(1,2.5),'posteriorMinimum':region(-3.8,-2),'globalMinimum':min(samples,key=lambda s:s['gapCm']) if samples else None,'penetrationSamples':[s for s in samples if s['gapCm']<-.01],'nearContactSamples':sum(abs(s['gapCm'])<=.03 for s in samples),'sampleCount':len(samples)})
report={'controls_source_sha256':hashlib.sha256(controls_source.encode()).hexdigest(),'basis_sha256':hashlib.sha256((B/'bilateral-topology.json').read_bytes()).hexdigest(),'controls_used':ns['controls'],'method':'Vertical triangle intersections at x -3.8..2.5 cm, y 0,+/-.4 cm. Roof candidates hard palate, upper alveolar tissue, velum; lower roof filter z>-.7. Screening excludes display sections. Negative gaps imply overlap in sampled columns; positive gaps do not prove continuous airway. Not clinical validation.','records':records}
(B/'contact-audit.json').write_text(json.dumps(report,indent=2)+'\n')
for r in records:
 print(r['key'],'anterior',round(r['anteriorMinimum']['gapCm'],3) if r['anteriorMinimum'] else None,'posterior',round(r['posteriorMinimum']['gapCm'],3) if r['posteriorMinimum'] else None,'penetration samples',len(r['penetrationSamples']))
# Sagittal front ray envelope: detects open oral aperture, not watertight contact.
lips=[]
skin=next(o for o in topo['objects'] if o['name']=='Bilateral atlas skin')
for key in ['oral-neutral','bilabial-closure','labiodental-fricative','w-onset']:
 v=ns['deform'](skin['vertices'],skin['name'],skin.get('jaw'),ns['controls'][key]);v=v[:,[2,1,0]]
 rows=[]
 for z in np.arange(-3,-.5,.01):
  xs=intercepts(v,skin['triangles'],z,0)
  rows.append({'z':round(float(z),3),'frontX':float(max(xs)) if len(xs) else None})
 # A ray through mouth crosses posterior skin rather than anterior facial skin.
 openrows=[r for r in rows if r['frontX'] is None or r['frontX']<1]
 lips.append({'key':key,'openMidlineRayCount':len(openrows),'openMidlineZRange':([min(r['z'] for r in openrows),max(r['z'] for r in openrows)] if openrows else None),'sampleSpacingCm':.01})
report['lipMidlineRayScreen']=lips
(B/'contact-audit.json').write_text(json.dumps(report,indent=2)+'\n')
print('LIP APERTURE',lips)
# Separate interval-overlap screening for upper gingiva and source teeth.
# No arbitrary z cutoff: this catches anterior tissue excluded by roof screen.
collisions=[]
for key,p in ns['controls'].items():
 obs=[(o,ns['deform'](o['vertices'],o.get('parent_surface') or o['name'],o.get('jaw'),p)) for o in topo['objects'] if not o.get('parent_surface')]
 tongue=next((o,v) for o,v in obs if o['name']=='Refined tongue with connected root')
 targets=[(o,v) for o,v in obs if o['name']=='Upper gingival envelope' or 'tooth' in o['name'].lower()]
 hits=[]; gingiva_gaps=[]
 for x in np.arange(-3.8,3.01,.1):
  for y in [0,.4,-.4,.8,-.8]:
   tz=intercepts(tongue[1],tongue[0]['triangles'],x,y)
   if len(tz)<2:continue
   for o,v in targets:
    zs=intercepts(v,o['triangles'],x,y)
    if len(zs)<2:continue
    if o['name']=='Upper gingival envelope':gingiva_gaps.append({'x':round(float(x),2),'y':y,'gapCm':float(min(zs)-max(tz))})
    overlap=min(max(tz),max(zs))-max(min(tz),min(zs))
    if overlap>.01:hits.append({'object':o['name'],'x':round(float(x),2),'y':y,'overlapCm':float(overlap)})
 collisions.append({'key':key,'gingivaAnteriorMinimumGap':min((g for g in gingiva_gaps if g['x']>=1),key=lambda g:g['gapCm'],default=None),'gingivaPosteriorMinimumGap':min((g for g in gingiva_gaps if g['x']<=-1.5),key=lambda g:g['gapCm'],default=None),'gingivaClosestPositiveGap':min((g for g in gingiva_gaps if g['gapCm']>=0),key=lambda g:g['gapCm'],default=None),'overlapSampleCount':len(hits),'maxOverlap':max(hits,key=lambda h:h['overlapCm']) if hits else None,'samples':hits})
report['gingivaAndTeethIntervalScreen']=collisions
report['limitations']=['Roof candidates are reported separately from gingiva/teeth overlap; no assumption that bone is mucosal contact target.','Sparse ray sampling cannot prove manifold watertightness or continuous airway.','Multiple disconnected intervals can cause conservative false positives; inspect reported overlap regions visually.']
(B/'contact-audit.json').write_text(json.dumps(report,indent=2)+'\n')
for c in collisions:print('COLLISION',c['key'],c['overlapSampleCount'],c['maxOverlap'])
