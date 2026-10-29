"""Artist-refined educational cutaway, atlas-guided, not clinical ground truth.
Preserves prior atlas deliverables. New lining, tongue/root, palate and section
surfaces are authored illustrations using atlas landmarks, not measured anatomy.
"""
import bpy,bmesh,math,json
from pathlib import Path
from mathutils import Vector
from mathutils.geometry import tessellate_polygon
ROOT=Path(__file__).resolve().parents[2]; OUT=ROOT/'design/production-cutaway-base';OUT.mkdir(exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'design/atlas-anatomy-review/atlas-cutaway.blend'))
# Retain source in file for provenance, but render a coherent tissue cutaway.
for o in list(bpy.context.scene.objects):
 if o.type=='MESH':
  keep=o.name.startswith('FJ2810') or ('tooth' in o.name)
  o.hide_render=not keep;o.hide_viewport=not keep

def mat(name,color,rough=.46):
 m=bpy.data.materials.new(name);m.diffuse_color=(*color,1);m.use_nodes=True
 n=m.node_tree.nodes;p=n.get('Principled BSDF');p.inputs['Base Color'].default_value=(*color,1);p.inputs['Roughness'].default_value=rough
 p.inputs['Subsurface Weight'].default_value=.055
 return m
skin=mat('Refined warm skin',(.66,.36,.25));mucosa=mat('Mucosal surface',(.47,.12,.105),.42);section=mat('Soft tissue section',(.69,.30,.245));tongue=mat('Tongue surface',(.58,.20,.175),.42);muscle=mat('Tongue muscle section',(.64,.255,.215));bone=mat('Trabecular bone section',(.80,.69,.49));enamel=mat('Natural enamel',(.87,.81,.65),.30);dark=mat('Far cavity mucosa',(.22,.042,.036),.6);cart=mat('Epiglottis soft tissue',(.62,.28,.23))
# Subtle directional muscle finish in authoring scene; mobile output must bake
# or use an explicitly simplified material, never pretend shader is portable.
nt=muscle.node_tree;n=nt.nodes;p=n.get('Principled BSDF');tex=n.new('ShaderNodeTexNoise');tex.inputs['Scale'].default_value=12;tex.inputs['Detail'].default_value=2
coord=n.new('ShaderNodeTexCoord');mapping=n.new('ShaderNodeVectorMath');mapping.operation='MULTIPLY';mapping.inputs[1].default_value=(.3,1,5)
nt.links.new(coord.outputs['Generated'],mapping.inputs[0]);nt.links.new(mapping.outputs[0],tex.inputs['Vector']);bump=n.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.15;bump.inputs['Distance'].default_value=.018;nt.links.new(tex.outputs['Fac'],bump.inputs['Height']);nt.links.new(bump.outputs['Normal'],p.inputs['Normal'])

def smooth(points, steps=5):
 out=[];N=len(points)
 for i in range(N):
  p0,p1,p2,p3=[Vector(points[j%N]) for j in (i-1,i,i+1,i+2)]
  for k in range(steps):
   t=k/steps
   q=.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t)
   out.append(tuple(q))
 return out

def profile(name, pts, front=.16, depth=2.2, edge=mucosa,cap=section,bevel=.035):
 pts=smooth(pts);N=len(pts)
 v=[(x,front,z) for x,z in pts]+[(x,front-depth,z) for x,z in pts]
 f=[(i,(i+1)%N,(i+1)%N+N,i+N) for i in range(N)]
 vectors=[Vector((x,0,z)) for x,z in pts];lookup={tuple(v):i for i,v in enumerate(vectors)}
 tris=tessellate_polygon([vectors]);f += [tuple(p if isinstance(p,int) else lookup[tuple(p)] for p in t) for t in tris]; f += [tuple((p if isinstance(p,int) else lookup[tuple(p)])+N for p in reversed(t)) for t in tris]
 me=bpy.data.meshes.new(name);me.from_pydata(v,[],f);me.update();ob=bpy.data.objects.new(name,me);bpy.context.collection.objects.link(ob);me.materials.append(edge);me.materials.append(cap)
 for i,p in enumerate(me.polygons):p.material_index=0 if i<N else 1;p.use_smooth=i<N
 bm=bmesh.new();bm.from_mesh(me);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(me);bm.free()
 if bevel:
  mod=ob.modifiers.new('Rounded cut edge','BEVEL');mod.width=bevel;mod.segments=3
 ob['authorship']='Artist-refined surface based on atlas landmarks; not measured anatomy';return ob
# Replace exposed upper facial bone with coherent tissue and thin hard palate.
profile('Upper lip and alveolar tissue',[(3.9,2.7),(4.35,1.8),(4.0,.8),(4.05,.0),(3.95,-.55),(3.80,-1.05),(3.55,-.95),(3.2,-.45),(2.6,.0),(1.4,.16),(-1,.22),(-3.5,.24),(-3.8,.65),(-1.4,.85),(1.5,.95),(2.75,1.2),(3.1,2.25)],depth=2.7)
profile('Hard palate bone section',[(3.05,.55),(2.6,.82),(1.3,.65),(-1.7,.6),(-3.6,.48),(-3.45,.3),(-1,.38),(1.4,.35),(2.7,.32)],front=.24,depth=.18,edge=bone,cap=bone,bevel=.02)
profile('Upper gingival envelope',[(3.28,.15),(3.27,-.5),(2.65,-.67),(1.6,-.55),(.1,-.45),(-1.65,-.3),(-2.0,.2),(.2,.25),(2.5,.3)],front=-.10,depth=3.1,edge=mucosa,cap=mucosa,bevel=.08)
# Soft palate is continuous with hard palate, with hanging uvula.
profile('Velum and uvula', [(-3.5,.68),(-4.1,.45),(-4.65,.03),(-4.9,-.65),(-4.85,-1.3),(-4.6,-1.65),(-4.33,-1.4),(-4.38,-.83),(-4.16,-.24),(-3.6,.19)],depth=1.6,cap=section)
# Posterior pharyngeal wall, continuous down through the throat.
profile('Posterior pharyngeal lining',[(-5.45,5.5),(-6.1,4),(-6.25,1.9),(-6.1,-.5),(-6.25,-3),(-6.5,-6),(-6.8,-9),(-5.75,-9),(-5.6,-6.5),(-5.5,-4),(-5.35,-1.8),(-5.35,.3),(-5.45,2.6),(-4.9,4.8)],front=.12,depth=2.7)
# Nasal roof provides simple lining rather than skeletal dissection.
profile('Nasal roof tissue',[(3.0,7.8),(2.2,5.8),(1.3,4.9),(-.4,4.65),(-2.6,4.8),(-4.4,4.6),(-5.4,4),(-5.7,5),(-4.5,6.4),(-2,7.8)],front=.10,depth=2.8)
profile('Nasal inferior turbinate',[(2.7,2.5),(1.9,2.7),(.3,2.65),(-1.7,2.55),(-3.3,2.65),(-3.5,2.25),(-1.4,2.05),(.5,2.05),(2.2,2.1)],front=-.55,depth=.65,edge=mucosa,cap=mucosa)
profile('Nasal middle turbinate',[(1.9,3.7),(.4,3.9),(-1.3,3.95),(-3.2,3.7),(-3.45,3.35),(-1.6,3.25),(.3,3.25),(1.7,3.35)],front=-.65,depth=.65,edge=mucosa,cap=mucosa)
# An illustrated tongue/root surface, not an inflated low-resolution atlas mesh.
profile('Refined tongue with connected root',[(2.15,-1.85),(1.2,-1.30),(-.15,-.95),(-1.55,-.95),(-2.75,-1.2),(-3.65,-1.9),(-4.05,-2.85),(-3.85,-3.8),(-3.3,-4.45),(-2.8,-5.8),(-1.65,-5.6),(-.4,-5.15),(1.2,-4.65),(1.55,-3.3),(1.8,-2.5)],front=.27,depth=2.1,edge=tongue,cap=muscle,bevel=.065)
profile('Floor of mouth and lower lip',[(3.75,-2.25),(3.9,-2.65),(3.6,-3.25),(3.55,-4.6),(2.7,-5.5),(1.0,-6.0),(-1.3,-6.25),(-2.3,-7.3),(-2.55,-8.9),(-3.75,-8.9),(-3.8,-7.0),(-3.2,-5.85),(-1.8,-5.25),(.3,-4.9),(1.9,-4.3),(2.45,-3.5),(2.7,-2.4),(3.15,-2.05)],front=.1,depth=2.8,edge=skin,cap=section)
profile('Mandibular section',[(2.65,-2.8),(2.1,-3.7),(1.45,-4.6),(1.6,-5.08),(2.3,-5.05),(2.9,-4.65),(3.05,-3.8),(2.95,-3.1)],front=.23,depth=.15,edge=bone,cap=bone)
profile('Epiglottis lining',[(-3.8,-3.7),(-4.05,-4.1),(-4.2,-5),(-4.55,-6.0),(-4.8,-6.5),(-4.45,-6.6),(-4.0,-5.65),(-3.8,-4.8),(-3.62,-4)],front=.3,depth=.8,edge=cart,cap=cart)
# Internal cavity back walls give readable soft-tissue depth rather than a bare skin shell.
profile('Oral cavity far wall',[(3.0,-.25),(1.1,.03),(-1.7,.08),(-4.2,-.4),(-5.1,-1.5),(-5.3,-4.5),(-4.6,-5.5),(-2.7,-5.3),(.5,-4),(2.3,-2.4)],front=-2.35,depth=.2,edge=dark,cap=dark)
profile('Nasal cavity far wall',[(3.4,2.1),(2.7,4.5),(1.1,5.2),(-2.2,5.2),(-4.8,4.3),(-5.4,1.4),(-4.2,.7),(0,.9),(2.8,1.3)],front=-2.45,depth=.2,edge=mucosa,cap=mucosa)
# Existing atlas dentition: retained source shape. Lower row opens with jaw.
for o in bpy.context.scene.objects:
 if o.type!='MESH' or o.hide_render:continue
 if o.name.startswith('FJ2810'):
  o.data.materials.clear();o.data.materials.append(skin)
  for p in o.data.polygons:p.material_index=0
 elif 'tooth' in o.name:
  o.data.materials.clear();o.data.materials.append(enamel)
  for p in o.data.polygons:p.material_index=0
  if int(o.name[2:6])>=1261:
   bm=bmesh.new();bm.from_mesh(o.data);bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),plane_co=(0,0,-.45),plane_no=(0,0,1),clear_outer=True,dist=.00001);bm.to_mesh(o.data);bm.free()
  if int(o.name[2:6])<=1260:
   for v in o.data.vertices:v.co.z-=.55
# Intentional mouth/nose/throat teaching crop, matching the reference scope.
# Crop authoring geometry, not just camera: no giant posterior skin slab.
for o in list(bpy.context.scene.objects):
 if o.type!='MESH' or o.hide_render:continue
 bm=bmesh.new();bm.from_mesh(o.data)
 for co,no in [((-7.1,0,0),(-1,0,0)),((0,0,5.25),(0,0,1)),((0,0,-7.4),(0,0,-1))]:
  bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),plane_co=co,plane_no=no,clear_outer=True,dist=.00001)
 bm.to_mesh(o.data);bm.free()
# Directional muscle grain remains subtle, avoiding decorative invented anatomy.
nt=muscle.node_tree;n=nt.nodes;p=n.get('Principled BSDF')
wave=n.new('ShaderNodeTexWave');wave.wave_type='BANDS';wave.bands_direction='DIAGONAL';wave.inputs['Scale'].default_value=22;wave.inputs['Distortion'].default_value=7;wave.inputs['Detail'].default_value=3;wave.inputs['Detail Scale'].default_value=.7
nt.links.new(coord.outputs['Generated'],wave.inputs['Vector'])
ramp=n.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.1;ramp.color_ramp.elements[0].color=(.58,.23,.19,1);ramp.color_ramp.elements[1].position=.9;ramp.color_ramp.elements[1].color=(.66,.30,.25,1)
nt.links.new(wave.outputs['Color'],ramp.inputs[0]);# Wave stripe colour deliberately disabled after visual QA
# Bone grain is an illustrative texture, not a CT reconstruction.
nt=bone.node_tree;n=nt.nodes;p=n.get('Principled BSDF');noise=n.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=65;noise.inputs['Detail'].default_value=2
ramp=n.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.3;ramp.color_ramp.elements[0].color=(.42,.28,.13,1);ramp.color_ramp.elements[1].position=.6;ramp.color_ramp.elements[1].color=(.88,.80,.59,1);nt.links.new(noise.outputs['Fac'],ramp.inputs[0]);nt.links.new(ramp.outputs[0],p.inputs['Base Color'])
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=48;scene.cycles.use_denoising=False
scene.render.resolution_x=1200;scene.render.resolution_y=1200
scene.world.color=(.45,.45,.45)
scene.camera.data.ortho_scale=13.6
# Side camera maintains proper anatomical proportions; oblique confirms volume.
def render(name,pos,target):
 scene.camera.location=pos;scene.camera.rotation_euler=(Vector(target)-scene.camera.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(OUT/name);bpy.ops.render.render(write_still=True)
scene.view_settings.view_transform='AgX'
for o in scene.objects:
 if o.type=='LIGHT':o.data.energy*=.8
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'production-base.blend'))
render('side.png',(-.5,35,-1),(-.5,0,-1))
render('oblique.png',(6.8,35,3.0),(-.5,0,-1))
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'production-base.blend'))
