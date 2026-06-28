package com.thecircle.e2e.support;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

public class Hooks {
    public static final ScenarioWorld WORLD = new ScenarioWorld();

    @Before
    public void beforeScenario() {
        WebDriver driver = WebDriverFactory.createChromeDriver();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(Config.timeoutSeconds()));
        WORLD.driver(driver);
    }

    @After
    public void afterScenario() {
        WebDriver driver = WORLD.driver();
        if (driver != null) {
            driver.quit();
            WORLD.driver(null);
        }
    }
}