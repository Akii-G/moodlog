import SwiftUI

/// The privacy policy in short (the same points as PRIVACY.md), with a link to the full one.
struct PrivacyView: View {
    private var fullPolicy: URL {
        let italian = Locale.preferredLanguages.first?.hasPrefix("it") ?? false
        return URL(string: italian
                   ? "https://github.com/Akii-G/moodlog/blob/main/PRIVACY.it.md"
                   : "https://github.com/Akii-G/moodlog/blob/main/PRIVACY.md")!
    }

    var body: some View {
        List {
            Section {
                point(tr("Nobody collects your data"),
                      tr("Moodlog has no account, no server, no analytics and no ads. The developer never sees anything you write."))
                point(tr("It stays on your devices"),
                      tr("Everything is saved inside this app, on your own PC if you sync, and in the backup file if you pick one."))
                point(tr("Sync is encrypted"),
                      tr("Your entries go straight to your PC over your own Wi-Fi, encrypted with your token. The token itself is never sent."))
                point(tr("Health data only if you choose"),
                      tr("Sleep, steps, resting heart rate and, from iOS 26, your medications are read only if you allow it and turn it on in Settings, and only to show them next to your mood here and on your PC. Moodlog never writes to Apple Health."))
                point(tr("No internet"),
                      tr("Moodlog never connects to the internet. Only the link below opens a page in Safari."))
            } header: {
                Text("Your data is yours")
            }
            Section {
                Link(tr("Read the full policy"), destination: fullPolicy)
            }
        }
        .navigationTitle("Privacy")
    }

    private func point(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.headline)
            Text(text).font(.subheadline).foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}
