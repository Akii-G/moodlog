import Foundation
import HealthKit

/// One dose logged in the Health app: taken or skipped (other answers aren't kept).
struct DoseRecord: Hashable, Identifiable {
    let id: String
    let name: String
    let time: Date
    let taken: Bool
    let amount: Double?
    let unit: String

    var day: String { Day.string(time) }
    var clock: String { Day.timeString(time) }

    /// "Sertraline 50 mg" (the amount only when Health has one).
    var what: String {
        guard let amount, amount > 0 else { return name }
        let number = amount == amount.rounded() ? String(Int(amount)) : String(format: "%g", amount)
        let shown = ["", "count", "1"].contains(unit) ? "" : " \(unit)"
        return "\(name) \(number)\(shown)"
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

    /// Shows the Health sheet where you pick which medications Moodlog may read.
    /// Returns an error message, or nil when it worked.
    static func choose() async -> String? {
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
        return await annotated().map { MedicationInfo(name: name(of: $0), archived: $0.isArchived, asNeeded: !$0.hasSchedule) }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    /// Doses from `start` (nil: all of them) up to `end`, oldest first.
    static func doses(from start: Date?, to end: Date) async -> [DoseRecord] {
        guard isOn, #available(iOS 26.0, *) else { return [] }
        var out: [DoseRecord] = []
        for med in await annotated() {
            out += await doses(of: med, from: start, to: end)
        }
        return out.sorted { $0.time < $1.time }
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
            out.append("dose;\(iso.string(from: d.time));\(d.taken ? "taken" : "skipped");\(amount);\(clean(d.unit));\(clean(d.name))")
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

    @available(iOS 26.0, *)
    private static func annotated() async -> [HKUserAnnotatedMedication] {
        (try? await HKUserAnnotatedMedicationQueryDescriptor().result(for: HealthSender.store)) ?? []
    }

    @available(iOS 26.0, *)
    private static func doses(of med: HKUserAnnotatedMedication, from start: Date?, to end: Date) async -> [DoseRecord] {
        let predicate = NSCompoundPredicate(andPredicateWithSubpredicates: [
            HKQuery.predicateForMedicationDoseEvent(medicationConceptIdentifier: med.medication.identifier),
            HKQuery.predicateForSamples(withStart: start, end: end, options: []),
        ])
        let label = name(of: med)
        let samples: [HKSample] = await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: HKSeriesType.medicationDoseEventType(), predicate: predicate,
                                      limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
                continuation.resume(returning: samples ?? [])
            }
            HealthSender.store.execute(query)
        }
        return samples.compactMap { sample -> DoseRecord? in
            guard let event = sample as? HKMedicationDoseEvent else { return nil }
            let taken: Bool
            switch event.logStatus {
            case .taken: taken = true
            case .skipped: taken = false
            default: return nil
            }
            let amount: Double? = event.doseQuantity
            return DoseRecord(id: event.uuid.uuidString, name: label, time: event.startDate, taken: taken,
                              amount: amount, unit: event.unit.unitString)
        }
    }
}
