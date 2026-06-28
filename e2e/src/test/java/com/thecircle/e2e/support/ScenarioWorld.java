package com.thecircle.e2e.support;

import org.openqa.selenium.WebDriver;

public final class ScenarioWorld {
    private WebDriver driver;

    public WebDriver driver() {
        return driver;
    }

    public void driver(WebDriver driver) {
        this.driver = driver;
    }
}