import SwiftUI

struct SettingsView: View {
    @Environment(AppStore.self) private var store
    @Environment(SyncCoordinator.self) private var sync
    @Environment(TourState.self) private var tour
    @AppStorage("server") private var server = ""
    @AppStorage("token") private var token = ""
    @AppStorage("autoSync") private var autoSync = true
    @AppStorage("sendHealth") private var sendHealth = false
    @State private var testResult = ""
    @State private var testing = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Address, e.g. http://pop-os.local:8765", text: $server)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Token", text: $token)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button {
                        testing = true
                        Task {
                            do {
                                testResult = try await SyncClient.ping()
                            } catch {
                                testResult = error.localizedDescription
                            }
                            testing = false
                        }
                    } label: {
                        HStack {
                            Text("Test connection")
                            if testing { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(server.isEmpty || token.isEmpty || testing)
                    if !testResult.isEmpty {
                        Text(testResult).font(.footnote).foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Your PC")
                } footer: {
                    Text("In Moodlog on your PC, open Phone sync and switch the receiver on. Use the address and token shown there (the same ones as the shortcut). Your iPhone and PC need to be on the same Wi-Fi.")
                }

                Section {
                    Toggle("Sync automatically", isOn: $autoSync)
                    Button {
                        Task { await sync.run(store) }
                    } label: {
                        HStack {
                            Text("Sync now")
                            if sync.busy { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(server.isEmpty || token.isEmpty || sync.busy)
                    Text(sync.status).font(.footnote).foregroundStyle(.secondary)
                } header: {
                    Text("Sync")
                } footer: {
                    Text("When automatic, Moodlog syncs when you open it and a few seconds after each change. Entries made away from home are kept on the phone and sync when you're back.")
                }

                Section {
                    Toggle("Send with each sync (at most hourly)", isOn: $sendHealth)
                    Button("Send the last 7 days now") {
                        Task { await sync.sendHealth() }
                    }
                    .disabled(server.isEmpty || token.isEmpty)
                    if !sync.healthStatus.isEmpty {
                        Text(sync.healthStatus).font(.footnote).foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Apple Health")
                } footer: {
                    Text("Sends sleep, steps and resting heart rate to Moodlog on your PC, like the shortcut does. You can stop using the shortcut once this works.")
                }

                Section {
                    Button("Show the tour") { tour.start() }
                    NavigationLink("Privacy") { PrivacyView() }
                }
                Section("About") {
                    LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
                    LabeledContent("Entries on this iPhone", value: "\(store.entries.count)")
                    LabeledContent("Events", value: "\(store.events.count)")
                }
            }
            .navigationTitle("Settings")
        }
    }
}
