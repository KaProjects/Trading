import Foundation

/// Money and quantities travel as decimal strings ("33640.00") and are never floats.
public struct Amount: Codable, Sendable, Hashable, Comparable, CustomStringConvertible {
    public let value: Decimal

    public init(_ value: Decimal) { self.value = value }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let text = try container.decode(String.self)
        guard let value = Decimal(string: text, locale: Locale(identifier: "en_US_POSIX")) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "not a decimal: \(text)")
        }
        self.value = value
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }

    public var description: String { NSDecimalNumber(decimal: value).stringValue }

    public static func < (lhs: Amount, rhs: Amount) -> Bool { lhs.value < rhs.value }
}

public struct Contract: Codable, Sendable, Hashable {
    public let conid: Int
    public let symbol: String
    public let name: String
    public let secType: String
    public let exchange: String
    public let primaryExchange: String
    public let currency: String

    public init(conid: Int, symbol: String, name: String, secType: String, exchange: String,
                primaryExchange: String, currency: String) {
        self.conid = conid
        self.symbol = symbol
        self.name = name
        self.secType = secType
        self.exchange = exchange
        self.primaryExchange = primaryExchange
        self.currency = currency
    }
}

public struct CashBalance: Codable, Sendable, Hashable {
    public let currency: String
    public let amount: Amount
}

public struct AccountSummary: Codable, Sendable, Equatable {
    public let accountId: String
    public let baseCurrency: String
    public let netLiquidation: Amount
    public let totalCash: Amount
    public let availableFunds: Amount
    public let buyingPower: Amount
    public let cash: [CashBalance]
}

public struct Position: Codable, Sendable, Identifiable, Hashable {
    public let contract: Contract
    public let quantity: Amount
    public let avgCost: Amount
    public let costBasis: Amount
    public var id: Int { contract.conid }
}

public enum OrderSide: String, Codable, Sendable { case buy = "BUY", sell = "SELL" }

public struct Order: Codable, Sendable, Identifiable, Hashable {
    public let orderId: Int
    public let orderRef: String
    public let contract: Contract
    public let side: OrderSide
    public let quantity: Amount
    public let limitPrice: Amount
    public let tif: String
    public let outsideRth: Bool
    public let status: String
    public let filled: Amount
    public let remaining: Amount
    public let avgFillPrice: Amount?
    public let rejectReason: String?
    public let createdAt: Date
    public let updatedAt: Date
    public var id: Int { orderId }
}

public struct Fill: Codable, Sendable, Identifiable, Hashable {
    public let execId: String
    public let orderId: Int?
    public let orderRef: String?
    public let contract: Contract
    public let side: OrderSide
    public let quantity: Amount
    public let price: Amount
    public let commission: Amount
    public let commissionCurrency: String
    public let time: Date
    public var id: String { execId }
}

/// Last saved account state (D3.1), shown greyed out while not connected.
public struct AccountSnapshot: Codable, Sendable, Equatable {
    public let asOf: Date
    public let account: AccountSummary
    public let positions: [Position]
    public let orders: [Order]
    public let fills: [Fill]

    public init(asOf: Date, account: AccountSummary, positions: [Position], orders: [Order], fills: [Fill]) {
        self.asOf = asOf
        self.account = account
        self.positions = positions
        self.orders = orders
        self.fills = fills
    }
}
