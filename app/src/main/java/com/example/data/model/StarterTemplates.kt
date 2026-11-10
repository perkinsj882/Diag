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
            name = "2021 Ford F-150 3.5L EcoBoost Powertrain & PCM",
            modelNumber = "F150-35EB-PCM",
            systemCategory = SystemCategory.COMPUTER_CONTROLLER,
            environmentType = EnvironmentPreset.HEAVY_TOWING_HAULING.label,
            operatingHours = 1840L,
            notes = "10R80 10-speed automatic, High-Speed CAN-Bus (500 kbps), Motorcraft 5W-30"
        ),
        EquipmentSeed(
            name = "2020 Toyota Camry 2.5L Dynamic Force Engine",
            modelNumber = "A25A-FKS-DOHC",
            systemCategory = SystemCategory.MECHANICAL,
            environmentType = EnvironmentPreset.DAILY_STREET_HIGHWAY.label,
            operatingHours = 2450L,
            notes = "D-4S Dual Injection, VVT-iE Electric Intake Cam Phaser, 0W-16 synthetic oil"
        ),
        EquipmentSeed(
            name = "2021 Chevrolet Silverado 1500 Electronic Brake Booster & ABS",
            modelNumber = "T1XX-EBCM-IBOOST",
            systemCategory = SystemCategory.HYDRAULIC,
            environmentType = EnvironmentPreset.HEAVY_TOWING_HAULING.label,
            operatingHours = 3120L,
            notes = "Electronic Brake Booster (iBooster), 4-channel ABS/ESC & Electronic Parking Brake (EPB)"
        ),
        EquipmentSeed(
            name = "2022 Ford Mustang Mach-E / F-150 EPAS Electric Steering Rack",
            modelNumber = "EPAS-PSCM-RACK",
            systemCategory = SystemCategory.PNEUMATIC,
            environmentType = EnvironmentPreset.DAILY_STREET_HIGHWAY.label,
            operatingHours = 1490L,
            notes = "Belt-driven Electric Power Assisted Steering (EPAS) rack & pinion, torque/angle sensors & struts"
        ),
        EquipmentSeed(
            name = "2022 Tesla Model 3 Dual Motor LV/HV Electrical",
            modelNumber = "M3-PCS-16V-LFP",
            systemCategory = SystemCategory.ELECTRICAL,
            environmentType = EnvironmentPreset.EV_HYBRID_HIGH_VOLTAGE.label,
            operatingHours = 1620L,
            notes = "355V HV pack, Power Conversion System (PCS) DC-DC converter, 16V Li-ion auxiliary rail"
        ),
        EquipmentSeed(
            name = "2021 Honda Civic 1.5T VTEC Turbo & CVT Drivetrain",
            modelNumber = "L15B7-CVT-G2",
            systemCategory = SystemCategory.MECHANICAL,
            environmentType = EnvironmentPreset.TRACK_PERFORMANCE.label,
            operatingHours = 1980L,
            notes = "TD03 single-scroll turbocharger, electronic wastegate actuator, HCF-2 CVT fluid"
        )
    )

    val starterFlowcharts = listOf(
        // 1. COMPUTER CONTROLLER (ECM / PCM / CAN-Bus / OBD-II)
        FlowchartSeed(
            title = "OBD-II U0100 No-Comm & 5V Sensor Reference Bus Drop",
            equipmentModel = "2021 Ford F-150 3.5L / GM Global-A CAN",
            systemCategory = SystemCategory.COMPUTER_CONTROLLER,
            environmentType = EnvironmentPreset.AUTOMOTIVE_SERVICE_BAY.label,
            failureMode = "No crank / no start, cooling fan runs high speed, scan tool shows U0100 Lost Comm with ECM/PCM",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "With key OFF and battery negative disconnected, is resistance between OBD-II DLC Pin 6 (CAN-H) and Pin 14 (CAN-L) between 58 and 62 Ohms?",
                    observationHint = "Probe DLC Pin 6 and Pin 14 with a digital multimeter in Ohms mode (two 120-Ohm terminating resistors in parallel = 60 Ohms).",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Measure High-Speed CAN termination resistance"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "With key ON Engine OFF (KOEO), does the PCM 5V Reference 1 rail (VREF1 at MAP/FRP sensor pin) measure between 4.85V and 5.10V DC?",
                    observationHint = "Back-probe the 3-wire Fuel Rail Pressure (FRP) or MAP sensor 5V reference wire relative to battery negative.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Check PCM 5V sensor reference bus"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Open or shorted High-Speed CAN-Bus backbone or failed 120-Ohm terminating module.",
                    observationHint = "Reading ~120 Ohms indicates an open termination leg; < 5 Ohms indicates CAN-H shorted to CAN-L.",
                    recommendedAction = "Inspect rear frame/underbody harness near spare tire or wheel well for chafed twisted-pair yellow/green CAN leads and repair with heat-shrink crimp splices."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "QUESTION",
                    prompt = "Is battery voltage (12.2V - 14.4V) present at all PCM Power Relay output pins and is voltage drop on PCM Ground G104 < 0.08V?",
                    observationHint = "Load-test PCM power and chassis ground pins at connector C175B using a 21W test bulb.",
                    yesNodeId = "N6",
                    noNodeId = "N7",
                    recommendedAction = "Load-test PCM B+ feeds and G104 ground"
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Shorted 3-wire pressure transducer (FRP / AC Pressure / Exhaust Backpressure) dragging down the internal PCM 5V VREF regulator.",
                    observationHint = "When a 5V sensor shorts internally to ground, the PCM shuts down its internal microprocessor and drops off the CAN bus.",
                    recommendedAction = "Unplug 5V sensors one at a time (starting with Fuel Rail Pressure and A/C High Side transducer) until 5.0V returns and PCM communication restores; replace shorted sensor."
                ),
                FlowchartNode(
                    nodeId = "N6",
                    nodeType = "CONCLUSION",
                    prompt = "Internal PCM CAN transceiver failure or corrupted EEPROM flash.",
                    observationHint = "Power, grounds, 5V reference, and 60-Ohm CAN backbone all pass load testing while PCM remains mute.",
                    recommendedAction = "Attempt J2534 pass-thru PMI / module reprogramming; if module fails to respond to bootloader wake-up, replace and program PCM."
                ),
                FlowchartNode(
                    nodeId = "N7",
                    nodeType = "CONCLUSION",
                    prompt = "Corroded Underhood Battery Junction Box (BJB) PCM relay socket or oxidized fender ground eyelet G104.",
                    observationHint = "High resistance drops supply below 9.5V as soon as the PCM energizes injector drivers.",
                    recommendedAction = "Swap PCM Power Relay with identical horn/blower relay, clean G104 chassis ground stud to bare metal, apply dielectric grease, and torque to 10 Nm."
                )
            )
        ),
        // 2. MECHANICAL (Engine Valvetrain / Timing Chain / VVT)
        FlowchartSeed(
            title = "Cold-Start VVT Cam Phaser Rattle & P0016 Correlation Tree",
            equipmentModel = "Ford 3.5L EcoBoost / Toyota 2.5L A25A-FKS",
            systemCategory = SystemCategory.MECHANICAL,
            environmentType = EnvironmentPreset.DAILY_STREET_HIGHWAY.label,
            failureMode = "Loud metallic clatter for 2-4 seconds on cold start, rough idle, and DTC P0016 / P0011 Cam-Crank correlation",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "Does hot engine idle oil pressure at the cylinder head gallery port equal or exceed 18 PSI (1.25 bar) at 750 RPM?",
                    observationHint = "Thread a mechanical 0-100 PSI oil pressure gauge into the oil pressure switch port with oil at 90C.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Verify base engine oil pressure"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "When commanding VVT Oil Control Solenoid (OCV) duty cycle from 0% to 80% via OBD-II bi-directional test, does actual cam angle track within 2 degrees?",
                    observationHint = "Graph VVT Desired vs. Actual Cam Phaser Angle PIDs at 1,500 RPM.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Run OBD-II active VVT phaser test"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Sludged oil pickup screen, worn oil pump rotor, or sheared timing chain tensioner gasket bleeding pressure.",
                    observationHint = "Insufficient base oil pressure starves the upper cam phasers and hydraulic chain tensioner.",
                    recommendedAction = "Drop oil pan to inspect pickup tube O-ring and mesh screen for debris, and inspect hydraulic timing tensioner backing plate seal."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Worn VVT Cam Phaser internal locking pin & stretched primary timing chain exceeding tensioner travel.",
                    observationHint = "Locking pin fails to engage at shutdown, allowing unpressurized phaser vanes to slam on cold cranking.",
                    recommendedAction = "Lock camshafts at TDC Cylinder #1, replace intake/exhaust VVT cam phasers, primary timing chain, guides, and ratcheting hydraulic tensioner; torque phaser bolts to OEM yield spec."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Sticking VVT Oil Control Valve (OCV) spool or clogged VVT filter mesh screen.",
                    observationHint = "Varnish or metallic debris blocks oil delivery to the advance/retard phaser chambers.",
                    recommendedAction = "Remove VVT solenoid, inspect and clean the cylinder head OCV mesh filter screen, test solenoid coil resistance (6.8 - 7.8 Ohms at 20C), and replace sticking OCV."
                )
            )
        ),
        // 3. BRAKES (Electronic Brake Booster / ABS / ESC / Disc Brakes)
        FlowchartSeed(
            title = "Electronic Brake Booster (iBooster), ABS & Spongy Pedal Tree",
            equipmentModel = "Chevy Silverado T1XX / Bosch iBooster & 4-Channel ABS",
            systemCategory = SystemCategory.HYDRAULIC,
            environmentType = EnvironmentPreset.HEAVY_TOWING_HAULING.label,
            failureMode = "Service Brake Assist warning, hard or spongy brake pedal feel, or ABS/ESC fault",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "Is brake fluid level in the reservoir below MIN or is visible fluid present at calipers/hoses, OR are Electronic Brake Booster (EBB/iBooster) DTCs stored?",
                    observationHint = "Scan EBCM/iBooster for pedal travel sensor or motor supply codes and inspect calipers/lines.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Scan Electronic Brake Booster & inspect for brake fluid leaks"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "CONCLUSION",
                    prompt = "Electronic Brake Booster 40A supply/pedal sensor fault OR external caliper/brake hose leak.",
                    observationHint = "Loss of 12V supply to the electromechanical booster motor disables power assist and triggers a hard pedal warning.",
                    recommendedAction = "Load-test 40A Electronic Brake Booster fuse/feed and 5V pedal travel sensor; if leaking fluid, replace caliper/hose and run OBD-II Automated Brake Bleed."
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "QUESTION",
                    prompt = "With both master cylinder outlet ports plugged using brass test plugs, does the brake pedal still sink under steady 50-lb foot force?",
                    observationHint = "Isolate the master cylinder from the ABS/ESC modulator and caliper circuits.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Perform master cylinder port isolation test"
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Master Cylinder primary/secondary piston cup seal internal bypass.",
                    observationHint = "Fluid slips past worn EPDM cup seals back into the reservoir without external leakage.",
                    recommendedAction = "Bench-bleed and install a new Brake Master Cylinder, torque mounting nuts to 25 Nm, and run Electronic Brake Booster pedal travel calibration."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Trapped air in ABS/ESC Modulator secondary circuit or sticking ABS dump valve solenoid.",
                    observationHint = "Master cylinder holds rock-solid when plugged; compliance is downstream in the EBCM valve block.",
                    recommendedAction = "Connect pressure bleeder at 20 PSI and execute the Automated ABS/iBooster Bleed procedure via OBD-II bi-directional scan tool."
                )
            )
        ),
        // 4. ELECTRICAL (Starting / Charging / Parasitic Drain / EV LV)
        FlowchartSeed(
            title = "Overnight Parasitic Battery Drain & Charging Ripple Isolation",
            equipmentModel = "12V AGM / 16V EV Auxiliary & Alternator System",
            systemCategory = SystemCategory.ELECTRICAL,
            environmentType = EnvironmentPreset.EXTREME_COLD_WINTER.label,
            failureMode = "Battery drops below 10.5V overnight causing slow crank or clicking starter relay; charging voltage fluctuates",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "With engine idling at 2,000 RPM and headlamps/blower ON, is charging voltage at battery posts between 13.8V and 14.6V DC with < 50mV AC ripple?",
                    observationHint = "Measure DC volts and AC ripple across battery terminals with a true-RMS automotive multimeter.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Test alternator output & diode AC ripple"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "After latching all doors/hood and waiting 30 minutes for CAN bus sleep, does parasitic current draw exceed 50 mA?",
                    observationHint = "Measure in-series current at negative battery cable or use millivolt voltage-drop across fuse tops.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Measure sleep-mode parasitic draw"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Shorted alternator rectifier diode bridge or high resistance in B+ charge cable / chassis ground strap.",
                    observationHint = "AC ripple > 100mV AC indicates a leaky alternator diode that both undercharges and drains the battery key-off.",
                    recommendedAction = "Perform voltage-drop test on alternator B+ to battery positive (< 0.2V max); if wiring is clean, replace alternator assembly."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Body Control Module (BCM) / Infotainment Telematics node staying awake or welded accessory relay.",
                    observationHint = "Millivolt drop across fuse reveals 0.4mV - 2.5mV on active circuit after sleep timer expires.",
                    recommendedAction = "Use fuse voltage-drop chart to pinpoint active fuse without breaking circuit power, inspect door ajar switches/aftermarket OBD dongles, and update BCM firmware."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Sulfated 12V AGM battery cell with degraded Reserve Capacity (RC) and high internal resistance.",
                    observationHint = "Alternator charging and sleep draw (< 35 mA) are nominal, but battery fails conductance/load test.",
                    recommendedAction = "Replace 12V AGM battery with OEM-rated CCA unit and perform Battery Management System (BMS) reset via OBD-II scan tool."
                )
            )
        ),
        // 5. STEERING & SUSPENSION (EPAS Electric Rack & Pinion, Wheel Bearings & Suspension)
        FlowchartSeed(
            title = "Electric Rack & Pinion (EPAS) Steering Assist Fault & Suspension Tree",
            equipmentModel = "Ford F-150 / Mustang / GM Belt-Drive EPAS Steering Rack",
            systemCategory = SystemCategory.PNEUMATIC,
            environmentType = EnvironmentPreset.DAILY_STREET_HIGHWAY.label,
            failureMode = "'Steering Assist Fault - Service Required' message, heavy steering effort, or front suspension clunk",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "With engine running, is 12.2V–14.5V present at the 2-pin high-current EPAS Power Steering Control Module (PSCM) connector with < 0.15V drop under full lock-to-lock load?",
                    observationHint = "Check the 80A/100A Mega-Fuse at the battery junction box and load-test the heavy-gauge B+ and ground leads at the electric steering rack.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Load-test 80A EPAS power feed and chassis ground"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "When graphing Steering Wheel Torque Sensor PIDs via OBD-II while gently turning left/right, does torque signal transition smoothly (+/- Nm) without spikes or C102D faults?",
                    observationHint = "Monitor PSCM Steering Input Torque and Steering Angle Sensor PIDs in live data.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Graph EPAS Steering Torque & Angle Sensor PIDs"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "High resistance or blown 80A Mega-Fuse on the EPAS high-current battery supply or corroded frame ground.",
                    observationHint = "EPAS electric rack motors draw 60A+ during parking maneuvers; voltage drop below 10.5V causes immediate PSCM shutdown.",
                    recommendedAction = "Replace oxidized 80A fusible link or clean the front frame ground lug to bare metal, torque to 25 Nm, and clear PSCM low-voltage DTCs."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Water ingress into EPAS rack belt housing via torn inner tie-rod bellows boot or seized lower steering shaft U-joint.",
                    observationHint = "Disconnect outer tie rods from steering knuckles to isolate binding steering column U-joint vs. internal rack ball-nut drag.",
                    recommendedAction = "Lubricate or replace binding lower intermediate steering shaft U-joint; if internal EPAS rack belt/ball-nut is corroded, replace Electric Steering Rack assembly and perform 4-wheel alignment."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Failed internal EPAS Steering Torque Sensor or PSCM inverter bridge on the electric rack & pinion.",
                    observationHint = "Erratic torque sensor voltage causes the PSCM to disable electric motor assist for safety.",
                    recommendedAction = "Replace EPAS Electric Rack & Pinion assembly, torque rack mounting bolts to 145 Nm, program PSCM As-Built data via J2534/OBD-II, and run Steering Angle Sensor Zero-Point Calibration."
                )
            )
        ),
        // 6. MECHANICAL / PNEUMATIC (Turbocharger Boost & Wastegate)
        FlowchartSeed(
            title = "Turbocharger Underboost (P0299) & Charge-Air Leak Tree",
            equipmentModel = "Honda 1.5T L15B7 / VW EA888 2.0T / EcoBoost",
            systemCategory = SystemCategory.MECHANICAL,
            environmentType = EnvironmentPreset.TRACK_PERFORMANCE.label,
            failureMode = "Check Engine Light with P0299 Turbo Underboost, sluggish acceleration above 3,000 RPM, and whooshing air noise",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "When pressurizing the intake tract to 15 PSI with a regulated smoke/boost leak tester, does pressure hold steady with zero smoke escaping?",
                    observationHint = "Inspect charge-air cooler (intercooler) end tanks, throttle body coupler, and diverter/bypass valve diaphragm.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Smoke-test charge-air boost tract"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "CONCLUSION",
                    prompt = "Worn Electronic Wastegate Actuator linkage or binding turbine wastegate flapper pivot bushing.",
                    observationHint = "Wastegate fails to seal fully against the turbine housing seat, bleeding exhaust drive energy before boost builds.",
                    recommendedAction = "Perform OBD-II Wastegate Actuator Sweep Test, inspect clevis pin for oval wear, replace actuator or turbo manifold assembly, and calibrate wastegate closed-stop voltage."
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Split intercooler silicone charge pipe boot or ruptured turbo diverter valve diaphragm.",
                    observationHint = "Pressurized boost air escapes between the turbo compressor outlet and intake manifold MAP sensor.",
                    recommendedAction = "Replace split charge-air hose or torn diverter valve, clean oil film from mating beads, torque constant-tension T-bolt clamps to 7 Nm, and clear P0299."
                )
            )
        ),
        // 7. DRIVETRAIN (Automatic Transmission, Torque Converter, Transfer Case & Differentials)
        FlowchartSeed(
            title = "Drivetrain: Transmission Shift Flare, TCC Shudder (P0741) & AWD Transfer Case Tree",
            equipmentModel = "2021 Ford F-150 10R80 / GM 8L90 4WD Drivetrain",
            systemCategory = SystemCategory.MECHANICAL,
            environmentType = EnvironmentPreset.HEAVY_TOWING_HAULING.label,
            failureMode = "Torque Converter Clutch (TCC) shudder at 45–60 MPH (P0741), harsh 2-3/3-4 upshift flare, or AWD/4WD transfer case binding",
            nodes = listOf(
                FlowchartNode(
                    nodeId = "N1",
                    nodeType = "QUESTION",
                    prompt = "At operating ATF temperature (85°C–95°C via OBD-II TCM PID), is transmission fluid level clean and within the cross-hatched spec without burnt friction odor?",
                    observationHint = "Monitor TFT (Transmission Fluid Temp) PID and inspect ATF sample for dark clutch material or glycol contamination.",
                    yesNodeId = "N2",
                    noNodeId = "N3",
                    recommendedAction = "Verify ATF thermal level and fluid condition"
                ),
                FlowchartNode(
                    nodeId = "N2",
                    nodeType = "QUESTION",
                    prompt = "When graphing Torque Converter Clutch (TCC) Slip RPM at 50 MPH light load, does slip exceed 40 RPM with rhythmic shudder that vanishes when commanding TCC OFF via scan tool?",
                    observationHint = "Graph Turbine Speed vs. Engine RPM and TCC Duty Cycle % on a slight highway grade.",
                    yesNodeId = "N4",
                    noNodeId = "N5",
                    recommendedAction = "Graph TCC Slip RPM and command TCC solenoid lockup test"
                ),
                FlowchartNode(
                    nodeId = "N3",
                    nodeType = "CONCLUSION",
                    prompt = "Low / aerated Automatic Transmission Fluid (ATF) from cooler line leak or degraded friction modifier.",
                    observationHint = "Low sump level starves the main pump suction port under acceleration, causing shift flare and delayed Drive engagement.",
                    recommendedAction = "Repair leaking transmission cooler fitting, drop pan, replace filter & pan gasket (torque bolts to 12 Nm), refill with OEM ULV/DEXRON-VI fluid at 90°C, and reset TCM adaptive tables."
                ),
                FlowchartNode(
                    nodeId = "N4",
                    nodeType = "CONCLUSION",
                    prompt = "Glazed Torque Converter Clutch (TCC) lockup lining or sticking TCC PWM pressure control solenoid in the valve body.",
                    observationHint = "TCC slip oscillates 45–120 RPM under steady cruise (sets P0741 TCC Performance/Stuck Off).",
                    recommendedAction = "Perform full ultra-low-viscosity ATF exchange, test TCC PWM solenoid resistance (5.0–5.6 Ω), and if shudder persists, replace torque converter and flush transmission cooler."
                ),
                FlowchartNode(
                    nodeId = "N5",
                    nodeType = "CONCLUSION",
                    prompt = "Worn CDF clutch drum bushing / valve body solenoid strategy drift or binding 4WD/AWD transfer case clutch pack.",
                    observationHint = "TCC lockup is steady, but shift flare occurs on 3-4 upshift or driveline binds during low-speed turns.",
                    recommendedAction = "Update TCM calibration to latest OEM firmware, run OBD-II Transmission Clutch Adaptive Relearn drive cycle, and service transfer case fluid."
                )
            )
        )
    )

    val starterDiagnostics = listOf(
        DiagnosticSession(
            equipmentName = "2021 Ford F-150 3.5L EcoBoost Powertrain & PCM",
            equipmentModel = "F150-35EB-PCM",
            systemCategory = SystemCategory.COMPUTER_CONTROLLER.label,
            environmentType = EnvironmentPreset.HEAVY_TOWING_HAULING.label,
            symptomDescription = "Truck enters wrench limp-home mode while towing up grade; OBD-II scan shows U0100 intermittent PCM dropout and P06A6 5V Reference A Circuit Range/Performance.",
            attachedMediaSummary = "CameraX Photo of OBD-II freeze-frame &FRP harness connector",
            rootCauseTitle = "Shorted Fuel Rail Pressure Sensor Dragging Down PCM 5V VREF1 Bus",
            severity = "CRITICAL",
            confidencePercent = 95L,
            technicalAnalysis = "Thermal expansion of the high-pressure Fuel Rail Pressure (FRP) piezoelectric diaphragm shorts the 5V Reference 1 bus to ground at >95C engine bay temps, causing the PCM microprocessor supervisor to brown out and drop High-Speed CAN communication.",
            safetyWarnings = listOf(
                "Relieve high-pressure direct-injection fuel rail pressure via scan tool and allow engine to cool below 50C before loosening fuel rail fittings.",
                "Disconnect negative 12V battery terminal before unplugging main PCM C175B bulkhead harness."
            ),
            requiredTools = listOf(
                "Bi-Directional OBD-II Scan Tool with CAN-Bus Topology View",
                "True-RMS Automotive Digital Multimeter & Back-Probe Pins",
                "OEM Fuel Rail Pressure Sensor (Motorcraft CM-5258)",
                "3/8-inch Torque Wrench (24 Nm)"
            ),
            repairSteps = listOf(
                "Connect multimeter to VREF1 wire (Grey/Violet) at the Fuel Rail Pressure (FRP) sensor with ignition ON; confirm voltage is pulled down below 2.1V.",
                "Unplug the 3-pin FRP sensor connector and verify VREF1 immediately recovers to 5.00V DC and PCM CAN communication restores.",
                "Depressurize fuel system, remove faulted FRP sensor, and install new OEM FRP sensor torqued to 24 Nm.",
                "Inspect harness loom behind Bank 1 cylinder head for chafing, clear KAM & U0100/P06A6 DTCs, and verify 5.0V stability."
            ),
            completedStepIndices = listOf(0L, 1L),
            verificationTest = "Road-test under boost load for 20 minutes while graphing VREF1 (must stay 4.95V-5.05V) and confirm zero CAN bus error frames.",
            status = "IN_PROGRESS",
            repairDurationMinutes = 45,
            mechanicVoiceLogs = listOf(
                MechanicVoiceObservation(
                    id = "vn_f150_1",
                    timestampLabel = "09:14:22",
                    stepTag = "STEP 1",
                    categoryTag = "MEASUREMENT",
                    transcript = "Back-probed Grey/Violet VREF1 wire at Fuel Rail Pressure sensor with ignition ON; multimeter reads 1.84V DC instead of nominal 5.00V."
                ).toEncodedString(),
                MechanicVoiceObservation(
                    id = "vn_f150_2",
                    timestampLabel = "09:18:45",
                    stepTag = "STEP 2",
                    categoryTag = "OBD-II / TEST",
                    transcript = "Unplugged 3-pin FRP transducer harness; 5.01V reference restored immediately and PCM U0100 bus fault cleared."
                ).toEncodedString()
            ),
            vinNumber = "1FTFW1E85MFA10492",
            odometerMileage = 58_900L
        ),
        DiagnosticSession(
            equipmentName = "2020 Toyota Camry 2.5L Dynamic Force Engine",
            equipmentModel = "A25A-FKS-DOHC",
            systemCategory = SystemCategory.MECHANICAL.label,
            environmentType = EnvironmentPreset.DAILY_STREET_HIGHWAY.label,
            symptomDescription = "Loud metallic diesel-like rattle from front timing cover for 3 seconds on cold morning start accompanied by DTC P0016 Crankshaft-Camshaft Position Correlation Bank 1.",
            attachedMediaSummary = "Live Acoustic Capture (8s cold start, Peak ~91 dB) & timing cover photo",
            rootCauseTitle = "Worn VVT Intake Cam Phaser Locking Pin & Hydraulic Tensioner Leakdown",
            severity = "HIGH",
            confidencePercent = 93L,
            technicalAnalysis = "Overnight oil drainback from the primary hydraulic timing chain tensioner combined with a worn VVT cam phaser internal locking pin allows 6.5 degrees of un-damped camshaft phase oscillation during initial cranking before oil gallery pressure reaches 22 PSI.",
            safetyWarnings = listOf(
                "Support vehicle securely on certified jack stands and chock rear wheels before removing right-front wheel and engine mount.",
                "Never rotate crankshaft counter-clockwise when aligning timing chain colored links."
            ),
            requiredTools = listOf(
                "OBD-II Scan Tool with Active VVT Phaser Control",
                "Camshaft Holding Wrench & Crankshaft Pulley Holder",
                "OEM VVT Cam Phaser Sprocket & Ratcheting Timing Tensioner Kit",
                "Digital Torque Angle Gauge"
            ),
            repairSteps = listOf(
                "Verify hot idle oil pressure (>= 18 PSI at 750 RPM) and perform bi-directional VVT Oil Control Valve step test to rule out sludge blockage.",
                "Set Cylinder #1 to TDC Compression, support engine block, remove passenger side engine mount and front aluminum timing cover.",
                "Compress and pin timing tensioner, remove stretched primary chain, and replace Intake VVT Cam Phaser torqued to 85 Nm.",
                "Install new primary timing chain aligning painted links with cam/crank timing marks, release new ratcheting tensioner, and reseal cover with FIPG RTV."
            ),
            completedStepIndices = listOf(0L, 1L, 2L, 3L),
            verificationTest = "After overnight cold soak, start engine while recording acoustic waveform and graphing VVT Target vs. Actual angle (deviation < 1.0 deg, zero rattle).",
            status = "RESOLVED",
            repairDurationMinutes = 115,
            mechanicVoiceLogs = listOf(
                MechanicVoiceObservation(
                    id = "vn_camry_1",
                    timestampLabel = "08:05:10",
                    stepTag = "STEP 1",
                    categoryTag = "MEASUREMENT",
                    transcript = "Hot idle oil pressure verified at 24 PSI at 750 RPM; VVT oil control solenoid resistance measured 7.2 Ohms at 20C."
                ).toEncodedString(),
                MechanicVoiceObservation(
                    id = "vn_camry_2",
                    timestampLabel = "09:42:30",
                    stepTag = "STEP 3",
                    categoryTag = "PART / WEAR",
                    transcript = "Old intake VVT cam phaser locking pin bore shows 1.5mm oval wear step; new OEM phaser torqued to 85 Nm."
                ).toEncodedString()
            ),
            vinNumber = "4T1C11AK5LU892341",
            odometerMileage = 99_400L
        ),
        DiagnosticSession(
            equipmentName = "2021 Chevrolet Silverado 1500 Electronic Brake Booster & ABS",
            equipmentModel = "T1XX-EBCM-IBOOST",
            systemCategory = SystemCategory.HYDRAULIC.label,
            environmentType = EnvironmentPreset.HEAVY_TOWING_HAULING.label,
            symptomDescription = "Service Brake System warning with spongy brake pedal under trailer load; Electronic Brake Booster (iBooster) and master cylinder pressure correlation fault.",
            attachedMediaSummary = "Hands-free voice narration & Electronic Brake Booster reservoir photo",
            rootCauseTitle = "Master Cylinder Primary Seal Internal Bypass & Electronic Brake Booster Calibration",
            severity = "CRITICAL",
            confidencePercent = 94L,
            technicalAnalysis = "High brake fluid thermal cycling during heavy towing degraded the EPDM primary pressure seal inside the master cylinder coupled to the Electronic Brake Booster, causing pedal travel vs. hydraulic pressure plausibility drift.",
            safetyWarnings = listOf(
                "Do NOT drive or tow with the vehicle until full brake pedal firmness and electronic booster assist are verified.",
                "Deactivate Electronic Brake Booster / EPB into Service Mode via OBD-II scan tool before removing calipers or master cylinder."
            ),
            requiredTools = listOf(
                "Master Cylinder Brass Port Block-Off Plug Kit (M12x1.0 / M14x1.5)",
                "Diaphragm Pressure Brake Bleeder (20 PSI) & DOT 4 LV Brake Fluid",
                "OBD-II Scan Tool with Electronic Brake Booster & Automated ABS Bleed",
                "Flare-Nut Wrench Set (13mm / 14mm) & Torque Wrench"
            ),
            repairSteps = listOf(
                "Perform Master Cylinder Isolation Test using brass port plugs and verify Electronic Brake Booster pedal travel sensor 5V reference.",
                "Bench-bleed replacement OEM Brake Master Cylinder until zero micro-bubbles exit either outlet port.",
                "Bolt new Master Cylinder to Electronic Brake Booster assembly (torque nuts to 25 Nm) and torque brake line flare nuts to 22 Nm.",
                "Attach 20 PSI pressure bleeder, run OBD-II Automated Electronic Brake Booster / ABS Bleed, and run Pedal Travel Sensor Calibration."
            ),
            completedStepIndices = listOf(0L),
            verificationTest = "Apply 65 lbs steady foot force to brake pedal for 2 full minutes with ignition ON; confirm firm pedal height and matching iBooster pressure PIDs.",
            status = "IN_PROGRESS",
            repairDurationMinutes = 65,
            mechanicVoiceLogs = listOf(
                MechanicVoiceObservation(
                    id = "vn_silv_1",
                    timestampLabel = "10:11:04",
                    stepTag = "STEP 1",
                    categoryTag = "MEASUREMENT",
                    transcript = "Installed M14 brass block-off plugs in master cylinder ports; pedal sinks 45mm in 15 seconds while Electronic Brake Booster travel sensor reads 38% stroke."
                ).toEncodedString()
            ),
            vinNumber = "1GCUYDED9KZ219483",
            odometerMileage = 89_600L
        ),
        DiagnosticSession(
            equipmentName = "2022 Ford Mustang Mach-E / F-150 EPAS Electric Steering Rack",
            equipmentModel = "EPAS-PSCM-RACK",
            systemCategory = SystemCategory.PNEUMATIC.label,
            environmentType = EnvironmentPreset.DAILY_STREET_HIGHWAY.label,
            symptomDescription = "Intermittent 'Steering Assist Fault - Service Required' message with heavy steering effort during low-speed parking maneuvers and DTC C102D Steering Torque Sensor fault.",
            attachedMediaSummary = "CameraX Photo of EPAS Electric Steering Rack connector & torn inner tie-rod boot",
            rootCauseTitle = "EPAS Electric Rack & Pinion Torque Sensor Fault & Moisture Ingress via Torn Tie-Rod Boot",
            severity = "HIGH",
            confidencePercent = 93L,
            technicalAnalysis = "A split driver-side inner tie-rod bellows boot allowed road water to enter the Electric Power Assisted Steering (EPAS) rack housing, corroding the belt-drive ball nut and causing the Hall-effect steering torque sensor signal to drop out under load.",
            safetyWarnings = listOf(
                "Support front subframe securely on jack stands and lock steering wheel centered with a seat-belt/wheel holder before disconnecting the intermediate steering shaft so the clockspring cannot over-rotate.",
                "Disconnect 12V battery negative cable before unplugging the 80A high-current PSCM harness on the electric steering rack."
            ),
            requiredTools = listOf(
                "Bi-Directional OBD-II / J2534 Scan Tool (PSCM Programming & Angle Reset)",
                "OEM Electric Power Assisted Steering (EPAS) Rack & Pinion Assembly",
                "Outer Tie-Rod End Ball Joint Separator & Steering Wheel Holder",
                "1/2-inch Torque Wrench (145 Nm / 107 lb-ft)"
            ),
            repairSteps = listOf(
                "Verify 12.6V+ at the 2-pin 80A EPAS power connector and confirm C102D torque sensor implausibility in PSCM live data.",
                "Lock steering wheel at 0 degrees center, mark intermediate shaft pinch-bolt spline, and separate outer tie-rod ends from steering knuckles.",
                "Unbolt and remove corroded EPAS Electric Steering Rack, install new EPAS Rack & Pinion, and torque subframe mounting bolts to 145 Nm (107 lb-ft).",
                "Program PSCM module configuration via OBD-II, run Steering Angle & Torque Sensor Zero-Point Calibration, and set front toe alignment."
            ),
            completedStepIndices = listOf(0L, 1L, 2L, 3L),
            verificationTest = "Perform lock-to-lock steering sweep while graphing PSCM Motor Current (0–65A smooth curve) and Steering Angle (0.0 deg straight-ahead) on road test.",
            status = "RESOLVED",
            repairDurationMinutes = 95,
            vinNumber = "1FTFW1E80NFA48210",
            odometerMileage = 44_200L
        ),
        DiagnosticSession(
            equipmentName = "2022 Tesla Model 3 Dual Motor LV/HV Electrical",
            equipmentModel = "M3-PCS-16V-LFP",
            systemCategory = SystemCategory.ELECTRICAL.label,
            environmentType = EnvironmentPreset.EV_HYBRID_HIGH_VOLTAGE.label,
            symptomDescription = "Alert VCFRONT_a447 'Low Voltage Electrical System Power Reduced' appears intermittently over rough pavement; 16V LV bus dips to 12.6V.",
            attachedMediaSummary = "Multimeter voltage-drop log & front chassis ground stud photo",
            rootCauseTitle = "High Voltage-Drop on Front Left Chassis Ground Strap (G102) & LV Terminal Oxidation",
            severity = "HIGH",
            confidencePercent = 95L,
            technicalAnalysis = "Galvanic oxidation between the aluminum shock tower ground stud and the tinned copper Power Conversion System (PCS) LV return lug created 0.85 Ohms of contact resistance, dropping 16V rail voltage by 2.4V during EPAS power steering current peaks.",
            safetyWarnings = listOf(
                "Power down vehicle via Service Mode, disconnect 16V LV auxiliary connector, and pull High-Voltage First Responder Loop before unbolting PCS ground.",
                "Verify 0V DC across HV test points using a CAT III 1000V rated multimeter."
            ),
            requiredTools = listOf(
                "CAT III 1000V Digital Multimeter & 4-Wire Milliohmmeter",
                "Aluminum-Compatible Conductive Anti-Oxidant Joint Compound",
                "Brass Wire Brush & 99% Isopropyl Alcohol",
                "Insulated Torque Wrench (12 Nm)"
            ),
            repairSteps = listOf(
                "Measure dynamic voltage drop between PCS LV negative housing and front chassis ground stud G102 while turning steering wheel lock-to-lock (confirmed > 1.8V drop).",
                "Perform HV + LV power-down sequence and verify zero voltage.",
                "Unbolt G102 ground lug, clean white aluminum oxide film from stud and eyelet, and apply conductive anti-oxidant paste.",
                "Torque G102 ground nut to 12 Nm, reconnect LV + HV loop, and clear VCFRONT alerts."
            ),
            completedStepIndices = listOf(0L, 1L, 2L, 3L),
            verificationTest = "Command full EPAS steering and HVAC load while monitoring PCS LV output; confirm voltage stays between 15.3V and 15.6V with < 0.04V ground drop.",
            status = "RESOLVED",
            repairDurationMinutes = 38,
            vinNumber = "5YJ3E1EB7NF184920",
            odometerMileage = 31_500L
        ),
        DiagnosticSession(
            equipmentName = "2021 Honda Civic 1.5T VTEC Turbo & CVT Drivetrain",
            equipmentModel = "L15B7-CVT-G2",
            systemCategory = SystemCategory.MECHANICAL.label,
            environmentType = EnvironmentPreset.TRACK_PERFORMANCE.label,
            symptomDescription = "Check Engine Light with P0299 Turbocharger Underboost, peak boost capped at 8.5 PSI (target 16.5 PSI), and audible hiss under wide-open throttle.",
            attachedMediaSummary = "CameraX Photo of split lower intercooler silicone charge boot",
            rootCauseTitle = "Split Cold-Side Intercooler Charge Pipe Hose & Loose T-Bolt Clamp",
            severity = "MODERATE",
            confidencePercent = 96L,
            technicalAnalysis = "Oil vapor permeation and high turbo heat soak softened the lower cold-side silicone charge-air cooler coupler, causing a 28mm longitudinal split along the clamp bead that vents boost pressure above 8 PSI.",
            safetyWarnings = listOf(
                "Allow turbocharger turbine housing and exhaust manifold heat shield to cool below 45C before reaching into lower engine bay.",
                "Support vehicle on jack stands before removing lower aluminum aerodynamic splash shield."
            ),
            requiredTools = listOf(
                "Automotive Regulated Intake Smoke & Boost Leak Tester (15 PSI)",
                "Reinforced 4-Ply Aramid/Fluorosilicone Cold-Side Charge Hose",
                "Constant-Tension Stainless T-Bolt Clamps",
                "1/4-inch Torque Wrench (7 Nm)"
            ),
            repairSteps = listOf(
                "Adapter-plug intake at turbo inlet and pressurize charge tract to 15 PSI with smoke tester to pinpoint leak at lower cold-side intercooler outlet.",
                "Remove lower engine splash shield, loosen worm-gear clamps, and remove oil-soaked split rubber charge boot.",
                "Degrease intercooler outlet bead and throttle body inlet pipe with brake cleaner so hoses cannot slip off under boost.",
                "Install reinforced fluorosilicone charge pipe with constant-tension clamps torqued to 7 Nm and pressure-test to 18 PSI."
            ),
            completedStepIndices = listOf(0L, 1L, 2L, 3L),
            verificationTest = "Clear DTC P0299 and perform 3rd-gear WOT pull from 2,500 to 5,500 RPM; confirm MAP sensor reaches 16.4 PSI target boost with zero fuel trim divergence.",
            status = "RESOLVED",
            repairDurationMinutes = 50,
            vinNumber = "19XFC1F35ME019284",
            odometerMileage = 49_800L
        )
    )
}
