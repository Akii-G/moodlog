package io.github.akiig.moodlog

import android.content.Context
import java.util.Locale

// Texts are written in English in the code, as in the PC and iPhone apps, and translated
// from assets/i18n/<language>.strings (the same format as the iPhone's Localizable.strings,
// so translations can be shared). tools/extract_android_strings.py lists what's missing.

private var catalog: Map<String, String> = emptyMap()
private var locale: Locale = Locale.ENGLISH

fun appLocale(): Locale = locale

/** Call once at start, with the app's language (the phone's, or the one picked for Moodlog). */
fun loadTranslations(context: Context) {
    val current = context.resources.configuration.locales[0]
    val lang = current.language
    catalog = try {
        context.assets.open("i18n/$lang.strings").bufferedReader().use { parseStrings(it.readText()) }
    } catch (e: Exception) {
        emptyMap()
    }
    locale = if (catalog.isEmpty()) Locale.ENGLISH else current
}

/** Reads `"key" = "value";` lines, with \" \n \\ escapes; comments are skipped. */
fun parseStrings(text: String): Map<String, String> {
    val out = HashMap<String, String>()
    val lit = "\"((?:[^\"\\\\]|\\\\.)*)\""
    val re = Regex(lit + "\\s*=\\s*" + lit + "\\s*;")
    for (m in re.findAll(text)) out[unescape(m.groupValues[1])] = unescape(m.groupValues[2])
    return out
}

private fun unescape(s: String): String {
    val b = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (val n = s[i + 1]) {
                'n' -> b.append('\n')
                't' -> b.append('\t')
                else -> b.append(n)
            }
            i += 2
        } else {
            b.append(c); i++
        }
    }
    return b.toString()
}

/** Fills %@ (in order) and %1$@, %2$@ (by position), like the iPhone's String(format:). */
fun fill(format: String, args: List<String>): String {
    val b = StringBuilder()
    var next = 0
    var i = 0
    while (i < format.length) {
        val c = format[i]
        if (c == '%' && i + 1 < format.length) {
            if (format[i + 1] == '@') {
                b.append(args.getOrElse(next++) { "" }); i += 2; continue
            }
            if (format[i + 1] == '%') {
                b.append('%'); i += 2; continue
            }
            val m = Regex("^%(\\d+)\\$@").find(format.substring(i))
            if (m != null) {
                b.append(args.getOrElse(m.groupValues[1].toInt() - 1) { "" })
                i += m.value.length; continue
            }
        }
        b.append(c); i++
    }
    return b.toString()
}

fun tr(text: String, vararg args: Any): String {
    val format = catalog[text] ?: text
    return if (args.isEmpty()) format else fill(format, args.map { it.toString() })
}

/** One/many forms; the count is the first argument (%@ or %1$@). */
fun trn(one: String, many: String, n: Int, vararg args: Any): String {
    val key = if (n == 1) one else many
    val format = catalog[key] ?: key
    return fill(format, listOf(n.toString()) + args.map { it.toString() })
}
