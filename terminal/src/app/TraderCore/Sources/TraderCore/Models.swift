import Foundation

public enum BrokerEnvironment: String, Codable, Sendable, CaseIterable {
    case fake, paper, production

    public var label: String {
        switch self {
        case .fake: "Fake"
        case .paper: "Paper"
        case .production: "Live"
        }
    }

    public var badge: String { label.uppercased() }
}

public enum ConnectionState: String, Codable, Sendable {
    case disconnected
    case loggingIn = "logging_in"
    case connected
    case reconnecting
    case loginRequired = "login_required"
    case loginFailed = "login_failed"
}

public struct BrokerStatus: Codable, Sendable, Equatable {
    public let state: ConnectionState
    public let environment: BrokerEnvironment
    public let accountId: String?
    public let since: Date
    public let message: String?

    public init(state: ConnectionState, environment: BrokerEnvironment, accountId: String? = nil,
                since: Date = Date(), message: String? = nil) {
        self.state = state
        self.environment = environment
        self.accountId = accountId
        self.since = since
        self.message = message
    }
}

public struct ServiceEvent: Sendable {
    public let type: String
    /// Raw JSON of the event's `data` member, decoded per type by the consumer.
    public let data: Data

    public static func parse(_ message: Data) throws -> ServiceEvent {
        guard let object = try JSONSerialization.jsonObject(with: message) as? [String: Any],
              let type = object["type"] as? String else {
            throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "event without type"))
        }
        let data = try JSONSerialization.data(withJSONObject: object["data"] ?? [:])
        return ServiceEvent(type: type, data: data)
    }
}

public enum ServiceJSON {
    public static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .custom { decoder in
            let text = try decoder.singleValueContainer().decode(String.self)
            if let date = parseDate(text) { return date }
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath,
                                                    debugDescription: "bad date \(text)"))
        }
        return decoder
    }()

    public static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()

    /// Python emits ISO 8601 with microseconds and `Z` (e.g. 2026-10-21T15:14:08.168840Z).
    static func parseDate(_ text: String) -> Date? {
        let fractional = Date.ISO8601FormatStyle(includingFractionalSeconds: true)
        if let date = try? fractional.parse(text) { return date }
        return try? Date.ISO8601FormatStyle().parse(text)
    }
}
