package com.example.util

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Global/Local unit system mode allowing mechanics to toggle between
 * Metric (SI) and Imperial (SAE) automotive measurements.
 */
enum class AutomotiveUnitSystem(
    val label: String,
    val badge: String,
    val torqueUnit: String,
    val pressureUnit: String,
    val tempUnit: String,
    val speedUnit: String
) {
    METRIC(
        label = "Metric (SI)",
        badge = "Nm • bar/kPa • °C",
        torqueUnit = "Nm",
        pressureUnit = "bar / kPa",
        tempUnit = "°C",
        speedUnit = "km/h"
    ),
    IMPERIAL(
        label = "Imperial (SAE)",
        badge = "lb-ft • PSI • °F",
        torqueUnit = "lb-ft",
        pressureUnit = "PSI",
        tempUnit = "°F",
        speedUnit = "mph"
    )
}

enum class MeasurementCategory(
    val title: String,
    val metricPrimaryUnit: String,
    val metricSecondaryUnit: String,
    val imperialPrimaryUnit: String,
    val imperialSecondaryUnit: String
) {
    TORQUE(
        title = "Torque",
        metricPrimaryUnit = "Nm",
        metricSecondaryUnit = "kgf·m",
        imperialPrimaryUnit = "lb-ft",
        imperialSecondaryUnit = "lb-in"
    ),
    PRESSURE(
        title = "Pressure",
        metricPrimaryUnit = "bar",
        metricSecondaryUnit = "kPa",
        imperialPrimaryUnit = "PSI",
        imperialSecondaryUnit = "inHg"
    ),
    TEMPERATURE(
        title = "Temperature",
        metricPrimaryUnit = "°C",
        metricSecondaryUnit = "K",
        imperialPrimaryUnit = "°F",
        imperialSecondaryUnit = "°F (Delta)"
    )
}

data class AutomotiveConversionResult(
    val category: MeasurementCategory,
    val inputUnitSystem: AutomotiveUnitSystem,
    val rawInputValue: Double,
    val metricPrimaryFormatted: String,
    val metricSecondaryFormatted: String,
    val imperialPrimaryFormatted: String,
    val imperialSecondaryFormatted: String,
    val primaryDisplayLabel: String,
    val secondaryDisplayLabel: String,
    val shopNoteSummary: String
)

data class AutomotiveMeasurementPreset(
    val id: String,
    val category: MeasurementCategory,
    val componentLabel: String,
    val metricValue: Double,
    val imperialValue: Double,
    val oemApplicationNote: String
)

object AutomotiveUnitConverter {

    // --- TORQUE CONVERSIONS ---
    private const val NM_TO_LB_FT = 0.737562149
    private const val LB_FT_TO_NM = 1.355817948
    private const val NM_TO_LB_IN = 8.85074579
    private const val NM_TO_KGF_M = 0.101971621

    fun nmToLbFt(nm: Double): Double = nm * NM_TO_LB_FT
    fun lbFtToNm(lbFt: Double): Double = lbFt * LB_FT_TO_NM
    fun nmToLbIn(nm: Double): Double = nm * NM_TO_LB_IN
    fun lbInToNm(lbIn: Double): Double = lbIn / NM_TO_LB_IN

    // --- PRESSURE CONVERSIONS ---
    private const val BAR_TO_PSI = 14.5037738
    private const val PSI_TO_BAR = 0.0689475729
    private const val BAR_TO_KPA = 100.0
    private const val KPA_TO_PSI = 0.145037738
    private const val PSI_TO_KPA = 6.89475729
    private const val BAR_TO_IN_HG = 29.529983

    fun barToPsi(bar: Double): Double = bar * BAR_TO_PSI
    fun psiToBar(psi: Double): Double = psi * PSI_TO_BAR
    fun kpaToPsi(kpa: Double): Double = kpa * KPA_TO_PSI
    fun psiToKpa(psi: Double): Double = psi * PSI_TO_KPA
    fun kpaToBar(kpa: Double): Double = kpa / BAR_TO_KPA

    // --- TEMPERATURE CONVERSIONS ---
    fun celsiusToFahrenheit(c: Double): Double = (c * 9.0 / 5.0) + 32.0
    fun fahrenheitToCelsius(f: Double): Double = (f - 32.0) * 5.0 / 9.0
    fun celsiusToKelvin(c: Double): Double = c + 273.15

    // --- SPEED CONVERSIONS (FOR LIVE OBD-II TELEMETRY) ---
    fun kmhToMph(kmh: Int): Int = (kmh * 0.621371).roundToInt()

    /**
     * Evaluates a user-entered value in either Metric or Imperial units and returns
     * complete converted measurements across both unit systems.
     */
    fun convert(
        category: MeasurementCategory,
        inputValue: Double,
        inputSystem: AutomotiveUnitSystem
    ): AutomotiveConversionResult {
        return when (category) {
            MeasurementCategory.TORQUE -> {
                val nm = if (inputSystem == AutomotiveUnitSystem.METRIC) {
                    inputValue
                } else {
                    lbFtToNm(inputValue)
                }
                val lbFt = nmToLbFt(nm)
                val lbIn = nmToLbIn(nm)
                val kgfM = nm * NM_TO_KGF_M

                val metricMain = String.format(Locale.US, "%.1f Nm", nm)
                val metricSub = String.format(Locale.US, "%.2f kgf·m", kgfM)
                val impMain = String.format(Locale.US, "%.1f lb-ft", lbFt)
                val impSub = String.format(Locale.US, "%.0f lb-in", lbIn)

                AutomotiveConversionResult(
                    category = category,
                    inputUnitSystem = inputSystem,
                    rawInputValue = inputValue,
                    metricPrimaryFormatted = metricMain,
                    metricSecondaryFormatted = metricSub,
                    imperialPrimaryFormatted = impMain,
                    imperialSecondaryFormatted = impSub,
                    primaryDisplayLabel = if (inputSystem == AutomotiveUnitSystem.METRIC) impMain else metricMain,
                    secondaryDisplayLabel = if (inputSystem == AutomotiveUnitSystem.METRIC) impSub else metricSub,
                    shopNoteSummary = "Torque Spec: $metricMain ($impMain / $impSub)"
                )
            }

            MeasurementCategory.PRESSURE -> {
                val bar = if (inputSystem == AutomotiveUnitSystem.METRIC) {
                    inputValue
                } else {
                    psiToBar(inputValue)
                }
                val kpa = bar * BAR_TO_KPA
                val psi = barToPsi(bar)
                val inHg = bar * BAR_TO_IN_HG

                val metricMain = String.format(Locale.US, "%.2f bar", bar)
                val metricSub = String.format(Locale.US, "%.0f kPa", kpa)
                val impMain = String.format(Locale.US, "%.1f PSI", psi)
                val impSub = String.format(Locale.US, "%.1f inHg", inHg)

                AutomotiveConversionResult(
                    category = category,
                    inputUnitSystem = inputSystem,
                    rawInputValue = inputValue,
                    metricPrimaryFormatted = metricMain,
                    metricSecondaryFormatted = metricSub,
                    imperialPrimaryFormatted = impMain,
                    imperialSecondaryFormatted = impSub,
                    primaryDisplayLabel = if (inputSystem == AutomotiveUnitSystem.METRIC) impMain else "$metricMain ($metricSub)",
                    secondaryDisplayLabel = if (inputSystem == AutomotiveUnitSystem.METRIC) "$metricSub • $impSub" else impSub,
                    shopNoteSummary = "Pressure Reading: $metricMain / $metricSub ($impMain)"
                )
            }

            MeasurementCategory.TEMPERATURE -> {
                val celsius = if (inputSystem == AutomotiveUnitSystem.METRIC) {
                    inputValue
                } else {
                    fahrenheitToCelsius(inputValue)
                }
                val fahrenheit = celsiusToFahrenheit(celsius)
                val kelvin = celsiusToKelvin(celsius)

                val metricMain = String.format(Locale.US, "%.1f °C", celsius)
                val metricSub = String.format(Locale.US, "%.1f K", kelvin)
                val impMain = String.format(Locale.US, "%.1f °F", fahrenheit)
                val thermalState = when {
                    celsius < 0.0 -> "Sub-Freezing Cold Soak"
                    celsius <= 98.0 -> "Normal OEM Operating Range"
                    celsius <= 108.0 -> "Elevated Thermal Load (High Fan)"
                    else -> "CRITICAL OVERHEAT THRESHOLD"
                }

                AutomotiveConversionResult(
                    category = category,
                    inputUnitSystem = inputSystem,
                    rawInputValue = inputValue,
                    metricPrimaryFormatted = metricMain,
                    metricSecondaryFormatted = metricSub,
                    imperialPrimaryFormatted = impMain,
                    imperialSecondaryFormatted = thermalState,
                    primaryDisplayLabel = if (inputSystem == AutomotiveUnitSystem.METRIC) impMain else metricMain,
                    secondaryDisplayLabel = "$thermalState ($metricSub)",
                    shopNoteSummary = "Temperature Reading: $metricMain ($impMain) — $thermalState"
                )
            }
        }
    }

    /**
     * Formats live OBD-II coolant or intake temperature according to the active unit system.
     */
    fun formatObdTemperature(tempC: Int, unitSystem: AutomotiveUnitSystem): String {
        return if (unitSystem == AutomotiveUnitSystem.METRIC) {
            "${tempC}°C"
        } else {
            val tempF = celsiusToFahrenheit(tempC.toDouble()).roundToInt()
            "${tempF}°F"
        }
    }

    /**
     * Formats live OBD-II Manifold Absolute Pressure (kPa) according to the active unit system.
     */
    fun formatObdPressure(mapKpa: Int, unitSystem: AutomotiveUnitSystem): String {
        return if (unitSystem == AutomotiveUnitSystem.METRIC) {
            val bar = kpaToBar(mapKpa.toDouble())
            String.format(Locale.US, "%d kPa (%.2f bar)", mapKpa, bar)
        } else {
            val psi = kpaToPsi(mapKpa.toDouble())
            String.format(Locale.US, "%.1f PSI", psi)
        }
    }

    /**
     * Formats live OBD-II Vehicle Speed according to the active unit system.
     */
    fun formatObdSpeed(speedKmh: Int, unitSystem: AutomotiveUnitSystem): Pair<String, String> {
        return if (unitSystem == AutomotiveUnitSystem.METRIC) {
            "$speedKmh" to "km/h"
        } else {
            "${kmhToMph(speedKmh)}" to "mph"
        }
    }

    val PRESETS: List<AutomotiveMeasurementPreset> = listOf(
        // Torque Presets
        AutomotiveMeasurementPreset(
            id = "torque_lug_nuts",
            category = MeasurementCategory.TORQUE,
            componentLabel = "Wheel Lug Nuts (M14)",
            metricValue = 135.0,
            imperialValue = 100.0,
            oemApplicationNote = "Star pattern in 3 stages; never use impact wrench for final torque"
        ),
        AutomotiveMeasurementPreset(
            id = "torque_spark_plug",
            category = MeasurementCategory.TORQUE,
            componentLabel = "Spark Plugs (M12 Alloy Head)",
            metricValue = 20.0,
            imperialValue = 14.8,
            oemApplicationNote = "Dry threads on nickel-plated shells; 20 Nm (177 lb-in)"
        ),
        AutomotiveMeasurementPreset(
            id = "torque_oil_drain",
            category = MeasurementCategory.TORQUE,
            componentLabel = "Engine Oil Pan Drain Plug",
            metricValue = 34.0,
            imperialValue = 25.0,
            oemApplicationNote = "Replace crush washer every oil service"
        ),
        AutomotiveMeasurementPreset(
            id = "torque_caliper_bracket",
            category = MeasurementCategory.TORQUE,
            componentLabel = "Brake Caliper Anchor Bracket",
            metricValue = 170.0,
            imperialValue = 125.0,
            oemApplicationNote = "Apply medium-strength blue threadlocker to clean threads"
        ),
        AutomotiveMeasurementPreset(
            id = "torque_valve_cover",
            category = MeasurementCategory.TORQUE,
            componentLabel = "Valve Cover / Coil Bolt (M6)",
            metricValue = 10.0,
            imperialValue = 7.4,
            oemApplicationNote = "Low-range inch-pound fastener: 10 Nm = 89 lb-in"
        ),

        // Pressure Presets
        AutomotiveMeasurementPreset(
            id = "pressure_tire_cold",
            category = MeasurementCategory.PRESSURE,
            componentLabel = "Passenger Tire Cold Inflation",
            metricValue = 2.41,
            imperialValue = 35.0,
            oemApplicationNote = "2.41 bar (241 kPa / 35.0 PSI) measured after 3h cold soak"
        ),
        AutomotiveMeasurementPreset(
            id = "pressure_fuel_port",
            category = MeasurementCategory.PRESSURE,
            componentLabel = "Port Fuel Injection Rail",
            metricValue = 4.00,
            imperialValue = 58.0,
            oemApplicationNote = "4.0 bar (400 kPa / 58 PSI) key-on prime & idle rail pressure"
        ),
        AutomotiveMeasurementPreset(
            id = "pressure_turbo_boost",
            category = MeasurementCategory.PRESSURE,
            componentLabel = "EcoBoost Peak Turbo Boost",
            metricValue = 1.24,
            imperialValue = 18.0,
            oemApplicationNote = "1.24 bar gauge (124 kPa / 18.0 PSI) under WOT load"
        ),
        AutomotiveMeasurementPreset(
            id = "pressure_oil_hot_idle",
            category = MeasurementCategory.PRESSURE,
            componentLabel = "Hot Idle Engine Oil Gallery",
            metricValue = 2.07,
            imperialValue = 30.0,
            oemApplicationNote = "2.07 bar (207 kPa / 30 PSI) at 800 RPM with 95°C oil"
        ),
        AutomotiveMeasurementPreset(
            id = "pressure_cooling_cap",
            category = MeasurementCategory.PRESSURE,
            componentLabel = "Radiator Surge Cap Relief",
            metricValue = 1.10,
            imperialValue = 16.0,
            oemApplicationNote = "1.10 bar (110 kPa / 16 PSI) pressure test hold for 2 mins"
        ),

        // Temperature Presets
        AutomotiveMeasurementPreset(
            id = "temp_thermostat",
            category = MeasurementCategory.TEMPERATURE,
            componentLabel = "OEM Thermostat Opening",
            metricValue = 92.0,
            imperialValue = 197.6,
            oemApplicationNote = "Standard closed-loop ECT regulation target"
        ),
        AutomotiveMeasurementPreset(
            id = "temp_cooling_fan",
            category = MeasurementCategory.TEMPERATURE,
            componentLabel = "Radiator Fan High-Speed Trigger",
            metricValue = 104.0,
            imperialValue = 219.2,
            oemApplicationNote = "PCM commands 90%+ PWM fan duty cycle above 104°C"
        ),
        AutomotiveMeasurementPreset(
            id = "temp_overheat_limit",
            category = MeasurementCategory.TEMPERATURE,
            componentLabel = "Cylinder Head Overheat Warning",
            metricValue = 115.0,
            imperialValue = 239.0,
            oemApplicationNote = "Shut down engine immediately to prevent head gasket warping"
        ),
        AutomotiveMeasurementPreset(
            id = "temp_atf_normal",
            category = MeasurementCategory.TEMPERATURE,
            componentLabel = "Transmission ATF Level Check",
            metricValue = 82.0,
            imperialValue = 179.6,
            oemApplicationNote = "Verify ATF thermal expansion between 80°C and 90°C"
        ),
        AutomotiveMeasurementPreset(
            id = "temp_dot4_boil",
            category = MeasurementCategory.TEMPERATURE,
            componentLabel = "DOT 4 Brake Fluid Dry Boiling",
            metricValue = 230.0,
            imperialValue = 446.0,
            oemApplicationNote = "Minimum FMVSS 116 DOT 4 dry boiling point specification"
        )
    )
}
