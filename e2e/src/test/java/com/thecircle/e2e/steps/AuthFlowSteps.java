package com.thecircle.e2e.steps;

import com.thecircle.e2e.support.Config;
import com.thecircle.e2e.support.Hooks;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthFlowSteps {
    private static final String FIRST_NAME = "Alice";
    private static final String LAST_NAME = "User";
    private static final String PASSWORD = "Test1234!";

    private String email;
    private String lastOtp;

    private WebDriver driver() {
        return Hooks.WORLD.driver();
    }

    private WebDriverWait waitForPage() {
        return new WebDriverWait(driver(), Duration.ofSeconds(Config.timeoutSeconds()));
    }

    @Given("a browser session with auth response capture enabled")
    public void aBrowserSessionWithAuthResponseCaptureEnabled() {
        installAuthResponseCapture();
        driver().get(Config.baseUrl());
    }

    @When("I register a fresh user through the UI")
    public void iRegisterAFreshUserThroughTheUi() {
        email = "e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        driver().get(Config.baseUrl() + "/register");
        installAuthResponseCapture();

        fill(By.cssSelector("[data-testid='register-first-name']"), FIRST_NAME);
        fill(By.cssSelector("[data-testid='register-last-name']"), LAST_NAME);
        fill(By.cssSelector("[data-testid='register-email']"), email);
        fill(By.cssSelector("[data-testid='register-id-number']"), "12345678Z");
        fill(By.cssSelector("[data-testid='register-address']"), "E2E test street 1");
        fill(By.cssSelector("[data-testid='register-password']"), PASSWORD);
        click(By.cssSelector("[data-testid='register-account-submit']"));

        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='register-otp-form']")));
        lastOtp = captureOtp();
        assertThat(lastOtp).isNotBlank();
    }

    @When("I verify the signup code")
    public void iVerifyTheSignupCode() {
        fill(By.cssSelector("[data-testid='register-otp']"), lastOtp);
        click(By.cssSelector("[data-testid='register-otp-submit']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='register-kyc-form']")));
    }

    @When("I upload valid identity documents")
    public void iUploadValidIdentityDocuments() {
        uploadFile(By.cssSelector("[data-testid='register-kyc-front']"), resourcePath("/fixtures/kyc-front.png"));
        uploadFile(By.cssSelector("[data-testid='register-kyc-back']"), resourcePath("/fixtures/kyc-back.png"));
        click(By.cssSelector("[data-testid='register-kyc-submit']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='register-done-panel']")));
    }

    @Then("the registration flow finishes successfully")
    public void theRegistrationFlowFinishesSuccessfully() {
        assertThat(driver().findElement(By.cssSelector("[data-testid='register-done-panel']"))).isNotNull();
    }

    @Given("the user is logged out")
    public void theUserIsLoggedOut() {
        driver().manage().deleteAllCookies();
        ((JavascriptExecutor) driver()).executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
    }

    @When("I sign in with the same account")
    public void iSignInWithTheSameAccount() {
        driver().get(Config.loginUrl());
        installAuthResponseCapture();
        fill(By.cssSelector("[data-testid='login-email']"), email);
        fill(By.cssSelector("[data-testid='login-password']"), PASSWORD);
        click(By.cssSelector("[data-testid='login-submit']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='login-otp-form']")));
        lastOtp = captureOtp();
        assertThat(lastOtp).isNotBlank();
    }

    @When("I confirm the login code")
    public void iConfirmTheLoginCode() {
        fill(By.cssSelector("[data-testid='login-otp']"), lastOtp);
        click(By.cssSelector("[data-testid='login-otp-submit']"));
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1")));
    }

    @Then("the home page is shown")
    public void theHomePageIsShown() {
        assertThat(driver().findElement(By.cssSelector("h1")).getText()).contains("The Circle");
    }

    private void installAuthResponseCapture() {
        ((JavascriptExecutor) driver()).executeScript("""
                (function() {
                  if (window.__e2eAuthCaptureInstalled) return;
                  window.__e2eAuthCaptureInstalled = true;
                  window.__e2eAuthResponse = null;
                  const open = XMLHttpRequest.prototype.open;
                  const send = XMLHttpRequest.prototype.send;
                  XMLHttpRequest.prototype.open = function(method, url) {
                    this.__e2eUrl = url;
                    return open.apply(this, arguments);
                  };
                  XMLHttpRequest.prototype.send = function() {
                    this.addEventListener('loadend', function() {
                      try {
                        if (this.__e2eUrl && String(this.__e2eUrl).includes('/api/auth/')) {
                          const text = this.responseText || '';
                          if (text) {
                            window.__e2eAuthResponse = JSON.parse(text);
                          }
                        }
                      } catch (err) {
                        window.__e2eAuthResponse = { error: String(err) };
                      }
                    });
                    return send.apply(this, arguments);
                  };
                })();
                """);
    }

    private String captureOtp() {
        Object response = waitForPage().until(driver -> {
            Object value = ((JavascriptExecutor) driver).executeScript("return window.__e2eAuthResponse;");
            if (value instanceof Map<?, ?> map && map.get("otp") != null) {
                return value;
            }
            return null;
        });
        Map<?, ?> json = (Map<?, ?>) response;
        return Objects.toString(json.get("otp"), "");
    }

    private void fill(By selector, String value) {
        WebElement element = waitForPage().until(ExpectedConditions.elementToBeClickable(selector));
        element.clear();
        element.sendKeys(value);
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
}