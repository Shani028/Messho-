# Security Specification — Meesho ShipSmart

## 1. Data Invariants
1. **Authenticated Ownership**: Every document in `/skus/{skuId}` and `/members/{memberId}` must belong to an authenticated user (`userId == request.auth.uid`), or be accessed by a verified admin (`isAdmin()`).
2. **Immutable Metadata**: `userId` and `createdAt` cannot be modified after creation on any document.
3. **Strict Schema & Bounds**: All strings enforce non-empty minimum and maximum length bounds; all numeric fields enforce integer type and realistic positive ranges; no unexpected keys (`hasOnly`) are allowed on `create` or `update`.
4. **Query Enforcer**: All `list` operations explicitly verify `resource.data.userId == request.auth.uid || isAdmin()`.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read**: `GET /skus/sku_1` with `auth = null` -> REJECTED.
2. **Cross-User Read**: Alice reads Bob's `/skus/bob_sku` -> REJECTED.
3. **Unfiltered List Query**: Alice queries `/skus` without `.where("userId", "==", aliceUid)` -> REJECTED.
4. **Identity Spoofing on Create**: Alice creates `/skus/sku_1` with `userId: "bob_456"` -> REJECTED.
5. **Shadow Update (Ghost Field)**: Alice updates `/skus/sku_1` adding `isVerified: true` -> REJECTED via `hasOnly()`.
6. **Immutable Field Mutation**: Alice updates `userId` or `createdAt` on `/members/mem_1` -> REJECTED.
7. **Value Poisoning (Oversized String)**: Alice creates `/members/mem_1` with a 500-char `personName` -> REJECTED.
8. **Invalid Enum**: Alice sets `zone: "International"` on `/skus/sku_1` -> REJECTED.
9. **Negative Weight/Cost**: Alice sets `deadWeightGrams: -10` on `/skus/sku_1` -> REJECTED.
10. **Email Spoofing**: Unverified email `bhartisani933@gmail.com` (`email_verified: false`) attempts admin override -> REJECTED.
11. **Self-Assigned Admin Doc**: User attempts `CREATE /admins/alice_123` -> REJECTED (`allow write: if false`).
12. **Cross-User Delete**: Alice attempts to delete Bob's `/members/bob_mem` -> REJECTED.
