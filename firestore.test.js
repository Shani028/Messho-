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

function validSkuPayload(userId, now) {
  return {
    userId,
    productName: "Cotton Embroidered Kurti Set",
    category: "Ethnic Wear",
    lengthCm: 28,
    breadthCm: 22,
    heightCm: 9,
    deadWeightGrams: 380,
    sellingPrice: 449,
    productCost: 210,
    packagingCost: 12,
    zone: "National",
    currentShippingCharge: 94,
    optimizedShippingCharge: 68,
    monthlyOrders: 250,
    recommendation: "Reduce height from 9cm to 7cm using poly-mailer to drop from 1kg to 500g slab.",
    createdAt: now,
    updatedAt: now,
  };
}

function validMemberPayload(userId, now) {
  return {
    userId,
    personName: "Rajesh Sharma",
    roleTitle: "Admin",
    hubLocation: "Surat Hub",
    isAdminPerson: true,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot read skus or members", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("skus").get());
  await assertFails(unauthDb.collection("members").get());
});

test("Authenticated user: can create, update, query, and delete own SKU", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const skuRef = aliceDb.collection("skus").doc("sku_1");

  await assertSucceeds(skuRef.set(validSkuPayload(ALICE_UID, now)));
  await assertSucceeds(
    aliceDb.collection("skus").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    skuRef.update({
      heightCm: 7,
      optimizedShippingCharge: 64,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(skuRef.delete());
});

test("Authenticated user: can create, rename (update personName), and delete any person/admin in own members", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const memRef = aliceDb.collection("members").doc("mem_1");

  await assertSucceeds(memRef.set(validMemberPayload(ALICE_UID, now)));
  await assertSucceeds(
    aliceDb.collection("members").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    memRef.update({
      personName: "Vikram Patel (Updated Admin)",
      roleTitle: "Warehouse Manager",
      isAdminPerson: false,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(memRef.delete());
});

test("Cross-user isolation: Alice cannot read, rename, or delete Bob's member or SKU", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("members")
      .doc("bob_mem")
      .set(validMemberPayload(BOB_UID, now));
    await context
      .firestore()
      .collection("skus")
      .doc("bob_sku")
      .set(validSkuPayload(BOB_UID, now));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("members").doc("bob_mem").get());
  await assertFails(
    aliceDb.collection("members").doc("bob_mem").update({
      personName: "Hacked Name",
      updatedAt: new Date(),
    })
  );
  await assertFails(aliceDb.collection("members").doc("bob_mem").delete());
  await assertFails(aliceDb.collection("skus").doc("bob_sku").get());
});

test("Query enforcer: fails list query without userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("skus").get());
  await assertFails(aliceDb.collection("members").get());
});

test("Shadow update & email spoofing: rejects ghost fields and unverified admin email", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("members")
      .doc("bob_mem")
      .set(validMemberPayload(BOB_UID, now));
  });

  const spoofAdminDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "bhartisani933@gmail.com",
      email_verified: false,
    })
    .firestore();
  await assertFails(spoofAdminDb.collection("members").doc("bob_mem").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("members").doc("alice_mem").set({
      ...validMemberPayload(ALICE_UID, now),
      ghostField: true,
    })
  );
});
