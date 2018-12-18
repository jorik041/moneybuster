package net.eneiluj.ihatemoney.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.eneiluj.ihatemoney.model.CloudSession;
import net.eneiluj.ihatemoney.model.DBLocation;
import net.eneiluj.ihatemoney.model.DBSession;
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.model.SyncError;
import net.eneiluj.ihatemoney.service.LoggerService;
import net.eneiluj.ihatemoney.util.ICallback;

/**
 * Helps to add, get, update and delete log jobs, sessions, locations with the option to trigger a session Resync with the Server.
 */
public class PhoneTrackSQLiteOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = PhoneTrackSQLiteOpenHelper.class.getSimpleName();

    private static final int database_version = 8;
    private static final String database_name = "NEXTCLOUD_PHONETRACK";

    private static final String table_sessions = "SESSIONS";
    private static final String key_id = "ID";
    private static final String key_token = "TOKEN";
    private static final String key_nextURL = "NEXTURL";
    private static final String key_name = "NAME";

    private static final String table_logjobs = "LOGJOBS";
    private static final String key_title = "TITLE";
    private static final String key_url = "URL";
    private static final String key_deviceName = "DEVICENAME";
    private static final String key_minTime = "MINTIME";
    private static final String key_minDistance = "MINDISTANCE";
    private static final String key_minAccuracy = "MINACCURACY";
    private static final String key_post = "POST";
    private static final String key_enabled = "ENABLED";
    private static final String key_lastLocTimestamp = "LASTLOC";
    private static final String key_nbsync = "NBSYNC";
    private static final String key_lastSyncTimestamp = "LASTSYNC";
    private static final String key_lastSyncErrorTimestamp = "LASTSYNCERRTIME";
    private static final String key_lastSyncErrorText = "LASTSYNCERR";

    private static final String table_locations = "LOCATIONS";
    private static final String key_logjobid = "LOGJOBID";
    private static final String key_lat = "LAT";
    private static final String key_lon = "LON";
    private static final String key_time = "TIME";
    private static final String key_bearing = "BEARING";
    private static final String key_altitude = "ALTITUDE";
    private static final String key_speed = "SPEED";
    private static final String key_accuracy = "ACCURACY";
    private static final String key_satellites = "SATELLITES";
    private static final String key_battery = "BATTERY";

    private static final String[] columnsSessions = {key_id, key_token, key_name, key_nextURL};
    private static final String[] columnsLogjobs = {
            key_id, key_title, key_url, key_token, key_deviceName,
            key_minTime, key_minDistance, key_minAccuracy, key_post, key_enabled,
            key_nbsync, key_lastSyncTimestamp, key_lastLocTimestamp,
            key_lastSyncErrorTimestamp, key_lastSyncErrorText};
    private static final String[] columnsLocations = {
            key_id, key_logjobid, key_lat, key_lon, key_time,
            key_bearing, key_altitude, key_speed, key_accuracy,
            key_satellites, key_battery};

    private static final String default_order = key_id + " DESC";

    private static PhoneTrackSQLiteOpenHelper instance;

    private SessionServerSyncHelper serverSyncHelper;
    private Context context;

    private PhoneTrackSQLiteOpenHelper(Context context) {
        super(context, database_name, null, database_version);
        this.context = context.getApplicationContext();
        serverSyncHelper = SessionServerSyncHelper.getInstance(this);
        //recreateDatabase(getWritableDatabase());
    }

    public static PhoneTrackSQLiteOpenHelper getInstance(Context context) {
        if (instance == null)
            return instance = new PhoneTrackSQLiteOpenHelper(context.getApplicationContext());
        else
            return instance;
    }

    public SessionServerSyncHelper getPhonetrackServerSyncHelper() {
        return serverSyncHelper;
    }

    /**
     * Creates initial the Database
     *
     * @param db Database
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createTableSessions(db, table_sessions);
        createTableLogjobs(db, table_logjobs);
        createTableLocations(db, table_locations);
        createIndexes(db);
    }

    private void createTableSessions(SQLiteDatabase db, String tableName) {
        db.execSQL("CREATE TABLE " + tableName + " ( " +
                key_id + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                key_name + " TEXT, " +
                key_nextURL + " TEXT, " +
                key_token + " TEXT)");

    }

    private void createTableLogjobs(SQLiteDatabase db, String tableName) {
        db.execSQL("CREATE TABLE " + tableName + " ( " +
                key_id + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                key_title + " TEXT, " +
                key_url + " TEXT, " +
                key_deviceName + " TEXT, " +
                key_minTime + " INTEGER, " +
                key_minDistance + " INTEGER, " +
                key_minAccuracy + " INTEGER, " +
                key_post + " INTEGER DEFAULT 0, " +
                key_enabled + " INTEGER DEFAULT 0, " +
                key_nbsync + " INTEGER DEFAULT 0, " +
                key_lastSyncTimestamp + " INTEGER DEFAULT 0, " +
                key_lastLocTimestamp + " INTEGER DEFAULT 0, " +
                key_lastSyncErrorTimestamp + " INTEGER DEFAULT 0, " +
                key_lastSyncErrorText + " TEXT, " +
                key_token + " TEXT)");
    }

    private void createTableLocations(SQLiteDatabase db, String tableName) {
        // key_id, key_logjobid, key_lat, key_lon, key_time, key_bearing, key_altitude, key_speed, key_accuracy, key_satellites, key_battery
        db.execSQL("CREATE TABLE " + tableName + " ( " +
                key_id + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                key_logjobid + " INTEGER, " +
                key_lat + " FLOAT, " +
                key_lon + " FLOAT, " +
                key_time + " INTEGER, " +
                key_bearing + " FLOAT, " +
                key_altitude + " FLOAT, " +
                key_speed + " FLOAT, " +
                key_accuracy + " FLOAT, " +
                key_satellites + " INTEGER, " +
                key_battery + " FLOAT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    /*@Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            recreateDatabase(db);
        }
        if (oldVersion < 4) {
            clearDatabase(db);
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE " + table_logjobs + " ADD COLUMN " + key_remote_id + " INTEGER");
            db.execSQL("UPDATE " + table_logjobs + " SET " + key_remote_id + "=" + key_id + " WHERE (" + key_remote_id + " IS NULL OR " + key_remote_id + "=0) AND " + key_status + "!=?", new String[]{DBStatus.LOCAL_CREATED.getTitle()});
            db.execSQL("UPDATE " + table_logjobs + " SET " + key_remote_id + "=0, " + key_status + "=? WHERE " + key_status + "=?", new String[]{DBStatus.LOCAL_EDITED.getTitle(), DBStatus.LOCAL_CREATED.getTitle()});
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + table_logjobs + " ADD COLUMN " + key_favorite + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 7) {
            dropIndexes(db);
            db.execSQL("ALTER TABLE " + table_logjobs + " ADD COLUMN " + key_category + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + table_logjobs + " ADD COLUMN " + key_etag + " TEXT");
            createIndexes(db);
        }
        if (oldVersion < 8) {
            final String table_temp = "LOGJOBS_TEMP";
            createTable(db, table_temp);
            db.execSQL(String.format("INSERT INTO %s(%s,%s,%s,%s,%s,%s,%s,%s,%s) ", table_temp, key_id, key_remote_id, key_status, key_title, key_modified, key_content, key_favorite, key_category, key_etag)
                    + String.format("SELECT %s,%s,%s,%s,strftime('%%s',%s),%s,%s,%s,%s FROM %s", key_id, key_remote_id, key_status, key_title, key_modified, key_content, key_favorite, key_category, key_etag, table_logjobs));
            db.execSQL(String.format("DROP TABLE %s", table_logjobs));
            db.execSQL(String.format("ALTER TABLE %s RENAME TO %s", table_temp, table_logjobs));
        }
    }*/

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        recreateDatabase(db);
    }

    private void clearDatabase(SQLiteDatabase db) {
        db.delete(table_sessions, null, null);
        db.delete(table_logjobs, null, null);
    }

    private void recreateDatabase(SQLiteDatabase db) {
        dropIndexes(db);
        db.execSQL("DROP TABLE " + table_sessions);
        db.execSQL("DROP TABLE " + table_logjobs);
        onCreate(db);
    }

    private void dropIndexes(SQLiteDatabase db) {
        Cursor c = db.query("sqlite_master", new String[]{"name"}, "type=?", new String[]{"index"}, null, null, null);
        while (c.moveToNext()) {
            db.execSQL("DROP INDEX " + c.getString(0));
        }
        c.close();
    }

    private void createIndexes(SQLiteDatabase db) {
        createIndex(db, table_sessions, key_token);
        createIndex(db, table_logjobs, key_token);
    }

    private void createIndex(SQLiteDatabase db, String table, String column) {
        String indexName = table + "_" + column + "_idx";
        db.execSQL("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + column + ")");
    }

    public Context getContext() {
        return context;
    }

    /**
     * Creates a new session in the Database and adds a Synchronization Flag.
     *
     */
    @SuppressWarnings("UnusedReturnValue")
    public long addSessionAndSync(String name, String token, String nextURL) {
        CloudSession session = new CloudSession(name, token, nextURL);
        return addSessionAndSync(session);
    }

    public long addSessionAndSync(CloudSession session) {
        DBSession dbs = new DBSession(0, session.getName(), session.getToken(), session.getNextURL());
        long id = addSession(dbs);
        notifySessionsChanged();
        //getPhonetrackServerSyncHelper().scheduleSync(true);
        return id;
    }

    /**
     * Creates a new logjob in the Database and adds a Synchronization Flag.
     */
    @SuppressWarnings("UnusedReturnValue")
    public long addLogjobAndSync(String title, String url, String token, String deviceName, int minTime, int minDistance, int minAccuracy, int nbSync, boolean post) {
        // TODO there is an 'enabled' field
        DBLogjob dblj = new DBLogjob(0, title, url, token, deviceName, minTime, minDistance, minAccuracy, post,false, nbSync);
        long id = addLogjob(dblj);
        //getPhonetrackServerSyncHelper().scheduleSync(true);
        return id;
    }

    /**
     * Inserts a logjob directly into the Database.
     *
     * @param logjob logjob to be added.
     */
    public long addLogjob(DBLogjob logjob) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        if (logjob.getId() > 0) {
            values.put(key_id, logjob.getId());
        }
        values.put(key_title, logjob.getTitle());
        values.put(key_token, logjob.getToken());
        values.put(key_deviceName, logjob.getDeviceName());
        values.put(key_minTime, logjob.getMinTime());
        values.put(key_minDistance, logjob.getMinDistance());
        values.put(key_minAccuracy, logjob.getMinAccuracy());
        values.put(key_enabled, logjob.isEnabled() ? "1" : "0");
        values.put(key_post, logjob.getPost() ? "1" : "0");
        values.put(key_url, logjob.getUrl());
        values.put(key_nbsync, logjob.getNbSync());
        return db.insert(table_logjobs, null, values);
    }

    long addSession(CloudSession session) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_name, session.getName());
        values.put(key_token, session.getToken());
        values.put(key_nextURL, session.getNextURL());
        return db.insert(table_sessions, null, values);
    }

    /**
     * Get a single logjob by ID
     *
     * @param id int - ID of the requested log job
     * @return requested log job
     */
    public DBLogjob getLogjob(long id) {
        List<DBLogjob> logjobs = getLogjobsCustom(key_id + " = ?", new String[]{String.valueOf(id)}, null);
        return logjobs.isEmpty() ? null : logjobs.get(0);
    }

    /**
     * Query the database with a custom raw query.
     *
     * @param selection     A filter declaring which rows to return, formatted as an SQL WHERE clause (excluding the WHERE itself).
     * @param selectionArgs You may include ?s in selection, which will be replaced by the values from selectionArgs, in order that they appear in the selection. The values will be bound as Strings.
     * @param orderBy       How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort order, which may be unordered.
     * @return List of log jobs
     */
    @NonNull
    @WorkerThread
    private List<DBLogjob> getLogjobsCustom(@NonNull String selection, @NonNull String[] selectionArgs, @Nullable String orderBy) {
        SQLiteDatabase db = getReadableDatabase();
        if (selectionArgs.length > 2) {
            Log.v("Logjob", selection + "   ----   " + selectionArgs[0] + " " + selectionArgs[1] + " " + selectionArgs[2]);
        }
        Cursor cursor = db.query(table_logjobs, columnsLogjobs, selection, selectionArgs, null, null, orderBy);
        List<DBLogjob> logjobs = new ArrayList<>();
        while (cursor.moveToNext()) {
            logjobs.add(getLogjobFromCursor(cursor));
        }
        cursor.close();
        return logjobs;
    }

    /**
     * Creates a DBLogjob object from the current row of a Cursor.
     *
     * @param cursor database cursor
     * @return DBLogjob
     */
    @NonNull
    private DBLogjob getLogjobFromCursor(@NonNull Cursor cursor) {
        return new DBLogjob(cursor.getLong(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getInt(5),
                cursor.getInt(6),
                cursor.getInt(7),
                cursor.getInt(8) == 1,
                cursor.getInt(9) == 1,
                cursor.getInt(10)
        );
    }

    /**
     * Get a single session by ID
     *
     * @param id int - ID of the requested session
     * @return requested session
     */
    public DBSession getSession(long id) {
        List<DBSession> sessions = getSessionsCustom(key_id + " = ?", new String[]{String.valueOf(id)}, null);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    /**
     * Query the database with a custom raw query.
     *
     * @param selection     A filter declaring which rows to return, formatted as an SQL WHERE clause (excluding the WHERE itself).
     * @param selectionArgs You may include ?s in selection, which will be replaced by the values from selectionArgs, in order that they appear in the selection. The values will be bound as Strings.
     * @param orderBy       How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort order, which may be unordered.
     * @return List of sessions
     */
    @NonNull
    @WorkerThread
    private List<DBSession> getSessionsCustom(@NonNull String selection, @NonNull String[] selectionArgs, @Nullable String orderBy) {
        SQLiteDatabase db = getReadableDatabase();
        if (selectionArgs.length > 2) {
            Log.v("Session", selection + "   ----   " + selectionArgs[0] + " " + selectionArgs[1] + " " + selectionArgs[2]);
        }
        Cursor cursor = db.query(table_sessions, columnsSessions, selection, selectionArgs, null, null, orderBy);
        List<DBSession> sessions = new ArrayList<>();
        while (cursor.moveToNext()) {
            sessions.add(getSessionFromCursor(cursor));
        }
        cursor.close();
        return sessions;
    }

    /**
     * Creates a DBLogjob object from the current row of a Cursor.
     *
     * @param cursor database cursor
     * @return DBLogjob
     */
    @NonNull
    private DBSession getSessionFromCursor(@NonNull Cursor cursor) {
        return new DBSession(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3));
    }

    public void debugPrintFullDB() {
        List<DBSession> sessions = getSessionsCustom("", new String[]{}, default_order);
        Log.v(getClass().getSimpleName(), "Full Database (" + sessions.size() + " sessions):");
        for (DBSession session : sessions) {
            Log.v(getClass().getSimpleName(), "     " + session);
        }

        List<DBLogjob> logjobs = getLogjobsCustom("", new String[]{}, default_order);
        Log.v(getClass().getSimpleName(), "Full Database (" + logjobs.size() + " logjobs):");
        for (DBLogjob logjob : logjobs) {
            Log.v(getClass().getSimpleName(), "     " + logjob);
        }
    }

    @NonNull
    @WorkerThread
    public Map<String, Long> getTokenMap() {
        Map<String, Long> result = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(table_sessions, new String[]{key_token, key_id}, "", new String[]{}, null, null, null);
        while (cursor.moveToNext()) {
            result.put(cursor.getString(0), cursor.getLong(1));
        }
        cursor.close();
        return result;
    }

    /**
     * Returns a list of all sessions in the Database
     *
     * @return List&lt;DBSession&gt;
     */
    @NonNull
    @WorkerThread
    public List<DBSession> getSessions() {
        return getSessionsCustom("", new String[]{}, default_order);
    }

    @NonNull
    @WorkerThread
    public List<DBLogjob> getLogjobs() {
        return getLogjobsCustom("", new String[]{}, default_order);
    }

    /**
     * Returns a list of all logjobs in the Database
     *
     * @return List&lt;DBLogjob&gt;
     */
    @NonNull
    @WorkerThread
    public List<DBLogjob> searchLogjobs(@Nullable CharSequence query, @Nullable Boolean enabled) {
        List<String> where = new ArrayList<>();
        List<String> args = new ArrayList<>();

        if (query != null) {
            where.add("(" + key_title + " LIKE ? OR " + key_url + " LIKE ? OR " + key_deviceName + " LIKE ?)");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }

        if (enabled != null) {
            // TODO search with enabled
            //where.add(key_enabled + "=?");
            //args.add(enabled ? "1" : "0");
        }

        String order = key_title;
        return getLogjobsCustom(TextUtils.join(" AND ", where), args.toArray(new String[]{}), order);
    }


    @NonNull
    @WorkerThread
    public Map<String, Integer> getEnabledCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                table_logjobs,
                new String[]{key_enabled, "COUNT(*)"},
                "",
                new String[]{},
                key_enabled,
                null,
                key_enabled);
        Map<String, Integer> enabled = new HashMap<>(cursor.getCount());
        while (cursor.moveToNext()) {
            enabled.put(cursor.getString(0), cursor.getInt(1));
        }
        cursor.close();
        return enabled;
    }

    public void toggleEnabled(@NonNull DBLogjob logjob, @Nullable ICallback callback) {
        logjob.setEnabled(!logjob.isEnabled());
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_enabled, logjob.isEnabled() ? "1" : "0");
        db.update(table_logjobs, values, key_id + " = ?", new String[]{String.valueOf(logjob.getId())});
        /*if (callback != null) {
            serverSyncHelper.addCallbackPush(callback);
        }
        serverSyncHelper.scheduleSync(true);*/
    }

    public DBLogjob updateLogjobAndSync(@NonNull DBLogjob oldLogjob, @Nullable String newTitle, @Nullable String newToken, @Nullable String newUrl, @Nullable String newDevicename, boolean newPost, int newMinTime, int newMinDistance, int newMinAccuracy, @Nullable ICallback callback) {
        //debugPrintFullDB();
        DBLogjob newLogjob;
        if (newTitle == null) {
            newLogjob = new DBLogjob(oldLogjob.getId(), oldLogjob.getTitle(), oldLogjob.getUrl(), oldLogjob.getToken(), oldLogjob.getDeviceName(), oldLogjob.getMinTime(), oldLogjob.getMinDistance(), oldLogjob.getMinAccuracy(), oldLogjob.getPost(), oldLogjob.isEnabled(), oldLogjob.getNbSync());
        }
        else {
            newLogjob = new DBLogjob(oldLogjob.getId(), newTitle, newUrl, newToken, newDevicename, newMinTime, newMinDistance, newMinAccuracy, newPost, oldLogjob.isEnabled(), oldLogjob.getNbSync());
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_title, newLogjob.getTitle());
        values.put(key_url, newLogjob.getUrl());
        values.put(key_token, newLogjob.getToken());
        values.put(key_post, newLogjob.getPost() ? 1 : 0);
        values.put(key_minTime, newLogjob.getMinTime());
        values.put(key_minDistance, newLogjob.getMinDistance());
        values.put(key_minAccuracy, newLogjob.getMinAccuracy());
        values.put(key_deviceName, newLogjob.getDeviceName());
        int rows = db.update(table_logjobs, values, key_id + " = ?", new String[]{String.valueOf(newLogjob.getId())});
        // if data was changed, set new status and schedule sync (with callback); otherwise invoke callback directly.
        if (rows > 0) {
            return newLogjob;
        } else {
            if (callback != null) {
                callback.onFinish();
            }
            return oldLogjob;
        }
    }

    /**
     * Updates a single session with data from the server
     *
     * @param id                        local ID of session
     * @param remoteSession                session from the server.
     * @return The number of the Rows affected.
     */
    int updateSession(long id, @NonNull CloudSession remoteSession) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(key_name, remoteSession.getName());
        values.put(key_token, remoteSession.getToken());
        values.put(key_nextURL, remoteSession.getNextURL());
        String whereClause;
        String[] whereArgs;

        whereClause = key_id + " = ?";
        whereArgs = new String[]{String.valueOf(id)};

        int i = db.update(table_sessions, values, whereClause, whereArgs);
        Log.d(getClass().getSimpleName(), "updateSession: " + remoteSession + " => " + i + " rows updated");
        return i;
    }

    /**
     * Delete a single Logjob from the Database
     *
     * @param id            long - ID of the Logjob that should be deleted.
     */
    public void deleteLogjob(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        // delete all locations
        db.delete(table_locations,
                key_logjobid + " = ?",
                new String[]{String.valueOf(id)});
        // delete the log job
        db.delete(table_logjobs,
                key_id + " = ?",
                new String[]{String.valueOf(id)});
    }

    void deleteSession(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(table_sessions,
                key_id + " = ?",
                new String[]{String.valueOf(id)});
    }

    /**
     * key_lat, key_lon, key_time, key_bearing, key_altitude, key_speed, key_accuracy, key_satellites, key_battery
     *
     * @param ljId
     * @param loc
     */
    public void addLocation(String ljId, Location loc, float battery) {
        if (LoggerService.DEBUG) { Log.d(TAG, "[writeLocation from ljid, loc, battery]"); }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_logjobid, ljId);
        values.put(key_time, loc.getTime() / 1000);
        values.put(key_lat, loc.getLatitude());
        values.put(key_lon, loc.getLongitude());
        values.put(key_bearing, loc.hasBearing() ? loc.getBearing() : -1.0);
        values.put(key_altitude, loc.hasAltitude() ? loc.getAltitude() : -1.0);
        values.put(key_speed, loc.hasSpeed() ? loc.getSpeed() : -1.0);
        values.put(key_accuracy, loc.hasAccuracy() ? loc.getAccuracy() : -1.0);
        values.put(key_battery, battery);
        int sat = -1;
        //if (LoggerService.DEBUG) { Log.d(TAG, "[PROVIDER "+loc.getProvider()+"]"); }
        if(loc.getProvider() == "gps" && loc.getExtras() != null) {
            sat = loc.getExtras().getInt("satellites", -1);
        }
        values.put(key_satellites, sat);

        db.insert(table_locations, null, values);

        setLastLocTimestamp(ljId, loc.getTime() / 1000);
    }

    public void addLocation(DBLocation dbLoc) {
        if (LoggerService.DEBUG) { Log.d(TAG, "[writeLocation from dblocation]"); }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(key_logjobid, dbLoc.getLogjobId());
        values.put(key_time, dbLoc.getTimestamp());
        values.put(key_lat, dbLoc.getLat());
        values.put(key_lon, dbLoc.getLon());
        values.put(key_bearing, dbLoc.getBearing());
        values.put(key_altitude, dbLoc.getAltitude());
        values.put(key_speed, dbLoc.getSpeed());
        values.put(key_accuracy, dbLoc.getAccuracy());
        values.put(key_battery, dbLoc.getBattery());
        values.put(key_satellites, dbLoc.getSatellites());

        db.insert(table_locations, null, values);
    }

    /**
     * Get a single logjob by ID
     *
     * @param ljId int - ID of the logjob
     * @return requested locations
     */
    public List<DBLocation> getLocationOfLogjob(String ljId) {
        List<DBLocation> locations = getLocationsCustom(key_logjobid + " = ?", new String[]{ljId}, key_time + " ASC");
        return locations;
    }

    /**
     * Query the database with a custom raw query.
     *
     * @param selection     A filter declaring which rows to return, formatted as an SQL WHERE clause (excluding the WHERE itself).
     * @param selectionArgs You may include ?s in selection, which will be replaced by the values from selectionArgs, in order that they appear in the selection. The values will be bound as Strings.
     * @param orderBy       How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself). Passing null will use the default sort order, which may be unordered.
     * @return List of locations
     */
    @NonNull
    @WorkerThread
    private List<DBLocation> getLocationsCustom(@NonNull String selection, @NonNull String[] selectionArgs, @Nullable String orderBy) {
        SQLiteDatabase db = getReadableDatabase();
        if (selectionArgs.length > 2) {
            Log.v("Location", selection + "   ----   " + selectionArgs[0] + " " + selectionArgs[1] + " " + selectionArgs[2]);
        }
        Cursor cursor = db.query(table_locations, columnsLocations, selection, selectionArgs, null, null, orderBy);
        List<DBLocation> locations = new ArrayList<>();
        while (cursor.moveToNext()) {
            locations.add(getLocationFromCursor(cursor));
        }
        cursor.close();
        return locations;
    }

    /**
     * Creates a DBLocation object from the current row of a Cursor.
     * key_id, key_logjobid, key_lat, key_lon, 4 key_time, 5 key_bearing, 6 key_altitude, 7 key_speed, 8 key_accuracy, 9 key_satellites, 10 key_battery
     *
     * @param cursor database cursor
     * @return DBLocation
     */
    @NonNull
    private DBLocation getLocationFromCursor(@NonNull Cursor cursor) {
        return new DBLocation(cursor.getLong(0), cursor.getLong(1), cursor.getFloat(2), cursor.getFloat(3), cursor.getInt(4), cursor.getFloat(5), cursor.getFloat(6), cursor.getFloat(7), cursor.getFloat(8), cursor.getInt(9), cursor.getFloat(10));
    }

    @NonNull
    @WorkerThread
    public int getLogjobLocationCount(long ljId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                table_locations,
                new String[]{"COUNT(*)"},
                key_logjobid +" = ?",
                new String[]{String.valueOf(ljId)},
                null,
                null,
                null);
        int result = 0;
        while (cursor.moveToNext()) {
            result = cursor.getInt(0);
            break;
        }
        cursor.close();
        return result;
    }

    @NonNull
    @WorkerThread
    public int getLocationCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                table_locations,
                new String[]{"COUNT(*)"},
                null,
                new String[]{},
                null,
                null,
                null);
        int result = 0;
        while (cursor.moveToNext()) {
            result = cursor.getInt(0);
            break;
        }
        cursor.close();
        return result;
    }

    public void deleteLocation(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(table_locations,
                key_id + " = ?",
                new String[]{String.valueOf(id)});
    }

    public void incNbSync(@NonNull DBLogjob logjob) {
        logjob.setNbSync(logjob.getNbSync() + 1);
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_nbsync, logjob.getNbSync());
        db.update(table_logjobs, values, key_id + " = ?", new String[]{String.valueOf(logjob.getId())});
    }

    public int getNbSync(long logjobId) {
        DBLogjob lj = getLogjob(logjobId);
        return (lj == null) ? 0 : lj.getNbSync();
    }

    public int getNbTotalSync() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                table_logjobs,
                new String[]{"SUM("+key_nbsync+")"},
                null,
                new String[]{},
                null,
                null,
                null);
        int result = 0;
        while (cursor.moveToNext()) {
            result = cursor.getInt(0);
            break;
        }
        cursor.close();
        return result;
    }

    public void setLastLocTimestamp(String ljId, long ts) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_lastLocTimestamp, ts);
        db.update(table_logjobs, values, key_id + " = ?", new String[]{ljId});
    }

    public long getLastLocTimestamp(String ljId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(table_logjobs, new String[]{key_lastLocTimestamp}, key_id + " = ?", new String[]{ljId}, null, null, null);
        long res = 0;
        while (cursor.moveToNext()) {
            res = cursor.getLong(0);
            break;
        }
        cursor.close();
        return res;
    }

    public void setLastSyncTimestamp(String ljId, long ts) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_lastSyncTimestamp, ts);
        db.update(table_logjobs, values, key_id + " = ?", new String[]{ljId});
    }

    public long getLastSyncTimestamp(String ljId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(table_logjobs, new String[]{key_lastSyncTimestamp}, key_id + " = ?", new String[]{ljId}, null, null, null);
        long res = 0;
        while (cursor.moveToNext()) {
            res = cursor.getLong(0);
            break;
        }
        cursor.close();
        return res;
    }

    public void setLastSyncError(String ljId, long ts, String message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(key_lastSyncErrorTimestamp, ts);
        values.put(key_lastSyncErrorText, message);
        db.update(table_logjobs, values, key_id + " = ?", new String[]{ljId});
    }

    public SyncError getLastSyncError(String ljId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(table_logjobs, new String[]{key_lastSyncErrorTimestamp, key_lastSyncErrorText}, key_id + " = ?", new String[]{ljId}, null, null, null);
        long ts = 0;
        String msg = "";
        while (cursor.moveToNext()) {
            ts = cursor.getLong(0);
            msg = cursor.getString(1);
            break;
        }
        cursor.close();
        return new SyncError(ts, msg);
    }

    /**
     * Notify about changed logjob.
     */
    void notifySessionsChanged() {
        // update the widgets
    }
}
