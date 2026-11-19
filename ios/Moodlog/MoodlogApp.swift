import SwiftUI

@main
struct MoodlogApp: App {
    @State private var store = AppStore()
    @State private var sync = SyncCoordinator()
    @State private var tour = TourState()
    @Environment(\.scenePhase) private var phase

    init() {
        UserDefaults.standard.register(defaults: ["autoSync": true, "sendHealth": false])
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .environment(sync)
                .environment(tour)
                .onAppear {
                    let coordinator = sync
                    store.onChange = { [weak store] in
                        guard let store = store else { return }
                        coordinator.syncSoon(store, delay: 2)
                    }
                    coordinator.syncSoon(store, delay: 0)
                }
                .onChange(of: phase) { _, newPhase in
                    if newPhase == .active {
                        sync.syncSoon(store, delay: 0)
                    }
                }
        }
    }
}

struct ContentView: View {
    @Environment(AppStore.self) private var store
    @Environment(TourState.self) private var tour
    @AppStorage("tourDone") private var tourDone = false
    @State private var tab = 0

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
        .onAppear {
            // a brand-new install (nothing logged yet): show the welcome tour once
            if !tourDone && store.entries.isEmpty && store.events.isEmpty && store.dreams.isEmpty {
                tour.start()
            }
        }
    }
}
