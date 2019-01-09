package net.eneiluj.moneybuster.model;

public class CreditDebt {

    private long memberId;
    private double balance;

    public CreditDebt(long memberId, double balance) {
        this.memberId = memberId;
        this.balance = balance;
    }

    public long getMemberId() {
        return memberId;
    }

    public void setMemberId(long memberId) {
        this.memberId = memberId;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
