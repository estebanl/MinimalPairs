import Foundation

/// Compile with ScorerConnection.swift; uses an in-memory store, never real credentials.
struct ScorerConnectionTest {
    private let saved = ScorerConnection(baseURL: "https://old.example", functionKey: "old-test-key")

    func testProvisionedConnectionSurvivesAnOrdinaryRelaunch() {
        var stored: ScorerConnection?
        let provisioned = ScorerConnection.resolve(
            environment: ["MINIMAL_PAIRS_SCORER_URL": " https://scorer.example/ ",
                          "MINIMAL_PAIRS_SCORER_KEY": " test-key "],
            load: { fatalError("Explicit configuration must replace storage") },
            save: { stored = $0 }
        )
        expectEqual(provisioned, ScorerConnection(baseURL: "https://scorer.example/", functionKey: "test-key"))
        expectEqual(stored, provisioned)
        expectEqual(ScorerConnection.resolve(environment: [:], load: { stored },
                                               save: { _ in fatalError("Must not rewrite on ordinary launch") }), provisioned)
    }

    func testChangingEndpointNeverReusesThePreviousKey() {
        var stored = saved
        let connection = ScorerConnection.resolve(
            environment: ["MINIMAL_PAIRS_SCORER_URL": "https://new.example"],
            load: { stored }, save: { stored = $0 }
        )
        expectEqual(connection?.baseURL, "https://new.example")
        expectNil(connection?.functionKey)
        expectNil(stored.functionKey)
    }

    func testBlankKeyClearsTheSavedKey() {
        let connection = ScorerConnection.resolve(
            environment: ["MINIMAL_PAIRS_SCORER_URL": saved.baseURL, "MINIMAL_PAIRS_SCORER_KEY": "  "],
            load: { self.saved }, save: { expectNil($0.functionKey) }
        )
        expectNil(connection?.functionKey)
    }

    func testInvalidAddressesNeverReadOrOverwriteSavedCredentials() {
        for address in ["", " ", "$(SCORER_URL)", "http://scorer.example", "https://",
                        "https://user:password@scorer.example", "https://scorer.example?code=secret",
                        "https://scorer.example#fragment"] {
            var reports = [String]()
            expectNil(ScorerConnection.resolve(
                environment: ["MINIMAL_PAIRS_SCORER_URL": address, "MINIMAL_PAIRS_SCORER_KEY": "secret"],
                load: { fatalError("Must not read old credentials") },
                save: { _ in fatalError("Must not save invalid connection") }, report: { reports.append($0) }
            ))
            expectEqual(reports.count, 1)
            expectFalse(reports[0].contains("secret"))
        }
    }

    func testKeyAloneDoesNotPickUpASavedEndpoint() {
        expectNil(ScorerConnection.resolve(
            environment: ["MINIMAL_PAIRS_SCORER_KEY": "new-test-key"],
            load: { fatalError("Key belongs to host endpoint") },
            save: { _ in fatalError("Must not mix configurations") }
        ))
    }

    func testStorageFailuresKeepCurrentLaunchUsableWithoutLeakingTheKey() {
        enum TestError: Error { case unavailable }
        var reports = [String]()
        let connection = ScorerConnection.resolve(
            environment: ["MINIMAL_PAIRS_SCORER_URL": saved.baseURL, "MINIMAL_PAIRS_SCORER_KEY": "secret"],
            load: { nil }, save: { _ in throw TestError.unavailable }, report: { reports.append($0) }
        )
        expectEqual(connection?.functionKey, "secret")
        expectNil(ScorerConnection.resolve(environment: [:], load: { throw TestError.unavailable },
                                               save: { _ in }, report: { reports.append($0) }))
        expectEqual(reports.count, 2)
        expectFalse(reports.joined().contains("secret"))
        expectNil(ScorerConnection.resolve(environment: [:], load: { nil }, save: { _ in }))
    }

    func testInvalidSavedEndpointIsIgnored() {
        expectNil(ScorerConnection.resolve(
            environment: [:], load: { ScorerConnection(baseURL: "http://old.example", functionKey: "secret") },
            save: { _ in fatalError("Must not save on read") }
        ))
    }
}

private func expectEqual<T: Equatable>(_ actual: T, _ expected: T, file: StaticString = #file, line: UInt = #line) {
    precondition(actual == expected, "Values differ", file: file, line: line)
}

private func expectNil<T>(_ value: T?, file: StaticString = #file, line: UInt = #line) {
    precondition(value == nil, "Expected nil", file: file, line: line)
}

private func expectFalse(_ value: Bool, file: StaticString = #file, line: UInt = #line) {
    precondition(!value, "Expected false", file: file, line: line)
}

@main
enum ScorerConnectionTests {
    static func main() {
        let tests = ScorerConnectionTest()
        tests.testProvisionedConnectionSurvivesAnOrdinaryRelaunch()
        tests.testChangingEndpointNeverReusesThePreviousKey()
        tests.testBlankKeyClearsTheSavedKey()
        tests.testInvalidAddressesNeverReadOrOverwriteSavedCredentials()
        tests.testKeyAloneDoesNotPickUpASavedEndpoint()
        tests.testStorageFailuresKeepCurrentLaunchUsableWithoutLeakingTheKey()
        tests.testInvalidSavedEndpointIsIgnored()
        print("Passed 7 iOS scorer connection regression checks.")
    }
}
