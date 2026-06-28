package com.thecircle.e2e.support;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public final class WebDriverFactory {
    private WebDriverFactory() {
    }

    public static WebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        if (headless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1280,900");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        return new ChromeDriver(options);
    }

    private static boolean headless() {
        String value = System.getenv("E2E_HEADLESS");
        return value == null || value.isBlank() || Boolean.parseBoolean(value);
    }
}