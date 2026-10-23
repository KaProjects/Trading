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

public enum WarningLevel: String, Codable, Sendable { case info, warn, strong }

/// US regular session Mon–Fri 09:30–16:00 New York (holidays not handled): outside it an old price means
/// "market closed", not "stale".
public enum USMarket {
    public static func isRegularSession(_ date: Date = Date()) -> Bool {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        let parts = calendar.dateComponents([.weekday, .hour, .minute], from: date)
        guard let weekday = parts.weekday, (2...6).contains(weekday), let hour = parts.hour, let minute = parts.minute
        else { return false }
        let minutes = hour * 60 + minute
        return minutes >= 9 * 60 + 30 && minutes < 16 * 60
    }

    /// Next 09:30 New York on a weekday (now, if the session is open). Holidays not handled.
    public static func nextOpen(after date: Date = Date()) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        if isRegularSession(date) { return date }
        var day = calendar.startOfDay(for: date)
        for _ in 0..<8 {
            if let open = calendar.date(bySettingHour: 9, minute: 30, second: 0, of: day), open > date,
               (2...6).contains(calendar.component(.weekday, from: open)) {
                return open
            }
            day = calendar.date(byAdding: .day, value: 1, to: day)!
        }
        return date
    }
}

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
        let limit = request.limitPrice.value.formatted(number.precision(.fractionLength(2)))
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

    public init(symbol: String, last: Amount, prevClose: Amount, changePct: Amount, time: Date) {
        self.symbol = symbol
        self.last = last
        self.prevClose = prevClose
        self.changePct = changePct
        self.time = time
    }
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

struct OrderChange: Encodable {
    let quantity: Amount
    let limitPrice: Amount
}

public extension Order {
    var isPartiallyFilled: Bool { filled.value > 0 && remaining.value > 0 }

    /// "Submitted", "Partially filled 200 / 600", "Filled", "Cancelled", "Rejected: <reason>"
    var statusText: String {
        if isPartiallyFilled {
            let format = Decimal.FormatStyle.number.precision(.fractionLength(0...4))
            return "Partially filled \(filled.value.formatted(format)) / \(quantity.value.formatted(format))"
        }
        if status == "Rejected", let rejectReason { return "Rejected: \(rejectReason)" }
        return status
    }
}

/// Which order events deserve a notification (D6.2). Partial fills share one notification per order, updated
/// in place, so a large order filling in chunks does not spam.
public enum OrderNotification: Equatable, Sendable {
    case filled, partiallyFilled, rejected, cancelled

    public static func forUpdate(previous: Order?, current: Order) -> OrderNotification? {
        if current.status == previous?.status && current.filled == previous?.filled { return nil }
        switch current.status {
        case "Filled": return .filled
        case "Rejected": return previous?.status == "Rejected" ? nil : .rejected
        case "Cancelled": return previous?.status == "Cancelled" ? nil : .cancelled
        default:
            if current.isPartiallyFilled && current.filled != previous?.filled { return .partiallyFilled }
            return nil
        }
    }

    public func title(for order: Order) -> String {
        let side = order.side.rawValue, symbol = order.contract.symbol
        switch self {
        case .filled: return "\(side) \(symbol) filled"
        case .partiallyFilled: return "\(side) \(symbol) partially filled"
        case .rejected: return "\(side) \(symbol) rejected"
        case .cancelled: return "\(side) \(symbol) cancelled"
        }
    }

    public func body(for order: Order) -> String {
        let format = Decimal.FormatStyle.number.precision(.fractionLength(0...4))
        let filled = order.filled.value.formatted(format), total = order.quantity.value.formatted(format)
        let priceFormat = Decimal.FormatStyle.number.precision(.fractionLength(2))
        let price = order.avgFillPrice.map { " at avg \($0.value.formatted(priceFormat)) \(order.contract.currency)" } ?? ""
        switch self {
        case .filled: return "\(total) shares\(price) (order #\(order.orderId))"
        case .partiallyFilled: return "\(filled) of \(total) shares\(price) (order #\(order.orderId))"
        case .rejected: return order.rejectReason ?? "Rejected by the broker (order #\(order.orderId))"
        case .cancelled: return "\(filled) of \(total) filled before cancel (order #\(order.orderId))"
        }
    }
}

public extension Order {
    /// Plain words for IBKR order states.
    static func statusLabel(_ status: String) -> String {
        switch status {
        case "PendingSubmit", "ApiPending", "PreSubmitted": "Sending…"
        case "Submitted": "Working"
        case "Filled": "Filled"
        case "Cancelled", "ApiCancelled", "PendingCancel": "Cancelled"
        case "Rejected", "Inactive": "Rejected"
        default: status
        }
    }
}

/// Times shown to the user: the Mac's time zone with its offset, e.g. "Thu 15:30 UTC+2".
public enum LocalTime {
    public static func offsetLabel(for date: Date, in zone: TimeZone = .current) -> String {
        let minutes = zone.secondsFromGMT(for: date) / 60
        guard minutes != 0 else { return "UTC" }
        let hours = abs(minutes) / 60, rest = abs(minutes) % 60
        return "UTC\(minutes > 0 ? "+" : "−")\(hours)" + (rest > 0 ? String(format: ":%02d", rest) : "")
    }

    /// "Thu 15:30 UTC+2"
    public static func weekdayTime(_ date: Date, in zone: TimeZone = .current, locale: Locale = .current) -> String {
        var style = Date.FormatStyle(locale: locale, timeZone: zone).weekday(.abbreviated).hour(.twoDigits(amPM: .omitted)).minute()
        style.timeZone = zone
        return "\(date.formatted(style)) \(offsetLabel(for: date, in: zone))"
    }
}

/// What may be typed into number fields: digits only (quantities), or digits with one decimal separator
/// (volume, limit price). No letters, signs or spaces.
public enum NumericInput {
    public static func sanitize(_ text: String, allowsDecimal: Bool) -> String {
        var result = ""
        var hasSeparator = false
        for character in text {
            if character.isASCII && character.isNumber {
                result.append(character)
            } else if allowsDecimal, character == "." || character == ",", !hasSeparator {
                hasSeparator = true
                result.append(character)
            }
        }
        return result
    }
}
