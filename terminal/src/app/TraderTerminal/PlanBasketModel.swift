import Foundation
import Observation
import TraderCore

/// T9: a committed plan as a basket of BUY limit orders — review, place once, retry failed, reset.
@MainActor
@Observable
final class PlanBasketModel {
    enum Stage: Equatable {
        case planning     // plan editable, no basket
        case committed    // plan frozen, basket editable, Place / Replan available
        case placing      // sending; Place hidden (no double submit)
        case placed       // results shown; Retry failed and/or Reset
    }

    struct Line: Identifiable {
        let id: UUID              // the plan row's id
        let contract: Contract
        /// Fixed at commit, so retrying a send can never create a second order. Only when the broker cancelled the
        /// order without filling anything does the line get a new one (`<ref>.2`, `.3` …) to be placed again.
        var orderRef: String
        var quantityText: String
        var limitText: String
        // Planner orders are always DAY and regular hours only (D9.5).
        let tif: TimeInForce = .day
        let outsideRth = false
        var preview: OrderPreview?
        var previewError: String?
        var result: BasketResult?
        /// The live order once placed: follows fills and changes made elsewhere (Overview modify / cancel).
        var order: Order?

        var quantity: Decimal? { DecimalInput.parse(quantityText).flatMap { $0 > 0 ? $0 : nil } }
        var limit: Decimal? { DecimalInput.parse(limitText).flatMap { $0 > 0 ? $0 : nil } }
        var isPlaced: Bool { result?.isPlaced == true }
        /// Editable while committed, and after placing only if it failed (fix and retry).
        var isEditable: Bool { !isPlaced }
        var isFilled: Bool { order?.status == "Filled" }
        var isCancelled: Bool { ["Cancelled", "ApiCancelled", "Rejected", "Inactive"].contains(order?.status ?? "") }
        /// Placed and still waiting at the broker (not filled, not cancelled).
        var isWorking: Bool { isPlaced && !isFilled && !isCancelled }

        /// What the row shows: the live order once placed, the typed values before.
        var shownQuantity: Decimal? { order?.quantity.value ?? quantity }
        var shownLimit: Decimal? { order?.limitPrice.value ?? limit }

        var request: OrderRequest? {
            guard let quantity, let limit else { return nil }
            return OrderRequest(conid: contract.conid, side: .buy, quantity: Amount(quantity), limitPrice: Amount(limit),
                                tif: tif, outsideRth: outsideRth, orderRef: orderRef)
        }
    }

    private(set) var stage: Stage = .planning
    /// Settings threshold for "limit price far from the price" (D5.4), loaded at commit; checked live in the review.
    private(set) var deviationWarn: Decimal = 5
    /// Developer option (Debug + fake): ignore the market-closed block.
    var allowPlacingWhenClosed = AppEnvironment.devAllowPlacingWhenClosed
    private(set) var lines: [Line] = []
    /// T10: identity and commit time of the plan, saved as a PlanRecord at Place.
    private(set) var planId = UUID().uuidString
    private(set) var committedAt = Date()

    @ObservationIgnored private let api: ServiceAPI
    @ObservationIgnored private var previewTasks: [UUID: Task<Void, Never>] = [:]
    @ObservationIgnored private var latestOrders: [Int: Order] = [:]

    init(api: ServiceAPI) {
        self.api = api
    }

    var isFrozen: Bool { stage != .planning }

    // MARK: - Commit / replan / reset

    func commit(_ plan: PlannerSummary) async {
        guard stage == .planning, PlanCommit.canCommit(plan) else { return }
        let thresholds = try? await api.ruleThresholds()
        deviationWarn = thresholds?.priceDeviationWarn.value ?? 5
        lines = plan.planned.compactMap { item in
            guard let quantity = item.row.quantity, let last = item.quote?.last.value else { return nil }
            let limit = PlanCommit.limitPrice(last: last)
            return Line(id: item.row.id, contract: item.row.contract, orderRef: UUID().uuidString,
                        quantityText: Self.plain(quantity, fraction: 0...4), limitText: Self.plain(limit, fraction: 2...2))
        }
        planId = UUID().uuidString
        committedAt = Date()
        stage = .committed
        lines.forEach { refreshPreview($0.id) }
    }

    /// Undo a commit before anything was sent: plan editable again, review hidden.
    func replan() {
        guard stage == .committed else { return }
        previewTasks.values.forEach { $0.cancel() }
        previewTasks = [:]
        lines = []
        stage = .planning
    }

    /// Once nothing is still working at the broker: start over (the caller also clears the plan).
    func reset() {
        guard canReset else { return }
        lines = []
        stage = .planning
    }

    // MARK: - Editing

    func setQuantity(_ id: UUID, _ text: String) { edit(id) { $0.quantityText = text } }
    func setLimit(_ id: UUID, _ text: String) { edit(id) { $0.limitText = text } }

    private func edit(_ id: UUID, _ change: (inout Line) -> Void) {
        guard stage == .committed || stage == .placed,
              let index = lines.firstIndex(where: { $0.id == id }), lines[index].isEditable else { return }
        change(&lines[index])
        refreshPreview(id)
    }

    /// Same checks as the order ticket (POST /orders/preview), debounced per line.
    private func refreshPreview(_ id: UUID) {
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

    private func update(_ id: UUID, _ change: (inout Line) -> Void) {
        guard let index = lines.firstIndex(where: { $0.id == id }) else { return }
        change(&lines[index])
    }

    /// Live order events keep the review rows' status current (PendingSubmit → Submitted → Filled …).
    func orderUpdated(_ order: Order) {
        latestOrders[order.orderId] = order  // may arrive before the place-order reply
        guard let index = lines.firstIndex(where: {
            if case .placed(let orderId, _) = $0.result { orderId == order.orderId } else { false }
        }) else { return }
        lines[index].result = .placed(orderId: order.orderId, status: order.status)
        lines[index].order = order
        reopenIfCancelled(index)
    }

    /// A line whose order was cancelled or rejected with nothing filled (e.g. IBKR's limit price cap) can be fixed
    /// and placed again: it shows why, becomes editable, and gets the next order reference of the same plan row.
    private func reopenIfCancelled(_ index: Int) {
        let line = lines[index]
        guard line.isCancelled, let order = line.order, order.filled.value == 0 else { return }
        let reason = order.rejectReason.map { "\(order.status) by IBKR: \($0)" } ?? "\(order.status)"
        lines[index].result = .failed(reason)
        lines[index].order = nil
        lines[index].orderRef = Self.nextRef(line.orderRef)
        refreshPreview(line.id)
    }

    /// `abc` → `abc.2` → `abc.3`: the service attributes every attempt to the same plan row (T10 lots).
    static func nextRef(_ ref: String) -> String {
        let parts = ref.split(separator: ".")
        if parts.count == 2, let attempt = Int(parts[1]) { return "\(parts[0]).\(attempt + 1)" }
        return "\(ref).2"
    }

    // MARK: - Checks

    func totalAtLimit(account: AccountSummary) -> Decimal? {
        PlanCommit.totalAtLimit(lines.map { ($0.quantity, $0.limit, $0.contract.currency) }, account: account)
    }

    /// Why placing is not possible yet (nil = ready).
    func blockingReason(account: AccountSummary, available: Decimal, now: Date = Date()) -> String? {
        let pending = lines.filter { !$0.isPlaced }
        if pending.isEmpty { return "Nothing to place" }
        // Hard rule (D9.5): DAY orders inside regular hours only — nothing is placed while the market is closed.
        if !USMarket.isRegularSession(now) && !allowPlacingWhenClosed {
            return "Market closed · opens \(LocalTime.weekdayTime(USMarket.nextOpen(after: now)))"
        }
        if pending.contains(where: { $0.quantity == nil }) { return "Every order needs a quantity" }
        if pending.contains(where: { $0.limit == nil }) { return "Every order needs a limit price" }
        if let blocked = pending.first(where: { $0.preview?.block != nil }) {
            return "\(blocked.contract.symbol): \(blocked.preview!.block!.message)"
        }
        if pending.contains(where: { $0.preview == nil }) { return "Checking orders…" }
        guard let total = totalAtLimit(account: account) else { return "Missing currency rate" }
        let alreadyPlaced = lines.filter(\.isPlaced).reduce(Decimal(0)) { sum, line in
            sum + (line.quantity ?? 0) * (line.limit ?? 0) * (account.rate(line.contract.currency) ?? 1)
        }
        // Placed orders already reserve their part of "available" (it is recomputed from open orders).
        if total - alreadyPlaced > available {
            return "Total at limit prices exceeds available to invest"
        }
        return nil
    }

    var allPlaced: Bool { !lines.isEmpty && lines.allSatisfy(\.isPlaced) }
    var allFilled: Bool { !lines.isEmpty && lines.allSatisfy(\.isFilled) }
    var anyWorking: Bool { lines.contains(where: \.isWorking) }
    var cancelledCount: Int { lines.filter(\.isCancelled).count }
    /// Contracts this plan has bought shares of (filled or partly filled) — highlighted in Holdings until Finish.
    var newlyBoughtConids: Set<Int> {
        Set(lines.filter { ($0.order?.filled.value ?? 0) > 0 }.map(\.contract.conid))
    }
    /// Reset once nothing is still working at the broker (all filled, or cancelled e.g. from the Overview).
    var canReset: Bool { stage == .placed && !lines.isEmpty && !anyWorking }
    var failedCount: Int { lines.filter { if case .failed = $0.result { true } else { false } }.count }

    // MARK: - Placing

    /// T10: what gets saved when the plan is placed (expected returns come from the planner).
    func planRecord(expectedReturns: [UUID: Decimal]) -> PlanRecord {
        PlanRecord(id: planId, committedAt: committedAt, placedAt: Date(), rows: lines.map { line in
            .init(conid: line.contract.conid, symbol: line.contract.symbol, currency: line.contract.currency,
                  orderRef: line.orderRef, plannedQuantity: Amount(line.quantity ?? 0),
                  expectedReturnPct: expectedReturns[line.id].map(Amount.init))
        })
    }

    /// Sends every not-yet-placed line once, in order. The Place button is gone from the first click on.
    func place(account: AccountSummary, available: Decimal) async {
        guard stage == .committed || (stage == .placed && failedCount > 0),
              blockingReason(account: account, available: available) == nil else { return }
        stage = .placing
        for line in lines where !line.isPlaced {
            guard let request = line.request else { continue }
            let result: BasketResult
            do {
                let reply = try await api.placeOrder(request)
                let order = latestOrders[reply.orderId] ?? reply  // a newer event may have arrived first
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
