package net.eneiluj.ihatemoney.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DBLogjob represents a single logjob from the local SQLite database with all attributes.
 */
public class DBBill implements Serializable {

    // key_id, key_remoteId, key_projectid, key_payer_remoteId, key_amount, key_date, key_what
    private long id;
    private long remoteId;
    private long projectId;
    private long payerRemoteId;
    private double amount;
    private String date;
    private String what;

    private List<DBBillOwer> billOwers;

    public DBBill(long id, long remoteId, long projectId, long payerRemoteId, double amount, String date, String what) {
        this.id = id;
        this.remoteId = remoteId;
        this.projectId = projectId;
        this.payerRemoteId = payerRemoteId;
        this.amount = amount;
        this.date = date;
        this.what = what;

        this.billOwers = new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public List<DBBillOwer> getBillOwers() {
        return billOwers;
    }

    public void setBillOwers(List<DBBillOwer> billOwers) {
        this.billOwers = billOwers;
    }

    public long getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(long remoteId) {
        this.remoteId = remoteId;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public long getPayerRemoteId() {
        return payerRemoteId;
    }

    public void setPayerRemoteId(long payerRemoteId) {
        this.payerRemoteId = payerRemoteId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getWhat() {
        return what;
    }

    public void setWhat(String what) {
        this.what = what;
    }

    @Override
    public String toString() {
        // key_id, key_remoteId, key_projectid, key_payer_remoteId, key_amount, key_date, key_what
        return "#DBBill" + getId() + "/" + this.remoteId + "," + this.projectId
                + ", " + this.payerRemoteId + ", " + this.amount + ", " + this.date + ", " + this.what;
    }
}
