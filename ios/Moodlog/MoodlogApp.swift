import SwiftUI

@main
struct MoodlogApp: App {
    @State private var store = AppStore()
    @State private var sync = SyncCoordinator()
    @Environment(\.scenePhase) private var phase

    init() {
        UserDefaults.standard.register(defaults: ["autoSync": true, "sendHealth": false])
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .environment(sync)
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
    var body: some View {
        TabView {
            CalendarView()
                .tabItem { Label("Log", systemImage: "calendar") }
            ChartsView()
                .tabItem { Label("Charts", systemImage: "chart.xyaxis.line") }
            EventsView()
                .tabItem { Label("Events", systemImage: "flag") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }
}
