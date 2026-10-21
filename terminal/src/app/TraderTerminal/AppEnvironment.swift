import Foundation
import TraderCore

/// Environment is fixed at build time: Debug = fake/paper, Release = production only.
enum AppEnvironment {
    #if DEBUG
    static let isDebugBuild = true
    static let defaultBroker: BrokerEnvironment = .fake
    #else
    static let isDebugBuild = false
    static let defaultBroker: BrokerEnvironment = .production
    #endif

    /// Debug builds, fake environment: unlock without Touch ID and log in to the fake broker at launch.
    /// Never applies to paper or production. Toggle in the Developer menu (stored in UserDefaults).
    static var fakeAutoLogin: Bool {
        get { isDebugBuild && (UserDefaults.standard.object(forKey: "fakeAutoLogin") as? Bool ?? true) }
        set { UserDefaults.standard.set(newValue, forKey: "fakeAutoLogin") }
    }

    /// Implemented broker adapters (paper and production arrive with T1/T7).
    static let available: Set<BrokerEnvironment> = [.fake]

    static var supportDirectory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent(Bundle.main.bundleIdentifier ?? "org.kaleta.trader-terminal.dev")
    }

    static var socketPath: String {
        supportDirectory.appendingPathComponent("service.sock").path
    }

    static var serviceExecutable: URL? {
        guard let directory = Bundle.main.object(forInfoDictionaryKey: "TTServiceDirectory") as? String else {
            return nil
        }
        return URL(fileURLWithPath: directory).appendingPathComponent(".venv/bin/trader-service").standardized
    }
}
