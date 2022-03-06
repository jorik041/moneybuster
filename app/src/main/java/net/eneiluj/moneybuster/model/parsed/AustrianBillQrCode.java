package net.eneiluj.moneybuster.model.parsed;

import java.util.Date;

public class AustrianBillQrCode {
    private String cashDeskId;
    private Date date;
    private double amount;

    public AustrianBillQrCode(String cashDeskId, Date date, double amount) {
        this.cashDeskId = cashDeskId;
        this.date = date;
        this.amount = amount;
    }

    public String getCashDeskId() {
        return cashDeskId;
    }

    public Date getDate() {
        return date;
    }

    public double getAmount() {
        return amount;
    }
}
