package com.thecircle.e2e.steps;

import com.thecircle.e2e.support.Config;
import com.thecircle.e2e.support.Hooks;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URISyntaxException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class ContractSignatureSteps {
    private static final String LAST_NAME = "User";
    private static final String PASSWORD = "Test1234!";
    private static final String RENT_PRICE = "0";
    private static final String RENT_DEPOSIT = "5";
    private static final String VALID_IBAN = "ES91 2100 0418 4502 0005 1332";
    private static final String API_BASE_URL = "http://localhost:8080/api";
    private static final String CONTRACTS_SERVICE_BASE_URL = "http://localhost:8083/api";

    private String aliceEmail;
    private String bobEmail;
    private String articleTitle;
    private String contractId;
    private String signedStoredContractId;
    private String articleId;
    private String latestPaymentId;
    private String lastConversationId;
    private String lastSentMessageBody;

    private WebDriver driver() {
        return Hooks.WORLD.driver();
    }

    private WebDriverWait waitForPage() {
        return new WebDriverWait(driver(), Duration.ofSeconds(Config.timeoutSeconds()));
    }

    @Given("a browser session with auth and contract response capture enabled")
    public void aBrowserSessionWithAuthAndContractResponseCaptureEnabled() {
        installResponseCapture();
        driver().get(Config.baseUrl());
    }

    @Given("an active contract between Alice owner and Bob receiver")
    public void anActiveContractBetweenAliceOwnerAndBobReceiver() {
        prepareContractBetweenAliceAndBob();
    }

    @Given("a contract between Alice and Bob")
    public void aContractBetweenAliceAndBob() {
        prepareContractBetweenAliceAndBobWithConversation();
    }

    @When("Alice registers, verifies and completes KYC")
    public void aliceRegistersVerifiesAndCompletesKyc() {
        resetBrowserSession();
        installResponseCapture();
        aliceEmail = registerVerifiedUser("Alice");
    }

    @When("Alice publishes a rental article")
    public void alicePublishesARentalArticle() {
        articleTitle = "E2E Contract Rental " + UUID.randomUUID().toString().substring(0, 8);
        ensurePayoutIbanOnFile();
        driver().get(Config.baseUrl() + "/create");

        fill(By.cssSelector("[data-testid='article-title']"), articleTitle);
        fill(By.cssSelector("[data-testid='article-description']"), "E2E rental article for advanced signature");
        selectByValue(By.cssSelector("[data-testid='article-product-type']"), "SYMBOLIC_RENTAL");
        fill(By.cssSelector("[data-testid='article-price']"), RENT_PRICE);
        fill(By.cssSelector("[data-testid='article-deposit']"), RENT_DEPOSIT);
        click(By.cssSelector("[data-testid='article-submit']"));

        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='catalog-search-form']")));
    }

    @When("Bob registers, verifies and completes KYC")
    public void bobRegistersVerifiesAndCompletesKyc() {
        resetBrowserSession();
        installResponseCapture();
        bobEmail = registerVerifiedUser("Bob");
    }

    @When("Bob starts the rental contract for the article")
    public void bobStartsTheRentalContractForTheArticle() {
        openArticleDetailByTitle(articleTitle);
        click(By.cssSelector("[data-testid='article-acquire-button']"));

        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='sign-signer-email']")));
        contractId = extractContractIdFromCurrentUrl();
        assertThat(contractId).isNotBlank();
        waitForPage().until(ExpectedConditions.attributeToBe(By.cssSelector("[data-testid='sign-signer-email']"), "value", bobEmail));
    }

    @When("Bob signs it using the OTP sent to his email")
    public void bobSignsItUsingTheOtpSentToHisEmail() {
        signContractWithOtp(bobEmail);
    }

    @Then("the contract status is PENDING_SIGNATURES")
    public void theContractStatusIsPendingSignatures() {
        assertContractStatus("PENDING_SIGNATURES");
    }

    @When("Bob completes the deposit payment")
    public void bobCompletesTheDepositPayment() {
        driver().get(Config.baseUrl() + "/contracts/" + contractId + "/checkout");

        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='checkout-form']")));
        fill(By.cssSelector("[data-testid='checkout-holder-name']"), "Bob User");
        fill(By.cssSelector("[data-testid='checkout-card-number']"), "4242 4242 4242 4242");
        fill(By.cssSelector("[data-testid='checkout-expiry']"), "12/30");
        fill(By.cssSelector("[data-testid='checkout-cvc']"), "123");
        click(By.cssSelector("[data-testid='checkout-submit']"));

        waitForPage().until(ExpectedConditions.urlContains("/receipt"));
        latestPaymentId = extractPaymentIdFromReceiptUrl(driver().getCurrentUrl());
        assertThat(latestPaymentId).isNotBlank();
    }

    @When("Bob pays with a valid test card")
    public void bobPaysWithAValidTestCard() {
        bobCompletesTheDepositPayment();
    }

    @Then("the contract status is AWAITING_COUNTERPARTY")
    public void theContractStatusIsAwaitingCounterparty() {
        assertContractStatus("AWAITING_COUNTERPARTY");
    }

    @When("Alice signs it using the OTP sent to her email")
    public void aliceSignsItUsingTheOtpSentToHerEmail() {
        loginAsAlice();
        driver().get(Config.baseUrl() + "/contracts/" + contractId + "/sign");
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='sign-signer-email']")));
        signContractWithOtp(aliceEmail);
    }

        @When("Alice signs within the escrow window")
        public void aliceSignsWithinTheEscrowWindow() {
                aliceSignsItUsingTheOtpSentToHerEmail();
        }

        @When("Bob sends a message {string}")
        public void bobSendsAMessage(String message) {
                loginAsBob();
            if (articleId == null || articleId.isBlank()) {
                articleId = fetchContractItemId();
            }
            String token = currentBearerToken();

            if (lastConversationId == null || lastConversationId.isBlank()) {
                String body = "{\"articleId\":\"" + articleId + "\"}";
                HttpResponse<String> createConversation = sendContractsApi("POST", "/chat/conversations", token, body);
                assertThat(createConversation.statusCode()).isEqualTo(200);
                lastConversationId = extractJsonField(createConversation.body(), "id");
                assertThat(lastConversationId).isNotBlank();
            }

            String sendBody = "{\"body\":\"" + jsonEscape(message) + "\"}";
            HttpResponse<String> sendMessage = sendContractsApi(
                    "POST",
                    "/chat/conversations/" + lastConversationId + "/messages",
                    token,
                    sendBody
            );
            assertThat(sendMessage.statusCode()).isEqualTo(201);
                lastSentMessageBody = message;
        }

    @Then("the contract status is ACTIVE")
    public void theContractStatusIsActive() {
        assertContractStatus("ACTIVE");
    }

        @Then("the payment status is {word}")
        public void thePaymentStatusIs(String expectedStatus) {
                Map<?, ?> payment = getContractPaymentByIdOrLatest();
                assertThat(Objects.toString(payment.get("status"), "")).isEqualTo(expectedStatus);
        }

        @Then("a payment receipt is available")
        public void aPaymentReceiptIsAvailable() {
                Map<?, ?> payment = getContractPaymentByIdOrLatest();
                assertThat(Objects.toString(payment.get("id"), "")).isNotBlank();
                assertThat(Objects.toString(payment.get("contractId"), "")).isEqualTo(contractId);

                driver().get(Config.baseUrl() + "/contracts/" + contractId + "/payments/" + latestPaymentId + "/receipt");
                waitForPage().until(ExpectedConditions.urlContains("/payments/" + latestPaymentId + "/receipt"));
        }

        @Then("Alice sees the message in her conversation with Bob")
        public void aliceSeesTheMessageInHerConversationWithBob() {
                loginAsAlice();
                String token = currentBearerToken();
                HttpResponse<String> listConversations = sendContractsApi("GET", "/chat/conversations", token, null);
                assertThat(listConversations.statusCode()).isEqualTo(200);
                assertThat(listConversations.body()).contains(lastConversationId);

                HttpResponse<String> listMessages = sendContractsApi(
                                "GET",
                                "/chat/conversations/" + lastConversationId + "/messages",
                                token,
                                null
                );
                assertThat(listMessages.statusCode()).isEqualTo(200);
                assertThat(listMessages.body()).contains(lastSentMessageBody);
        }

    @Then("a signed PDF is available for download")
    public void aSignedPdfIsAvailableForDownload() {
        assertThat(signedStoredContractId).isNotBlank();

        Object result = ((JavascriptExecutor) driver()).executeAsyncScript("""
                const done = arguments[0];
                const token = window.localStorage.getItem('token');
                                fetch('%s/contracts/joint-rental/download/%s', {
                  headers: token ? { Authorization: 'Bearer ' + token } : {}
                })
                  .then(async (response) => {
                    const blob = await response.blob();
                    done({ ok: response.ok, status: response.status, size: blob.size });
                  })
                  .catch((error) => done({ ok: false, error: String(error) }));
                                """.formatted(API_BASE_URL, signedStoredContractId));

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> json = (Map<?, ?>) result;
        assertThat(Boolean.TRUE.equals(json.get("ok"))).isTrue();
        assertThat(((Number) json.get("status")).intValue()).isEqualTo(200);
        assertThat(((Number) json.get("size")).intValue()).isGreaterThan(0);
    }

    private String registerVerifiedUser(String firstName) {
        String email = firstName.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        driver().get(Config.baseUrl() + "/register");
        installResponseCapture();
        clearCapturedResponses();
        fill(By.cssSelector("[data-testid='register-first-name']"), firstName);
        fill(By.cssSelector("[data-testid='register-last-name']"), LAST_NAME);
        fill(By.cssSelector("[data-testid='register-email']"), email);
        fill(By.cssSelector("[data-testid='register-id-number']"), "12345678Z");
        fill(By.cssSelector("[data-testid='register-address']"), "E2E test street 1");
        fill(By.cssSelector("[data-testid='register-password']"), PASSWORD);
        click(By.cssSelector("[data-testid='register-account-submit']"));

        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='register-otp-form']")));
        String signupOtp = captureOtp("window.__e2eAuthResponse", "otp");
        fill(By.cssSelector("[data-testid='register-otp']"), signupOtp);
        click(By.cssSelector("[data-testid='register-otp-submit']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='register-kyc-form']")));

        uploadFile(By.cssSelector("[data-testid='register-kyc-front']"), resourcePath("/fixtures/kyc-front.png"));
        uploadFile(By.cssSelector("[data-testid='register-kyc-back']"), resourcePath("/fixtures/kyc-back.png"));
        click(By.cssSelector("[data-testid='register-kyc-submit']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='register-done-panel']")));

        return email;
    }

    private void prepareContractBetweenAliceAndBob() {
        aBrowserSessionWithAuthAndContractResponseCaptureEnabled();
        aliceRegistersVerifiesAndCompletesKyc();
        alicePublishesARentalArticle();
        bobRegistersVerifiesAndCompletesKyc();
        bobStartsTheRentalContractForTheArticle();
        bobSignsItUsingTheOtpSentToHisEmail();
        theContractStatusIsPendingSignatures();
        articleId = fetchContractItemId();
    }

    private void prepareContractBetweenAliceAndBobWithConversation() {
        prepareContractBetweenAliceAndBob();
    }

    private String currentBearerToken() {
        Object token = ((JavascriptExecutor) driver()).executeScript("return window.localStorage.getItem('token');");
        return Objects.toString(token, "");
    }

    private HttpResponse<String> sendContractsApi(String method, String path, String token, String jsonBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(CONTRACTS_SERVICE_BASE_URL + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json");

            if (jsonBody != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Contracts API call failed for " + method + " " + path, e);
        }
    }

    private String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher matcher = pattern.matcher(Objects.toString(json, ""));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String jsonEscape(String text) {
        return Objects.toString(text, "").replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void loginAsAlice() {
        loginAs(aliceEmail);
    }

    private void loginAsBob() {
        loginAs(bobEmail);
    }

    private void loginAs(String email) {
        resetBrowserSession();

        driver().get(Config.loginUrl());
        installResponseCapture();
        clearCapturedResponses();
        fill(By.cssSelector("[data-testid='login-email']"), email);
        fill(By.cssSelector("[data-testid='login-password']"), PASSWORD);
        click(By.cssSelector("[data-testid='login-submit']"));

        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='login-otp-form']")));
        String loginOtp = captureOtp("window.__e2eAuthResponse", "otp");
        fill(By.cssSelector("[data-testid='login-otp']"), loginOtp);
        click(By.cssSelector("[data-testid='login-otp-submit']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1")));
    }

        private String fetchContractItemId() {
                Object response = ((JavascriptExecutor) driver()).executeAsyncScript("""
                                const done = arguments[0];
                                const token = window.localStorage.getItem('token');
                                fetch('%s/contracts/%s', {
                                    headers: token ? { Authorization: 'Bearer ' + token } : {}
                                })
                                    .then(async (response) => {
                                        const json = await response.json();
                                        done({ ok: response.ok, status: response.status, itemId: json.itemId });
                                    })
                                    .catch((error) => done({ ok: false, error: String(error) }));
                                """.formatted(API_BASE_URL, contractId));

                assertThat(response).isInstanceOf(Map.class);
                Map<?, ?> json = (Map<?, ?>) response;
                assertThat(Boolean.TRUE.equals(json.get("ok"))).isTrue();
                assertThat(((Number) json.get("status")).intValue()).isEqualTo(200);
                String item = Objects.toString(json.get("itemId"), "");
                assertThat(item).isNotBlank();
                return item;
        }

        private Map<?, ?> getContractPaymentByIdOrLatest() {
                Object response = ((JavascriptExecutor) driver()).executeAsyncScript("""
                                const done = arguments[0];
                                const token = window.localStorage.getItem('token');
                                fetch('%s/contracts/%s/payments', {
                                    headers: token ? { Authorization: 'Bearer ' + token } : {}
                                })
                                    .then(async (response) => {
                                        const json = await response.json();
                                        done({ ok: response.ok, status: response.status, payments: json });
                                    })
                                    .catch((error) => done({ ok: false, error: String(error) }));
                                """.formatted(API_BASE_URL, contractId));

                assertThat(response).isInstanceOf(Map.class);
                Map<?, ?> json = (Map<?, ?>) response;
                assertThat(Boolean.TRUE.equals(json.get("ok"))).isTrue();
                assertThat(((Number) json.get("status")).intValue()).isEqualTo(200);
                assertThat(json.get("payments")).isInstanceOf(List.class);
                List<?> payments = (List<?>) json.get("payments");
                assertThat(payments).isNotEmpty();

                Map<?, ?> selected = null;
                for (Object item : payments) {
                        if (!(item instanceof Map<?, ?> map)) {
                                continue;
                        }
                        if (latestPaymentId != null && latestPaymentId.equals(Objects.toString(map.get("id"), ""))) {
                                selected = map;
                                break;
                        }
                        if (selected == null) {
                                selected = map;
                        }
                }
                assertThat(selected).isNotNull();
                latestPaymentId = Objects.toString(selected.get("id"), "");
                assertThat(latestPaymentId).isNotBlank();
                return selected;
        }

    private void signContractWithOtp(String signerEmail) {
        installResponseCapture();
        clearCapturedResponse("window.__e2eContractResponse");

        waitForPage().until(ExpectedConditions.attributeToBe(By.cssSelector("[data-testid='sign-signer-email']"), "value", signerEmail));
        click(By.cssSelector("[data-testid='sign-request-submit']"));

        String requestOtp = captureOtp("window.__e2eContractResponse", "otp");
        fill(By.cssSelector("[data-testid='sign-otp']"), requestOtp);
        click(By.cssSelector("[data-testid='sign-confirm-submit']"));

        Object response = waitForJson("window.__e2eContractResponse", "storedContractId");
        Map<?, ?> json = (Map<?, ?>) response;
        signedStoredContractId = Objects.toString(json.get("storedContractId"), "");
        assertThat(signedStoredContractId).isNotBlank();
    }

    private void assertContractStatus(String expectedStatus) {
        Object response = ((JavascriptExecutor) driver()).executeAsyncScript("""
                const done = arguments[0];
                const token = window.localStorage.getItem('token');
                                fetch('%s/contracts/%s', {
                  headers: token ? { Authorization: 'Bearer ' + token } : {}
                })
                  .then(async (response) => {
                    const json = await response.json();
                    done({ ok: response.ok, status: response.status, contractStatus: json.status });
                  })
                  .catch((error) => done({ ok: false, error: String(error) }));
                                """.formatted(API_BASE_URL, contractId));

        assertThat(response).isInstanceOf(Map.class);
        Map<?, ?> json = (Map<?, ?>) response;
        assertThat(Boolean.TRUE.equals(json.get("ok"))).isTrue();
        assertThat(((Number) json.get("status")).intValue()).isEqualTo(200);
        assertThat(Objects.toString(json.get("contractStatus"), "")).isEqualTo(expectedStatus);
    }

    private void openArticleDetailByTitle(String title) {
        driver().get(Config.baseUrl() + "/catalog");
        installResponseCapture();
        fill(By.cssSelector("[data-testid='catalog-search-query']"), title);
        click(By.cssSelector("[data-testid='catalog-search-submit']"));

        By articleLink = By.xpath("//a[.//h3[normalize-space()='" + title + "']]");
        waitForPage().until(ExpectedConditions.elementToBeClickable(articleLink)).click();
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='article-acquire-button']")));
    }

    private void ensurePayoutIbanOnFile() {
        driver().get(Config.baseUrl() + "/settings");
        click(By.cssSelector("[data-testid='settings-tab-payments']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='settings-payments-form']")));

        fill(By.cssSelector("[data-testid='settings-iban-input']"), VALID_IBAN);
        click(By.cssSelector("[data-testid='settings-iban-submit']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='settings-iban-current']")));
    }

    private String captureOtp(String jsVar, String field) {
        Object response = waitForJson(jsVar, field);
        Map<?, ?> json = (Map<?, ?>) response;
        return Objects.toString(json.get(field), "");
    }

    private Object waitForJson(String jsVar, String field) {
        return waitForPage().until(driver -> {
            Object value = ((JavascriptExecutor) driver).executeScript("return " + jsVar + ";");
            if (value instanceof Map<?, ?> map && map.get(field) != null) {
                return value;
            }
            return null;
        });
    }

    private void clearCapturedResponse(String jsVar) {
        ((JavascriptExecutor) driver()).executeScript(jsVar + " = null;");
    }

    private void clearCapturedResponses() {
        clearCapturedResponse("window.__e2eAuthResponse");
        clearCapturedResponse("window.__e2eContractResponse");
    }

    private void installResponseCapture() {
        ((JavascriptExecutor) driver()).executeScript("""
                (function() {
                  if (window.__e2eResponseCaptureInstalled) return;
                  window.__e2eResponseCaptureInstalled = true;
                  window.__e2eAuthResponse = null;
                  window.__e2eContractResponse = null;
                  const open = XMLHttpRequest.prototype.open;
                  const send = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__e2eUrl = url;
                    return open.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener('loadend', function() {
                      try {
                        const url = String(this.__e2eUrl || '');
                        const text = this.responseText || '';
                        if (!text) return;
                        if (url.includes('/api/auth/')) {
                          window.__e2eAuthResponse = JSON.parse(text);
                        }
                        if (url.includes('/api/contracts/joint-rental/sign/request')
                            || url.includes('/api/contracts/joint-rental/sign/confirm')) {
                          window.__e2eContractResponse = JSON.parse(text);
                        }
                      } catch (err) {
                        window.__e2eContractResponse = { error: String(err) };
                      }
                    });
                    return send.apply(this, arguments);
                  };
                })();
                """);
    }

    private void resetBrowserSession() {
        driver().manage().deleteAllCookies();
        ((JavascriptExecutor) driver()).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
    }

    private void fill(By selector, String value) {
        WebElement element = waitForPage().until(ExpectedConditions.elementToBeClickable(selector));
        element.clear();
        element.sendKeys(value);
    }

    private void selectByValue(By selector, String value) {
        WebElement element = waitForPage().until(ExpectedConditions.elementToBeClickable(selector));
        new Select(element).selectByValue(value);
    }

    private void click(By selector) {
        waitForPage().until(ExpectedConditions.elementToBeClickable(selector)).click();
    }

    private void uploadFile(By selector, Path path) {
        WebElement element = waitForPage().until(ExpectedConditions.presenceOfElementLocated(selector));
        element.sendKeys(path.toAbsolutePath().toString());
    }

    private Path resourcePath(String resource) {
        try {
            if ("/fixtures/kyc-front.png".equals(resource) || "/fixtures/kyc-back.png".equals(resource)) {
                return Path.of("..", "frontend", "public", "icons", "favicon-96x96.png").toAbsolutePath().normalize();
            }
            return Path.of(Objects.requireNonNull(getClass().getResource(resource)).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve test resource " + resource, e);
        }
    }

    private String extractContractIdFromCurrentUrl() {
        String currentUrl = driver().getCurrentUrl();
        int start = currentUrl.indexOf("/contracts/");
        int end = currentUrl.indexOf("/sign", start);
        if (start < 0 || end < 0 || end <= start + 11) {
            throw new IllegalStateException("Cannot extract contract id from URL: " + currentUrl);
        }
        return currentUrl.substring(start + 11, end);
    }

    private String extractPaymentIdFromReceiptUrl(String currentUrl) {
        int start = currentUrl.indexOf("/payments/");
        int end = currentUrl.indexOf("/receipt", start);
        if (start < 0 || end < 0 || end <= start + 10) {
            throw new IllegalStateException("Cannot extract payment id from URL: " + currentUrl);
        }
        return currentUrl.substring(start + 10, end);
    }

}