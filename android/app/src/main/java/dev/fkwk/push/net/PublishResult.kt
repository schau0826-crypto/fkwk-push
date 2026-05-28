package dev.fkwk.push.net

sealed interface PublishResult {
    data class Success(val httpCode: Int) : PublishResult
    data class Failure(val httpCode: Int?, val error: String) : PublishResult
}
