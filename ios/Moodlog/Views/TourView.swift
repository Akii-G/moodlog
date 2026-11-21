import SwiftUI

/// The welcome tour: shown once on a new install, and again from Settings.
/// Each step can switch to a tab, so the person sees each screen while it is explained.
@Observable
final class TourState {
    var step: Int?
    /// Screen frames of things the tour can point at, reported by `.tourTarget(_:)`.
    var frames: [String: CGRect] = [:]

    func start() { step = 0 }
}

struct TourStep {
    let tab: Int?         // switch to this tab
    let target: String?   // spotlight this view (see .tourTarget)
    let pointsAtTabs: Bool
    let title: String
    let text: String
    var customize = false // the card shows the Customize options
}

/// Where a tab sits in the tab bar (0...), when Dreams may be hidden.
func tabPosition(_ tag: Int) -> (index: Int, count: Int) {
    Prefs.dreams ? (tag, 5) : (tag > 2 ? tag - 1 : tag, 4)
}

enum Tour {
    static var steps: [TourStep] {
        return all.filter { Prefs.dreams || $0.tab != 2 }
    }

    private static var all: [TourStep] {
        return [
            TourStep(tab: 0, target: nil, pointsAtTabs: false,
                     title: tr("Welcome to Moodlog"),
                     text: tr("Hey! No pressure, I'll help you learn how the interface works. Don't get overwhelmed, it's quite simple, I'll take care of you.")),
            TourStep(tab: 0, target: nil, pointsAtTabs: true,
                     title: tr("Five tabs, down here"),
                     text: tr("Down here are the tabs. You'll spend most of your time in Log; the others are there whenever you want them. Let's have a quick look at each.")),
            TourStep(tab: 0, target: "calendar", pointsAtTabs: false,
                     title: tr("Your month at a glance"),
                     text: tr("Each day takes the colour of your mood. Tap a day to see it or to log how you feel: pick a face, that's the whole job. Energy, anxiety and a note are optional.")),
            TourStep(tab: 1, target: nil, pointsAtTabs: true,
                     title: tr("Charts fill in on their own"),
                     text: tr("Your days turn into charts. Nothing to set up: the more you log, the more they show.")),
            TourStep(tab: 2, target: nil, pointsAtTabs: true,
                     title: tr("Dreams, if you remember them"),
                     text: tr("A small dream journal, for the mornings you remember something. Totally optional.")),
            TourStep(tab: 3, target: nil, pointsAtTabs: true,
                     title: tr("Mark what matters"),
                     text: tr("Started a new medication, or something big happened? Add it here, and Moodlog shows how you felt in the two weeks before and after.")),
            TourStep(tab: 4, target: nil, pointsAtTabs: true,
                     title: tr("Your PC and Apple Health"),
                     text: tr("Here you can connect to Moodlog on your PC and send sleep and steps from Apple Health. All optional: the app works on its own too. You can watch this tour again from here.")),
            TourStep(tab: 0, target: nil, pointsAtTabs: false,
                     title: tr("Customize your experience"),
                     text: tr("Turn off what you don't need. Nothing is deleted, and you can change this any time in Settings."),
                     customize: true),
            TourStep(tab: 0, target: nil, pointsAtTabs: false,
                     title: tr("That's all, really"),
                     text: tr("Everything stays on your phone, private. Log a mood whenever you feel like it; even once a day is plenty. You've got this.")),
        ]
    }
}

extension View {
    /// Lets the tour put a spotlight on this view.
    func tourTarget(_ name: String, in tour: TourState) -> some View {
        background(GeometryReader { g in
            Color.clear
                .onAppear { tour.frames[name] = g.frame(in: .global) }
                .onChange(of: g.frame(in: .global)) { _, frame in tour.frames[name] = frame }
        })
    }
}

/// Dims the screen except for a spotlight (or, on tab steps, keeps the screen visible),
/// leaves the tab bar visible and points at the tab being explained.
struct TourOverlay: View {
    @Bindable var tour: TourState
    @Binding var tab: Int
    let onFinish: () -> Void

    var body: some View {
        GeometryReader { geo in
            let steps = Tour.steps
            let index = min(tour.step ?? 0, steps.count - 1)
            let step = steps[index]
            let size = geo.size
            let barHeight = geo.safeAreaInsets.bottom + 54          // the tab bar, left visible
            let hole = step.target.flatMap { tour.frames[$0] }?.insetBy(dx: -6, dy: -6) ?? .zero
            let centred = step.target == nil && !step.pointsAtTabs
            let place = tabPosition(step.tab ?? 0)
            let tabX = (CGFloat(place.index) + 0.5) * size.width / CGFloat(place.count)
            let cardWidth = min(size.width - 32, 360)
            let lift = size.height - barHeight
            let roomBelow = hole != .zero && hole.maxY + 200 < lift
            let cardY: CGFloat = centred ? size.height / 2 : (roomBelow ? hole.maxY + 120 : lift - 240)
            let showScreen = step.pointsAtTabs && step.target == nil && index > 1
            let dim: Double = showScreen ? 0.18 : 0.6

            ZStack(alignment: .topLeading) {
                Spotlight(hole: hole, bottom: barHeight)
                    .fill(Color.black.opacity(dim), style: FillStyle(eoFill: true))
                    .contentShape(Rectangle())
                    .onTapGesture {}                                  // taps stay on the tour
                if hole != .zero {
                    PulsingRing(rect: hole)
                }
                if step.pointsAtTabs {
                    PointerLine(from: CGPoint(x: min(max(tabX, 40), size.width - 40), y: cardY + 90),
                                to: CGPoint(x: tabX, y: lift + 6))
                        .id(index)
                        .transition(.opacity)
                }
                card(step: step, index: index, count: steps.count)
                    .frame(width: cardWidth)
                    .position(x: size.width / 2, y: cardY)
            }
            .animation(.spring(response: 0.5, dampingFraction: 0.86), value: index)
            .animation(.spring(response: 0.5, dampingFraction: 0.86), value: hole)
        }
        .ignoresSafeArea()
        .onChange(of: tour.step) { _, new in
            if let new = new, new < Tour.steps.count, let t = Tour.steps[new].tab {
                withAnimation { tab = t }
            }
        }
        .onAppear { tab = 0 }
    }

    private func card(step: TourStep, index: Int, count: Int) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if step.target == nil && !step.pointsAtTabs && !step.customize {
                TourFace().frame(width: 76, height: 76).frame(maxWidth: .infinity)
            }
            Text(step.title).font(.title3.weight(.semibold))
            Text(step.text).font(.subheadline).fixedSize(horizontal: false, vertical: true)
            if step.customize {
                ScrollView {   // the list grows; the card must still fit the screen
                    VStack(alignment: .leading, spacing: 12) { CustomizeOptions() }
                        .font(.subheadline)
                }
                .frame(maxHeight: 330)
            }
            HStack(spacing: 5) {
                ForEach(0..<count, id: \.self) { i in
                    Circle().fill(i == index ? Color.primary : Color.secondary.opacity(0.35))
                        .frame(width: 6, height: 6)
                }
            }
            HStack {
                if index < count - 1 {
                    Button(tr("Skip tour")) { onFinish() }
                        .buttonStyle(.borderless)
                }
                Spacer()
                if index > 0 {
                    Button(tr("Back")) { tour.step = index - 1 }
                        .buttonStyle(.bordered)
                }
                Button(index == count - 1 ? tr("Let's start") : tr("Next")) {
                    if index == count - 1 { onFinish() } else { tour.step = index + 1 }
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(18)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.25), radius: 20, y: 8)
        .id(index)
        .transition(.asymmetric(insertion: .opacity.combined(with: .scale(scale: 0.96)), removal: .opacity))
    }
}

/// The dimmed area: the whole screen above the tab bar, minus a rounded hole.
struct Spotlight: Shape {
    var hole: CGRect
    var bottom: CGFloat

    var animatableData: AnimatablePair<AnimatablePair<CGFloat, CGFloat>, AnimatablePair<CGFloat, CGFloat>> {
        get { AnimatablePair(AnimatablePair(hole.minX, hole.minY), AnimatablePair(hole.width, hole.height)) }
        set { hole = CGRect(x: newValue.first.first, y: newValue.first.second,
                            width: newValue.second.first, height: newValue.second.second) }
    }

    func path(in rect: CGRect) -> Path {
        var p = Path(CGRect(x: 0, y: 0, width: rect.width, height: rect.height - bottom))
        if hole.width > 1 && hole.height > 1 {
            p.addRoundedRect(in: hole, cornerSize: CGSize(width: 16, height: 16), style: .continuous)
        }
        return p
    }
}

struct PulsingRing: View {
    let rect: CGRect

    var body: some View {
        TimelineView(.animation) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.accentColor.opacity(0.55 + 0.45 * (0.5 + 0.5 * sin(t * 3.2))), lineWidth: 3)
                .frame(width: rect.width, height: rect.height)
                .position(x: rect.midX, y: rect.midY)
        }
        .allowsHitTesting(false)
    }
}

/// A curved line with a dot at its end, from the card down to a tab.
struct PointerLine: View {
    let from: CGPoint
    let to: CGPoint
    @State private var drawn: CGFloat = 0

    var body: some View {
        ZStack {
            Path { p in
                p.move(to: from)
                p.addCurve(to: to, control1: CGPoint(x: from.x, y: (from.y + to.y) / 2),
                           control2: CGPoint(x: to.x, y: (from.y + to.y) / 2))
            }
            .trim(from: 0, to: drawn)
            .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
            Circle().fill(Color.accentColor).frame(width: 9, height: 9)
                .position(to)
                .opacity(drawn > 0.95 ? 1 : 0)
        }
        .allowsHitTesting(false)
        .onAppear { withAnimation(.easeOut(duration: 0.45).delay(0.2)) { drawn = 1 } }
    }
}

/// A face that pops in, bobs gently and smiles more and more.
struct TourFace: View {
    @State private var start = Date()

    var body: some View {
        TimelineView(.animation) { context in
            let t = context.date.timeIntervalSince(start)
            let mood = t < 0.6 ? 3 : (t < 1.3 ? 4 : 5)
            ZStack {
                Circle().fill(Palette.mood(mood))
                FaceView(mood: mood, ink: Palette.faceInk(mood), outline: false).padding(8)
            }
            .scaleEffect(0.85 + 0.15 * min(1, t / 0.5))
            .offset(y: sin(t * 2.4) * 3)
        }
    }
}
