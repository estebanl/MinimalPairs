import fs from 'node:fs';
import assert from 'node:assert/strict';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import * as THREE from 'three';
import validator from 'gltf-validator';
import {poses, parseState} from './poses.mjs';
const data = fs.readFileSync(new URL('./vocal-tract.glb',import.meta.url));
const report = await validator.validateBytes(new Uint8Array(data),{maxIssues:1000});
assert.equal(report.issues.numErrors,0);
const jsonLength=data.readUInt32LE(12), gltf=JSON.parse(data.subarray(20,20+jsonLength));
assert.equal(gltf.animations?.length||0,0,'No phoneme clips may be implied');
// Geometry-only loader: discard textures to avoid introducing browser/renderer stubs.
const binary=data.subarray(28+jsonLength);
delete gltf.images; delete gltf.textures; delete gltf.samplers;
gltf.materials=[]; for (const mesh of gltf.meshes) for (const primitive of mesh.primitives) delete primitive.material;
gltf.buffers=[{byteLength:binary.length,uri:'data:application/octet-stream;base64,'+binary.toString('base64')}];
if (!globalThis.ProgressEvent) globalThis.ProgressEvent=class {constructor(type,options){Object.assign(this,{type},options)}};
const model=await new GLTFLoader().parseAsync(JSON.stringify(gltf),'');
const meshes=[]; model.scene.traverse(x=>{if(x.isMesh)meshes.push(x)});
assert.equal(meshes.length,65);
const tongue=model.scene.getObjectByName('Tongue');
assert.deepEqual(Object.keys(tongue.morphTargetDictionary).sort(),['Tongue_advance','Tongue_raise','Tongue_tip_raise']);
assert.ok(model.scene.getObjectByName('Jaw_LowerTeeth'));
const state={version:1,type:'state',pose:'sheep',view:'front',opacity:.5,active:true,reducedMotion:true};
assert.ok(parseState(state));
for (const bad of [{pose:'I'},{pose:'uː'},{view:'oblique'},{opacity:NaN},{opacity:2},{active:'yes'},{version:2}]) assert.equal(parseState({...state,...bad}),null);
const tongueBounds=[];
for (let step=0;step<=20;step++) {
  const t=step/20;
  const pose=Object.fromEntries(Object.keys(poses.sheep).map(k=>[k,poses.ship[k]*(1-t)+poses.sheep[k]*t]));
  for (const [morph,key] of [['Tongue_raise','height'],['Tongue_advance','advance'],['Tongue_tip_raise','tip']]) tongue.morphTargetInfluences[tongue.morphTargetDictionary[morph]]=pose[key];
  model.scene.updateMatrixWorld(true);
  const box=new THREE.Box3(),point=new THREE.Vector3();
  for(let i=0;i<tongue.geometry.attributes.position.count;i++) {
    tongue.getVertexPosition(i,point); point.applyMatrix4(tongue.matrixWorld);
    assert.ok(point.toArray().every(Number.isFinite)); box.expandByPoint(point);
  }
  assert.ok(box.max.y<.127,'Tongue remains below the palatal vault bounding height');
  assert.ok(box.max.x<.075,'Tongue stays behind the front facial boundary');
  tongueBounds.push({step,min:box.min.toArray(),max:box.max.toArray()});
}
assert.notDeepEqual(tongueBounds[0].max,tongueBounds.at(-1).max,'Distinct reference geometry');
const html=fs.readFileSync(new URL('../../iosApp/iosApp/MouthPosition/mouth-position.html',import.meta.url),'utf8');
assert.ok(html.includes("connect-src blob:"));
assert.ok(!/<script[^>]+src=/.test(html));
for (const name of ['MODEL-LICENSE.txt','THREE-LICENSE.txt']) assert.ok(html.includes(fs.readFileSync(new URL(name,import.meta.url),'utf8')),'App bundle must include complete notices');
assert.ok(html.includes(data.toString('base64')));
console.log(JSON.stringify({validatorErrors:report.issues.numErrors,validatorWarnings:report.issues.numWarnings,meshes:meshes.length,transitionSteps:tongueBounds.length,tongueBounds:[tongueBounds[0],tongueBounds.at(-1)],htmlBytes:Buffer.byteLength(html)},null,2));
