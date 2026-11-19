import Charts
import SwiftUI

struct ChartsView: View {
    @Environment(AppStore.self) private var store
    @State private var month = Day.monthStart(Date())

    private struct Point: Identifiable {
        let id: String
        let date: Date
        let value: Double
        let series: String
    }

    var body: some View {
        let days = Day.days(inMonth: month)
        let moodPoints: [Point] = days.compactMap { d in
            guard let a = store.average(on: d), let date = Day.date(d) else { return nil }
            return Point(id: d, date: date, value: a, series: "Mood")
        }
        let scalePoints: [Point] = days.flatMap { d -> [Point] in
            guard let date = Day.date(d) else { return [] }
            var out: [Point] = []
            if let e = store.scaleAverage(on: d, energy: true) {
                out.append(Point(id: d + "e", date: date, value: e, series: tr("Energy")))
            }
            if let a = store.scaleAverage(on: d, energy: false) {
                out.append(Point(id: d + "a", date: date, value: a, series: tr("Anxiety")))
            }
            return out
        }
        let eventDates: [Point] = store.events.filter { days.contains($0.date) }.compactMap { e in
            guard let date = Day.date(e.date) else { return nil }
            return Point(id: e.id, date: date, value: 0, series: e.title)
        }
        let allMoods = days.flatMap { store.moods(on: $0) }

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Text(Day.monthTitle(month)).font(.title2.weight(.bold))
                        Spacer()
                        Button { month = Day.addMonths(-1, to: month) } label: { Image(systemName: "chevron.left") }
                        Button { month = Day.addMonths(1, to: month) } label: { Image(systemName: "chevron.right") }
                            .disabled(Day.addMonths(1, to: month) > Date())
                    }
                    .font(.title3)

                    tiles(moodPoints: moodPoints, allMoods: allMoods)

                    card("Mood day by day") {
                        if moodPoints.isEmpty {
                            empty("No moods logged this month yet")
                        } else {
                            Chart {
                                ForEach(eventDates) { event in
                                    RuleMark(x: .value("Event", event.date, unit: .day))
                                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [3, 3]))
                                        .foregroundStyle(Color.secondary)
                                        .annotation(position: .top) {
                                            Image(systemName: "flag.fill").font(.system(size: 8))
                                        }
                                }
                                ForEach(moodPoints) { p in
                                    LineMark(x: .value("Day", p.date, unit: .day), y: .value("Mood", p.value))
                                        .foregroundStyle(Color.gray)
                                        .interpolationMethod(.linear)
                                }
                                ForEach(moodPoints) { p in
                                    PointMark(x: .value("Day", p.date, unit: .day), y: .value("Mood", p.value))
                                        .foregroundStyle(Palette.mood(Mood.level(p.value)))
                                        .symbolSize(60)
                                }
                            }
                            .chartYScale(domain: 1...5)
                            .chartYAxis {
                                AxisMarks(values: [1, 2, 3, 4, 5]) { value in
                                    AxisGridLine()
                                    AxisValueLabel {
                                        if let v = value.as(Int.self) { Text(Mood.name(v)) }
                                    }
                                }
                            }
                            .frame(height: 220)
                        }
                    }

                    card("Energy and anxiety (1–5)") {
                        if scalePoints.isEmpty {
                            empty("No energy or anxiety logged this month yet")
                        } else {
                            Chart(scalePoints) { p in
                                LineMark(x: .value("Day", p.date, unit: .day), y: .value("Value", p.value))
                                    .foregroundStyle(by: .value("Scale", p.series))
                                    .symbol(by: .value("Scale", p.series))
                            }
                            .chartForegroundStyleScale([tr("Energy"): Palette.energy, tr("Anxiety"): Palette.anxiety])
                            .chartYScale(domain: 1...5)
                            .frame(height: 180)
                            Text("Energy: 1 very low, 5 very high. Anxiety: 1 calm, 5 very anxious.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }

                    card("How often you logged each mood") {
                        if allMoods.isEmpty {
                            empty("Nothing logged this month yet")
                        } else {
                            Chart {
                                ForEach([5, 4, 3, 2, 1], id: \.self) { level in
                                    BarMark(x: .value("Times", allMoods.filter { $0 == level }.count),
                                            y: .value("Mood", Mood.name(level)))
                                        .foregroundStyle(Palette.mood(level))
                                        .annotation(position: .trailing) {
                                            Text("\(allMoods.filter { $0 == level }.count)")
                                                .font(.caption).foregroundStyle(.secondary)
                                        }
                                }
                            }
                            .chartXAxis(.hidden)
                            .frame(height: 170)
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Charts")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func tiles(moodPoints: [Point], allMoods: [Int]) -> some View {
        let avg = moodPoints.isEmpty ? nil : moodPoints.map { $0.value }.reduce(0, +) / Double(moodPoints.count)
        let common = (1...5).max { a, b in
            let ca = allMoods.filter { $0 == a }.count, cb = allMoods.filter { $0 == b }.count
            return ca == cb ? a < b : ca < cb
        }
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            tile("Average mood", avg.map { String(format: "%.1f", $0) } ?? "–",
                 avg.map { Mood.name(Mood.level($0)) } ?? tr("nothing logged"))
            tile("Days logged", "\(moodPoints.count)", tr("this month"))
            tile("Most common", allMoods.isEmpty ? "–" : Mood.name(common ?? 3),
                 allMoods.isEmpty ? "" : trn("%@ time", "%@ times", allMoods.filter { $0 == (common ?? 3) }.count))
            tile("Current streak", trn("%@ day", "%@ days", store.streak), tr("in a row"))
        }
    }

    private func tile(_ title: String, _ value: String, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(tr(title)).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.title2.weight(.bold))
            Text(sub).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func card<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(tr(title)).font(.headline)
            content()
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func empty(_ text: String) -> some View {
        Text(tr(text)).font(.subheadline).foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, minHeight: 80)
    }
}
