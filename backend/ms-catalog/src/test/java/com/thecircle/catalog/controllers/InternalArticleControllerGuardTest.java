package com.thecircle.catalog.controllers;

import com.thecircle.catalog.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fail-closed guard on the /internal/catalog endpoints (issue #87).
 * A blank internal API key must be tolerated only under local/dev (or no
 * profile); in any other profile startup must fail so the guarded endpoints
 * — including the destructive DELETE /users/{authorId} — never run unguarded.
 */
class InternalArticleControllerGuardTest {

    private InternalArticleController newController(String key, String... activeProfiles) {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(activeProfiles);
        InternalArticleController controller =
                new InternalArticleController(mock(ArticleService.class), env);
        ReflectionTestUtils.setField(controller, "internalApiKey", key);
        return controller;
    }

    @Test
    void blankKeyWithNoActiveProfileStartsUp() {
        InternalArticleController controller = newController("");
        assertThatCode(controller::verifyKeyConfigured).doesNotThrowAnyException();
    }

    @Test
    void blankKeyUnderDevProfileStartsUp() {
        InternalArticleController controller = newController("", "dev");
        assertThatCode(controller::verifyKeyConfigured).doesNotThrowAnyException();
    }

    @Test
    void blankKeyUnderLocalProfileStartsUp() {
        InternalArticleController controller = newController(" ", "local");
        assertThatCode(controller::verifyKeyConfigured).doesNotThrowAnyException();
    }

    @Test
    void nullKeyUnderProdProfileFailsClosed() {
        InternalArticleController controller = newController(null, "prod");
        assertThatThrownBy(controller::verifyKeyConfigured)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("catalog.internal.api-key");
    }

    @Test
    void blankKeyUnderProdProfileFailsClosed() {
        InternalArticleController controller = newController("", "prod");
        assertThatThrownBy(controller::verifyKeyConfigured)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configuredKeyUnderProdProfileStartsUp() {
        InternalArticleController controller = newController("a-real-secret", "prod");
        assertThatCode(controller::verifyKeyConfigured).doesNotThrowAnyException();
    }

    @Test
    void devProfileIsCaseInsensitive() {
        InternalArticleController controller = newController("", "DEV");
        assertThat(controller).isNotNull();
        assertThatCode(controller::verifyKeyConfigured).doesNotThrowAnyException();
    }
}
