import * as THREE from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {poses, parseState} from './poses.mjs';

const canvas = document.querySelector('canvas');
let renderer, scene, camera, root, jawPivot, observer, frame = 0, disposed = false, loaded = false;
let state = {version: 1, type: 'state', pose: 'sheep', view: 'front', opacity: .5, active: false, reducedMotion: true};
let current = {...poses.sheep}, transition, hasPresentedState = false;
const meshes = [];
const stats = {renders: 0, loaded: false, disposed: false, error: null};
const native = type => window.webkit?.messageHandlers?.mouthPosition?.postMessage({version: 1, type});
function fail() { if (disposed) return; stats.error = 'viewer'; native('error'); dispose(); }
function schedule() { if (!frame && loaded && state.active && !disposed && !document.hidden) frame = requestAnimationFrame(draw); }
function stop() { cancelAnimationFrame(frame); frame = 0; }
function applyPose() {
  for (const mesh of meshes) {
    for (const [name, key] of [['Tongue_raise','height'], ['Tongue_advance','advance'], ['Tongue_tip_raise','tip'], ['Lip_round','round'], ['Jaw_open','jaw']]) {
      const index = mesh.morphTargetDictionary?.[name];
      if (index !== undefined) mesh.morphTargetInfluences[index] = current[key];
    }
  }
  if (jawPivot) jawPivot.rotation.z = -THREE.MathUtils.degToRad(10) * current.jaw;
}
function opacity() {
  for (const mesh of meshes) if (/FacialShell|NoseNostril|Lip/.test(mesh.name)) {
    const amount = /Lip/.test(mesh.name) ? state.opacity : state.opacity * .4;
    mesh.renderOrder = /Lip/.test(mesh.name) ? 2 : 3;
    for (const material of [mesh.material].flat()) {
      material.opacity = amount; material.transparent = amount < 1;
      material.depthWrite = amount >= 1; material.needsUpdate = true;
    }
  }
}
function fit() {
  if (!root) return;
  root.updateMatrixWorld(true);
  const box = new THREE.Box3();
  for (const mesh of meshes) if (mesh.visible) {
    mesh.geometry.computeBoundingBox();
    box.union(mesh.geometry.boundingBox.clone().applyMatrix4(mesh.matrixWorld));
  }
  const center = box.getCenter(new THREE.Vector3());
  const dir = state.view === 'front' ? new THREE.Vector3(1,0,0) : new THREE.Vector3(0,0,1);
  const right = new THREE.Vector3().crossVectors(new THREE.Vector3(0,1,0), dir).normalize();
  const up = new THREE.Vector3().crossVectors(dir, right).normalize();
  const tanV = Math.tan(THREE.MathUtils.degToRad(camera.fov)/2), tanH = tanV*camera.aspect;
  let distance = .12;
  for (const x of [box.min.x,box.max.x]) for (const y of [box.min.y,box.max.y]) for (const z of [box.min.z,box.max.z]) {
    const q = new THREE.Vector3(x,y,z).sub(center);
    distance = Math.max(distance,q.dot(dir)+1.25*Math.abs(q.dot(right))/tanH,q.dot(dir)+1.25*Math.abs(q.dot(up))/tanV);
  }
  camera.position.copy(center).addScaledVector(dir,distance); camera.lookAt(center); camera.updateMatrixWorld();
}
function draw(time) {
  frame = 0;
  if (disposed || !state.active || document.hidden) return;
  try {
    if (transition) {
      const t = state.reducedMotion ? 1 : Math.min(1,Math.max(0,(time-transition.start)/220));
      const eased = t*t*(3-2*t);
      for (const key of Object.keys(current)) current[key] = transition.from[key]+(transition.to[key]-transition.from[key])*eased;
      applyPose();
      if (t === 1) transition = null;
    }
    renderer.render(scene,camera); stats.renders++;
    canvas.style.visibility = 'visible';
    if (transition) schedule();
  } catch { fail(); }
}
function receive(value) {
  if (disposed) return false;
  if (value?.version === 1 && value.type === 'dispose') { dispose(); return true; }
  const next = parseState(value); if (!next) { fail(); return false; }
  const changed = next.pose !== state.pose;
  state = next;
  if (!loaded) return true; // latest state replaces all earlier loading selections
  if (!hasPresentedState || changed || state.reducedMotion || !state.active) {
    if (!hasPresentedState || state.reducedMotion || !state.active) { current = {...poses[state.pose]}; transition = null; applyPose(); }
    else transition = {from: {...current}, to: poses[state.pose], start: performance.now()};
  }
  hasPresentedState = true;
  opacity(); fit();
  if (!state.active) stop(); else schedule();
  return true;
}
function visibility() {
  if (document.hidden) { stop(); transition = null; current = {...poses[state.pose]}; applyPose(); }
  else schedule();
}
function contextLost(event) { event.preventDefault(); fail(); }
function disposeRoot(object) {
  const geometries = new Set(), materials = new Set(), textures = new Set();
  object?.traverse(mesh => {
    if (mesh.geometry) geometries.add(mesh.geometry);
    for (const material of [mesh.material].flat().filter(Boolean)) {
      materials.add(material);
      for (const value of Object.values(material)) if (value?.isTexture) textures.add(value);
    }
  });
  textures.forEach(x=>x.dispose()); materials.forEach(x=>x.dispose()); geometries.forEach(x=>x.dispose());
}
function dispose() {
  if (disposed) return;
  disposed = true; stats.disposed = true; stop(); observer?.disconnect(); transition = null;
  document.removeEventListener('visibilitychange',visibility); window.removeEventListener('pagehide',dispose);
  canvas.removeEventListener('webglcontextlost',contextLost);
  disposeRoot(root); scene?.clear(); renderer?.dispose(); renderer?.forceContextLoss();
}
// The only callable bridge accepts validated pose/view/lifecycle state. No audio API.
window.mouthPosition = Object.freeze({receive});
// Read-only diagnostics for local QA; no pixel capture buffer retained in production.
window.mouthPositionDiagnostics = () => ({...stats, state: {...state}, current: {...current}, transitioning: !!transition,
  meshes: meshes.length, geometries: renderer?.info.memory.geometries, textures: renderer?.info.memory.textures,
  jawRotation: jawPivot?.rotation.z, morphs: meshes.filter(x=>x.morphTargetDictionary).map(x=>({name:x.name, targets:x.morphTargetDictionary, values:[...x.morphTargetInfluences]}))});
try {
  renderer = new THREE.WebGLRenderer({canvas,antialias:true,alpha:true,preserveDrawingBuffer:false});
  if (renderer.debug) renderer.debug.onShaderError = fail;
  renderer.setPixelRatio(Math.min(devicePixelRatio||1,2)); renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05;
  scene = new THREE.Scene(); camera = new THREE.PerspectiveCamera(33,1,.001,10);
  scene.add(new THREE.HemisphereLight(0xf5f4ef,0x52616b,1.4));
  for (const [color,power,position] of [[0xffeee2,2.8,[.25,.4,.5]],[0xdbe9ed,1.1,[-.3,.12,.3]],[0xf1efe1,1.3,[.08,.25,-.3]]]) {
    const light = new THREE.DirectionalLight(color,power); light.position.set(...position); scene.add(light);
  }
  observer = new ResizeObserver(()=>{
    const {width,height} = canvas.getBoundingClientRect(); if (!width || !height || disposed) return;
    renderer.setSize(width,height,false); camera.aspect = width/height; camera.updateProjectionMatrix(); fit(); schedule();
  }); observer.observe(canvas);
  canvas.addEventListener('webglcontextlost',contextLost);
  document.addEventListener('visibilitychange',visibility); window.addEventListener('pagehide',dispose);
  const bytes = Uint8Array.from(atob(document.querySelector('#model').textContent.trim()),c=>c.charCodeAt(0));
  new GLTFLoader().parse(bytes.buffer,'',gltf=>{
    if (disposed) { disposeRoot(gltf.scene); return; }
    root = gltf.scene; scene.add(root);
    root.traverse(mesh=>{
      if (!mesh.isMesh) return;
      meshes.push(mesh);
      mesh.visible = !/Mandible|MouthFloor|Pharyn|Nasal|Laryn|Trache|Epiglott|VocalFold/.test(mesh.name);
      for (const material of [mesh.material].flat()) { material.side = THREE.DoubleSide; if (material.transparent) material.depthWrite = false; }
    });
    const jaw = root.getObjectByName('Jaw_LowerTeeth');
    if (!root.getObjectByName('Tongue')?.morphTargetDictionary?.hasOwnProperty('Tongue_raise') || !jaw) { fail(); return; }
    jawPivot = new THREE.Group(); jawPivot.position.set(-.052,.075,0); root.add(jawPivot); root.updateMatrixWorld(true); jawPivot.attach(jaw);
    current = {...poses[state.pose]}; applyPose(); opacity(); loaded = true; stats.loaded = true; fit();
    // Compile and draw once before announcing ready, even while native state is queued.
    renderer.render(scene,camera); stats.renders++; if (!disposed) native('ready'); schedule();
  },fail);
} catch { fail(); }
