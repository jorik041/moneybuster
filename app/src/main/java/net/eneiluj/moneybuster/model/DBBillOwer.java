package net.eneiluj.moneybuster.model;

import java.io.Serializable;

public class DBBillOwer implements Serializable {

    // key_id, key_billId, key_member_remoteId
    private long id;
    private long billId;
    private long memberId;

    public DBBillOwer(long id, long billId, long memberId) {
        this.id = id;
        this.billId = billId;
        this.memberId = memberId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getBillId() {
        return billId;
    }

    public void setBillId(long billId) {
        this.billId = billId;
    }

    public long getMemberId() {
        return memberId;
    }

    public void setMemberId(long memberId) {
        this.memberId = memberId;
    }

    @Override
    public String toString() {
        return "#DBBillOwer" + getId() + "/" + this.billId + "," + this.memberId;
    }
}
