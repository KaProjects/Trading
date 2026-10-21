import Foundation

public enum TimeInForce: String, Codable, Sendable, CaseIterable { case day = "DAY", gtc = "GTC" }

/// Order ticket input (T5). `orderRef` makes a retried send safe: same ref → same order, never a second one.
public struct OrderRequest: Codable, Sendable, Equatable {
    public var conid: Int
    public var side: OrderSide
    public var quantity: Amount
    public var limitPrice: Amount
    public var tif: TimeInForce
    public var outsideRth: Bool
    public var orderRef: String?

    public init(conid: Int, side: OrderSide, quantity: Amount, limitPrice: Amount, tif: TimeInForce = .day,
                outsideRth: Bool = false, orderRef: String? = nil) {
        self.conid = conid
        self.side = side
        self.quantity = quantity
        self.limitPrice = limitPrice
        self.tif = tif
        self.outsideRth = outsideRth
        self.orderRef = orderRef
    }
}

public enum WarningLevel: String, Codable, Sendable { case warn, strong }

public struct OrderWarning: Codable, Sendable, Hashable {
    public let code: String
    public let level: WarningLevel
    public let message: String
}

public struct OrderBlock: Codable, Sendable, Hashable {
    public let code: String
    public let message: String
}

public struct OrderPreview: Codable, Sendable, Equatable {
    public let contract: Contract
    public let value: Amount
    public let valueBase: Amount
    public let pctOfAccount: Amount?
    public let lastPrice: Amount?
    public let warnings: [OrderWarning]
    public let block: OrderBlock?
}

public struct RuleThresholds: Codable, Sendable, Equatable {
    public var pctOfAccountWarn: Amount
    public var pctOfAccountStrong: Amount
    public var orderValueWarn: Amount
    public var quantityWarn: Amount
    public var priceDeviationWarn: Amount

    public init(pctOfAccountWarn: Amount, pctOfAccountStrong: Amount, orderValueWarn: Amount, quantityWarn: Amount,
                priceDeviationWarn: Amount) {
        self.pctOfAccountWarn = pctOfAccountWarn
        self.pctOfAccountStrong = pctOfAccountStrong
        self.orderValueWarn = orderValueWarn
        self.quantityWarn = quantityWarn
        self.priceDeviationWarn = priceDeviationWarn
    }
}

/// One-line order summary for the confirmation dialog (T5):
/// `BUY 100 AAPL (Apple Inc · NASDAQ · USD) LMT 182.50 DAY ≈ 18,250.00 USD`
public enum OrderSummary {
    public static func line(_ request: OrderRequest, contract: Contract, value: Amount,
                            locale: Locale = .current) -> String {
        let number = Decimal.FormatStyle.number.locale(locale)
        let quantity = request.quantity.value.formatted(number.precision(.fractionLength(0...4)))
        let limit = request.limitPrice.value.formatted(number.precision(.fractionLength(2...4)))
        let total = value.value.formatted(number.precision(.fractionLength(2)))
        let session = request.outsideRth ? " +ext hours" : ""
        return "\(request.side.rawValue) \(quantity) \(contract.symbol) (\(contract.name) · "
            + "\(contract.primaryExchange) · \(contract.currency)) LMT \(limit) \(request.tif.rawValue)\(session) "
            + "≈ \(total) \(contract.currency)"
    }
}

public struct Quote: Codable, Sendable, Equatable {
    public let symbol: String
    public let last: Amount
    public let prevClose: Amount
    public let changePct: Amount
    public let time: Date
}

/// Parses what the user typed: accepts the Mac's locale ("182,50" in Czech) and plain "182.50".
public enum DecimalInput {
    public static func parse(_ text: String, locale: Locale = .current) -> Decimal? {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        let separator = locale.decimalSeparator ?? "."
        let grouping = locale.groupingSeparator ?? ","
        var normalized = trimmed.replacingOccurrences(of: "\u{00A0}", with: "")
            .replacingOccurrences(of: "\u{202F}", with: "")
        if separator != "." {
            if grouping != separator { normalized = normalized.replacingOccurrences(of: grouping, with: "") }
            normalized = normalized.replacingOccurrences(of: separator, with: ".")
        } else {
            normalized = normalized.replacingOccurrences(of: ",", with: "")
        }
        normalized = normalized.replacingOccurrences(of: " ", with: "")
        guard normalized.range(of: #"^\d+(\.\d+)?$"#, options: .regularExpression) != nil else { return nil }
        return Decimal(string: normalized, locale: Locale(identifier: "en_US_POSIX"))
    }
}
