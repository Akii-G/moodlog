package io.github.akiig.moodlog

import org.json.JSONObject

/**
 * Fixes made once and applied by every Sottovoce, synced with the PC (corrections.json there,
 * class Corrections in src/moodlog, same rules):
 *   nights  {"2026-11-11": stamp}  sleep deleted for good (the watch wasn't worn)
 *   names   {"Aripiprazolo 10mg": {"to": "Aripiprazolo Teva 15 mg", "updated": stamp}}  a medicine's
 *           old name and the one it's shown as; "to": "" undoes it. The newest change wins.
 */
data class Corrections(
    val nights: Map<String, String> = emptyMap(),
    val names: Map<String, Rename> = emptyMap(),
) {
    data class Rename(val to: String, val updated: String)

    fun json(): JSONObject = JSONObject()
        .put("nights", JSONObject(nights.toSortedMap() as Map<*, *>))
        .put("names", JSONObject().also { o ->
            names.toSortedMap().forEach { (k, v) -> o.put(k, JSONObject().put("to", v.to).put("updated", v.updated)) }
        })

    /** These corrections with another device's taken in, and how many changed. */
    fun absorb(other: Corrections): Pair<Corrections, Int> {
        var changes = 0
        val n = nights.toMutableMap()
        for ((day, stamp) in other.nights) {
            if (day !in n && Regex("""\d{4}-\d{2}-\d{2}""").matches(day)) { n[day] = stamp; changes++ }
        }
        val m = names.toMutableMap()
        for ((old, item) in other.names) {
            if (old.isEmpty() || old == item.to) continue
            val mine = m[old]
            if (mine != null && mine.updated >= item.updated) continue
            if (mine?.to != item.to) changes++
            m[old] = item
        }
        return Corrections(n, m) to changes
    }

    val deletedNights: Set<String> get() = nights.keys

    fun deleteNight(day: String): Corrections =
        if (day in nights) this else copy(nights = nights + (day to Stamp.now()))

    /** {old name: name shown}, following a renamed name that was renamed again. */
    fun renames(): Map<String, String> {
        val direct = names.filterValues { it.to.isNotEmpty() }.mapValues { it.value.to }
        val out = HashMap<String, String>()
        for (old in direct.keys) {
            var to = direct.getValue(old)
            val seen = mutableSetOf(old)
            while (to in direct && to !in seen) { seen += to; to = direct.getValue(to) }
            if (to != old) out[old] = to
        }
        return out
    }

    /** Show these names as `to` from now on (and the names already shown as one of them). */
    fun rename(list: Collection<String>, to: String): Corrections {
        val stamp = Stamp.now()
        val m = names.toMutableMap()
        for ((old, item) in names) if (item.to in list && old != to) m[old] = Rename(to, stamp)
        for (name in list) if (name != to) m[name] = Rename(to, stamp)
        if (to in m) m[to] = Rename("", stamp)
        return copy(names = m)
    }

    fun undoRename(to: String): Corrections {
        val stamp = Stamp.now()
        return copy(names = names.mapValues { (_, item) -> if (item.to == to) Rename("", stamp) else item })
    }

    companion object {
        fun from(o: JSONObject?): Corrections? {
            o ?: return null
            val nights = HashMap<String, String>()
            o.optJSONObject("nights")?.let { n -> n.keys().forEach { k -> nights[k] = n.optString(k) } }
            val names = HashMap<String, Rename>()
            o.optJSONObject("names")?.let { n ->
                n.keys().forEach { k ->
                    val item = n.optJSONObject(k) ?: return@forEach
                    val updated = item.optString("updated")
                    if (k.isNotBlank() && updated.isNotEmpty()) names[k] = Rename(item.optString("to"), updated)
                }
            }
            return Corrections(nights, names)
        }

        private val makers = setOf("teva", "mylan", "sandoz", "eg", "doc", "ratiopharm", "zentiva", "aurobindo",
            "accord", "krka", "pensa", "hexal", "almus", "sun", "germed", "dorom", "abc", "aristo", "viatris")

        /** Groups of names that look like one medicine and aren't renamed yet: the same name
         *  before the strength, or the same active ingredient in Dose. */
        fun suggestions(names: Collection<String>, ingredients: Map<String, String>, renames: Map<String, String>): List<List<String>> {
            fun base(name: String): String {
                val words = mutableListOf<String>()
                for (w in name.lowercase().split(Regex("\\s+"))) {
                    if (w.any { it.isDigit() }) break
                    if (w !in makers && w != "·" && w != "-" && w.isNotEmpty()) words += w
                }
                return words.joinToString(" ")
            }
            val parent = names.associateWith { it }.toMutableMap()
            fun find(n: String): String { var x = n; while (parent[x] != x) x = parent.getValue(x); return x }
            val seen = HashMap<String, String>()
            for (n in names) {
                val keys = setOfNotNull(base(n), ingredients[n]?.lowercase()?.split(" ")?.firstOrNull())
                for (key in keys) {
                    if (key.isEmpty()) continue
                    val other = seen[key]
                    if (other != null) parent[find(n)] = find(other) else seen[key] = n
                }
            }
            return names.groupBy { find(it) }.values
                .filter { g -> g.size > 1 && g.map { renames[it] ?: it }.toSet().size > 1 }
                .map { g -> g.sortedBy { it.lowercase() } }
                .sortedBy { it.first().lowercase() }
        }
    }
}

/** Dose's medicines with the new names: a renamed medicine shows once, under its new name. */
fun DoseFeed.renamed(map: Map<String, String>): DoseFeed {
    if (map.isEmpty()) return this
    val meds = LinkedHashMap<String, DoseFeed.Med>()
    for (m in medicines) {
        val name = map[m.name] ?: m.name
        val had = meds[name]
        meds[name] = m.copy(name = name, archived = m.archived && (had?.archived ?: true),
            ingredient = had?.ingredient?.ifEmpty { null } ?: m.ingredient)
    }
    return DoseFeed(meds.values.toList(), doses.map { d -> map[d.name]?.let { d.copy(name = it) } ?: d })
}
