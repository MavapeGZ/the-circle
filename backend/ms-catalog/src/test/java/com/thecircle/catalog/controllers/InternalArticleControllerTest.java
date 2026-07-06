package com.thecircle.catalog.controllers;

import com.thecircle.catalog.model.Article;
import com.thecircle.catalog.model.ArticleStatus;
import com.thecircle.catalog.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization behaviour of the service-to-service {@code /internal/catalog}
 * endpoints (doc §4.6): with a configured key only callers presenting the
 * matching {@code X-Internal-Api-Key} header are served, missing resources
 * surface as 404, and a blank key (local/dev) disables the check.
 *
 * <p>The startup fail-closed guard itself is covered by
 * {@link InternalArticleControllerGuardTest}; here the focus is request-time
 * access control.
 */
class InternalArticleControllerTest {

    private static final String KEY = "s3cret";

    private final ArticleService service = mock(ArticleService.class);

    private InternalArticleController controllerWithKey(String key) {
        InternalArticleController controller =
                new InternalArticleController(service, mock(Environment.class));
        ReflectionTestUtils.setField(controller, "internalApiKey", key);
        return controller;
    }

    private static HttpServletRequest requestWithKey(String headerValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Internal-Api-Key")).thenReturn(headerValue);
        return request;
    }

    @Test
    void correctKeyIsAuthorizedAndStatusIsUpdated() {
        InternalArticleController controller = controllerWithKey(KEY);
        Article updated = Article.builder().id("a1").status(ArticleStatus.SOLD).build();
        when(service.updateStatus("a1", ArticleStatus.SOLD)).thenReturn(updated);

        ResponseEntity<Article> response =
                controller.updateStatus("a1", ArticleStatus.SOLD, requestWithKey(KEY));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(updated);
    }

    @Test
    void wrongKeyIsForbiddenAndServiceIsNeverCalled() {
        InternalArticleController controller = controllerWithKey(KEY);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.updateStatus("a1", ArticleStatus.SOLD, requestWithKey("nope")))
                .matches(e -> e.getStatusCode().equals(HttpStatus.FORBIDDEN));

        verify(service, never()).updateStatus(any(), any());
    }

    @Test
    void missingKeyHeaderIsForbidden() {
        InternalArticleController controller = controllerWithKey(KEY);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.updateStatus("a1", ArticleStatus.SOLD, requestWithKey(null)))
                .matches(e -> e.getStatusCode().equals(HttpStatus.FORBIDDEN));
    }

    @Test
    void unknownArticleOnAuthorizedReadReturnsNotFound() {
        InternalArticleController controller = controllerWithKey(KEY);
        when(service.getArticleById("missing")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.getById("missing", requestWithKey(KEY)))
                .matches(e -> e.getStatusCode().equals(HttpStatus.NOT_FOUND));
    }

    @Test
    void blankKeyDisablesTheGuardAndLetsCallsThrough() {
        InternalArticleController controller = controllerWithKey("");

        assertThatCode(() ->
                controller.deleteUserArticles(7L, requestWithKey(null))).doesNotThrowAnyException();

        verify(service).removeArticlesByAuthorId(7L);
    }

    @Test
    void deleteUserArticlesWithCorrectKeyReturnsNoContent() {
        InternalArticleController controller = controllerWithKey(KEY);

        ResponseEntity<Void> response = controller.deleteUserArticles(7L, requestWithKey(KEY));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(service).removeArticlesByAuthorId(7L);
    }

    @Test
    void deleteUserArticlesWithWrongKeyIsForbidden() {
        InternalArticleController controller = controllerWithKey(KEY);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.deleteUserArticles(7L, requestWithKey("nope")))
                .matches(e -> e.getStatusCode().equals(HttpStatus.FORBIDDEN));

        verify(service, never()).removeArticlesByAuthorId(anyLong());
    }
}
