package com.example.data.model

object StarterTemplates {

    data class EquipmentSeed(
        val name: String,
        val modelNumber: String,
        val systemCategory: SystemCategory,
        val environmentType: String,
        val operatingHours: Long,
        val notes: String
    )

    data class FlowchartSeed(
        val title: String,
        val equipmentModel: String,
        val systemCategory: SystemCategory,
        val environmentType: String,
        val failureMode: String,
        val nodes: List<FlowchartNode>
    )

    val starterEquipment = listOf(
        EquipmentSeed(
            name = "Siemens SIMATIC S7-1500 PLC Cell Controller",
            modelNumber = "6ES7515-2AM02-0AB0",
            systemCategory = SystemCategory.COMPUTER_CONTROLLER,
            environmentType = EnvironmentPreset.MANUFACTURING.label,
            operatingHours = 6420L,
            notes = "PROFINET ring master controlling robotic weld station #4; firmware v2.9.4"
        ),
        EquipmentSeed(
            name = "Dell PowerEdge R750 Industrial Edge Server",
            modelNumber = "PER750-2X6338-NVME",
            systemCategory = SystemCategory.COMPUTER,
            environmentType = EnvironmentPreset.DATA_CENTER.label,
            operatingHours = 11850L,
            notes = "Dual Xeon SCADA historian & vision inference server; RAID-10 NVMe array"
        ),
        EquipmentSeed(
            name = "Caterpillar 320D Main Hydraulic System",
            modelNumber = "CAT-320D-SBS120",
            systemCategory = SystemCategory.HYDRAULIC,
            environmentType = EnvironmentPreset.HEAVY_INDUSTRIAL.label,
            operatingHours = 4890L,
            notes = "Variable displacement axial piston twin pump; ISO VG 46 hydraulic oil"
        ),
        EquipmentSeed(
            name = "Festo CPX-FB36 Pneumatic Valve Terminal",
            modelNumber = "CPX-MPA1-24V-8bar",
            systemCategory = SystemCategory.PNEUMATIC,
            environmentType = EnvironmentPreset.MANUFACTURING.label,
            operatingHours = 5100L,
            notes = "6.5 bar regulated supply with 5-micron coalescing filter"
        ),
        EquipmentSeed(
            name = "ABB M3BP 45kW 3-Phase Induction Drive Motor",
            modelNumber = "M3BP-225SMB-4",
            systemCategory = SystemCategory.ELECTRICAL,
            environmentType = EnvironmentPreset.MARINE_OFFSHORE.label,
            operatingHours = 8900L,
            notes = "400V 50Hz IP55 marine-duty auxiliary pump motor driven by ACS880 VFD"
        ),
        EquipmentSeed(
            name = "Haas VF-4SS High-Speed CNC Spindle Gearbox",
            modelNumber = "VF4SS-12K-INLINE",
            systemCategory = SystemCategory.MECHANICAL,
            environmentType = EnvironmentPreset.MANUFACTURING.label,
            operatingHours = 3740L,
            notes = "12,000 RPM inline direct-drive spindle with air-oil mist lubrication"
        )
    )

    val starterFlowcharts = listOf(
        // 1. COMPUTER CONTROLLER (PLC / ECU / CNC)
        FlowchartSeed(
            title = "PLC CPU Fault LED & PROFINET I/O Bus Drop",
            equipmentModel = "Siemens S7-1500 / ControlLogix L83E",
            systemCategory = SystemCategory.COMPUTER_CONTROLLER,
            environmentType = EnvironmentPreset.MANUFACTURING.label,
            failureMode = "Controller enters STOP or flashes red BF/SF LED with intermittent remote I/O station dropouts",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "Is 24V DC backplane supply between 22.8V and 25.2V under peak solenoid load?",
                    observationHint = "Measure L+ to M directly at the PLC power input terminals using a true-RMS multimeter with MIN/MAX hold enabled.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Verify 24V DC rail stability"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "Does the CPU diagnostic buffer show OB86 Rack Failure or PROFINET CRC packet errors on Port X1?",
                    observationHint = "Check the front panel display Diagnostics > Diagnostic Buffer or online engineering portal.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Inspect CPU diagnostic buffer"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "24V Switched-Mode Power Supply (SMPS) overload or loose terminal block causing brownout resets.",
                    observationHint = "Voltage dips below 20.4V trip the PLC power-good supervisor circuit.",
                    recommendedAction = "Isolate inductive solenoid loads onto a separate fused 24V supply, torque L+/M terminals to 0.6 Nm, and replace degraded SMPS."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "QUESTION",
                    prompt = "Are shielded RJ45/M12 Ethernet cables routed within 100mm of unshielded VFD motor power leads?",
                    observationHint = "Inspect cable tray separation and verify 360-degree shield grounding at cabinet entry.",
                    yesNodeId = "N6",
                    noNodeId = "N7",
                    recommendedAction = "Check EMI shielding and routing"
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Program watchdog timeout (OB80) or unallocated I/O address access fault.",
                    observationHint = "Cycle time exceeded configured maximum scan limit or analog module hardware address mismatch.",
                    recommendedAction = "Inspect recent ladder/SCL loop logic for unbounded loops, verify hardware configuration matches physical slot modules, and reload hardware config."
                ),
                FlowchartNode(
                    nodeId = "N6",
                    nodeType = "CONCLUSION",
                    prompt = "Common-mode EMI noise from VFD PWM switching corrupting PROFINET frames.",
                    observationHint = "High dV/dt motor leads induce ground loop currents on communication shields.",
                    recommendedAction = "Reroute industrial Ethernet cable >= 200mm away from VFD power cables, bond braided shield to PE busbar at both ends, and replace damaged RJ45 connector."
                ),
                FlowchartNode(
                    nodeId = "N7",
                    nodeType = "CONCLUSION",
                    prompt = "Remote I/O interface module or backplane bus terminator failure.",
                    observationHint = "Single remote node link LED is dark or amber while upstream switch port is active.",
                    recommendedAction = "Reseat the ET200SP/Point I/O bus adapter, inspect gold backplane pins for fretting corrosion, and replace the terminating bus module."
                )
            )
        ),
        // 2. COMPUTER (Industrial PC / Rack Server / Workstation)
        FlowchartSeed(
            title = "Server / Industrial PC No-POST & Kernel Panic Isolation",
            equipmentModel = "Dell PowerEdge R750 / Advantech IPC-610",
            systemCategory = SystemCategory.COMPUTER,
            environmentType = EnvironmentPreset.DATA_CENTER.label,
            failureMode = "System fails to complete POST, emits diagnostic beep/LED codes, or crashes under compute load",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "Do the motherboard standby LEDs illuminate and PSU fans spin briefly when power is applied?",
                    observationHint = "Check rear PSU status LED (solid green vs blinking amber) and BMC/iDRAC heartbeat LED.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Check standby power rail"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "Does the system halt during POST with DRAM/Memory training error codes before video output?",
                    observationHint = "Observe front panel diagnostic LED code or POST hex display (e.g., codes B0-BF / 53).",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Observe POST code stage"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Hot-swap PSU failure, backplane 12V short circuit, or CMOS RTC battery depletion (< 2.6V).",
                    observationHint = "Blinking amber PSU LED indicates over-current protection (OCP) latch.",
                    recommendedAction = "Test with a single known-good PSU in Slot 1, measure CR2032 CMOS battery voltage (replace if < 2.8V), and disconnect non-essential PCIe cards to clear 12V short."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "DIMM channel training fault or oxidized DDR4/DDR5 edge connector.",
                    observationHint = "Single-bit or multi-bit ECC training failure on populated memory channel.",
                    recommendedAction = "Power off and discharge fleas, boot with minimum 1 DIMM per CPU in primary slot A1, clean gold contacts with 99% isopropyl alcohol, and swap suspect DIMM."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "QUESTION",
                    prompt = "Does CPU package temperature exceed 92C or VRM telemetry report thermal throttling before crash?",
                    observationHint = "Check BMC/iDRAC hardware health log or BIOS thermal sensors.",
                    yesNodeId = "N6",
                    noNodeId = "N7",
                    recommendedAction = "Check CPU/VRM thermal telemetry"
                ),
                FlowchartNode(
                    nodeId = "N6",
                    nodeType = "CONCLUSION",
                    prompt = "Chassis airflow obstruction, degraded thermal interface material (TIM), or failed PWM fan module.",
                    observationHint = "Thermal trip (THERMTRIP#) asserts when junction temp exceeds TjMax.",
                    recommendedAction = "Clean front intake dust filter, verify all hot-swap fan modules run above 4,000 RPM under load, and clean/reapply high-conductivity thermal paste on CPU heatsink."
                ),
                FlowchartNode(
                    nodeId = "N7",
                    nodeType = "CONCLUSION",
                    prompt = "NVMe RAID controller namespace degradation or PCIe Riser lane retraining WHEA fault.",
                    observationHint = "OS logs show WHEA-Logger Event ID 17/18 or NVMe I/O timeout.",
                    recommendedAction = "Reseat PCIe Riser assembly, check SMART wear-leveling percentage on NVMe drives via nvme-cli/iDRAC, and update storage controller firmware."
                )
            )
        ),
        // 3. HYDRAULIC
        FlowchartSeed(
            title = "Hydraulic Pump Cavitation & Loss of Actuator Force",
            equipmentModel = "CAT 320D / Rexroth A10VSO Axial Piston",
            systemCategory = SystemCategory.HYDRAULIC,
            environmentType = EnvironmentPreset.HEAVY_INDUSTRIAL.label,
            failureMode = "High-pitched rattling whine from main pump and sluggish cylinder movement under load",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "Is hydraulic fluid in the sight glass milky, foamy, or below the minimum cold mark?",
                    observationHint = "Inspect reservoir sight gauge and check oil temperature (nominal 45C-65C).",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Inspect hydraulic fluid condition"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "CONCLUSION",
                    prompt = " Fluid aeration from low reservoir level or suction line air ingress.",
                    observationHint = "Air bubbles compress violently on the high-pressure outlet port, eroding swashplate shoes.",
                    recommendedAction = "Top up with clean ISO VG 46 hydraulic oil, tighten suction hose T-bolt clamps, and pressure-test pump inlet flange O-ring."
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "QUESTION",
                    prompt = "Does inlet vacuum at the pump suction port exceed 0.25 bar (7.5 inHg) at rated RPM?",
                    observationHint = "Connect compound vacuum gauge to pump suction diagnostic port.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Measure suction vacuum"
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Collapsed suction hose liner or clogged tank suction strainer.",
                    observationHint = "Starved pump inlet causes vapor cavity formation (true cavitation).",
                    recommendedAction = "Depressurize tank breather, remove and ultrasonic-clean the 100-mesh suction strainer, and replace delaminated suction hose."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Main pressure relief valve stuck partially open or excessive pump case drain bypass.",
                    observationHint = "Use IR thermometer: a relief valve bypassing high-pressure oil will be >20C hotter than adjacent lines.",
                    recommendedAction = "Measure pump case drain flow (< 10% of rated flow) and clean/re-calibrate main relief valve cartridge to factory spec."
                )
            )
        ),
        // 4. ELECTRICAL
        FlowchartSeed(
            title = "3-Phase Motor Overload Trip & Ground Fault Isolation",
            equipmentModel = "ABB M3BP 45kW / 400V VFD Drive",
            systemCategory = SystemCategory.ELECTRICAL,
            environmentType = EnvironmentPreset.MARINE_OFFSHORE.label,
            failureMode = "Motor breaker or VFD trips on Overcurrent / Earth Fault within seconds of start command",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "With LOTO applied and motor leads disconnected from VFD, does the VFD still trip when run unloaded?",
                    observationHint = "Perform Lockout/Tagout, verify zero voltage, and unbolt U/V/W motor cables at drive output.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Isolate drive from motor"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "CONCLUSION",
                    prompt = "Shorted VFD IGBT output transistor module or faulty internal current transducer.",
                    observationHint = "Diode-test U/V/W to DC+ and DC- bus terminals; balanced forward drop should be ~0.45V.",
                    recommendedAction = "Replace VFD power stage module or entire drive unit; inspect DC bus capacitors for bulging."
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "QUESTION",
                    prompt = "Is insulation resistance (Megger test at 500V DC) from U, V, W to Earth Frame greater than 100 MOhm?",
                    observationHint = "Connect megohmmeter between motor stator leads and clean unpainted motor ground lug.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Run 500V DC Megger insulation test"
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Mechanical shaft seizure or phase-to-phase turn-to-turn winding imbalance.",
                    observationHint = "Rotate motor shaft by hand and compare U-V, V-W, W-U milliohm resistance (must match within 2%).",
                    recommendedAction = "Uncouple driven pump/load to verify free shaft rotation; if locked, replace seized drive-end bearing."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Moisture ingress in motor terminal box or stator winding insulation breakdown to ground.",
                    observationHint = "Insulation resistance below 5 MOhm indicates wet hygroscopic contamination or charred enamel.",
                    recommendedAction = "Open terminal box, dry moisture, inspect cable gland seals, and if reading remains low at motor pins, rewind or replace stator."
                )
            )
        ),
        // 5. PNEUMATIC
        FlowchartSeed(
            title = "Pneumatic Actuator Sluggish Stroke & Pressure Drop",
            equipmentModel = "Festo CPX-FB36 / ISO 15552 Cylinder",
            systemCategory = SystemCategory.PNEUMATIC,
            environmentType = EnvironmentPreset.MANUFACTURING.label,
            failureMode = "Pneumatic cylinder fails to complete extension stroke on time or stalls mid-travel",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "Does the FRL manifold pressure gauge maintain >= 6.0 bar dynamically while the cylinder actuates?",
                    observationHint = "Watch regulator gauge needle during valve firing; a drop > 0.8 bar indicates supply restriction.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Observe dynamic manifold pressure"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "Is air continuously hissing from the solenoid manifold exhaust muffler while the cylinder is holding?",
                    observationHint = "Listen at the bronze/sintered exhaust port on the 5/2 valve bank.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Check exhaust port for blow-by"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Saturated FRL 5-micron filter element or kinked upstream supply tubing.",
                    observationHint = "Differential pressure indicator pop-up on filter bowl is red.",
                    recommendedAction = "Depressurize FRL bowl, drain condensate water/oil emulsion, and replace the sintered filter cartridge."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Worn cylinder piston lip seal allowing pressurized air to bypass directly to the exhaust port.",
                    observationHint = "Air enters extend port, bypasses worn piston seal, and exits retract line through valve exhaust.",
                    recommendedAction = "Isolate air supply, disassemble cylinder barrel, inspect bore for scoring, and install new polyurethane piston seal kit."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Clogged exhaust muffler or misadjusted meter-out flow control needle valve on cylinder port.",
                    observationHint = "Backpressure on the exhausting chamber restricts piston velocity.",
                    recommendedAction = "Replace oil-logged sintered exhaust muffler and open cylinder meter-out speed controller 1.5 turns counter-clockwise."
                )
            )
        ),
        // 6. MECHANICAL
        FlowchartSeed(
            title = "High-Speed Spindle Bearing Vibration & Thermal Runaway",
            equipmentModel = "Haas VF-4SS / Angular Contact Spindle",
            systemCategory = SystemCategory.MECHANICAL,
            environmentType = EnvironmentPreset.MANUFACTURING.label,
            failureMode = "Audible metallic rumble above 6,000 RPM, poor surface finish chatter, and nosecone temp > 60C",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "Does spindle nose radial runout (TIR) on a certified test bar exceed 0.005 mm (0.0002 in)?",
                    observationHint = "Mount a 0.001mm dial test indicator against ground test arbor inside clean taper.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Measure spindle taper TIR"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "CONCLUSION",
                    prompt = "Brinelled angular contact bearing races or fretted CAT40/BT40 taper surface.",
                    observationHint = "Acoustic spectrum shows sharp peaks at Ball Pass Frequency Outer/Inner (BPFO/BPFI).",
                    recommendedAction = "Hone minor taper fretting or replace precision ABEC-7/P4 angular contact bearing set and recalibrate drawbar pull force."
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Air-oil mist lubricator blockage or drive coupling dynamic imbalance.",
                    observationHint = "Verify air-oil injector pulses every 6 minutes and check spider coupling elastomer insert.",
                    recommendedAction = "Prime air-oil lubrication lines, verify 0.5cc/hr oil delivery, and replace worn jaw coupling elastomer spider."
                )
            )
        )
    )

    val starterDiagnostics = listOf(
        DiagnosticSession(
            equipmentName = "Siemens SIMATIC S7-1500 PLC Cell Controller",
            equipmentModel = "6ES7515-2AM02-0AB0",
            systemCategory = SystemCategory.COMPUTER_CONTROLLER.label,
            environmentType = EnvironmentPreset.MANUFACTURING.label,
            symptomDescription = "CPU flashes red BF LED with intermittent PROFINET station #3 I/O dropout whenever weld contactor fires.",
            attachedMediaSummary = "CameraX Photo of PLC diagnostic buffer & cabinet wiring",
            rootCauseTitle = "PROFINET Shield Ground Loop & 24V Solenoid Brownout",
            severity = "HIGH",
            confidencePercent = 94L,
            technicalAnalysis = "Inductive kickback from un-suppressed 24V weld pneumatic solenoids sags the shared 24V PLC bus below 19.8V while high dV/dt weld cables induce common-mode noise on the ungrounded RJ45 shield.",
            safetyWarnings = listOf(
                "Perform Lockout/Tagout (LOTO) on 480V main cabinet disconnect before rerouting cable trays.",
                "Verify zero stored energy in weld capacitor bank before touching PE busbar."
            ),
            requiredTools = listOf(
                "True-RMS Multimeter with MIN/MAX capture",
                "PROFINET M12/RJ45 cable tester",
                "24V DC flyback suppression diodes",
                "Torque screwdriver (0.6 Nm)"
            ),
            repairSteps = listOf(
                "Capture CPU Diagnostic Buffer OB86 entries to confirm station #3 MAC address drops.",
                "Separate green PROFINET Cat6A shielded cable by at least 200mm from high-current motor/weld leads.",
                "Bond the PROFINET braided cable shield 360-degrees to the cabinet grounding clamp bar.",
                "Install flyback suppression diodes across weld solenoid coils and move coils to auxiliary 24V PSU."
            ),
            completedStepIndices = listOf(0L, 1L),
            verificationTest = "Cycle weld station 25 times under full load while monitoring PLC 24V rail (must stay >23.2V) and confirm zero CRC errors on Port X1.",
            status = "IN_PROGRESS"
        ),
        DiagnosticSession(
            equipmentName = "Dell PowerEdge R750 Industrial Edge Server",
            equipmentModel = "PER750-2X6338-NVME",
            systemCategory = SystemCategory.COMPUTER.label,
            environmentType = EnvironmentPreset.DATA_CENTER.label,
            symptomDescription = "Server reboots unexpectedly under SCADA historian query load with iDRAC WHEA Bus Fatal Error on PCIe Slot 2.",
            attachedMediaSummary = "CameraX Photo of iDRAC LCD & thermal telemetry log",
            rootCauseTitle = "PCIe Riser 1 Edge Oxidation & VRM Airflow Restriction",
            severity = "CRITICAL",
            confidencePercent = 92L,
            technicalAnalysis = "PCIe Gen4 x16 lane margining degraded due to micro-fretting on Riser 1 gold fingers compounded by elevated VRM temperatures (96C) from a clogged front bezel filter.",
            safetyWarnings = listOf(
                "Attach grounded ESD wrist strap to chassis bare metal before handling riser or DIMMs.",
                "Allow heatsink and VRM inductors to cool for 5 minutes after power-down."
            ),
            requiredTools = listOf(
                "ESD wrist strap and mat",
                "99.9% Electronics-grade Isopropyl Alcohol & lint-free swabs",
                "Torx T15 driver",
                "Compressed dry air canister"
            ),
            repairSteps = listOf(
                "Gracefully shut down OS, disconnect both redundant AC power cords, and hold power button 15s to drain flea power.",
                "Unlatch and remove PCIe Riser 1 assembly; inspect gold edge contacts under magnification.",
                "Clean Riser 1 and NVMe HBA gold contacts with 99.9% isopropyl alcohol and allow to dry completely.",
                "Vacuum and clear front bezel intake filter, reseat Riser 1 firmly until retention latch clicks, and restore power."
            ),
            completedStepIndices = listOf(0L, 1L, 2L, 3L),
            verificationTest = "Boot into iDRAC Hardware Diagnostics, verify PCIe link negotiates at 16.0 GT/s x16 width, and run 30-minute stress test with VRM temp < 78C.",
            status = "RESOLVED"
        ),
        DiagnosticSession(
            equipmentName = "Caterpillar 320D Main Hydraulic System",
            equipmentModel = "CAT-320D-SBS120",
            systemCategory = SystemCategory.HYDRAULIC.label,
            environmentType = EnvironmentPreset.HEAVY_INDUSTRIAL.label,
            symptomDescription = "High-pitched metallic rattling whine from main axial piston pump above 1800 RPM; boom lift speed reduced by 40%.",
            attachedMediaSummary = "Live Acoustic Capture (8s, Peak ~94 dB) & sight glass photo",
            rootCauseTitle = "Suction Strainer Restriction & Inlet Flange Air Ingress",
            severity = "HIGH",
            confidencePercent = 90L,
            technicalAnalysis = "Acoustic signature matches vapour cavity collapse (cavitation) at pump swashplate port plate caused by a 0.35 bar suction vacuum restriction and hardened inlet O-ring.",
            safetyWarnings = listOf(
                "Lower boom and bucket flat to ground and cycle joysticks with key ON/engine OFF to release trapped accumulator pressure.",
                "Depressurize hydraulic reservoir breather cap slowly before loosening suction flange bolts; oil temperature may exceed 65C."
            ),
            requiredTools = listOf(
                "Compound vacuum/pressure gauge (-1 to +10 bar)",
                "90-durometer Viton suction flange O-ring",
                "Ultrasonic solvent wash tank",
                "Torque wrench (45 Nm)"
            ),
            repairSteps = listOf(
                "Relieve hydraulic tank air pre-charge pressure via top breather valve.",
                "Remove tank suction strainer cover, extract 100-mesh stainless strainer, and clean out varnish/debris.",
                "Unbolt main pump suction elbow, replace flattened Viton O-ring, and torque flange bolts in star pattern to 45 Nm.",
                "Fill pump case drain port with clean ISO VG 46 oil prior to initial startup to prevent dry bearing scoring."
            ),
            completedStepIndices = listOf(0L),
            verificationTest = "Warm hydraulic oil to 50C, run engine at 2000 RPM, and verify pump inlet vacuum remains below 0.15 bar with smooth boom cycle time (< 3.8s).",
            status = "IN_PROGRESS"
        )
    )
}
