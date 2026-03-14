package com.devtamuno.heliocore.services

import com.devtamuno.heliocore.domain.SolarConfigRequest
import com.devtamuno.heliocore.domain.SolarConfigResponse
import com.devtamuno.heliocore.domain.SolarValidator
import com.devtamuno.heliocore.domain.ValidationException
import com.devtamuno.heliocore.repository.SolarConfigRepository
import java.util.UUID

class SolarConfigService(private val repository: SolarConfigRepository) {

    suspend fun create(userId: UUID, request: SolarConfigRequest): SolarConfigResponse {
        validate(request)
        return repository.create(userId, request)
    }

    suspend fun getByUserId(userId: UUID): List<SolarConfigResponse> {
        return repository.getByUserId(userId)
    }

    suspend fun deleteById(userId: UUID, id: String): Boolean {
        val uuid = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Invalid ID format")
        }
        return repository.deleteById(userId, uuid)
    }

    suspend fun update(userId: UUID, id: String, request: SolarConfigRequest): Boolean {
        val uuid = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Invalid ID format")
        }
        validate(request)
        return repository.update(userId, uuid, request)
    }

    private fun validate(request: SolarConfigRequest) {
        SolarValidator.validate(
            latitude = request.latitude,
            longitude = request.longitude,
            panelWattage = request.panelWattage,
            panelCount = request.panelCount,
            panelTilt = request.panelTilt,
            azimuth = request.azimuth
        )
    }
}
