import Foundation
import Security

/// Provisioned at launch, never included in the application bundle.
struct ScorerConnection: Codable, Equatable {
    let baseURL: String
    let functionKey: String?

    static func resolve(
        environment: [String: String] = ProcessInfo.processInfo.environment,
        load: () throws -> ScorerConnection? = read,
        save: (ScorerConnection) throws -> Void = write,
        report: (String) -> Void = { NSLog("%@", $0) }
    ) -> ScorerConnection? {
        // Treat an explicit endpoint as a complete replacement. Never send a
        // previously saved key to a different service.
        if let address = environment["MINIMAL_PAIRS_SCORER_URL"] {
            let key = environment["MINIMAL_PAIRS_SCORER_KEY"]?
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let connection = ScorerConnection(
                baseURL: address.trimmingCharacters(in: .whitespacesAndNewlines),
                functionKey: key.flatMap { $0.isEmpty ? nil : $0 }
            )
            guard connection.isValid else {
                report("Phoneme service configuration requires a valid HTTPS base URL.")
                return nil
            }
            do {
                try save(connection)
                report("Phoneme service connection saved to device Keychain.")
            } catch {
                report("Phoneme service connection works for this launch, but could not be saved to Keychain. Provision it again on the next launch.")
            }
            return connection
        }

        // A key on its own belongs to the host's existing endpoint configuration.
        guard environment["MINIMAL_PAIRS_SCORER_KEY"] == nil else { return nil }
        do {
            guard let connection = try load(), connection.isValid else { return nil }
            report("Phoneme service connection restored from device Keychain.")
            return connection
        } catch {
            report("Phoneme service connection could not be read from Keychain. Provision it again.")
            return nil
        }
    }

    private var isValid: Bool {
        guard let url = URLComponents(string: baseURL),
              url.scheme?.lowercased() == "https",
              let host = url.host, !host.isEmpty,
              url.user == nil, url.password == nil,
              url.query == nil, url.fragment == nil else { return false }
        return true
    }

    private static var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: (Bundle.main.bundleIdentifier ?? "com.estebanl.minimalpairs") + ".phoneme-scorer",
            kSecAttrAccount as String: "connection"
        ]
    }

    private static func read() throws -> ScorerConnection? {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw StorageError.keychain(status)
        }
        return try JSONDecoder().decode(ScorerConnection.self, from: data)
    }

    private static func write(_ connection: ScorerConnection) throws {
        let attributes: [String: Any] = [
            kSecValueData as String: try JSONEncoder().encode(connection),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw StorageError.keychain(status) }
    }

    private enum StorageError: Error {
        case keychain(OSStatus)
    }
}
