import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        if let connection = ScorerConnection.resolve() {
            PhonemeScorerConfiguration.shared.baseUrl = connection.baseURL
            PhonemeScorerConfiguration.shared.functionKey = connection.functionKey
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
