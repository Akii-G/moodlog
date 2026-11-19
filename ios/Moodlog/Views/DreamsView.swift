import SwiftUI

/// The dream journal: a summary of the last 30 days, a search, and every dream, newest first.
struct DreamsView: View {
    @Environment(AppStore.self) private var store
    @State private var editing: Dream?
    @State private var adding = false
    @State private var query = ""

    var body: some View {
        let shown = store.dreams
            .filter { d in
                query.isEmpty || d.text.localizedCaseInsensitiveContains(query)
                    || d.details.localizedCaseInsensitiveContains(query)
            }
            .sorted { ($0.date, $0.updated) > ($1.date, $1.updated) }
        NavigationStack {
            List {
                if store.dreams.isEmpty {
                    Text("A dream journal. Each dream belongs to the morning you woke up and shows as a moon in the calendar. Write it down soon after waking: dreams fade fast.")
                        .foregroundStyle(.secondary)
                } else if let summary = store.dreamSummary(from: Day.adding(-29, to: Day.today), to: Day.today) {
                    Section("Last 30 days") {
                        Text(summary).font(.subheadline)
                    }
                }
                Section {
                    ForEach(shown) { dream in
                        Button {
                            editing = dream
                        } label: {
                            DreamRow(dream: dream)
                        }
                        .buttonStyle(.plain)
                    }
                    .onDelete { offsets in
                        for i in offsets { store.delete(shown[i]) }
                    }
                }
            }
            .searchable(text: $query, prompt: Text("Search your dreams"))
            .navigationTitle("Dreams")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        adding = true
                    } label: {
                        Label("Add a dream", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $adding) {
                DreamEditor(original: nil, day: Day.today)
            }
            .sheet(item: $editing) { dream in
                DreamEditor(original: dream, day: dream.date)
            }
        }
    }
}

struct DreamRow: View {
    let dream: Dream

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: dream.isNightmare ? "moon.fill" : "moon")
                .foregroundStyle(.secondary)
                .frame(width: 22)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                Text([Day.pretty(dream.date, style: "EEEE d MMMM yyyy"), dream.details]
                        .filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary)
                if !dream.text.isEmpty {
                    Text(dream.text).lineLimit(4)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }
}

struct DreamEditor: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    let original: Dream?
    @State private var date: Date
    @State private var feeling: String
    @State private var tags: Set<String>
    @State private var text: String

    init(original: Dream?, day: String) {
        self.original = original
        _date = State(initialValue: Day.date(original?.date ?? day) ?? Date())
        _feeling = State(initialValue: original?.feeling ?? "")
        _tags = State(initialValue: Set(original?.tags ?? []))
        _text = State(initialValue: original?.text ?? "")
    }

    private var isEmpty: Bool {
        return feeling.isEmpty && tags.isEmpty && text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Morning of", selection: $date, in: ...Date(), displayedComponents: .date)
                    Picker("How it felt", selection: $feeling) {
                        Text("Not set").tag("")
                        ForEach(DreamFeeling.allCases) { f in
                            Text(f.label).tag(f.rawValue)
                        }
                    }
                }
                Section("What did you dream?") {
                    TextField("A few words, or as much as you remember", text: $text, axis: .vertical)
                        .lineLimit(4...16)
                }
                Section {
                    ForEach(DreamTag.allCases) { tag in
                        Toggle(tag.label, isOn: Binding(
                            get: { tags.contains(tag.rawValue) },
                            set: { on in
                                if on { tags.insert(tag.rawValue) } else { tags.remove(tag.rawValue) }
                            }))
                    }
                } footer: {
                    Text("Didn't remember a dream? Turn that on: it counts too.")
                }
                if let original = original {
                    Section {
                        Button("Delete this dream", role: .destructive) {
                            store.delete(original)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(tr(original == nil ? "Add a dream" : "Edit dream"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(isEmpty)
                }
            }
        }
    }

    private func save() {
        let ordered = DreamTag.allCases.map { $0.rawValue }.filter { tags.contains($0) }
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if var dream = original {
            dream.date = Day.string(date)
            dream.feeling = feeling
            dream.tags = ordered
            dream.text = clean
            store.update(dream)
        } else {
            store.addDream(date: Day.string(date), feeling: feeling, tags: ordered, text: clean)
        }
        dismiss()
    }
}
