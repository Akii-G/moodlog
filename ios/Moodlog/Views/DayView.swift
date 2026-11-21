import SwiftUI

struct DayView: View {
    @Environment(AppStore.self) private var store
    let day: String
    var startAdding = false

    @State private var editing: Entry?
    @State private var adding = false
    @State private var editingEvent: MoodEvent?
    @State private var addingEvent = false
    @State private var editingDream: Dream?
    @State private var addingDream = false
    @State private var doses: [DoseRecord] = []

    var body: some View {
        let entries = store.entries(on: day)
        let events = store.events(on: day)
        let dreams = store.dreams(on: day)
        List {
            Section {
                if entries.isEmpty {
                    Text(tr(day == Day.today ? "Nothing logged yet today." : "Nothing logged on this day."))
                        .foregroundStyle(.secondary)
                }
                ForEach(entries) { entry in
                    Button {
                        editing = entry
                    } label: {
                        EntryRow(entry: entry)
                    }
                    .buttonStyle(.plain)
                }
                .onDelete { offsets in
                    for i in offsets { store.delete(entries[i]) }
                }
                Button {
                    adding = true
                } label: {
                    Label(tr(day == Day.today ? "How do you feel now?" : "Add a mood"), systemImage: "plus.circle.fill")
                }
            } header: {
                if let avg = store.average(on: day), entries.count > 1 {
                    Text(tr("Average %@ · %@", String(format: "%.1f", avg), Mood.name(Mood.level(avg))))
                }
            }
            Section("Events") {
                ForEach(events) { event in
                    Button {
                        editingEvent = event
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(EventKind.label(for: event.kind)).font(.caption).foregroundStyle(.secondary)
                            Text(event.title)
                        }
                    }
                    .buttonStyle(.plain)
                }
                .onDelete { offsets in
                    for i in offsets { store.delete(events[i]) }
                }
                Button {
                    addingEvent = true
                } label: {
                    Label("Add an event, e.g. a medication change", systemImage: "flag")
                }
            }
            if Medications.isOn {
                Section {
                    if doses.isEmpty {
                        Text("No doses logged in Apple Health on this day.").foregroundStyle(.secondary)
                    }
                    ForEach(doses.sorted { ($0.clock, $0.name) < ($1.clock, $1.name) }) { dose in
                        let colour: Color = dose.status == "taken" ? .green : (dose.status == "skipped" ? .gray : .orange)
                        HStack(spacing: 12) {
                            Image(systemName: dose.liquid ? "drop.fill" : "pills.fill")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 30, height: 30)
                                .background(Circle().fill(colour))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(dose.name).lineLimit(1)
                                Text([dose.clock, dose.amountText].filter { !$0.isEmpty }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 4)
                            Text(dose.statusText)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(dose.status == "skipped" ? Color.secondary : colour)
                        }
                    }
                } header: {
                    Text("Medication")
                } footer: {
                    Text("From the Health app.")
                }
            }
            Section("Dreams") {
                ForEach(dreams) { dream in
                    Button {
                        editingDream = dream
                    } label: {
                        DreamRow(dream: dream)
                    }
                    .buttonStyle(.plain)
                }
                .onDelete { offsets in
                    for i in offsets { store.delete(dreams[i]) }
                }
                Button {
                    addingDream = true
                } label: {
                    Label("Add a dream from this night", systemImage: "moon.stars")
                }
            }
        }
        .navigationTitle(day == Day.today ? tr("Today") : Day.pretty(day, style: "EEE d MMM"))
        .onAppear { if startAdding { adding = true } }
        .task(id: day) { doses = await Medications.doses(on: day) }
        .sheet(isPresented: $adding) {
            EntryEditor(day: day, original: nil)
        }
        .sheet(item: $editing) { entry in
            EntryEditor(day: day, original: entry)
        }
        .sheet(isPresented: $addingEvent) {
            EventEditor(original: nil, day: day)
        }
        .sheet(item: $editingEvent) { event in
            EventEditor(original: event, day: event.date)
        }
        .sheet(isPresented: $addingDream) {
            DreamEditor(original: nil, day: day)
        }
        .sheet(item: $editingDream) { dream in
            DreamEditor(original: dream, day: dream.date)
        }
    }
}

struct EntryRow: View {
    let entry: Entry

    var body: some View {
        HStack(spacing: 12) {
            Text(entry.time.isEmpty ? "--:--" : entry.time)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
            if entry.mood > 0 {
                MoodBadge(mood: entry.mood, size: 30)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    if entry.mood > 0 { Text(Mood.name(entry.mood)).font(.body.weight(.medium)) }
                    if entry.energy > 0 { Text("E\(entry.energy)").font(.caption).foregroundStyle(Palette.energy) }
                    if entry.anxiety > 0 { Text("A\(entry.anxiety)").font(.caption).foregroundStyle(Palette.anxiety) }
                }
                if !entry.note.isEmpty {
                    Text(entry.note).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
    }
}

struct EntryEditor: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    let day: String
    let original: Entry?

    @State private var hasTime: Bool
    @State private var time: Date
    @State private var mood: Int
    @State private var energy: Int
    @State private var anxiety: Int
    @State private var note: String

    init(day: String, original: Entry?) {
        self.day = day
        self.original = original
        let isToday = day == Day.today
        _hasTime = State(initialValue: original.map { !$0.time.isEmpty } ?? isToday)
        let start = original.flatMap { Day.time($0.time, on: day) }
            ?? (isToday ? Date() : (Day.time("12:00", on: day) ?? Date()))
        _time = State(initialValue: start)
        _mood = State(initialValue: original?.mood ?? 0)
        _energy = State(initialValue: original?.energy ?? 0)
        _anxiety = State(initialValue: original?.anxiety ?? 0)
        _note = State(initialValue: original?.note ?? "")
    }

    private var isEmpty: Bool {
        return mood == 0 && energy == 0 && anxiety == 0
            && note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("How do you feel?") {
                    MoodPicker(value: $mood)
                }
                Section {
                    ScalePicker(title: "Energy", words: Scale.energyWords, color: Palette.energy, value: $energy)
                    ScalePicker(title: "Anxiety", words: Scale.anxietyWords, color: Palette.anxiety, value: $anxiety)
                } header: {
                    Text("Optional")
                } footer: {
                    Text("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious.")
                }
                Section {
                    Toggle("Time", isOn: $hasTime)
                    if hasTime {
                        DatePicker("At", selection: $time, displayedComponents: .hourAndMinute)
                    }
                }
                Section("Note") {
                    TextField("A few words about it", text: $note, axis: .vertical)
                        .lineLimit(3...10)
                }
                if let original = original {
                    Section {
                        Button("Delete this entry", role: .destructive) {
                            store.delete(original)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(tr(original == nil ? "New entry" : "Edit entry"))
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
        let t = hasTime ? Day.timeString(time) : ""
        if var entry = original {
            entry.time = t
            entry.mood = mood
            entry.energy = energy
            entry.anxiety = anxiety
            entry.note = note
            store.update(entry)
        } else {
            store.addEntry(date: day, time: t, mood: mood, energy: energy, anxiety: anxiety, note: note)
        }
        dismiss()
    }
}
