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

    /// Debug builds, fake environment: the Planner starts with these rows pre-filled (ticker, volume in thousands),
    /// so they don't have to be typed after every restart.
    static let devPlannerPreset: [(symbol: String, volume: String, expectedReturn: String)] = [
        ("SNDK", "30", "15"), ("MU", "30", "12"),
    ]

    /// Developer menu, Debug + fake only: which dev data the fake environment starts with (all on by default).
    /// seedTransactions = the default dev data (the seed's positions, open order and fills); devPlans = the Planner prefill rows plus the Execution /
    /// History plans.
    enum DevData: String, CaseIterable {
        case seedTransactions, devPlans

        var label: String {
            switch self {
            case .seedTransactions: "Default Dev Data"
            case .devPlans: "Dev Plans (Planner, Execution, History)"
            }
        }

        var isOn: Bool {
            get { UserDefaults.standard.object(forKey: "devData.\(rawValue)") as? Bool ?? true }
            nonmutating set { UserDefaults.standard.set(newValue, forKey: "devData.\(rawValue)") }
        }
    }

    /// Developer menu, Debug + fake only: let the planner place orders while the US market is closed (testing in
    /// the evening). Off by default; never applies to paper or production.
    static var devAllowPlacingWhenClosed: Bool {
        get { isDebugBuild && defaultBroker == .fake && UserDefaults.standard.bool(forKey: "devAllowPlacingWhenClosed") }
        set { UserDefaults.standard.set(newValue, forKey: "devAllowPlacingWhenClosed") }
    }

    /// Implemented broker adapters: fake, paper (T1). Production comes with T7.
    static let available: Set<BrokerEnvironment> = [.fake, .paper]

    /// The environment this run uses. Release: production. Debug: fake or paper, chosen in the Developer menu
    /// (switching relaunches the app).
    static var selected: BrokerEnvironment {
        guard isDebugBuild else { return defaultBroker }
        let saved = UserDefaults.standard.string(forKey: "debugEnvironment").flatMap(BrokerEnvironment.init)
        return saved.flatMap { available.contains($0) && $0 != .production ? $0 : nil } ?? defaultBroker
    }

    static func select(_ environment: BrokerEnvironment) {
        UserDefaults.standard.set(environment.rawValue, forKey: "debugEnvironment")
    }

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
