package local.vitahome

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

val WHITE: Int = 0xFFFFFFFF.toInt()

class BgEntry(val image: String, val fontColor: Int, val shadow: Boolean)

/** A PS Vita theme pack (.zip with theme.xml + png files), read straight from the zip. */
class VitaTheme(
    val title: String,
    private val zip: ZipFile?,
    private val names: Map<String, String>,
    val bgs: List<BgEntry>,
    val barColor: Int,
    val indicatorColor: Int,
    val basePage: String?,
    val curPage: String?,
    val icons: Map<String, String>
) {
    private val cache = LruCache<String, Bitmap>(24)

    fun bitmap(file: String?): Bitmap? {
        if (file == null || zip == null) return null
        val real = names[file.trim().lowercase()] ?: return null
        cache.get(real)?.let { return it }
        return try {
            val entry = zip.getEntry(real) ?: return null
            val bmp = zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
            if (bmp != null) cache.put(real, bmp)
            bmp
        } catch (t: Throwable) {
            null
        }
    }

    fun icon(slot: String): Bitmap? = bitmap(icons[slot])

    fun close() {
        try { zip?.close() } catch (t: Throwable) { }
    }

    companion object {
        private val ICON_TAGS = mapOf(
            "browser" to "m_browser", "calendar" to "m_calendar", "camera" to "m_camera",
            "email" to "m_email", "friend" to "m_friend", "collabo" to "m_hostCollabo",
            "message" to "m_message", "music" to "m_music", "near" to "m_near",
            "parental" to "m_parental", "party" to "m_party", "ps3" to "m_ps3Link",
            "ps4" to "m_ps4Link", "power" to "m_power", "settings" to "m_settings",
            "trophy" to "m_trophy", "video" to "m_video"
        )

        fun builtin() = VitaTheme(
            "Built-in", null, emptyMap(), emptyList(),
            0xCC101010.toInt(), WHITE, null, null, emptyMap()
        )

        fun load(file: File): VitaTheme? = try { parse(file) } catch (t: Throwable) { null }

        private fun color(s: String?, def: Int): Int =
            try { java.lang.Long.parseLong(s!!.trim(), 16).toInt() } catch (t: Throwable) { def }

        private fun parse(file: File): VitaTheme {
            val zip = ZipFile(file)
            try {
                val all = zip.entries().asSequence().map { it.name }.toList()
                val xmlName = all.firstOrNull { it.lowercase().endsWith("theme.xml") }
                    ?: error("no theme.xml")
                val prefix = xmlName.substring(0, xmlName.length - "theme.xml".length)
                val names = HashMap<String, String>()
                for (n in all) if (n.startsWith(prefix)) names[n.substring(prefix.length).lowercase()] = n

                var xml = zip.getInputStream(zip.getEntry(xmlName)).use { it.readBytes().toString(Charsets.UTF_8) }
                // Real-world packs have junk before the root tag, stray backticks, bare ampersands.
                val start = Regex("<theme[\\s>]").find(xml)?.range?.first ?: error("no <theme>")
                xml = xml.substring(start).replace("`", "")
                    .replace(Regex("&(?!(amp|lt|gt|quot|apos|#\\d+);)"), "&amp;")

                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(InputSource(StringReader(xml)))
                val root = doc.documentElement

                fun el(p: Element?, tag: String): Element? =
                    p?.getElementsByTagName(tag)?.item(0) as? Element
                fun txt(p: Element?, tag: String): String? =
                    el(p, tag)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

                val info = el(root, "InfomationProperty")
                val title = txt(el(info, "m_title"), "m_default") ?: file.nameWithoutExtension
                val bar = el(root, "InfomationBarProperty")
                val home = el(root, "HomeProperty")

                val bgs = ArrayList<BgEntry>()
                val bl = root.getElementsByTagName("BackgroundParam")
                for (i in 0 until bl.length) {
                    val b = bl.item(i) as Element
                    val img = txt(b, "m_imageFilePath") ?: continue
                    bgs.add(BgEntry(img, color(txt(b, "m_fontColor"), WHITE), txt(b, "m_fontShadow") != "0"))
                }

                val icons = HashMap<String, String>()
                for ((slot, tag) in ICON_TAGS) {
                    txt(el(home, tag), "m_iconFilePath")?.let { icons[slot] = it }
                }

                return VitaTheme(
                    title, zip, names, bgs,
                    color(txt(bar, "m_barColor"), 0xFF101010.toInt()),
                    color(txt(bar, "m_indicatorColor"), WHITE),
                    txt(home, "m_basePageFilePath"), txt(home, "m_curPageFilePath"), icons
                )
            } catch (t: Throwable) {
                try { zip.close() } catch (u: Throwable) { }
                throw t
            }
        }
    }
}

class ThemeStore(private val dir: File, private val prefs: SharedPreferences) {
    private var active: VitaTheme? = null

    fun files(): List<File> {
        dir.mkdirs()
        return dir.listFiles { f -> f.isFile && f.extension.equals("zip", true) }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun current(): VitaTheme = switchTo(prefs.getString("theme", null))

    fun next(): VitaTheme {
        val fs = files()
        if (fs.isEmpty()) return switchTo(null)
        val i = fs.indexOfFirst { it.name == prefs.getString("theme", null) }
        return switchTo(fs[(i + 1) % fs.size].name)
    }

    private fun switchTo(name: String?): VitaTheme {
        val fs = files()
        val ordered = if (name == null) fs else fs.filter { it.name == name } + fs.filter { it.name != name }
        for (f in ordered) {
            val t = VitaTheme.load(f)
            if (t != null) {
                active?.close()
                active = t
                prefs.edit().putString("theme", f.name).apply()
                return t
            }
        }
        active?.close()
        val b = VitaTheme.builtin()
        active = b
        return b
    }
}
