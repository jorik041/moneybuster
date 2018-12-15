package net.eneiluj.nextcloud.phonetrack.model;

import android.util.Log;

import java.io.Serializable;

/**
 * DBLogjob represents a single logjob from the local SQLite database with all attributes.
 */
public class DBLogjob implements Item, Serializable {

    private long id;
    private String title;
    private String url;
    private String token;
    private String deviceName;
    private boolean post;
    private int minTime;
    private int minDistance;
    private int minAccuracy;
    private Boolean enabled;
    private int nbSync;

    public DBLogjob(long id, String title, String url, String token, String deviceName, int minTime, int minDistance, int minAccuracy, boolean post, Boolean enabled, int nbSync) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.token = token;
        this.deviceName = deviceName;
        this.post = post;
        this.minAccuracy = minAccuracy;
        this.minDistance = minDistance;
        this.minTime = minTime;
        this.enabled = enabled;
        this.nbSync = nbSync;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setNbSync(int nbSync) {
        this.nbSync = nbSync;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public void setPost(boolean post) {
        this.post = post;
    }

    public boolean setAttrFromLoggingUrl(String loggingUrl) {
        boolean worked = false;
        String[] spl = loggingUrl.split("/apps/phonetrack/");
        if (spl.length == 2) {
            String nextURL = spl[0];
            if (nextURL.contains("index.php")) {
                nextURL = nextURL.replace("index.php", "");
            }

            String right = spl[1];
            String[] spl2 = right.split("/");
            if (spl2.length > 2) {
                String token;
                String[] splEnd;
                // example .../apps/phonetrack/logGet/token/devname?lat=0.1...
                if (spl2.length == 3) {
                    token = spl2[1];
                    splEnd = spl2[2].split("\\?");
                }
                // example .../apps/phonetrack/log/osmand/token/devname?lat=0.1...
                else {
                    token = spl2[2];
                    splEnd = spl2[3].split("\\?");
                }
                String devname = splEnd[0];
                this.title = "From PhoneTrack logging URL";
                this.deviceName = devname;
                this.token = token;
                this.url = nextURL;
                worked = true;
            }
        }
        return worked;
    }

    public String getToken() {
        return token;
    }

    public int getNbSync() {
        return nbSync;
    }

    public String getUrl() {
        return url;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public boolean getPost() {
        return post;
    }

    public int getMinTime() {
        return minTime;
    }
    public int getMinDistance() {
        return minDistance;
    }
    public int getMinAccuracy() {
        return minAccuracy;
    }

    public Boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isSection() {
        return false;
    }

    @Override
    public String toString() {
        return "#DBLogjob" + getId() + "/" + this.title + ", " + this.enabled + ", " +
                this.url + ", " + this.token + ", " +
                this.deviceName;
    }
}
