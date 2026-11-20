import SwiftUI
import UserNotifications

/// Set when the reminder notification is tapped: the Log opens today's page with a new entry.
@MainActor
@Observable
final class Launch: NSObject, UNUserNotificationCenterDelegate {
    static let shared = Launch()
    var openToday = false

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                            withCompletionHandler completionHandler: @escaping () -> Void) {
        Task { @MainActor in self.openToday = true }
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
                        backupSoon(store)
                    }
                    coordinator.syncSoon(store, delay: 0)
                    Reminder.plan(store)
                }
                .onChange(of: phase) { _, newPhase in
                    if newPhase == .active {
                        sync.syncSoon(store, delay: 0)
                        Reminder.plan(store)
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
        guard Backup.folderName != nil else { return }
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

    var body: some View {
        TabView(selection: $tab) {
            CalendarView()
                .tabItem { Label("Log", systemImage: "calendar") }
                .tag(0)
            ChartsView()
                .tabItem { Label("Charts", systemImage: "chart.xyaxis.line") }
                .tag(1)
            DreamsView()
                .tabItem { Label("Dreams", systemImage: "moon.stars") }
                .tag(2)
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
                    tab = 0
                }
                .transition(.opacity)
            }
        }
        .onChange(of: launch.openToday) { _, open in
            if open { tour.step = nil; tab = 0 }
        }
        .onAppear {
            // a brand-new install (nothing logged yet): the welcome tour once;
            // after an update: "What's new" once
            let fresh = store.entries.isEmpty && store.events.isEmpty && store.dreams.isEmpty
            let since = WhatsNew.since(lastSeen: lastSeenVersion, usedBefore: tourDone || !fresh)
            lastSeenVersion = WhatsNew.current
            if !tourDone && fresh {
                tour.start()
            } else if let since, !launch.openToday {
                whatsNew = WhatsNewItem(since: since)
            }
        }
        .sheet(item: $whatsNew) { item in WhatsNewView(since: item.since) }
    }
}

struct WhatsNewItem: Identifiable {
    let since: String?
    var id: String { since ?? "all" }
}
