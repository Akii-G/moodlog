import Foundation

/// Translated text, keyed by the English text (it.lproj/Localizable.strings for Italian).
/// SwiftUI translates plain literals such as Text("Save") by itself; tr() is for text
/// built in code. Placeholders are always %@, so every value is passed as a String.
func tr(_ text: String) -> String {
    return NSLocalizedString(text, comment: "")
}

func tr(_ format: String, _ args: String...) -> String {
    return String(format: NSLocalizedString(format, comment: ""), arguments: args)
}

/// One form for 1, another otherwise (English and Italian both work this way).
func trn(_ one: String, _ many: String, _ n: Int, _ args: String...) -> String {
    let format = NSLocalizedString(n == 1 ? one : many, comment: "")
    return String(format: format, arguments: [String(n)] + args)
}
