package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.content.SharedPreferences;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;
import net.eneiluj.nextcloud.phonetrack.model.CloudSession;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

/**
 * Provides entity classes for handling server responses with a single logjob ({@link SessionResponse}) or a list of phonetrack ({@link SessionsResponse}).
 */
public class ServerResponse {

    public static class NotModifiedException extends IOException {
    }

    public static class SessionResponse extends ServerResponse {
        public SessionResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public CloudSession getSession(PhoneTrackSQLiteOpenHelper dbHelper) throws JSONException {
            return getSessionFromJSON(new JSONArray(getContent()), dbHelper);
        }
    }

    public static class SessionsResponse extends ServerResponse {
        public SessionsResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public List<CloudSession> getSessions(PhoneTrackSQLiteOpenHelper dbHelper) throws JSONException {
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

    protected CloudSession getSessionFromJSON(JSONArray json, PhoneTrackSQLiteOpenHelper dbHelper) throws JSONException {
        //long id = 0;
        String name = "";
        String token = "";
        if (json.length() > 1) {
            name = json.getString(0);
            token = json.getString(1);
        }
        /*if (!json.isNull(PhoneTrackClient.JSON_ID)) {
            id = json.getLong(PhoneTrackClient.JSON_ID);
        }
        if (!json.isNull(PhoneTrackClient.JSON_TITLE)) {
            title = json.getString(PhoneTrackClient.JSON_TITLE);
        }
        if (!json.isNull(PhoneTrackClient.JSON_CONTENT)) {
            content = json.getString(PhoneTrackClient.JSON_CONTENT);
        }
        if (!json.isNull(PhoneTrackClient.JSON_MODIFIED)) {
            modified = GregorianCalendar.getInstance();
            modified.setTimeInMillis(json.getLong(PhoneTrackClient.JSON_MODIFIED) * 1000);
        }
        if (!json.isNull(PhoneTrackClient.JSON_FAVORITE)) {
            favorite = json.getBoolean(PhoneTrackClient.JSON_FAVORITE);
        }
        if (!json.isNull(PhoneTrackClient.JSON_CATEGORY)) {
            category = json.getString(PhoneTrackClient.JSON_CATEGORY);
        }
        if (!json.isNull(PhoneTrackClient.JSON_ETAG)) {
            etag = json.getString(PhoneTrackClient.JSON_ETAG);
        }
        return new CloudSession(id, modified, title, content, favorite, category, etag);
        */

        Context appContext = dbHelper.getContext().getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext.getApplicationContext());
        String url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS);
        return new CloudSession(name, token, url);
    }
}
