package com.minimalpairs.app.sdd

import com.minimalpairs.app.ui.reference.*
import kotlin.math.abs
import kotlin.test.*

class OralTongueSectionTest {
    private fun cube(part: OralPart = OralPart.TONGUE): List<OralTriangle> {
        val p = listOf(OralPoint(-1f,-1f,-1f),OralPoint(1f,-1f,-1f),OralPoint(1f,1f,-1f),OralPoint(-1f,1f,-1f),
            OralPoint(-1f,-1f,1f),OralPoint(1f,-1f,1f),OralPoint(1f,1f,1f),OralPoint(-1f,1f,1f))
        return listOf(listOf(0,1,2,3),listOf(4,7,6,5),listOf(0,4,5,1),listOf(3,2,6,7),listOf(0,3,7,4),listOf(1,5,6,2))
            .flatMap { q -> listOf(listOf(q[0],q[1],q[2]),listOf(q[0],q[2],q[3])) }.map { t ->
                val a=p[t[0]];val b=p[t[1]];val c=p[t[2]];val n=(b-a).cross(c-a).normalized()
                OralTriangle(a,b,c,part,n,n,n)
            }
    }

    @Test fun trueSectionContainsOnlyTongueIntersectionsAndHasOppositeSideNormals() {
        val tongue = cube()
        val foreign = cube(OralPart.TISSUE).map { it.copy(a=it.a.copy(y=it.a.y+20),b=it.b.copy(y=it.b.y+20),c=it.c.copy(y=it.c.y+20)) }
        for (left in listOf(false,true)) {
            val cut = OralCavityModel.cutaway(tongue + foreign, true, left, sectionClosedTongue = true)
            val cap = cut.filter { it.isSection }
            assertEquals(2, cap.size)
            assertTrue(cap.all { it.part == OralPart.TONGUE && it.na.x == if(left) -1f else 1f })
            assertTrue(cap.flatMap { listOf(it.a,it.b,it.c) }.all { it.x == 0f && abs(it.y) <= 1f && abs(it.z) <= 1f })
            val area = cap.sumOf { abs((it.b.y-it.a.y)*(it.c.z-it.a.z)-(it.b.z-it.a.z)*(it.c.y-it.a.y)).toDouble()/2 }
            assertEquals(4.0, area, .0001)
            assertEquals(cap, OralCavityModel.cutaway(tongue,true,left,sectionClosedTongue=true).filter { it.isSection })
            assertTrue(cap.all { (it.b-it.a).cross(it.c-it.a).x * it.na.x > 0 })
        }
        assertTrue(OralCavityModel.cutaway(tongue,true).none { it.isSection }, "Original-surface default must not invent sections")
    }

    @Test fun openContourDoesNotGetAnInventedClosingBridge() {
        val openTongue = cube().drop(2)
        val cut = OralCavityModel.cutaway(openTongue,true,sectionClosedTongue=true)
        assertTrue(cut.none { it.isSection })
        assertEquals(openTongue, cut, "Unsupported cap must retain the complete surface, not an open sliced shell")
        assertTrue(OralTongueSection.cap(emptyList()).isEmpty())
    }

    @Test fun exactCoplanarSectionWinsRegardlessOfDrawOrder() {
        val n=OralPoint(0f,0f,1f)
        val wall=OralTriangle(OralPoint(-1f,-1f,0f),OralPoint(1f,-1f,0f),OralPoint(0f,1f,0f),OralPart.TISSUE,n,n,n)
        val cap=wall.copy(part=OralPart.TONGUE,isSection=true)
        val view=OralView(yaw=0f)
        val a=OralRasterizer.render(listOf(wall,cap),view,32,32)
        val b=OralRasterizer.render(listOf(cap,wall),view,32,32)
        assertContentEquals(a.argb,b.argb)
        assertContentEquals(OralRasterizer.render(listOf(cap),view,32,32).argb,a.argb)
    }
}
