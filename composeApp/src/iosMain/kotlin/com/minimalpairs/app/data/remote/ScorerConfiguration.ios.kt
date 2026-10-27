package com.minimalpairs.app.data.remote

import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

actual fun defaultPhonemeScorerBaseUrl(): String? = PhonemeScorerConfiguration.baseUrl
    ?: NSProcessInfo.processInfo.environment["MINIMAL_PAIRS_SCORER_URL"] as? String
    ?: NSBundle.mainBundle.objectForInfoDictionaryKey("PhonemeScorerURL") as? String
