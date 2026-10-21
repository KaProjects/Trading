import Darwin
import Foundation

public enum SocketError: Error, Equatable {
    case pathTooLong
    case connect(Int32)
    case write(Int32)
    case read(Int32)
    case closed
}

/// Blocking AF_UNIX stream socket. Used from dedicated threads only (never the cooperative pool).
final class UnixSocket: @unchecked Sendable {
    private let fd: Int32

    init(path: String, timeout: TimeInterval?) throws {
        fd = socket(AF_UNIX, SOCK_STREAM, 0)
        guard fd >= 0 else { throw SocketError.connect(errno) }

        var one: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, socklen_t(MemoryLayout<Int32>.size))
        if let timeout {
            var tv = timeval(tv_sec: Int(timeout), tv_usec: Int32((timeout - timeout.rounded(.down)) * 1_000_000))
            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
            setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
        }

        var address = sockaddr_un()
        address.sun_family = sa_family_t(AF_UNIX)
        let bytes = Array(path.utf8)
        guard bytes.count < MemoryLayout.size(ofValue: address.sun_path) else {
            close(fd)
            throw SocketError.pathTooLong
        }
        withUnsafeMutableBytes(of: &address.sun_path) { buffer in
            buffer.copyBytes(from: bytes)
            buffer[bytes.count] = 0
        }
        address.sun_len = UInt8(MemoryLayout<sockaddr_un>.size)
        let result = withUnsafePointer(to: &address) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.connect(fd, $0, socklen_t(MemoryLayout<sockaddr_un>.size))
            }
        }
        guard result == 0 else {
            let code = errno
            close(fd)
            throw SocketError.connect(code)
        }
    }

    deinit { close(fd) }

    func write(_ data: Data) throws {
        try data.withUnsafeBytes { raw in
            var offset = 0
            while offset < raw.count {
                let written = Darwin.write(fd, raw.baseAddress! + offset, raw.count - offset)
                if written < 0 {
                    if errno == EINTR { continue }
                    throw SocketError.write(errno)
                }
                offset += written
            }
        }
    }

    /// Returns an empty Data on EOF.
    func read(maxLength: Int = 65_536) throws -> Data {
        var buffer = [UInt8](repeating: 0, count: maxLength)
        while true {
            let count = Darwin.read(fd, &buffer, maxLength)
            if count < 0 {
                if errno == EINTR { continue }
                throw SocketError.read(errno)
            }
            return Data(buffer[0..<count])
        }
    }

    /// Unblocks a thread sitting in `read` (used to close the event stream).
    func shutdown() {
        Darwin.shutdown(fd, SHUT_RDWR)
    }
}

/// Runs blocking work on its own thread and bridges the result to async code.
func onDedicatedThread<T: Sendable>(_ work: @escaping @Sendable () throws -> T) async throws -> T {
    try await withCheckedThrowingContinuation { continuation in
        Thread.detachNewThread {
            do { continuation.resume(returning: try work()) } catch { continuation.resume(throwing: error) }
        }
    }
}
