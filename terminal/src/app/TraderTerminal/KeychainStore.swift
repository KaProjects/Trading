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

    // MARK: - IBKR login (D1.3): username + password per environment, behind Touch ID. Read with the context of
    // the app unlock, so Log in doesn't prompt again. Whether they exist is remembered in UserDefaults (no secret).

    private struct IbkrLogin: Codable {
        let username: String
        let password: String
    }

    static func ibkrCredentials(environment: String, context: LAContext?) throws -> (username: String, password: String)? {
        guard let data = try read(account: "ibkr-login.\(environment)", context: context),
              let login = try? JSONDecoder().decode(IbkrLogin.self, from: data) else { return nil }
        return (login.username, login.password)
    }

    static func saveIbkrCredentials(environment: String, username: String, password: String) throws {
        deleteIbkrCredentials(environment: environment)
        let data = try JSONEncoder().encode(IbkrLogin(username: username, password: password))
        try add(account: "ibkr-login.\(environment)", value: data, userPresence: true)
        UserDefaults.standard.set(true, forKey: "ibkrLoginSaved.\(environment)")
    }

    static func deleteIbkrCredentials(environment: String) {
        delete(account: "ibkr-login.\(environment)")
        UserDefaults.standard.removeObject(forKey: "ibkrLoginSaved.\(environment)")
    }

    static func hasIbkrCredentials(environment: String) -> Bool {
        UserDefaults.standard.bool(forKey: "ibkrLoginSaved.\(environment)")
    }

    // MARK: - Finnhub API key (D4.2): market data only, no account access → no Touch ID. Readable only by this
    // app (code signature + keychain access group) while the Mac is unlocked; never leaves this device.

    private static let finnhubAccount = "finnhub-api-key.app"
    private static let legacyFinnhubAccount = "finnhub-api-key"  // T4 first cut, Touch ID protected

    static func finnhubKey() throws -> String? {
        try read(account: finnhubAccount, context: nil).map { String(decoding: $0, as: UTF8.self) }
    }

    static func saveFinnhubKey(_ key: String) throws {
        deleteFinnhubKey()
        try add(account: finnhubAccount, value: Data(key.utf8), userPresence: false)
    }

    static func deleteFinnhubKey() {
        delete(account: finnhubAccount)
    }

    /// The old copy can't be read without a fingerprint; drop it (deleting needs no authentication).
    static func removeLegacyFinnhubKey() {
        delete(account: legacyFinnhubAccount)
    }

    static var hasFinnhubKey: Bool { (try? finnhubKey()) != nil }

    private static func delete(account: String) {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: account,
            kSecUseDataProtectionKeychain: true,
        ]
        SecItemDelete(query as CFDictionary)
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
