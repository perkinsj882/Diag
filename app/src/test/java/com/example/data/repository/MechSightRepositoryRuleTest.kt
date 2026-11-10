package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DiagnosticSession
import com.example.data.model.FlowchartNode
import com.example.data.model.SystemCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MechSightRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveEquipment_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = MechSightRepository(firestore)

        val eqId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createEquipment(
                name = "2021 Ford F-150 3.5L EcoBoost PCM",
                modelNumber = "F150-35EB-PCM",
                systemCategory = SystemCategory.COMPUTER_CONTROLLER.label,
                environmentType = "Automotive Service Bay",
                operatingHours = 1840L,
                notes = "High-Speed CAN-Bus & 10R80 TCM"
            ).getOrThrow()
        }
        assertTrue(eqId.isNotEmpty())

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeEquipment(uid).first { list -> list.any { it.id == eqId } }
        }
        assertTrue(observed.any { it.id == eqId && it.systemCategory == "Computer Controller" })
    }

    @Test
    fun createUpdateAndLockDiagnosticSession_enforcesTerminalStateRule() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = MechSightRepository(firestore)

        val diagId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createDiagnosticSession(
                DiagnosticSession(
                    equipmentName = "2020 Toyota Camry 2.5L VVT-iE",
                    equipmentModel = "A25A-FKS-DOHC",
                    systemCategory = SystemCategory.MECHANICAL.label,
                    environmentType = "Street & Highway Commute",
                    symptomDescription = "Cold-start VVT cam phaser rattle and P0016 correlation code",
                    attachedMediaSummary = "CameraX photo of timing cover attached",
                    rootCauseTitle = "Worn VVT Intake Cam Phaser Locking Pin",
                    severity = "CRITICAL",
                    confidencePercent = 93L,
                    technicalAnalysis = "Un-damped camshaft phase oscillation during cold cranking.",
                    safetyWarnings = listOf("Support vehicle on jack stands and chock wheels"),
                    requiredTools = listOf("OBD-II scan tool", "Torque wrench"),
                    repairSteps = listOf("Set Cylinder 1 to TDC Compression", "Replace VVT intake cam phaser")
                )
            ).getOrThrow()
        }

        // Valid update to RESOLVED
        val resolveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateDiagnosticProgress(diagId, listOf(0L, 1L), "RESOLVED")
        }
        assertTrue(resolveResult.isSuccess)

        // Subsequent update on RESOLVED session must fail due to terminal state lock rule
        val lockedUpdateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateDiagnosticProgress(diagId, listOf(0L), "IN_PROGRESS")
        }
        assertTrue(lockedUpdateResult.isFailure)
        val fsEx = lockedUpdateResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun createAndWalkthroughFlowchart_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = MechSightRepository(firestore)

        val flowId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createFlowchart(
                title = "Hydraulic Pump Cavitation Tree",
                equipmentModel = "CAT 320D",
                systemCategory = SystemCategory.HYDRAULIC.label,
                environmentType = "Heavy Industrial",
                failureMode = "High-pitched whine and slow boom lift",
                nodes = listOf(
                    FlowchartNode(
                        nodeId = "N1",
                        nodeType = "QUESTION",
                        prompt = "Is hydraulic reservoir sight glass level within nominal range?",
                        observationHint = "Inspect tank sight gauge with cylinders retracted",
                        yesNodeId = "N2",
                        noNodeId = "N3",
                        recommendedAction = "Check oil level"
                    ),
                    FlowchartNode(
                        nodeId = "N2",
                        nodeType = "CONCLUSION",
                        prompt = "Inspect suction strainer for restriction or air ingress at pump inlet flange.",
                        observationHint = "Vacuum gauge exceeds 0.25 bar restriction",
                        recommendedAction = "Clean suction strainer and replace inlet O-ring"
                    )
                )
            ).getOrThrow()
        }

        val stepUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateFlowchartWalkthrough(
                flowchartId = flowId,
                currentNodeId = "N2",
                visitedPathSummary = "N1 (YES) -> N2",
                isResolved = true
            )
        }
        assertTrue(stepUpdate.isSuccess)

        val flowcharts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFlowcharts(uid).first { list -> list.any { it.id == flowId && it.isResolved } }
        }
        assertTrue(flowcharts.any { it.id == flowId && it.currentNodeId == "N2" })
    }

    @Test
    fun getEquipmentById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = MechSightRepository(firestore)
        val eqId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createEquipment(
                name = "Alice CNC Spindle",
                modelNumber = "HAAS-VF2",
                systemCategory = SystemCategory.MECHANICAL.label,
                environmentType = "Manufacturing Floor",
                operatingHours = 1200L,
                notes = "Spindle runout check"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = MechSightRepository(firestore)
        val bobRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getEquipmentById(eqId)
        }
        assertTrue(bobRead.isFailure)
        val fsEx = bobRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeDiagnostics_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = MechSightRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeDiagnostics(uid).first()
            }
            fail("Expected PERMISSION_DENIED when observing diagnostics while unauthenticated")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@mechsight.test"
        const val BOB_EMAIL = "bob@mechsight.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
