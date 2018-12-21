package net.eneiluj.ihatemoney.model;

import java.io.Serializable;

/**
 * DBLogjob represents a single logjob from the local SQLite database with all attributes.
 */
public class DBBillOwer implements Serializable {

    // key_id, key_billId, key_member_remoteId
    private long id;
    private long billId;
    private long memberRemoteId;

    public DBBillOwer(long id, long billId, long memberRemoteId) {
        this.id = id;
        this.billId = billId;
        this.memberRemoteId = memberRemoteId;
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

    public long getMemberRemoteId() {
        return memberRemoteId;
    }

    public void setMemberRemoteId(long memberRemoteId) {
        this.memberRemoteId = memberRemoteId;
    }

    @Override
    public String toString() {
        return "#DBBillOwer" + getId() + "/" + this.billId + "," + this.memberRemoteId;
    }
}
