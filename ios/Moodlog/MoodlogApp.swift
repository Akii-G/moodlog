import SwiftUI
import UserNotifications

/// Set when a notification is tapped: the daily reminder opens today's page with a new entry,
/// the questionnaire reminder opens the questionnaires.
@MainActor
@Observable
final class Launch: NSObject, UNUserNotificationCenterDelegate {
    static let shared = Launch()
    var openToday = false
    var openSurveys = false
    var openAppointment = false

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                            withCompletionHandler completionHandler: @escaping () -> Void) {
        let open = response.notification.request.content.userInfo["open"] as? String
        Task { @MainActor in
            switch open {
            case "surveys": self.openSurveys = true
            case "appointment": self.openAppointment = true
            default: self.openToday = true
            }
        }
        completionHandler()
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                            withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner])
    }
}

@main
struct MoodlogApp: App {
    @State private var store = AppStore()
    @State private var sync = SyncCoordinator()
    @State private var tour = TourState()
    @State private var launch = Launch.shared
    @State private var backupTask: Task<Void, Never>?
    @Environment(\.scenePhase) private var phase

    init() {
        UserDefaults.standard.register(defaults: ["autoSync": true, "sendHealth": false, "kindWord": true])
        // set before launch finishes, so tapping a reminder also works when Moodlog wasn't running
        UNUserNotificationCenter.current().delegate = Launch.shared
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .environment(sync)
                .environment(tour)
                .environment(launch)
                .onAppear {
                    let coordinator = sync
                    store.onChange = { [weak store] in
                        guard let store = store else { return }
                        coordinator.syncSoon(store, delay: 2)
                        Reminder.plan(store)          // logging today means no reminder today
                        SurveyReminder.plan(store)    // a new questionnaire moves the next reminder
                        AppointmentReminder.plan(store)   // a new or moved appointment moves its reminders
                        backupSoon(store)
                    }
                    coordinator.syncSoon(store, delay: 0)
                    Reminder.plan(store)
                    SurveyReminder.plan(store)
                    AppointmentReminder.plan(store)
                }
                .onChange(of: phase) { _, newPhase in
                    if newPhase == .active {
                        sync.syncSoon(store, delay: 0)
                        Reminder.plan(store)
                        SurveyReminder.plan(store)
                        AppointmentReminder.plan(store)
                    } else if newPhase == .background {
                        Backup.write(store)
                    }
                }
        }
    }
}

extension MoodlogApp {
    /// Writes the backup a few seconds after the last change (if a folder was picked).
    @MainActor
    func backupSoon(_ store: AppStore) {
        guard Backup.placeName != nil else { return }
        backupTask?.cancel()
        backupTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if !Task.isCancelled { Backup.write(store) }
        }
    }
}

struct ContentView: View {
    @Environment(AppStore.self) private var store
    @Environment(TourState.self) private var tour
    @Environment(Launch.self) private var launch
    @AppStorage("tourDone") private var tourDone = false
    @AppStorage("lastSeenVersion") private var lastSeenVersion = ""
    @State private var tab = 0
    @State private var whatsNew: WhatsNewItem?
    @State private var customizing = false
    @State private var showingSurveys = false
    @AppStorage("customizeSeen") private var customizeSeen = false
    @AppStorage("showDreams") private var showDreams = true
    @AppStorage("showQuestionnaires") private var showQuestionnaires = true

    var body: some View {
        TabView(selection: $tab) {
            CalendarView()
                .tabItem { Label("Log", systemImage: "calendar") }
                .tag(0)
            ChartsView()
                .tabItem { Label("Charts", systemImage: "chart.xyaxis.line") }
                .tag(1)
            if showDreams {
                DreamsView()
                    .tabItem { Label("Dreams", systemImage: "moon.stars") }
                    .tag(2)
            }
            EventsView()
                .tabItem { Label("Events", systemImage: "flag") }
                .tag(3)
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(4)
        }
        .overlay {
            if tour.step != nil {
                TourOverlay(tour: tour, tab: $tab) {
                    withAnimation(.easeOut(duration: 0.25)) { tour.step = nil }
                    tourDone = true
                    customizeSeen = true
                    tab = 0
                }
                .transition(.opacity)
            }
        }
        .onChange(of: launch.openToday) { _, open in
            if open { tour.step = nil; tab = 0 }
        }
        .onChange(of: launch.openAppointment) { _, open in
            if open { tour.step = nil; tab = 0 }
        }
        .onChange(of: launch.openSurveys) { _, open in
            if open { tour.step = nil; tab = 4; showingSurveys = true; launch.openSurveys = false }
        }
        .onAppear {
            // a brand-new install (nothing logged yet): the welcome tour once;
            // after an update: "What's new" once
            let fresh = store.entries.isEmpty && store.events.isEmpty && store.dreams.isEmpty
            let since = WhatsNew.since(lastSeen: lastSeenVersion, usedBefore: tourDone || !fresh)
            lastSeenVersion = WhatsNew.current
            if !tourDone && fresh {
                tour.start()
            } else if let since, !launch.openToday && !launch.openSurveys && !launch.openAppointment {
                whatsNew = WhatsNewItem(since: since)
            } else if !customizeSeen && !launch.openToday && !launch.openSurveys && !launch.openAppointment {
                customizeSeen = true
                customizing = true
            }
            if launch.openAppointment { tab = 0 }   // CalendarView opens it
            if launch.openSurveys {   // opened by tapping the questionnaire reminder
                tab = 4; showingSurveys = true; launch.openSurveys = false
            }
        }
        .onChange(of: showDreams) { _, on in if !on && tab == 2 { tab = 0 } }
        .onChange(of: showQuestionnaires) { _, _ in SurveyReminder.plan(store) }
        .sheet(item: $whatsNew, onDismiss: {
            // after an update: "Customize your experience" once, right after What's new
            if !customizeSeen { customizeSeen = true; customizing = true }
        }) { item in WhatsNewView(since: item.since) }
        .sheet(isPresented: $customizing) { CustomizeSheet() }
        .sheet(isPresented: $showingSurveys) {
            NavigationStack {
                SurveysView()
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showingSurveys = false } } }
            }
        }
    }
}

struct WhatsNewItem: Identifiable {
    let since: String?
    var id: String { since ?? "all" }
}
