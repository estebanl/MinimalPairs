"""BodyParts3D-derived static anatomy review; original atlas alignment retained.
Source: DBCLS BodyParts3D 4.0; current CC BY4.0; see packaged attribution.
Modifications: subset, coordinate/unit transform, smoothing, sagittal clipping,
artist display materials. Not a clinically validated model or speech pose.
"""
import bpy,bmesh,json,math,os
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'design/atlas-anatomy-review';OUT.mkdir(exist_ok=True)
SRC=Path(os.environ.get('BP3D_SOURCE','/workspace/shared/bodyparts3d-research'))
manifest=json.loads((SRC/'head-mesh-manifest.json').read_text())
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
def material(name,c,rough=.5):
 m=bpy.data.materials.new(name);m.diffuse_color=(*c,1);m.use_nodes=True;p=m.node_tree.nodes.get('Principled BSDF');p.inputs['Base Color'].default_value=(*c,1);p.inputs['Roughness'].default_value=rough;return m
mats={k:material(k,c) for k,c in [('bone',(.77,.66,.47)),('enamel',(.91,.85,.69)),('muscle',(.49,.15,.12)),('tongue',(.64,.25,.23)),('cartilage',(.44,.62,.61)),('palate',(.68,.30,.25)),('skin',(.64,.37,.27)),('section',(.59,.26,.20))]}
# Keep known discrete components, deduplicate overlapping named classifications.
selected=['FJ2761','FJ2762','FJ2741','FJ2748','FJ2753','FJ2760','FJ3199','FJ3263','FJ3369','FJ3289','FJ3269','FJ3375','FJ3273','FJ3379','FJ2740','FJ2742','FJ2747','FJ2752','FJ2754','FJ2759','FJ2770','FJ2808','FJ2769','FJ2775','FJ2792','FJ2772','FJ2738','FJ2750']+[f'FJ{i}' for i in range(1254,1282)]
# OBJ input remains byte-exact in source folder; authoring uses aligned vertices.
for id in selected:
 path=SRC/'head-obj'/f'{id}.obj'
 if not path.exists():continue
 v=[];f=[]
 for l in path.read_text().splitlines():
  if l.startswith('v '):
   x,y,z=map(float,l.split()[1:4]);v.append(((-y-150)*.1,-x*.1,(z-1470)*.1))
  elif l.startswith('f '):f.append(tuple(int(w.split('/')[0])-1 for w in l.split()[1:]))
 me=bpy.data.meshes.new(id);me.from_pydata(v,[],f);me.update();name=manifest[id]['names'][0];ob=bpy.data.objects.new(id+' '+name,me);bpy.context.collection.objects.link(ob)
 key='enamel' if 'tooth' in ' '.join(manifest[id]['names']) else 'cartilage' if 'cartilage' in name or id=='FJ2770' else 'tongue' if id=='FJ2761' else 'palate' if id in ['FJ2762','FJ2741','FJ2748','FJ2753','FJ2760'] else 'bone' if id in ['FJ3199','FJ3263','FJ3369','FJ3289','FJ3269','FJ3375','FJ3273','FJ3379','FJ2772'] else 'muscle'
 me.materials.append(mats[key]);ob['source_id']=id;ob['source_sha256']=manifest[id]['sha256'];ob['license']='BodyParts3D, DBCLS, CC BY 4.0 (current publisher license)'
 for p in me.polygons:p.use_smooth=True
 # Weld coincident original OBJ seam vertices before subdivision.
 bm=bmesh.new();bm.from_mesh(me);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.00001);bmesh.ops.dissolve_degenerate(bm,edges=list(bm.edges),dist=.000001);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
 # Anatomical component surfaces are smoothed, not replaced by ellipsoids.
 bpy.context.view_layer.objects.active=ob
 sm=ob.modifiers.new('Subdivision of original atlas surface','SUBSURF');sm.levels=2 if id=='FJ2761' else 1;sm.render_levels=sm.levels;bpy.ops.object.modifier_apply(modifier=sm.name)
 # Remove near hemisphere to reveal atlas-based internal structures.
 bm=bmesh.new();bm.from_mesh(me);result=bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),plane_co=(0,0,0),plane_no=(0,1,0),dist=.00001,clear_outer=True)
 edges=[e for e in result['geom_cut'] if isinstance(e,bmesh.types.BMEdge) and e.is_boundary]
 if edges:
  caps=bmesh.ops.holes_fill(bm,edges=edges,sides=0)['faces'];me.materials.append(mats['section'] if key in ['tongue','muscle','palate'] else mats[key])
  for p in caps:p.material_index=len(me.materials)-1;p.smooth=False
 bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.00001);bmesh.ops.dissolve_degenerate(bm,edges=list(bm.edges),dist=.000001);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
 if not len(me.polygons):bpy.data.objects.remove(ob,do_unlink=True)
scene=bpy.context.scene;scene.unit_settings.system='METRIC';scene.unit_settings.scale_length=.01
scene.render.engine='CYCLES';scene.cycles.samples=40;scene.cycles.use_denoising=False;scene.world.color=(.25,.25,.25);scene.render.resolution_x=1200;scene.render.resolution_y=1400;scene.render.resolution_percentage=100;scene.view_settings.view_transform='AgX'
def aim(o,p):o.rotation_euler=(Vector(p)-o.location).to_track_quat('-Z','Y').to_euler()
for name,pos,power,size in [('Key',(8,13,15),1400,9),('Fill',(-8,10,3),1000,10),('Rim',(1,-7,12),1500,8)]:
 bpy.ops.object.light_add(type='AREA',location=pos);o=bpy.context.object;o.name=name;o.data.energy=power;o.data.shape='DISK';o.data.size=size;aim(o,(0,0,0))
bpy.ops.object.camera_add(location=(0,35,0));cam=bpy.context.object;scene.camera=cam;cam.data.type='ORTHO';cam.data.ortho_scale=20;aim(cam,(0,0,-1))
scene.render.image_settings.file_format='PNG';scene.render.film_transparent=True;scene.use_nodes=True;n=scene.node_tree.nodes;n.clear();rl=n.new('CompositorNodeRLayers');over=n.new('CompositorNodeAlphaOver');over.inputs[1].default_value=(.94,.91,.86,1);scene.node_tree.links.new(rl.outputs['Image'],over.inputs[2]);comp=n.new('CompositorNodeComposite');scene.node_tree.links.new(over.outputs[0],comp.inputs[0])
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'atlas-cutaway.blend'))
if not os.environ.get('ATLAS_GEOMETRY_ONLY'):
 scene.render.filepath=str(OUT/'atlas-side.png');bpy.ops.render.render(write_still=True)
