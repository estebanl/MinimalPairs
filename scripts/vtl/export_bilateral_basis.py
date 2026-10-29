"""Decimate bilateral diagnostic once and retain correspondence for authored poses.
Never writes app resources. Run Blender background with this script.
"""
import bpy,bmesh,math,json,struct,hashlib,sys
sys.path.insert(0,str(__import__("pathlib").Path(__file__).resolve().parent))
from articulation_fan import kernel_anchor
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'design/bilateral-articulation-candidate'
bpy.ops.wm.open_mainfile(filepath=str(OUT/'bilateral-base.blend'))
objects=sorted([o for o in bpy.context.scene.objects if o.type=='MESH' and not o.hide_render],key=lambda o:o.name)
source=[]
for o in objects:
 deps=bpy.context.evaluated_depsgraph_get();me=bpy.data.meshes.new_from_object(o.evaluated_get(deps));o.modifiers.clear();o.data=me
 bpy.context.view_layer.objects.active=o;me.calc_loop_triangles();n=len(me.loop_triangles)
 budget=160 if 'tooth' in o.name else 2000 if 'skin' in o.name.lower() else 800 if 'tongue' in o.name.lower() and not o.get('parent_surface') else 100 if o.get('parent_surface') else 280
 source.append((o,n,min(n,budget)))
weight=min(1,9600/sum(b for _,_,b in source))
for o,n,b in source:
 if n>max(8,int(b*weight)):
  bpy.context.view_layer.objects.active=o;m=o.modifiers.new('Fixed diagnostic LOD','DECIMATE');m.ratio=max(8,int(b*weight))/n;bpy.ops.object.modifier_apply(modifier=m.name)
for o,_,_ in source:
 bpy.context.view_layer.objects.active=o
 m=o.modifiers.new('Freeze triangle topology before poses','TRIANGULATE');m.quad_method='FIXED';m.ngon_method='CLIP';bpy.ops.object.modifier_apply(modifier=m.name)
# Keep authored crop boundaries planar after decimation. Only near-boundary
# vertices are snapped, never an arbitrary interior contour.
boundary_snaps=[]
for o,_,_ in source:
 inv=o.matrix_world.inverted()
 for v in o.data.vertices:
  p=o.matrix_world@v.co
  for axis,plane in [(2,4.5),(2,-6.7),(0,-7.1)]:
   if abs(p[axis]-plane)<=.025:
    old=float(p[axis]);p[axis]=plane
    if abs(old-plane)>1e-7:boundary_snaps.append({'object':o.name,'vertex':v.index,'axis':axis,'before':old,'after':plane})
  v.co=inv@p
 o.data.update()
# Enforce visibility against the actual decimated source skin, not a loose
# nearest-vertex envelope from the higher-resolution authoring mesh.
skin=next(o for o,_,_ in source if o.name=='Bilateral atlas skin')
skin.data.calc_loop_triangles()
skin_bvh=BVHTree.FromPolygons([skin.matrix_world@v.co for v in skin.data.vertices],
 [tuple(t.vertices) for t in skin.data.loop_triangles],all_triangles=True)
clamp_records=[]
for o,_,_ in source:
 if o.get('source_id') or o.get('external_surface'):continue
 inv=o.matrix_world.inverted()
 for v in o.data.vertices:
  p=o.matrix_world@v.co
  sample_z=min(4.495,max(-6.695,p.z))
  hit,_,_,_=skin_bvh.ray_cast(Vector((10,p.y,sample_z)),Vector((-1,0,0)),30)
  outside_mouth=(p.z>.5 or p.z<-3.7)
  affected=(o.name in ['Nasal cavity far wall','Floor of mouth and lower lip'] or o.get('parent_surface') in ['Nasal cavity far wall','Floor of mouth and lower lip'])
  accept_hit=hit is not None and (hit.x>1 or (affected and outside_mouth))
  if accept_hit and p.x>hit.x-.20:
   old=float(p.x);p.x=hit.x-.20;v.co=inv@p
   clamp_records.append({'object':o.name,'vertex':v.index,'old_world_x':old,'new_world_x':float(p.x),'skin_hit_x':float(hit.x),'sample_z':float(sample_z),'extended_neck_or_nasal_containment':bool(affected and outside_mouth and hit.x<=1)})
 o.data.update()
# Re-triangulate the tongue display cap with a constrained interior grid.
# Preserve every boundary position; no per-pose deletion or welding is allowed.
from mathutils.geometry import delaunay_2d_cdt
cap_retriangulation=[]
for o,_,_ in source:
 if not o.get('parent_surface'):continue
 me=o.data; counts={}
 for p in me.polygons:
  ids=list(p.vertices)
  for a,b in zip(ids,ids[1:]+ids[:1]):
   e=tuple(sorted((a,b)));counts[e]=counts.get(e,0)+1
 edges=[e for e,n in counts.items() if n==1];adj={}
 for a,b in edges:adj.setdefault(a,[]).append(b);adj.setdefault(b,[]).append(a)
 assert all(len(v)==2 for v in adj.values()),'Cap boundary must be simple'
 start=min(adj);loop=[start];prev=None;cur=start
 while True:
  nxt=next(v for v in adj[cur] if v!=prev)
  if nxt==start:break
  loop.append(nxt);prev,cur=cur,nxt
 assert len(loop)==len(adj),'Unexpected multiple cap boundaries'
 world=[o.matrix_world@me.vertices[i].co for i in loop]
 pts=[Vector((v.x,v.z)) for v in world]
 boundary=pts[:]
 xmax=max(v.x for v in pts);zmin=min(v.y for v in pts);zmax=max(v.y for v in pts)
 def inside(x,z):
  odd=False
  for a,b in zip(boundary,boundary[1:]+boundary[:1]):
   if (a.y>z)!=(b.y>z) and x<(b.x-a.x)*(z-a.y)/(b.y-a.y)+a.x:odd=not odd
  return odd
 spacing=.45 if o.get('parent_surface')=='Refined tongue with connected root' else .7
 x=min(v.x for v in pts)+spacing/2
 while x<xmax:
  z=zmin+spacing/2
  while z<zmax:
   if inside(x,z):pts.append(Vector((x,z)))
   z+=spacing
  x+=spacing
 # CDT uses explicit original boundary indices; interior grid cannot alter it.
 if o.get('parent_surface')=='Refined tongue with connected root':
  anchor=kernel_anchor([(v.x,v.y) for v in boundary]);vs=boundary+[Vector(anchor)];fs=[(i,(i+1)%len(loop),len(loop)) for i in range(len(loop))];o['fan_boundary_count']=len(loop)
 else:
  vs,es,fs,*_=delaunay_2d_cdt(pts,[(i,(i+1)%len(loop)) for i in range(len(loop))],[list(range(len(loop)))],1,1e-8)
 old=len(me.polygons);mat=me.materials[0] if me.materials else None
 nm=bpy.data.meshes.new(o.name+' constrained cap');inv=o.matrix_world.inverted()
 nm.from_pydata([inv@Vector((v.x,0,v.y)) for v in vs],[],fs)
 if mat:nm.materials.append(mat)
 nm.update();o.data=nm
 cap_retriangulation.append({'object':o.name,'boundary_vertices':len(loop),'vertices':len(vs),'before_triangles':old,'after_triangles':len(fs),'interior_grid_cm':spacing,'method':('Fixed boundary-edge fan with per-pose visibility-kernel anchor, original boundary preserved' if o.get('fan_boundary_count') else 'Constrained Delaunay, original boundary preserved')})
# Lateral tongue endcaps need the same robust topology as the midline cap.
# Reuse original rim vertex IDs so caps remain conforming with the outer shell.
for o,_,_ in source:
 if o.name!='Refined tongue with connected root':continue
 me=o.data;world=[o.matrix_world@v.co for v in me.vertices]
 faces=[list(p.vertices) for p in me.polygons];mats=[p.material_index for p in me.polygons]
 removed=set();newfaces=[];newmats=[]
 for side in [min(v.y for v in world),max(v.y for v in world)]:
  selected=[i for i,f in enumerate(faces) if all(abs(world[v].y-side)<.01 for v in f)]
  counts={}
  for i in selected:
   f=faces[i]
   for a,b in zip(f,f[1:]+f[:1]):
    edge=tuple(sorted((a,b)));counts[edge]=counts.get(edge,0)+1
  adj={}
  for (a,b),n in counts.items():
   if n==1:adj.setdefault(a,[]).append(b);adj.setdefault(b,[]).append(a)
  assert adj and all(len(v)==2 for v in adj.values()),'Lateral cap boundary must be one simple loop'
  start=min(adj);loop=[start];prev=None;cur=start
  while True:
   nxt=next(v for v in adj[cur] if v!=prev)
   if nxt==start:break
   loop.append(nxt);prev,cur=cur,nxt
  assert len(loop)==len(adj)
  pts=[Vector((world[i].x,world[i].z)) for i in loop];boundary=pts[:]
  def inside_lateral(x,z):
   odd=False
   for a,b in zip(boundary,boundary[1:]+boundary[:1]):
    if (a.y>z)!=(b.y>z) and x<(b.x-a.x)*(z-a.y)/(b.y-a.y)+a.x:odd=not odd
   return odd
  spacing=.3;x=min(v.x for v in boundary)+spacing/2
  while x<max(v.x for v in boundary):
   z=min(v.y for v in boundary)+spacing/2
   while z<max(v.y for v in boundary):
    if inside_lateral(x,z):pts.append(Vector((x,z)))
    z+=spacing
   x+=spacing
  vs,es,fs,*_=delaunay_2d_cdt(pts,[(i,(i+1)%len(loop)) for i in range(len(loop))],[list(range(len(loop)))],1,1e-8)
  mapping=[]
  for v in vs:
   match=next((loop[i] for i,p in enumerate(boundary) if (v-p).length<1e-7),None)
   if match is None:match=len(world);world.append(Vector((v.x,side,v.y)))
   mapping.append(match)
  for f in fs:
   ids=[mapping[i] for i in f];a,b,c=[world[i] for i in ids]
   # Outward side normal; preserve full surface orientation.
   if (b-a).cross(c-a).y*side<0:ids.reverse()
   newfaces.append(ids);newmats.append(mats[selected[0]])
  removed.update(selected)
  cap_retriangulation.append({'object':o.name,'side_y':side,'boundary_vertices':len(loop),'before_triangles':len(selected),'after_triangles':len(fs),'interior_grid_cm':spacing,'method':'Lateral constrained Delaunay, shared shell rim IDs retained'})
 keep=[i for i in range(len(faces)) if i not in removed]
 nm=bpy.data.meshes.new(o.name+' stable lateral caps');inv=o.matrix_world.inverted()
 nm.from_pydata([inv@v for v in world],[],[faces[i] for i in keep]+newfaces)
 for m in me.materials:nm.materials.append(m)
 for p,m in zip(nm.polygons,[mats[i] for i in keep]+newmats):p.material_index=m
 nm.update();o.data=nm
# Remove collapsed triangles introduced by boundary/envelope corrections.
# Threshold is evaluated at renderer scale, conservatively above its1e-12 guard.
removed_degenerates=[]
for o,_,_ in source:
 bm=bmesh.new();bm.from_mesh(o.data);bm.faces.ensure_lookup_table()
 bad=[]
 for f in bm.faces:
  assert len(f.verts)==3
  a,b,c=[o.matrix_world@v.co for v in f.verts]
  area2=((b-a).cross(c-a)).length_squared*(.78*.23)**4
  if area2<=2e-12:
   removed_degenerates.append({'object':o.name,'face_before_cleanup':f.index,'vertex_ids':[v.index for v in f.verts],'renderer_cross_area_squared':area2});bad.append(f)
 if bad:bmesh.ops.delete(bm,geom=bad,context='FACES_ONLY')
 bm.to_mesh(o.data);bm.free();o.data.update()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'bilateral-lod.blend'))
def category(name,mat):
 s=(name+' '+mat).lower();m=mat.lower()
 if 'far cavity mucosa' in m:return 5
 if 'tongue' in s:return 1
 if 'tooth' in s or 'enamel' in s:return 2
 if 'bone' in s or 'mandibular' in s:return 7
 if 'cartilage' in s or 'epiglottis' in s:return 8
 if 'mucosal' in m or 'mucosa' in m:return 6
 if 'palate' in s or 'velum' in s or 'uvula' in s:return 0
 if 'vermilion' in m or 'lip' in m:return 3
 return 4
scale=.78;center=Vector((-.5,0,-1))
def point(v):
 q=(v-center)*scale
 return (q.x+2,q.z-3.7,q.y)
def norm(n):return (n.x,n.z,n.y)
groups={i:{'vertices':[],'triangles':[],'corner_normals':[],'triangle_sources':[]} for i in range(9)}
records=[]
for o,original_count,_ in source:
 me=o.data;me.calc_loop_triangles();world=o.matrix_world;nt=world.to_3x3().inverted().transposed()
 vertices=[tuple(world@v.co) for v in me.vertices]
 rec={'name':o.name,'source_id':o.get('source_id'),'parent_surface':o.get('parent_surface'),'jaw':o.get('jaw'),'fan_boundary_count':o.get('fan_boundary_count'),'fanBoundaryIndices':list(range(int(o['fan_boundary_count']))) if o.get('fan_boundary_count') else None,'fanAnchorIndex':int(o['fan_boundary_count']) if o.get('fan_boundary_count') else None,'vertices':vertices,'triangles':[],'triangle_materials':[],'corner_normals':[],'group_vertex_map':{}}
 for tri in me.loop_triangles:
  m=me.materials[tri.material_index].name if me.materials else '';mat=category(o.name,m);g=groups[mat];mapping=rec['group_vertex_map'].setdefault(str(mat),{})
  inds=[];normals=[]
  for vi,li in zip(tri.vertices,tri.loops):
   if str(vi) not in mapping:mapping[str(vi)]=len(g['vertices']);g['vertices'].append(point(Vector(vertices[vi])))
   inds.append(mapping[str(vi)]);normals.append(tuple((nt@me.corner_normals[li].vector).normalized()))
  rec['triangles'].append(list(tri.vertices));rec['triangle_materials'].append(mat);rec['corner_normals'].append(normals)
  g['triangles'].append(inds);g['corner_normals'].append([norm(Vector(n)) for n in normals]);g['triangle_sources'].append({'object':o.name,'triangle':len(rec['triangles'])-1,'display_section':bool(o.get('parent_surface'))})
 records.append(rec)
active=[(i,g) for i,g in groups.items() if g['triangles']];raw=bytearray(b'VTLM'+struct.pack('<II',1,len(active)))
for mat,g in active:
 assert 3<=len(g['vertices'])<=10000 and 1<=len(g['triangles'])<=20000
 raw+=struct.pack('<III',mat,len(g['vertices']),len(g['triangles']))
 for p in g['vertices']:raw+=struct.pack('<3f',*p)
 for ids,ns in zip(g['triangles'],g['corner_normals']):raw+=struct.pack('<3I9f',*ids,*[v for n in ns for v in n])
assert len(raw)<2000000
n=sum(len(g['triangles']) for _,g in active);assert n<=15000,n
(OUT/'bilateral-base.vtlm').write_bytes(raw)
report={'format':'Stable LOD correspondence v1','coordinates':'Blender world cm, X anterior/Y lateral/Z superior; VTLM=(scaled_x+2,scaled_z-3.7,scaled_y)','scale':scale,'center':list(center),'camera':'Front yaw0; positive-Blender-Y cut side yaw-90; opposite yaw+90','objects':records,'groups':{str(i):g for i,g in active},'input_sha256':hashlib.sha256((OUT/'bilateral-base.blend').read_bytes()).hexdigest(),'triangles':n,'bytes':len(raw),'vtlm_sha256':hashlib.sha256(raw).hexdigest(),'cap_retriangulation':cap_retriangulation,'removed_degenerate_triangles':removed_degenerates,'boundary_snaps':boundary_snaps,'boundary_planes_cm':{'superior':4.5,'inferior':-6.7,'posterior':-7.1},'envelope_clamps':clamp_records,'envelope_method':'Anterior ray against final decimated skin; crop-edge rays sampled0.005cm inside; x<=1 allowed only for diagnosed nasal/floor surfaces outside mouth region; margin0.20cm; source/external surfaces excluded','notes':['Do not redecimate poses: preserve object vertex IDs, triangle order and group_vertex_map.','Display sections are retained as explicit metadata; VTLM v1 does not carry isSection flags.','Recompute corner normals from deformed geometry; never apply undeformed normals to poses.','Do not infer speech validity from distinct shape keys.']}
(OUT/'bilateral-topology.json').write_text(json.dumps(report,separators=(',',':'))+'\n')
print('BILATERAL',n,len(raw),report['vtlm_sha256'])
