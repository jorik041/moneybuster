package net.eneiluj.ihatemoney.util;

//import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

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
    }

    public static class EditRemoteProjectResponse extends ServerResponse {
        public EditRemoteProjectResponse(IHateMoneyClient.ResponseData response) {
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
}
