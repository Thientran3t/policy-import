package com.interview.policyimport.service;

import com.interview.policyimport.model.CanonicalEnrollment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PolicyServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("policy_import_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
        registry.add(
                "spring.flyway.enabled",
                () -> true
        );
    }

    @Autowired
    private PolicyService policyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        // Tests use the seeded ACME and TELCO partners.
        jdbcTemplate.update("DELETE FROM policy");
    }

    private CanonicalEnrollment enrollment(String imei) {
        return new CanonicalEnrollment(
                imei,
                "PLAN_A",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 9, 30),
                new BigDecimal("100.00"),
                "USD"
        );
    }

    private long countPolicies(
            String partnerCode,
            String imei
    ) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM policy
                WHERE partner_code = ?
                  AND imei = ?
                """,
                Long.class,
                partnerCode,
                imei
        );

        return count == null ? 0 : count;
    }

    @Test
    void shouldCreateNewPolicy() {
        String imei = "123456789012345";

        boolean created = policyService.createPolicy(
                "ACME",
                enrollment(imei)
        );

        assertTrue(created);
        assertEquals(1, countPolicies("ACME", imei));
    }

    @Test
    void shouldReturnFalseWhenPolicyAlreadyExists() {
        String imei = "123456789012346";

        boolean first = policyService.createPolicy(
                "ACME",
                enrollment(imei)
        );

        boolean second = policyService.createPolicy(
                "ACME",
                enrollment(imei)
        );

        assertTrue(first);
        assertFalse(second);
        assertEquals(1, countPolicies("ACME", imei));
    }

    @Test
    void shouldAllowSameImeiForDifferentPartners() {
        String imei = "123456789012347";

        boolean acme = policyService.createPolicy(
                "ACME",
                enrollment(imei)
        );

        boolean telco = policyService.createPolicy(
                "TELCO",
                enrollment(imei)
        );

        assertTrue(acme);
        assertTrue(telco);

        assertEquals(1, countPolicies("ACME", imei));
        assertEquals(1, countPolicies("TELCO", imei));
    }

    @Test
    void shouldHandleConcurrentPolicyCreation()
            throws Exception {

        String imei = "123456789012348";

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> task = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new TimeoutException(
                        "Timed out waiting for concurrent start"
                );
            }

            return policyService.createPolicy(
                    "ACME",
                    enrollment(imei)
            );
        };

        try {
            Future<Boolean> first = executor.submit(task);
            Future<Boolean> second = executor.submit(task);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            boolean result1 =
                    first.get(30, TimeUnit.SECONDS);

            boolean result2 =
                    second.get(30, TimeUnit.SECONDS);

            // Exactly one INSERT should succeed.
            assertEquals(
                    1,
                    (result1 ? 1 : 0) + (result2 ? 1 : 0)
            );

            assertEquals(
                    1,
                    countPolicies("ACME", imei)
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldThrowExceptionForUnknownPartner() {
        String imei = "123456789012349";

        assertThrows(
                DataIntegrityViolationException.class,
                () -> policyService.createPolicy(
                        "UNKNOWN_PARTNER",
                        enrollment(imei)
                )
        );

        assertEquals(
                0,
                countPolicies("UNKNOWN_PARTNER", imei)
        );
    }

    @Test
    void shouldRollbackPolicyCreationWhenTransactionFails() {
        String imei = "123456789012350";

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        assertThrows(
                IllegalStateException.class,
                () -> transactionTemplate.execute(status -> {

                    boolean created =
                            policyService.createPolicy(
                                    "ACME",
                                    enrollment(imei)
                            );

                    assertTrue(created);

                    throw new IllegalStateException(
                            "Simulated batch failure"
                    );
                })
        );

        assertEquals(
                0,
                countPolicies("ACME", imei)
        );
    }
}