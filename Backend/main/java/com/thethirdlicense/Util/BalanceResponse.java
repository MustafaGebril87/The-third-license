package com.thethirdlicense.Util;
import java.math.BigDecimal;

public class BalanceResponse {
    private double balance;

    public BalanceResponse(double balance2) {
        this.balance = balance2;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
