import Charts
import SwiftUI

struct ChartsView: View {
    @Environment(AppStore.self) private var store
    @State private var month = Day.monthStart(Date())
    @State private var showReport = false
    @State private var sleep: [String: Double]?
    @State private var sleepLoaded = false

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

                    if sleepLoaded { sleepCard(days) }

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
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { showReport = true } label: { Label("Report for your doctor", systemImage: "doc.text") }
                }
            }
            .sheet(isPresented: $showReport) { ReportView() }
            .task(id: month) {
                sleepLoaded = false
                sleep = await HealthSender.nightlySleep(from: days.first ?? Day.today, to: days.last ?? Day.today)
                sleepLoaded = true
            }
        }
    }

    /// Sleep from Apple Health night by night, the day's mood on top, and the mood after
    /// shorter vs longer nights.
    private func sleepCard(_ days: [String]) -> some View {
        card("Sleep and mood") {
            if let nights = sleep, !nights.isEmpty {
                let points: [Point] = days.compactMap { d in
                    guard let h = nights[d], let date = Day.date(d) else { return nil }
                    return Point(id: d, date: date, value: h, series: "sleep")
                }
                Chart {
                    ForEach(points) { p in
                        BarMark(x: .value("Day", p.date, unit: .day), y: .value("Hours", p.value))
                            .foregroundStyle(Palette.health)
                            .cornerRadius(3)
                    }
                    ForEach(points) { p in
                        if let m = store.average(on: p.id) {
                            PointMark(x: .value("Day", p.date, unit: .day), y: .value("Hours", p.value + 0.6))
                                .foregroundStyle(Palette.mood(Mood.level(m)))
                                .symbolSize(50)
                        }
                    }
                }
                .chartYAxis { AxisMarks(values: [0, 3, 6, 9]) { v in AxisGridLine(); AxisValueLabel { if let h = v.as(Int.self) { Text("\(h) h") } } } }
                .frame(height: 170)
                HStack(spacing: 14) {
                    Label("Hours asleep", systemImage: "circle.fill").foregroundStyle(Palette.health)
                    Label("Mood that day", systemImage: "circle.fill").foregroundStyle(Palette.mood(4))
                }
                .font(.caption).labelStyle(.titleAndIcon)
                let short = nights.filter { $0.value < 7 }.keys.compactMap { store.average(on: $0) }
                let long = nights.filter { $0.value >= 7 }.keys.compactMap { store.average(on: $0) }
                let mean = { (v: [Double]) in v.reduce(0, +) / Double(max(v.count, 1)) }
                let text = tr("Average %@ h a night (%@ nights).", String(format: "%.1f", mean(Array(nights.values))), "\(nights.count)")
                    + (short.count >= 3 && long.count >= 3
                       ? " " + tr("Mood after nights under 7 h: %@; after 7 h or more: %@. A pattern in your data, not a cause.",
                                  String(format: "%.1f", mean(short)), String(format: "%.1f", mean(long))) : "")
                Text(text).font(.caption).foregroundStyle(.secondary)
            } else if sleep == nil || !HealthSender.isAvailable {
                Text("Allow Apple Health in Settings to see your sleep here, next to your mood.")
                    .font(.subheadline).foregroundStyle(.secondary)
            } else {
                empty("No sleep in Apple Health this month")
            }
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
