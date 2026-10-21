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

    /// D0.2: price feed for the fake environment — "fake" (simulated, default) or "finnhub" (real prices, opt-in in
    /// the Developer menu). Paper/production always use Finnhub.
    static var fakePriceFeed: String {
        get { UserDefaults.standard.string(forKey: "fakePriceFeed.v2") ?? "fake" }
        set { UserDefaults.standard.set(newValue, forKey: "fakePriceFeed.v2") }
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
