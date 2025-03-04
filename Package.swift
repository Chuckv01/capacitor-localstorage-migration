// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorLocalstorageMigration",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapacitorLocalstorageMigration",
            targets: ["LocalStorageMigrationPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "LocalStorageMigrationPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/LocalStorageMigrationPlugin"),
        .testTarget(
            name: "LocalStorageMigrationPluginTests",
            dependencies: ["LocalStorageMigrationPlugin"],
            path: "ios/Tests/LocalStorageMigrationPluginTests")
    ]
)