package net.eneiluj.moneybuster.model;

public class Transaction {

    private long owerMemberId;
    private long receiverMemberId;
    private double amount;

    public Transaction(long owerMemberId, long receiverMemberId, double amount) {
        this.owerMemberId = owerMemberId;
        this.receiverMemberId = receiverMemberId;
        this.amount = amount;
    }

    public long getOwerMemberId() {
        return owerMemberId;
    }

    public void setOwerMemberId(long owerMemberId) {
        this.owerMemberId = owerMemberId;
    }

    public long getReceiverMemberId() {
        return receiverMemberId;
    }

    public void setReceiverMemberId(long receiverMemberId) {
        this.receiverMemberId = receiverMemberId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}

