# Android Clean Architecture

## Trigger Conditions
- Creating new feature modules or screens
- Refactoring existing code into layers
- User asks about MVVM, MVI, use cases, or DI
- Designing data flow between UI and network/database
- Adding new repository or use case classes

## Architecture Layers

```
┌─────────────────────────────────┐
│  UI Layer (Compose + ViewModel) │  ← Presentation
├─────────────────────────────────┤
│  Domain Layer (UseCases)        │  ← Business Logic
├─────────────────────────────────┤
│  Data Layer (Repos + Sources)   │  ← Data Access
└─────────────────────────────────┘
```

---

## Module Structure (KhanaBook)

```
app/
├── di/                    # Hilt modules
├── ui/                    # Compose screens + ViewModels
│   ├── billing/
│   ├── menu/
│   ├── reports/
│   └── sync/
├── domain/
│   ├── model/             # Domain entities
│   ├── usecase/           # Business logic
│   └── repository/        # Repository interfaces
├── data/
│   ├── repository/        # Repository implementations
│   ├── local/             # Room DAOs, DataStore
│   ├── remote/            # Retrofit API services
│   └── mapper/            # Entity ↔ DTO mappers
└── core/
    ├── util/
    └── extension/
```

## MVVM Pattern (Default for KhanaBook)

```kotlin
// ViewModel — holds UI state, calls use cases
@HiltViewModel
class BillingViewModel @Inject constructor(
    private val createBillUseCase: CreateBillUseCase,
    private val getMenuItemsUseCase: GetMenuItemsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    fun addItem(item: MenuItem) {
        _uiState.update { it.copy(items = it.items + item) }
    }

    fun createBill() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            createBillUseCase(_uiState.value.toBillRequest())
                .onSuccess { bill ->
                    _uiState.update { it.copy(isLoading = false, createdBill = bill) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }
}
```

## Use Case Pattern

```kotlin
// Single-responsibility use case
class CreateBillUseCase @Inject constructor(
    private val billRepository: BillRepository,
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(request: BillRequest): Result<Bill> {
        return runCatching {
            val bill = billRepository.createBill(request)
            syncRepository.scheduleBillSync(bill.id)
            bill
        }
    }
}

// Use case with flow (for observing data)
class ObserveMenuItemsUseCase @Inject constructor(
    private val menuRepository: MenuRepository
) {
    operator fun invoke(categoryId: String): Flow<List<MenuItem>> {
        return menuRepository.observeMenuItems(categoryId)
    }
}
```

## Repository Pattern

```kotlin
// Interface in domain layer
interface BillRepository {
    suspend fun createBill(request: BillRequest): Bill
    fun observeBills(date: LocalDate): Flow<List<Bill>>
    suspend fun syncPendingBills(): Result<Int>
}

// Implementation in data layer
class BillRepositoryImpl @Inject constructor(
    private val billDao: BillDao,
    private val billApi: BillApiService,
    private val mapper: BillMapper
) : BillRepository {

    override suspend fun createBill(request: BillRequest): Bill {
        val entity = mapper.toEntity(request)
        val id = billDao.insert(entity)
        return mapper.toDomain(entity.copy(id = id))
    }

    override fun observeBills(date: LocalDate): Flow<List<Bill>> {
        return billDao.observeByDate(date).map { entities ->
            entities.map(mapper::toDomain)
        }
    }

    override suspend fun syncPendingBills(): Result<Int> {
        val pending = billDao.getPendingSyncBills()
        return runCatching {
            pending.forEach { bill ->
                billApi.uploadBill(mapper.toDto(bill))
                billDao.markSynced(bill.id)
            }
            pending.size
        }
    }
}
```

## Hilt DI Setup

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindBillRepository(impl: BillRepositoryImpl): BillRepository

    @Binds
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KhanaBookDatabase {
        return Room.databaseBuilder(context, KhanaBookDatabase::class.java, "khanabook.db")
            .addMigrations(*AllMigrations.list)
            .build()
    }

    @Provides
    fun provideBillDao(db: KhanaBookDatabase): BillDao = db.billDao()
}
```

## MVI Pattern (For complex screens)

```kotlin
// Use MVI when: multiple user intents, complex state transitions
sealed interface BillingIntent {
    data class AddItem(val item: MenuItem) : BillingIntent
    data class RemoveItem(val index: Int) : BillingIntent
    data class ApplyDiscount(val percent: Double) : BillingIntent
    data object SubmitBill : BillingIntent
}

// Process intents in ViewModel
fun processIntent(intent: BillingIntent) {
    when (intent) {
        is BillingIntent.AddItem -> addItem(intent.item)
        is BillingIntent.RemoveItem -> removeItem(intent.index)
        is BillingIntent.ApplyDiscount -> applyDiscount(intent.percent)
        is BillingIntent.SubmitBill -> createBill()
    }
}
```

---

## Anti-patterns
- ❌ ViewModel directly calling DAO or Retrofit (skip use case for complex logic)
- ❌ Passing Context to ViewModel (use @ApplicationContext in repo)
- ❌ Domain layer depending on Android framework classes
- ❌ Repository returning Room entities to UI (always map to domain models)
- ❌ Use cases with zero logic (don't wrap repo calls for nothing)
- ❌ God ViewModel with 500+ lines (split into multiple use cases)

## Verification Checklist
- [ ] Each layer only depends on the layer below it
- [ ] Domain layer has zero Android imports
- [ ] All dependencies injected via Hilt (no manual instantiation)
- [ ] ViewModels use StateFlow (not LiveData) for Compose
- [ ] Repository handles offline-first logic (local write → schedule sync)
- [ ] Use cases are single-responsibility and testable
- [ ] Mappers exist between all layer boundaries
