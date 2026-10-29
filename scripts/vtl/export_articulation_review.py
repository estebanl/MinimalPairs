"""Portable GLB/Blender review exports of frozen VTLM candidates; not app assets."""
import bpy,struct,json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'design/bilateral-articulation-candidate';OUT=BASE/'review-package';OUT.mkdir(exist_ok=True)
COLORS=[0xD4ACA0,0xC97982,0xF2E7D4,0xA85C6A,0xCBA69B,0x4C1C23,0xAE6863,0xD8C7A3,0xBC8078]
NAMES=['Palate','Tongue','Teeth','Lips','Tissue','Cavity','Mucosa','Bone','Cartilage']
def load(path):
 b=path.read_bytes();off=12;out=[]
 for _ in range(struct.unpack_from('<I',b,8)[0]):
  m,nv,nt=struct.unpack_from('<III',b,off);off+=12;v=[]
  for i in range(nv):
   x,z,y=struct.unpack_from('<fff',b,off);off+=12
   v.append(((x-2)/.78-.5,y/.78,(z+3.7)/.78-1))
  f=[]
  for i in range(nt):
   f.append(struct.unpack_from('<III',b,off));off+=48
  out.append((m,v,f))
 return out
records=[]
for item in json.loads((BASE/'poses/manifest.json').read_text())['files']:
 key=item['key'];bpy.ops.wm.read_factory_settings(use_empty=True)
 for material,verts,faces in load(BASE/'poses'/item['path']):
  mesh=bpy.data.meshes.new(NAMES[material]);mesh.from_pydata(verts,[],faces);mesh.update();obj=bpy.data.objects.new(NAMES[material],mesh);bpy.context.collection.objects.link(obj)
  rgb=COLORS[material];color=tuple(((rgb>>s)&255)/255 for s in [16,8,0]);mat=bpy.data.materials.new(NAMES[material]);mat.diffuse_color=(*color,1);mat.use_nodes=True;node=mat.node_tree.nodes.get('Principled BSDF');node.inputs['Base Color'].default_value=(*color,1);node.inputs['Roughness'].default_value=.6;mesh.materials.append(mat)
  for p in mesh.polygons:p.use_smooth=True
  obj['review_status']='UNREVIEWED - not clinically validated';obj['vtlm_sha256']=item['sha256'];obj['material_id']=material
 bpy.context.scene['license_note']='BodyParts3D source skin/teeth, CC BY4.0, DBCLS; modified and artist-authored tissue. See README. Existing VTL GPL assets are separate and not re-licensed.'
 bpy.context.scene['review_note']='Static pose review export. App uses VTLM with explicit section metadata, not GLB. No animations or rig in this review file.'
 file=OUT/(key+'.glb');bpy.ops.export_scene.gltf(filepath=str(file),export_format='GLB',export_yup=True,export_animations=False,export_extras=True)
 if key=='oral-neutral':bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'oral-neutral-review.blend'))
 records.append({'pose':key,'file':file.name,'bytes':file.stat().st_size,'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'sourceVtlmSHA256':item['sha256']})
(OUT/'review-exports.json').write_text(json.dumps(records,indent=2)+'\n')
