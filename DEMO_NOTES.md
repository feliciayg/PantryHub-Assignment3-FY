# InventoryHub Demo Notes

InventoryHub is a lightweight inventory and expiry management app for small
food-based teams such as cafes, bakeries, school canteens, mini marts,
community food pantries, and small restaurants.

## Firebase Data

The migrated app starts with fresh records in the new Firebase project:

- `users/{uid}`
- `stores/{storeId}`
- `stores/{storeId}/staff/{uid}`
- `stores/{storeId}/inventoryItems/{itemId}`
- `stores/{storeId}/restockOrders/{orderId}`
- `stores/{storeId}/activityLogs/{logId}`

The old Firebase project and its records are not read, modified, or copied.

## Suggested Demo Flow

1. Register with a real email and password, then log out and log in again.
2. Create a store and show its invite code.
3. Add a stock item with quantity, unit, expiry date, reorder threshold, and supplier details.
4. Edit the item, reduce stock, and record waste.
5. Show Inventory Health, expiry summaries, low stock, and Inventory Insights.
6. Move a restock order through To Order, Ordered, In Transit, and Received.
7. Confirm inventory increases only when the order reaches Received.
8. Export inventory CSV, import inventory CSV, and import a sales CSV.
9. Show the resulting stock deductions and activity history.

## Fresh Demo Data

For another fresh demonstration, delete records only from the new test store:

- `inventoryItems`
- `restockOrders`
- `activityLogs`

Keep `users`, the `stores/{storeId}` document, and its `staff` records unless
you also want to repeat registration and store setup.
