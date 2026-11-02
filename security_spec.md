# MechSight Security Specification (Phase 0: Payload-First Security TDD)

## 1. Data Invariants
1. **Authentication & Multi-User Isolation**: Every document in `/equipment/{equipmentId}`, `/diagnostics/{diagnosticId}`, and `/flowcharts/{flowchartId}` must belong to an authenticated user (`request.auth != null`) and strictly enforce `userId == request.auth.uid`.
2. **Path Variable Hardening**: Document IDs (`equipmentId`, `diagnosticId`, `flowchartId`) on `get`, `create`, `update`, and `delete` must match `^[a-zA-Z0-9_\-]+$` and be at most 128 characters.
3. **Strict Key Allowlisting**: All `create` operations must enforce `hasAll` and `hasOnly` for allowed keys. All `update` operations must begin with `isValid[Entity](incoming())`, preserve immutable `userId` and `createdAt`, and restrict modified keys via `affectedKeys().hasOnly(...)`.
4. **Terminal State Locking**: Once a `DiagnosticSession` reaches `status == 'RESOLVED'`, no further updates are permitted (`existing().status != 'RESOLVED'`).
5. **Bounded Strings & Lists**: Every string field enforces explicit `.size()` bounds, and every list enforces `.size() <= MAX` and first-element type validation.
6. **Temporal Integrity**: `createdAt` and `updatedAt` must be Firestore `timestamp` objects with `<= request.time`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read/Write**: `request.auth == null` attempting `get`, `list`, `create`, `update`, or `delete` on `/diagnostics` -> REJECTED.
2. **Cross-User Read (PII/Data Isolation)**: User `alice_123` reading a document with `userId: "bob_456"` -> REJECTED.
3. **Unfiltered Collection Query Scraping**: User `alice_123` executing `db.collection("diagnostics").get()` without `.where("userId", "==", "alice_123")` -> REJECTED.
4. **Identity Spoofing on Create**: User `alice_123` creating an equipment profile with `userId: "bob_456"` -> REJECTED.
5. **Ownership Transfer on Update**: User `alice_123` updating `userId` on their own document to `"bob_456"` -> REJECTED.
6. **Shadow Field Injection**: Sending extra unapproved key `{"isAdmin": true}` on create or update -> REJECTED by `hasOnly`.
7. **Immutable Timestamp Tampering**: Modifying `createdAt` during an `update` -> REJECTED by `incoming().createdAt == existing().createdAt`.
8. **Future Timestamp Injection**: Setting `createdAt` or `updatedAt` to a timestamp > `request.time` or an integer epoch -> REJECTED.
9. **Terminal State Bypass**: Updating a `DiagnosticSession` whose `existing().status` is already `'RESOLVED'` -> REJECTED.
10. **Enum Poisoning**: Setting `systemCategory` to `"NuclearReactor"` or `severity` to `"EXTREME"` -> REJECTED.
11. **Oversized String / Resource Exhaustion**: Sending a 10,000-character `symptomDescription` or 200-character document ID -> REJECTED.
12. **Unbounded Array / Element Type Poisoning**: Sending 50 items in `repairSteps` or non-string elements `[12345]` -> REJECTED.
