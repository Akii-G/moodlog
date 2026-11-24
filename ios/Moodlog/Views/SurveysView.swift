import Charts
import SwiftUI

/// Questionnaires: answer PHQ-9 or GAD-7, see the scores over time, set the two-week reminder.
struct SurveysView: View {
    @Environment(AppStore.self) private var store
    @AppStorage("surveyRemind") private var remind = true
    @State private var answering: SurveyKindItem?
    @State private var care = false
    @State private var toDelete: Survey?

    var body: some View {
        Form {
            Section {
                Text("Two short questionnaires doctors use: PHQ-9 about low mood and GAD-7 about anxiety. Each one asks about the last two weeks. Sottovoce shows only your score, and the report can include them for your doctor.")
                    .font(.subheadline).foregroundStyle(.secondary)
                ForEach(SurveyKind.allCases) { kind in
                    Button {
                        answering = SurveyKindItem(kind: kind)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tr("Answer %@", kind.name)).font(.headline)
                            Text(kind.text.about).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            Section {
                Toggle("Remind me every two weeks", isOn: $remind)
                    .onChange(of: remind) { _, on in
                        Task {
                            if on { _ = await Reminder.requestPermission() }
                            SurveyReminder.plan(store)
                        }
                    }
            } footer: {
                Text("A notification two weeks after your last questionnaire. Nothing is sent before your first one.")
            }
            if !store.surveys.isEmpty {
                Section("Scores over time") {
                    SurveyChart(surveys: store.surveys).frame(height: 180)
                }
                Section {
                    ForEach(store.surveys.reversed()) { s in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(tr("%@ · %@ of %@", s.name, "\(s.score)", "\(s.maxScore)")).font(.headline)
                                Text(details(s)).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if s.hidden { Image(systemName: "lock.fill").foregroundStyle(.secondary).font(.caption) }
                        }
                        .swipeActions {
                            Button(role: .destructive) { toDelete = s } label: { Label("Delete", systemImage: "trash") }
                        }
                    }
                } header: {
                    Text("History")
                } footer: {
                    Text("Swipe left to delete one.")
                }
            }
            Section {
                Text(SurveyText.current.credit).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Questionnaires")
        .sheet(item: $answering) { item in
            SurveyAnswerView(kind: item.kind) { survey in
                answering = nil
                if survey.worries {
                    Task { try? await Task.sleep(nanoseconds: 600_000_000); care = true }
                }
            }
        }
        .sheet(isPresented: $care) { SurveyCareView() }
        .confirmationDialog("Delete this questionnaire?", isPresented: Binding(get: { toDelete != nil }, set: { if !$0 { toDelete = nil } }),
                            titleVisibility: .visible) {
            Button("Delete", role: .destructive) { if let s = toDelete { store.delete(s) }; toDelete = nil }
        } message: {
            Text("It's deleted on your other devices too at the next sync.")
        }
    }

    private func details(_ s: Survey) -> String {
        var parts = [Day.pretty(s.date, style: "EEEE d MMMM yyyy")]
        if !s.time.isEmpty { parts.append(s.time) }
        if s.difficulty >= 0 { parts.append(SurveyText.current.difficultyOptions[s.difficulty]) }
        return parts.joined(separator: " · ")
    }
}

struct SurveyKindItem: Identifiable {
    let kind: SurveyKind
    var id: String { kind.rawValue }
}

/// PHQ-9 and GAD-7 scores over time, one line each, on the same points axis.
struct SurveyChart: View {
    let surveys: [Survey]

    var body: some View {
        Chart(surveys) { s in
            LineMark(x: .value("Date", Day.date(s.date) ?? Date()), y: .value("Score", s.score))
                .foregroundStyle(by: .value("Questionnaire", s.name))
            PointMark(x: .value("Date", Day.date(s.date) ?? Date()), y: .value("Score", s.score))
                .foregroundStyle(by: .value("Questionnaire", s.name))
        }
        .chartForegroundStyleScale(["PHQ-9": Color(red: 0.11, green: 0.69, blue: 0.48),
                                    "GAD-7": Color(red: 0.48, green: 0.35, blue: 0.84)])
        .chartYScale(domain: 0...27)
        .chartYAxis { AxisMarks(values: [0, 9, 18, 27]) }
    }
}

/// One questionnaire, with the official words. Saving needs every question answered.
struct SurveyAnswerView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @AppStorage("privateTick") private var privateTick = true
    let kind: SurveyKind
    let onSave: (Survey) -> Void
    @State private var answers: [Int?]
    @State private var difficulty = -1
    @State private var isPrivate = Prefs.privateDefault

    init(kind: SurveyKind, onSave: @escaping (Survey) -> Void) {
        self.kind = kind
        self.onSave = onSave
        _answers = State(initialValue: Array(repeating: nil, count: kind.size))
    }

    private var left: Int { answers.filter { $0 == nil }.count }

    var body: some View {
        let form = kind.text
        NavigationStack {
            Form {
                Section {
                    Text(form.question).font(.headline)
                } header: {
                    Text(form.about)
                }
                ForEach(0..<kind.size, id: \.self) { i in
                    Section {
                        ForEach(0..<4, id: \.self) { value in
                            choice(form.options[value], number: value, selected: answers[i] == value) { answers[i] = value }
                        }
                    } header: {
                        Text("\(i + 1). \(form.items[i])").textCase(nil).font(.subheadline).foregroundStyle(.primary)
                    }
                }
                if answers.contains(where: { ($0 ?? 0) > 0 }) {
                    Section {
                        ForEach(0..<4, id: \.self) { value in
                            choice(SurveyText.current.difficultyOptions[value], number: nil, selected: difficulty == value) {
                                difficulty = value
                            }
                        }
                    } header: {
                        Text(SurveyText.current.difficulty).textCase(nil).font(.subheadline).foregroundStyle(.primary)
                    }
                }
                if privateTick {
                    Section {
                        Toggle("Private: keep it out of the doctor report", isOn: $isPrivate)
                    }
                }
                Section {
                    Text(SurveyText.current.credit).font(.caption2).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(kind.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.disabled(left > 0)
                }
                ToolbarItem(placement: .bottomBar) {
                    Text(left > 0 ? trn("%@ question left", "%@ questions left", left)
                                  : tr("Score: %@ of %@", "\(answers.compactMap { $0 }.reduce(0, +))", "\(kind.maxScore)"))
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        .interactiveDismissDisabled(left < kind.size)
    }

    private func choice(_ label: String, number: Int?, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                if let number { Text("\(number)").monospacedDigit().foregroundStyle(.secondary).frame(width: 18) }
                Text(label).foregroundStyle(.primary)
                Spacer()
                if selected { Image(systemName: "checkmark").foregroundStyle(Color.accentColor).fontWeight(.semibold) }
            }
            .contentShape(Rectangle())
        }
    }

    private func save() {
        let values = answers.compactMap { $0 }
        guard values.count == kind.size else { return }
        let survey = store.addSurvey(kind: kind, answers: values, difficulty: values.contains { $0 > 0 } ? difficulty : -1,
                                     isPrivate: privateTick && isPrivate)
        dismiss()
        onSave(survey)
    }
}

/// After a PHQ-9 with question 9 above "Not at all": kind words and helplines. Moodlog never
/// tells or alerts anyone.
struct SurveyCareView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("You said you've had thoughts of being better off dead or of hurting yourself. That's a lot to carry, and you don't have to carry it alone. Please tell your doctor, or someone you trust, soon. If it feels heavy right now, you can talk to someone today:")
                }
                Section {
                    ForEach(Care.helplines) { line in
                        VStack(alignment: .leading, spacing: 2) {
                            if let number = line.number,
                               let url = URL(string: "tel:" + number.replacingOccurrences(of: " ", with: "")) {
                                Link(destination: url) { Text("\(line.name) · \(number)").font(.headline) }
                            } else if let link = line.link, let url = URL(string: link) {
                                Link(destination: url) { Text("\(line.name) · \(url.host ?? link)").font(.headline) }
                            }
                            Text(line.note).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                } footer: {
                    Text("Sottovoce doesn't tell anyone: this stays on your devices.")
                }
            }
            .navigationTitle("Thank you for answering honestly")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Close") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

#if DEVTOOLS
/// Developer build only (DEVTOOLS): buttons to see both notifications right away.
struct DevNotificationsSection: View {
    @State private var message = ""

    var body: some View {
        Section {
            Button { Task { message = await DevNotifications.daily() } } label: { Text(verbatim: "Daily reminder in 5 seconds") }
            Button { Task { message = await DevNotifications.survey() } } label: { Text(verbatim: "Questionnaire reminder in 5 seconds") }
            Button { Task { message = await DevNotifications.appointment() } } label: { Text(verbatim: "Appointment reminder in 5 seconds") }
            Button { Task { message = await DevNotifications.pending() } } label: { Text(verbatim: "Show planned notifications") }
            if !message.isEmpty { Text(message).font(.footnote).foregroundStyle(.secondary) }
        } header: {
            Text(verbatim: "Developer: test notifications")
        } footer: {
            Text(verbatim: "Only in the developer build. Tap one, then go to the Home Screen or lock the phone. Tap the notification to check it opens the right place.")
        }
    }
}
#endif
