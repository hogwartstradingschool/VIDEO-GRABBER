package com.vg.grabber

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

// Original SVG puppy (40x40) ka Canvas port
class PuppyView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float, col: String, a: Int = 255, rot: Float = 0f, cv: Canvas) {
        p.style = Paint.Style.FILL; p.color = Color.parseColor(col); p.alpha = a
        cv.save(); cv.rotate(rot, cx, cy); cv.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p); cv.restore()
    }
    override fun onDraw(cv: Canvas) {
        cv.scale(width / 40f, height / 40f)
        oval(8.5f, 17f, 6f, 11f, "#b9774a", rot = 14f, cv = cv)
        oval(31.5f, 17f, 6f, 11f, "#b9774a", rot = -14f, cv = cv)
        oval(20f, 21.5f, 12.5f, 12.5f, "#fff1de", cv = cv)
        oval(14.8f, 19.5f, 2.3f, 2.3f, "#2a1f3d", cv = cv); oval(25.2f, 19.5f, 2.3f, 2.3f, "#2a1f3d", cv = cv)
        oval(15.5f, 18.7f, .85f, .85f, "#ffffff", cv = cv); oval(25.9f, 18.7f, .85f, .85f, "#ffffff", cv = cv)
        oval(11f, 25f, 2.4f, 2.4f, "#ff8fb5", 153, cv = cv); oval(29f, 25f, 2.4f, 2.4f, "#ff8fb5", 153, cv = cv)
        oval(20f, 24.2f, 3.1f, 2.2f, "#3b2a3a", cv = cv)
        val tongue = Path().apply { moveTo(18.3f, 28.6f); rQuadTo(1.7f, 5f, 3.4f, 0f); close() }
        p.style = Paint.Style.FILL; p.color = Color.parseColor("#ff6b9a"); p.alpha = 255; cv.drawPath(tongue, p)
        val mouth = Path().apply { moveTo(17.4f, 28f); rQuadTo(2.6f, 2.2f, 5.2f, 0f) }
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f; p.strokeCap = Paint.Cap.ROUND; p.color = Color.parseColor("#3b2a3a"); cv.drawPath(mouth, p)
    }
}
