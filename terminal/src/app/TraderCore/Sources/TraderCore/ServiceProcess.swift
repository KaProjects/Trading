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
