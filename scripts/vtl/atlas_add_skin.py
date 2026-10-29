import bpy,bmesh,json,math,os
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'design/atlas-anatomy-review';SRC=Path(os.environ.get('BP3D_SOURCE','/workspace/shared/bodyparts3d-research'))
bpy.ops.wm.open_mainfile(filepath=str(OUT/'atlas-cutaway.blend'))
m=bpy.data.materials.new('skin');m.use_nodes=True;m.diffuse_color=(.64,.37,.27,1);m.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.64,.37,.27,1)
v=[];f=[]
for l in (SRC/'head-obj/FJ2810.obj').read_text().splitlines():
 if l.startswith('v '):
  x,y,z=map(float,l.split()[1:4]);v.append(((-y-150)*.1,-x*.1,(z-1470)*.1))
 elif l.startswith('f '):f.append(tuple(int(w.split('/')[0])-1 for w in l.split()[1:]))
me=bpy.data.meshes.new('FJ2810 cropped original skin');me.from_pydata(v,[],f);me.update();ob=bpy.data.objects.new('FJ2810 retained far-side head skin',me);bpy.context.collection.objects.link(ob);me.materials.append(bpy.data.materials['skin']);ob['source_id']='FJ2810';ob['source_sha256']=json.loads((SRC/'head-mesh-manifest.json').read_text())['FJ2810']['sha256']
bm=bmesh.new();bm.from_mesh(me);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.00001)
for co,no in [((0,0,0),(0,1,0)),((0,0,8),(0,0,1)),((0,0,-10),(0,0,-1))]:
 bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),plane_co=co,plane_no=no,dist=.00001,clear_outer=True)
bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
for p in me.polygons:p.use_smooth=True
bpy.context.view_layer.objects.active=ob
s=ob.modifiers.new('Gentle atlas skin subdivision','SUBSURF');s.levels=1;bpy.ops.object.modifier_apply(modifier=s.name)
s=ob.modifiers.new('Illustrative retained skin thickness 1mm','SOLIDIFY');s.thickness=.10;s.offset=-1;bpy.ops.object.modifier_apply(modifier=s.name)
# Subtle authored finish, not a claim of measured tissue detail.
for mat in [m,bpy.data.materials.get('tongue'),bpy.data.materials.get('section')]:
 if not mat:continue
 nodes=mat.node_tree.nodes;links=mat.node_tree.links;p=nodes.get('Principled BSDF');base=list(p.inputs['Base Color'].default_value)
 tex=nodes.new('ShaderNodeTexCoord');noise=nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=35;noise.inputs['Detail'].default_value=2;links.new(tex.outputs['Object'],noise.inputs['Vector'])
 ramp=nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].color=tuple(c*.90 for c in base[:3])+(1,);ramp.color_ramp.elements[1].color=tuple(min(1,c*1.08) for c in base[:3])+(1,);links.new(noise.outputs['Fac'],ramp.inputs[0]);links.new(ramp.outputs[0],p.inputs['Base Color'])
 bump=nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.13;bump.inputs['Distance'].default_value=.006;links.new(noise.outputs['Fac'],bump.inputs['Height']);links.new(bump.outputs['Normal'],p.inputs['Normal'])
scene=bpy.context.scene;scene.camera.data.ortho_scale=20.2
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'atlas-cutaway.blend'))
for name,loc in [('side',(0,35,-.7)),('oblique',(8,33,4))]:
 scene.camera.location=loc;scene.camera.rotation_euler=(Vector((0,0,-1))-scene.camera.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(OUT/f'atlas-{name}.png');bpy.ops.render.render(write_still=True)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'atlas-cutaway.blend'))
