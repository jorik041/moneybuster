package net.eneiluj.ihatemoney.util;

//import android.preference.PreferenceManager;

import net.eneiluj.ihatemoney.model.DBBill;
import net.eneiluj.ihatemoney.model.DBBillOwer;
import net.eneiluj.ihatemoney.model.DBMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides entity classes for handling server responses with a single logjob ({@link ProjectResponse}) or a list of ihatemoney ({@link SessionsResponse}).
 */
public class ServerResponse {

    public static class NotModifiedException extends IOException {
    }

    public static class ProjectResponse extends ServerResponse {
        public ProjectResponse(IHateMoneyClient.ResponseData response) {
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

    public static class EditRemoteProjectResponse extends ServerResponse {
        public EditRemoteProjectResponse(IHateMoneyClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class DeleteRemoteBillResponse extends ServerResponse {
        public DeleteRemoteBillResponse(IHateMoneyClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class DeleteRemoteProjectResponse extends ServerResponse {
        public DeleteRemoteProjectResponse(IHateMoneyClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class CreateRemoteProjectResponse extends ServerResponse {
        public CreateRemoteProjectResponse(IHateMoneyClient.ResponseData response) {
            super(response);
        }

        public String getStringContent() {
            return getContent();
        }
    }

    public static class BillsResponse extends ServerResponse {
        public BillsResponse(IHateMoneyClient.ResponseData response) {
            super(response);
        }

        public List<DBBill> getBills(long projId) throws JSONException {
            return getBillsFromJSON(new JSONArray(getContent()), projId);
        }
    }

    private final IHateMoneyClient.ResponseData response;

    public ServerResponse(IHateMoneyClient.ResponseData response) {
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
        return new DBMember(0, remoteId, projId, name, activated, weight);
    }

    protected List<DBBill> getBillsFromJSON(JSONArray json, long projId) throws JSONException {
        List<DBBill> bills = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            JSONObject jsonB = json.getJSONObject(i);
            bills.add(getBillFromJSON(jsonB, projId));
        }
        return bills;
    }

    protected DBBill getBillFromJSON(JSONObject json, long projId) throws JSONException {
        long remoteId = 0;
        long payerRemoteId = 0;
        double amount = 0;
        String date = "";
        String what = "";
        if (!json.isNull("id")) {
            remoteId = json.getLong("id");
        }
        if (!json.isNull("payer_id")) {
            payerRemoteId = json.getLong("payer_id");
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
        DBBill bill = new DBBill(0, remoteId, projId, payerRemoteId, amount, date, what, DBBill.STATE_OK);
        bill.setBillOwers(getBillOwersFromJson(json));
        return bill;
    }

    protected List<DBBillOwer> getBillOwersFromJson(JSONObject json) throws JSONException {
        List<DBBillOwer> billOwers = new ArrayList<>();

        if (json.has("owers")) {
            JSONArray jsonOs = json.getJSONArray("owers");
            for (int i = 0; i < jsonOs.length(); i++) {
                JSONObject jsonO = jsonOs.getJSONObject(i);
                billOwers.add(new DBBillOwer(0,0, jsonO.getLong("id")));
            }
        }
        return billOwers;
    }
}
