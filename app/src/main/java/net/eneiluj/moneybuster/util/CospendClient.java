package net.eneiluj.moneybuster.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.api.QueryParam;
import com.nextcloud.android.sso.api.Response;
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException;
import com.nextcloud.android.sso.exceptions.TokenMismatchException;

import net.eneiluj.moneybuster.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
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

    public ServerResponse.AccountProjectsResponse getAccountProjects(CustomCertManager ccm) throws JSONException, IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String target = "/index.php/apps/cospend/" + "getProjects";
        Log.d(getClass().getSimpleName(), "target "+target);
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get account projects");
            return new ServerResponse.AccountProjectsResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, null));
        }
        else {
            return new ServerResponse.AccountProjectsResponse(requestServer(ccm, target, METHOD_POST, null, "", true, false));
        }
    }

    public ServerResponse.CapabilitiesResponse getColor(CustomCertManager ccm) throws JSONException, IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String target = "/ocs/v2.php/cloud/capabilities";
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get color");
            //return new ServerResponse.SessionsResponse(new ResponseData("[]", lastETag, lastModified));
            return new ServerResponse.CapabilitiesResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.CapabilitiesResponse(requestServer(ccm, target, METHOD_GET, null, null, true, true));
        }
    }

    public ServerResponse.AvatarResponse getAvatar(CustomCertManager ccm, @Nullable String otherUserName) throws JSONException, IOException, TokenMismatchException, NextcloudHttpRequestFailedException {
        String targetUserName = username;
        if (otherUserName != null) {
            targetUserName = otherUserName;
        }
        String target = "/index.php/avatar/" + targetUserName + "/45";
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get avatar");
            //return new ServerResponse.SessionsResponse(new ResponseData("[]", lastETag, lastModified));
            return new ServerResponse.AvatarResponse(imageRequestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.AvatarResponse(imageRequestServer(ccm, target, METHOD_GET, null, null, true, false));
        }
    }

    private VersatileProjectSyncClient.ResponseData requestServerWithSSO(NextcloudAPI nextcloudAPI, String target, String method, List<QueryParam> qParams) throws TokenMismatchException, NextcloudHttpRequestFailedException {
        StringBuffer result = new StringBuffer();

        NextcloudRequest nextcloudRequest;
        if (qParams == null) {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target).build();
        } else {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target)
                    .setParameter(qParams)
                    .build();
        }

        try {
            // InputStream inputStream = nextcloudAPI.performNetworkRequest(nextcloudRequest);
            Response response = nextcloudAPI.performNetworkRequestV2(nextcloudRequest);
            InputStream inputStream = response.getBody();

            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            Log.d(getClass().getSimpleName(), "RES " + result.toString());
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
        } catch (NextcloudHttpRequestFailedException e) {
            Log.d(getClass().getSimpleName(), "SSO server HTTP request failed "+e.getStatusCode());
            throw e;
        } catch (Exception e) {
            // TODO handle errors
            Log.d(getClass().getSimpleName(), "SSO server request error "+e.toString());
        }

        return new VersatileProjectSyncClient.ResponseData(result.toString(), "", 0);
    }

    private VersatileProjectSyncClient.ResponseData imageRequestServerWithSSO(NextcloudAPI nextcloudAPI, String target, String method, List<QueryParam> qParams) throws TokenMismatchException, NextcloudHttpRequestFailedException {
        StringBuffer result = new StringBuffer();
        String strBase64 = "";

        NextcloudRequest nextcloudRequest;
        if (qParams == null) {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target).build();
        } else {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target)
                    .setParameter(qParams)
                    .build();
        }

        try {
            // InputStream inputStream = nextcloudAPI.performNetworkRequest(nextcloudRequest);
            Response response = nextcloudAPI.performNetworkRequestV2(nextcloudRequest);
            InputStream inputStream = response.getBody();

            Bitmap selectedImage = BitmapFactory.decodeStream(inputStream);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            selectedImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            strBase64 = Base64.encodeToString(byteArray, 0);


            inputStream.close();
        } catch (TokenMismatchException e) {
            Log.d(getClass().getSimpleName(), "Mismatcho SSO server request error "+e.toString());
            throw e;
        } catch (NextcloudHttpRequestFailedException e) {
            Log.d(getClass().getSimpleName(), "SSO server HTTP request failed "+e.getStatusCode());
            throw e;
        } catch (Exception e) {
            // TODO handle errors
            Log.d(getClass().getSimpleName(), "SSO server request error "+e.toString());
        }

        return new VersatileProjectSyncClient.ResponseData(strBase64, "", 0);
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
    private VersatileProjectSyncClient.ResponseData requestServer(CustomCertManager ccm, String target,
                                                                  String method, JSONObject params, String lastETag, boolean needLogin, boolean isOCSRequest)
            throws IOException, NextcloudHttpRequestFailedException {
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
        if (responseCode >= 400) {
            throw new NextcloudHttpRequestFailedException(responseCode, new IOException(""));
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
        return new VersatileProjectSyncClient.ResponseData(result.toString(), "", 0);
    }

    private VersatileProjectSyncClient.ResponseData imageRequestServer(CustomCertManager ccm, String target,
                                                                  String method, JSONObject params, String lastETag, boolean needLogin, boolean isOCSRequest)
            throws IOException, NextcloudHttpRequestFailedException {
        StringBuffer result = new StringBuffer();
        String strBase64 = "";
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
        if (responseCode >= 400) {
            throw new NextcloudHttpRequestFailedException(responseCode, new IOException(""));
        }

        Log.i(TAG, "METHOD : "+method);

        Bitmap selectedImage = BitmapFactory.decodeStream(con.getInputStream());
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        selectedImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        strBase64 = Base64.encodeToString(byteArray, 0);

        return new VersatileProjectSyncClient.ResponseData(strBase64, "", 0);
    }
}
