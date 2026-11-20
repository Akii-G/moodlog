import Foundation

/// The kind word on hard stretches: after several hard days close together, the Log shows a
/// small, calm card with a helpline. It can be hidden (then it waits a week) or turned off.
/// The same rule as Moodlog for Android and the PC.
enum Care {
    /// Three logged days averaging 1.75 or lower within the last five, the latest today or yesterday.
    @MainActor
    static func hardStretch(_ store: AppStore, today: String = Day.today) -> Bool {
        let days = (0..<5).map { Day.adding(-$0, to: today) }
        let hard = days.filter { (store.average(on: $0) ?? 9) <= 1.75 }
        return hard.count >= 3 && (hard.contains(today) || hard.contains(Day.adding(-1, to: today)))
    }

    @MainActor
    static func shouldShow(_ store: AppStore, today: String = Day.today) -> Bool {
        let defaults = UserDefaults.standard
        guard defaults.object(forKey: "kindWord") as? Bool ?? true else { return false }
        if let hidden = defaults.string(forKey: "kindHidden"), let a = Day.date(hidden), let b = Day.date(today),
           (Calendar.current.dateComponents([.day], from: a, to: b).day ?? 99) < 7 {
            return false
        }
        return hardStretch(store, today: today)
    }

    struct Helpline: Identifiable {
        let id = UUID()
        let name: String
        let number: String?
        let link: String?
        let note: String
    }

    /// Checked on 20 November 2026: Telefono Amico Italia 02 2327 2327 (WhatsApp 324 011 7252);
    /// 112 is the emergency number across Europe; findahelpline.com lists helplines worldwide.
    static var helplines: [Helpline] {
        if Locale.preferredLanguages.first?.hasPrefix("it") ?? false {
            return [Helpline(name: "Telefono Amico Italia", number: "02 2327 2327", link: nil,
                             note: tr("Every day, free and anonymous. Also on WhatsApp: 324 011 7252.")),
                    Helpline(name: tr("Emergency"), number: "112", link: nil, note: tr("If you're in danger right now."))]
        }
        return [Helpline(name: "Find A Helpline", number: nil, link: "https://findahelpline.com",
                         note: tr("Free, confidential helplines in your country.")),
                Helpline(name: tr("Emergency"), number: "112", link: nil,
                         note: tr("If you're in danger right now: 112 in Europe, or your local emergency number."))]
    }
}
