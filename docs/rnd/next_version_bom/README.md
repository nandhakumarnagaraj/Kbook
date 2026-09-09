# Raw Material Inventory & Recipe (BOM) Depletion Engine (Next Version)

This directory contains the fully verified and tested implementation of the Offline-First Delta-Event Sourced Raw Material & Recipe (BOM) Depletion Engine, ready to be integrated in the next major version of KhanaBook POS.

## Archived Components
- `RawMaterialEntity.kt`: Room entity for raw materials table (`name`, `unit`, `stockQuantity`, `lowStockThreshold`, `costPerUnit`).
- `ItemRecipeEntity.kt`: Room entity mapping menu items to raw material ingredient requirements per plate.
- `RawMaterialDao.kt`: Room DAO for raw material queries and atomic stock decrements.
- `ItemRecipeDao.kt`: Room DAO for recipe lookups per menu item.
- `next_version_bom_inventory.patch`: Git patch for `AppDatabase.kt` (v73 + `MIGRATION_72_73`), `DatabaseProvider.kt`, `DatabaseModule.kt`, `TenantDaos.kt`, `InventoryConsumptionManager.kt`, `MasterSyncProcessor.kt`, and `InventoryViewModel.kt`.

## How to Apply in Next Version
1. Copy the entity and DAO files back to `Android/app/src/main/java/com/khanabook/lite/pos/data/local/`:
   - `RawMaterialEntity.kt` & `ItemRecipeEntity.kt` -> `entity/`
   - `RawMaterialDao.kt` & `ItemRecipeDao.kt` -> `dao/`
2. Apply the patch:
   ```bash
   git apply docs/rnd/next_version_bom/next_version_bom_inventory.patch
   ```
3. Run tests to verify:
   ```bash
   cd Android && .\gradlew.bat testDebugUnitTest --tests "com.khanabook.lite.pos.InventoryConsumptionTest"
   ```
