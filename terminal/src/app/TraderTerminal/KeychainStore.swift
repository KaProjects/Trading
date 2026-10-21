import Foundation
import LocalAuthentication
import Security

enum KeychainError: Error, LocalizedError {
    case status(OSStatus)

    var errorDescription: String? {
        switch self {
        case .status(let code):
            let message = SecCopyErrorMessageString(code, nil) as String? ?? "unknown"
            return "Keychain error \(code): \(message)"
        }
    }
}

/// Data-protection Keychain items behind user presence (Touch ID, or the Mac password as fallback).
/// Reads pass the LAContext from the app unlock (D2.8), so the user is not prompted a second time.
enum KeychainStore {
    private static var service: String { Bundle.main.bundleIdentifier ?? "org.kaleta.trader-terminal.dev" }

    /// The 32-byte key encrypting everything the service persists (D3.2/D3.3). Created on first use.
    /// `userPresence: false` is only for the fake environment's auto log-in (Debug builds): fake data, no Touch ID.
    static func dataKey(environment: String, context: LAContext?, userPresence: Bool = true) throws -> Data {
        let account = userPresence ? "data-key.\(environment)" : "data-key.\(environment).no-touch-id"
        if let existing = try read(account: account, context: context) {
            return existing
        }
        var key = Data(count: 32)
        let result = key.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
        guard result == errSecSuccess else { throw KeychainError.status(result) }
        try add(account: account, value: key, userPresence: userPresence)
        return key
    }

    // MARK: - Finnhub API key (D4.2): Touch ID protected, shared by all environments of this build

    private static let finnhubAccount = "finnhub-api-key"

    static func finnhubKey(context: LAContext?) throws -> String? {
        try read(account: finnhubAccount, context: context).map { String(decoding: $0, as: UTF8.self) }
    }

    static func saveFinnhubKey(_ key: String) throws {
        deleteFinnhubKey()
        try add(account: finnhubAccount, value: Data(key.utf8), userPresence: true)
    }

    static func deleteFinnhubKey() {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: finnhubAccount,
            kSecUseDataProtectionKeychain: true,
        ]
        SecItemDelete(query as CFDictionary)
    }

    static var hasFinnhubKey: Bool {
        let context = LAContext()
        context.interactionNotAllowed = true  // only check existence, never prompt
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: finnhubAccount,
            kSecUseDataProtectionKeychain: true, kSecUseAuthenticationContext: context,
        ]
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }

    private static func read(account: String, context: LAContext?) throws -> Data? {
        var query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecUseDataProtectionKeychain: true,
            kSecReturnData: true,
        ]
        if let context { query[kSecUseAuthenticationContext] = context }
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess: return item as? Data
        case errSecItemNotFound: return nil
        default: throw KeychainError.status(status)
        }
    }

    private static func add(account: String, value: Data, userPresence: Bool) throws {
        var error: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(
            nil, kSecAttrAccessibleWhenUnlockedThisDeviceOnly, userPresence ? .userPresence : [], &error
        ) else {
            throw error!.takeRetainedValue() as Error
        }
        let attributes: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecAttrLabel: "Trader Terminal data key",
            kSecUseDataProtectionKeychain: true,
            kSecAttrAccessControl: access,
            kSecValueData: value,
        ]
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError.status(status) }
    }
}
