import fs from 'node:fs';
import {build} from 'esbuild';
import {contentSecurityPolicy} from './security.mjs';
import {fileURLToPath} from 'node:url';
const dir = fileURLToPath(new URL('.',import.meta.url));
const destination = process.argv[2];
if (!destination) throw new Error('Pass an absolute temporary fixture directory');
fs.mkdirSync(destination,{recursive:true});
const js = `import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
const violations=[];
document.addEventListener('securitypolicyviolation', e=>violations.push({directive:e.effectiveDirective,uri:e.blockedURI}));
(async()=>{
  try {
    const bytes=Uint8Array.from(atob(document.querySelector('#model').textContent.trim()),c=>c.charCodeAt(0));
    const gltf=await new GLTFLoader().parseAsync(bytes.buffer,'');
    const textures=await gltf.parser.getDependencies('texture');
    const images=textures.map(t=>t?.image).filter(Boolean);
    // This probe must be rejected by CSP before any network request is made.
    await fetch('https://example.invalid/csp-probe').catch(()=>{});
    await new Promise(resolve=>setTimeout(resolve,30));
    window.webkit.messageHandlers.textureResult.postMessage({
      declared:gltf.parser.json.images.length,pngCount:gltf.parser.json.images.filter(i=>i.mimeType==='image/png').length,decoded:images.filter(i=>i.width>0&&i.height>0).length,
      allImageBitmaps:images.length===10 && images.every(i=>i instanceof ImageBitmap),
      blobBlockedByCsp:violations.some(v=>v.directive==='connect-src'&&v.uri.startsWith('blob')),
      networkBlockedByCsp:violations.some(v=>v.directive==='connect-src'&&v.uri.startsWith('https://example.invalid'))
    });
    new Set(images).forEach(i=>i.close?.());
  }catch(error){window.webkit.messageHandlers.textureResult.postMessage({error:String(error)})}
})();`;
const bundle = await build({stdin:{contents:js,resolveDir:dir,loader:'js'},bundle:true,minify:true,format:'iife',write:false});
const script=bundle.outputFiles[0].text.replaceAll('</script','<\\/script');
const model=fs.readFileSync(dir+'vocal-tract.glb').toString('base64');
for (const [name,policy] of [['blob-only',contentSecurityPolicy],['connect-none',contentSecurityPolicy.replace('connect-src blob:',"connect-src 'none'")]]) {
 fs.writeFileSync(destination+'/'+name+'.html',`<!doctype html><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${policy}"><script id="model" type="application/octet-stream">${model}</script><script>${script}</script>`);
}
