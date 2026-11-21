import SwiftUI

/// "Customize your experience": parts of Moodlog you can turn off. Nothing is deleted; it's
/// only hidden. Shown in the tour, once after updating, and in Settings.
enum Prefs {
    static var dreams: Bool { UserDefaults.standard.object(forKey: "showDreams") as? Bool ?? true }
    static var medication: Bool { UserDefaults.standard.object(forKey: "showMedication") as? Bool ?? true }
    /// Days with several moods: "grid" shows them all, "latest" only the last one.
    static var several: String { UserDefaults.standard.string(forKey: "severalMoods") ?? "grid" }
    /// The Private tick on notes and dreams, and whether new ones start ticked.
    static var privateTick: Bool { UserDefaults.standard.object(forKey: "privateTick") as? Bool ?? true }
    static var privateDefault: Bool { UserDefaults.standard.bool(forKey: "privateDefault") }
}

/// The options themselves: rows in a Form, or stacked in the tour's card.
struct CustomizeOptions: View {
    @AppStorage("showDreams") private var showDreams = true
    @AppStorage("showMedication") private var showMedication = true
    @AppStorage("severalMoods") private var several = "grid"
    @AppStorage("privateTick") private var privateTick = true
    @AppStorage("privateDefault") private var privateDefault = false

    var body: some View {
        Toggle(isOn: $showDreams) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Dreams")
                Text("The Dreams tab and the moons on the calendar. Your dreams are kept even when it's off.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        if Medications.isSupported {
            Toggle(isOn: $showMedication) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Medication")
                    Text("The doses from Apple Health on each day and in the report.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        Toggle(isOn: $privateTick) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Private notes and dreams")
                Text("A Private tick on each note and dream: private notes never go in the doctor report, and private dreams are only counted.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        Toggle(isOn: $privateDefault) {
            VStack(alignment: .leading, spacing: 2) {
                Text("New ones start private")
                Text("Every new note and dream is ticked private. You can still untick it.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .disabled(!privateTick)
        VStack(alignment: .leading, spacing: 8) {
            Text("Days with several moods")
            HStack(spacing: 10) {
                choice("grid", tr("All of them, in a grid"))
                choice("latest", tr("Only the latest"))
            }
        }
    }

    private func choice(_ mode: String, _ label: String) -> some View {
        Button {
            several = mode
        } label: {
            VStack(spacing: 6) {
                SeveralPreview(mode: mode).frame(width: 40, height: 40)
                Text(label).font(.caption.weight(.semibold)).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(several == mode ? Color.accentColor.opacity(0.18) : Color.secondary.opacity(0.10)))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(several == mode ? Color.accentColor : .clear, lineWidth: 2))
        }
        .buttonStyle(.plain)
    }
}

/// A tiny calendar day with three moods: as a grid, or only the latest.
struct SeveralPreview: View {
    let mode: String

    var body: some View {
        ZStack {
            if mode == "latest" {
                RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Palette.mood(4))
                FaceView(mood: 4, ink: Palette.faceInk(4), outline: false).padding(9)
            } else {
                RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Color.secondary.opacity(0.15))
                VStack(spacing: 2) {
                    Spacer().frame(height: 8)
                    HStack(spacing: 2) { Palette.mood(2); Palette.mood(3) }
                    HStack(spacing: 2) { Palette.mood(4); Color.clear }
                }
                .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
            }
        }
    }
}

/// The sheet shown once after updating (and the same options as Settings).
struct CustomizeSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    CustomizeOptions()
                } footer: {
                    Text("Turn off what you don't need. Nothing is deleted, and you can change this any time in Settings.")
                }
            }
            .navigationTitle(tr("Customize your experience"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(tr("Done")) { dismiss() } }
            }
        }
    }
}
