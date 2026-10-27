// DOM/lifecycle unit verification with renderer and model-load doubles. Not WebGL evidence.
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {JSDOM} from 'jsdom';
import {fileURLToPath} from 'node:url';
const dir=fileURLToPath(new URL('.',import.meta.url));
const three=dir+'node_modules/three/build/three.module.js';
const result=await build({entryPoints:[dir+'viewer.js'],bundle:true,format:'iife',write:false,plugins:[{name:'test-doubles',setup(build){
  build.onResolve({filter:/^three$/},args=>args.importer===dir+'viewer.js'?{path:'renderer',namespace:'test'}:undefined);
  build.onResolve({filter:/^three\/addons\/loaders\/GLTFLoader.js$/},()=>({path:'loader',namespace:'test'}));
  build.onLoad({filter:/.*/,namespace:'test'},args=>({loader:'js',resolveDir:dir,contents:args.path==='renderer'?`
    export * from ${JSON.stringify(three)};
    export class WebGLRenderer {
      info={memory:{geometries:0,textures:0}};
      setPixelRatio(){} setSize(){} render(){window.__renders++} dispose(){window.__disposed++} forceContextLoss(){}
    }
  `:`
    import * as THREE from ${JSON.stringify(three)};
    export class GLTFLoader {parse(bytes,path,done){window.__load=()=>{
      const root=new THREE.Group(); const jaw=new THREE.Group(); jaw.name='Jaw_LowerTeeth'; root.add(jaw);
      const tongue=new THREE.Mesh(new THREE.BoxGeometry(.05,.05,.05),new THREE.MeshStandardMaterial());
      tongue.name='Tongue'; tongue.morphTargetDictionary={Tongue_raise:0,Tongue_advance:1,Tongue_tip_raise:2}; tongue.morphTargetInfluences=[0,0,0]; root.add(tongue);
      const image={width:32,height:32,close(){window.__closedImages++}};
      tongue.material.map=new THREE.Texture(image);
      tongue.material.normalMap=new THREE.Texture(image); // shared bitmap: close exactly once
      return done({scene:root,parser:{json:{textures:[{}]},getDependencies:async()=>[window.__missingTexture?null:{image}]}});
    }}}
  `}));
}}]});
function session() {
  const dom=new JSDOM('<canvas></canvas><script id="model">AA==</script>',{runScripts:'outside-only',pretendToBeVisual:true});
  const w=dom.window,frames=new Map(),messages=[];let id=0;
  w.__renders=0;w.__disposed=0;w.__disconnects=0;w.__closedImages=0;
  w.requestAnimationFrame=fn=>{frames.set(++id,fn);return id};w.cancelAnimationFrame=id=>frames.delete(id);
  w.ResizeObserver=class {observe(){} disconnect(){w.__disconnects++}};
  w.webkit={messageHandlers:{mouthPosition:{postMessage:x=>messages.push(x)}}};
  w.eval(result.outputFiles[0].text);
  const send=change=>w.mouthPosition.receive({version:1,type:'state',pose:'sheep',view:'front',opacity:.5,active:true,reducedMotion:true,...change});
  const tick=(time=1000)=>{const jobs=[...frames.values()];frames.clear();jobs.forEach(f=>f(time))};
  return {dom,w,frames,messages,send,tick};
}
for(let navigation=0;navigation<10;navigation++) {
  const s=session();
  s.send({pose:'sheep'});s.send({pose:'ship',view:'side',active:false});
  assert.equal(s.messages.length,0);await s.w.__load();
  assert.equal(s.messages[0].type,'ready');
  let d=s.w.mouthPositionDiagnostics();assert.equal(d.state.pose,'ship');assert.equal(d.current.height,.42);
  assert.equal(s.frames.size,0,'Hidden/loading state does not animate');
  assert.notEqual(s.w.document.querySelector('canvas').style.visibility,'visible','No unselected startup pose is presented');
  s.send({pose:'ship'});s.tick();
  assert.equal(s.w.document.querySelector('canvas').style.visibility,'visible');
  const idle=s.w.__renders;s.tick();assert.equal(s.w.__renders,idle,'Idle view renders only on demand');
  for(let i=0;i<100;i++) s.send({pose:i%2?'sheep':'ship',reducedMotion:false});
  assert.equal(s.frames.size,1,'Rapid selection coalesces frames');s.tick(10000);
  d=s.w.mouthPositionDiagnostics();assert.equal(d.current.height,.72);assert.equal(d.transitioning,false);
  s.send({pose:'ship',reducedMotion:false});assert.equal(s.frames.size,1);
  s.send({pose:'ship',active:false,reducedMotion:false});assert.equal(s.frames.size,0);
  s.send({pose:'ship',active:true});s.tick();assert.equal(s.w.mouthPositionDiagnostics().current.height,.42);
  assert.equal(s.w.mouthPosition.receive({version:1,type:'dispose'}),true);
  assert.equal(s.w.__disposed,1);assert.equal(s.w.__closedImages,1,'Shared decoded images close exactly once');assert.equal(s.w.__disconnects,1);assert.equal(s.frames.size,0);
  assert.equal(s.send({}),false);assert.equal(s.w.__disposed,1);assert.equal(s.w.__closedImages,1);s.dom.window.close();
}
for(const invalid of [{pose:'U'},{opacity:NaN},{view:'oblique'}]) {
  const s=session();await s.w.__load();assert.equal(s.send(invalid),false);
  assert.equal(s.messages.at(-1).type,'error');assert.equal(s.w.__disposed,1);s.dom.window.close();
}
const early=session();early.w.mouthPosition.receive({version:1,type:'dispose'});await early.w.__load();
assert.equal(early.messages.length,0,'Late load cannot revive a disposed session');early.dom.window.close();


const missing=session();missing.w.__missingTexture=true;await missing.w.__load();assert.equal(missing.messages.at(-1).type,'error');assert.ok(!missing.messages.some(x=>x.type==='ready'));assert.equal(missing.w.__closedImages,1);missing.dom.window.close();

console.log('PASS: DOM lifecycle doubles: 10 navigation sessions, latest loading pose, rapid selection, idle render, pause/resume, reduced motion, unsupported state, early disposal, missing-texture error, deduplicated bitmap close. This does not test WebGL.');
