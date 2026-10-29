"""Fail-closed numerical audit of candidate VTLM files; not anatomical validation."""
import argparse,json,struct,math,hashlib
from pathlib import Path
import numpy as np

def audit(path):
 b=path.read_bytes();off=0
 def read(fmt):
  nonlocal off
  n=struct.calcsize(fmt);v=struct.unpack_from(fmt,b,off);off+=n;return v
 assert b[:4]==b'VTLM';off=4
 version,count=read('<II');assert version==1 and 0<count<=32 and len(b)<=2_000_000
 bad=[];total=0;mins=[]
 for surface in range(count):
  material,nv,nt=read('<III');assert material<=8 and 0<nv<=10000 and 0<nt<=20000
  v=np.array([read('<fff') for _ in range(nv)],dtype=np.float32)
  assert np.isfinite(v).all()
  # Mirror actual decoder's float32 operations and axis permutation.
  runtime=np.stack((v[:,2]*np.float32(.23),(v[:,1]+np.float32(3.7))*np.float32(.23),(v[:,0]-np.float32(2))*np.float32(.23)),axis=1)
  for local in range(nt):
   ids=read('<III');assert max(ids)<nv
   normals=np.array(read('<fffffffff'),dtype=np.float32).reshape(3,3)
   assert np.isfinite(normals).all()
   a,c,d=runtime[list(ids)];cross=np.cross(c-a,d-a);area=float(cross@cross);mins.append(area)
   if area<=1e-12 or any(float(n@n)<.01 for n in normals):bad.append({'triangle':total,'surface':surface,'local':local,'crossAreaSquared':area})
   total+=1
 assert off==len(b)
 return {'sha256':hashlib.sha256(b).hexdigest(),'path':str(path),'bytes':len(b),'triangles':total,'surfaces':count,'minimumCrossAreaSquared':min(mins),'invalidTriangles':bad,'passesNumericalGuard':not bad,'anatomicallyValidated':False}

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('files',nargs='+',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
 result=[audit(f) for f in a.files];s=json.dumps(result,indent=2)
 if a.output:a.output.write_text(s+'\n')
 else:print(s)
 raise SystemExit(0 if all(r['passesNumericalGuard'] for r in result) else 1)
