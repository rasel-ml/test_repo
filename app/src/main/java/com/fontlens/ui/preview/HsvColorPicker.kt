package com.fontlens.ui.preview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

object HsvColorPicker {

    @SuppressLint("ClickableViewAccessibility")
    fun show(ctx: Context, title: String, initial: Int, onPick: (Int) -> Unit) {
        val dp = ctx.resources.displayMetrics.density

        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        var hue   = hsv[0]
        var sat   = hsv[1]
        var value = hsv[2]

        fun currentColor() = Color.HSVToColor(floatArrayOf(hue, sat, value))
        fun colorHex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

        var svRebuildRef: (() -> Unit)?                               = null
        var hueViewRef:   View?                                       = null
        var currBoxRef:   View?                                       = null
        var currEditRef:  android.widget.EditText?                    = null
        var currBgRef:    android.graphics.drawable.GradientDrawable? = null
        var suppressSync  = false

        val updateAll: () -> Unit = {
            val color = currentColor()
            currBgRef?.setColor(color)
            currBoxRef?.invalidate()
            if (!suppressSync) {
                currEditRef?.let {
                    suppressSync = true
                    it.setText(colorHex(color))
                    it.setSelection(it.text.length)
                    suppressSync = false
                }
            }
            svRebuildRef?.invoke()
            hueViewRef?.invalidate()
        }

        val squareSz = (220 * dp).toInt()
        val pad    = (12 * dp).toInt()
        val hueW   = (22 * dp).toInt()
        val hueGap = (8  * dp).toInt()
        val corner = 6f * dp

        // ── SV Square ─────────────────────────────────────────────────────
        val svView = object : View(ctx) {
            var bmp: Bitmap? = null
            fun rebuild() {
                if (width < 1 || height < 1) return
                val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                    Color.WHITE, hueColor, Shader.TileMode.CLAMP)
                paint.xfermode = null
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
                paint.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.xfermode = null; paint.shader = null
                bmp = b; invalidate()
            }
            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = rebuild()
            override fun onDraw(canvas: Canvas) {
                bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                val cx = sat * width; val cy = (1f - value) * height
                val p = Paint(Paint.ANTI_ALIAS_FLAG); p.style = Paint.Style.STROKE
                p.strokeWidth = 3f * dp; p.color = Color.WHITE
                canvas.drawCircle(cx, cy, 9f * dp, p)
                p.strokeWidth = 1.5f * dp; p.color = Color.BLACK
                canvas.drawCircle(cx, cy, 9f * dp, p)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                    sat   = (e.x / width).coerceIn(0f, 1f)
                    value = 1f - (e.y / height).coerceIn(0f, 1f)
                    updateAll()
                }
                return true
            }
        }.apply { layoutParams = android.widget.LinearLayout.LayoutParams(squareSz, squareSz) }
        svRebuildRef = svView::rebuild

        // ── Hue bar ───────────────────────────────────────────────────────
        val hueView = object : View(ctx) {
            var bmp: Bitmap? = null
            fun rebuild() {
                if (width < 1 || height < 1) return
                val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                val colors = IntArray(361) { i -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f)) }
                val paint = Paint()
                paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), colors, null, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), corner, corner, paint)
                bmp = b; invalidate()
            }
            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = rebuild()
            override fun onDraw(canvas: Canvas) {
                bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                val y = (hue / 360f * height).coerceIn(0f, height.toFloat())
                val th = 5f * dp
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.style = Paint.Style.FILL; p.color = Color.WHITE
                canvas.drawRoundRect(RectF(1f*dp, y-th, width-1f*dp, y+th), 2f*dp, 2f*dp, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1f*dp; p.color = Color.argb(100,0,0,0)
                canvas.drawRoundRect(RectF(1f*dp, y-th, width-1f*dp, y+th), 2f*dp, 2f*dp, p)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                    hue = ((e.y / height) * 360f).coerceIn(0f, 360f)
                    svView.rebuild(); invalidate(); updateAll()
                }
                return true
            }
        }.apply { layoutParams = android.widget.LinearLayout.LayoutParams(hueW, squareSz) }
        hueViewRef = hueView

        val pickerRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(svView)
            addView(View(ctx).apply { layoutParams = android.widget.LinearLayout.LayoutParams(hueGap, 1) })
            addView(hueView)
        }

        // ── Preview boxes ─────────────────────────────────────────────────
        val boxPadH      = (10 * dp).toInt()
        val boxPadV      = (6  * dp).toInt()
        val hexSizeSp    = 12f
        val hexSizePx    = hexSizeSp * dp

        val prevBox = object : View(ctx) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = hexSizePx; typeface = android.graphics.Typeface.MONOSPACE
                textAlign = Paint.Align.CENTER
            }
            override fun onMeasure(wSpec: Int, hSpec: Int) {
                val tw = tp.measureText(colorHex(initial)).toInt() + boxPadH * 2
                val th = (-tp.ascent() + tp.descent()).toInt() + boxPadV * 2
                setMeasuredDimension(tw, th)
            }
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = initial; p.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(0f,0f,w,h), corner, corner, p)
                p.color = Color.argb(140,128,128,128); p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f*dp
                canvas.drawRoundRect(RectF(0.6f*dp,0.6f*dp,w-0.6f*dp,h-0.6f*dp), corner, corner, p)
                val hex = colorHex(initial); val tx = w/2f
                val ty = h/2f - (tp.ascent()+tp.descent())/2f
                tp.color = Color.argb(150,0,0,0); canvas.drawText(hex, tx+1f*dp, ty+1f*dp, tp)
                tp.color = Color.WHITE; canvas.drawText(hex, tx, ty, tp)
            }
        }.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val arrowTv = android.widget.TextView(ctx).apply {
            text = "→"; textSize = 14f; setTextColor(Color.argb(160,80,80,80))
            gravity = android.view.Gravity.CENTER
            setPadding((6*dp).toInt(),0,(6*dp).toInt(),0)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val currBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = corner; setColor(currentColor())
            setStroke((1.2f*dp).toInt(), Color.argb(140,128,128,128))
        }
        currBgRef = currBg

        val currEdit = android.widget.EditText(ctx).apply {
            setText(colorHex(initial)); textSize = hexSizeSp
            setTypeface(android.graphics.Typeface.MONOSPACE)
            gravity = android.view.Gravity.CENTER; setTextColor(Color.WHITE)
            setShadowLayer(1.5f*dp, 0.8f*dp, 0.8f*dp, Color.argb(160,0,0,0))
            background = currBg; maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters   = arrayOf(android.text.InputFilter.LengthFilter(7))
            setPadding(boxPadH, boxPadV, boxPadH, boxPadV)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        currBoxRef = currEdit; currEditRef = currEdit

        currEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressSync) return
                val str = s.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                if (str.length == 7) {
                    try {
                        val parsed = Color.parseColor(str)
                        val h = FloatArray(3); Color.colorToHSV(parsed, h)
                        hue=h[0]; sat=h[1]; value=h[2]
                        suppressSync = true
                        currBgRef?.setColor(currentColor())
                        svView.rebuild(); hueView.invalidate(); currEdit.invalidate()
                        suppressSync = false
                    } catch (_: Exception) {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        val previewRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(prevBox); addView(arrowTv); addView(currEdit)
        }

        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(pickerRow); addView(previewRow)
        }

        android.app.AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Apply") { _, _ -> onPick(currentColor()) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
