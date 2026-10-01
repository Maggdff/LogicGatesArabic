package com.logicgates

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

private val GREEN = Color(0xFF2E7D32)
private val GRAY = Color(0xFF90A4AE)
private val INK = Color(0xFF263238)
private fun wireColor(on: Boolean?) = when (on) { true -> GREEN; false -> GRAY; null -> INK }

fun DrawScope.drawCircuit(c: Circuit, v: Map<Int, Boolean>?) {
    val paint = Paint().apply { isAntiAlias = true; textSize = 22f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.BLACK }
    for (e in c.edges) {
        val s = c.nodes[e.src]; val d = c.nodes[e.dst]
        val (sx, sy) = c.outPin(s); val (dx, dy) = c.inPin(d, e.k); val on = v?.get(e.src)
        val p = Path().apply { moveTo(sx, sy); lineTo(e.cx, sy); lineTo(e.cx, dy); lineTo(dx, dy) }
        drawPath(p, wireColor(on), style = Stroke(if (on == true) 5f else 3f, join = StrokeJoin.Round))
    }
    for (n in c.nodes) {
        val on = v?.get(n.id); val x = n.x; val y = n.y
        when (n.t) {
            GT.IN, GT.OUT -> {
                val left = if (n.t == GT.IN) x - 26 else x
                drawRect(Color(0xFFE3F2FD), Offset(left, y - 18), Size(46f, 36f))
                drawRect(wireColor(on), Offset(left, y - 18), Size(46f, 36f), style = Stroke(3f))
                val label = if (n.name == "0" || n.name == "1") n.name
                else n.name + (if (on == null) "" else "=" + (if (on) "1" else "0"))
                drawContext.canvas.nativeCanvas.drawText(label, left + 23, y + 8, paint)
            }
            GT.WIRE -> drawLine(wireColor(on), Offset(x, y), Offset(x + 80, y), 3f)
            else -> gate(n, on)
        }
    }
}

private fun DrawScope.gate(n: CN, on: Boolean?) {
    val x = n.x; val y = n.y; val col = if (on == true) GREEN else INK
    val p = Path(); var end: Float
    when (n.t) {
        GT.AND, GT.NAND -> { p.moveTo(x, y - 30); p.lineTo(x + 30, y - 30)
            p.arcTo(Rect(x, y - 30, x + 60, y + 30), -90f, 180f, false); p.lineTo(x, y + 30); p.close(); end = x + 60 }
        GT.OR, GT.NOR, GT.XOR, GT.XNOR -> { p.moveTo(x, y - 30); p.quadraticBezierTo(x + 35, y - 30, x + 65, y)
            p.quadraticBezierTo(x + 35, y + 30, x, y + 30); p.quadraticBezierTo(x + 18, y, x, y - 30); p.close(); end = x + 65 }
        else -> { p.moveTo(x, y - 25); p.lineTo(x + 50, y); p.lineTo(x, y + 25); p.close(); end = x + 50 }
    }
    drawPath(p, Color.White)
    drawPath(p, col, style = Stroke(4f, join = StrokeJoin.Round))
    if (n.t == GT.OR || n.t == GT.NOR || n.t == GT.XOR || n.t == GT.XNOR) {
        drawLine(col, Offset(x, y - 15), Offset(x + 7, y - 15), 4f); drawLine(col, Offset(x, y + 15), Offset(x + 7, y + 15), 4f)
    }
    if (n.t == GT.XOR || n.t == GT.XNOR) {
        val q = Path().apply { moveTo(x - 9, y - 30); quadraticBezierTo(x + 9, y, x - 9, y + 30) }
        drawPath(q, col, style = Stroke(4f))
    }
    if (n.t == GT.NAND || n.t == GT.NOR || n.t == GT.XNOR || n.t == GT.NOT) {
        drawCircle(Color.White, 5f, Offset(end + 5, y)); drawCircle(col, 5f, Offset(end + 5, y), style = Stroke(3f)); end += 10
    }
    drawLine(col, Offset(end, y), Offset(x + 80, y), 4f)
}

/** يحفظ الدائرة كصورة PNG عالية الدقة في المعرض ويعيد الـ Uri. */
fun exportPng(ctx: Context, c: Circuit, vals: Map<Int, Boolean>?): Uri? {
    val b = c.bounds(); val k = 3f
    val w = (b[2] * k).toInt(); val h = (b[3] * k).toInt()
    val bmp = ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawRect(Color.White)
        withTransform({ scale(k, k, Offset.Zero); translate(-b[0], -b[1]) }) { drawCircuit(c, vals) }
    }
    val cv = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "circuit_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LogicGates")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    return uri
}
