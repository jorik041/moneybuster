package net.eneiluj.ihatemoney.model;

/**
 * DBLocation represents a location from the local SQLite database with all attributes.
 * key_id, key_logjobid, key_lat, key_lon, 4 key_time, 5 key_bearing,
 * 6 key_altitude, 7 key_speed, 8 key_accuracy, 9 key_satellites, 10 key_battery
 */
public class DBLocation {

    private long id;
    private long logjobId;
    private float lat;
    private float lon;
    private int timestamp;
    private float bearing;
    private float altitude;
    private float speed;
    private float accuracy;
    private int satellites;
    private float battery;

    public DBLocation(long id, long logjobId, float lat, float lon, int timestamp, float bearing,
                      float altitude, float speed, float accuracy, int satellites, float battery) {
        this.id = id;
        this.logjobId = logjobId;
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
        this.bearing = bearing;
        this.altitude = altitude;
        this.speed = speed;
        this.accuracy = accuracy;
        this.satellites = satellites;
        this.battery = battery;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getLogjobId() {
        return logjobId;
    }

    public void setLogjobId(long logjobId) {
        this.logjobId = logjobId;
    }

    public float getLat() {
        return lat;
    }

    public void setLat(float lat) {
        this.lat = lat;
    }

    public float getLon() {
        return lon;
    }

    public void setLon(float lon) {
        this.lon = lon;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public float getBearing() {
        return bearing;
    }

    public void setBearing(float bearing) {
        this.bearing = bearing;
    }

    public float getAltitude() {
        return altitude;
    }

    public void setAltitude(float altitude) {
        this.altitude = altitude;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public int getSatellites() {
        return satellites;
    }

    public void setSatellites(int satellites) {
        this.satellites = satellites;
    }

    public float getBattery() {
        return battery;
    }

    public void setBattery(float battery) {
        this.battery = battery;
    }

    public long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "#DBLocation" + getId() + "/" + this.logjobId + ", " + this.lat + ", " +
                this.lon + ", " + this.timestamp + ", acc " + this.accuracy + ", speed : "+ this.speed +
                ", sat : "+ this.satellites + ", bea : " + this.bearing + ", alt : " +this.altitude +
                ", bat : " + this.battery;
    }
}
