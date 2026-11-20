import SwiftUI

/// Names for the medications Health doesn't name for Moodlog. Each one is recognised by its
/// last dose (when and how much); the names stay on this phone and go to the PC with the doses.
struct MedicationNamesView: View {
    @State private var items: [Medications.Unnamed] = []
    @State private var names: [String: String] = Medications.customNames
    @State private var loading = true

    var body: some View {
        List {
            Section {
                if loading {
                    HStack { ProgressView(); Text("Reading Health…").foregroundStyle(.secondary) }
                } else if items.isEmpty {
                    Text("Health gives the names of all your medications: nothing to name here.")
                        .foregroundStyle(.secondary)
                }
                ForEach(items) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        TextField(tr("Medication %@", "\(item.number)"), text: binding(item.id))
                            .textInputAutocapitalization(.words)
                            .submitLabel(.done)
                        Text(detail(item)).font(.footnote).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            } footer: {
                Text("Health doesn't tell Moodlog the names of these medications. Name each one once, recognising it by its last dose; you can find it in the Health app → Medications. The names stay on this phone and go to your PC with the doses.")
            }
        }
        .navigationTitle(tr("Name your medications"))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            items = await Medications.unnamed()
            loading = false
        }
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
        return tr("Last dose: %@", when + amount) + " · " + trn("%@ dose in 90 days", "%@ doses in 90 days", item.doses)
    }
}
