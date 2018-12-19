package net.eneiluj.ihatemoney.util;

import android.content.Context;
import android.content.SharedPreferences;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.eneiluj.ihatemoney.android.activity.SettingsActivity;
import net.eneiluj.ihatemoney.model.CloudSession;
import net.eneiluj.ihatemoney.persistence.IHateMoneySQLiteOpenHelper;

/**
 * Provides entity classes for handling server responses with a single logjob ({@link SessionResponse}) or a list of ihatemoney ({@link SessionsResponse}).
 */
public class ServerResponse {

    public static class NotModifiedException extends IOException {
    }

    public static class SessionResponse extends ServerResponse {
        public SessionResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public CloudSession getSession(IHateMoneySQLiteOpenHelper dbHelper) throws JSONException {
            return getSessionFromJSON(new JSONArray(getContent()), dbHelper);
        }
    }

    public static class SessionsResponse extends ServerResponse {
        public SessionsResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public List<CloudSession> getSessions(IHateMoneySQLiteOpenHelper dbHelper) throws JSONException {
            List<CloudSession> sessionsList = new ArrayList<>();
            //JSONObject topObj = new JSONObject(getTitle());
            JSONArray sessions = new JSONArray(getContent());
            for (int i = 0; i < sessions.length(); i++) {
                JSONArray json = sessions.getJSONArray(i);
                // if session is not shared
                if (json.length() > 4) {
                    sessionsList.add(getSessionFromJSON(json, dbHelper));
                }
            }
            return sessionsList;
        }
    }

    public static class ShareDeviceResponse extends ServerResponse {
        public ShareDeviceResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public String getPublicToken() throws JSONException {
            return getPublicTokenFromJSON(new JSONObject(getContent()));
        }
    }

    private final PhoneTrackClient.ResponseData response;

    public ServerResponse(PhoneTrackClient.ResponseData response) {
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

    protected CloudSession getSessionFromJSON(JSONArray json, IHateMoneySQLiteOpenHelper dbHelper) throws JSONException {
        String name = "";
        String token = "";
        if (json.length() > 1) {
            name = json.getString(0);
            token = json.getString(1);
        }

        Context appContext = dbHelper.getContext().getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext.getApplicationContext());
        String url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS);
        return new CloudSession(name, token, url);
    }
}
