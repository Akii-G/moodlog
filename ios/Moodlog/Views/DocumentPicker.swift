import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// iOS's own document picker: to save a file where you choose (`saving`), or to open one.
struct DocumentPicker: UIViewControllerRepresentable {
    var types: [UTType] = []
    var saving: URL? = nil
    let onPick: (URL) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = saving.map { UIDocumentPickerViewController(forExporting: [$0], asCopy: false) }
            ?? UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: false)
        picker.allowsMultipleSelection = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIDocumentPickerViewController, context: Context) {}

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let parent: DocumentPicker
        init(_ parent: DocumentPicker) { self.parent = parent }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            if let url = urls.first { parent.onPick(url) } else { parent.onCancel() }
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            parent.onCancel()
        }
    }
}

/// Shows iOS's document picker directly from UIKit, not inside a SwiftUI sheet: inside a sheet
/// its Save button sometimes doesn't appear.
@MainActor
enum FilePicker {
    private static var current: Delegate?

    static func present(saving file: URL?, types: [UTType] = [.json, .data],
                        onPick: @escaping (URL) -> Void, onCancel: @escaping () -> Void = {}) {
        let picker = file.map { UIDocumentPickerViewController(forExporting: [$0], asCopy: false) }
            ?? UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: false)
        picker.allowsMultipleSelection = false
        let delegate = Delegate(onPick: { url in current = nil; onPick(url) },
                                onCancel: { current = nil; onCancel() })
        current = delegate
        picker.delegate = delegate
        topController()?.present(picker, animated: true)
    }

    private static func topController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        let window = scene?.windows.first { $0.isKeyWindow } ?? scene?.windows.first
        var top = window?.rootViewController
        while let next = top?.presentedViewController { top = next }
        return top
    }

    private final class Delegate: NSObject, UIDocumentPickerDelegate {
        let onPick: (URL) -> Void
        let onCancel: () -> Void
        init(onPick: @escaping (URL) -> Void, onCancel: @escaping () -> Void) {
            self.onPick = onPick
            self.onCancel = onCancel
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            if let url = urls.first { onPick(url) } else { onCancel() }
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { onCancel() }
    }
}
