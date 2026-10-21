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
    var quantityText: String = "" { didSet { inputsChanged() } }
    var limitText: String = "" { didSet { inputsChanged() } }
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

    init(api: ServiceAPI, contract: Contract? = nil, side: OrderSide = .buy) {
        self.api = api
        self.modifying = nil
        self.side = side
        if let contract {
            symbol = contract.symbol
            Task { await select(contract) }
        }
    }

    /// Modify an open order (T6): contract, side and time in force are fixed; quantity and limit can change.
    init(api: ServiceAPI, modifying order: Order) {
        self.api = api
        self.modifying = order
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
        quote = try? await api.quote(conid: contract.conid)
        if limitText.isEmpty, let last = quote?.last {
            limitText = last.value.formatted(.number.precision(.fractionLength(2)).grouping(.never))
        }
        inputsChanged()
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
