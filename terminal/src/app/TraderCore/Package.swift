// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TraderCore",
    platforms: [.macOS(.v15)],
    products: [.library(name: "TraderCore", targets: ["TraderCore"])],
    targets: [
        .target(name: "TraderCore"),
        .testTarget(name: "TraderCoreTests", dependencies: ["TraderCore"]),
    ]
)
