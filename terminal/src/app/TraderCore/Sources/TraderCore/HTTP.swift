import Foundation

public struct HTTPResponse: Sendable, Equatable {
    public let status: Int
    public let headers: [String: String]
    public let body: Data
}

public enum HTTPError: Error, Equatable {
    case malformedResponse
}

enum HTTPParser {
    private static let separator = Data("\r\n\r\n".utf8)

    static func parse(_ raw: Data) throws -> HTTPResponse {
        guard let end = raw.range(of: separator),
              let head = String(data: raw[raw.startIndex..<end.lowerBound], encoding: .utf8) else {
            throw HTTPError.malformedResponse
        }
        var lines = head.components(separatedBy: "\r\n")
        let statusParts = lines.removeFirst().split(separator: " ", maxSplits: 2)
        guard statusParts.count >= 2, statusParts[0].hasPrefix("HTTP/"), let status = Int(statusParts[1]) else {
            throw HTTPError.malformedResponse
        }
        var headers: [String: String] = [:]
        for line in lines {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let name = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
            headers[name] = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        }
        var body = Data(raw[end.upperBound...])
        if headers["transfer-encoding"]?.lowercased() == "chunked" {
            body = try dechunk(body)
        } else if let length = headers["content-length"].flatMap(Int.init), body.count > length {
            body = body.prefix(length)
        }
        return HTTPResponse(status: status, headers: headers, body: body)
    }

    static func dechunk(_ data: Data) throws -> Data {
        var output = Data()
        var rest = data[...]
        let crlf = Data("\r\n".utf8)
        while true {
            guard let lineEnd = rest.range(of: crlf),
                  let sizeLine = String(data: rest[rest.startIndex..<lineEnd.lowerBound], encoding: .ascii),
                  let size = Int(sizeLine.split(separator: ";")[0].trimmingCharacters(in: .whitespaces), radix: 16)
            else { throw HTTPError.malformedResponse }
            if size == 0 { return output }
            let start = lineEnd.upperBound
            guard rest.distance(from: start, to: rest.endIndex) >= size + 2 else { throw HTTPError.malformedResponse }
            let chunkEnd = rest.index(start, offsetBy: size)
            output.append(rest[start..<chunkEnd])
            rest = rest[rest.index(chunkEnd, offsetBy: 2)...]
        }
    }
}

/// Minimal HTTP/1.1 client over a Unix socket: one connection per request, `Connection: close`.
public struct UnixHTTPClient: Sendable {
    public let socketPath: String
    public var timeout: TimeInterval

    public init(socketPath: String, timeout: TimeInterval = 10) {
        self.socketPath = socketPath
        self.timeout = timeout
    }

    public func send(_ method: String, _ path: String, json: Data? = nil) async throws -> HTTPResponse {
        let socketPath = socketPath, timeout = timeout
        return try await onDedicatedThread {
            let socket = try UnixSocket(path: socketPath, timeout: timeout)
            var head = "\(method) \(path) HTTP/1.1\r\nHost: service\r\nConnection: close\r\nAccept: application/json\r\n"
            if let json {
                head += "Content-Type: application/json\r\nContent-Length: \(json.count)\r\n"
            } else if method != "GET" {
                head += "Content-Length: 0\r\n"
            }
            var request = Data((head + "\r\n").utf8)
            if let json { request.append(json) }
            try socket.write(request)

            var raw = Data()
            while true {
                let chunk = try socket.read()
                if chunk.isEmpty { break }
                raw.append(chunk)
            }
            return try HTTPParser.parse(raw)
        }
    }
}
