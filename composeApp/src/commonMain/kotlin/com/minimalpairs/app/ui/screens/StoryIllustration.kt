package com.minimalpairs.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minimalpairs.app.core.model.ComicStory
import com.minimalpairs.app.core.model.MeaningTrapBranch

/** Original vector artwork: the same character and setting persist through each story's branches. */
@Composable
internal fun StoryIllustration(
    story: ComicStory,
    branch: MeaningTrapBranch = MeaningTrapBranch.PENDING,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.widthIn(max = 760.dp).fillMaxWidth().aspectRatio(2f)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = storyIllustrationDescription(story, branch) }
        ) {
            scale(size.width / 700f, size.height / 350f, pivot = Offset.Zero) {
                when (story.id) {
                    "sheep_vs_ship" -> farmScene(branch)
                    "pen_vs_pan" -> examScene(branch)
                    "heart_vs_hut" -> floristScene(branch)
                    "fool_vs_full" -> banquetScene(branch)
                    else -> conversationScene(branch)
                }
            }
        }
    }
}

internal fun storyIllustrationDescription(story: ComicStory, branch: MeaningTrapBranch): String {
    val scene = when (story.id) {
        "sheep_vs_ship" -> when (branch) {
            MeaningTrapBranch.PENDING -> "Farmer Giles welcomes a visitor beside a red barn and a fenced green pasture."
            MeaningTrapBranch.INTENDED_SUCCESS -> "Farmer Giles presents a fluffy white sheep in the peaceful pasture."
            MeaningTrapBranch.TRAP_TRIGGERED -> "A huge cargo ship crashes through the pasture fence as Farmer Giles looks surprised."
        }
        "pen_vs_pan" -> when (branch) {
            MeaningTrapBranch.PENDING -> "Professor Smith stands beside an empty exam desk in a classroom."
            MeaningTrapBranch.INTENDED_SUCCESS -> "Professor Smith presents a blue pen beside the exam paper."
            MeaningTrapBranch.TRAP_TRIGGERED -> "A smoking frying pan sits on the exam desk as Professor Smith looks surprised."
        }
        "heart_vs_hut" -> when (branch) {
            MeaningTrapBranch.PENDING -> "Cupid's florist welcomes a customer at a flower shop counter with roses and a gold pendant."
            MeaningTrapBranch.INTENDED_SUCCESS -> "Cupid's florist presents a gold pendant decorated with a red heart."
            MeaningTrapBranch.TRAP_TRIGGERED -> "Cupid's florist presents a gold pendant decorated with a little wooden hut."
        }
        "fool_vs_full" -> when (branch) {
            MeaningTrapBranch.PENDING -> "The emperor's butler offers a covered serving dish beside a royal banquet table."
            MeaningTrapBranch.INTENDED_SUCCESS -> "The butler respectfully closes the serving dish beside a full dinner plate."
            MeaningTrapBranch.TRAP_TRIGGERED -> "The surprised butler stands beside a court jester in a colorful pointed hat."
        }
        else -> when (branch) {
            MeaningTrapBranch.PENDING -> "Story illustration: ${story.promptCharacter} invites a reply. ${story.premise}"
            MeaningTrapBranch.INTENDED_SUCCESS -> "Story illustration: the conversation follows the intended meaning, ${story.intendedWord}."
            MeaningTrapBranch.TRAP_TRIGGERED -> "Story illustration: the character is surprised by the alternate meaning, ${story.trapWord}."
        }
    }
    return scene
}

private val ink = Color(0xFF283547)
private val cream = Color(0xFFFFF8E8)
private val gold = Color(0xFFE3AD46)
private val red = Color(0xFFD96963)
private val green = Color(0xFF5B8F74)
private val blue = Color(0xFF6F9BB7)

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = ink, width: Float = 4f) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)
}

private fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 10f, outline: Boolean = true) {
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(radius))
    if (outline) drawRoundRect(ink, Offset(x, y), Size(w, h), CornerRadius(radius), style = Stroke(3f))
}

private fun DrawScope.oval(x: Float, y: Float, w: Float, h: Float, color: Color, outline: Boolean = true) {
    drawOval(color, Offset(x, y), Size(w, h))
    if (outline) drawOval(ink, Offset(x, y), Size(w, h), style = Stroke(3f))
}

private fun DrawScope.polygon(color: Color, vararg points: Pair<Float, Float>) {
    val path = Path().apply {
        points.forEachIndexed { index, point -> if (index == 0) moveTo(point.first, point.second) else lineTo(point.first, point.second) }
        close()
    }
    drawPath(path, color)
    drawPath(path, ink, style = Stroke(3f))
}

private fun DrawScope.spark(x: Float, y: Float, color: Color = gold) {
    line(x - 7, y, x + 7, y, color, 3f)
    line(x, y - 7, x, y + 7, color, 3f)
}

private fun DrawScope.character(x: Float, y: Float, clothes: Color, surprised: Boolean, role: String) {
    translate(x, y) {
        // Feet, torso, and hands have identical proportions in every panel.
        box(22f, 129f, 24f, 35f, ink, 5f)
        box(59f, 129f, 24f, 35f, ink, 5f)
        box(13f, 63f, 82f, 76f, clothes, 17f)
        line(19f, 87f, -3f, 117f, clothes, 17f)
        line(89f, 87f, 119f, 90f, clothes, 17f)
        oval(-13f, 110f, 19f, 18f, Color(0xFFE5AC85))
        oval(112f, 80f, 19f, 18f, Color(0xFFE5AC85))
        oval(21f, 0f, 64f, 70f, Color(0xFFE5AC85))
        drawArc(ink, 185f, 170f, false, Offset(24f, -2f), Size(58f, 37f), style = Stroke(10f))
        oval(35f, 26f, 5f, 7f, ink, false)
        oval(63f, 26f, 5f, 7f, ink, false)
        if (surprised) oval(45f, 44f, 14f, 17f, cream)
        else drawArc(ink, 5f, 170f, false, Offset(40f, 34f), Size(25f, 20f), style = Stroke(3f))
        when (role) {
            "farmer" -> {
                box(19f, -16f, 67f, 25f, gold, 6f)
                box(4f, 2f, 98f, 10f, gold, 5f)
                line(34f, 67f, 34f, 135f, blue, 11f)
                line(76f, 67f, 76f, 135f, blue, 11f)
                box(31f, 102f, 48f, 30f, blue, 3f)
            }
            "professor" -> {
                oval(28f, 22f, 23f, 17f, Color.Transparent)
                oval(57f, 22f, 23f, 17f, Color.Transparent)
                line(51f, 29f, 57f, 29f)
                polygon(gold, 47f to 70f, 60f to 70f, 55f to 100f)
            }
            "florist" -> {
                box(30f, 82f, 51f, 53f, cream, 4f)
                flower(55f, 108f, red)
            }
            "butler" -> {
                polygon(cream, 35f to 67f, 73f to 67f, 54f to 104f)
                polygon(red, 40f to 68f, 54f to 74f, 67f to 68f, 67f to 82f, 54f to 76f, 40f to 82f)
                oval(51f, 109f, 5f, 5f, gold, false)
                oval(51f, 123f, 5f, 5f, gold, false)
            }
        }
    }
}

private fun DrawScope.flower(x: Float, y: Float, color: Color) {
    listOf(-7f to 0f, 7f to 0f, 0f to -7f, 0f to 7f).forEach { (dx, dy) ->
        drawCircle(color, 7f, Offset(x + dx, y + dy))
    }
    drawCircle(gold, 5f, Offset(x, y))
}

private fun DrawScope.farmScene(branch: MeaningTrapBranch) {
    val trap = branch == MeaningTrapBranch.TRAP_TRIGGERED
    drawRect(Color(0xFFDCEEF0), size = Size(700f, 350f))
    oval(546f, 26f, 48f, 48f, gold, false)
    oval(-90f, 159f, 650f, 250f, Color(0xFFA5C696), false)
    oval(320f, 177f, 570f, 215f, Color(0xFF88B28F), false)
    box(44f, 123f, 133f, 111f, red, 3f)
    polygon(Color(0xFFB24D4E), 30f to 124f, 111f to 58f, 191f to 124f)
    box(89f, 169f, 47f, 65f, cream, 2f)
    line(93f, 174f, 131f, 231f)
    line(131f, 174f, 93f, 231f)
    for (x in listOf(24f, 80f, 136f, 516f, 574f, 632f, 684f)) box(x, 246f, 12f, 66f, Color(0xFFDDC297), 2f)
    line(14f, 262f, 160f, 262f, cream, 9f)
    line(14f, 287f, 160f, 287f, cream, 9f)
    line(508f, 262f, 698f, if (trap) 305f else 262f, cream, 9f)
    line(508f, 287f, 698f, if (trap) 253f else 287f, cream, 9f)
    character(201f, 138f, green, trap, "farmer")
    when (branch) {
        MeaningTrapBranch.PENDING -> {
            oval(408f, 150f, 154f, 87f, cream)
            oval(425f, 235f, 15f, 12f, cream)
            line(445f, 180f, 501f, 180f, blue)
            line(445f, 195f, 523f, 195f, blue)
        }
        MeaningTrapBranch.INTENDED_SUCCESS -> {
            line(412f, 265f, 412f, 304f, ink, 9f)
            line(493f, 265f, 493f, 304f, ink, 9f)
            oval(375f, 202f, 145f, 81f, cream)
            listOf(389f to 216f, 417f to 201f, 453f to 200f, 486f to 212f, 399f to 250f, 445f to 255f).forEach { (x, y) -> oval(x, y, 37f, 34f, cream, false) }
            oval(501f, 215f, 44f, 52f, ink)
            oval(524f, 225f, 6f, 7f, cream, false)
            polygon(ink, 506f to 215f, 493f to 199f, 516f to 205f)
            spark(560f, 192f)
            spark(372f, 180f)
        }
        MeaningTrapBranch.TRAP_TRIGGERED -> {
            box(411f, 154f, 174f, 57f, cream, 4f)
            box(445f, 116f, 48f, 38f, cream, 3f)
            box(461f, 89f, 17f, 28f, red, 2f)
            listOf(422f, 454f, 486f, 518f, 550f).forEach { x -> box(x, 170f, 19f, 16f, blue, 2f) }
            polygon(Color(0xFF405E79), 345f to 210f, 660f to 210f, 615f to 274f, 390f to 274f)
            box(500f, 192f, 47f, 18f, red, 2f)
            box(552f, 192f, 47f, 18f, gold, 2f)
            line(610f, 300f, 631f, 287f, gold, 7f)
            line(647f, 295f, 659f, 316f, gold, 7f)
            spark(375f, 292f, red)
        }
    }
}

private fun DrawScope.examScene(branch: MeaningTrapBranch) {
    drawRect(Color(0xFFE9E7DA), size = Size(700f, 350f))
    box(46f, 30f, 378f, 129f, green, 5f)
    line(69f, 65f, 225f, 65f, cream, 5f)
    line(69f, 88f, 318f, 88f, cream, 4f)
    line(69f, 111f, 265f, 111f, cream, 4f)
    box(550f, 40f, 96f, 116f, cream, 3f)
    line(598f, 44f, 598f, 150f, blue)
    line(555f, 96f, 641f, 96f, blue)
    drawRect(Color(0xFFD1C1A5), Offset(0f, 286f), Size(700f, 64f))
    character(161f, 158f, Color(0xFF696880), branch == MeaningTrapBranch.TRAP_TRIGGERED, "professor")
    box(346f, 266f, 303f, 18f, Color(0xFFC99162), 4f)
    box(366f, 284f, 13f, 66f, Color(0xFFAE7754), 2f)
    box(619f, 284f, 13f, 66f, Color(0xFFAE7754), 2f)
    polygon(cream, 393f to 231f, 503f to 231f, 521f to 264f, 405f to 264f)
    line(416f, 241f, 476f, 241f, blue, 2f)
    line(420f, 250f, 493f, 250f, blue, 2f)
    when (branch) {
        MeaningTrapBranch.INTENDED_SUCCESS -> {
            line(452f, 174f, 498f, 246f, blue, 13f)
            polygon(ink, 493f to 243f, 502f to 240f, 503f to 256f)
            line(451f, 175f, 465f, 196f, gold, 5f)
            spark(523f, 191f)
        }
        MeaningTrapBranch.TRAP_TRIGGERED -> {
            oval(428f, 211f, 136f, 42f, ink)
            oval(441f, 216f, 110f, 24f, Color(0xFF9DABB4))
            line(556f, 233f, 618f, 213f, ink, 15f)
            drawArc(blue, -90f, 150f, false, Offset(463f, 169f), Size(20f, 35f), style = Stroke(4f))
            drawArc(blue, -90f, 150f, false, Offset(500f, 161f), Size(20f, 35f), style = Stroke(4f))
        }
        MeaningTrapBranch.PENDING -> spark(539f, 229f, blue)
    }
}

private fun DrawScope.floristScene(branch: MeaningTrapBranch) {
    drawRect(Color(0xFFF4E5E2), size = Size(700f, 350f))
    box(28f, 21f, 222f, 157f, cream, 5f)
    line(138f, 24f, 138f, 174f, gold)
    line(32f, 100f, 246f, 100f, gold)
    for (x in listOf(61f, 113f, 173f, 222f)) {
        line(x, 161f, x + 8f, 122f, green, 4f)
        flower(x + 8f, 119f, red)
    }
    box(48f, 159f, 185f, 26f, green, 4f)
    character(258f, 129f, Color(0xFFC07991), branch == MeaningTrapBranch.TRAP_TRIGGERED, "florist")
    box(20f, 283f, 660f, 67f, Color(0xFFD4AE8E), 5f)
    line(28f, 301f, 670f, 301f, cream, 3f)
    box(61f, 240f, 58f, 43f, blue, 5f)
    for ((dx, dy) in listOf(0f to 0f, 25f to -8f, 44f to 5f)) {
        line(89f, 244f, 65f + dx, 213f + dy, green)
        flower(65f + dx, 209f + dy, red)
    }
    oval(437f, 171f, 177f, 105f, Color(0xFF795B76))
    oval(456f, 180f, 139f, 84f, gold)
    drawArc(gold, 190f, 160f, false, Offset(470f, 50f), Size(110f, 153f), style = Stroke(5f))
    when (branch) {
        MeaningTrapBranch.INTENDED_SUCCESS -> {
            val heart = Path().apply {
                moveTo(526f, 243f)
                cubicTo(475f, 211f, 504f, 190f, 526f, 211f)
                cubicTo(549f, 190f, 577f, 211f, 526f, 243f)
                close()
            }
            drawPath(heart, red)
            drawPath(heart, ink, style = Stroke(3f))
            spark(617f, 157f)
        }
        MeaningTrapBranch.TRAP_TRIGGERED -> {
            box(499f, 213f, 56f, 32f, Color(0xFFB48157), 2f)
            polygon(Color(0xFF87633F), 489f to 215f, 527f to 188f, 565f to 215f)
            box(521f, 224f, 13f, 21f, cream, 1f)
            line(507f, 218f, 507f, 242f, ink, 2f)
            line(547f, 218f, 547f, 242f, ink, 2f)
        }
        MeaningTrapBranch.PENDING -> spark(526f, 224f, cream)
    }
}

private fun DrawScope.banquetScene(branch: MeaningTrapBranch) {
    drawRect(Color(0xFFECE1CD), size = Size(700f, 350f))
    box(52f, 20f, 163f, 205f, blue, 75f)
    polygon(Color(0xFFB97475), 36f to 20f, 108f to 20f, 77f to 185f, 36f to 226f)
    polygon(Color(0xFFB97475), 224f to 20f, 156f to 20f, 191f to 185f, 224f to 226f)
    line(131f, 24f, 131f, 210f, gold)
    line(57f, 111f, 209f, 111f, gold)
    box(469f, 36f, 141f, 121f, gold, 4f)
    box(479f, 46f, 121f, 101f, green, 2f)
    polygon(gold, 506f to 108f, 502f to 76f, 524f to 91f, 539f to 65f, 555f to 91f, 578f to 76f, 572f to 108f)
    character(244f, 133f, ink, branch == MeaningTrapBranch.TRAP_TRIGGERED, "butler")
    box(31f, 286f, 635f, 64f, cream, 5f)
    line(41f, 302f, 654f, 302f, gold, 3f)
    when (branch) {
        MeaningTrapBranch.PENDING, MeaningTrapBranch.INTENDED_SUCCESS -> {
            oval(410f, 268f, 193f, 21f, gold)
            drawArc(blue, 180f, 180f, true, Offset(424f, 209f), Size(166f, 122f))
            line(426f, 270f, 589f, 270f)
            oval(494f, 200f, 21f, 12f, gold)
            oval(99f, 266f, 118f, 20f, blue)
            oval(115f, 266f, 85f, 14f, green, false)
            if (branch == MeaningTrapBranch.INTENDED_SUCCESS) {
                spark(611f, 235f)
                spark(93f, 235f)
            }
        }
        MeaningTrapBranch.TRAP_TRIGGERED -> {
            character(462f, 132f, Color(0xFF8879B5), false, "jester")
            polygon(red, 476f to 142f, 466f to 90f, 497f to 112f, 513f to 78f, 532f to 112f, 563f to 90f, 551f to 142f)
            for ((x, y) in listOf(466f to 90f, 513f to 78f, 563f to 90f)) drawCircle(gold, 7f, Offset(x, y))
            box(483f, 141f, 62f, 9f, gold, 2f)
        }
    }
}

/** A deliberate conversation scene for new catalog entries without bespoke artwork yet. */
private fun DrawScope.conversationScene(branch: MeaningTrapBranch) {
    drawRect(Color(0xFFE2ECE9), size = Size(700f, 350f))
    oval(440f, 35f, 194f, 194f, Color(0xFFCEDFD6), false)
    box(62f, 46f, 160f, 164f, cream, 5f)
    line(142f, 50f, 142f, 204f, blue)
    line(67f, 127f, 216f, 127f, blue)
    character(227f, 141f, green, branch == MeaningTrapBranch.TRAP_TRIGGERED, "host")
    oval(408f, 94f, 179f, 107f, cream)
    polygon(cream, 425f to 183f, 405f to 222f, 456f to 198f)
    if (branch == MeaningTrapBranch.INTENDED_SUCCESS) {
        line(459f, 146f, 482f, 170f, green, 8f)
        line(482f, 170f, 532f, 119f, green, 8f)
    } else {
        line(451f, 132f, 538f, 132f, blue, 5f)
        line(451f, 153f, 519f, 153f, blue, 5f)
        line(451f, 174f, 501f, 174f, blue, 5f)
    }
    drawRect(Color(0xFFCFD6CA), Offset(0f, 315f), Size(700f, 35f))
}
