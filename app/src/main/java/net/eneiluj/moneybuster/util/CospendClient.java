package net.eneiluj.moneybuster.util;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.exceptions.TokenMismatchException;

import net.eneiluj.moneybuster.BuildConfig;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import at.bitfire.cert4android.CustomCertManager;

@WorkerThread
public class CospendClient {

    private static final String TAG = CospendClient.class.getSimpleName();


    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_DELETE = "DELETE";
    public static final String JSON_ID = "id";
    public static final String JSON_TITLE = "title";
    public static final String JSON_ETAG = "etag";
    private static final String application_json = "application/json";

    private String url;
    private String username;
    private String password;
    private NextcloudAPI nextcloudAPI;

    public CospendClient(String url, String username, String password, @Nullable NextcloudAPI nextcloudAPI) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.nextcloudAPI = nextcloudAPI;
    }

    public ServerResponse.AccountProjectsResponse getAccountProjects(CustomCertManager ccm) throws JSONException, IOException, TokenMismatchException {
        String target = "/index.php/apps/cospend/" + "getProjects";
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get account projects");
            return new ServerResponse.AccountProjectsResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, null));
        }
        else {
            return new ServerResponse.AccountProjectsResponse(requestServer(ccm, target, METHOD_POST, null, "", true, false));
        }
    }

    private IHateMoneyClient.ResponseData requestServerWithSSO(NextcloudAPI nextcloudAPI, String target, String method, Map<String, String> params) throws TokenMismatchException{
        StringBuffer result = new StringBuffer();

        NextcloudRequest nextcloudRequest;
        if (params == null) {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target).build();
        }
        else {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target)
                    .setParameter(params)
                    .build();
        }

        try {
            Log.d(getClass().getSimpleName(), "BEGGGGGGGGGGG ");
            InputStream inputStream = nextcloudAPI.performNetworkRequest(nextcloudRequest);

            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            Log.d(getClass().getSimpleName(), "RESSSS " + result.toString());
            inputStream.close();
        } catch (TokenMismatchException e) {
            Log.d(getClass().getSimpleName(), "Mismatcho SSO server request error "+e.toString());
            /*try {
                SingleAccountHelper.reauthenticateCurrentAccount(:smile:);
            } catch (NextcloudFilesAppAccountNotFoundException | NoCurrentAccountSelectedException | NextcloudFilesAppNotSupportedException ee) {
                UiExceptionManager.showDialogForException(new SettingsActivity(), ee);
            } catch (NextcloudFilesAppAccountPermissionNotGrantedException ee) {
                // Unable to reauthenticate account just like that..
                // TODO Show login screen here
                LoginDialogFragment loginDialogFragment = new LoginDialogFragment();
                loginDialogFragment.show(new SettingsActivity().getSupportFragmentManager(), "NoticeDialogFragment");
            }*/
            throw e;

        } catch (Exception e) {
            // TODO handle errors
            Log.d(getClass().getSimpleName(), "SSO server request error "+e.toString());
        }

        return new IHateMoneyClient.ResponseData(result.toString(), "", 0);
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
    private IHateMoneyClient.ResponseData requestServer(CustomCertManager ccm, String target, String method, JSONObject params, String lastETag, boolean needLogin, boolean isOCSRequest)
            throws IOException {
        StringBuffer result = new StringBuffer();
        // setup connection
        String targetURL = url + target.replaceAll("^/", "");
        HttpURLConnection con = SupportUtil.getHttpURLConnection(ccm, targetURL);
        con.setRequestMethod(method);
        if (needLogin) {
            con.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP));
        }
        // https://github.com/square/retrofit/issues/805#issuecomment-93426183
        con.setRequestProperty( "Connection", "Close");
        con.setRequestProperty("User-Agent", "phonetrack-android/" + BuildConfig.VERSION_NAME);
        if (lastETag != null && METHOD_GET.equals(method)) {
            con.setRequestProperty("If-None-Match", lastETag);
        }
        if (isOCSRequest) {
            con.setRequestProperty("OCS-APIRequest", "true");
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

        Log.i(TAG, "METHOD : "+method);
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
        return new IHateMoneyClient.ResponseData(result.toString(), "", 0);
    }
}
