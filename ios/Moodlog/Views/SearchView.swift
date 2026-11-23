import SwiftUI

/// Opened from the magnifying glass in the diary: every note to read back, or a search through
/// notes, feeling words and events. A result opens its day.
struct SearchRoute: Hashable {}

struct SearchView: View {
    @Environment(AppStore.self) private var store
    @State private var query = ""

    var body: some View {
        let hits = Search.results(store, query)
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        List {
            if hits.isEmpty {
                Text(q.isEmpty ? tr("No notes yet. Add a few words when you log a mood and they will show up here.")
                               : tr("Nothing matches “%@”.", q))
                    .foregroundStyle(.secondary)
            }
            ForEach(hits.prefix(300)) { hit in
                NavigationLink(value: hit.date) {
                    SearchRow(hit: hit, query: q)
                }
            }
        }
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always),
                    prompt: Text("Search notes, feelings and events"))
        .navigationTitle("Search")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct SearchRow: View {
    let hit: Search.Hit
    let query: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            switch hit {
            case .entry(let e):
                if e.mood > 0 {
                    MoodBadge(mood: e.mood, size: 30)
                } else {
                    Color.clear.frame(width: 30, height: 30)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text([Day.pretty(e.date, style: "EEEE d MMMM yyyy"), e.time, e.mood > 0 ? Mood.name(e.mood) : ""]
                            .filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(.secondary)
                    if !e.note.isEmpty {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            if e.hidden && Prefs.privateTick {
                                Image(systemName: "lock.fill").font(.caption2).foregroundStyle(.secondary)
                            }
                            Text(Search.marked(e.note, query)).lineLimit(6)
                        }
                    }
                    let words = Feeling.text(e.feelings ?? [])
                    if !words.isEmpty && (e.note.isEmpty || Search.matches(words, query)) {
                        Text(Search.marked(words, query)).font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            case .event(let ev):
                Image(systemName: "flag.fill")
                    .foregroundStyle(.secondary)
                    .frame(width: 30, height: 30)
                VStack(alignment: .leading, spacing: 3) {
                    Text(Day.pretty(ev.date, style: "EEEE d MMMM yyyy") + " · " + EventKind.label(for: ev.kind))
                        .font(.caption).foregroundStyle(.secondary)
                    Text(Search.marked(ev.title, query))
                    if !ev.note.isEmpty {
                        Text(Search.marked(ev.note, query)).font(.subheadline).foregroundStyle(.secondary).lineLimit(4)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
    }
}
