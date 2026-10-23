import Foundation
import Observation
import TraderCore

/// Order ticket state (T5): ticker → contract lookup → inputs with live warnings → summary → send.
@MainActor
@Observable
final class OrderTicketModel {
    enum Step: Equatable {
        case editing
        case reviewing
        case sending
        case sent(Order)
        case failed(String)
    }

    var symbol: String = "" { didSet { symbolChanged() } }
    private(set) var candidates: [Contract] = []
    private(set) var contract: Contract?
    private(set) var lookupMessage: String?
    var side: OrderSide = .buy { didSet { inputsChanged() } }
    var quantityText: String = "" { didSet { quantityChanged() } }
    var limitText: String = "" { didSet { limitChanged() } }
    /// Buy helper: expected volume in thousands (base currency), turned into whole shares like in the Planner.
    var volumeText: String = "" { didSet { volumeChanged() } }
    var tif: TimeInForce = .day { didSet { inputsChanged() } }
    var outsideRth = false { didSet { inputsChanged() } }
    private(set) var quote: Quote?
    private(set) var preview: OrderPreview?
    private(set) var previewError: String?
    private(set) var step: Step = .editing

    @ObservationIgnored private let api: ServiceAPI
    @ObservationIgnored private var previewTask: Task<Void, Never>?
    @ObservationIgnored private var lookupTask: Task<Void, Never>?
    /// Fixed once the user reviews; reused if a send has to be retried (never a second order).
    @ObservationIgnored private var orderRef: String?
    /// T6: set when this ticket changes an open order instead of placing a new one.
    let modifying: Order?
    @ObservationIgnored private var lastSendUncertain = false

    /// Helpers on top of the plain ticket. Both fix the side and keep the limit following the live price (2 % above
    /// for Buy, 2 % below for Quick Sell) until the limit is typed in by hand.
    enum Helper { case none, buy, quickSell }
    let helper: Helper
    @ObservationIgnored private var limitTyped = false
    @ObservationIgnored private var settingByHelper = false
    /// Units of base currency per unit of a currency (for the volume → shares helper).
    @ObservationIgnored private let rate: (String) -> Decimal?
    /// The app streams the ticket's contract (watchlist) and pushes its live prices here.
    @ObservationIgnored var contractChanged: ((Contract?) -> Void)?
    /// Sent: the app closes the ticket and shows the order.
    @ObservationIgnored var onSent: ((Order) -> Void)?

    init(api: ServiceAPI, contract: Contract? = nil, side: OrderSide = .buy, helper: Helper = .none,
         rate: @escaping (String) -> Decimal? = { _ in nil }) {
        self.api = api
        self.modifying = nil
        self.helper = helper
        self.rate = rate
        self.side = helper == .buy ? .buy : helper == .quickSell ? .sell : side
        if let contract {
            symbol = contract.symbol
            Task { await select(contract) }
        }
    }

    /// Quick Sell (Overview positions): sell every share, limit 2 % below the last price — still reviewed and
    /// confirmed like any order.
    convenience init(api: ServiceAPI, quickSell position: Position) {
        self.init(api: api, contract: nil, helper: .quickSell)
        byHelper {
            quantityText = position.quantity.value.formatted(.number.precision(.fractionLength(0...4)).grouping(.never))
        }
        symbol = position.contract.symbol
        Task { await select(position.contract) }
    }

    /// Modify an open order (T6): contract, side and time in force are fixed; quantity and limit can change.
    init(api: ServiceAPI, modifying order: Order) {
        self.api = api
        self.modifying = order
        self.helper = .none
        self.rate = { _ in nil }
        symbol = order.contract.symbol
        contract = order.contract
        side = order.side
        tif = TimeInForce(rawValue: order.tif) ?? .day
        outsideRth = order.outsideRth
        quantityText = order.quantity.value.formatted(.number.precision(.fractionLength(0...4)).grouping(.never))
        limitText = order.limitPrice.value.formatted(.number.precision(.fractionLength(2)).grouping(.never))
        Task {
            quote = try? await api.quote(conid: order.contract.conid)
            inputsChanged()
        }
    }

    var isModification: Bool { modifying != nil }

    // MARK: - Inputs

    var quantity: Decimal? { DecimalInput.parse(quantityText).flatMap { $0 > 0 ? $0 : nil } }
    var limitPrice: Decimal? { DecimalInput.parse(limitText).flatMap { $0 > 0 ? $0 : nil } }

    var request: OrderRequest? {
        guard let contract, let quantity, let limitPrice else { return nil }
        return OrderRequest(conid: contract.conid, side: side, quantity: Amount(quantity),
                            limitPrice: Amount(limitPrice), tif: tif, outsideRth: outsideRth, orderRef: orderRef)
    }

    var canReview: Bool { request != nil && preview != nil && preview?.block == nil && step == .editing }

    /// Why Review is disabled, shown under the inputs.
    var missing: String? {
        if contract == nil {
            if !candidates.isEmpty { return "Pick a listing" }
            return symbol.trimmingCharacters(in: .whitespaces).isEmpty ? "Enter a ticker" : nil
        }
        if quantity == nil { return quantityText.isEmpty ? "Enter a quantity" : "Quantity must be a positive number" }
        if limitPrice == nil { return limitText.isEmpty ? "Enter a limit price" : "Limit price must be a positive number" }
        if let previewError { return previewError }
        return nil
    }

    /// Typing a ticker looks it up after a short pause (Enter looks it up immediately).
    private func symbolChanged() {
        let wanted = symbol.trimmingCharacters(in: .whitespaces).uppercased()
        if let contract, contract.symbol == wanted { return }
        lookupTask?.cancel()
        if contract != nil || !candidates.isEmpty {
            contract = nil
            candidates = []
            quote = nil
            preview = nil
            contractChanged?(nil)
        }
        guard !wanted.isEmpty else { return }
        lookupTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            await self?.lookUp()
        }
    }

    func lookUp() async {
        lookupTask?.cancel()
        let wanted = symbol.trimmingCharacters(in: .whitespaces).uppercased()
        if let contract, contract.symbol == wanted { return }
        contract = nil
        candidates = []
        quote = nil
        preview = nil
        lookupMessage = nil
        guard !wanted.isEmpty else { return }
        do {
            let found = try await api.contracts(symbol: wanted)
            switch found.count {
            case 0: lookupMessage = "No contract found for \(wanted)"
            case 1: await select(found[0])
            default:
                candidates = found
                lookupMessage = "\(wanted) is listed on several exchanges — pick one"
            }
        } catch {
            lookupMessage = describe(error)
        }
    }

    func select(_ contract: Contract) async {
        self.contract = contract
        candidates = []
        lookupMessage = nil
        contractChanged?(contract)
        if let first = try? await api.quote(conid: contract.conid), quote == nil { quote = first }
        applyHelper()
        inputsChanged()
    }

    /// A live price for the ticket's contract (Finnhub stream): shown, and the helpers follow it.
    func priceUpdated(_ quote: Quote) {
        guard step == .editing else { return }
        self.quote = quote
        applyHelper()
    }

    /// Fill in what the helper computes from the last price (limit, and shares from the volume).
    private func applyHelper() {
        guard modifying == nil, let contract, let last = quote?.last.value else { return }
        byHelper {
            switch helper {
            case .none:
                if limitText.isEmpty { limitText = Self.price(last) }
            case .buy:
                if !limitTyped { limitText = Self.price(PlanCommit.limitPrice(last: last)) }
                if let thousands = DecimalInput.parse(volumeText),
                   let shares = PlanVolume.quantity(thousands: thousands, price: last,
                                                    fxToBase: rate(contract.currency) ?? 1) {
                    quantityText = Self.plain(shares)
                }
            case .quickSell:
                if !limitTyped { limitText = Self.price(PlanCommit.sellLimitPrice(last: last)) }
            }
        }
    }

    private func byHelper(_ change: () -> Void) {
        settingByHelper = true
        change()
        settingByHelper = false
    }

    private func limitChanged() {
        if !settingByHelper { limitTyped = !limitText.isEmpty }
        inputsChanged()
    }

    private func quantityChanged() {
        if !settingByHelper && helper == .buy && !volumeText.isEmpty {
            byHelper { volumeText = "" }  // typed shares replace the volume (as in the Planner)
        }
        inputsChanged()
    }

    private func volumeChanged() {
        guard !settingByHelper else { return }
        if volumeText.isEmpty { return }
        applyHelper()
    }

    private static func price(_ value: Decimal) -> String {
        value.formatted(.number.precision(.fractionLength(2)).grouping(.never))
    }

    private static func plain(_ value: Decimal) -> String {
        value.formatted(.number.precision(.fractionLength(0...4)).grouping(.never))
    }

    private func inputsChanged() {
        orderRef = nil
        guard step == .editing || step == .reviewing else { return }
        step = .editing
        previewTask?.cancel()
        guard let request else {
            preview = nil
            previewError = nil
            return
        }
        previewTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled, let self else { return }
            do {
                let result = try await api.preview(request, modifying: modifying?.orderId)
                guard !Task.isCancelled else { return }
                preview = result
                previewError = nil
            } catch {
                guard !Task.isCancelled else { return }
                preview = nil
                previewError = "Could not check the order: \(describe(error))"
            }
        }
    }

    // MARK: - Review and send

    func review() {
        guard canReview else { return }
        if modifying == nil { orderRef = UUID().uuidString }
        step = .reviewing
    }

    func backToEditing() {
        step = .editing
    }

    /// The user closed the summary without sending — goes to the activity log (T5).
    func cancelledInReview() async {
        guard let contract else { return }
        try? await api.note("order_dialog_cancelled", detail: [
            "symbol": contract.symbol, "side": side.rawValue, "quantity": quantityText, "limit_price": limitText,
        ])
    }

    func send() async {
        guard step == .reviewing || isRetryable, let request, request.orderRef != nil || modifying != nil
        else { return }
        step = .sending
        lastSendUncertain = false
        do {
            let order = if let modifying {
                // Re-sending the same quantity/limit is harmless, so a modification can always be retried.
                try await api.modifyOrder(modifying.orderId, quantity: request.quantity, limitPrice: request.limitPrice)
            } else {
                try await api.placeOrder(request)
            }
            step = order.status == "Rejected" ? .failed("Rejected: \(order.rejectReason ?? "no reason")") : .sent(order)
            if case .sent(let order) = step { onSent?(order) }
        } catch ServiceError.http(_, _, let message) {
            orderRef = nil  // the service refused it: nothing was placed, a new review gets a new ref
            step = .failed(message ?? "Order refused")
        } catch {
            lastSendUncertain = true
            step = .failed("Not sure the order was received: \(describe(error)). Retrying is safe.")
        }
    }

    /// After a transport error the same orderRef is kept, so retrying can never create a second order.
    var isRetryable: Bool {
        guard case .failed = step else { return false }
        return orderRef != nil || (modifying != nil && lastSendUncertain)
    }

    var summary: String? {
        guard let request, let contract, let preview else { return nil }
        let line = OrderSummary.line(request, contract: contract, value: preview.value)
        return modifying.map { "MODIFY #\($0.orderId) → \(line)" } ?? line
    }

    private func describe(_ error: Error) -> String {
        if case ServiceError.http(_, _, let message) = error { return message ?? "Request failed" }
        return error.localizedDescription
    }
}
