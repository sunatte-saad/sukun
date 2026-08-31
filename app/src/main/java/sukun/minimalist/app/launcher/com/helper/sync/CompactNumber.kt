package sukun.minimalist.app.launcher.com.helper.sync

/** Encodes integers compactly: 450 → "450", 12000 → "12k", 10500 → "10.5k". */
object CompactNumber {

    fun encode(value: Int): String {
        if (value < 10_000) return value.toString()
        val thousands = value / 1000f
        return if (thousands == thousands.toInt().toFloat()) {
            "${thousands.toInt()}k"
        } else {
            val rounded = (thousands * 10).toInt() / 10f
            "${rounded}k"
        }
    }

    fun decode(raw: String): Int {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0
        return if (trimmed.endsWith('k', ignoreCase = true)) {
            val num = trimmed.dropLast(1).toFloatOrNull() ?: return 0
            (num * 1000).toInt()
        } else {
            trimmed.toIntOrNull() ?: 0
        }
    }
}
