import Foundation
import TraderCore
import UserNotifications

/// macOS notifications: user action needed (2FA pending, login failed/required) and order events (D6.2).
@MainActor
final class Notifier: NSObject, UNUserNotificationCenterDelegate {
    func requestAuthorization() {
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    /// Show banners even while the app is in front — that is when orders usually fill.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                            withCompletionHandler completionHandler:
                                            @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func post(_ notification: StatusNotification) {
        post(title: notification.title, body: notification.body, id: "status")
    }

    /// D6.2: one notification per order, replaced in place as it fills (no spam for partial fills).
    func post(_ notification: OrderNotification, for order: Order) {
        post(title: notification.title(for: order), body: notification.body(for: order), id: "order-\(order.orderId)",
             sound: notification != .partiallyFilled)
    }

    private func post(title: String, body: String, id: String, sound: Bool = true) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        if sound { content.sound = .default }
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }
}
