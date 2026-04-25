package com.khanabook.saas.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EasebuzzControllerTest {

    @Test
    void canonicalStatus_mapsKnownSuccessVariants() {
        assertThat(EasebuzzController.canonicalStatus("success")).isEqualTo("success");
        assertThat(EasebuzzController.canonicalStatus("SUCCESS")).isEqualTo("success");
        assertThat(EasebuzzController.canonicalStatus("successful")).isEqualTo("success");
        assertThat(EasebuzzController.canonicalStatus("captured")).isEqualTo("success");
    }

    @Test
    void canonicalStatus_mapsKnownFailureVariants() {
        assertThat(EasebuzzController.canonicalStatus("failure")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("failed")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("userCancelled")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("user_cancelled")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("dropped")).isEqualTo("failed");
        assertThat(EasebuzzController.canonicalStatus("bounced")).isEqualTo("failed");
    }

    @Test
    void canonicalStatus_unknownAndNullDefaultToPending() {
        assertThat(EasebuzzController.canonicalStatus(null)).isEqualTo("pending");
        assertThat(EasebuzzController.canonicalStatus("initiated")).isEqualTo("pending");
        assertThat(EasebuzzController.canonicalStatus("garbage")).isEqualTo("pending");
        assertThat(EasebuzzController.canonicalStatus("")).isEqualTo("pending");
    }

    @Test
    void sha512_isDeterministicAndHexEncoded() {
        String a = EasebuzzController.sha512("hello|world");
        String b = EasebuzzController.sha512("hello|world");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(128).matches("[0-9a-f]+");
    }
}
