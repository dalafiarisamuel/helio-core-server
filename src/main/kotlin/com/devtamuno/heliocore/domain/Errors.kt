package com.devtamuno.heliocore.domain

import kotlinx.serialization.Serializable

sealed class DomainException(message: String) : RuntimeException(message)
class ValidationException(message: String) : DomainException(message)
class ExternalServiceException(message: String) : DomainException(message)

@Serializable
data class ErrorResponse(val message: String)
