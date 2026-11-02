const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validEquipmentPayload(userId) {
  const now = new Date();
  return {
    userId,
    name: "Siemens S7-1500 PLC Rack",
    modelNumber: "6ES7511-1AK02-0AB0",
    systemCategory: "Computer Controller",
    environmentType: "Manufacturing Floor",
    operatingHours: 4250,
    notes: "PROFINET ring controller on Line 2",
    createdAt: now,
    updatedAt: now,
  };
}

function validDiagnosticPayload(userId) {
  const now = new Date();
  return {
    userId,
    equipmentName: "CAT 320D Excavator",
    equipmentModel: "320D-HYD-2024",
    systemCategory: "Hydraulic",
    environmentType: "Heavy Industrial",
    symptomDescription: "Boom cylinder sluggish under load with high-pitched cavitation whine at 2200 RPM.",
    attachedMediaSummary: "1 photo attached, 8s acoustic spectrum recorded",
    rootCauseTitle: "Suction Line Air Ingress & Main Relief Valve Drift",
    severity: "HIGH",
    confidencePercent: 91,
    technicalAnalysis: "Acoustic signature indicates pump cavitation caused by suction strainer restriction.",
    safetyWarnings: ["Depressurize hydraulic reservoir before opening suction flange"],
    requiredTools: ["Digital pressure gauge (0-400 bar)", "24mm flare wrench"],
    repairSteps: ["Lower boom to ground and relieve system pressure", "Inspect suction hose clamps"],
    completedStepIndices: [0],
    verificationTest: "Run main pump at 1800 RPM and verify main relief pressure holds 350 bar.",
    status: "IN_PROGRESS",
    createdAt: now,
    updatedAt: now,
  };
}

function validFlowchartPayload(userId) {
  const now = new Date();
  return {
    userId,
    title: "PLC CPU Fault LED Flashing Red",
    equipmentModel: "Siemens S7-1500 / Allen-Bradley ControlLogix",
    systemCategory: "Computer Controller",
    environmentType: "Manufacturing Floor",
    failureMode: "Controller enters STOP mode with I/O bus fault",
    currentNodeId: "N1",
    visitedPathSummary: "Started diagnostic flowchart",
    nodesList: [
      "N1::QUESTION::Is 24V DC power within 22.8V-25.2V at the controller backplane?::Measure with multimeter under load::N2::N3::Verify backplane supply",
      "N2::CONCLUSION::Replace faulty 24V switched-mode power supply or tighten loose terminal block::Voltage drop under load triggers brownout reset::::::Replace 24V PSU",
    ],
    isResolved: false,
    createdAt: now,
    updatedAt: now,
  };
}

// --- DIRTY DOZEN & SECURITY BOUNDARIES ---

test("1. Unauthenticated user cannot read or write equipment, diagnostics, or flowcharts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("equipment").get());
  await assertFails(unauthDb.collection("diagnostics").get());
  await assertFails(unauthDb.collection("flowcharts").get());
  await assertFails(
    unauthDb.collection("equipment").doc("eq_1").set(validEquipmentPayload(ALICE_UID))
  );
});

test("2. Authenticated user can create valid EquipmentProfile, DiagnosticSession, and TroubleshootingFlowchart", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("equipment").doc("eq_1").set(validEquipmentPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("diagnostics").doc("diag_1").set(validDiagnosticPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("flowcharts").doc("flow_1").set(validFlowchartPayload(ALICE_UID))
  );
});

test("3. Cross-user isolation: Alice cannot read, update, or delete Bob's records", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("diagnostics").doc("bob_diag").set(validDiagnosticPayload(BOB_UID));
    await db.collection("flowcharts").doc("bob_flow").set(validFlowchartPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("diagnostics").doc("bob_diag").get());
  await assertFails(aliceDb.collection("flowcharts").doc("bob_flow").get());
  await assertFails(aliceDb.collection("diagnostics").doc("bob_diag").delete());
});

test("4. Query alignment: Authenticated user can query with matching userId filter and fails without filter", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("diagnostics").doc("alice_diag").set(validDiagnosticPayload(ALICE_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("diagnostics").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("diagnostics").get());
});

test("5. Identity spoofing: Alice cannot create a record with Bob's userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("equipment").doc("spoof_eq").set(validEquipmentPayload(BOB_UID))
  );
});

test("6. Shadow field injection: Extra unapproved fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payloadWithGhost = {
    ...validEquipmentPayload(ALICE_UID),
    isVerifiedAdmin: true,
  };
  await assertFails(
    aliceDb.collection("equipment").doc("ghost_eq").set(payloadWithGhost)
  );
});

test("7. Terminal state locking: DiagnosticSession with status RESOLVED cannot be updated further", async () => {
  const initial = {
    ...validDiagnosticPayload(ALICE_UID),
    status: "RESOLVED",
  };
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("diagnostics").doc("resolved_diag").set(initial);
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("diagnostics").doc("resolved_diag").update({
      completedStepIndices: [0, 1],
      updatedAt: new Date(),
    })
  );
});

test("8. Enum validation: Invalid systemCategory is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const invalidPayload = {
    ...validEquipmentPayload(ALICE_UID),
    systemCategory: "Antigravity Drive",
  };
  await assertFails(
    aliceDb.collection("equipment").doc("bad_cat").set(invalidPayload)
  );
});
