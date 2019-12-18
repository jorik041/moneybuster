package net.eneiluj.moneybuster.util;

//import android.preference.PreferenceManager;

import android.util.Log;

import net.eneiluj.moneybuster.model.DBAccountProject;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBBillOwer;
import net.eneiluj.moneybuster.model.DBMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides entity classes for handling server responses
 */
public class ServerResponse {

    public static class NotModifiedException extends IOException {
    }

    public static class ProjectResponse extends ServerResponse {
        public ProjectResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public String getEmail() throws JSONException {
            return getEmailFromJSON(new JSONObject(getContent()));
        }

        public String getName() throws JSONException {
            return getNameFromJSON(new JSONObject(getContent()));
        }

        public List<DBMember> getMembers(long projId) throws JSONException {
            return getMembersFromJSON(new JSONObject(getContent()), projId);
        }
    }

    public static class CreateRemoteMemberResponse extends ServerResponse {
        public CreateRemoteMemberResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class EditRemoteProjectResponse extends ServerResponse {
        public EditRemoteProjectResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class EditRemoteMemberResponse extends ServerResponse {
        public EditRemoteMemberResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public long getRemoteId(long projectId) throws JSONException {
            return getMemberFromJSON(new JSONObject(getContent()), projectId).getRemoteId();
        }
    }

    public static class EditRemoteBillResponse extends ServerResponse {
        public EditRemoteBillResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class CreateRemoteBillResponse extends ServerResponse {
        public CreateRemoteBillResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class DeleteRemoteBillResponse extends ServerResponse {
        public DeleteRemoteBillResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class DeleteRemoteProjectResponse extends ServerResponse {
        public DeleteRemoteProjectResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class CreateRemoteProjectResponse extends ServerResponse {
        public CreateRemoteProjectResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class BillsResponse extends ServerResponse {
        public BillsResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public List<DBBill> getBillsIHM(long projId, Map<Long, Long> memberRemoteIdToId) throws JSONException {
            return getBillsFromJSONArray(new JSONArray(getContent()), projId, memberRemoteIdToId);
        }

        public List<DBBill> getBillsCospend(long projId, Map<Long, Long> memberRemoteIdToId) throws JSONException {
            return getBillsFromJSONObject(new JSONObject(getContent()), projId, memberRemoteIdToId);
        }

        public List<Long> getAllBillIds() throws JSONException {
            return getAllBillIdsFromJSON(new JSONObject(getContent()));
        }

        public Long getSyncTimestamp() throws JSONException {
            return getSyncTimestampFromJSON(new JSONObject(getContent()));
        }
    }

    public static class MembersResponse extends ServerResponse {
        public MembersResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public List<DBMember> getMembers(long projId) throws JSONException {
            return getMembersFromJSONArray(new JSONArray(getContent()), projId);
        }
    }

    public static class AccountProjectsResponse extends ServerResponse {
        public AccountProjectsResponse(VersatileProjectSyncClient.ResponseData response) {
            super(response);
        }

        public List<DBAccountProject> getAccountProjects(String ncUrl) throws JSONException {
            return getAccountProjectsFromJSONArray(new JSONArray(getContent()), ncUrl);
        }
    }

    private final VersatileProjectSyncClient.ResponseData response;

    public ServerResponse(VersatileProjectSyncClient.ResponseData response) {
        this.response = response;
    }

    protected String getContent() {
        return response.getContent();
    }

    public String getETag() {
        return response.getETag();
    }

    public long getLastModified() {
        return response.getLastModified();
    }

    protected String getPublicTokenFromJSON(JSONObject json) throws JSONException {
        int done = 0;
        String publictoken;
        if (json.has("code") && json.has("sharetoken")) {
            done = json.getInt("code");
            publictoken = json.getString("sharetoken");
            if (done == 1) {
                return publictoken;
            }
        }
        return null;
    }

    protected String getNameFromJSON(JSONObject json) throws JSONException {
        String name = "";
        if (json.has("name")) {
            name = json.getString("name");
        }
        return name;
    }

    protected String getEmailFromJSON(JSONObject json) throws JSONException {
        String email = "";
        if (json.has("contact_email")) {
            email = json.getString("contact_email");
        }
        return email;
    }

    protected List<DBMember> getMembersFromJSONArray(JSONArray jsonMs, long projId) throws JSONException {
        List<DBMember> members = new ArrayList<>();
        for (int i = 0; i < jsonMs.length(); i++) {
            JSONObject jsonM = jsonMs.getJSONObject(i);
            members.add(getMemberFromJSON(jsonM, projId));
        }

        return members;
    }

    protected List<DBMember> getMembersFromJSON(JSONObject json, long projId) throws JSONException {
        List<DBMember> members = new ArrayList<>();

        if (json.has("members")) {
            JSONArray jsonMs = json.getJSONArray("members");
            for (int i = 0; i < jsonMs.length(); i++) {
                JSONObject jsonM = jsonMs.getJSONObject(i);
                members.add(getMemberFromJSON(jsonM, projId));
            }
        }
        return members;
    }

    protected DBMember getMemberFromJSON(JSONObject json, long projId) throws JSONException {
        boolean activated = true;
        double weight = 1;
        long remoteId = 0;
        String name = "";
        Integer r = null;
        Integer g = null;
        Integer b = null;
        if (!json.isNull("id")) {
            remoteId = json.getLong("id");
        }
        if (!json.isNull("weight")) {
            weight = json.getDouble("weight");
        }
        if (!json.isNull("activated")) {
            activated = json.getBoolean("activated");
        }
        if (!json.isNull("name")) {
            name = json.getString("name");
        }
        if (json.has("color") && !json.isNull("color")) {
            JSONObject color = json.getJSONObject("color");
            if (color.has("r") && !color.isNull("r")) {
                r = color.getInt("r");
            }
            if (color.has("g") && !color.isNull("g")) {
                g = color.getInt("g");
            }
            if (color.has("b") && !color.isNull("b")) {
                b = color.getInt("b");
            }
        }
        return new DBMember(0, remoteId, projId, name, activated, weight, DBBill.STATE_OK, r, g, b);
    }

    protected List<Long> getAllBillIdsFromJSON(JSONObject json) throws JSONException {
        List<Long> billIds = new ArrayList<>();
        if (json.has("allBillIds") && !json.isNull("allBillIds")) {
            JSONArray jsonBillIds = json.getJSONArray("allBillIds");
            for (int i = 0; i < jsonBillIds.length(); i++) {
                billIds.add(jsonBillIds.getLong(i));
            }
        }
        return billIds;
    }

    protected Long getSyncTimestampFromJSON(JSONObject json) throws JSONException {
        Long ts = Long.valueOf(0);
        if (json.has("timestamp") && !json.isNull("timestamp")) {
            ts = json.getLong("timestamp");
        }
        return ts;
    }

    protected List<DBBill> getBillsFromJSONArray(JSONArray json, long projId, Map<Long, Long> memberRemoteIdToId) throws JSONException {
        List<DBBill> bills = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            JSONObject jsonBill = json.getJSONObject(i);
            bills.add(getBillFromJSON(jsonBill, projId, memberRemoteIdToId));
        }
        return bills;
    }

    protected List<DBBill> getBillsFromJSONObject(JSONObject json, long projId, Map<Long, Long> memberRemoteIdToId) throws JSONException {
        List<DBBill> bills;
        if (json.has("bills") && !json.isNull("bills")) {
            JSONArray jsonBills = json.getJSONArray("bills");
            bills = getBillsFromJSONArray(jsonBills, projId, memberRemoteIdToId);
        }
        else {
            bills = new ArrayList<>();
        }
        return bills;
    }

    protected DBBill getBillFromJSON(JSONObject json, long projId, Map<Long, Long> memberRemoteIdToId) throws JSONException {
        long remoteId = 0;
        long payerRemoteId = 0;
        long payerId = 0;
        double amount = 0;
        String date = "";
        String what = "";
        String repeat = DBBill.NON_REPEATED;
        String paymentMode = DBBill.PAYMODE_NONE;
        int categoryId = DBBill.CATEGORY_NONE;
        if (!json.isNull("id")) {
            remoteId = json.getLong("id");
        }
        if (!json.isNull("payer_id")) {
            payerRemoteId = json.getLong("payer_id");
            payerId = memberRemoteIdToId.get(payerRemoteId);
            //Log.w(ServerResponse.class.getSimpleName(), "WTF : "+payerId);
        }
        if (!json.isNull("amount")) {
            amount = json.getDouble("amount");
        }
        if (!json.isNull("date")) {
            date = json.getString("date");
        }
        if (!json.isNull("what")) {
            what = json.getString("what");
        }
        if (json.has("repeat") && !json.isNull("repeat")) {
            repeat = json.getString("repeat");
        }
        if (json.has("paymentmode") && !json.isNull("paymentmode")) {
            paymentMode = json.getString("paymentmode");
        }
        if (json.has("categoryid") && !json.isNull("categoryid")) {
            categoryId = json.getInt("categoryid");
            Log.d("PLOP", "LOADED CATTTTTTTTTTTT " + categoryId);
        }
        DBBill bill = new DBBill(0, remoteId, projId, payerId, amount, date, what,
                DBBill.STATE_OK, repeat, paymentMode, categoryId);
        bill.setBillOwers(getBillOwersFromJson(json, memberRemoteIdToId));
        return bill;
    }

    protected List<DBBillOwer> getBillOwersFromJson(JSONObject json, Map<Long, Long> memberRemoteIdToId) throws JSONException {
        List<DBBillOwer> billOwers = new ArrayList<>();

        if (json.has("owers")) {
            JSONArray jsonOs = json.getJSONArray("owers");
            for (int i = 0; i < jsonOs.length(); i++) {
                JSONObject jsonO = jsonOs.getJSONObject(i);
                long memberRemoteId = jsonO.getLong("id");
                long memberLocalId = memberRemoteIdToId.get(memberRemoteId);
                billOwers.add(new DBBillOwer(0,0, memberLocalId));
            }
        }
        return billOwers;
    }

    protected List<DBAccountProject> getAccountProjectsFromJSONArray(JSONArray jsonAPs, String ncUrl) throws JSONException {
        List<DBAccountProject> accountProjects = new ArrayList<>();
        for (int i = 0; i < jsonAPs.length(); i++) {
            JSONObject jsonAP = jsonAPs.getJSONObject(i);
            accountProjects.add(getAccountProjectFromJSON(jsonAP, ncUrl));
        }

        return accountProjects;
    }

    protected DBAccountProject getAccountProjectFromJSON(JSONObject json, String accountNcUrl) throws JSONException {
        String remoteId = "";
        String name = "";
        String ncUrl = "";

        if (!json.isNull("name")) {
            name = json.getString("name");
        }
        if (!json.isNull("id")) {
            remoteId = json.getString("id");
        }
        if (!json.isNull("ncurl")) {
            ncUrl = json.getString("ncUrl");
        }
        if (ncUrl.isEmpty()) {
            ncUrl = accountNcUrl;
        }
        return new DBAccountProject(0, remoteId, null, name, ncUrl);
    }
}
