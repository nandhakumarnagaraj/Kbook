# Spring Boot API Patterns

## Trigger Conditions
- Creating new REST endpoints
- Refactoring controller/service/repository structure
- Adding validation, pagination, or error handling
- User asks about DTO mapping or API conventions
- Designing request/response contracts

---

## Controller Structure

```java
@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse createBill(
            @Valid @RequestBody CreateBillRequest request,
            @AuthenticationPrincipal MerchantPrincipal merchant) {
        return billService.createBill(request, merchant.getId());
    }

    @GetMapping
    public Page<BillSummaryResponse> listBills(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) LocalDate date) {
        return billService.listBills(merchant.getId(), date, PageRequest.of(page, size));
    }

    @GetMapping("/{billId}")
    public BillResponse getBill(
            @PathVariable UUID billId,
            @AuthenticationPrincipal MerchantPrincipal merchant) {
        return billService.getBill(billId, merchant.getId());
    }

    @PutMapping("/{billId}/status")
    public BillResponse updateStatus(
            @PathVariable UUID billId,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal MerchantPrincipal merchant) {
        return billService.updateStatus(billId, request, merchant.getId());
    }
}
```

### Controller Rules
- Controllers ONLY handle HTTP concerns (status codes, path mapping)
- No business logic in controllers
- Always validate request bodies with `@Valid`
- Always scope to authenticated merchant (multi-tenancy)
- Return DTOs, never entities

---

## Service Layer

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillService {

    private final BillRepository billRepository;
    private final MenuItemRepository menuItemRepository;
    private final BillMapper billMapper;
    private final EventPublisher eventPublisher;

    @Transactional
    public BillResponse createBill(CreateBillRequest request, UUID merchantId) {
        // Validate items exist and belong to merchant
        List<MenuItem> items = menuItemRepository
            .findAllByIdInAndMerchantId(request.getItemIds(), merchantId);
        if (items.size() != request.getItemIds().size()) {
            throw new InvalidBillException("One or more items not found");
        }

        Bill bill = billMapper.toEntity(request, items, merchantId);
        bill.calculateTotals();
        bill = billRepository.save(bill);

        eventPublisher.publish(new BillCreatedEvent(bill.getId()));
        return billMapper.toResponse(bill);
    }

    public Page<BillSummaryResponse> listBills(UUID merchantId, LocalDate date, Pageable pageable) {
        Page<Bill> bills = (date != null)
            ? billRepository.findByMerchantIdAndDate(merchantId, date, pageable)
            : billRepository.findByMerchantId(merchantId, pageable);
        return bills.map(billMapper::toSummaryResponse);
    }

    public BillResponse getBill(UUID billId, UUID merchantId) {
        Bill bill = billRepository.findByIdAndMerchantId(billId, merchantId)
            .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
        return billMapper.toResponse(bill);
    }
}
```

### Service Rules
- Class-level `@Transactional(readOnly = true)`, method-level `@Transactional` for writes
- Throw domain exceptions (not HTTP exceptions)
- Validate business rules here (not in controller)
- Publish events for side effects (don't call other services directly)

---

## Repository Pattern

```java
public interface BillRepository extends JpaRepository<Bill, UUID> {

    Page<Bill> findByMerchantId(UUID merchantId, Pageable pageable);

    @Query("SELECT b FROM Bill b WHERE b.merchantId = :merchantId AND DATE(b.createdAt) = :date")
    Page<Bill> findByMerchantIdAndDate(UUID merchantId, LocalDate date, Pageable pageable);

    Optional<Bill> findByIdAndMerchantId(UUID id, UUID merchantId);

    @Query("SELECT SUM(b.totalAmount) FROM Bill b WHERE b.merchantId = :merchantId AND b.createdAt BETWEEN :start AND :end")
    Optional<BigDecimal> sumTotalByMerchantAndDateRange(UUID merchantId, Instant start, Instant end);
}
```

---

## DTO Mapping

```java
@Mapper(componentModel = "spring")
public interface BillMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "merchantId", source = "merchantId")
    Bill toEntity(CreateBillRequest request, List<MenuItem> items, UUID merchantId);

    BillResponse toResponse(Bill bill);
    BillSummaryResponse toSummaryResponse(Bill bill);
}

// Request DTO (with validation)
public record CreateBillRequest(
    @NotEmpty List<UUID> itemIds,
    @NotNull @Positive BigDecimal totalAmount,
    @Size(max = 500) String notes,
    @NotNull PaymentMethod paymentMethod
) {}

// Response DTO
public record BillResponse(
    UUID id,
    List<BillItemResponse> items,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal discount,
    BigDecimal totalAmount,
    BillStatus status,
    Instant createdAt
) {}
```

---

## Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidBillException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleInvalidBill(InvalidBillException ex) {
        return new ErrorResponse("INVALID_BILL", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
        return new ValidationErrorResponse("VALIDATION_FAILED", errors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
    }
}

public record ErrorResponse(String code, String message) {}
```

---

## Pagination

```java
// Standard paginated response (Spring Page auto-serializes)
// GET /api/v1/bills?page=0&size=20&sort=createdAt,desc

// Custom PageResponse if needed:
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages(), page.isLast()
        );
    }
}
```

---

## Anti-patterns
- ❌ Business logic in controllers
- ❌ Returning JPA entities from controllers (lazy loading exceptions, data leaks)
- ❌ Unbounded queries (no pagination on list endpoints)
- ❌ Catching generic Exception in service methods (be specific)
- ❌ Stack traces in API error responses
- ❌ Missing `@Valid` on request bodies
- ❌ N+1 queries from lazy-loaded relationships

## Verification Checklist
- [ ] All endpoints have proper HTTP method and status codes
- [ ] Request DTOs have validation annotations
- [ ] Response DTOs don't expose internal IDs or sensitive data
- [ ] Multi-tenant scoping on all queries (merchantId filter)
- [ ] Pagination on all list endpoints
- [ ] Global exception handler covers all domain exceptions
- [ ] No entity returned directly from controller
- [ ] Transactions scoped correctly (readOnly for reads)
