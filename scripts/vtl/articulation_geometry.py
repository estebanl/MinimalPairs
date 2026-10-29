"""Candidate mesh column constraints; geometry contacts are not phonetic approval."""
from pathlib import Path
from functools import lru_cache
import json
import numpy as np
@lru_cache(maxsize=1)
def surfaces():
 p=Path(__file__).resolve().parents[2]/'design/bilateral-articulation-candidate/bilateral-topology.json'
 j=json.loads(p.read_text());out={}
 for o in j['objects']:
  if o.get('parent_surface'):continue
  name=o['name'];v=np.array(o['vertices'],dtype=float)
  if 'Velum' in name:
   w=np.clip((-v[:,0]-3.5)/1.1,0,1);v[:,0]-=.5*w;v[:,2]+=.8*w
  out[name]=v[np.array(o['triangles'])]
 return out

def hits(t,x,y):
 p=t[:,:,:2];u=p[:,1]-p[:,0];v=p[:,2]-p[:,0];d=np.array([x,y])-p[:,0]
 den=u[:,0]*v[:,1]-u[:,1]*v[:,0];ok=abs(den)>1e-12
 a=np.divide(d[:,0]*v[:,1]-d[:,1]*v[:,0],den,out=np.zeros_like(den),where=ok)
 b=np.divide(u[:,0]*d[:,1]-u[:,1]*d[:,0],den,out=np.zeros_like(den),where=ok)
 ok&=(a>=-1e-5)&(b>=-1e-5)&(a+b<=1.00001)
 return (t[:,0,2]+a*(t[:,1,2]-t[:,0,2])+b*(t[:,2,2]-t[:,0,2]))[ok]
@lru_cache(maxsize=40000)
def tongue_column(x,y):
 h=hits(surfaces()['Refined tongue with connected root'],x,y)
 return (float(h.min()),float(h.max())) if len(h)>1 and h.max()-h.min()>.025 else (None,None)
@lru_cache(maxsize=60000)
def mucosal_roof(x,y):
 z=[]
 for name in ['Upper gingival envelope','Upper lip and alveolar tissue','Velum and uvula']:
  h=hits(surfaces()[name],x,y);h=h[h> -1.4]
  if len(h):z.append(float(h.min()))
 return min(z) if z else None
