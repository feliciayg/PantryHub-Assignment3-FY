# InventoryHub Firebase Migration

## Configuration

- Active Android config: `app/google-services.json`
- Staged copy: `new-firebase/google-services.json`
- Previous config backup: `migration-backup/google-services.old.json`
- Pre-migration source backup:
  `migration-backup/inventoryhub-pre-schema-v2-2026-06-11.tar.gz`

The Android application ID remains
`com.example.pantryhub_assignment3_fy` because the InventoryHub Firebase
Android app was registered with that exact ID. The product name, code concepts,
resources, and Firestore schema use InventoryHub terminology.

## Authentication

Firebase Email/Password Authentication receives the user's real email and
password directly. Firestore stores profile information under `users/{uid}`.
There is no username-to-generated-email conversion.

## Firestore

The app uses:

```text
users/{uid}
stores/{storeId}
stores/{storeId}/staff/{uid}
stores/{storeId}/inventoryItems/{itemId}
stores/{storeId}/restockOrders/{orderId}
stores/{storeId}/activityLogs/{logId}
```

Date and audit fields are written as Firestore timestamps. App models expose
milliseconds to keep the existing expiry and calendar calculations stable.

Deploy the checked-in security configuration after selecting the new Firebase
project with the Firebase CLI:

```bash
firebase use --add
firebase deploy --only firestore:rules,firestore:indexes
```

`firestore.indexes.json` is intentionally empty because the current queries use
single-field indexes that Firestore creates automatically.

## Images

The current image picker stores Android document URIs, matching the existing
app behavior. Firebase Storage is not currently a dependency, so no Storage
rules are required. Shared cloud image upload can be implemented separately if
images must display on every staff device.

## Verification Checklist

1. Register with a real email and password.
2. Log out and log in with the same email.
3. Create a store.
4. Join it from a second fresh account using the invite code.
5. Create, read, edit, and delete an inventory item.
6. Test supplier call and email actions.
7. Reduce stock and record waste.
8. Complete the restock workflow through Received.
9. Verify stock increases only on Received.
10. Test inventory CSV import/export and sales CSV deduction.
11. Check Home, Calendar, Activity, Insights, and notifications.
12. Confirm Firebase contains only the new InventoryHub collection names.
