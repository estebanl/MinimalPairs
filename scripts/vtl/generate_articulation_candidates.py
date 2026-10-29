"""Generate UNREVIEWED illustrative targets on one fixed bilateral topology.
These hand-authored controls are not a biomechanical speech solver or validated
English targets. Files remain outside app resources until every release gate passes.
"""
from pathlib import Path
import math,json,struct,hashlib,copy
import numpy as np
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'design/bilateral-articulation-candidate';OUT=BASE/'poses';OUT.mkdir(exist_ok=True)
topo=json.loads((BASE/'bilateral-topology.json').read_text());S=topo['scale'];C=np.array(topo['center'])
# Angle degrees: positive opens the jaw by clockwise rotation in sagittal view.
controls={
 'oral-neutral':{},
 'fleece':dict(jaw=-3,body=1.0,body_x=.4,body_width=1.4),
 'kit':dict(jaw=-1.5,body=.65,body_x=.3,body_width=1.5),
 'dress':dict(jaw=1,body=.35,body_x=.4,body_width=1.6),
 'trap':dict(jaw=5,body=-.35,body_x=.3,body_width=2),
 'palm':dict(jaw=7,body=-.65,body_x=-1.5,body_width=2,back=.3),
 'strut':dict(jaw=3,body=-.3,body_x=-.7,body_width=2),
 'foot':dict(jaw=-1,body=.85,body_x=-2.4,body_width=1.6,round=.3),
 'goose':dict(jaw=-2,body=1.25,body_x=-2.6,body_width=1.4,round=.55),
 'lot':dict(jaw=6,body=-.45,body_x=-1.8,body_width=2,round=.35),
 'thought':dict(jaw=2,body=.2,body_x=-2.0,body_width=1.7,round=.4),
 'bilabial-closure':dict(close=1.0,jaw=-1),
 'alveolar-closure':dict(tip=.4,tip_retract=0,jaw=-1,contact='alveolar'),
 'velar-closure':dict(body=2.1,body_x=-3.4,body_width=1.1,jaw=-1,contact='velar'),
 'labiodental-fricative':dict(labiodental=1,jaw=-.5),
 'dental-fricative':dict(protrude=.65,thin=.9,jaw=-.5),
 'alveolar-fricative':dict(tip=2.25,tip_retract=.35,jaw=-1,groove=.12),
 'postalveolar-fricative':dict(tip=1.5,tip_retract=.35,body=.8,body_x=.3,body_width=1.4,round=.15),
 'postalveolar-closure':dict(tip=1.2,tip_retract=.3,body=1.5,body_x=.75,body_width=1.0,contact='postalveolar'),
 'alveolar-lateral':dict(tip=2.6,tip_retract=.35,lateral=.5,jaw=-1),
 'rhotic-bunched':dict(body=.85,body_x=-1.2,body_width=1.0,back=.3,round=.12),
 'rhotic-tip-up':dict(tip=.9,tip_retract=.4,back=.3,round=.12),
 'w-onset':dict(body=1.1,body_x=-2.6,body_width=1.4,round=.6,jaw=-2),
}

def deform(points,name,jaw,p):
 a=np.array(points,dtype=float);x,y,z=a.T.copy();low=np.clip((-z-1.3)/2,0,1);anterior=np.clip((x+3)/4,0,1)
 is_tongue='tongue' in name.lower();is_skin='atlas skin' in name.lower()
 is_lower=jaw=='lower' or 'mandibular' in name.lower() or 'floor of mouth' in name.lower()
 w=np.ones(len(a)) if is_lower else low*anterior if is_skin else np.ones(len(a))*.35 if is_tongue else np.zeros(len(a))
 angle=-math.radians(p.get('jaw',0))*w;dx=x+4.8;dz=z+.4
 a[:,0]=-4.8+np.cos(angle)*dx-np.sin(angle)*dz;a[:,2]=-.4+np.sin(angle)*dx+np.cos(angle)*dz
 if is_tongue:
  top=np.clip((z+5.3)/4,0,1);tipw=np.exp(-((x-2.05)/.70)**2);bodyw=np.exp(-((x-p.get('body_x',-.5))/p.get('body_width',1.6))**2)
  lift=p.get('tip',0)*tipw+p.get('body',0)*bodyw
  roof=np.interp(x,[-5,-3.5,-1.5,.6,1.5,2.0,3.5],[-1.0,-.55,-.60,-.65,-.72,-.80,-1.05])
  # Positive vertical Jacobian: scale each anatomical column between fixed
  # inferior and superior reference profiles. Unlike nonlinear per-vertex
  # saturation, this cannot turn an interior vertical column inside out.
  upper=np.interp(x,[-4.1,-3.65,-2.75,-1.55,-.15,1.2,2.3],[-2.85,-1.9,-1.2,-.95,-.95,-1.3,-1.85])
  lower=np.interp(x,[-4.1,-3.85,-3.3,-2.8,-1.65,-.4,1.2,1.55,1.8,2.3],[-2.85,-3.8,-4.45,-5.8,-5.6,-5.15,-4.65,-3.3,-2.5,-2.2])
  from articulation_geometry import tongue_column,mucosal_roof
  clearance=.12 if p.get('contact')=='velar' else .015
  shifted_x=a[:,0]+(p.get('protrude',0)-p.get('tip_retract',0))*tipw-p.get('back',0)*top
  for i,(px,py,dx) in enumerate(zip(x,y,shifted_x)):
   lo,hi=tongue_column(float(px),float(py))
   if lo is not None:
    blend=float(np.clip((2.1-px)/.4,0,1));lower[i]=blend*lo+(1-blend)*lower[i];upper[i]=blend*hi+(1-blend)*upper[i]
   actual=mucosal_roof(float(dx),float(py))
   if actual is not None:roof[i]=actual-clearance
  span=np.maximum(upper-lower,.025);jaw_delta=a[:,2]-z
  target=np.minimum(upper+lift,roof-jaw_delta)
  # Explicit finite contact band, using the actual authored mucosal underside.
  # This is a geometry constraint only; anatomy/phonetic review remains required.
  bands={'alveolar':(1.15,1.75),'postalveolar':(.25,1.05),'velar':(-3.75,-3.45)}
  if p.get('contact') in bands:
   lo,hi=bands[p['contact']];distance=np.maximum(lo-shifted_x,0)+np.maximum(shifted_x-hi,0)
   weight=np.clip(1-distance/(.20 if p.get('contact')=='velar' else .35),0,1)
   target=target*(1-weight)+(roof+clearance-jaw_delta)*weight
  ratio=np.maximum((target-lower)/span,.12)
  a[:,2]=lower+(z-lower)*ratio+jaw_delta
  a[:,0]+=(p.get('protrude',0)-p.get('tip_retract',0))*tipw-p.get('back',0)*top
  thin=p.get('thin',0)*tipw;a[:,2]=(1-thin)*a[:,2]+thin*(-1.95)
  a[:,2]-=p.get('lateral',0)*np.clip(abs(y)/1.4,0,1)*top
  a[:,2]-=p.get('groove',0)*np.exp(-(y/.35)**2)*top
  # Conservative roof envelope prevents obvious penetration; proximity is not
  # specialist-validated closure. Report contact metrics rather than assert speech.
  # Positive lift approaches roof monotonically without flattening triangles.
 if 'velum' in name.lower() or 'uvula' in name.lower():
  w=np.clip((-x-3.5)/1.1,0,1);a[:,0]-=.5*w;a[:,2]+=.8*w
 lipregion=np.exp(-((z+1.65)/1.05)**4)*np.clip((x-2.2)/1.3,0,1)
 lipregion*=np.clip((-.35-z)/.35,0,1)  # explicit upper-face exclusion
 if is_skin or 'lip' in name.lower():
  # Move lips toward contact without collapsing their tissue thickness.
  side=np.where(z>-1.65,1,-1)
  if is_skin and p.get('close',0):
   radial=np.sqrt((y/2.4)**2+((z+1.65)/.70)**2)
   rim=np.exp(-(np.maximum(abs(radial-1)-.05,0)/.42)**2)*np.clip((x-1.5)/.7,0,1)
   # The actual authored elliptical aperture boundary closes to a common seam;
   # neighboring tissue follows smoothly, rather than broad upper-face warping.
   a[:,2]-=(a[:,2]+1.65)*rim*p['close']
  else:a[:,2]-=side*.88*p.get('close',0)*lipregion*np.sqrt(np.clip(1-(y/2.4)**2,0,1))
  rw=p.get('round',0)*lipregion;a[:,1]*=(1-.45*rw);a[:,0]+=.55*rw
  if p.get('labiodental',0):
   w=lipregion*np.clip((-z-1.6)/.8,0,1);a[:,2]+=.8*w;a[:,0]-=.65*w
 return a

# Conforming cap interior motion: move shared boundary with the anatomy, solve
# interior harmonic displacement so independently warped grid points cannot cross
# a nearby narrow boundary ear. This is not a change to per-pose connectivity.
_cap_weights={}
def constrain_section_interior(ob,positions):
 if ob.get('fan_boundary_count') is not None:
  from articulation_fan import place_fan_anchor
  return place_fan_anchor(positions,int(ob['fan_boundary_count']))
 if not ob.get('parent_surface'):return positions
 key=ob['name']
 if key not in _cap_weights:
  from collections import Counter
  tris=ob['triangles'];edges=Counter(tuple(sorted((t[k],t[(k+1)%3]))) for t in tris for k in range(3))
  boundary=sorted({v for e,c in edges.items() if c==1 for v in e});inner=sorted({v for t in tris for v in t}-set(boundary))
  if not inner or not boundary:_cap_weights[key]=None
  else:
   ii={v:i for i,v in enumerate(inner)};bb={v:i for i,v in enumerate(boundary)};L=np.zeros((len(inner),len(inner)));R=np.zeros((len(inner),len(boundary)));adj={v:set() for v in range(len(ob['vertices']))}
   for a,b in edges:adj[a].add(b);adj[b].add(a)
   for v,row in ii.items():
    L[row,row]=len(adj[v])
    for u in adj[v]:
     if u in ii:L[row,ii[u]]-=1
     else:R[row,bb[u]]+=1
   _cap_weights[key]=(inner,boundary,np.linalg.solve(L,R))
 spec=_cap_weights[key]
 if spec:
  inner,boundary,W=spec;positions[inner]=W@positions[boundary]
 return positions

# Index topology hash is independent of positions/normals and fixed across poses.
def topology_hash():
 b=bytearray()
 for k,g in topo['groups'].items():
  b+=struct.pack('<III',int(k),len(g['vertices']),len(g['triangles']))
  for t in g['triangles']:b+=struct.pack('<3I',*t)
 return hashlib.sha256(b).hexdigest()
th=topology_hash();records=[]
for key,p in controls.items():
 groups={k:{'vertices':np.array(g['vertices'],dtype=float),'normals':[]} for k,g in topo['groups'].items()};object_positions={};object_normals={}
 for ob in topo['objects']:
  if not ob['vertices'] or not ob['triangles']:continue
  name=ob.get('parent_surface') or ob['name'];a=constrain_section_interior(ob,deform(ob['vertices'],name,ob.get('jaw'),p));object_positions[ob['name']]=a
  ids=np.array(ob['triangles'],dtype=int);cross=np.cross(a[ids[:,1]]-a[ids[:,0]],a[ids[:,2]]-a[ids[:,0]])
  sizes=np.linalg.norm(cross,axis=1);fn=cross/np.maximum(sizes[:,None],1e-12)
  vn=np.zeros_like(a)
  for col in range(3):np.add.at(vn,ids[:,col],cross)
  vn/=np.maximum(np.linalg.norm(vn,axis=1)[:,None],1e-12)
  # Dedicated display sections retain planar normals. Smooth anatomical surfaces
  # use area-weighted normals on the fixed triangles.
  ns=np.repeat(fn[:,None,:],3,axis=1) if ob.get('parent_surface') else vn[ids]
  object_normals[ob['name']]=ns
  q=(a-C)*S;out=np.column_stack((q[:,0]+2,q[:,2]-3.7,q[:,1]))
  for k,mapping in ob['group_vertex_map'].items():
   for src,dst in mapping.items():groups[k]['vertices'][dst]=out[int(src)]
 raw=bytearray(b'VTLM'+struct.pack('<II',1,len(topo['groups'])))
 for k,g in topo['groups'].items():
  verts=groups[k]['vertices'];raw+=struct.pack('<III',int(k),len(verts),len(g['triangles']))
  raw+=np.asarray(verts,dtype='<f4').tobytes()
  for tri,source in zip(g['triangles'],g['triangle_sources']):
   ns=object_normals[source['object']][source['triangle']][:,[0,2,1]].reshape(-1)
   raw+=struct.pack('<3I9f',*tri,*ns)
 assert len(raw)<2_000_000
 (OUT/(key+'.vtlm')).write_bytes(raw)
 # Honest coarse diagnostics, not proof that an English phone is correct.
 tv=np.concatenate([v for n,v in object_positions.items() if 'tongue' in n.lower()])
 tip=tv[(abs(tv[:,1])<.25)&(tv[:,0]>1.6)]
 back=tv[(abs(tv[:,1])<.25)&(tv[:,0]<-2)&(tv[:,0]>-3.8)]
 records.append({'key':key,'path':key+'.vtlm','sha256':hashlib.sha256(raw).hexdigest(),'bytes':len(raw),'topologyHash':th,'controls':p,'finite':bool(all(np.isfinite(v).all() for v in object_positions.values())),'tipMaxZ_cm':float(tip[:,2].max()) if len(tip) else None,'backMaxZ_cm':float(back[:,2].max()) if len(back) else None,'reviewStatus':'UNREVIEWED'})
# Phonetic mapping retains voicing-pair sharing. Voicing is not visible geometry.
phones={'iː':['fleece'],'ɪ':['kit'],'e':['dress'],'æ':['trap'],'ɑː':['palm'],'ʌ':['strut'],'ʊ':['foot'],'uː':['goose'],'ɒ':['lot'],'ɔː':['thought'],
'p':['bilabial-closure','oral-neutral'],'b':['bilabial-closure','oral-neutral'],'t':['alveolar-closure','oral-neutral'],'d':['alveolar-closure','oral-neutral'],'k':['velar-closure','oral-neutral'],'ɡ':['velar-closure','oral-neutral'],'f':['labiodental-fricative'],'v':['labiodental-fricative'],'θ':['dental-fricative'],'ð':['dental-fricative'],'s':['alveolar-fricative'],'z':['alveolar-fricative'],'ʃ':['postalveolar-fricative'],'ʒ':['postalveolar-fricative'],'tʃ':['postalveolar-closure','postalveolar-fricative'],'dʒ':['postalveolar-closure','postalveolar-fricative'],'l':['alveolar-lateral'],'r':['rhotic-bunched'],'w':['w-onset','oral-neutral']}
section_indices=[i for i,t in enumerate(t for g in topo['groups'].values() for t in g['triangle_sources']) if t['display_section']]
manifest={'clipMode':'AUTHORED_SECTIONS','sectionTriangleIndices':section_indices,'modelFamily':'atlas-illustrative-v1','format':'VTLM v1; material IDs0-8','status':'UNREVIEWED_DO_NOT_ENABLE','topologyHash':th,'sourceBasisSHA256':hashlib.sha256((BASE/'bilateral-topology.json').read_bytes()).hexdigest(),'files':records,'targets':[{'phoneme':ph,'phases':[{'name':('closure' if 'closure' in k else 'friction' if 'fricative' in k else 'onset' if k=='w-onset' else 'release' if k=='oral-neutral' else 'target'),'assetPath':'files/anatomy/'+k+'.vtlm','transitionToNextMs':500 if i<len(keys)-1 else 0} for i,k in enumerate(keys)],'reviewStatus':'UNREVIEWED'} for ph,keys in phones.items()], 'limitations':['No clinical/phonetic expert review','No proof of correct closure or airflow','Timings illustrative, not audio synchronized','Voicing pairs share visible oral shape','W release is generic demonstration, not word-specific coarticulation','R tip-up is a separate alternative, not an animation phase','Not bundled in app resources or active registry']}
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n');print('Generated',len(records),'candidate endpoints for',len(phones),'selected targets; topology',th)
