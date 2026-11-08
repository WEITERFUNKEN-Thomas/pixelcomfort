package com.noirdraco.pixelcomfort

import android.content.Context

/** Anzeigename einer App, oder der Paketname, falls sie nicht (mehr) installiert ist. */
internal fun Context.appLabel(pkg: String): String =
    runCatching { packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString() }
        .getOrDefault(pkg)
