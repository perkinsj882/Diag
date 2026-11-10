package com.example

import com.example.data.model.SystemCategory
import com.example.util.ObdBleScannerManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests verifying automotive subsystem terminology and diagnostic trouble code mappings.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun automotiveSubsystemLabels_areCorrect() {
    assertEquals("Engine & Powertrain", SystemCategory.MECHANICAL.label)
    assertEquals("ENG", SystemCategory.MECHANICAL.badgeCode)

    assertEquals("Brakes", SystemCategory.HYDRAULIC.label)
    assertEquals("BRK", SystemCategory.HYDRAULIC.badgeCode)
    assertTrue(SystemCategory.HYDRAULIC.subtitle.contains("Electronic Brake Boosters", ignoreCase = true))

    assertEquals("Steering & Suspension", SystemCategory.PNEUMATIC.label)
    assertEquals("STRG", SystemCategory.PNEUMATIC.badgeCode)
    assertTrue(SystemCategory.PNEUMATIC.subtitle.contains("Electric Rack & Pinion", ignoreCase = true))

    assertEquals("Battery, Starting & Charging", SystemCategory.ELECTRICAL.label)
    assertEquals("ELEC", SystemCategory.ELECTRICAL.badgeCode)

    assertEquals("ECU, Modules & CAN-Bus", SystemCategory.COMPUTER_CONTROLLER.label)
    assertEquals("ECU", SystemCategory.COMPUTER_CONTROLLER.badgeCode)

    assertEquals(SystemCategory.HYDRAULIC, SystemCategory.fromLabel("Brakes"))
    assertEquals(SystemCategory.PNEUMATIC, SystemCategory.fromLabel("Steering & Suspension"))
  }

  @Test
  fun dtcMetadata_mapsToAutomotiveSubsystems() {
    val c0035 = ObdBleScannerManager.lookupDtcMetadata("C0035")
    assertEquals(SystemCategory.HYDRAULIC, c0035.second)
    assertTrue(c0035.first.contains("Electronic Brake Booster", ignoreCase = true))

    val c102d = ObdBleScannerManager.lookupDtcMetadata("C102D")
    assertEquals(SystemCategory.PNEUMATIC, c102d.second)
    assertTrue(c102d.first.contains("EPAS Electric Rack & Pinion", ignoreCase = true))
  }

  @Test
  fun guidedVehicleSystems_andObdTelemetryHistory_areValid() {
    val systems = com.example.ui.components.GuidedVehicleSystem.entries
    assertTrue(systems.any { it.displayTitle == "Engine" })
    assertTrue(systems.any { it.displayTitle == "Drivetrain" })
    assertTrue(systems.any { it.displayTitle.contains("Steering") })
    systems.forEach { sys ->
      assertTrue(sys.symptomPresets.size >= 4)
    }

    val history = ObdBleScannerManager.buildInitialTelemetryHistory()
    assertEquals(16, history.size)
    val latest = history.last()
    assertTrue(latest.engineRpm > 0)
    assertTrue(latest.coolantTempC in 70..110)
    assertEquals(
      latest.shortTermFuelTrimB1 + latest.longTermFuelTrimB1,
      latest.totalFuelTrimB1,
      0.001
    )
  }
}
