package com.devtamuno.heliocore

import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.ValidationException
import com.devtamuno.heliocore.services.SolarProductionCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SolarProductionCalculatorTest {

    private val calculator = SolarProductionCalculator(defaultSystemLosses = 0.0)

    @Test
    fun `calculate returns values with specific units`() {
        val request = SolarEstimateRequest(
            latitude = 10.0,
            longitude = 20.0,
            panelWattage = 500.0,
            panelCount = 8,
            panelTilt = 25.0,
            azimuth = 180.0
        )

        val result = calculator.calculate(request, peakSunHours = 5.0)

        assertEquals("kW", result.systemCapacity.unit)
        assertEquals("hours", result.peakSunHours.unit)
        assertEquals("kWh", result.dailyEnergy.unit)
        assertTrue(result.dailyEnergy.value > 0)
    }

    @Test
    fun `calculate applies default system losses and rounds to single decimal`() {
        val calculatorWithLosses = SolarProductionCalculator() // default 14% losses
        val request = SolarEstimateRequest(
            latitude = 5.0,
            longitude = 5.0,
            panelWattage = 1000.0,
            panelCount = 1,
            panelTilt = 20.0,
            azimuth = 180.0
        )

        val result = calculatorWithLosses.calculate(request, peakSunHours = 5.0)

        assertEquals(1.0, result.systemCapacity.value)
        assertEquals(4.3, result.dailyEnergy.value)   // 5.0 * 0.86 = 4.3
        assertEquals(129.0, result.monthlyEnergy.value)
        assertEquals(1569.5, result.annualEnergy.value)
    }

    @Test
    fun `validate rejects out of range tilt`() {
        val badRequest = SolarEstimateRequest(
            latitude = 0.0,
            longitude = 0.0,
            panelWattage = 400.0,
            panelCount = 4,
            panelTilt = 95.0,
            azimuth = 180.0
        )

        assertFailsWith<ValidationException> {
            calculator.validate(badRequest)
        }
    }
}
