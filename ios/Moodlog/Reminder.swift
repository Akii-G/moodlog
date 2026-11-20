import Foundation
import UserNotifications

/// The daily reminder: one quiet notification at the time you chose, only on days you haven't
/// logged a mood yet. iOS can't check at the moment it fires, so Moodlog plans the next week
/// of reminders and plans again whenever you open it or log something, leaving out today
/// once today has a mood.
enum Reminder {
    static let prefix = "moodlog-reminder-"

    static var isOn: Bool { UserDefaults.standard.bool(forKey: "reminderOn") }

    static var time: (hour: Int, minute: Int) {
        let text = UserDefaults.standard.string(forKey: "reminderTime") ?? "20:30"
        let parts = text.split(separator: ":").compactMap { Int($0) }
        return parts.count == 2 ? (parts[0], parts[1]) : (20, 30)
    }

    /// Asks once for permission to show notifications.
    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    /// The days to remind on, starting today unless today is logged or its time has passed.
    static func days(now: Date, loggedToday: Bool, count: Int = 7) -> [Date] {
        let cal = Calendar.current
        let (h, m) = time
        var out: [Date] = []
        for offset in 0..<(count + 1) {
            guard let day = cal.date(byAdding: .day, value: offset, to: now),
                  let at = cal.date(bySettingHour: h, minute: m, second: 0, of: day) else { continue }
            if offset == 0 && (loggedToday || at <= now) { continue }
            out.append(at)
            if out.count == count { break }
        }
        return out
    }

    @MainActor
    static func plan(_ store: AppStore) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let old = requests.map(\.identifier).filter { $0.hasPrefix(prefix) }
            center.removePendingNotificationRequests(withIdentifiers: old)
        }
        guard isOn else { return }
        let logged = !store.moods(on: Day.today).isEmpty
        for at in days(now: Date(), loggedToday: logged) {
            let content = UNMutableNotificationContent()
            content.title = tr("How are you today?")
            content.body = tr("One tap on a face is enough.")
            content.sound = nil
            content.userInfo = ["open": "today"]
            let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: at)
            let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
            center.add(UNNotificationRequest(identifier: prefix + Day.string(at), content: content, trigger: trigger))
        }
    }
}
