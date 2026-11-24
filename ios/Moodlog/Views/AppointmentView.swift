import SwiftUI

/// Before the appointment: when it is, a calm recap since the last one, and the things to talk
/// about. Opened from the card in the diary or from Events.
struct AppointmentView: View {
    @Environment(AppStore.self) private var store
    @State private var date = Date()
    @State private var hasTime = false
    @State private var time = Day.time("09:00", on: Day.today) ?? Date()
    @State private var with = ""
    @State private var newPoint = ""
    @State private var loaded = false
    @State private var saved = false

    var body: some View {
        let appt = store.appointment
        let hasDate = !(appt?.date.isEmpty ?? true)
        let today = Day.today
        Form {
            Section {
                Text("When is your next appointment with your doctor or therapist? In the week before, the Log shows how things have gone since the last one.")
                    .font(.subheadline).foregroundStyle(.secondary)
                DatePicker("Date", selection: $date, displayedComponents: .date)
                Toggle("Time", isOn: $hasTime)
                if hasTime {
                    DatePicker("At", selection: $time, displayedComponents: .hourAndMinute)
                }
                TextField("With (optional), e.g. Dr Rossi", text: $with)
                Button {
                    store.setAppointment(date: Day.string(date), time: hasTime ? Day.timeString(time) : "", with: with)
                    saved = true
                } label: {
                    Text(saved ? tr("Saved") : tr("Save the date"))
                }
                if hasDate {
                    Button("Remove the appointment", role: .destructive) {
                        store.setAppointment(date: nil)
                        saved = false
                    }
                }
            } footer: {
                VStack(alignment: .leading, spacing: 6) {
                    if let a = appt, hasDate { Text(Gently.when(a)) }
                    Text(Gently.recap(store, from: Gently.recapStart(appt, today: today), to: today))
                }
            }
            Section {
                HStack {
                    TextField("e.g. I sleep badly since the new dose", text: $newPoint)
                        .onSubmit(add)
                    Button("Add", action: add)
                        .disabled(newPoint.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                let sorted = store.points.sorted { ($0.done ? 1 : 0, $0.updated) < ($1.done ? 1 : 0, $1.updated) }
                ForEach(sorted) { point in
                    Button {
                        store.setDone(point, !point.done)
                    } label: {
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Image(systemName: point.done ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(point.done ? Color.accentColor : .secondary)
                            Text(point.text).foregroundStyle(point.done ? .secondary : .primary)
                            Spacer(minLength: 0)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(point.text + (point.done ? ", " + tr("Said it") : ""))
                }
                .onDelete { offsets in
                    for i in offsets { store.delete(sorted[i]) }
                }
            } header: {
                Text("Things I want to talk about")
            } footer: {
                Text("Tick what you've said. The rest can go in the doctor report, if you choose so there.")
            }
        }
        .navigationTitle("Before the appointment")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            if let a = appt, !a.date.isEmpty {
                date = Day.date(a.date) ?? Date()
                hasTime = !a.time.isEmpty
                if hasTime { time = Day.time(a.time, on: a.date) ?? time }
                with = a.with
                saved = true
            }
        }
        .onChange(of: date) { _, _ in saved = false }
        .onChange(of: hasTime) { _, _ in saved = false }
        .onChange(of: time) { _, _ in saved = false }
        .onChange(of: with) { _, _ in saved = false }
    }

    private func add() {
        store.addPoint(newPoint)
        newPoint = ""
    }
}

/// In the diary, from a week before the appointment until the day after.
struct AppointmentCard: View {
    @Environment(AppStore.self) private var store
    let appointment: Appointment

    var body: some View {
        let today = Day.today
        let open = store.openPoints.count
        NavigationLink(value: AppointmentRoute()) {
            VStack(alignment: .leading, spacing: 6) {
                Label {
                    Text(tr("Your appointment") + " · " + Gently.when(appointment, today: today))
                } icon: {
                    Image(systemName: "stethoscope")
                }
                .font(.headline)
                Text(Gently.recap(store, from: Gently.recapStart(appointment, today: today), to: today))
                    .font(.subheadline)
                Text(open > 0 ? trn("%@ thing to talk about", "%@ things to talk about", open)
                              : tr("Anything you want to remember to say? Add it here."))
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.leading)
            .foregroundStyle(.primary)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// "Your week, gently": on Sunday evening and on Monday.
struct WeekCard: View {
    let text: String
    let onHide: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Your week", systemImage: "leaf")
                .font(.headline)
            Text(text).font(.subheadline)
            Button("Close", action: onHide).font(.subheadline)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
