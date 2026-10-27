// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "MouthTextureTests", platforms: [.macOS(.v12)], targets: [
    .testTarget(name: "TextureTests", path: "Tests/TextureTests")
])
