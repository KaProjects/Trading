import Foundation
import os

/// The Python service as a child process (D2.5). We hold its stdin pipe for our whole lifetime:
/// when the app quits or crashes the pipe closes and the service shuts itself down (`--watch-stdin`).
public final class ServiceProcess: @unchecked Sendable {
    private let process = Process()
    private let stdin = Pipe()
    private let stderr = Pipe()
    private let logger = Logger(subsystem: "org.kaleta.trader-terminal", category: "service")

    public init(executable: URL, arguments: [String], onExit: @escaping @Sendable (Int32) -> Void) {
        process.executableURL = executable
        process.arguments = arguments
        process.standardInput = stdin
        process.standardOutput = FileHandle.nullDevice
        process.standardError = stderr
        process.terminationHandler = { onExit($0.terminationStatus) }
        let logger = logger
        stderr.fileHandleForReading.readabilityHandler = { handle in
            let data = handle.availableData
            guard !data.isEmpty else { return }
            logger.info("\(String(decoding: data, as: UTF8.self), privacy: .private)")
        }
    }

    public var isRunning: Bool { process.isRunning }

    /// D3.3: the data key and lock signal travel over this private pipe, never over the socket.
    public func send(_ message: ControlMessage) throws {
        try stdin.fileHandleForWriting.write(contentsOf: message.line)
    }

    public func start() throws {
        try process.run()
    }

    /// Close stdin (graceful exit), then terminate if it is still running after `grace` seconds.
    public func stop(grace: TimeInterval = 3) {
        try? stdin.fileHandleForWriting.close()
        let process = process
        DispatchQueue.global().asyncAfter(deadline: .now() + grace) {
            if process.isRunning { process.terminate() }
        }
    }

    public func waitUntilExit() {
        process.waitUntilExit()
    }
}

public enum ControlMessage: Sendable {
    case unlock(dataKey: Data)
    case lock
    case finnhubKey(String)
    /// T1: right before Log in; the service hands them to IBC and forgets them (D1.3).
    case ibkrCredentials(username: String, password: String)
    /// T11: IBKR Flex Web Service, for the transactions export; after unlock.
    case flex(token: String, queryId: String)

    var line: Data {
        let object: [String: String] = switch self {
        case .unlock(let key): ["type": "unlock", "data_key": key.base64EncodedString()]
        case .lock: ["type": "lock"]
        case .finnhubKey(let key): ["type": "finnhub_key", "key": key]
        case .ibkrCredentials(let username, let password):
            ["type": "ibkr_credentials", "username": username, "password": password]
        case .flex(let token, let queryId): ["type": "flex", "token": token, "query_id": queryId]
        }
        var data = (try? JSONSerialization.data(withJSONObject: object)) ?? Data()
        data.append(0x0A)
        return data
    }
}
