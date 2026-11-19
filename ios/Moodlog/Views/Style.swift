import SwiftUI
import UIKit

extension UIColor {
    convenience init(hex: String) {
        var value: UInt64 = 0
        Scanner(string: hex.replacingOccurrences(of: "#", with: "")).scanHexInt64(&value)
        self.init(red: CGFloat((value >> 16) & 0xff) / 255, green: CGFloat((value >> 8) & 0xff) / 255,
                  blue: CGFloat(value & 0xff) / 255, alpha: 1)
    }
}

/// The same colours as Moodlog on the PC (checked for colour-blind readability there).
enum Palette {
    static let moodLight = [1: "#cf3f3f", 2: "#eb9a94", 3: "#bdbbb3", 4: "#8fbcf0", 5: "#2a78d6"]
    static let moodDark = [1: "#e05252", 2: "#eb9a94", 3: "#cfcdc5", 4: "#8fbcf0", 5: "#3987e5"]

    private static func dynamic(_ light: String, _ dark: String) -> Color {
        return Color(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }

    static func mood(_ value: Int) -> Color {
        return dynamic(moodLight[value] ?? "#bdbbb3", moodDark[value] ?? "#cfcdc5")
    }

    static func faceInk(_ value: Int) -> Color {
        return (value == 1 || value == 5) ? .white : Color(uiColor: UIColor(hex: "#1b1b1b"))
    }

    static let energy = dynamic("#b87c10", "#c08418")
    static let anxiety = dynamic("#7b5ad6", "#9a7ef0")
    static let health = dynamic("#1baf7a", "#199e70")
}

/// The five line-drawn faces, the same drawing as on the PC.
struct FaceView: View {
    let mood: Int
    var ink: Color = .primary
    var outline: Bool = true

    var body: some View {
        Canvas { context, size in
            let r = min(size.width, size.height) / 2 - 1
            let cx = size.width / 2
            let cy = size.height / 2
            let line = StrokeStyle(lineWidth: max(1.4, r * 0.13), lineCap: .round, lineJoin: .round)
            if outline {
                let circle = Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r))
                context.stroke(circle, with: .color(ink), style: line)
            }
            let ex: CGFloat = r * 0.36, ey: CGFloat = r * 0.20, er: CGFloat = max(1, r * 0.11)
            for sx in [CGFloat(-1), CGFloat(1)] {
                let eye = Path(ellipseIn: CGRect(x: cx + sx * ex - er, y: cy - ey - er, width: 2 * er, height: 2 * er))
                context.fill(eye, with: .color(ink))
            }
            let w: CGFloat = r * 0.46, y: CGFloat = cy + r * 0.30
            var mouth = Path()
            switch mood {
            case 1:
                var brows = Path()
                for sx in [CGFloat(-1), CGFloat(1)] {
                    brows.move(to: CGPoint(x: cx + sx * (ex + r * 0.18), y: cy - ey - r * 0.22))
                    brows.addLine(to: CGPoint(x: cx + sx * (ex - r * 0.14), y: cy - ey - r * 0.36))
                }
                context.stroke(brows, with: .color(ink), style: line)
                mouth.move(to: CGPoint(x: cx - w, y: y + r * 0.22))
                mouth.addCurve(to: CGPoint(x: cx + w, y: y + r * 0.22),
                               control1: CGPoint(x: cx - w * 0.5, y: y - r * 0.26),
                               control2: CGPoint(x: cx + w * 0.5, y: y - r * 0.26))
                context.stroke(mouth, with: .color(ink), style: line)
            case 2:
                mouth.move(to: CGPoint(x: cx - w, y: y + r * 0.12))
                mouth.addCurve(to: CGPoint(x: cx + w, y: y + r * 0.12),
                               control1: CGPoint(x: cx - w * 0.5, y: y - r * 0.12),
                               control2: CGPoint(x: cx + w * 0.5, y: y - r * 0.12))
                context.stroke(mouth, with: .color(ink), style: line)
            case 3:
                mouth.move(to: CGPoint(x: cx - w, y: y + r * 0.04))
                mouth.addLine(to: CGPoint(x: cx + w, y: y + r * 0.04))
                context.stroke(mouth, with: .color(ink), style: line)
            case 4:
                mouth.move(to: CGPoint(x: cx - w, y: y - r * 0.08))
                mouth.addCurve(to: CGPoint(x: cx + w, y: y - r * 0.08),
                               control1: CGPoint(x: cx - w * 0.5, y: y + r * 0.24),
                               control2: CGPoint(x: cx + w * 0.5, y: y + r * 0.24))
                context.stroke(mouth, with: .color(ink), style: line)
            default:
                mouth.move(to: CGPoint(x: cx - w * 1.1, y: y - r * 0.14))
                mouth.addLine(to: CGPoint(x: cx + w * 1.1, y: y - r * 0.14))
                mouth.addCurve(to: CGPoint(x: cx - w * 1.1, y: y - r * 0.14),
                               control1: CGPoint(x: cx + w * 0.8, y: y + r * 0.44),
                               control2: CGPoint(x: cx - w * 0.8, y: y + r * 0.44))
                mouth.closeSubpath()
                context.fill(mouth, with: .color(ink))
                context.stroke(mouth, with: .color(ink), style: line)
            }
        }
        .accessibilityLabel(Mood.name(mood))
    }
}

/// A filled circle in the mood colour with the face on it.
struct MoodBadge: View {
    let mood: Int
    var size: CGFloat = 28

    var body: some View {
        ZStack {
            Circle().fill(Palette.mood(mood))
            FaceView(mood: mood, ink: Palette.faceInk(mood), outline: false)
                .padding(size * 0.12)
        }
        .frame(width: size, height: size)
    }
}

/// Five buttons 1-5; tapping the chosen one again clears it (the scales are optional).
struct ScalePicker: View {
    let title: String
    let words: [Int: String]
    let color: Color
    @Binding var value: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(tr(title))
                Spacer()
                Text(value > 0 ? "\(value) · \(words[value] ?? "")" : tr("not set"))
                    .foregroundStyle(.secondary)
                    .font(.subheadline)
            }
            HStack(spacing: 6) {
                ForEach(1...5, id: \.self) { n in
                    Button {
                        value = (value == n) ? 0 : n
                    } label: {
                        Text("\(n)")
                            .font(.body.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(value == n ? color : Color.secondary.opacity(0.15), in: Capsule())
                            .foregroundStyle(value == n ? Color.white : Color.primary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(tr(title)) \(n), \(words[n] ?? "")")
                }
            }
        }
        .padding(.vertical, 4)
    }
}

struct MoodPicker: View {
    @Binding var value: Int

    var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { n in
                Button {
                    value = (value == n) ? 0 : n
                } label: {
                    VStack(spacing: 4) {
                        FaceView(mood: n)
                            .frame(width: 40, height: 40)
                        Text(Mood.name(n))
                            .font(.caption)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(value == n ? Palette.mood(n).opacity(0.45) : Color.clear,
                                in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(value == n ? .isSelected : [])
            }
        }
    }
}
