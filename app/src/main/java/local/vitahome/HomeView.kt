package local.vitahome

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Draws the whole home screen on one canvas: Vita-style bubble pages plus a favourites dock. */
class HomeView(context: Context) : View(context) {

    private val cols = 5
    private val rows = 2
    private val perPage = cols * rows

    private var page = 0
    private var idx = 0
    private var inDock = false
    private var dockIdx = 0

    var theme: VitaTheme = VitaTheme.builtin()
    var onLaunch: ((Item) -> Unit)? = null
    var onCycleTheme: (() -> Unit)? = null

    var grid: List<Item> = emptyList()
        set(v) { field = v; fixFocus() }
    var dock: List<Item> = emptyList()
        set(v) { field = v; fixFocus() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val src = Rect()
    private val dst = RectF()
    private val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val disc = 0xE6FFFFFF.toInt()

    private fun pageCount() = max(1, ceil(grid.size / perPage.toFloat()).toInt())
    private fun pageItems() = min(perPage, grid.size - page * perPage).coerceAtLeast(0)

    private fun fixFocus() {
        page = page.coerceIn(0, pageCount() - 1)
        val n = pageItems()
        idx = if (n == 0) 0 else idx.coerceIn(0, n - 1)
        if (dock.isEmpty()) inDock = false else dockIdx = dockIdx.coerceIn(0, dock.size - 1)
        invalidate()
    }

    fun resetFocus() {
        page = 0; idx = 0; inDock = false
        invalidate()
    }

    fun handleKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> activate()
            KeyEvent.KEYCODE_MENU -> onCycleTheme?.invoke()
            KeyEvent.KEYCODE_BACK -> resetFocus()
            else -> return false
        }
        invalidate()
        return true
    }

    private fun activate() {
        val item = if (inDock) dock.getOrNull(dockIdx) else grid.getOrNull(page * perPage + idx)
        if (item != null) onLaunch?.invoke(item)
    }

    private fun move(dx: Int, dy: Int) {
        if (inDock) {
            if (dx != 0) {
                dockIdx = (dockIdx + dx).coerceIn(0, max(0, dock.size - 1))
            } else if (dy < 0) {
                val n = pageItems()
                if (n > 0) {
                    inDock = false
                    val col = (dockIdx * cols / max(1, dock.size)).coerceIn(0, cols - 1)
                    idx = min(n - 1, (rows - 1) * cols + col)
                }
            }
            return
        }
        val n = pageItems()
        if (n == 0) {
            if (dy > 0 && dock.isNotEmpty()) inDock = true
            return
        }
        val row = idx / cols
        val col = idx % cols
        when {
            dx > 0 -> {
                if (col < cols - 1 && idx + 1 < n) idx++
                else if (page < pageCount() - 1) {
                    page++
                    idx = min(row * cols, pageItems() - 1)
                }
            }
            dx < 0 -> {
                if (col > 0) idx--
                else if (page > 0) {
                    page--
                    idx = min(row * cols + cols - 1, pageItems() - 1)
                }
            }
            dy > 0 -> {
                if (row < rows - 1 && idx + cols < n) idx += cols
                else if (dock.isNotEmpty()) {
                    inDock = true
                    dockIdx = (col * dock.size / cols).coerceIn(0, dock.size - 1)
                }
            }
            dy < 0 -> if (row > 0) idx -= cols
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        drawBackground(c, w, h)

        val bg = if (theme.bgs.isEmpty()) null else theme.bgs[page % theme.bgs.size]
        val fontColor = bg?.fontColor ?: WHITE
        val shadow = bg?.shadow ?: true

        drawTopBar(c, w, h)
        drawDots(c, w, h)

        val n = pageItems()
        val colW = w * 0.87f / cols
        for (i in 0 until n) {
            val row = i / cols
            val col = i % cols
            val cx = w * 0.10f + colW * (col + 0.5f)
            val cy = h * (0.26f + 0.34f * row)
            drawBubble(c, grid[page * perPage + i], cx, cy, h * 0.19f,
                !inDock && i == idx, h * 0.032f, colW * 0.95f, fontColor, shadow)
        }
        drawDock(c, w, h, fontColor, shadow)
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float) {
        val bmp = if (theme.bgs.isEmpty()) null else theme.bitmap(theme.bgs[page % theme.bgs.size].image)
        if (bmp == null) {
            p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF1B2A41.toInt(), 0xFF0B1220.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p)
            p.shader = null
        } else {
            val scale = max(w / bmp.width, h / bmp.height)
            val sw = w / scale
            val sh = h / scale
            val sx = (bmp.width - sw) / 2f
            val sy = (bmp.height - sh) / 2f
            src.set(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
            dst.set(0f, 0f, w, h)
            c.drawBitmap(bmp, src, dst, bp)
        }
        p.color = 0x28000000
        c.drawRect(0f, 0f, w, h, p)
    }

    private fun drawTopBar(c: Canvas, w: Float, h: Float) {
        p.color = theme.barColor
        c.drawRect(0f, 0f, w, h * 0.07f, p)
        tp.clearShadowLayer()
        tp.color = theme.indicatorColor
        tp.textAlign = Paint.Align.RIGHT
        tp.textSize = h * 0.034f
        c.drawText(clockFmt.format(System.currentTimeMillis()), w * 0.96f, h * 0.048f, tp)
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = h * 0.028f
        c.drawText(theme.title, w * 0.03f, h * 0.046f, tp)
    }

    private fun drawDots(c: Canvas, w: Float, h: Float) {
        val count = pageCount()
        if (count <= 1) return
        val step = h * 0.045f
        val x = w * 0.04f
        val y0 = h * 0.5f - step * (count - 1) / 2f
        for (i in 0 until count) {
            val cy = y0 + step * i
            val bmp = theme.bitmap(if (i == page) theme.curPage else theme.basePage)
            if (bmp != null) {
                val s = h * 0.026f
                dst.set(x - s / 2, cy - s / 2, x + s / 2, cy + s / 2)
                c.drawBitmap(bmp, null, dst, bp)
            } else {
                p.color = if (i == page) WHITE else 0x88FFFFFF.toInt()
                c.drawCircle(x, cy, h * (if (i == page) 0.009f else 0.006f), p)
            }
        }
    }

    private fun drawDock(c: Canvas, w: Float, h: Float, fontColor: Int, shadow: Boolean) {
        val n = dock.size
        if (n == 0) return
        val d = h * 0.105f
        val step = d * 1.55f
        val total = step * (n - 1)
        val x0 = w / 2f - total / 2f
        val cy = h * 0.855f
        dst.set(x0 - d * 1.05f, h * 0.785f, x0 + total + d * 1.05f, h * 0.965f)
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawRoundRect(dst, h * 0.07f, h * 0.07f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = 0x44FFFFFF
        c.drawRoundRect(dst, h * 0.07f, h * 0.07f, p)
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            drawBubble(c, dock[i], x0 + step * i, cy, d, inDock && i == dockIdx,
                h * 0.022f, step * 0.95f, fontColor, shadow)
        }
    }

    private fun drawBubble(
        c: Canvas, item: Item, cx: Float, cy: Float, d: Float, focused: Boolean,
        labelSize: Float, maxLabelW: Float, fontColor: Int, shadow: Boolean
    ) {
        val r = d / 2f * (if (focused) 1.12f else 1f)
        val art = when (item.kind) {
            Kind.SETTINGS -> theme.icon("settings")
            Kind.THEMES -> theme.icon("camera")
            else -> null
        }
        p.style = Paint.Style.FILL
        if (art != null) {
            dst.set(cx - r, cy - r, cx + r, cy + r)
            c.drawBitmap(art, null, dst, bp)
        } else {
            p.color = 0x55000000
            c.drawCircle(cx, cy + r * 0.06f, r * 1.02f, p)
            p.color = disc
            c.drawCircle(cx, cy, r, p)
            val dr = item.icon
            if (dr != null) {
                val ir = r * 0.78f
                c.save()
                path.reset()
                path.addCircle(cx, cy, r * 0.88f, Path.Direction.CW)
                c.clipPath(path)
                dr.setBounds((cx - ir).toInt(), (cy - ir).toInt(), (cx + ir).toInt(), (cy + ir).toInt())
                dr.draw(c)
                c.restore()
            } else {
                tp.clearShadowLayer()
                tp.textAlign = Paint.Align.CENTER
                tp.color = 0xFF333333.toInt()
                tp.textSize = r * 0.9f
                c.drawText(item.label.take(1).uppercase(), cx, cy + r * 0.3f, tp)
            }
        }
        if (focused) {
            val sw = max(3f, d * 0.035f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = sw
            p.color = WHITE
            c.drawCircle(cx, cy, r + sw / 2f, p)
            p.style = Paint.Style.FILL
        }
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = labelSize
        tp.color = fontColor
        if (shadow) tp.setShadowLayer(labelSize * 0.18f, 0f, labelSize * 0.08f, 0xFF000000.toInt())
        else tp.clearShadowLayer()
        val s = TextUtils.ellipsize(item.label, tp, maxLabelW, TextUtils.TruncateAt.END)
        c.drawText(s, 0, s.length, cx, cy + r + labelSize * 1.35f, tp)
        tp.clearShadowLayer()
    }
}
