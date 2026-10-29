"""Render UNREVIEWED candidate endpoints with the actual Kotlin rasterizer."""
from pathlib import Path
import json,subprocess,hashlib
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'design/bilateral-articulation-candidate';BUILD=OUT/'pose-diagnostic-jvm';BUILD.mkdir(exist_ok=True);DEST=OUT/'pose-previews';DEST.mkdir(exist_ok=True)
SRC=ROOT/'composeApp/src/commonMain/kotlin/com/minimalpairs/app/ui/reference'
for n in ['OralCavityModel.kt','OralTongueSection.kt','OralRasterizer.kt']:(BUILD/n).write_bytes((SRC/n).read_bytes())
s=(SRC/'VocalTractLabMeshes.kt').read_text();(BUILD/'Decoder.kt').write_text('package com.minimalpairs.app.ui.reference\nobject DiagnosticDecoder {\n'+s[s.index('    fun decode(bytes:'):])
t=json.loads((OUT/'bilateral-topology.json').read_text());(BUILD/'section-flags.txt').write_text(','.join(str(x['display_section']).lower() for g in t['groups'].values() for x in g['triangle_sources']))
(BUILD/'Main.kt').write_text('''import com.minimalpairs.app.ui.reference.*
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
fun main(args:Array<String>){
 val flags=File(args[2]).readText().split(",").map{it.toBoolean()}
 for(file in File(args[0]).listFiles()!!.filter{it.extension=="vtlm"}.sortedBy{it.name}){
  val mesh=DiagnosticDecoder.decode(file.readBytes()).mapIndexed{i,f->f.copy(isSection=flags[i])}
  val zero=mesh.sumOf{f->listOf(f.na,f.nb,f.nc).count{it.x*it.x+it.y*it.y+it.z*it.z<.01f}}
  val clipped=OralCavityModel.cutaway(mesh,true,false,authoredSections=true)
  val frame=OralRasterizer.render(clipped,OralView(-90f))
  val img=BufferedImage(frame.width,frame.height,BufferedImage.TYPE_INT_ARGB);img.setRGB(0,0,frame.width,frame.height,frame.argb,0,frame.width)
  val out=BufferedImage(frame.width,frame.height,BufferedImage.TYPE_INT_ARGB);val g=out.createGraphics();g.color=java.awt.Color(0xF2EEE8);g.fillRect(0,0,frame.width,frame.height);g.drawImage(img,0,0,null);g.dispose()
  ImageIO.write(out,"png",File(args[1]+"/"+file.nameWithoutExtension+".png"))
  println("${file.name} triangles=${mesh.size} zeroNormals=$zero pixels=${frame.argb.count{it!=0}}")
 }
}
''')
jars=list(Path('/tmp/minimalpairs-kotlinc').glob('*.jar'));cp=':'.join(map(str,jars))
subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(BUILD/'diagnostic.jar')]+[str(p) for p in BUILD.glob('*.kt')],check=True)
r=subprocess.run(['java','-Djava.awt.headless=true','-cp',str(BUILD/'diagnostic.jar')+':'+cp,'MainKt',str(OUT/'poses'),str(DEST),str(BUILD/'section-flags.txt')],capture_output=True,text=True,check=True)
(OUT/'pose-raster-audit.txt').write_text(r.stdout);print(r.stdout)
