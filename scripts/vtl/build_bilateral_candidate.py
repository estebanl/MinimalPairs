"""Full-source skin/teeth and explicitly symmetrized artist tissue candidate.
No production resources changed. Educational target deformation is not validated
anatomy or a biomechanical speech simulation.
"""
import bpy,bmesh,math,json,os
from pathlib import Path
from mathutils import Vector
from mathutils.kdtree import KDTree
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'design/bilateral-articulation-candidate';OUT.mkdir(exist_ok=True)
SRC=Path(os.environ.get('BP3D_SOURCE','/workspace/shared/bodyparts3d-research'))/'head-obj'
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'design/production-cutaway-base/production-base.blend'))
# Remove hidden reference meshes; original sources remain in prior authoring file.
for o in list(bpy.context.scene.objects):
 if o.type=='MESH' and (o.hide_render or o.name.startswith('FJ') or 'turbinate' in o.name.lower()):bpy.data.objects.remove(o,do_unlink=True)

def crop(bm):
 for co,no in [((-7.1,0,0),(-1,0,0)),((0,0,4.5),(0,0,1)),((0,0,-6.7),(0,0,-1))]:
  cut=bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),plane_co=co,plane_no=no,clear_outer=True,dist=.00001)
  edges=[e for e in cut['geom_cut'] if isinstance(e,bmesh.types.BMEdge) and e.is_boundary]
  if edges:
   for f in bmesh.ops.holes_fill(bm,edges=edges,sides=0)['faces']:f.smooth=False

def import_source(id,name,mat):
 v=[];f=[]
 for l in (SRC/(id+'.obj')).read_text().splitlines():
  if l.startswith('v '):
   x,y,z=map(float,l.split()[1:4]);v.append(((-y-150)*.1,-x*.1,(z-1470)*.1))
  elif l.startswith('f '):f.append(tuple(int(w.split('/')[0])-1 for w in l.split()[1:]))
 me=bpy.data.meshes.new(name);me.from_pydata(v,[],f);me.update();ob=bpy.data.objects.new(name,me);bpy.context.collection.objects.link(ob);me.materials.append(bpy.data.materials[mat])
 bm=bmesh.new();bm.from_mesh(me);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.00001);crop(bm);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
 for p in me.polygons:p.use_smooth=True
 ob['source_id']=id;ob['authorship']='Full bilateral BodyParts3D source, cropped and modified; not mirrored source anatomy';return ob
# Retain original bilateral skin rather than inventing a mirrored human face.
skin=import_source('FJ2810','Bilateral atlas skin','Refined warm skin')
bm=bmesh.new();bm.from_mesh(skin.data)
original_skin_bvh=BVHTree.FromBMesh(bm)
# Illustrative aperture: this is an authored opening, not a captured speaking face.
faces=[]
for f in bm.faces:
 c=f.calc_center_median();y=c.y
 if c.x>2.8 and abs(y)<2.4:
  rz=.70*math.sqrt(max(0,1-(y/2.4)**2))
  if abs(c.z+1.65)<rz:faces.append(f)
bmesh.ops.delete(bm,geom=faces,context='FACES')
# Reproject the authored aperture boundary onto a smooth ellipse on source skin.
# This corrects the jagged whole-face cut, retaining source surface depth.
boundary={v for e in bm.edges if e.is_boundary for v in e.verts}
for v in boundary:
 x,y,z=v.co
 if x>2.4 and abs(y)<3.0 and -3.1<z<-.2:
  theta=math.atan2((z+1.65)/.70,y/2.4);ny=2.4*math.cos(theta);nz=-1.65+.70*math.sin(theta)
  hit=original_skin_bvh.ray_cast(Vector((10,ny,nz)),Vector((-1,0,0)))[0]
  v.co=(hit.x if hit is not None and hit.x>1 else x,ny,nz)
bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(skin.data);bm.free()
skin['authored_aperture']='Educational speaking opening; not measured'
manifest=json.loads((SRC.parent/'head-mesh-manifest.json').read_text())
for id in [f'FJ{i}' for i in range(1254,1282)]:
 o=import_source(id,id+' '+manifest[id]['names'][0],'Natural enamel')
 # Determine upper/lower from source coordinate centroid, not ID assumptions.
 # IDs1254..1260/1268..1274 are lower teeth in this source subset.
 lower=int(id[2:]) in list(range(1254,1261))+list(range(1268,1275))
 o['jaw']='lower' if lower else 'upper'
 if lower:
  for v in o.data.vertices:v.co.z-=.55
  # Retain the visible crown, not the long root formerly exposed in cutaway.
  crown_base=max(v.co.z for v in o.data.vertices)-.80
  bm=bmesh.new();bm.from_mesh(o.data)
  cut=bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),plane_co=(0,0,crown_base),plane_no=(0,0,-1),clear_outer=True,dist=.00001)
  edges=[e for e in cut['geom_cut'] if isinstance(e,bmesh.types.BMEdge) and e.is_boundary]
  if edges:bmesh.ops.holes_fill(bm,edges=edges,sides=0)
  bm.to_mesh(o.data);bm.free()
  o['crown_display']='Root hidden at authored gingival boundary; crown height 0.8 cm approximation' 
 else:
  bm=bmesh.new();bm.from_mesh(o.data);cut=bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),plane_co=(0,0,-.45),plane_no=(0,0,1),clear_outer=True,dist=.00001)
  bm.to_mesh(o.data);bm.free()
# Symmetrize only newly authored tissue, explicitly preserving a display section.
for o in list(bpy.context.scene.objects):
 if o.type!='MESH' or o.get('source_id'):continue
 bpy.context.view_layer.objects.active=o
 for m in list(o.modifiers):bpy.ops.object.modifier_apply(modifier=m.name)
 bm=bmesh.new();bm.from_mesh(o.data);crop(bm)
 if o.name=='Upper gingival envelope' and bm.verts:
  # The palatal mucosal envelope must join the midline; its original half-view
  # offset was a display choice, not a real central anatomical cleft.
  front=max(v.co.y for v in bm.verts)
  for v in bm.verts:v.co.y-=front
 if bm.verts and min(v.co.y for v in bm.verts)>0:
  shift=max(v.co.y for v in bm.verts)
  for v in bm.verts:v.co.y-=shift
 cut=bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),plane_co=(0,0,0),plane_no=(0,1,0),clear_outer=True,dist=.00001)
 # Remove any pre-existing half-view cap on the mirror plane before extracting
 # one explicit display section. Otherwise the mirror duplicates an internal face.
 midfaces=[f for f in bm.faces if all(abs(v.co.y)<.0001 for v in f.verts)]
 if midfaces:bmesh.ops.delete(bm,geom=midfaces,context='FACES_ONLY')
 edges=[e for e in bm.edges if e.is_boundary and all(abs(v.co.y)<.0001 for v in e.verts)]
 # An internal midline illustration plane, separate from the outer volume.
 if edges:
  caps=bmesh.ops.holes_fill(bm,edges=edges,sides=0)['faces'];verts=[];faces=[]
  for f in caps:
   faces.append(tuple(range(len(verts),len(verts)+len(f.verts))));verts.extend([tuple(v.co) for v in f.verts])
  me=bpy.data.meshes.new(o.name+' section');me.from_pydata(verts,[],faces);me.update();sec=bpy.data.objects.new(o.name+' display section',me);bpy.context.collection.objects.link(sec)
  me.materials.append(o.data.materials[min(1,len(o.data.materials)-1)]);sec['authorship']='Artist-authored internal display section, not a separate anatomical membrane';sec['parent_surface']=o.name
  bmesh.ops.delete(bm,geom=caps,context='FACES_ONLY')
 bm.to_mesh(o.data);bm.free()
 mirror=o.modifiers.new('Artist symmetry, not mirrored source atlas','MIRROR');mirror.use_axis[0]=False;mirror.use_axis[1]=True;mirror.use_clip=True;mirror.use_mirror_merge=True;mirror.merge_threshold=.0001;bpy.ops.object.modifier_apply(modifier=mirror.name)
 # Curved transverse contour, preserving sagittal landmarks at y=0.
 if 'lip' in o.name.lower() or 'gingiv' in o.name.lower():
  for v in o.data.vertices:
   if v.co.x>1.5:v.co.x-=.14*v.co.y*v.co.y
 if 'tongue' in o.name.lower():
  for v in o.data.vertices:
   v.co.x-=.09*v.co.y*v.co.y*max(0,min(1,(v.co.x+1)/3))
   v.co.z-=.07*v.co.y*v.co.y*max(0,min(1,(v.co.z+4)/3))
 o['authorship']='Artist-authored bilateral symmetry from atlas-guided sagittal landmarks, not measured tissue geometry'
# Constrain authored internal tissue to the real source face envelope.
# Source skin itself is never replaced with mirrored/profile geometry.
skin_bm=bmesh.new();skin_bm.from_mesh(skin.data);skin_bvh=BVHTree.FromBMesh(skin_bm)
for ob in bpy.context.scene.objects:
 if ob.type!='MESH' or ob.get('source_id'):continue
 for v in ob.data.vertices:
  hit=skin_bvh.ray_cast(Vector((10,v.co.y,v.co.z)),Vector((-1,0,0)))[0]
  if hit is not None and hit.x>1 and v.co.x>hit.x-.12:v.co.x=hit.x-.12
skin_bm.free()
# Keep natural source-skin material rather than a blotchy face-center lip mask.
# A dedicated lip surface is postponed until topology/pose review.
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'bilateral-base.blend'))
