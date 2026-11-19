import SwiftUI

struct EventsView: View {
    @Environment(AppStore.self) private var store
    @State private var editing: MoodEvent?
    @State private var adding = false

    var body: some View {
        let events = store.events.sorted { $0.date > $1.date }
        NavigationStack {
            List {
                if events.isEmpty {
                    Text("Log things that can affect how you feel, such as a medication change. They show as a flag in the calendar, and each one compares your mood in the two weeks before and after it.")
                        .foregroundStyle(.secondary)
                }
                ForEach(events) { event in
                    Button {
                        editing = event
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(Day.pretty(event.date, style: "EEEE d MMMM yyyy") + " · " + EventKind.label(for: event.kind))
                                .font(.caption).foregroundStyle(.secondary)
                            Text(event.title).font(.headline)
                            if !event.note.isEmpty {
                                Text(event.note).font(.subheadline)
                            }
                            Text(summary(event)).font(.caption).foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 2)
                    }
                    .buttonStyle(.plain)
                }
                .onDelete { offsets in
                    for i in offsets { store.delete(events[i]) }
                }
            }
            .navigationTitle("Events")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        adding = true
                    } label: {
                        Label("Add event", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $adding) {
                EventEditor(original: nil, day: Day.today)
            }
            .sheet(item: $editing) { event in
                EventEditor(original: event, day: event.date)
            }
        }
    }

    private func summary(_ event: MoodEvent) -> String {
        if event.date > Day.today {
            return "Planned. Your mood before and after will be compared once it has happened."
        }
        let around = store.moodAround(event.date)
        func part(_ value: (Double, Int)) -> String {
            return value.1 < 3 ? "not enough days logged (\(value.1))" : String(format: "%.1f", value.0) + " (\(value.1) days)"
        }
        var text = "Mood in the 2 weeks before: \(part(around.before)) · after: \(part(around.after))"
        if !around.complete { text += ", so far" }
        return text
    }
}

struct EventEditor: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    let original: MoodEvent?
    @State private var date: Date
    @State private var kind: String
    @State private var title: String
    @State private var note: String

    init(original: MoodEvent?, day: String) {
        self.original = original
        _date = State(initialValue: Day.date(original?.date ?? day) ?? Date())
        _kind = State(initialValue: original?.kind ?? EventKind.medication.rawValue)
        _title = State(initialValue: original?.title ?? "")
        _note = State(initialValue: original?.note ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Date", selection: $date, displayedComponents: .date)
                    Picker("Type", selection: $kind) {
                        ForEach(EventKind.allCases) { k in
                            Text(k.label).tag(k.rawValue)
                        }
                    }
                    TextField("What happened, e.g. new dose", text: $title)
                }
                Section("Details (optional)") {
                    TextField("Details", text: $note, axis: .vertical).lineLimit(2...8)
                }
                if let original = original {
                    Section {
                        Button("Delete this event", role: .destructive) {
                            store.delete(original)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(original == nil ? "Add event" : "Edit event")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func save() {
        let cleanTitle = title.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        if var event = original {
            event.date = Day.string(date)
            event.kind = kind
            event.title = cleanTitle
            event.note = note
            store.update(event)
        } else {
            store.addEvent(date: Day.string(date), kind: kind, title: cleanTitle, note: note)
        }
        dismiss()
    }
}
