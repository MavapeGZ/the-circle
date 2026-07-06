package com.thecircle.e2e.steps;

import com.thecircle.e2e.support.Config;
import com.thecircle.e2e.support.Hooks;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalogFlowSteps {
    private static final String VALID_IBAN = "ES91 2100 0418 4502 0005 1332";
    private final String publishedTitle = "E2E Catalog " + UUID.randomUUID().toString().substring(0, 8);

    private WebDriver driver() {
        return Hooks.WORLD.driver();
    }

    private WebDriverWait waitForPage() {
        return new WebDriverWait(driver(), Duration.ofSeconds(Config.timeoutSeconds()));
    }

    @When("I publish a sale article through the UI")
    public void iPublishASaleArticleThroughTheUi() {
        driver().get(Config.baseUrl() + "/create");

        fill("[data-testid='article-title']", publishedTitle);
        fill("[data-testid='article-description']", "E2E catalog article");
        fill("[data-testid='article-price']", "19.95");
        click("[data-testid='article-submit']");

        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='catalog-search-form']")));
    }

    @Given("I have a payout IBAN on file")
    public void iHaveAPayoutIbanOnFile() {
        driver().get(Config.baseUrl() + "/settings");
        waitForPage().until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='settings-tab-payments']"))).click();
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='settings-payments-form']")));

        var ibanInput = waitForPage().until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='settings-iban-input']")));
        ibanInput.clear();
        ibanInput.sendKeys(VALID_IBAN);

        waitForPage().until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='settings-iban-submit']"))).click();
        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='settings-iban-current']")));
    }

    @Then("the article can be found in the catalog search")
    public void theArticleCanBeFoundInTheCatalogSearch() {
        driver().get(Config.baseUrl() + "/catalog");
        fill("[data-testid='catalog-search-query']", publishedTitle);
        click("[data-testid='catalog-search-submit']");

        waitForPage().until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h3[normalize-space()='" + publishedTitle + "']")));
        assertThat(driver().findElement(By.xpath("//h3[normalize-space()='" + publishedTitle + "']")).getText())
                .isEqualTo(publishedTitle);
    }

    private void fill(String selector, String value) {
        var element = waitForPage().until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
        element.clear();
        element.sendKeys(value);
    }

    private void click(String selector) {
        waitForPage().until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector))).click();
    }

}