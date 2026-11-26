import Foundation
import HealthKit

/// Reads the last days of steps, resting heart rate and sleep, and turns them into the
/// same text lines the Moodlog shortcut sends (metric;date;value and sleep;start;end;stage).
enum HealthSender {
    static let store = HKHealthStore()

    static var isAvailable: Bool {
        return HKHealthStore.isHealthDataAvailable()
    }

    static func authorize() async throws {
        let read: Set<HKObjectType> = [
            HKQuantityType.quantityType(forIdentifier: .stepCount)!,
            HKQuantityType.quantityType(forIdentifier: .restingHeartRate)!,
            HKCategoryType.categoryType(forIdentifier: .sleepAnalysis)!,
        ]
        try await store.requestAuthorization(toShare: [], read: read)
    }

    private static func daily(_ id: HKQuantityTypeIdentifier, unit: HKUnit, sum: Bool, days: Int) async throws -> [(Date, Double)] {
        guard let type = HKQuantityType.quantityType(forIdentifier: id) else { return [] }
        let calendar = Calendar.current
        let end = Date()
        let start = calendar.startOfDay(for: calendar.date(byAdding: .day, value: -(days - 1), to: end) ?? end)
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
        let options: HKStatisticsOptions = sum ? .cumulativeSum : .discreteAverage
        return try await withCheckedThrowingContinuation { continuation in
            let query = HKStatisticsCollectionQuery(quantityType: type, quantitySamplePredicate: predicate,
                                                    options: options, anchorDate: start,
                                                    intervalComponents: DateComponents(day: 1))
            query.initialResultsHandler = { _, results, error in
                if let error = error {
                    // "No data" is reported as an error; treat it as nothing found.
                    if let hk = error as? HKError, hk.code == .errorNoData {
                        continuation.resume(returning: [])
                    } else {
                        continuation.resume(throwing: error)
                    }
                    return
                }
                var out: [(Date, Double)] = []
                results?.enumerateStatistics(from: start, to: end) { stats, _ in
                    let quantity = sum ? stats.sumQuantity() : stats.averageQuantity()
                    if let quantity = quantity {
                        out.append((stats.startDate, quantity.doubleValue(for: unit)))
                    }
                }
                continuation.resume(returning: out)
            }
            store.execute(query)
        }
    }

    private static func sleepSamples(days: Int) async throws -> [HKCategorySample] {
        guard let type = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) else { return [] }
        let start = Calendar.current.date(byAdding: .day, value: -days, to: Date()) ?? Date()
        let predicate = HKQuery.predicateForSamples(withStart: start, end: Date(), options: [])
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)
        return try await withCheckedThrowingContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit,
                                      sortDescriptors: [sort]) { _, samples, error in
                if let error = error {
                    if let hk = error as? HKError, hk.code == .errorNoData {
                        continuation.resume(returning: [])
                    } else {
                        continuation.resume(throwing: error)
                    }
                    return
                }
                continuation.resume(returning: (samples as? [HKCategorySample]) ?? [])
            }
            store.execute(query)
        }
    }

    private static func stage(_ value: Int) -> String {
        if value == HKCategoryValueSleepAnalysis.inBed.rawValue { return "In Bed" }
        if value == HKCategoryValueSleepAnalysis.awake.rawValue { return "Awake" }
        if value == HKCategoryValueSleepAnalysis.asleepCore.rawValue { return "Core" }
        if value == HKCategoryValueSleepAnalysis.asleepDeep.rawValue { return "Deep" }
        if value == HKCategoryValueSleepAnalysis.asleepREM.rawValue { return "REM" }
        return "Asleep"
    }

    /// Asleep segments -> [wake-up day: hours], overlaps merged. The same rule as the PC
    /// (_sleep_hours in src/moodlog) and Android: a night belongs to the day of end + 9 hours.
    static func sleepHours(_ segments: [(Date, Date)]) -> [String: Double] {
        var nights: [String: [(Date, Date)]] = [:]
        for (s, e) in segments where e > s {
            nights[Day.string(e.addingTimeInterval(9 * 3600)), default: []].append((s, e))
        }
        var out: [String: Double] = [:]
        for (day, spans) in nights {
            var total = 0.0
            var cur: (Date, Date)?
            for (s, e) in spans.sorted(by: { $0.0 < $1.0 }) {
                if let c = cur, s <= c.1 {
                    cur = (c.0, max(c.1, e))
                } else {
                    if let c = cur { total += c.1.timeIntervalSince(c.0) }
                    cur = (s, e)
                }
            }
            if let c = cur { total += c.1.timeIntervalSince(c.0) }
            if total > 0 && total <= 24 * 3600 { out[day] = (total / 36).rounded() / 100 }
        }
        return out
    }

    /// Hours asleep per night from `start` to `end` (days), or nil if Moodlog can't read sleep.
    static func nightlySleep(from start: String, to end: String) async -> [String: Double]? {
        guard isAvailable, let first = Day.date(start), let last = Day.date(end),
              let type = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) else { return nil }
        try? await authorize()
        let from = Calendar.current.date(byAdding: .day, value: -1, to: first) ?? first
        let to = Calendar.current.date(byAdding: .day, value: 2, to: last) ?? last
        let predicate = HKQuery.predicateForSamples(withStart: from, end: to, options: [])
        let samples: [HKCategorySample] = await withCheckedContinuation { continuation in
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit,
                                      sortDescriptors: nil) { _, samples, _ in
                continuation.resume(returning: (samples as? [HKCategorySample]) ?? [])
            }
            store.execute(query)
        }
        let asleep = samples.filter { ["Core", "Deep", "REM", "Asleep"].contains(stage($0.value)) }
            .map { ($0.startDate, $0.endDate) }
        let deleted = Corrections.current.deletedNights   // nights without the watch, deleted in Sottovoce
        return sleepHours(asleep).filter { $0.key >= start && $0.key <= end && !deleted.contains($0.key) }
    }

    static func lines(days: Int) async throws -> [String] {
        try await authorize()
        var out: [String] = []
        for (day, value) in try await daily(.stepCount, unit: .count(), sum: true, days: days) {
            out.append("steps;\(Day.string(day));\(Int(value.rounded()))")
        }
        let bpm = HKUnit.count().unitDivided(by: .minute())
        for (day, value) in try await daily(.restingHeartRate, unit: bpm, sum: false, days: days) {
            out.append("resting_hr;\(Day.string(day));\(Int(value.rounded()))")
        }
        let iso = ISO8601DateFormatter()
        for sample in try await sleepSamples(days: days + 1) {
            out.append("sleep;\(iso.string(from: sample.startDate));\(iso.string(from: sample.endDate));\(stage(sample.value))")
        }
        return out
    }
}
