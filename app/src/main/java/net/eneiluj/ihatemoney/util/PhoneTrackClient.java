package net.eneiluj.ihatemoney.util;

import android.support.annotation.WorkerThread;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import at.bitfire.cert4android.CustomCertManager;
import net.eneiluj.ihatemoney.BuildConfig;

@WorkerThread
public class PhoneTrackClient {

    /**
     * This entity class is used to return relevant data of the HTTP reponse.
     */
    public static class ResponseData {
        private final String content;
        private final String etag;
        private final long lastModified;

        public ResponseData(String content, String etag, long lastModified) {
            this.content = content;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        public String getContent() {
            return content;
        }

        public String getETag() {
            return etag;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String JSON_ID = "id";
    public static final String JSON_TITLE = "title";
    public static final String JSON_ETAG = "etag";
    private static final String application_json = "application/json";
    private String url;
    private String username;
    private String password;

    public PhoneTrackClient(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public ServerResponse.SessionsResponse getSessions(CustomCertManager ccm, long lastModified, String lastETag) throws JSONException, IOException {
        String target = "api/getsessions";
        return new ServerResponse.SessionsResponse(requestServer(ccm, target, METHOD_GET, null, lastETag));
    }

    public ServerResponse.ShareDeviceResponse shareDevice(CustomCertManager ccm, String token, String deviceName) throws JSONException, IOException {
        String target = "api/sharedevice/" + token + "/" + deviceName;
        return new ServerResponse.ShareDeviceResponse(requestServer(ccm, target, METHOD_GET, null, null));
    }

    /**
     * Request-Method for POST, PUT with or without JSON-Object-Parameter
     *
     * @param target Filepath to the wanted function
     * @param method GET, POST, DELETE or PUT
     * @param params JSON Object which shall be transferred to the server.
     * @return Body of answer
     * @throws MalformedURLException
     * @throws IOException
     */
    private ResponseData requestServer(CustomCertManager ccm, String target, String method, JSONObject params, String lastETag)
            throws IOException {
        StringBuffer result = new StringBuffer();
        // setup connection
        String targetURL = url + "index.php/apps/ihatemoney/" + target;
        HttpURLConnection con = SupportUtil.getHttpURLConnection(ccm, targetURL);
        con.setRequestMethod(method);
        con.setRequestProperty(
                "Authorization",
                "Basic " + Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP));
        // https://github.com/square/retrofit/issues/805#issuecomment-93426183
        con.setRequestProperty( "Connection", "Close");
        con.setRequestProperty("User-Agent", "ihatemoney-android/" + BuildConfig.VERSION_NAME);
        if (lastETag != null && METHOD_GET.equals(method)) {
            con.setRequestProperty("If-None-Match", lastETag);
        }
        con.setConnectTimeout(10 * 1000); // 10 seconds
        Log.d(getClass().getSimpleName(), method + " " + targetURL);
        // send request data (optional)
        byte[] paramData = null;
        if (params != null) {
            paramData = params.toString().getBytes();
            Log.d(getClass().getSimpleName(), "Params: " + params);
            con.setFixedLengthStreamingMode(paramData.length);
            con.setRequestProperty("Content-Type", application_json);
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            os.write(paramData);
            os.flush();
            os.close();
        }
        // read response data
        int responseCode = con.getResponseCode();
        Log.d(getClass().getSimpleName(), "HTTP response code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw new ServerResponse.NotModifiedException();
        }

        System.out.println("METHOD : "+method);
        BufferedReader rd = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        // create response object
        String etag = con.getHeaderField("ETag");
        long lastModified = con.getHeaderFieldDate("Last-Modified", 0) / 1000;
        Log.i(getClass().getSimpleName(), "Result length:  " + result.length() + (paramData == null ? "" : "; Request length: " + paramData.length));
        Log.d(getClass().getSimpleName(), "ETag: " + etag + "; Last-Modified: " + lastModified + " (" + con.getHeaderField("Last-Modified") + ")");
        // return these header fields since they should only be saved after successful processing the result!
        return new ResponseData(result.toString(), etag, lastModified);
    }
}
