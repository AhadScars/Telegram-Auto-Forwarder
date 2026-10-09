package com.shoaib.tgforward.engine

object LinkText {
    data class Entity(
        val offset: Int,
        val length: Int,
        val hiddenUrl: String?,
    )

    data class Rewritten(
        val text: String,
        val entities: List<Entity>,
        val changed: Boolean,
        val failed: Boolean,
    )

    private val urlPattern = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val trail = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

    fun needsShort(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return AmazonLinks.shouldShorten(url)
    }

    fun rewrite(text: String, entities: List<Entity>, shorten: (String) -> String): Rewritten {
        val ranges = ArrayList<Range>()
        for (match in urlPattern.findAll(text)) {
            val clean = match.value.trimEnd(*trail)
            if (!needsShort(clean)) continue
            val start = match.range.first
            ranges.add(Range(start, start + clean.length, clean))
        }
        ranges.sortBy { it.start }

        val cache = HashMap<String, String>()
        var failed = false
        fun short(url: String): String {
            val cached = cache[url]
            if (cached != null) return cached
            val value = try {
                shorten(url).trim()
            } catch (_: Exception) {
                ""
            }
            val ok = (value.startsWith("http://") || value.startsWith("https://")) && value.length < url.length
            if (!ok) failed = true
            val chosen = if (ok) value else url
            cache[url] = chosen
            return chosen
        }

        var hiddenChanged = false
        val withHidden = entities.map { entity ->
            val hidden = entity.hiddenUrl
            if (hidden != null && needsShort(hidden)) {
                val next = short(hidden)
                if (next != hidden) hiddenChanged = true
                entity.copy(hiddenUrl = next)
            } else {
                entity
            }
        }

        if (ranges.isEmpty()) {
            return Rewritten(text, withHidden, hiddenChanged, failed)
        }

        val map = IntArray(text.length + 1)
        val out = StringBuilder()
        var old = 0
        var newPos = 0
        for (range in ranges) {
            if (range.start < old) continue
            while (old < range.start) {
                map[old] = newPos
                out.append(text[old])
                old++
                newPos++
            }
            val replacement = short(range.url)
            repeat(range.end - range.start) { step ->
                map[range.start + step] = newPos
            }
            out.append(replacement)
            newPos += replacement.length
            old = range.end
        }
        while (old < text.length) {
            map[old] = newPos
            out.append(text[old])
            old++
            newPos++
        }
        map[text.length] = newPos

        val shifted = withHidden.map { entity ->
            val start = entity.offset.coerceIn(0, text.length)
            val end = (entity.offset + entity.length).coerceIn(start, text.length)
            entity.copy(offset = map[start], length = map[end] - map[start])
        }
        val newText = out.toString()
        return Rewritten(newText, shifted, hiddenChanged || newText != text, failed)
    }

    private class Range(val start: Int, val end: Int, val url: String)
}
