"""Fixed-connectivity sagittal display fan; never changes physical tongue geometry."""
import numpy as np

def kernel_anchor(points):
 """Return strict interior kernel centroid of ordered Nx2 polygon, or fail closed."""
 points=np.asarray(points,dtype=float)
 def cross(a,b):return a[0]*b[1]-a[1]*b[0]
 sign=1 if sum(cross(a,b) for a,b in zip(points,np.roll(points,-1,axis=0)))>0 else -1
 poly=[np.array(p,float) for p in [(-100,-100),(100,-100),(100,100),(-100,100)]]
 for a,b in zip(points,np.roll(points,-1,axis=0)):
  out=[]
  for p,q in zip(poly,poly[1:]+poly[:1]):
   dp=sign*cross(b-a,p-a);dq=sign*cross(b-a,q-a)
   if dp>=0:out.append(p)
   if (dp>=0)!=(dq>=0):out.append(p+(q-p)*dp/(dp-dq))
  poly=out
  if not poly:raise ValueError('Tongue outline has no star-shaped kernel')
 anchor=np.mean(poly,axis=0)
 if min(sign*cross(b-a,anchor-a) for a,b in zip(points,np.roll(points,-1,axis=0)))<=1e-10:raise ValueError('Tongue fan has no strict positive-area kernel')
 return anchor

def place_fan_anchor(deformed_vertices,boundary_count):
 """Return copied Nx3 coordinates, moving ONLY last fan anchor at index boundary_count.
 Boundary vertices0..boundary_count-1 remain unchanged; use x/z kernel,y=0.
 """
 a=np.asarray(deformed_vertices,dtype=float).copy()
 if len(a)!=boundary_count+1:raise ValueError('Unexpected fixed fan topology')
 anchor=kernel_anchor(a[:boundary_count][:,[0,2]])
 a[boundary_count]=[anchor[0],0,anchor[1]]
 return a

def kernel_center(vertices_xz, boundary_indices):
 """Return a kernel anchor for an explicitly indexed ordered boundary."""
 return kernel_anchor(np.asarray(vertices_xz,dtype=float)[list(boundary_indices)])
