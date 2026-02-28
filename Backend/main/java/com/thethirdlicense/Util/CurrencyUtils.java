package com.thethirdlicense.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CurrencyUtils {

    @Autowired
    private ApplicationProperties applicationProperties;

    // Calculate currency earned based on KB of code
    public double calculateEarnings(int codeSizeKb) {
        return codeSizeKb * applicationProperties.getCurrencyExchangeRate();
    }
}
