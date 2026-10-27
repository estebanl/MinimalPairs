package com.minimalpairs.app.data.remote

actual fun defaultPhonemeScorerBaseUrl(): String? = PhonemeScorerConfiguration.baseUrl
    ?: System.getProperty("minimalpairs.scorerUrl")
    ?: System.getenv("MINIMAL_PAIRS_SCORER_URL")

actual fun defaultPhonemeScorerFunctionKey(): String? = PhonemeScorerConfiguration.functionKey
    ?: System.getenv("MINIMAL_PAIRS_SCORER_KEY")
