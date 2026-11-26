import Foundation
import HealthKit

/// One dose logged in the Health app: "taken" or "skipped".
struct DoseRecord: Hashable, Identifiable {
    let id: String
    let name: String
    let time: Date
    let status: String
    let amount: Double?
    let unit: String
    /// From Dose: the amount already written out ("1½ pills"). nil for Health's doses.
    var label: String? = nil

    var taken: Bool { status == "taken" }
    var fromDose: Bool { label != nil }

    /// A dose nobody marked whose time has passed: not taken. Otherwise it's still to take.
    var missed: Bool { status == "pending" && time <= Date() }
    var liquid: Bool { unit.lowercased().hasSuffix("ml") }

    /// "× 1", "× 1 mL" ("" when Health has no amount).
    var amountText: String {
        if let label { return label }
        guard let amount, amount > 0 else { return "" }
        let number = amount == amount.rounded() ? String(Int(amount)) : String(format: "%g", amount)
        return "× " + number + (["", "count", "1"].contains(unit) ? "" : " \(unit)")
    }

    var statusText: String {
        switch status {
        case "taken": return tr("Taken")
        case "skipped": return tr("Skipped")
        default: return missed ? tr("Not taken") : tr("Yet to take")
        }
    }

    var day: String { Day.string(time) }
    var clock: String { Day.timeString(time) }

    /// "Sertraline 50 mg" (the amount only when Health has one).
    var what: String {
        guard let amount, amount > 0 else { return name }
        let number = amount == amount.rounded() ? String(Int(amount)) : String(format: "%g", amount)
        let shown = ["", "count", "1"].contains(unit) ? "" : " \(unit)"
        return "\(name) · \(number)\(shown)"
    }
}

struct MedicationInfo: Hashable {
    let name: String
    let archived: Bool
    let asNeeded: Bool
}

/// Reads the medications you log in the Health app (iOS 26 and newer). Moodlog can only read
/// them: it never changes anything in Health, and it never suggests anything about a medication.
enum Medications {
    static var isSupported: Bool {
        if #available(iOS 26.0, *) { return HealthSender.isAvailable }
        return false
    }

    static var isOn: Bool { isSupported && UserDefaults.standard.bool(forKey: "medicationsOn") }

    /// Set by choose(): false when Health returned at once without showing its sheet, which it
    /// does when every medication already has an answer (HealthKit doesn't say so directly).
    nonisolated(unsafe) static var lastChooseShowedSheet = true

    /// Shows the Health sheet where you pick which medications Moodlog may read.
    /// Returns an error message, or nil when it worked.
    static func choose() async -> String? {
        let started = Date()
        defer { lastChooseShowedSheet = Date().timeIntervalSince(started) > 1.0 }
        guard #available(iOS 26.0, *) else { return tr("Needs iOS 26 or newer.") }
        do {
            try await HealthSender.store.requestPerObjectReadAuthorization(for: HKObjectType.userAnnotatedMedicationType(),
                                                                          predicate: nil)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    static func list() async -> [MedicationInfo] {
        guard isOn, #available(iOS 26.0, *) else { return [] }
        let meds = await annotated().map { MedicationInfo(name: name(of: $0), archived: $0.isArchived, asNeeded: !$0.hasSchedule) }
        if !meds.isEmpty {
            return meds.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
        // Health gave no list: name them from the doses ("Medication 1", ...).
        let names = Set(await doses(from: nil, to: Date()).map(\.name))
        return names.sorted().map { MedicationInfo(name: $0, archived: false, asNeeded: false) }
    }

    /// Doses from `start` (nil: all of them) up to `end`, oldest first.
    static func doses(from start: Date?, to end: Date) async -> [DoseRecord] {
        guard isOn, #available(iOS 26.0, *) else { return [] }
        let names = await nameLookup()
        let events = await doseEvents(from: start, to: end)
        return events.compactMap { event -> DoseRecord? in
            let status: String
            switch event.logStatus {
            case .taken: status = "taken"
            case .skipped: status = "skipped"
            default: return nil   // not answered: Health doesn't list future doses, so these aren't shown
            }
            let amount: Double? = event.doseQuantity
            return DoseRecord(id: event.uuid.uuidString, name: names(event.medicationConceptIdentifier), time: event.startDate,
                              status: status, amount: amount, unit: event.unit.unitString)
        }
        .sorted { $0.time < $1.time }
    }

    static func doses(on day: String) async -> [DoseRecord] {
        guard let start = Day.date(day), let end = Day.calendar.date(byAdding: .day, value: 1, to: start) else { return [] }
        return await doses(from: start, to: end)
    }

    /// The lines added to the health data sent to the PC (see take_medication_lines in
    /// src/moodlog). The first time, the whole history; then the last 30 days. Every dose of
    /// the period is sent, so the PC can drop the ones deleted in Health.
    static func lines() async -> (lines: [String], all: Bool) {
        guard isOn else { return ([], false) }
        let all = !UserDefaults.standard.bool(forKey: "medicationsSentAll")
        let end = Date()
        let start = all ? nil : Day.calendar.date(byAdding: .day, value: -29, to: Day.calendar.startOfDay(for: end))
        let meds = await list()
        if meds.isEmpty { return ([], false) }
        let doses = await doses(from: start, to: end)
        let iso = ISO8601DateFormatter()
        var out = ["med_range;\(start.map(Day.string) ?? "2000-01-01");\(Day.string(end))"]
        for m in meds {
            out.append("med;\(m.archived ? "archived" : "active");\(m.asNeeded ? "as_needed" : "scheduled");\(clean(m.name))")
        }
        for d in doses {
            let amount = d.amount.map { String($0) } ?? ""
            out.append("dose;\(iso.string(from: d.time));\(d.status);\(amount);\(clean(d.unit));\(clean(d.name))")
        }
        return (out, all)
    }

    private static func clean(_ s: String) -> String {
        s.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: "\r", with: " ")
    }

    @available(iOS 26.0, *)
    private static func name(of med: HKUserAnnotatedMedication) -> String {
        if let nick = med.nickname, !nick.trimmingCharacters(in: .whitespaces).isEmpty { return nick }
        return med.medication.displayText
    }

    /// The last error Health gave when reading medications (shown in Settings).
    nonisolated(unsafe) static var lastError: String?

    @available(iOS 26.0, *)
    private static func annotated() async -> [HKUserAnnotatedMedication] {
        lastError = nil
        do {
            let meds = try await HKUserAnnotatedMedicationQueryDescriptor(predicate: nil, limit: nil).result(for: HealthSender.store)
            if !meds.isEmpty { return meds }
        } catch {
            lastError = error.localizedDescription
        }
        // Asking for active and archived medications separately (some iOS versions answer
        // these but not the plain request).
        var split: [HKUserAnnotatedMedication] = []
        for archived in [false, true] {
            let predicate = HKQuery.predicateForUserAnnotatedMedications(isArchived: archived)
            if let found = try? await HKUserAnnotatedMedicationQueryDescriptor(predicate: predicate, limit: nil)
                .result(for: HealthSender.store) {
                split += found
            }
        }
        if !split.isEmpty { lastError = nil; return split }
        // The older, callback style query: one medication per call, then done = true.
        let meds: [HKUserAnnotatedMedication] = await withCheckedContinuation { continuation in
            let box = Collected()
            let query = HKUserAnnotatedMedicationQuery(predicate: nil, limit: HKObjectQueryNoLimit) { _, med, done, error in
                if let error { lastError = error.localizedDescription }
                if let result = box.add(med, done: done || error != nil) { continuation.resume(returning: result) }
            }
            HealthSender.store.execute(query)
        }
        if !meds.isEmpty { lastError = nil }
        return meds
    }

    /// Medication concept -> the name you gave it (or Health's). Doses whose medication Health
    /// doesn't list get "Medication 1", "Medication 2"..., always in the same order.
    @available(iOS 26.0, *)
    private static func nameLookup() async -> (HKHealthConceptIdentifier) -> String {
        var names: [HKHealthConceptIdentifier: String] = [:]
        for med in await annotated() { names[med.medication.identifier] = name(of: med) }
        let known = names
        let unknown = await unnamedIDs(known)
        let custom = customNames
        return { id in
            if let n = known[id] { return n }
            if let n = custom[key(id)], !n.isEmpty { return n }
            let i = (unknown.firstIndex(of: id) ?? unknown.count) + 1
            return tr("Medication %@", "\(i)")
        }
    }

    /// Medications with doses but no name from Health, in the order of their first dose.
    @available(iOS 26.0, *)
    private static func unnamedIDs(_ known: [HKHealthConceptIdentifier: String]) async -> [HKHealthConceptIdentifier] {
        var missing: [HKHealthConceptIdentifier] = []
        for event in await doseEvents(from: nil, to: Date()) where known[event.medicationConceptIdentifier] == nil {
            if !missing.contains(event.medicationConceptIdentifier) { missing.append(event.medicationConceptIdentifier) }
        }
        return missing
    }

    // MARK: names you give in Moodlog (when Health doesn't give them)

    private static let namesKey = "medicationNames"

    static var customNames: [String: String] {
        (UserDefaults.standard.dictionary(forKey: namesKey) as? [String: String]) ?? [:]
    }

    static func setName(_ name: String, for key: String) {
        var all = customNames
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        all[key] = clean.isEmpty ? nil : clean
        UserDefaults.standard.set(all, forKey: namesKey)
        UserDefaults.standard.set(false, forKey: "medicationsSentAll")   // the PC gets the new names
    }

    /// A stable text for a medication (the same on every launch and device).
    @available(iOS 26.0, *)
    private static func key(_ id: HKHealthConceptIdentifier) -> String {
        if let data = try? NSKeyedArchiver.archivedData(withRootObject: id, requiringSecureCoding: true) {
            return data.base64EncodedString()
        }
        return String(describing: id)
    }

    /// One medication Health doesn't name, for the naming screen.
    struct Unnamed: Identifiable, Hashable {
        let id: String           // the stable key
        let number: Int          // "Medication 3"
        let doses: Int           // in the last 90 days
        let last: DoseRecord?    // the most recent dose, to recognise it
        let usual: String?       // the time it's usually scheduled for, "21:00" (nil: as needed)
        let liquid: Bool         // measured in mL
    }

    static func unnamed() async -> [Unnamed] {
        guard isOn, #available(iOS 26.0, *) else { return [] }
        var known: [HKHealthConceptIdentifier: String] = [:]
        for med in await annotated() { known[med.medication.identifier] = name(of: med) }
        let ids = await unnamedIDs(known)
        let since = Day.calendar.date(byAdding: .day, value: -90, to: Date())
        let events = await doseEvents(from: since, to: Date())
        return ids.enumerated().map { i, id in
            let mine = events.filter { $0.medicationConceptIdentifier == id }
            let last = mine.last.map { e -> DoseRecord in
                let amount: Double? = e.doseQuantity
                return DoseRecord(id: e.uuid.uuidString, name: tr("Medication %@", "\(i + 1)"), time: e.startDate,
                                  status: e.logStatus == .taken ? "taken" : (e.logStatus == .skipped ? "skipped" : "pending"),
                                  amount: amount, unit: e.unit.unitString)
            }
            // The usual scheduled time, like the groups in Health's medication log.
            var times: [String: Int] = [:]
            for e in mine where e.scheduleType != .asNeeded {
                times[Day.timeString(e.scheduledDate ?? e.startDate), default: 0] += 1
            }
            let usual = times.max { a, b in a.value == b.value ? a.key > b.key : a.value < b.value }?.key
            let liquid = mine.contains { $0.unit.unitString.lowercased().hasSuffix("ml") }
            return Unnamed(id: key(id), number: i + 1, doses: mine.count, last: last, usual: usual, liquid: liquid)
        }
    }

    /// Every dose event Moodlog may read, oldest first.
    @available(iOS 26.0, *)
    private static func doseEvents(from start: Date?, to end: Date) async -> [HKMedicationDoseEvent] {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)
        let samples: [HKSample] = await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: HKSeriesType.medicationDoseEventType(), predicate: predicate,
                                      limit: HKObjectQueryNoLimit, sortDescriptors: [sort]) { _, samples, error in
                if let error { lastError = error.localizedDescription }
                continuation.resume(returning: samples ?? [])
            }
            HealthSender.store.execute(query)
        }
        return samples.compactMap { $0 as? HKMedicationDoseEvent }
    }

    /// What Moodlog can see right now, for Settings: medications, doses in the last 30 days,
    /// or the error Health gave.
    static func status() async -> String {
        guard isOn, #available(iOS 26.0, *) else { return "" }
        let found = await annotated().count
        if let error = lastError { return tr("Health said: %@", error) }
        let end = Date()
        let doses = await doses(from: Day.calendar.date(byAdding: .day, value: -30, to: end), to: end)
        let meds = await list()
        if meds.isEmpty {
            return tr("Sottovoce can't see any medications. Log them in the Health app, or allow them in Settings → Health → Data Access & Devices → Sottovoce.")
        }
        var out = trn("Sottovoce can read %@ medication.", "Sottovoce can read %@ medications.", meds.count) + " "
            + tr("Doses in the last 30 days: %@.", "\(doses.count)")
        if found == 0 { out += " " + tr("Health doesn't give their names: you can name them below.") }
        return out
    }

}

/// Collects the medications the callback query hands over one by one (from HealthKit's queue).
private final class Collected: @unchecked Sendable {
    private let lock = NSLock()
    private var items: [AnyObject] = []
    private var finished = false

    /// Adds one; returns everything once, when the query says it's done.
    @available(iOS 26.0, *)
    func add(_ med: HKUserAnnotatedMedication?, done: Bool) -> [HKUserAnnotatedMedication]? {
        lock.lock(); defer { lock.unlock() }
        if finished { return nil }
        if let med { items.append(med) }
        if !done { return nil }
        finished = true
        return items.compactMap { $0 as? HKUserAnnotatedMedication }
    }
}

/// Medication from Dose, the sister app, sent by Sottovoce on the PC with each sync (see
/// dose_to_json in src/moodlog). Only read here: Dose keeps the medicines and the doses.
struct DoseFeed: Codable, Hashable {
    struct Medicine: Codable, Hashable {
        var name: String
        var archived: Bool
        var asNeeded: Bool
        var ingredient: String? = nil   // from PC 3.4: for suggesting names that are one medicine
    }
    struct Item: Codable, Hashable {
        var date: String
        var time: String
        var status: String      // taken, skipped, pending (scheduled, nobody marked it)
        var amount: String      // "1½ pills"
        var name: String
    }
    var medicines: [Medicine]
    var doses: [Item]

    var infos: [MedicationInfo] { medicines.map { MedicationInfo(name: $0.name, archived: $0.archived, asNeeded: $0.asNeeded) } }

    func records(from start: String, to end: String) -> [DoseRecord] {
        doses.filter { $0.date >= start && $0.date <= end }.compactMap { d in
            guard let at = Day.time(d.time.isEmpty ? "12:00" : d.time, on: d.date) else { return nil }
            return DoseRecord(id: "dose-\(d.date)-\(d.time)-\(d.name)", name: d.name, time: at, status: d.status,
                              amount: nil, unit: "", label: d.amount)
        }
    }
}
