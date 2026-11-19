import SwiftUI

struct CalendarView: View {
    @Environment(AppStore.self) private var store
    @Environment(SyncCoordinator.self) private var sync
    @AppStorage("server") private var server = ""
    @State private var month = Day.monthStart(Date())

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 7)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if server.isEmpty {
                        Label("Connect to Moodlog on your PC in Settings to sync.", systemImage: "desktopcomputer")
                            .font(.subheadline)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(.blue.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    header
                    HStack(spacing: 6) {
                        ForEach(Day.weekdayInitials, id: \.self) { d in
                            Text(d).font(.caption2).foregroundStyle(.secondary).frame(maxWidth: .infinity)
                        }
                    }
                    LazyVGrid(columns: columns, spacing: 6) {
                        ForEach(Array(Day.grid(forMonth: month).enumerated()), id: \.offset) { _, day in
                            if let day = day {
                                NavigationLink(value: day) {
                                    DayCell(day: day)
                                }
                                .buttonStyle(.plain)
                                .disabled(day > Day.today)
                            } else {
                                Color.clear.aspectRatio(1, contentMode: .fit)
                            }
                        }
                    }
                    summary
                }
                .padding()
            }
            .refreshable {
                await sync.run(store)
            }
            .navigationTitle("Moodlog")
            .navigationDestination(for: String.self) { day in
                DayView(day: day)
            }
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    NavigationLink(value: Day.today) {
                        Label("Today", systemImage: "plus")
                    }
                }
            }
        }
    }

    private var header: some View {
        HStack {
            Text(Day.monthTitle(month)).font(.title2.weight(.bold))
            Spacer()
            Button {
                month = Day.addMonths(-1, to: month)
            } label: {
                Image(systemName: "chevron.left")
            }
            .accessibilityLabel("Previous month")
            Button {
                month = Day.addMonths(1, to: month)
            } label: {
                Image(systemName: "chevron.right")
            }
            .disabled(Day.addMonths(1, to: month) > Date())
            .accessibilityLabel("Next month")
        }
        .font(.title3)
    }

    private var summary: some View {
        let days = Day.days(inMonth: month).filter { store.average(on: $0) != nil }
        let values = days.compactMap { store.average(on: $0) }
        let text: String
        if values.isEmpty {
            text = tr("Nothing logged this month yet")
        } else {
            let avg = values.reduce(0, +) / Double(values.count)
            text = trn("%@ day logged · average %@ (%@)", "%@ days logged · average %@ (%@)", days.count,
                       String(format: "%.1f", avg), Mood.name(Mood.level(avg)).lowercased())
        }
        return VStack(alignment: .leading, spacing: 4) {
            Text(text).font(.footnote).foregroundStyle(.secondary)
            Text(sync.status).font(.caption2).foregroundStyle(.tertiary)
        }
    }
}

/// One day in the month grid: one colour for one mood, a small grid of tiles for several.
struct DayCell: View {
    @Environment(AppStore.self) private var store
    let day: String

    var body: some View {
        let moods = store.moods(on: day)
        let isToday = day == Day.today
        let future = day > Day.today
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(moods.count == 1 ? Palette.mood(moods[0]) : Color.secondary.opacity(future ? 0.05 : 0.12))
            if moods.count == 1 {
                FaceView(mood: moods[0], ink: Palette.faceInk(moods[0]), outline: false)
                    .padding(9)
                    .padding(.top, 6)
            } else if moods.count > 1 {
                tiles(moods)
                    .padding(.top, 15)
            }
            HStack(spacing: 3) {
                Text(dayNumber)
                    .font(.system(size: 10, weight: isToday ? .bold : .regular))
                    .foregroundStyle(moods.count == 1 ? Palette.faceInk(moods[0]) : Color.primary)
                    .opacity(future ? 0.35 : 0.85)
                Spacer(minLength: 0)
                if !store.events(on: day).isEmpty {
                    Image(systemName: "flag.fill").font(.system(size: 7))
                }
                if !store.dreams(on: day).isEmpty {
                    Image(systemName: "moon.fill").font(.system(size: 7))
                }
            }
            .padding(.horizontal, 5)
            .padding(.top, 3)
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            if isToday {
                RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color.primary, lineWidth: 1.5)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibility(moods))
    }

    private var dayNumber: String {
        return String(Int(day.suffix(2)) ?? 0)
    }

    private func accessibility(_ moods: [Int]) -> String {
        let date = Day.pretty(day)
        if moods.isEmpty { return tr("%@, nothing logged", date) }
        return "\(date), " + moods.map { Mood.name($0) }.joined(separator: ", ")
    }

    @ViewBuilder
    private func tiles(_ moods: [Int]) -> some View {
        let shown = Array(moods.prefix(9))
        let cols = Int(ceil(sqrt(Double(shown.count))))
        let rows = Int(ceil(Double(shown.count) / Double(cols)))
        VStack(spacing: 2) {
            ForEach(0..<rows, id: \.self) { r in
                HStack(spacing: 2) {
                    ForEach(0..<cols, id: \.self) { c in
                        let i = r * cols + c
                        if i < shown.count {
                            MoodTile(mood: shown[i])
                        } else {
                            Rectangle().fill(Color.clear)
                        }
                    }
                }
            }
        }
    }
}

/// One entry's tile inside a day: its colour, plus the face when the tile is big enough.
struct MoodTile: View {
    let mood: Int

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            ZStack {
                Rectangle().fill(Palette.mood(mood))
                if side >= 14 {
                    FaceView(mood: mood, ink: Palette.faceInk(mood), outline: false)
                        .frame(width: side * 0.8, height: side * 0.8)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}
