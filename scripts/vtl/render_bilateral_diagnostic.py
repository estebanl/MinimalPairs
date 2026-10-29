"""Actual Kotlin clipper/rasterizer diagnostic for bilateral fixed-topology candidate."""
from pathlib import Path
import json,subprocess,hashlib
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'design/bilateral-articulation-candidate';BUILD=OUT/'diagnostic-jvm';BUILD.mkdir(exist_ok=True)
SRC=ROOT/'composeApp/src/commonMain/kotlin/com/minimalpairs/app/ui/reference'
for name in ['OralCavityModel.kt','OralTongueSection.kt','OralRasterizer.kt']:(BUILD/name).write_bytes((SRC/name).read_bytes())
s=(SRC/'VocalTractLabMeshes.kt').read_text();(BUILD/'Decoder.kt').write_text('package com.minimalpairs.app.ui.reference\nobject DiagnosticDecoder {\n'+s[s.index('    fun decode(bytes:'):])
topo=json.loads((OUT/'bilateral-topology.json').read_text());flags=[str(t['display_section']).lower() for g in topo['groups'].values() for t in g['triangle_sources']]
(BUILD/'section-flags.txt').write_text(','.join(flags))
(BUILD/'Main.kt').write_text('''import com.minimalpairs.app.ui.reference.*
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
fun main(args: Array<String>) {
 val raw=DiagnosticDecoder.decode(File(args[0]).readBytes())
 val flags=File(args[2]).readText().split(",").map { it.toBoolean() }
 check(raw.size==flags.size)
 val mesh=raw.mapIndexed { i,f -> f.copy(isSection=flags[i]) }
 fun render(label: String, faces: List<OralTriangle>, view: OralView) {
  val frame=OralRasterizer.render(faces,view)
  val img=BufferedImage(frame.width,frame.height,BufferedImage.TYPE_INT_ARGB)
  img.setRGB(0,0,frame.width,frame.height,frame.argb,0,frame.width)
  ImageIO.write(img,"png",File(args[1]+"-"+label+"-argb.png"))
  val out=BufferedImage(frame.width,frame.height,BufferedImage.TYPE_INT_ARGB)
  val g=out.createGraphics();g.color=java.awt.Color(0xF2EEE8);g.fillRect(0,0,frame.width,frame.height);g.drawImage(img,0,0,null);g.dispose()
  ImageIO.write(out,"png",File(args[1]+"-"+label+".png"))
  println("$label triangles=${faces.size} sections=${faces.count{it.isSection}} pixels=${frame.argb.count{it!=0}}")
 }
 render("front",mesh,OralView(0f))
 for(left in listOf(true,false)) {
  val label=if(left) "positive" else "negative"
  val yaw=if(left) 90f else -90f
  val current=OralCavityModel.cutaway(mesh,true,left,true)
  val tongueCrosses=current.filter{it.part==OralPart.TONGUE}.any{f->listOf(f.a,f.b,f.c).any{if(left)it.x<-.00001f else it.x>.00001f}}
  println("$label currentClosedTongueFallbackCrosses=$tongueCrosses")
  render(label+"-current",current,OralView(yaw))
  val clipped=OralCavityModel.cutaway(mesh,true,left,authoredSections=true)
  check(clipped.all{f->listOf(f.a,f.b,f.c).all{if(left)it.x>=-.00001f else it.x<=.00001f}})
  render(label+"-all-parts",clipped,OralView(yaw))
  render(label+"-oblique",clipped,OralView(yaw*.72f,10f))
 }
}
''')
jars=list(Path('/tmp/minimalpairs-kotlinc').glob('*.jar'));cp=':'.join(map(str,jars))
subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(BUILD/'diagnostic.jar')]+[str(p) for p in BUILD.glob('*.kt')],check=True)
r=subprocess.run(['java','-Djava.awt.headless=true','-cp',str(BUILD/'diagnostic.jar')+':'+cp,'MainKt',str(OUT/'bilateral-base.vtlm'),str(OUT/'kotlin'),str(BUILD/'section-flags.txt')],check=True,capture_output=True,text=True)
(OUT/'kotlin-diagnostic.json').write_text(json.dumps({'scope':'Exact current Kotlin renderer/clipper; metadata-tagged sections and actual authoredSections clip mode, no app integration','source_hashes':{n:hashlib.sha256((SRC/n).read_bytes()).hexdigest() for n in ['OralCavityModel.kt','OralTongueSection.kt','OralRasterizer.kt']},'results':r.stdout.splitlines()},indent=2)+'\n');print(r.stdout)
