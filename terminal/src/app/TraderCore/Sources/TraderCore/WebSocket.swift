import CryptoKit
import Foundation

public enum WebSocketError: Error, Equatable {
    case handshakeFailed(String)
    case protocolError(String)
}

enum Opcode: UInt8 {
    case continuation = 0x0, text = 0x1, binary = 0x2, close = 0x8, ping = 0x9, pong = 0xA
}

struct Frame: Equatable {
    let fin: Bool
    let opcode: Opcode
    let payload: Data
}

/// RFC 6455 framing — just what a client of our own service needs.
enum FrameCodec {
    /// Client frames must be masked.
    static func encode(_ opcode: Opcode, payload: Data, maskKey: [UInt8]) -> Data {
        var frame = Data([0x80 | opcode.rawValue])
        switch payload.count {
        case 0..<126:
            frame.append(0x80 | UInt8(payload.count))
        case 126...0xFFFF:
            frame.append(0x80 | 126)
            frame.append(contentsOf: withUnsafeBytes(of: UInt16(payload.count).bigEndian, Array.init))
        default:
            frame.append(0x80 | 127)
            frame.append(contentsOf: withUnsafeBytes(of: UInt64(payload.count).bigEndian, Array.init))
        }
        frame.append(contentsOf: maskKey)
        frame.append(contentsOf: payload.enumerated().map { $0.element ^ maskKey[$0.offset % 4] })
        return frame
    }

    /// `take(n)` must return exactly n bytes or throw.
    static func decode(take: (Int) throws -> Data) throws -> Frame {
        let header = try take(2)
        let b0 = header[header.startIndex], b1 = header[header.startIndex + 1]
        guard let opcode = Opcode(rawValue: b0 & 0x0F) else {
            throw WebSocketError.protocolError("unknown opcode \(b0 & 0x0F)")
        }
        var length = UInt64(b1 & 0x7F)
        if length == 126 {
            length = try take(2).reduce(0) { $0 << 8 | UInt64($1) }
        } else if length == 127 {
            length = try take(8).reduce(0) { $0 << 8 | UInt64($1) }
        }
        guard length <= 16 * 1024 * 1024 else { throw WebSocketError.protocolError("frame too large") }
        let maskKey = b1 & 0x80 != 0 ? Array(try take(4)) : nil
        var payload = try take(Int(length))
        if let maskKey {
            payload = Data(payload.enumerated().map { $0.element ^ maskKey[$0.offset % 4] })
        }
        return Frame(fin: b0 & 0x80 != 0, opcode: opcode, payload: payload)
    }

    static func acceptValue(for key: String) -> String {
        let digest = Insecure.SHA1.hash(data: Data((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").utf8))
        return Data(digest).base64EncodedString()
    }
}

/// Read-only WebSocket client over a Unix socket (the service only pushes events).
public final class UnixWebSocket: @unchecked Sendable {
    public let messages: AsyncThrowingStream<Data, Error>
    private let socket: UnixSocket
    private let lock = NSLock()
    private var closed = false

    private init(socket: UnixSocket, buffered: Data) {
        self.socket = socket
        var continuation: AsyncThrowingStream<Data, Error>.Continuation!
        messages = AsyncThrowingStream { continuation = $0 }
        let stream = continuation!
        stream.onTermination = { [weak self] _ in self?.close() }
        Thread.detachNewThread { [self] in readLoop(buffered: buffered, into: stream) }
    }

    public static func connect(socketPath: String, path: String) async throws -> UnixWebSocket {
        try await onDedicatedThread {
            let socket = try UnixSocket(path: socketPath, timeout: nil)
            let key = Data((0..<16).map { _ in UInt8.random(in: .min ... .max) }).base64EncodedString()
            let request = "GET \(path) HTTP/1.1\r\nHost: service\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: \(key)\r\nSec-WebSocket-Version: 13\r\n\r\n"
            try socket.write(Data(request.utf8))

            var buffer = Data()
            let separator = Data("\r\n\r\n".utf8)
            while buffer.range(of: separator) == nil {
                let chunk = try socket.read()
                if chunk.isEmpty { throw WebSocketError.handshakeFailed("connection closed") }
                buffer.append(chunk)
            }
            let end = buffer.range(of: separator)!
            let head = String(decoding: buffer[buffer.startIndex..<end.lowerBound], as: UTF8.self)
            let lines = head.components(separatedBy: "\r\n")
            guard lines.first?.contains(" 101 ") == true else {
                throw WebSocketError.handshakeFailed(lines.first ?? "no status line")
            }
            let accept = lines.first { $0.lowercased().hasPrefix("sec-websocket-accept:") }
                .map { $0.split(separator: ":", maxSplits: 1)[1].trimmingCharacters(in: .whitespaces) }
            guard accept == FrameCodec.acceptValue(for: key) else {
                throw WebSocketError.handshakeFailed("bad Sec-WebSocket-Accept")
            }
            return UnixWebSocket(socket: socket, buffered: Data(buffer[end.upperBound...]))
        }
    }

    public func close() {
        lock.lock()
        let wasClosed = closed
        closed = true
        lock.unlock()
        guard !wasClosed else { return }
        try? socket.write(FrameCodec.encode(.close, payload: Data(), maskKey: Self.maskKey()))
        socket.shutdown()
    }

    private func readLoop(buffered: Data, into stream: AsyncThrowingStream<Data, Error>.Continuation) {
        var buffer = buffered
        let take: (Int) throws -> Data = { count in
            while buffer.count < count {
                let chunk = try self.socket.read()
                if chunk.isEmpty { throw SocketError.closed }
                buffer.append(chunk)
            }
            let out = Data(buffer.prefix(count))
            buffer.removeFirst(count)
            return out
        }
        var message = Data()
        do {
            while true {
                let frame = try FrameCodec.decode(take: take)
                switch frame.opcode {
                case .text, .binary, .continuation:
                    message.append(frame.payload)
                    if frame.fin {
                        stream.yield(message)
                        message = Data()
                    }
                case .ping:
                    try socket.write(FrameCodec.encode(.pong, payload: frame.payload, maskKey: Self.maskKey()))
                case .pong:
                    break
                case .close:
                    stream.finish()
                    close()
                    return
                }
            }
        } catch {
            lock.lock()
            let expected = closed
            lock.unlock()
            if expected { stream.finish() } else { stream.finish(throwing: error) }
        }
    }

    private static func maskKey() -> [UInt8] {
        (0..<4).map { _ in UInt8.random(in: .min ... .max) }
    }
}
