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
    @AppStorage("medicationsOn") private var medicationsOn = false
    @State private var medicationMessage = ""
    @AppStorage("doseAuto") private var doseAuto = false   // ask Dose when Sottovoce opens (DoseLink)
    @State private var testResult = ""
    @State private var testing = false
    @AppStorage("reminderOn") private var reminderOn = false
    @AppStorage("reminderTime") private var reminderTime = "20:30"
    @AppStorage("kindWord") private var kindWord = true
    @AppStorage("weekCard") private var weekCard = true
    @AppStorage("backupError") private var backupError = ""   // set by Backup when a write fails
    @AppStorage("showQuestionnaires") private var showQuestionnaires = true
    @AppStorage("lastBackup") private var lastBackup = ""
    @State private var backupPlace = Backup.placeName
    @State private var backupMessage = ""
    @State private var reopen: URL?   // the file just saved, to be opened once more (see useFile)

    private func chooseMedications() async {
        if let error = await Medications.choose() {
            medicationMessage = error
            return
        }
        medicationMessage = await Medications.status()
        if !Medications.lastChooseShowedSheet {
            medicationMessage = tr("Health has nothing new to ask: every medication already has an answer. To change which ones Sottovoce can read, go to Settings → Health → Data Access & Devices → Sottovoce.")
                + "\n\n" + medicationMessage
        }
        UserDefaults.standard.set(false, forKey: "medicationsSentAll")   // send the whole history again
    }

    private func chooseFile() {
        do {
            let draft = try Backup.draft(store)
            FilePicker.present(saving: draft, onPick: { useFile($0) })
        } catch {
            backupMessage = error.localizedDescription
        }
    }

    private func useFile(_ url: URL) {
        do {
            try Backup.remember(file: url)
            backupPlace = Backup.placeName
            lastBackup = UserDefaults.standard.string(forKey: "lastBackup") ?? ""
            if let error = Backup.writeError(store) {
                backupMessage = tr("The backup was saved, but Sottovoce can't update it there (%@). Try another place, for example iCloud Drive.", error)
            } else {
                backupMessage = tr("Backup saved in %@.", Backup.placeName ?? url.lastPathComponent)
                // A file saved with the Save picker can be written only until the app restarts.
                // Opening it once with the Open picker gives the lasting permission.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { reopen = url }
            }
            lastBackup = UserDefaults.standard.string(forKey: "lastBackup") ?? ""
        } catch {
            backupMessage = tr("Couldn't use that place (%@). Try another one, for example iCloud Drive.", error.localizedDescription)
        }
    }

    /// The second step: the same file, picked with the Open picker, which iOS lets an app keep.
    private func keepFile(_ url: URL) {
        do {
            try Backup.remember(file: url)
            backupPlace = Backup.placeName
            backupMessage = Backup.writeError(store).map {
                tr("The backup was saved, but Sottovoce can't update it there (%@). Try another place, for example iCloud Drive.", $0)
            } ?? tr("Done: Sottovoce will keep %@ up to date, also after restarting.", Backup.placeName ?? url.lastPathComponent)
            lastBackup = UserDefaults.standard.string(forKey: "lastBackup") ?? ""
        } catch {
            backupMessage = tr("Couldn't use that place (%@). Try another one, for example iCloud Drive.", error.localizedDescription)
        }
    }

    private func restore(from url: URL) {
        do {
            let n = store.absorb(try Backup.read(url))
            backupMessage = trn("Restored: %@ change.", "Restored: %@ changes.", n)
        } catch {
            backupMessage = tr("That file isn't a Sottovoce backup.")
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
                    CustomizeOptions()
                } header: {
                    Text("Customize your experience")
                } footer: {
                    Text("Turn off what you don't need. Nothing is deleted.")
                }
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
                    Text("In Sottovoce on your PC, open Phone sync and switch the receiver on. Use the address and token shown there (the same ones as the shortcut). Your iPhone and PC need to be on the same Wi-Fi.")
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
                    Text("When automatic, Sottovoce syncs when you open it and a few seconds after each change. Entries made away from home are kept on the phone and sync when you're back.")
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
                    Text("Sends sleep, steps and resting heart rate to Sottovoce on your PC, like the shortcut does. You can stop using the shortcut once this works.")
                }

                Section {
                    if Medications.isSupported {
                        Toggle("Read my medications from Apple Health", isOn: $medicationsOn)
                            .task { if medicationsOn { medicationMessage = await Medications.status() } }
                            .onChange(of: medicationsOn) { _, on in
                                medicationMessage = ""
                                if on { Task { await chooseMedications() } }
                            }
                        if medicationsOn {
                            Button("Choose which medications…") { Task { await chooseMedications() } }
                        }
                        if !medicationMessage.isEmpty {
                            Text(medicationMessage).font(.footnote).foregroundStyle(.secondary)
                        }
                        if medicationsOn {
                            NavigationLink("Name your medications") { MedicationNamesView() }
                        }
                    } else {
                        Text("Needs iOS 26 or newer.").foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Medication")
                } footer: {
                    Text("Sottovoce reads the medications you log in the Health app, and whether you took or skipped each dose. It shows them on each day and in the report, and sends them to your PC with the Health data above. It can't change anything in Health. Health asks only about medications you haven't decided on yet; to change your choice, go to Settings → Health → Data Access & Devices → Sottovoce.")
                }

                Section {
                    Button("Update from Dose") { DoseLink.askDose() }
                        .disabled(!DoseLink.doseInstalled)
                    Toggle("Update when I open Sottovoce", isOn: $doseAuto)
                        .disabled(!DoseLink.doseInstalled)
                    if !DoseLink.doseInstalled {
                        Text("Dose isn't on this iPhone, or it's older than 1.4.1.").font(.footnote).foregroundStyle(.secondary)
                    } else if store.localDose != nil, let when = DoseLink.lastUpdate {
                        Text(tr("Updated %@.", when.formatted(date: .abbreviated, time: .shortened)))
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                    if store.localDose != nil {
                        Button("Stop using Dose here", role: .destructive) {
                            DoseLink.forget()
                            doseAuto = false
                            store.readLocalDose()
                        }
                    }
                } header: {
                    Text("Dose on this iPhone")
                } footer: {
                    Text("Without a PC: Update from Dose switches to Dose for a moment, and Dose sends your medicines and doses straight back. They show on each day and in the report. Nothing goes on the internet. With the switch on, Sottovoce asks Dose when you open it (at most every 15 minutes).")
                }

                #if DEVTOOLS
                DevNotificationsSection()
                #endif

                if showQuestionnaires {
                    Section {
                        NavigationLink {
                            SurveysView()
                        } label: {
                            LabeledContent("Questionnaires for your doctor",
                                           value: store.surveys.last.map { Day.pretty($0.date, style: "d MMM") } ?? "")
                        }
                    } header: {
                        Text("Questionnaires")
                    } footer: {
                        Text("PHQ-9 and GAD-7, with a gentle reminder every two weeks.")
                    }
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
                    if let place = backupPlace {
                        if !backupError.isEmpty {
                            Label(tr("The last backup didn't work (%@). This often happens after installing an update with a sideloading app: tap Choose another file… and save it again in the same place, replacing the old one.", backupError),
                                  systemImage: "exclamationmark.triangle.fill")
                                .foregroundStyle(.orange)
                        }
                        LabeledContent("File", value: place)
                        LabeledContent("Last backup", value: lastBackup.isEmpty ? tr("not yet") : lastBackup)
                        Button("Back up now") {
                            backupMessage = Backup.writeError(store) ?? tr("Backup saved in %@.", place)
                            lastBackup = UserDefaults.standard.string(forKey: "lastBackup") ?? lastBackup
                        }
                        Button("Choose another file…") { chooseFile() }
                        Button("Stop backing up", role: .destructive) {
                            Backup.forget(); backupPlace = nil
                        }
                    } else {
                        Button("Choose a backup file…") { chooseFile() }
                    }
                    Button("Restore from a backup…") { FilePicker.present(saving: nil, onPick: { restore(from: $0) }) }
                    if !backupMessage.isEmpty { Text(backupMessage).font(.footnote).foregroundStyle(.secondary) }
                } header: {
                    Text("Backup")
                } footer: {
                    Text("Save the backup file once where you like, for example in iCloud Drive, and Sottovoce keeps it up to date after each change. iCloud Drive uploads it; Sottovoce itself never goes on the internet. You can restore it on any phone, Android included.")
                }

                Section {
                    Toggle("A kind word on hard days", isOn: $kindWord)
                } footer: {
                    Text("After a few very hard days close together, the Log shows a short, kind note with a helpline. It never tells anyone; you can hide it any time.")
                }

                Section {
                    Toggle("Your week, gently", isOn: $weekCard)
                } footer: {
                    Text("On Sunday evening and on Monday, a few calm words about your week: days logged, the usual mood, your best day. No scores.")
                }

                Section {
                    Button("Show the tour") { tour.start() }
                    NavigationLink("Privacy") { PrivacyView() }
                    NavigationLink("What's new") { WhatsNewView(since: nil) }
                }
                Section {
                    LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
                    LabeledContent("Entries on this iPhone", value: "\(store.entries.count)")
                    LabeledContent("Events", value: "\(store.events.count)")
                    LabeledContent("Dreams", value: "\(store.dreams.count)")
                    LabeledContent("Questionnaires", value: "\(store.surveys.count)")
                } header: {
                    Text("About")
                } footer: {
                    Text("Sottovoce — a quiet mood diary. Formerly called Moodlog.")
                }
            }
            .navigationTitle("Settings")
            .alert(Text("One more step"), isPresented: Binding(get: { reopen != nil }, set: { if !$0 { reopen = nil } })) {
                Button("Choose the file") {
                    let folder = reopen?.deletingLastPathComponent()
                    reopen = nil
                    FilePicker.present(saving: nil, startIn: folder, onPick: { keepFile($0) })
                }
                Button("Not now", role: .cancel) { reopen = nil }
            } message: {
                Text("So that iOS lets Sottovoce keep updating the backup after the app restarts, tap the file you just saved: sottovoce-backup.json.")
            }
        }
    }
}
