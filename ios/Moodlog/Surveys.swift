import Foundation
import UserNotifications

/// One answered questionnaire (PHQ-9 or GAD-7). Synced and backed up like moods: the newest
/// change wins, deletions are kept as tombstones of type "survey". The same as the PC and Android.
struct Survey: Codable, Identifiable, Hashable {
    var id: String
    var date: String        // "YYYY-MM-DD"
    var time: String        // "HH:MM"
    var kind: String        // "phq9" or "gad7"
    var answers: [Int]      // 0-3 for each question, in order
    var difficulty: Int     // -1 = not answered, 0-3
    var updated: String
    /// Ticked private: never in the doctor report.
    var isPrivate: Bool? = nil

    var hidden: Bool { isPrivate == true }
    var score: Int { answers.reduce(0, +) }
    var maxScore: Int { 3 * answers.count }
    var name: String { SurveyKind(rawValue: kind)?.name ?? kind }

    /// PHQ-9 question 9 answered above "Not at all": Moodlog answers with kind words and
    /// helplines. It never tells or alerts anyone.
    var worries: Bool { kind == "phq9" && answers.count == 9 && answers[8] > 0 }

    /// Only well-formed questionnaires are kept from a sync or a backup.
    var isValid: Bool {
        guard let k = SurveyKind(rawValue: kind) else { return false }
        return answers.count == k.size && answers.allSatisfy { (0...3).contains($0) }
            && (-1...3).contains(difficulty) && !id.isEmpty && Day.date(date) != nil
    }

    enum CodingKeys: String, CodingKey {
        case id, date, time, kind, answers, difficulty, updated
        case isPrivate = "private"
    }
}

enum SurveyKind: String, CaseIterable, Identifiable {
    case phq9, gad7
    var id: String { rawValue }
    var name: String { self == .phq9 ? "PHQ-9" : "GAD-7" }
    var size: Int { self == .phq9 ? 9 : 7 }
    var maxScore: Int { 3 * size }
    var text: SurveyText.Form { SurveyText.current.forms[self]! }
}

/// The official words, as published on phqscreeners.com (checked 21 November 2026). Each
/// language has its own validated wording, so these are not translated with tr().
/// "Developed by Drs. Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and colleagues, with an
/// educational grant from Pfizer Inc. No permission required to reproduce, translate, display or
/// distribute." Moodlog shows raw scores only: no severity words, nothing like a diagnosis.
struct SurveyText {
    struct Form {
        let about: String
        let question: String
        let options: [String]
        let items: [String]
    }
    let forms: [SurveyKind: Form]
    let difficulty: String
    let difficultyOptions: [String]
    let credit: String

    static var current: SurveyText {
        (Bundle.main.preferredLocalizations.first ?? "en").hasPrefix("it") ? italian : english
    }

    static let english = SurveyText(
        forms: [
            .phq9: Form(
                about: "Patient Health Questionnaire",
                question: "Over the last 2 weeks, how often have you been bothered by any of the following problems?",
                options: ["Not at all", "Several days", "More than half the days", "Nearly every day"],
                items: [
                    "Little interest or pleasure in doing things",
                    "Feeling down, depressed, or hopeless",
                    "Trouble falling or staying asleep, or sleeping too much",
                    "Feeling tired or having little energy",
                    "Poor appetite or overeating",
                    "Feeling bad about yourself — or that you are a failure or have let yourself or your family down",
                    "Trouble concentrating on things, such as reading the newspaper or watching television",
                    "Moving or speaking so slowly that other people could have noticed? Or the opposite — being so fidgety or restless that you have been moving around a lot more than usual",
                    "Thoughts that you would be better off dead or of hurting yourself in some way",
                ]),
            .gad7: Form(
                about: "Generalized Anxiety Disorder 7-item scale",
                question: "Over the last 2 weeks, how often have you been bothered by the following problems?",
                options: ["Not at all", "Several days", "More than half the days", "Nearly every day"],
                items: [
                    "Feeling nervous, anxious or on edge",
                    "Not being able to stop or control worrying",
                    "Worrying too much about different things",
                    "Trouble relaxing",
                    "Being so restless that it is hard to sit still",
                    "Becoming easily annoyed or irritable",
                    "Feeling afraid as if something awful might happen",
                ]),
        ],
        difficulty: "If you checked off any problems, how difficult have these problems made it for you to do your work, take care of things at home, or get along with other people?",
        difficultyOptions: ["Not difficult at all", "Somewhat difficult", "Very difficult", "Extremely difficult"],
        credit: "Developed by Drs. Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and colleagues, with an educational grant from Pfizer Inc. No permission required to reproduce, translate, display or distribute.")

    static let italian = SurveyText(
        forms: [
            .phq9: Form(
                about: "Questionario sulla salute del/della paziente",
                question: "Nelle ultime 2 settimane, con quale frequenza le ha dato fastidio ciascuno dei seguenti problemi?",
                options: ["Mai", "Alcuni giorni", "Per più della metà dei giorni", "Quasi ogni giorno"],
                items: [
                    "Scarso interesse o piacere nel fare le cose",
                    "Sentirsi giù, triste o disperato/a",
                    "Problemi ad addormentarsi o a dormire tutta la notte senza svegliarsi, o a dormire troppo",
                    "Sentirsi stanco/a o avere poca energia",
                    "Scarso appetito o mangiare troppo",
                    "Avere una scarsa opinione di sé, o sentirsi un/una fallito/a o aver deluso se stesso/a o i propri familiari",
                    "Difficoltà a concentrarsi su qualcosa, per esempio leggere il giornale o guardare la televisione",
                    "Muoversi o parlare così lentamente da poter essere notato/a da altre persone. O, al contrario, essere così irrequieto/a da muoversi molto più del solito",
                    "Pensare che sarebbe meglio morire o farsi del male in un modo o nell'altro",
                ]),
            .gad7: Form(
                about: "Questionario sul disturbo d'ansia generalizzato",
                question: "Nelle ultime 2 settimane, con quale frequenza le ha dato fastidio ciascuno dei seguenti problemi?",
                options: ["Mai", "Alcuni giorni", "Per oltre la metà dei giorni", "Quasi ogni giorno"],
                items: [
                    "Sentirsi nervoso/a, ansioso/a o teso/a",
                    "Non riuscire a smettere di preoccuparsi o a tenere sotto controllo le preoccupazioni",
                    "Preoccuparsi troppo per varie cose",
                    "Avere difficoltà a rilassarsi",
                    "Essere talmente irrequieto/a da far fatica a stare seduto/a fermo/a",
                    "Infastidirsi o irritarsi facilmente",
                    "Avere paura che possa succedere qualcosa di terribile",
                ]),
        ],
        difficulty: "Se ha fatto una crocetta su uno qualsiasi di questi problemi, quanto questi problemi le hanno reso difficile fare il suo lavoro, occuparsi delle sue cose a casa o avere buoni rapporti con gli altri?",
        difficultyOptions: ["Per niente difficile", "Abbastanza difficile", "Molto difficile", "Estremamente difficile"],
        credit: "Elaborato dai dottori Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke e colleghi, con un finanziamento da parte della Pfizer Inc. Non è richiesto un permesso per la riproduzione, traduzione, visualizzazione o distribuzione.")
}

/// The two-week reminder: one notification 14 days after the last questionnaire, at 19:00.
/// If that day has passed and nothing new was answered, once a week after that. Nothing is
/// planned before the first questionnaire.
enum SurveyReminder {
    static let id = "moodlog-survey"
    static let everyDays = 14
    static let hour = 19

    static var isOn: Bool {
        Prefs.questionnaires && (UserDefaults.standard.object(forKey: "surveyRemind") as? Bool ?? true)
    }

    /// When to remind, or nil: due day at 19:00, then every 7 days until it's in the future.
    static func next(lastDay: String?, now: Date = Date()) -> Date? {
        guard let lastDay, let due = Day.date(Day.adding(everyDays, to: lastDay)),
              var at = Day.calendar.date(bySettingHour: hour, minute: 0, second: 0, of: due) else { return nil }
        while at <= now {
            guard let later = Day.calendar.date(byAdding: .day, value: 7, to: at) else { return nil }
            at = later
        }
        return at
    }

    static func content() -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = tr("Two weeks since your last questionnaires")
        content.body = tr("Answering PHQ-9 and GAD-7 again takes about two minutes, and helps your doctor see how things are going.")
        content.sound = nil
        content.userInfo = ["open": "surveys"]
        return content
    }

    @MainActor
    static func plan(_ store: AppStore) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [id])
        guard isOn, let at = next(lastDay: store.surveys.map(\.date).max()) else { return }
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: at)
        let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
        center.add(UNNotificationRequest(identifier: id, content: content(), trigger: trigger))
    }
}

#if DEVTOOLS
/// Developer build only: fire each notification a few seconds from now, to check how it looks
/// and that tapping it opens the right place.
enum DevNotifications {
    static func daily(in seconds: TimeInterval = 5) async -> String {
        guard await Reminder.requestPermission() else { return "Notifications are not allowed for Sottovoce." }
        let content = UNMutableNotificationContent()
        content.title = tr("How are you today?")
        content.body = tr("One tap on a face is enough.")
        content.userInfo = ["open": "today"]
        return await add("moodlog-dev-daily", content, seconds)
    }

    static func survey(in seconds: TimeInterval = 5) async -> String {
        guard await Reminder.requestPermission() else { return "Notifications are not allowed for Sottovoce." }
        return await add("moodlog-dev-survey", SurveyReminder.content(), seconds)
    }

    static func appointment(in seconds: TimeInterval = 5) async -> String {
        guard await Reminder.requestPermission() else { return "Notifications are not allowed for Sottovoce." }
        let sample = Appointment(date: Day.adding(1, to: Day.today), time: "16:30", with: "", previous: "", updated: "")
        return await add("moodlog-dev-appointment", AppointmentReminder.content(sample, lead: 1, open: 2), seconds)
    }

    static func pending() async -> String {
        let requests = await UNUserNotificationCenter.current().pendingNotificationRequests()
        let lines = requests.sorted { $0.identifier < $1.identifier }.map { r -> String in
            let when = (r.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate()
                .map { DateFormatter.localizedString(from: $0, dateStyle: .short, timeStyle: .short) } ?? "soon"
            return "\(r.identifier): \(when)"
        }
        return lines.isEmpty ? "Nothing planned." : lines.joined(separator: "\n")
    }

    private static func add(_ id: String, _ content: UNMutableNotificationContent, _ seconds: TimeInterval) async -> String {
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
        do {
            try await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
            return "Coming in \(Int(seconds)) seconds. Lock the phone or go to the Home Screen to see it as a banner."
        } catch {
            return error.localizedDescription
        }
    }
}
#endif
