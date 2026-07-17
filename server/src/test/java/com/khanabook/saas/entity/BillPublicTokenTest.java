package com.khanabook.saas.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BillPublicTokenTest {

    @Test
    void prePersist_assignsNonNullPublicTokenWhenAbsent() {
        Bill bill = new Bill();
        bill.ensurePublicToken();
        assertThat(bill.getPublicToken()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingPublicToken() {
        UUID existing = UUID.randomUUID();
        Bill bill = new Bill();
        bill.setPublicToken(existing);
        bill.ensurePublicToken();
        assertThat(bill.getPublicToken()).isEqualTo(existing);
    }

    @Test
    void generatedTokensAreUniqueAcrossManyBills() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            Bill bill = new Bill();
            bill.ensurePublicToken();
            assertThat(seen.add(bill.getPublicToken())).isTrue();
        }
    }
}
