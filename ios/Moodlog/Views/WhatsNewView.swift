import SwiftUI

/// Every version of Moodlog for iPhone, newest first; versions after `since` are marked new.
struct WhatsNewView: View {
    let since: String?
    @Environment(\.dismiss) private var dismiss
    private let versions = WhatsNew.load()

    var body: some View {
        NavigationStack {
            List {
                ForEach(versions) { v in
                    Section {
                        if let since, WhatsNew.newer(v.version, than: since) {
                            Text("New")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                        ForEach(v.lines, id: \.self) { line in
                            Label { Text(line) } icon: { Image(systemName: "circle.fill").font(.system(size: 6)) }
                        }
                    } header: {
                        Text(tr("Version %@", v.version) + " · " + Day.pretty(v.date, style: "d MMMM yyyy"))
                    }
                }
            }
            .navigationTitle("What's new")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}
