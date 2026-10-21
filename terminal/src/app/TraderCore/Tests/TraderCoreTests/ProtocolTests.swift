import Foundation
import Testing
@testable import TraderCore

@Suite struct HTTPParserTests {
    @Test func contentLength() throws {
        let raw = Data("HTTP/1.1 200 OK\r\ncontent-type: application/json\r\ncontent-length: 2\r\n\r\n{}".utf8)
        let response = try HTTPParser.parse(raw)
        #expect(response.status == 200)
        #expect(response.headers["content-type"] == "application/json")
        #expect(response.body == Data("{}".utf8))
    }

    @Test func chunked() throws {
        let raw = Data("HTTP/1.1 409 Conflict\r\nTransfer-Encoding: chunked\r\n\r\n4\r\n{\"a\"\r\n3\r\n:1}\r\n0\r\n\r\n".utf8)
        let response = try HTTPParser.parse(raw)
        #expect(response.status == 409)
        #expect(response.body == Data("{\"a\":1}".utf8))
    }

    @Test func malformed() {
        #expect(throws: HTTPError.malformedResponse) { try HTTPParser.parse(Data("garbage".utf8)) }
        #expect(throws: HTTPError.malformedResponse) { try HTTPParser.parse(Data("NOPE x\r\n\r\n".utf8)) }
    }
}

@Suite struct FrameCodecTests {
    private func decoder(_ bytes: Data) -> (Int) throws -> Data {
        var rest = bytes
        return { count in
            guard rest.count >= count else { throw SocketError.closed }
            defer { rest.removeFirst(count) }
            return Data(rest.prefix(count))
        }
    }

    @Test func decodesUnmaskedServerTextFrame() throws {
        let frame = try FrameCodec.decode(take: decoder(Data([0x81, 0x05]) + Data("hello".utf8)))
        #expect(frame == Frame(fin: true, opcode: .text, payload: Data("hello".utf8)))
    }

    @Test func decodesExtendedLength() throws {
        let payload = Data(repeating: 0x41, count: 300)
        let frame = try FrameCodec.decode(take: decoder(Data([0x81, 126, 0x01, 0x2C]) + payload))
        #expect(frame.payload == payload)
    }

    @Test func encodesMaskedClientFrameThatRoundTrips() throws {
        let payload = Data(repeating: 0x42, count: 200)
        let encoded = FrameCodec.encode(.text, payload: payload, maskKey: [1, 2, 3, 4])
        #expect(encoded[0] == 0x81)
        #expect(encoded[1] == 0x80 | 126)  // masked, 16-bit length
        #expect(try FrameCodec.decode(take: decoder(encoded)).payload == payload)
    }

    @Test func rejectsUnknownOpcode() {
        #expect(throws: WebSocketError.self) { try FrameCodec.decode(take: decoder(Data([0x83, 0x00]))) }
    }

    @Test func acceptValueFromRFC6455() {
        #expect(FrameCodec.acceptValue(for: "dGhlIHNhbXBsZSBub25jZQ==") == "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
    }
}

@Suite struct ModelTests {
    @Test func decodesStatusWithMicroseconds() throws {
        let json = #"{"state":"logging_in","environment":"fake","account_id":null,"since":"2026-10-21T15:14:08.168840Z","message":"Approve on your phone"}"#
        let status = try ServiceJSON.decoder.decode(BrokerStatus.self, from: Data(json.utf8))
        #expect(status.state == .loggingIn)
        #expect(status.environment == .fake)
        #expect(status.message == "Approve on your phone")
        #expect(abs(status.since.timeIntervalSince1970 - 1_792_595_648.168) < 0.01)
    }

    @Test func parsesEventEnvelope() throws {
        let event = try ServiceEvent.parse(Data(#"{"type":"status","data":{"state":"connected"},"time":"x"}"#.utf8))
        #expect(event.type == "status")
        let data = try JSONSerialization.jsonObject(with: event.data) as? [String: String]
        #expect(data == ["state": "connected"])
    }
}

@Suite struct StatusPresentationTests {
    private func status(_ state: ConnectionState, account: String? = nil, message: String? = nil) -> BrokerStatus {
        BrokerStatus(state: state, environment: .fake, accountId: account, message: message)
    }

    @Test func lockedShowsOnlyLocked() {
        let shown = StatusPresentation.make(locked: true, phase: .running, status: status(.connected, account: "FAKE0001"))
        #expect(shown.title == "Locked" && shown.isLocked && shown.indicator == .grey)
        #expect(shown.canLogIn && !shown.canStop)
    }

    @Test func connectedShowsEnvironmentAndAccount() {
        let shown = StatusPresentation.make(locked: false, phase: .running, status: status(.connected, account: "FAKE0001"))
        #expect(shown.title == "Connected (Fake · FAKE0001)")
        #expect(shown.indicator == .green && shown.canStop && !shown.canLogIn)
    }

    @Test func statesMapToIndicators() {
        let cases: [(ConnectionState, Indicator, Bool)] = [
            (.disconnected, .red, true), (.loggingIn, .yellow, false), (.reconnecting, .yellow, false),
            (.loginRequired, .red, true), (.loginFailed, .red, true),
        ]
        for (state, indicator, canLogIn) in cases {
            let shown = StatusPresentation.make(locked: false, phase: .running, status: status(state))
            #expect(shown.indicator == indicator, "\(state)")
            #expect(shown.canLogIn == canLogIn, "\(state)")
        }
        #expect(StatusPresentation.make(locked: false, phase: .running, status: status(.loginFailed)).title
                == "Login failed — Retry")
    }

    @Test func serviceProblems() {
        #expect(StatusPresentation.make(locked: false, phase: .starting, status: nil).title == "Starting…")
        let failed = StatusPresentation.make(locked: false, phase: .failed("exit 1"), status: nil)
        #expect(failed.indicator == .red && failed.detail == "exit 1" && !failed.canLogIn)
        #expect(!StatusPresentation.make(locked: true, phase: .starting, status: nil).canLogIn)
    }

    @Test func notificationsOnlyOnTransitionsNeedingAction() {
        #expect(StatusNotification.forTransition(from: .disconnected, to: .loggingIn) == .approveOnPhone)
        #expect(StatusNotification.forTransition(from: .loggingIn, to: .loginFailed) == .loginFailed)
        #expect(StatusNotification.forTransition(from: .connected, to: .loginRequired) == .loginRequired)
        #expect(StatusNotification.forTransition(from: .loggingIn, to: .connected) == nil)
        #expect(StatusNotification.forTransition(from: .loggingIn, to: .loggingIn) == nil)
    }
}

@Suite struct DecimalInputTests {
    @Test func parsesLocaleAndPlainInput() {
        let us = Locale(identifier: "en_US"), cz = Locale(identifier: "cs_CZ")
        #expect(DecimalInput.parse("182.50", locale: us) == Decimal(string: "182.50"))
        #expect(DecimalInput.parse("1,250.5", locale: us) == Decimal(string: "1250.5"))
        #expect(DecimalInput.parse("182,50", locale: cz) == Decimal(string: "182.50"))
        #expect(DecimalInput.parse("1 250,5", locale: cz) == Decimal(string: "1250.5"))
        #expect(DecimalInput.parse(" 10 ", locale: us) == 10)
    }

    @Test func rejectsGarbage() {
        let us = Locale(identifier: "en_US")
        for text in ["", "abc", "-5", "1.2.3", "1e5"] {
            #expect(DecimalInput.parse(text, locale: us) == nil, "\(text)")
        }
    }
}

@Suite struct PricedPositionTests {
    let contract = Contract(conid: 1, symbol: "AAPL", name: "Apple", secType: "STK", exchange: "SMART",
                            primaryExchange: "NASDAQ", currency: "USD", priceSymbol: "AAPL")

    @Test func valuesFromQuote() throws {
        let position = Position(contract: contract, quantity: Amount(50), avgCost: Amount(190), costBasis: Amount(9500))
        let now = Date(timeIntervalSince1970: 1_792_594_800)  // Wed 2026-10-21 15:00 UTC = 11:00 New York (open)
        let quote = Quote(symbol: "AAPL", last: Amount(Decimal(string: "228.4")!), prevClose: Amount(225),
                          changePct: Amount(Decimal(string: "1.51")!), time: now.addingTimeInterval(-5))
        let priced = PricedPosition(position: position, quote: quote, now: now)
        #expect(priced.marketValue == Amount(11420))
        #expect(priced.unrealizedPnL == Amount(1920))
        let pct = try #require(priced.unrealizedPct).value
        #expect(pct > Decimal(string: "20.2")! && pct < Decimal(string: "20.22")!)  // 1920 / 9500 = 20.21 %
        #expect(!priced.isStale)
        #expect(PricedPosition(position: position, quote: quote, now: now.addingTimeInterval(120)).isStale)
        #expect(PricedPosition(position: position, quote: nil).marketValue == nil)
    }

    @Test func oldSnapshotsWithoutFeedSymbolStillDecode() throws {
        let json = #"{"conid":1,"symbol":"SAP","name":"SAP SE","sec_type":"STK","exchange":"SMART","primary_exchange":"IBIS","currency":"EUR"}"#
        let decoded = try ServiceJSON.decoder.decode(Contract.self, from: Data(json.utf8))
        #expect(decoded.feedSymbol == "SAP")
    }
}


@Suite struct USMarketTests {
    func utc(_ text: String) -> Date { try! Date.ISO8601FormatStyle().parse(text) }

    @Test func regularSession() {
        #expect(USMarket.isRegularSession(utc("2026-10-21T13:30:00Z")))   // 09:30 New York
        #expect(!USMarket.isRegularSession(utc("2026-10-21T13:29:00Z")))  // 09:29
        #expect(!USMarket.isRegularSession(utc("2026-10-21T20:00:00Z")))  // 16:00 close
        #expect(!USMarket.isRegularSession(utc("2026-10-24T15:00:00Z")))  // Saturday
        #expect(USMarket.isRegularSession(utc("2026-12-01T15:00:00Z")))   // 10:00 in winter time
    }

    @Test func nextOpen() {
        #expect(USMarket.nextOpen(after: utc("2026-10-21T21:14:00Z")) == utc("2026-10-22T13:30:00Z"))  // Wed evening → Thu
        #expect(USMarket.nextOpen(after: utc("2026-10-23T21:00:00Z")) == utc("2026-10-26T13:30:00Z"))  // Fri evening → Mon
        #expect(USMarket.nextOpen(after: utc("2026-10-22T12:00:00Z")) == utc("2026-10-22T13:30:00Z"))  // pre-market
        #expect(USMarket.nextOpen(after: utc("2026-12-01T13:00:00Z")) == utc("2026-12-01T14:30:00Z"))  // winter time
    }

    @Test func closedMarketPricesAreNotStale() {
        let contract = Contract(conid: 1, symbol: "A", name: "A", secType: "STK", exchange: "SMART",
                                primaryExchange: "NASDAQ", currency: "USD", priceSymbol: "A")
        let position = Position(contract: contract, quantity: Amount(1), avgCost: Amount(1), costBasis: Amount(1))
        let evening = utc("2026-10-21T21:14:00Z")
        let quote = Quote(symbol: "A", last: Amount(1), prevClose: Amount(1), changePct: Amount(0),
                          time: utc("2026-10-21T20:00:00Z"))
        #expect(!PricedPosition(position: position, quote: quote, now: evening).isStale)
    }
}


@Suite struct LocalTimeTests {
    let open = try! Date.ISO8601FormatStyle().parse("2026-10-22T13:30:00Z")
    let winter = try! Date.ISO8601FormatStyle().parse("2026-12-01T14:30:00Z")

    @Test func offsetLabels() {
        #expect(LocalTime.offsetLabel(for: open, in: TimeZone(identifier: "Europe/Prague")!) == "UTC+2")
        #expect(LocalTime.offsetLabel(for: winter, in: TimeZone(identifier: "Europe/Prague")!) == "UTC+1")
        #expect(LocalTime.offsetLabel(for: open, in: TimeZone(identifier: "Asia/Kolkata")!) == "UTC+5:30")
        #expect(LocalTime.offsetLabel(for: open, in: TimeZone(identifier: "America/New_York")!) == "UTC−4")
        #expect(LocalTime.offsetLabel(for: open, in: TimeZone(identifier: "UTC")!) == "UTC")
    }

    @Test func weekdayTime() {
        let text = LocalTime.weekdayTime(open, in: TimeZone(identifier: "Europe/Prague")!, locale: Locale(identifier: "en_GB"))
        #expect(text == "Thu 15:30 UTC+2")
    }
}

@Suite struct NumericInputTests {
    @Test func quantitiesAreDigitsOnly() {
        #expect(NumericInput.sanitize("12a3", allowsDecimal: false) == "123")
        #expect(NumericInput.sanitize("-5", allowsDecimal: false) == "5")
        #expect(NumericInput.sanitize("1.5", allowsDecimal: false) == "15")
        #expect(NumericInput.sanitize("abc", allowsDecimal: false) == "")
        #expect(NumericInput.sanitize(" 1 000 ", allowsDecimal: false) == "1000")
    }

    @Test func decimalsKeepOneSeparator() {
        #expect(NumericInput.sanitize("2.5", allowsDecimal: true) == "2.5")
        #expect(NumericInput.sanitize("2,5", allowsDecimal: true) == "2,5")
        #expect(NumericInput.sanitize("1.2.3", allowsDecimal: true) == "1.23")
        #expect(NumericInput.sanitize("+30k", allowsDecimal: true) == "30")
        #expect(NumericInput.sanitize("1e5", allowsDecimal: true) == "15")
    }
}
