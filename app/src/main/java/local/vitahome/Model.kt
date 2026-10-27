package local.vitahome

import android.graphics.drawable.Drawable

enum class Kind { APP, SETTINGS, THEMES }

class Item(val label: String, val pkg: String?, val kind: Kind, val icon: Drawable?)
