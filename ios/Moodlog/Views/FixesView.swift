import SwiftUI

/// Nights under 4 hours, to delete the ones when the watch was charging, not worn. A deleted
/// night goes away here and on the PC (with the sync) and never comes back from Health.
struct ShortNightsView: View {
    @Environment(AppStore.self) private var store
    @State private var nights: [Night] = []
    @State private var loading = true
    @State private var asking: String?

    struct Night: Identifiable {
        let day: String
        let hours: Double
        var id: String { day }
    }

    var body: some View {
        List {
            Section {
                Text("Nights under 4 hours. If you weren't wearing your watch (for example while it was charging), delete that night: it goes away from Sottovoce here and on your PC, and won't come back with the next sync. Real short nights are worth keeping: they matter to your doctor.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            if loading {
                HStack { ProgressView(); Text("Reading Health…").foregroundStyle(.secondary) }
            } else if nights.isEmpty {
                Text("No nights under 4 hours.").foregroundStyle(.secondary)
            } else {
                Section {
                    ForEach(nights) { night in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(Day.pretty(night.day, style: "EEE d MMM yyyy"))
                                Text(String(format: "%.1f h", night.hours)).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button(role: .destructive) { asking = night.day } label: { Text("Delete") }
                                .buttonStyle(.bordered)
                        }
                    }
                }
            }
        }
        .navigationTitle(tr("Short nights"))
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(tr("Delete this night?"),
                            isPresented: Binding(get: { asking != nil }, set: { if !$0 { asking = nil } }),
                            titleVisibility: .visible) {
            Button(role: .destructive) {
                if let day = asking { delete(day) }
            } label: { Text("Delete") }
        } message: {
            Text("This night's sleep will be deleted from Sottovoce here and on your PC. Apple Health keeps its own copy.")
        }
        .task { await load() }
    }

    private func load() async {
        let end = Day.today
        let all = await HealthSender.nightlySleep(from: Day.adding(-365, to: end), to: end) ?? [:]
        nights = all.filter { $0.value < 4 }.map { Night(day: $0.key, hours: $0.value) }.sorted { $0.day > $1.day }
        loading = false
    }

    private func delete(_ day: String) {
        var corrections = Corrections.current
        corrections.deleteNight(day)
        Corrections.current = corrections
        nights.removeAll { $0.day == day }
        store.correctionsChanged(here: true)
    }
}

/// One medicine logged under several names (in Health, then in Dose): give it one name, and
/// every dose shows under it here and on the PC.
struct RenameMedicinesView: View {
    @Environment(AppStore.self) private var store
    @State private var logged: [String: Logged] = [:]
    @State private var loading = true
    @State private var picked: Set<String> = []
    @State private var otherName = ""
    @State private var groupNames: [String: String] = [:]

    struct Logged {
        var first: String
        var last: String
        var fromDose: Bool
    }

    var body: some View {
        let _ = store.correctionsVersion
        let corrections = Corrections.current
        let renames = corrections.renames
        let ingredients = Dictionary((store.doseFeed?.medicines ?? []).map { ($0.name, $0.ingredient ?? "") },
                                     uniquingKeysWith: { a, _ in a })
        let groups = Corrections.suggestions(Array(logged.keys), ingredients: ingredients)
        let renamed = Dictionary(grouping: renames.keys, by: { renames[$0]! })
        let free = logged.keys.filter { renames[$0] == nil }
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        List {
            Section {
                Text("If you logged one medicine under different names (for example in Apple Health and then in Dose), give them one name: all its doses then show under it, here and on your PC. Don't do it for two strengths if your dose really changed.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            if loading {
                HStack { ProgressView(); Text("Reading Health…").foregroundStyle(.secondary) }
            }
            ForEach(groups, id: \.self) { group in
                Section {
                    ForEach(group, id: \.self) { name in nameRow(name) }
                    nameField(Binding(get: { groupNames[key(group)] ?? newest(group) },
                                      set: { groupNames[key(group)] = $0 }),
                              names: group)
                } header: {
                    if group == groups.first { Text("These look like the same medicine") }
                }
            }
            if !renamed.isEmpty {
                Section("Renamed") {
                    ForEach(renamed.keys.sorted(), id: \.self) { to in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(to)
                                Text(renamed[to]!.sorted().joined(separator: ", "))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button { undo(to) } label: { Text("Undo") }.buttonStyle(.bordered)
                        }
                    }
                }
            }
            if !free.isEmpty {
                Section {
                    ForEach(free, id: \.self) { name in
                        Button {
                            if picked.contains(name) { picked.remove(name) } else { picked.insert(name) }
                        } label: {
                            HStack {
                                Image(systemName: picked.contains(name) ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(picked.contains(name) ? Color.accentColor : Color.secondary)
                                nameRow(name)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                    if picked.count >= 2 {
                        nameField($otherName, names: Array(picked))
                    }
                } header: {
                    Text("Other names")
                } footer: {
                    Text("Tick two or more names, then give them one name.")
                }
            }
        }
        .navigationTitle(tr("Medicine names"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func key(_ group: [String]) -> String { group.joined(separator: "|") }

    private func newest(_ names: [String]) -> String {
        names.max { (logged[$0]?.last ?? "") < (logged[$1]?.last ?? "") } ?? ""
    }

    private func nameRow(_ name: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name).foregroundStyle(.primary)
            if let info = logged[name] {
                let span = info.first == info.last ? Day.pretty(info.first, style: "d MMM")
                    : Day.pretty(info.first, style: "d MMM") + " – " + Day.pretty(info.last, style: "d MMM yyyy")
                Text((info.fromDose ? "Dose" : tr("Apple Health")) + " · " + span)
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func nameField(_ text: Binding<String>, names: [String]) -> some View {
        HStack {
            TextField(tr("Name to show in the report"), text: text)
                .textInputAutocapitalization(.words)
            Button {
                let to = text.wrappedValue.trimmingCharacters(in: .whitespaces)
                if !to.isEmpty { rename(names, to: to) }
            } label: { Text("Use this name") }
            .buttonStyle(.borderedProminent)
            .disabled(text.wrappedValue.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }

    private func rename(_ names: [String], to: String) {
        var corrections = Corrections.current
        corrections.rename(names, to: to)
        Corrections.current = corrections
        picked = []
        otherName = ""
        store.correctionsChanged(here: true)
    }

    private func undo(_ to: String) {
        var corrections = Corrections.current
        corrections.undoRename(to)
        Corrections.current = corrections
        store.correctionsChanged(here: true)
    }

    private func load() async {
        var out: [String: Logged] = [:]
        func add(_ name: String, _ day: String, _ fromDose: Bool) {
            if var info = out[name] {
                info.first = min(info.first, day)
                info.last = max(info.last, day)
                info.fromDose = info.fromDose || fromDose
                out[name] = info
            } else {
                out[name] = Logged(first: day, last: day, fromDose: fromDose)
            }
        }
        for dose in await Medications.doses(from: nil, to: Date()) { add(dose.name, Day.string(dose.time), false) }
        for dose in store.doseFeed?.doses ?? [] where dose.status != "pending" { add(dose.name, dose.date, true) }
        logged = out
        loading = false
    }
}
