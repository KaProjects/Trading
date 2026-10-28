import Foundation
import Observation
import TraderCore

/// T12: sell review from the Execution tab — Sell all, Sell on one ticker, or the selected ones. One SELL limit
/// order per ticker: all active shares, limit 2 % below the live price (follows the price until typed over).
/// Lines can be edited or removed, then placed once; a line cancelled with nothing filled can be retried.
@MainActor
@Observable
final class SellBasketModel {
    enum Stage: Equatable {
        case closed       // no review shown
        case reviewing    // lines editable, Place available
        case placing      // sending; Place hidden (no double submit)
        case placed       // results shown; Retry failed and/or Done
    }

    struct Line: Identifiable {
        var id: Int { contract.conid }
        let contract: Contract
        let held: Decimal         // active shares when the review opened
        /// Fixed when the review opens, so retrying a send can never create a second order; a new one
        /// (`<ref>.2` …) only after the broker cancelled the order with nothing filled.
        var orderRef: String
        var quantityText: String
        var limitText: String
        /// The limit follows the live price (last − 2 %) until it is typed over.
        var limitFollowsPrice = true
        let tif: TimeInForce = .day
        let outsideRth = false
        var preview: OrderPreview?
        var previewError: String?
        var result: BasketResult?
        var order: Order?

        var quantity: Decimal? { DecimalInput.parse(quantityText).flatMap { $0 > 0 ? $0 : nil } }
        var limit: Decimal? { DecimalInput.parse(limitText).flatMap { $0 > 0 ? $0 : nil } }
        var isPlaced: Bool { result?.isPlaced == true }
        var isFilled: Bool { order?.status == "Filled" }
        var isCancelled: Bool { ["Cancelled", "ApiCancelled", "Rejected", "Inactive"].contains(order?.status ?? "") }
        var isWorking: Bool { isPlaced && !isFilled && !isCancelled }
        var shownQuantity: Decimal? { order?.quantity.value ?? quantity }
        var shownLimit: Decimal? { order?.limitPrice.value ?? limit }

        var request: OrderRequest? {
            guard let quantity, let limit else { return nil }
            return OrderRequest(conid: contract.conid, side: .sell, quantity: Amount(quantity),
                                limitPrice: Amount(limit), tif: tif, outsideRth: outsideRth, orderRef: orderRef)
        }
    }

    private(set) var stage: Stage = .closed
    private(set) var lines: [Line] = []

    @ObservationIgnored private let api: ServiceAPI
    @ObservationIgnored private var previewTasks: [Int: Task<Void, Never>] = [:]
    @ObservationIgnored private var latestOrders: [Int: Order] = [:]

    init(api: ServiceAPI) {
        self.api = api
    }

    var isOpen: Bool { stage != .closed }

    // MARK: - Open / close

    /// positions: contract and active shares of each ticker to sell; quotes: live prices by feed symbol.
    func open(_ positions: [(Contract, Decimal)], quotes: [String: Quote]) {
        guard stage == .closed || canClose, !positions.isEmpty else { return }
        lines = positions.map { contract, held in
            let limit = quotes[contract.feedSymbol].map { PlanCommit.sellLimitPrice(last: $0.last.value) }
            return Line(contract: contract, held: held, orderRef: "sell-\(UUID().uuidString)",
                        quantityText: Self.plain(held, fraction: 0...4),
                        limitText: limit.map { Self.plain($0, fraction: 2...2) } ?? "")
        }
        stage = .reviewing
        lines.forEach { refreshPreview($0.id) }
    }

    /// Before placing, or once nothing is still working at the broker.
    func close() {
        guard canClose else { return }
        previewTasks.values.forEach { $0.cancel() }
        previewTasks = [:]
        lines = []
        stage = .closed
    }

    var canClose: Bool { stage == .reviewing || (stage == .placed && !anyWorking) }

    // MARK: - Editing

    func setQuantity(_ id: Int, _ text: String) { edit(id) { $0.quantityText = text } }
    func setLimit(_ id: Int, _ text: String) {
        edit(id) { line in
            guard line.limitText != text else { return }  // the field re-submits unchanged text on focus
            line.limitText = text
            line.limitFollowsPrice = false
        }
    }

    func remove(_ id: Int) {
        guard let line = lines.first(where: { $0.id == id }), !line.isPlaced,
              stage == .reviewing || stage == .placed else { return }
        previewTasks[id]?.cancel()
        lines.removeAll { $0.id == id }
        if lines.isEmpty { stage = .closed }
    }

    func priceUpdated(_ quote: Quote) {
        for index in lines.indices where lines[index].contract.feedSymbol == quote.symbol
            && lines[index].limitFollowsPrice && !lines[index].isPlaced {
            lines[index].limitText = Self.plain(PlanCommit.sellLimitPrice(last: quote.last.value), fraction: 2...2)
        }
    }

    private func edit(_ id: Int, _ change: (inout Line) -> Void) {
        guard stage == .reviewing || stage == .placed,
              let index = lines.firstIndex(where: { $0.id == id }), !lines[index].isPlaced else { return }
        change(&lines[index])
        refreshPreview(id)
    }

    /// Same checks as the order ticket (POST /orders/preview), debounced per line.
    private func refreshPreview(_ id: Int) {
        previewTasks[id]?.cancel()
        guard let request = lines.first(where: { $0.id == id })?.request else {
            update(id) { $0.preview = nil; $0.previewError = nil }
            return
        }
        previewTasks[id] = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled, let self else { return }
            do {
                let preview = try await api.preview(request)
                guard !Task.isCancelled else { return }
                update(id) { $0.preview = preview; $0.previewError = nil }
            } catch {
                guard !Task.isCancelled else { return }
                update(id) { $0.preview = nil; $0.previewError = "Could not check this order" }
            }
        }
    }

    private func update(_ id: Int, _ change: (inout Line) -> Void) {
        guard let index = lines.firstIndex(where: { $0.id == id }) else { return }
        change(&lines[index])
    }

    /// Live order events keep the lines' status current.
    func orderUpdated(_ order: Order) {
        latestOrders[order.orderId] = order
        guard let index = lines.firstIndex(where: {
            if case .placed(let orderId, _) = $0.result { orderId == order.orderId } else { false }
        }) else { return }
        lines[index].result = .placed(orderId: order.orderId, status: order.status)
        lines[index].order = order
        reopenIfCancelled(index)
    }

    /// Cancelled or rejected with nothing filled: shows why, becomes editable and gets the next order reference.
    private func reopenIfCancelled(_ index: Int) {
        let line = lines[index]
        guard line.isCancelled, let order = line.order, order.filled.value == 0 else { return }
        lines[index].result = .failed(order.rejectReason.map { "\(order.status) by IBKR: \($0)" } ?? order.status)
        lines[index].order = nil
        lines[index].orderRef = PlanBasketModel.nextRef(line.orderRef)
        refreshPreview(line.id)
    }

    // MARK: - Checks

    /// Why placing is not possible yet (nil = ready).
    func blockingReason(allowWhenClosed: Bool, now: Date = Date()) -> String? {
        let pending = lines.filter { !$0.isPlaced }
        if pending.isEmpty { return "Nothing to place" }
        // Same hard rule as the plan's buys (D9.5): DAY orders, regular hours only.
        if !USMarket.isRegularSession(now) && !allowWhenClosed {
            return "Market closed · opens \(LocalTime.weekdayTime(USMarket.nextOpen(after: now)))"
        }
        if pending.contains(where: { $0.quantity == nil }) { return "Every order needs a quantity" }
        if let over = pending.first(where: { ($0.quantity ?? 0) > $0.held }) {
            return "\(over.contract.symbol): only \(Self.plain(over.held, fraction: 0...4)) shares active"
        }
        if pending.contains(where: { $0.limit == nil }) { return "Every order needs a limit price" }
        if let blocked = pending.first(where: { $0.preview?.block != nil }) {
            return "\(blocked.contract.symbol): \(blocked.preview!.block!.message)"
        }
        if pending.contains(where: { $0.preview == nil && $0.previewError == nil }) { return "Checking orders…" }
        return nil
    }

    var anyWorking: Bool { lines.contains(where: \.isWorking) }
    var allFilled: Bool { !lines.isEmpty && lines.allSatisfy(\.isFilled) }
    var cancelledCount: Int { lines.filter(\.isCancelled).count }
    var failedCount: Int { lines.filter { if case .failed = $0.result { true } else { false } }.count }
    var pendingCount: Int { lines.filter { !$0.isPlaced }.count }

    // MARK: - Placing

    /// Sends every not-yet-placed line once, in order.
    func place(allowWhenClosed: Bool) async {
        guard stage == .reviewing || (stage == .placed && failedCount > 0),
              blockingReason(allowWhenClosed: allowWhenClosed) == nil else { return }
        stage = .placing
        for line in lines where !line.isPlaced {
            guard let request = line.request else { continue }
            let result: BasketResult
            do {
                let reply = try await api.placeOrder(request)
                let order = latestOrders[reply.orderId] ?? reply
                result = order.status == "Rejected"
                    ? .failed("Rejected: \(order.rejectReason ?? "no reason")")
                    : .placed(orderId: order.orderId, status: order.status)
                if result.isPlaced { update(line.id) { $0.order = order } }
            } catch ServiceError.http(_, _, let message) {
                result = .failed(message ?? "Refused")
            } catch {
                result = .failed("Not sure it was received — retrying is safe (same order reference)")
            }
            update(line.id) { $0.result = result }
            if let index = lines.firstIndex(where: { $0.id == line.id }) { reopenIfCancelled(index) }
        }
        stage = .placed
    }

    private static func plain(_ value: Decimal, fraction: ClosedRange<Int>) -> String {
        value.formatted(.number.precision(.fractionLength(fraction)).grouping(.never))
    }
}
