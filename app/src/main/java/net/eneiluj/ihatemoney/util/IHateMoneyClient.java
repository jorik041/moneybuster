package net.eneiluj.ihatemoney.util;

import android.support.annotation.WorkerThread;
import android.util.ArrayMap;
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
import java.net.URLEncoder;
import java.util.Map;

import at.bitfire.cert4android.CustomCertManager;
import net.eneiluj.ihatemoney.BuildConfig;
import net.eneiluj.ihatemoney.model.DBProject;

@WorkerThread
public class IHateMoneyClient {

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
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_DELETE = "DELETE";
    public static final String JSON_ID = "id";
    public static final String JSON_TITLE = "title";
    public static final String JSON_ETAG = "etag";
    private static final String application_json = "application/json";


    public IHateMoneyClient() {

    }

    public ServerResponse.ProjectResponse getProject(CustomCertManager ccm, DBProject project, long lastModified, String lastETag) throws JSONException, IOException {
        String target = project.getIhmUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId();
        //https://ihatemoney.org/api/projects/demo
        return new ServerResponse.ProjectResponse(requestServer(ccm, target, METHOD_GET, null, lastETag, project.getRemoteId(), project.getPassword()));
    }

    public ServerResponse.EditRemoteProjectResponse editRemoteProject(CustomCertManager ccm, DBProject project, String newName, String newEmail, String newPassword) throws IOException {
        String target = project.getIhmUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId();
        //https://ihatemoney.org/api/projects/demo
        Map<String, String> params = new ArrayMap<>();
        params.put("name", newName == null ? "" : newName);
        params.put("contact_email", newEmail == null ? "" : newEmail);
        params.put("password", newPassword == null ? "" : newPassword);
        return new ServerResponse.EditRemoteProjectResponse(requestServer(ccm, target, METHOD_PUT, params, null, project.getRemoteId(), project.getPassword()));
    }

    public ServerResponse.DeleteRemoteProjectResponse deleteRemoteProject(CustomCertManager ccm, DBProject project) throws IOException {
        String target = project.getIhmUrl().replaceAll("/+$", "")
                + "/api/projects/" + project.getRemoteId();
        return new ServerResponse.DeleteRemoteProjectResponse(requestServer(ccm, target, METHOD_DELETE, null, null, project.getRemoteId(), project.getPassword()));
    }

    public ServerResponse.CreateRemoteProjectResponse createRemoteProject(CustomCertManager ccm, DBProject project) throws IOException {
        String target = project.getIhmUrl().replaceAll("/+$", "")
                + "/api/projects";
        Map<String, String> params = new ArrayMap<>();
        params.put("name", project.getName() == null ? "" : project.getName());
        params.put("contact_email", project.getEmail() == null ? "" : project.getEmail());
        params.put("password", project.getPassword() == null ? "" : project.getPassword());
        params.put("id", project.getRemoteId() == null ? "" : project.getRemoteId());
        return new ServerResponse.CreateRemoteProjectResponse(requestServer(ccm, target, METHOD_POST, params, null, null, null));
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
    private ResponseData requestServer(CustomCertManager ccm, String target, String method, Map<String, String>  params, String lastETag, String username, String password)
            throws IOException {
        StringBuffer result = new StringBuffer();
        // setup connection
        String targetURL = target;
        HttpURLConnection con = SupportUtil.getHttpURLConnection(ccm, targetURL);
        con.setRequestMethod(method);
        if (username != null) {
            con.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP));
        }
        con.setRequestProperty("Connection", "Close");
        con.setRequestProperty("User-Agent", "ihatemoney-android/" + BuildConfig.VERSION_NAME);
        if (lastETag != null && METHOD_GET.equals(method)) {
            con.setRequestProperty("If-None-Match", lastETag);
        }
        con.setConnectTimeout(10 * 1000); // 10 seconds
        Log.d(getClass().getSimpleName(), method + " " + targetURL);
        // send request data (optional)
        byte[] paramData = null;
        if (params != null) {
            String dataString = "";
            for (Map.Entry<String, String> p : params.entrySet()) {
                String key = p.getKey();
                String value = p.getValue();
                if (dataString.length() > 0) {
                    dataString += "&";
                }
                dataString += URLEncoder.encode(key, "UTF-8") + "=";
                dataString += URLEncoder.encode(value, "UTF-8");
            }
            byte[] data = dataString.getBytes();

            Log.d(getClass().getSimpleName(), "Params: " + params);
            con.setFixedLengthStreamingMode(data.length);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            con.setRequestProperty("Content-Length", Integer.toString(data.length));
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            os.write(data);
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
        BufferedReader rd;
        if (responseCode >= 200 && responseCode < 400) {
            rd = new BufferedReader(new InputStreamReader(con.getInputStream()));
        }
        else {
            rd = new BufferedReader(new InputStreamReader(con.getErrorStream()));
        }
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        if (responseCode >= 400) {
            throw new IOException(result.toString());
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
