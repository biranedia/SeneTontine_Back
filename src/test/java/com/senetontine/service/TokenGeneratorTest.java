package com.senetontine.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {

    private final TokenGenerator generator = new TokenGenerator();

    @Test
    void generateToken_producesDifferentTokensEachTime() {
        String t1 = generator.generateToken();
        String t2 = generator.generateToken();
        assertThat(t1).isNotEqualTo(t2);
        assertThat(t1).hasSizeGreaterThan(20); // 256 bits encodes en base64url, pas un id court devinable
    }

    @Test
    void hash_isDeterministic() {
        String token = "un-token-de-test";
        assertThat(generator.hash(token)).isEqualTo(generator.hash(token));
    }

    @Test
    void hash_doesNotLeakTheOriginalToken() {
        String token = "un-token-de-test";
        String hash = generator.hash(token);
        assertThat(hash).isNotEqualTo(token);
        assertThat(hash).doesNotContain(token);
    }

    @Test
    void hash_differentTokensProduceDifferentHashes() {
        assertThat(generator.hash("token-a")).isNotEqualTo(generator.hash("token-b"));
    }
}
