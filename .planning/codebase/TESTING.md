# Testing Patterns

**Analysis Date:** 2024-12-25

## Test Framework

**Runner:**
- **Backend:** JUnit 5 (JUnit Jupiter) [5.10.1 from `spring-boot-starter-test` 3.2.0]
- **Frontend:** Not detected (likely none configured yet).

**Assertion Library:**
- **Backend:** AssertJ and JUnit 5 Assertions (`org.junit.jupiter.api.Assertions`).

**Run Commands:**
```bash
./mvnw test            # Run all backend tests
npm run lint           # Frontend linting (nearest equivalent currently)
```

## Test File Organization

**Location:**
- **Backend:** Co-located in `backend/src/test/java` mirroring the main source package structure.

**Naming:**
- **Backend:** `*Test.java` (e.g., `BacktestServiceTest.java`).
- **Performance tests:** `*BenchmarkTest.java` or `*PerformanceTest.java`.

**Structure:**
```
backend/src/test/java/com/quantbackengine/backend/
├── controller/
│   └── FileUploadControllerTest.java
├── service/
│   ├── BacktestServiceBenchmarkTest.java
│   ├── BacktestServiceTest.java
│   └── MarketDataServicePerformanceTest.java
└── strategy/  (empty)
```

## Test Structure

**Suite Organization (JUnit 5):**
```typescript
@ExtendWith(MockitoExtension.class)
class ExampleTest {
    @Mock
    private Dependency dependency;

    @InjectMocks
    private ServiceToTest service;

    @BeforeEach
    void setUp() {
        // Setup logic
    }

    @Test
    void testMethodName_Scenario_ExpectedResult() {
        // Arrange
        // Act
        // Assert
    }
}
```

**Patterns:**
- **Setup pattern:** Uses `@BeforeEach` and `ReflectionTestUtils.setField` to initialize private values in tested services.
- **Assertion pattern:** Standard JUnit 5 `assertNotNull`, `assertEquals`, `assertFalse`, `assertTrue`.

## Mocking

**Framework:** Mockito (`mockito-core`).

**Patterns:**
```typescript
@ExtendWith(MockitoExtension.class)
class BacktestServiceTest {
    @Mock
    private StrategyRegistry strategyRegistry;

    @InjectMocks
    private BacktestService backtestService;

    @Test
    void testRunBacktest_Success() {
        // Mock specific behavior
        when(strategyRegistry.getStrategy("NAME")).thenReturn(Optional.of(mockStrategy));
        
        // Use the mocked service
        backtestService.runBacktest(request);
    }
}
```

**What to Mock:**
- External dependencies (Repositories, Services, Registries).
- Complex library objects that are hard to instantiate (e.g., `BarSeries` in some cases).

**What NOT to Mock:**
- DTOs and simple data objects (use `@Builder` to create them).
- Java standard library classes (`List`, `Map`, `Optional`).

## Fixtures and Factories

**Test Data:**
- Manual creation in test methods using `@Builder` patterns or inline instantiation.
- Large datasets for performance tests are generated dynamically in loops.

**Location:**
- Inline within test classes or using `ReflectionTestUtils` for private field injection.

## Coverage

**Requirements:** None explicitly enforced in `pom.xml`.

**View Coverage:**
- Typically via IDE (IntelliJ/Eclipse) or by adding JaCoCo plugin (not present).

## Test Types

**Unit Tests:**
- Focus on single service methods with mocked dependencies.
- Example: `BacktestServiceTest.java`.

**Performance & Benchmark Tests:**
- Custom tests that measure execution time for large datasets.
- Use manual timing (`System.nanoTime()`) and report results to `System.out`.
- Example: `BacktestServiceBenchmarkTest.java`, `MarketDataServicePerformanceTest.java`.

**Integration Tests:**
- `FileUploadControllerTest.java` uses `@SpringBootTest` (implied, let's verify if it uses `MockMvc`).

## Common Patterns

**Async Testing:**
- Not observed (core engine appears synchronous).

**Error Testing:**
- Not explicitly found in samples, but standard JUnit 5 `assertThrows` pattern is recommended.

---

*Testing analysis: 2024-12-25*
