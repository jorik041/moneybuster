package net.eneiluj.moneybuster.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * DBLogjob represents a single logjob from the local SQLite database with all attributes.
 */
public class DBBill implements Item, Serializable {

    // key_id, key_remoteId, key_projectid, key_payer_remoteId, key_amount, key_date, key_what
    private long id;
    private long remoteId;
    private long projectId;
    private long payerId;
    private double amount;
    private String date;
    private String what;
    // OK, ADDED, EDITED, DELETED
    private int state;

    private List<DBBillOwer> billOwers;

    public static final int STATE_OK = 0;
    public static final int STATE_ADDED = 1;
    public static final int STATE_EDITED = 2;
    public static final int STATE_DELETED = 3;

    public DBBill(long id, long remoteId, long projectId, long payerId, double amount, String date, String what, int state) {
        this.id = id;
        this.remoteId = remoteId;
        this.projectId = projectId;
        this.payerId = payerId;
        this.amount = amount;
        this.date = date;
        this.what = what;

        this.billOwers = new ArrayList<>();

        this.state = state;
    }

    public List<Long> getBillOwersIds() {
        List<Long> result = new ArrayList<>();
        for (DBBillOwer bo : billOwers) {
            result.add(bo.getMemberId());
        }
        return result;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
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

    public long getPayerId() {
        return payerId;
    }

    public void setPayerId(long payerId) {
        this.payerId = payerId;
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
                + ", " + this.payerId + ", " + this.amount + ", " + this.date + ", "
                + this.what + ", " + this.state;
    }

    @Override
    public boolean isSection() {
        return false;
    }
}
