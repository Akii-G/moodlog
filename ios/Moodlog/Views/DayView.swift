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
    @AppStorage("showDreams") private var showDreams = true
    @AppStorage("showMedication") private var showMedication = true

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
            if Medications.isOn && showMedication {
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
            if showDreams {
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
                if Prefs.feelings, let words = entry.feelings, !words.isEmpty {
                    Text(Feeling.text(words)).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
                if !entry.note.isEmpty {
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        if entry.hidden && Prefs.privateTick {
                            Image(systemName: "lock.fill").font(.caption2).foregroundStyle(.secondary)
                        }
                        Text(entry.note).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                    }
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
    @State private var isPrivate: Bool
    @State private var feelings: Set<String>
    @State private var promptOffset = 0
    @AppStorage("privateTick") private var privateTick = true
    @AppStorage("showFeelings") private var showFeelings = true
    @AppStorage("showPrompts") private var showPrompts = true

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
        _isPrivate = State(initialValue: original.map { $0.hidden } ?? (Prefs.privateTick && Prefs.privateDefault))
        _feelings = State(initialValue: Set(original?.feelings ?? []))
    }

    private var isEmpty: Bool {
        return mood == 0 && energy == 0 && anxiety == 0 && feelings.isEmpty
            && note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var noteIsEmpty: Bool { note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    private var placeholder: String {
        showPrompts ? Prompts.forDay(day, offset: promptOffset) : tr("A few words about it")
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
                if showFeelings {
                    Section {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 8)], spacing: 8) {
                            ForEach(Feeling.all, id: \.key) { f in
                                let on = feelings.contains(f.key)
                                Button {
                                    if on { feelings.remove(f.key) } else { feelings.insert(f.key) }
                                } label: {
                                    Text(f.label)
                                        .font(.subheadline.weight(on ? .semibold : .regular))
                                        .lineLimit(1).minimumScaleFactor(0.8)
                                        .frame(maxWidth: .infinity, minHeight: 34)
                                        .background(on ? Color.accentColor.opacity(0.18) : Color.primary.opacity(0.06),
                                                    in: Capsule())
                                        .overlay(Capsule().stroke(on ? Color.accentColor : .clear, lineWidth: 1.5))
                                        .foregroundStyle(on ? Color.accentColor : .primary)
                                }
                                .buttonStyle(.plain)
                                .accessibilityAddTraits(on ? .isSelected : [])
                            }
                        }
                        .padding(.vertical, 4)
                    } header: {
                        Text("Feelings")
                    } footer: {
                        Text("Pick any that fit. Optional.")
                    }
                }
                Section {
                    Toggle("Time", isOn: $hasTime)
                    if hasTime {
                        DatePicker("At", selection: $time, displayedComponents: .hourAndMinute)
                    }
                }
                Section {
                    TextField(placeholder, text: $note, axis: .vertical)
                        .lineLimit(3...10)
                    if showPrompts && noteIsEmpty {
                        Button { promptOffset += 1 } label: {
                            Label("Another question", systemImage: "arrow.clockwise")
                        }
                        .font(.subheadline)
                    }
                    if privateTick {
                        Toggle(isOn: $isPrivate) { Label("Private note", systemImage: "lock.fill") }
                    }
                } header: {
                    Text("Note")
                } footer: {
                    if privateTick { Text("A private note never goes in the doctor report.") }
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
            if privateTick { entry.isPrivate = isPrivate ? true : nil }
            entry.feelings = feelings.isEmpty ? nil : Feeling.clean(Array(feelings))
            store.update(entry)
        } else {
            store.addEntry(date: day, time: t, mood: mood, energy: energy, anxiety: anxiety, note: note,
                           isPrivate: privateTick && isPrivate, feelings: Array(feelings))
        }
        dismiss()
    }
}
