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
