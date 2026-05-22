# Spring Buddy

Spring Buddy is an IntelliJ IDEA plugin that adds focused inspections, quick fixes, and visual hints for Spring Java projects. It combines Spring Java Format conventions with practical Spring transaction and persistence checks that are easy to miss during everyday development.

## What Is Included

### Spring Java Format Inspections

These inspections mirror or complement Spring Java Format and related Checkstyle rules:

- Catch variable naming: flags single-letter catch variables such as `e` and encourages clearer names like `ex`.
- Test class naming: enforces Spring-style `*Tests` names and handles integration-test variants such as `UserServiceTestIT -> UserServiceITTests`.
- Import ordering: groups and sorts imports according to Spring Java Format conventions.
- Utility class constructors: flags utility classes without a private constructor, while excluding Spring-managed classes such as `@Configuration`, `@Service`, `@Component`, `@Repository`, and `@SpringBootApplication`.
- Method order: checks `equals`, `hashCode`, and `toString` order while avoiding Lombok-generated method false positives.
- Method visibility: reports public/protected methods that overexpose non-public/final classes, while ignoring interface methods.
- Ternary conventions: detects missing parentheses and null-comparison patterns that violate Spring style.

Why: These checks keep code aligned with Spring project conventions directly in the IDE, before Checkstyle or CI catches the issue.

### Testing Style Migrations

The plugin includes inspections and fixes for common test style problems:

- JUnit assertions to AssertJ conversions, including:
  - `assertEquals(expected, actual)` -> `Assertions.assertThat(actual).isEqualTo(expected)`
  - `assertNull(actual)` -> `Assertions.assertThat(actual).isNull()`
  - `assertNotNull(actual)` -> `Assertions.assertThat(actual).isNotNull()`
  - `assertTrue(condition)` / `assertFalse(condition)`
  - `assertThrows(...)`
  - `assertInstanceOf(...)`
  - `assertDoesNotThrow(...)`
  - `fail(Throwable)` -> `Assertions.fail("Unexpected exception", ex)`
  - `fail()` -> `Assertions.fail("exception")`
- Safer conversion of `try/catch + fail(ex)` patterns to AssertJ without mutating captured local variables inside lambdas.
- Mockito style inspection and quick fix for aligning BDDMockito/Mockito stubbing style according to the configured convention.

Why: AssertJ produces more fluent, readable assertions, but migration is repetitive and error-prone. The plugin automates common cases while avoiding known lambda-capture pitfalls.

### Transactional Pitfall Inspections

The plugin has a dedicated `@Transactional pitfalls` group. Each pitfall is registered as its own inspection so results are grouped clearly in the inspection tree.

Included checks:

- Private `@Transactional` methods: Spring proxies cannot intercept private methods.
- Static `@Transactional` methods: static calls are not intercepted by Spring AOP.
- Final `@Transactional` methods: CGLIB subclass proxies cannot override final methods.
- Final `@Transactional` classes: CGLIB cannot subclass final classes.
- `@Transactional` methods in final classes.
- Self-invocation: same-class calls bypass Spring proxies.
- `REQUIRES_NEW` self-invocation: the new transaction boundary is ignored.
- `REQUIRES_NEW` in loops or deep call chains: can borrow multiple database connections and exhaust the pool.
- Constructor trap: constructors calling `@Transactional` methods run before the proxy exists.
- `@Async` and transaction boundaries: async execution loses thread-bound transaction context.
- `@Async` with `@TransactionalEventListener`: can race transaction commit visibility.
- Long-running work inside transactions: flags likely external/client calls and sleeps inside transactional methods.
- Read-only transactions that perform writes.
- Read-only transactions that return likely entity types instead of DTO/projection/scalar/container types.
- Checked exceptions that do not trigger rollback by default.
- Conflicting rollback/no-rollback rules.
- Swallowed exceptions inside `@Transactional` methods.
- `@Transactional` on `@Bean` methods in `@Configuration` classes.
- `@Transactional` on interface methods when CGLIB proxying is enabled.
- Multiple persistence writes without an apparent service transaction boundary.
- `@Transactional` on integration tests, including method-level `@Transactional` on `@Test` methods.
- Repository-level `@Transactional`, which is usually too narrow for business units of work.
- Weak/default isolation with multiple reads.
- `setRollbackOnly()` without an explicit thrown exception.
- `Propagation.NESTED` with standard JPA transaction managers.
- `@Transactional` timeout usage with likely unsupported drivers.
- `@Transactional` on abstract classes.

Quick fixes are provided where there is a reasonably safe transformation, for example:

- Make private transactional methods public.
- Remove `static` or `final` modifiers.
- Remove problematic `@Transactional` annotations.
- Add `@Transactional` for multiple write operations without a boundary.
- Change read-only settings.
- Add rollback rules.
- Use stronger isolation.
- Disable `spring.jpa.open-in-view`.

Why: Most transaction bugs are caused by invisible proxy behavior, wrong boundaries, or assumptions about rollback. These inspections make the hidden Spring AOP model visible while you code.

### Persistence Performance Inspections

The plugin includes persistence-oriented performance checks:

- N+1 query detection for lazy association access inside loops and stream operations.
- Detection of lazy access inside enhanced `for`, classic `for`, `while`, `do/while`, and common stream/lambda operations.
- Lazy association detection for JPA and Hibernate patterns, including default-lazy collection relationships and explicit `FetchType.LAZY` annotations.
- OSIV-aware detection: if Open Session In View is enabled, the N+1 inspection can detect lazy access outside explicit `@Transactional` methods.
- Open Session In View (`spring.jpa.open-in-view=true`) inspection with a quick fix to disable it.

Why: N+1 issues and OSIV often do not appear as compile errors, but they create serious runtime performance and connection-pool problems.

### Transaction Propagation Visualizer

The plugin registers an IntelliJ inlay hints provider for Java:

- Shows inline hints near resolved calls to `@Transactional` methods.
- Indicates propagation behavior such as:
  - `Starts Tx`
  - `Starts New Tx`
  - `Suspends Tx`
  - `Nested Tx`
  - `Requires Existing Tx`
  - `⚠ Ignored: Self-Invocation`
  - `⚠ Ignored: private`
  - `⚠ Write-Call in Read-Only`
  - `⚡ New Thread: No Context`
- Adds tooltips explaining why the transaction behavior matters.
- Suppresses redundant default `REQUIRED -> REQUIRED` hints to reduce noise.

Why: Transaction behavior is invisible in code. Inlay hints make propagation boundaries and proxy limitations visible in the editor.

### Bundled Inspection Runner

The plugin adds a context-menu action:

- `Run Spring Buddy Inspections`

It can run the plugin inspections together with enabled built-in inspections from the bundled profile.

Why: This gives you a project/file-level inspection pass without manually configuring every inspection profile each time.

## What This Plugin Is Not

This plugin uses static analysis and heuristics. It does not replace:

- Runtime integration tests.
- Database-specific testing.
- Load testing.
- Full call-graph or whole-program transaction analysis.
- Spring runtime proxy configuration introspection in every edge case.

The inspections are intentionally conservative where automatic fixes could change behavior. Some warnings include suggested manual actions rather than applying risky code changes.

## Development

Build the plugin:

```bash
./gradlew buildPlugin
```

Run tests:

```bash
./gradlew test
```

Run a specific test class:

```bash
./gradlew test --tests com.sarhan.springformat.inspection.SpringTransactionalPrivateMethodInspectionTest
```

## Project Scope

The plugin started as Spring Java Format support and has expanded into a broader Spring productivity and correctness companion. The current focus is:

- Spring coding conventions.
- Test style migration.
- Transaction proxy pitfalls.
- Persistence performance pitfalls.
- IDE-time visualization of transaction behavior.

## Follow-up Roadmap

Future high-value inspections are tracked in [TODO.md](TODO.md). The roadmap currently prioritizes:

- Spring Security proxy pitfalls for `@PreAuthorize`, `@PostAuthorize`, `@Secured`, and `@RolesAllowed`.
- Cache proxy pitfalls for `@Cacheable`, `@CachePut`, and `@CacheEvict`.
- Async proxy pitfalls beyond transaction interactions.
- JPA entity design inspections for Lombok, constructors, and relationship defaults.
- Controller boundary inspections for entity leaks and repository access.
- Validation and configuration property pitfalls.
- Scheduled job transaction and clustering pitfalls.

Use `TODO.md` as the source of truth for planned follow-up inspections.
