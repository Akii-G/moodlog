import SwiftUI
import UniformTypeIdentifiers

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
    @AppStorage("reminderOn") private var reminderOn = false
    @AppStorage("reminderTime") private var reminderTime = "20:30"
    @AppStorage("kindWord") private var kindWord = true
    @AppStorage("lastBackup") private var lastBackup = ""
    @State private var backupFolder = Backup.folderName
    @State private var picking = false
    @State private var pickingFolder = true   // false: picking a backup file to restore
    @State private var backupMessage = ""

    private func useFolder(_ url: URL) {
        do {
            try Backup.remember(folder: url)
            backupFolder = Backup.folderName
            if Backup.write(store) {
                backupMessage = tr("Backup saved in %@.", Backup.folderName ?? url.lastPathComponent)
            } else {
                backupMessage = tr("Couldn't write the backup. Pick the folder again.")
            }
        } catch {
            backupMessage = tr("Couldn't use that folder (%@). Try another one, for example in iCloud Drive.", error.localizedDescription)
        }
    }

    private func restore(from url: URL) {
        do {
            let n = store.absorb(try Backup.read(url))
            backupMessage = trn("Restored: %@ change.", "Restored: %@ changes.", n)
        } catch {
            backupMessage = tr("That file isn't a Moodlog backup.")
        }
    }

    private var reminderDate: Binding<Date> {
        Binding(get: {
            let parts = reminderTime.split(separator: ":").compactMap { Int($0) }
            return Calendar.current.date(bySettingHour: parts.first ?? 20, minute: parts.count > 1 ? parts[1] : 30, second: 0, of: Date()) ?? Date()
        }, set: { date in
            reminderTime = Day.timeString(date)
            Reminder.plan(store)
        })
    }

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
                    Toggle("Remind me once a day", isOn: $reminderOn)
                        .onChange(of: reminderOn) { _, on in
                            Task {
                                if on {
                                    let allowed = await Reminder.requestPermission()
                                    if !allowed { reminderOn = false }
                                }
                                Reminder.plan(store)
                            }
                        }
                    if reminderOn {
                        DatePicker("At", selection: reminderDate, displayedComponents: .hourAndMinute)
                    }
                } header: {
                    Text("Reminder")
                } footer: {
                    Text("Only on days you haven't logged a mood yet. No streaks, no guilt: just a gentle nudge.")
                }

                Section {
                    if let folder = backupFolder {
                        LabeledContent("Folder", value: folder)
                        LabeledContent("Last backup", value: lastBackup.isEmpty ? tr("not yet") : lastBackup)
                        Button("Back up now") {
                            backupMessage = Backup.write(store) ? "" : tr("Couldn't write the backup. Pick the folder again.")
                        }
                        Button("Choose another folder…") { pickingFolder = true; picking = true }
                        Button("Stop backing up", role: .destructive) {
                            Backup.forget(); backupFolder = nil
                        }
                    } else {
                        Button("Choose a backup folder…") { pickingFolder = true; picking = true }
                    }
                    Button("Restore from a backup…") { pickingFolder = false; picking = true }
                    if !backupMessage.isEmpty { Text(backupMessage).font(.footnote).foregroundStyle(.secondary) }
                } header: {
                    Text("Backup")
                } footer: {
                    Text("Pick a folder once, for example in iCloud Drive, and Moodlog keeps a copy of everything there after each change. iCloud Drive uploads it; Moodlog itself never goes on the internet. You can restore it on any phone, Android included.")
                }

                Section {
                    Toggle("A kind word on hard days", isOn: $kindWord)
                } footer: {
                    Text("After a few very hard days close together, the Log shows a short, kind note with a helpline. It never tells anyone; you can hide it any time.")
                }

                Section {
                    Button("Show the tour") { tour.start() }
                    NavigationLink("Privacy") { PrivacyView() }
                    NavigationLink("What's new") { WhatsNewView(since: nil) }
                }
                Section("About") {
                    LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
                    LabeledContent("Entries on this iPhone", value: "\(store.entries.count)")
                    LabeledContent("Events", value: "\(store.events.count)")
                    LabeledContent("Dreams", value: "\(store.dreams.count)")
                }
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $picking) {
                DocumentPicker(types: pickingFolder ? [.folder] : [.json, .data], onPick: { url in
                    picking = false
                    if pickingFolder { useFolder(url) } else { restore(from: url) }
                }, onCancel: { picking = false })
                .ignoresSafeArea()
            }
        }
    }
}
