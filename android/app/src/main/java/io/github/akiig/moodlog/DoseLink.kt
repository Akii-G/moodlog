package io.github.akiig.moodlog

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.time.LocalDate

/**
 * Dose on this phone: Sottovoce reads its medicines and doses directly (read only), once you allow
 * it ("read your Dose medicines"). Without it, medication still comes from Dose on the PC through
 * the sync. The same reading as the PC's DoseMedication (src/moodlog).
 */
object DoseLink {
    const val PACKAGE = "io.github.akiig.dose"
    const val PERMISSION = "io.github.akiig.dose.permission.READ_DOSES"
    private const val URI = "content://io.github.akiig.dose.data/dose.json"
    private const val DAYS = 400L     // how far back unmarked doses are filled in

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** What Dose on this phone has, or null (not installed, not allowed, unreadable). */
    fun read(context: Context): DoseFeed? {
        if (!installed(context) || !allowed(context)) return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(URI))?.use { feed(JSONObject(it.readBytes().toString(Charsets.UTF_8))) }
        } catch (e: Exception) { null }
    }

    /** Dose's units, one and many, in the app's language. */
    private fun units() = mapOf("pill" to (tr("pill") to tr("pills")), "capsule" to (tr("capsule") to tr("capsules")),
        "drop" to (tr("drop") to tr("drops")), "ml" to ("ml" to "ml"), "puff" to (tr("puff") to tr("puffs")),
        "sachet" to (tr("sachet") to tr("sachets")), "patch" to (tr("patch") to tr("patches")),
        "injection" to (tr("injection") to tr("injections")), "application" to (tr("application") to tr("applications")))

    /** 1.5, "pill" -> "1½ pills". */
    fun amountText(amount: Double, unit: String): String {
        val (one, many) = units()[unit] ?: (unit to unit)
        val whole = amount.toLong()
        val frac = Math.round((amount - whole) * 100) / 100.0
        val glyph = mapOf(0.25 to "¼", 0.5 to "½", 0.75 to "¾")[frac]
        val num = when {
            frac == 0.0 -> whole.toString()
            glyph != null && unit != "ml" -> (if (whole > 0) whole.toString() else "") + glyph
            else -> amount.toString().trimEnd('0').trimEnd('.').replace('.', if (appLocale().language == "it") ',' else '.')
        }
        return "$num ${if (amount <= 1) one else many}"
    }

    private fun dueOn(s: JSONObject, day: LocalDate): Boolean {
        val start = try { LocalDate.parse(s.optString("start")) } catch (e: Exception) { null }
        val end = try { LocalDate.parse(s.optString("end")) } catch (e: Exception) { null }
        val kind = s.optString("kind", "daily")
        if (kind == "needed" || (start != null && day < start) || (end != null && day > end)) return false
        return when (kind) {
            "weekdays" -> s.optJSONArray("weekdays")?.let { a -> (0 until a.length()).any { a.optInt(it) == day.dayOfWeek.value } } ?: false
            "every" -> {
                val every = maxOf(s.optInt("every", 1), 1)
                java.time.temporal.ChronoUnit.DAYS.between(start ?: day, day) % every == 0L
            }
            else -> true
        }
    }

    /** Dose's file as the feed the day view and the report use. */
    fun feed(doc: JSONObject, today: LocalDate = LocalDate.now()): DoseFeed {
        val medsArr = doc.optJSONArray("medicines")
        val meds = (0 until (medsArr?.length() ?: 0)).mapNotNull { medsArr!!.optJSONObject(it) }
            .filter { it.optString("id").isNotEmpty() && it.optString("name").isNotEmpty() }.associateBy { it.optString("id") }
        fun title(m: JSONObject) = m.optString("name") + m.optString("strength").let { if (it.isEmpty()) "" else " $it" }
        val items = ArrayList<DoseFeed.Item>()
        val marked = HashSet<Triple<String, String, String>>()
        val dosesArr = doc.optJSONArray("doses")
        for (i in 0 until (dosesArr?.length() ?: 0)) {
            val d = dosesArr!!.optJSONObject(i) ?: continue
            val m = meds[d.optString("med")] ?: continue
            val status = d.optString("status")
            val day = d.optString("day")
            if (status !in setOf("taken", "skipped") || day.length != 10) continue
            marked.add(Triple(d.optString("med"), day, d.optString("time")))
            val at = d.optString("at"); val time = d.optString("time")
            val whenText = if (status == "taken" && at.isNotEmpty()) at else time.ifEmpty { at }
            items.add(DoseFeed.Item(day, whenText, status, amountText(d.optDouble("amount", 1.0), m.optString("unit", "pill")), title(m)))
        }
        // scheduled doses nobody marked, up to today: not taken, or yet to take
        for ((id, m) in meds) {
            val s = m.optJSONObject("schedule") ?: continue
            if (m.optBoolean("archived") || s.optString("kind") == "needed") continue
            val start = try { LocalDate.parse(s.optString("start")) } catch (e: Exception) { today }
            var day = maxOf(today.minusDays(DAYS), start)
            val times = s.optJSONArray("times")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val own = s.optJSONObject("amounts")
            while (!day.isAfter(today)) {
                if (dueOn(s, day)) for (t in times) {
                    if (Triple(id, day.toString(), t) in marked) continue
                    val amount = own?.optDouble(t, Double.NaN)?.takeIf { !it.isNaN() } ?: m.optDouble("amount", 1.0)
                    items.add(DoseFeed.Item(day.toString(), t, "pending", amountText(amount, m.optString("unit", "pill")), title(m)))
                }
                day = day.plusDays(1)
            }
        }
        items.sortWith(compareBy({ it.date }, { it.time }, { it.name }))
        val list = meds.values.map { DoseFeed.Med(title(it), it.optBoolean("archived"),
            it.optJSONObject("schedule")?.optString("kind") == "needed") }.sortedBy { it.name }
        return DoseFeed(list, items)
    }
}
