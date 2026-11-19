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
