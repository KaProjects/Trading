import TraderCore
import UserNotifications

/// macOS notifications when user action is needed (2FA pending, login failed, login required).
@MainActor
final class Notifier {
    func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    func post(_ notification: StatusNotification) {
        let content = UNMutableNotificationContent()
        content.title = notification.title
        content.body = notification.body
        content.sound = .default
        let request = UNNotificationRequest(identifier: "status", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
