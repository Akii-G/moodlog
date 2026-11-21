import SwiftUI
import UIKit

/// "Report for your doctor": pick a period and what to include, then share the PDF (the share
/// sheet also offers Save to Files, Print, Mail and so on).
struct ReportView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var period = ReportPeriod.threeMonths
    @State private var notes = false
    @State private var sleep = true
    @State private var medication = true
    @AppStorage("showDreams") private var showDreams = true
    @AppStorage("showMedication") private var showMedication = true
    @State private var dreams = false
    @State private var dreamText = false
    @State private var busy = false
    @State private var file: URL?
    @State private var message = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("A printable PDF with your mood over a period, energy and anxiety, sleep, your events with the mood before and after, and a day-by-day table.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                Section("Period") {
                    Picker("Period", selection: $period) {
                        ForEach(ReportPeriod.allCases) { p in Text(p.label).tag(p) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section {
                    Toggle("Include my notes", isOn: $notes)
                    if HealthSender.isAvailable { Toggle("Include sleep from Apple Health", isOn: $sleep) }
                    if Medications.isOn && showMedication { Toggle("Include medication from Apple Health", isOn: $medication) }
                    if showDreams {
                        Toggle("Include dreams (how many, and nightmare nights)", isOn: $dreams)
                            .onChange(of: dreams) { _, on in if !on { dreamText = false } }
                        Toggle("Also what the dreams were about", isOn: $dreamText)
                            .onChange(of: dreamText) { _, on in if on { dreams = true } }
                    }
                } header: {
                    Text("Include")
                } footer: {
                    Text("Notes can be private. Leave this off to share only the numbers and events.")
                }
                Section {
                    Button {
                        Task { await build() }
                    } label: {
                        HStack {
                            Text("Share PDF…")
                            if busy { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(busy)
                    if !message.isEmpty { Text(message).font(.footnote).foregroundStyle(.secondary) }
                }
            }
            .navigationTitle("Report for your doctor")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .sheet(item: Binding(get: { file.map(SharedFile.init) }, set: { if $0 == nil { file = nil } })) { shared in
                ShareSheet(url: shared.url)
            }
        }
    }

    private func build() async {
        busy = true
        message = ""
        let (start, end) = period.range(store)
        let nights = sleep && HealthSender.isAvailable ? (await HealthSender.nightlySleep(from: start, to: end) ?? [:]) : [:]
        var doses: [DoseRecord] = []
        var meds: [MedicationInfo] = []
        if medication && showMedication && Medications.isOn, let from = Day.date(start), let last = Day.date(end),
           let to = Day.calendar.date(byAdding: .day, value: 1, to: last) {
            doses = await Medications.doses(from: from, to: to)
            meds = await Medications.list()
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(tr("Moodlog report") + " \(start) – \(end).pdf")
        do {
            try Report(store: store, start: start, end: end, includeNotes: notes, sleep: nights,
                       includeDreams: dreams && showDreams, dreamText: dreams && dreamText && showDreams, doses: doses, meds: meds).write(to: url)
            file = url
        } catch {
            message = error.localizedDescription
        }
        busy = false
    }
}

private struct SharedFile: Identifiable {
    let url: URL
    var id: String { url.path }
}

/// The iOS share sheet for one file.
struct ShareSheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
