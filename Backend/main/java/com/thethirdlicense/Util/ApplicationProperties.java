package com.thethirdlicense.Util;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class ApplicationProperties {
    private double currencyExchangeRate;

    public double getCurrencyExchangeRate() {
        return currencyExchangeRate;
    }

    public void setCurrencyExchangeRate(double currencyExchangeRate) {
        this.currencyExchangeRate = currencyExchangeRate;
    }
}
