package local.vitahome

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import java.io.File

class HomeActivity : Activity() {

    private lateinit var view: HomeView
    private lateinit var store: ThemeStore
    private val ui = Handler(Looper.getMainLooper())
    private val iconCache = HashMap<String, Drawable>()

    // Dock apps, most used first. Not-installed ones are skipped.
    // Override by putting one package name per line in <app files dir>/favourites.txt
    private val defaultFavourites = listOf(
        "com.google.android.youtube.tv",   // YouTube
        "org.jellyfin.androidtv",          // Jellyfin
        "com.tailscale.ipn",               // Tailscale
        "com.limelight",                   // Moonlight
        "com.stremio.one",                 // Stremio
        "au.net.abc.iview",                // ABC iview
        "com.sbs.ondemand.tv",             // SBS On Demand
        "tv.twitch.android.app",           // Twitch
        "au.com.kayosports.tv"             // Kayo
    )

    // Stock Google TV launcher packages are hidden from the grid (that's where Gemini lives).
    private val hidden = setOf("com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher")

    private val tick = object : Runnable {
        override fun run() {
            view.invalidate()
            ui.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeDir = getExternalFilesDir("themes") ?: File(filesDir, "themes")
        store = ThemeStore(themeDir, getSharedPreferences("vita", MODE_PRIVATE))
        view = HomeView(this)
        view.onLaunch = { launch(it) }
        view.onCycleTheme = { cycleTheme() }
        view.theme = store.current()
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        reload()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        view.resetFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        view.handleKey(keyCode) || super.onKeyDown(keyCode, event)

    private fun cycleTheme() {
        view.theme = store.next()
        view.invalidate()
    }

    private fun favourites(): List<String> {
        val f = File(getExternalFilesDir(null) ?: filesDir, "favourites.txt")
        if (f.isFile) {
            val l = f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            if (l.isNotEmpty()) return l
        }
        return defaultFavourites
    }

    private fun reload() {
        val pm = packageManager
        val seen = HashSet<String>()
        val apps = ArrayList<Item>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || pkg in hidden || !seen.add(pkg)) continue
                val icon = iconCache.getOrPut(pkg) { ri.loadIcon(pm) }
                apps.add(Item(ri.loadLabel(pm).toString(), pkg, Kind.APP, icon))
            }
        }
        apps.sortBy { it.label.lowercase() }
        val byPkg = apps.associateBy { it.pkg }
        view.dock = favourites().mapNotNull { byPkg[it] }.take(9)
        view.grid = listOf(
            Item("Settings", null, Kind.SETTINGS, null),
            Item("Themes", null, Kind.THEMES, null)
        ) + apps
    }

    private fun launch(item: Item) {
        when (item.kind) {
            Kind.SETTINGS -> start(Intent(Settings.ACTION_SETTINGS))
            Kind.THEMES -> cycleTheme()
            Kind.APP -> {
                val pkg = item.pkg ?: return
                val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                    ?: packageManager.getLaunchIntentForPackage(pkg)
                if (i != null) start(i)
            }
        }
    }

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (t: Throwable) {
            Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show()
        }
    }
}
