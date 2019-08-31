package net.eneiluj.moneybuster.util;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.StringRes;

import net.eneiluj.moneybuster.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;

import at.bitfire.cert4android.CustomCertManager;


public class CospendClientUtil {

    public enum LoginStatus {
        OK(0),
        AUTH_FAILED(R.string.error_username_password_invalid),
        CONNECTION_FAILED(R.string.error_io),
        NO_NETWORK(R.string.error_no_network),
        JSON_FAILED(R.string.error_json),
        SERVER_FAILED(R.string.error_server),
        SSO_TOKEN_MISMATCH(R.string.error_token_mismatch);

        @StringRes
        public final int str;

        LoginStatus(@StringRes int str) {
            this.str = str;
        }
    }

    /**
     * Checks if the given url String starts with http:// or https://
     *
     * @param url String
     * @return true, if the given String is only http
     */
    public static boolean isHttp(String url) {
        return url != null && url.length() > 4 && url.startsWith("http") && url.charAt(4) != 's';
    }

    /**
     * Strips the api part from the path of a given url, handles trailing slash and missing protocol
     *
     * @param url String
     * @return formatted URL
     */
    public static String formatURL(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        String[] replacements = new String[]{"ihatemoney/", "v0.2/", "api/", "ihatemoney/", "apps/", "index.php/"};
        for (String replacement : replacements) {
            if (url.endsWith(replacement)) {
                url = url.substring(0, url.length() - replacement.length());
            }
        }
        return url;
    }

    /**
     * @param url      String
     * @param username String
     * @param password String
     * @return Username and Password are a valid Login-Combination for the given URL.
     */
    public static LoginStatus isValidLogin(CustomCertManager ccm, String url, String username, String password) {
        try {
            String targetURL = url + "index.php/apps/cospend/api/ping";
            HttpURLConnection con = SupportUtil.getHttpURLConnection(ccm, targetURL);
            con.setRequestMethod("GET");
            con.setRequestProperty(
                    "Authorization",
                    "Basic "
                            + new String(Base64.encode((username + ":"
                            + password).getBytes(), Base64.NO_WRAP)));
            con.setConnectTimeout(10 * 1000); // 10 seconds
            con.connect();

            Log.v(CospendClientUtil.class.getSimpleName(), "Establishing connection to server");
            if (con.getResponseCode() == 200) {
                Log.v(CospendClientUtil.class.getSimpleName(), "" + con.getResponseMessage());
                StringBuilder result = new StringBuilder();
                BufferedReader rd = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }
                Log.v(CospendClientUtil.class.getSimpleName(), result.toString());
                new JSONArray(result.toString());
                return LoginStatus.OK;
            } else if (con.getResponseCode() >= 401 && con.getResponseCode() <= 403) {
                return LoginStatus.AUTH_FAILED;
            } else {
                return LoginStatus.SERVER_FAILED;
            }
        } catch (MalformedURLException | SocketTimeoutException  e) {
            Log.e(CospendClientUtil.class.getSimpleName(), "Exception", e);
            return LoginStatus.CONNECTION_FAILED;
        } catch (IOException e) {
            Log.e(CospendClientUtil.class.getSimpleName(), "Exception", e);
            return LoginStatus.CONNECTION_FAILED;
        } catch (JSONException e) {
            Log.e(CospendClientUtil.class.getSimpleName(), "Exception", e);
            return LoginStatus.JSON_FAILED;
        }
    }

    /**
     * Pings a server and checks if there is a installed ownCloud instance
     *
     * @param url String URL to server
     * @return true if there is a installed instance, false if not
     */
    public static boolean isValidURL(CustomCertManager ccm, String url) {
        StringBuilder result = new StringBuilder();
        try {
            HttpURLConnection con = SupportUtil.getHttpURLConnection(ccm, url + "status.php");
            con.setRequestMethod(VersatileProjectSyncClient.METHOD_GET);
            con.setConnectTimeout(10 * 1000); // 10 seconds
            BufferedReader rd = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            JSONObject response = new JSONObject(result.toString());
            return response.getBoolean("installed");
        } catch (IOException | JSONException | NullPointerException e) {
            return false;
        }
    }

}