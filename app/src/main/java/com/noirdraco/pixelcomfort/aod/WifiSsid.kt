package com.noirdraco.pixelcomfort.aod

object WifiSsid {
    private const val UNKNOWN = "<unknown ssid>"
    private const val MAX_BYTES = 32

    /** WifiInfo reports the SSID in quotes, or a placeholder when it is not readable. */
    fun clean(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == UNKNOWN) return null
        val unquoted = if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value.substring(1, value.length - 1)
        } else value
        return unquoted.ifEmpty { null }
    }

    fun isValid(ssid: String): Boolean = ssid.isNotBlank() && ssid.encodeToByteArray().size <= MAX_BYTES
}
