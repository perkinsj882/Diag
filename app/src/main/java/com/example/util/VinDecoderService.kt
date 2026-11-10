package com.example.util

import com.example.data.model.EnvironmentPreset
import com.example.data.model.SystemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class DecodedVinInfo(
    val vin: String,
    val year: String,
    val make: String,
    val model: String,
    val trim: String,
    val engineSummary: String,
    val fuelType: String,
    val driveType: String,
    val transmission: String,
    val bodyClass: String,
    val plantCountry: String,
    val sourceLabel: String,
    val formattedVehicleName: String,
    val formattedModelCode: String,
    val recommendedEnvironment: String,
    val recommendedCategory: SystemCategory? = null
)

data class SampleVinPreset(
    val shortLabel: String,
    val vin: String,
    val vehicleHint: String
)

object VinDecoderService {

    private const val NHTSA_VPIC_BASE_URL =
        "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValuesExtended/"

    val sampleVinPresets: List<SampleVinPreset> = listOf(
        SampleVinPreset(
            shortLabel = "2021 Ford F-150 3.5L",
            vin = "1FTFW1E85MFA10492",
            vehicleHint = "Ford F-150 3.5L V6 EcoBoost 4WD"
        ),
        SampleVinPreset(
            shortLabel = "2020 Toyota Camry 2.5L",
            vin = "4T1C11AK5LU892341",
            vehicleHint = "Toyota Camry 2.5L A25A-FKS DOHC"
        ),
        SampleVinPreset(
            shortLabel = "2019 Chevy Silverado",
            vin = "1GCUYDED9KZ219483",
            vehicleHint = "Chevrolet Silverado 1500 5.3L V8"
        ),
        SampleVinPreset(
            shortLabel = "2022 BMW X5 xDrive40i",
            vin = "5UXCR6C05N9K48210",
            vehicleHint = "BMW X5 xDrive40i 3.0L Turbo AWD"
        ),
        SampleVinPreset(
            shortLabel = "2022 Tesla Model 3",
            vin = "5YJ3E1EB7NF184920",
            vehicleHint = "Tesla Model 3 Dual Motor AWD EV"
        ),
        SampleVinPreset(
            shortLabel = "2021 Honda Civic 1.5T",
            vin = "19XFC1F35ME019284",
            vehicleHint = "Honda Civic 1.5L VTEC Turbo"
        )
    )

    fun sanitizeVin(rawInput: String): String {
        return rawInput
            .trim()
            .uppercase(Locale.US)
            .filter { it.isLetterOrDigit() && it !in listOf('I', 'O', 'Q') }
            .take(17)
    }

    /**
     * Queries the official U.S. DOT NHTSA vPIC REST API
     * (`https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValuesExtended/{VIN}?format=json`)
     * to decode Year, Make, Model, Trim, Engine Displacement/Cylinders, Fuel Type, and Drive Type.
     * Falls back to ISO 3779 WMI + 10th-digit Model Year decoding if the device is offline.
     */
    suspend fun decodeVin(rawVin: String): Result<DecodedVinInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanVin = sanitizeVin(rawVin)
            require(cleanVin.length in 11..17) {
                "Please enter a valid 17-character automotive VIN (excluding I, O, Q). Current length: ${cleanVin.length}."
            }

            val apiResult = runCatching { fetchFromNhtsaApi(cleanVin) }.getOrNull()
            if (apiResult != null && apiResult.make.isNotBlank() && apiResult.year.isNotBlank()) {
                return@runCatching apiResult
            }

            // Offline / fallback ISO-3779 WMI + Model Year decoder
            decodeOfflineIso3779(cleanVin)
        }
    }

    private fun fetchFromNhtsaApi(cleanVin: String): DecodedVinInfo {
        val endpoint = "$NHTSA_VPIC_BASE_URL$cleanVin?format=json"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("NHTSA vPIC API returned HTTP $responseCode")
            }

            val responseBody = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            val rootJson = JSONObject(responseBody)
            val resultsArray = rootJson.optJSONArray("Results")
                ?: throw IllegalStateException("Missing Results array in NHTSA vPIC response")
            val item = resultsArray.optJSONObject(0)
                ?: throw IllegalStateException("Empty Results array in NHTSA vPIC response")

            val year = item.optString("ModelYear").trim()
            val rawMake = item.optString("Make").trim()
            val make = formatMakeTitleCase(rawMake)
            val model = item.optString("Model").trim()
            val trim = item.optString("Trim").trim().ifBlank { item.optString("Series").trim() }
            val displacementL = item.optString("DisplacementL").trim()
            val cylinders = item.optString("EngineCylinders").trim()
            val engineModel = item.optString("EngineModel").trim()
            val fuelType = item.optString("FuelTypePrimary").trim().ifBlank { "Gasoline" }
            val driveType = item.optString("DriveType").trim()
            val transmission = item.optString("TransmissionStyle").trim()
            val bodyClass = item.optString("BodyClass").trim()
            val plantCountry = item.optString("PlantCountry").trim()

            if (make.isBlank() && model.isBlank()) {
                throw IllegalStateException("VIN returned empty Make/Model from vPIC")
            }

            val dispFormatted = if (displacementL.isNotBlank()) {
                val d = displacementL.toDoubleOrNull()
                if (d != null) String.format(Locale.US, "%.1fL", d) else "${displacementL}L"
            } else {
                ""
            }
            val cylFormatted = if (cylinders.isNotBlank()) {
                if (cylinders == "4" || cylinders == "6" || cylinders == "8") "${cylinders}-Cyl" else "${cylinders}c"
            } else {
                ""
            }

            val engineSummary = listOf(dispFormatted, cylFormatted, engineModel)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank {
                    if (fuelType.contains("Electric", ignoreCase = true)) "Dual-Motor EV" else "OEM Powertrain"
                }

            val formattedVehicleName = listOf(year, make, model, dispFormatted)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()

            val shortVinSuffix = cleanVin.takeLast(6)
            val codePrefix = when {
                engineModel.isNotBlank() -> engineModel
                dispFormatted.isNotBlank() -> "${dispFormatted.replace(".", "")}-$cylFormatted"
                trim.isNotBlank() -> trim
                else -> make.uppercase(Locale.US).take(4)
            }
            val formattedModelCode = "$codePrefix-VIN$shortVinSuffix"

            val recommendedEnv = inferEnvironmentFromSpecs(fuelType, bodyClass, driveType)

            return DecodedVinInfo(
                vin = cleanVin,
                year = year,
                make = make,
                model = model.ifBlank { "Vehicle" },
                trim = trim,
                engineSummary = engineSummary,
                fuelType = fuelType,
                driveType = driveType.ifBlank { "Standard Drive" },
                transmission = transmission.ifBlank { "Automatic" },
                bodyClass = bodyClass.ifBlank { "Passenger Vehicle" },
                plantCountry = plantCountry.ifBlank { "USA" },
                sourceLabel = "NHTSA vPIC Live API",
                formattedVehicleName = formattedVehicleName,
                formattedModelCode = formattedModelCode,
                recommendedEnvironment = recommendedEnv
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Offline ISO 3779 WMI (chars 1-3), VDS (chars 4-8), and 10th-character Model Year decoder
     * used when the device is in Remote Field Offline Mode or has no internet connectivity.
     */
    internal fun decodeOfflineIso3779(cleanVin: String): DecodedVinInfo {
        val wmi = cleanVin.take(3)
        val yearChar = cleanVin.getOrNull(9) ?: 'M'
        val year = decodeModelYearChar(yearChar)
        val vds = cleanVin.substring(3, minOf(8, cleanVin.length))

        data class OfflineSpec(
            val make: String,
            val model: String,
            val trim: String,
            val engine: String,
            val fuel: String,
            val drive: String,
            val body: String,
            val code: String
        )

        val spec = when {
            cleanVin.startsWith("1FTFW1E8") -> OfflineSpec(
                make = "Ford",
                model = "F-150",
                trim = "SuperCrew 4x4",
                engine = "3.5L EcoBoost V6 Twin-Turbo",
                fuel = "Gasoline",
                drive = "4WD / 4x4",
                body = "Pickup Truck",
                code = "F150-35EB"
            )
            cleanVin.startsWith("4T1C11AK") -> OfflineSpec(
                make = "Toyota",
                model = "Camry",
                trim = "SE / XSE",
                engine = "2.5L Dynamic Force A25A-FKS DOHC",
                fuel = "Gasoline",
                drive = "FWD",
                body = "Sedan",
                code = "A25A-FKS"
            )
            cleanVin.startsWith("1GCUYDED") -> OfflineSpec(
                make = "Chevrolet",
                model = "Silverado 1500",
                trim = "LT Z71 4WD",
                engine = "5.3L EcoTec3 V8 L84",
                fuel = "Gasoline",
                drive = "4WD / 4x4",
                body = "Pickup Truck",
                code = "K2XX-L84"
            )
            cleanVin.startsWith("5UXCR6C0") -> OfflineSpec(
                make = "BMW",
                model = "X5",
                trim = "xDrive40i",
                engine = "3.0L TwinPower Turbo I-6 B58",
                fuel = "Gasoline / 48V Mild Hybrid",
                drive = "AWD (xDrive)",
                body = "Sport Utility Vehicle (SUV)",
                code = "G05-B58"
            )
            cleanVin.startsWith("5YJ3E1EB") -> OfflineSpec(
                make = "Tesla",
                model = "Model 3",
                trim = "Dual Motor Long Range",
                engine = "Dual AC Permanent Magnet / Induction EV",
                fuel = "Electric (HV Battery + 16V LV)",
                drive = "AWD Dual Motor",
                body = "Sedan",
                code = "M3-PCS-EV"
            )
            cleanVin.startsWith("19XFC1F3") -> OfflineSpec(
                make = "Honda",
                model = "Civic",
                trim = "EX-T / Touring",
                engine = "1.5L VTEC Turbo L15B7 DOHC",
                fuel = "Gasoline",
                drive = "FWD",
                body = "Sedan",
                code = "L15B7-CVT"
            )
            else -> {
                val inferredMake = when {
                    wmi.startsWith("1FT") || wmi.startsWith("1FA") || wmi.startsWith("1FM") -> "Ford"
                    wmi.startsWith("1GC") || wmi.startsWith("1G1") -> "Chevrolet"
                    wmi.startsWith("1GT") -> "GMC"
                    wmi.startsWith("1C4") || wmi.startsWith("1C6") -> "Jeep / RAM"
                    wmi.startsWith("4T1") || wmi.startsWith("2T1") || wmi.startsWith("JTD") -> "Toyota"
                    wmi.startsWith("1HG") || wmi.startsWith("19X") || wmi.startsWith("2HG") || wmi.startsWith("JHM") -> "Honda"
                    wmi.startsWith("5YJ") || wmi.startsWith("7SA") -> "Tesla"
                    wmi.startsWith("WBA") || wmi.startsWith("WBS") || wmi.startsWith("5UX") -> "BMW"
                    wmi.startsWith("WDB") || wmi.startsWith("WDD") || wmi.startsWith("4JG") -> "Mercedes-Benz"
                    wmi.startsWith("WAU") || wmi.startsWith("WA1") -> "Audi"
                    wmi.startsWith("1N4") || wmi.startsWith("JN1") -> "Nissan"
                    wmi.startsWith("KM8") || wmi.startsWith("5NP") -> "Hyundai"
                    else -> "Automotive OEM ($wmi)"
                }
                OfflineSpec(
                    make = inferredMake,
                    model = "Series $vds",
                    trim = "Standard Trim",
                    engine = "OBD-II Compliant Powertrain",
                    fuel = if (wmi == "5YJ" || wmi == "7SA") "Electric" else "Gasoline",
                    drive = "AWD / FWD",
                    body = "Automotive Chassis",
                    code = "$wmi-$vds"
                )
            }
        }

        val formattedName = "$year ${spec.make} ${spec.model}"
        val formattedCode = "${spec.code}-VIN${cleanVin.takeLast(6)}"
        val recommendedEnv = inferEnvironmentFromSpecs(spec.fuel, spec.body, spec.drive)

        return DecodedVinInfo(
            vin = cleanVin,
            year = year,
            make = spec.make,
            model = spec.model,
            trim = spec.trim,
            engineSummary = spec.engine,
            fuelType = spec.fuel,
            driveType = spec.drive,
            transmission = "Automatic / OEM",
            bodyClass = spec.body,
            plantCountry = if (cleanVin.startsWith("J")) "Japan" else if (cleanVin.startsWith("W")) "Germany" else "North America",
            sourceLabel = "Offline ISO-3779 WMI Decoder",
            formattedVehicleName = formattedName,
            formattedModelCode = formattedCode,
            recommendedEnvironment = recommendedEnv
        )
    }

    private fun decodeModelYearChar(ch: Char): String {
        return when (ch.uppercaseChar()) {
            'A' -> "2010"
            'B' -> "2011"
            'C' -> "2012"
            'D' -> "2013"
            'E' -> "2014"
            'F' -> "2015"
            'G' -> "2016"
            'H' -> "2017"
            'J' -> "2018"
            'K' -> "2019"
            'L' -> "2020"
            'M' -> "2021"
            'N' -> "2022"
            'P' -> "2023"
            'R' -> "2024"
            'S' -> "2025"
            'T' -> "2026"
            '9' -> "2009"
            '8' -> "2008"
            '7' -> "2007"
            '6' -> "2006"
            '5' -> "2005"
            else -> "2022"
        }
    }

    private fun formatMakeTitleCase(raw: String): String {
        if (raw.isBlank()) return ""
        val upper = raw.uppercase(Locale.US)
        if (upper in listOf("BMW", "GMC", "RAM", "MINI", "BYD")) return upper
        return raw.lowercase(Locale.US).split(Regex("[\\s-]+")).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }

    private fun inferEnvironmentFromSpecs(
        fuelType: String,
        bodyClass: String,
        driveType: String
    ): String {
        return when {
            fuelType.contains("Electric", ignoreCase = true) ->
                EnvironmentPreset.EV_HYBRID_HIGH_VOLTAGE.label
            bodyClass.contains("Pickup", ignoreCase = true) ||
                bodyClass.contains("Truck", ignoreCase = true) ->
                EnvironmentPreset.HEAVY_TOWING_HAULING.label
            driveType.contains("4WD", ignoreCase = true) ||
                driveType.contains("4x4", ignoreCase = true) ->
                EnvironmentPreset.AUTOMOTIVE_SERVICE_BAY.label
            bodyClass.contains("Coupe", ignoreCase = true) ||
                bodyClass.contains("Convertible", ignoreCase = true) ->
                EnvironmentPreset.TRACK_PERFORMANCE.label
            else -> EnvironmentPreset.DAILY_STREET_HIGHWAY.label
        }
    }
}
