import SwiftUI

/// Names for the medications Health doesn't name for Moodlog, laid out like Health's own
/// medication log: grouped by the time each one is usually scheduled, then those taken as
/// needed. The names stay on this phone and go to the PC with the doses.
struct MedicationNamesView: View {
    @State private var items: [Medications.Unnamed] = []
    @State private var names: [String: String] = Medications.customNames
    @State private var loading = true

    private var times: [String] { Array(Set(items.compactMap(\.usual))).sorted() }

    var body: some View {
        List {
            if loading {
                HStack { ProgressView(); Text("Reading Health…").foregroundStyle(.secondary) }
            } else if items.isEmpty {
                Text("Health gives the names of all your medications: nothing to name here.")
                    .foregroundStyle(.secondary)
            } else {
                Section {
                    Text("Health doesn't tell Moodlog the names of these medications. Open Health → Medications: the groups below are in the same order as its medication log. Type each name once.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                ForEach(times, id: \.self) { time in
                    Section(time) {
                        ForEach(items.filter { $0.usual == time && $0.last != nil }) { row($0) }
                    }
                }
                let asNeeded = items.filter { $0.usual == nil && $0.last != nil }
                if !asNeeded.isEmpty {
                    Section("Taken as needed") { ForEach(asNeeded) { row($0) } }
                }
                let old = items.filter { $0.last == nil }
                if !old.isEmpty {
                    Section {
                        ForEach(old) { row($0) }
                    } header: {
                        Text("No doses in the last 90 days")
                    }
                }
            }
        }
        .navigationTitle(tr("Name your medications"))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            items = await Medications.unnamed()
            loading = false
        }
    }

    private func row(_ item: Medications.Unnamed) -> some View {
        HStack(spacing: 12) {
            Image(systemName: item.liquid ? "drop.fill" : "pills.fill")
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(Circle().fill(Palette.health))
            VStack(alignment: .leading, spacing: 3) {
                TextField(tr("Medication %@", "\(item.number)"), text: binding(item.id))
                    .textInputAutocapitalization(.words)
                    .submitLabel(.done)
                Text(detail(item)).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { names[key] ?? "" }, set: { value in
            names[key] = value
            Medications.setName(value, for: key)
        })
    }

    private func detail(_ item: Medications.Unnamed) -> String {
        guard let last = item.last else { return tr("No doses in the last 90 days.") }
        let when = Day.pretty(last.day, style: "EEE d MMM") + " " + last.clock
        var amount = ""
        if let a = last.amount, a > 0 {
            amount = " · " + (a == a.rounded() ? String(Int(a)) : String(format: "%g", a))
                + (["", "count", "1"].contains(last.unit) ? "" : " " + last.unit)
        }
        return tr("Last dose: %@", when + amount)
    }
}
