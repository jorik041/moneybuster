package net.eneiluj.moneybuster.util;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.exceptions.TokenMismatchException;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import net.eneiluj.moneybuster.BuildConfig;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.bitfire.cert4android.CustomCertManager;

@WorkerThread
public class VersatileProjectSyncClient {

    private static final String TAG = VersatileProjectSyncClient.class.getSimpleName();

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

    private String url;
    private String username;
    private String password;
    private NextcloudAPI nextcloudAPI;
    private SingleSignOnAccount ssoAccount;

    public VersatileProjectSyncClient(String url, String username, String password,
                                      @Nullable NextcloudAPI nextcloudAPI, @Nullable SingleSignOnAccount ssoAccount) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.nextcloudAPI = nextcloudAPI;
        this.ssoAccount = ssoAccount;
    }

    public boolean canAccessProjectWithNCLogin(DBProject project) {
        return (project.getPassword().equals("")
                && !url.replaceAll("/+$", "").equals("")
                && project.getIhmUrl()
                    .replace("/index.php/apps/cospend", "")
                    .equals(url.replaceAll("/+$", ""))
        );
    }

    public boolean canAccessProjectWithSSO(DBProject project) {
        return (project.getPassword().equals("")
                && ssoAccount != null
                && project.getIhmUrl().replace("/index.php/apps/cospend", "").equals(ssoAccount.url)
        );
    }

    public ServerResponse.ProjectResponse getProject(CustomCertManager ccm, DBProject project, long lastModified, String lastETag) throws JSONException, IOException, TokenMismatchException {
        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId();
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId();
                return new ServerResponse.ProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null, null));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/" + project.getPassword();
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
        }

        return new ServerResponse.ProjectResponse(requestServer(ccm, target, METHOD_GET, null, null, lastETag, username, password));
    }

    public ServerResponse.EditRemoteProjectResponse editRemoteProject(CustomCertManager ccm, DBProject project, String newName, String newEmail, String newPassword) throws IOException, TokenMismatchException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(newName == null ? "" : newName);
        paramKeys.add("contact_email");
        paramValues.add(newEmail == null ? "" : newEmail);
        paramKeys.add("password");
        paramValues.add(newPassword == null ? "" : newPassword);

        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId();
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId();
                return new ServerResponse.EditRemoteProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/" + project.getPassword();
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId();
            //https://ihatemoney.org/api/projects/demo
            username = project.getRemoteId();
            password = project.getPassword();
        }
        return new ServerResponse.EditRemoteProjectResponse(requestServer(ccm, target, METHOD_PUT, paramKeys, paramValues, null, username, password));
    }

    public ServerResponse.EditRemoteMemberResponse editRemoteMember(CustomCertManager ccm, DBProject project, DBMember member) throws IOException, TokenMismatchException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(member.getName());
        paramKeys.add("weight");
        paramValues.add(String.valueOf(member.getWeight()));
        paramKeys.add("activated");
        paramValues.add(member.isActivated() ? "true" : "false");

        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
                return new ServerResponse.EditRemoteMemberResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/"
                        + project.getPassword() + "/members/" + member.getRemoteId();
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
        }

        return new ServerResponse.EditRemoteMemberResponse(requestServer(ccm, target, METHOD_PUT, paramKeys, paramValues, null, username, password));
    }

    public ServerResponse.EditRemoteBillResponse editRemoteBill(CustomCertManager ccm, DBProject project, DBBill bill, Map<Long, Long> memberIdToRemoteId) throws IOException, TokenMismatchException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("date");
        paramValues.add(bill.getDate());
        paramKeys.add("what");
        paramValues.add(bill.getWhat());
        paramKeys.add("payer");
        paramValues.add(
                String.valueOf(
                        memberIdToRemoteId.get(bill.getPayerId())
                )
        );
        paramKeys.add("amount");
        paramValues.add(String.valueOf(bill.getAmount()));

        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            paramKeys.add("payed_for");
            String payedFor = "";
            for (long boId : bill.getBillOwersIds()) {
                payedFor += String.valueOf(memberIdToRemoteId.get(boId)) + ",";
            }
            payedFor = payedFor.replaceAll(",$", "");
            paramValues.add(payedFor);

            paramKeys.add("repeat");
            paramValues.add(bill.getRepeat());
            paramKeys.add("paymentmode");
            paramValues.add(bill.getPaymentMode());
            paramKeys.add("categoryid");
            paramValues.add(String.valueOf(bill.getCategoryRemoteId()));

            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId();
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId();
                return new ServerResponse.EditRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_PUT, paramKeys, paramValues));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/"
                        + project.getPassword() + "/bills/" + bill.getRemoteId();
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/bills/" + bill.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();

            for (long boId : bill.getBillOwersIds()) {
                paramKeys.add("payed_for");
                paramValues.add(
                        String.valueOf(
                                memberIdToRemoteId.get(boId)
                        )
                );
            }
        }
        return new ServerResponse.EditRemoteBillResponse(requestServer(ccm, target, METHOD_PUT, paramKeys, paramValues, null, username, password));
    }

    public ServerResponse.DeleteRemoteProjectResponse deleteRemoteProject(CustomCertManager ccm, DBProject project) throws IOException, TokenMismatchException {
        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId();
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId();
                return new ServerResponse.DeleteRemoteProjectResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_DELETE, null, null));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/" + project.getPassword();
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
        }
        return new ServerResponse.DeleteRemoteProjectResponse(requestServer(ccm, target, METHOD_DELETE, null,null, null, username, password));
    }

    public ServerResponse.DeleteRemoteBillResponse deleteRemoteBill(CustomCertManager ccm, DBProject project, long billRemoteId) throws IOException, TokenMismatchException {
        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId() + "/bills/" + billRemoteId;
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/bills/" + billRemoteId;
                return new ServerResponse.DeleteRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_DELETE, null, null));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/"
                        + project.getPassword() + "/bills/" + billRemoteId;
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/bills/" + billRemoteId;
            username = project.getRemoteId();
            password = project.getPassword();
        }
        return new ServerResponse.DeleteRemoteBillResponse(requestServer(ccm, target, METHOD_DELETE, null,null, null, username, password));
    }

    public ServerResponse.CreateRemoteProjectResponse createRemoteProject(CustomCertManager ccm, DBProject project) throws IOException {
        String target = project.getIhmUrl().replaceAll("/+$", "")
                + "/api/projects";
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(project.getName() == null ? "" : project.getName());
        paramKeys.add("contact_email");
        paramValues.add(project.getEmail() == null ? "" : project.getEmail());
        paramKeys.add("password");
        paramValues.add(project.getPassword() == null ? "" : project.getPassword());
        paramKeys.add("id");
        paramValues.add(project.getRemoteId() == null ? "" : project.getRemoteId());
        return new ServerResponse.CreateRemoteProjectResponse(requestServer(ccm, target, METHOD_POST, paramKeys, paramValues, null, null, null));
    }

    public ServerResponse.CreateRemoteBillResponse createRemoteBill(CustomCertManager ccm, DBProject project, DBBill bill, Map<Long, Long> memberIdToRemoteId) throws IOException, TokenMismatchException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("date");
        paramValues.add(bill.getDate());
        paramKeys.add("what");
        paramValues.add(bill.getWhat());
        paramKeys.add("payer");
        paramValues.add(
                String.valueOf(
                        memberIdToRemoteId.get(bill.getPayerId())
                )
        );
        paramKeys.add("amount");
        paramValues.add(String.valueOf(bill.getAmount()));

        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            paramKeys.add("payed_for");
            String payedFor = "";
            for (long boId : bill.getBillOwersIds()) {
                payedFor += String.valueOf(memberIdToRemoteId.get(boId)) + ",";
            }
            payedFor = payedFor.replaceAll(",$", "");
            paramValues.add(payedFor);

            paramKeys.add("repeat");
            paramValues.add(bill.getRepeat());
            paramKeys.add("paymentmode");
            paramValues.add(bill.getPaymentMode());
            paramKeys.add("categoryid");
            paramValues.add(String.valueOf(bill.getCategoryRemoteId()));

            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId() + "/bills";
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/bills";
                return new ServerResponse.CreateRemoteBillResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/"
                        + project.getPassword() + "/bills";
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/bills";
            username = project.getRemoteId();
            password = project.getPassword();

            for (long boId : bill.getBillOwersIds()) {
                paramKeys.add("payed_for");
                paramValues.add(
                        String.valueOf(
                                memberIdToRemoteId.get(boId)
                        )
                );
            }
        }

        return new ServerResponse.CreateRemoteBillResponse(requestServer(ccm, target, METHOD_POST, paramKeys, paramValues, null, username, password));
    }

    public ServerResponse.CreateRemoteMemberResponse createRemoteMember(CustomCertManager ccm, DBProject project, DBMember member) throws IOException, TokenMismatchException {
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(member.getName());

        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId() + "/members";
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/members";
                return new ServerResponse.CreateRemoteMemberResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, paramKeys, paramValues));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/"
                        + project.getPassword() + "/members";
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/members";
            username = project.getRemoteId();
            password = project.getPassword();
        }

        return new ServerResponse.CreateRemoteMemberResponse(requestServer(ccm, target, METHOD_POST, paramKeys, paramValues, null, username, password));
    }

    public ServerResponse.BillsResponse getBills(CustomCertManager ccm, DBProject project, boolean cospendSmartSync) throws JSONException, IOException, TokenMismatchException {
        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            Long tsLastSync = project.getLastSyncedTimestamp();
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId() + "/bills?lastchanged=" + tsLastSync;
                return new ServerResponse.BillsResponse(requestServer(ccm, target, METHOD_GET, null, null,null, username, password), true);
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/bills";
                List<String> paramKeys = new ArrayList<>();
                List<String> paramValues = new ArrayList<>();
                paramKeys.add("lastchanged");
                paramValues.add(String.valueOf(tsLastSync));
                return new ServerResponse.BillsResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, paramKeys, paramValues), true);
            }
            else {
                if (cospendSmartSync) {
                    target = project.getIhmUrl().replaceAll("/+$", "")
                            + "/apiv2/projects/" + project.getRemoteId() + "/"
                            + project.getPassword() + "/bills?lastchanged=" + tsLastSync;
                    return new ServerResponse.BillsResponse(requestServer(ccm, target, METHOD_GET, null, null,null, username, password), true);
                } else {
                    target = project.getIhmUrl().replaceAll("/+$", "")
                            + "/api/projects/" + project.getRemoteId() + "/"
                            + project.getPassword() + "/bills";
                    return new ServerResponse.BillsResponse(requestServer(ccm, target, METHOD_GET, null, null,null, username, password), false);
                }
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/bills";
            username = project.getRemoteId();
            password = project.getPassword();
            return new ServerResponse.BillsResponse(requestServer(ccm, target, METHOD_GET, null, null,null, username, password), false);
        }
    }

    public ServerResponse.MembersResponse getMembers(CustomCertManager ccm, DBProject project) throws JSONException, IOException, TokenMismatchException {
        String target;
        String username = null;
        String password = null;
        if (ProjectType.COSPEND.equals(project.getType())) {
            if (canAccessProjectWithNCLogin(project)) {
                username = this.username;
                password = this.password;
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api-priv/projects/" + project.getRemoteId() + "/members";
            }
            else if (canAccessProjectWithSSO(project)) {
                target = "/index.php/apps/cospend/api-priv/projects/" + project.getRemoteId() + "/members";
                Log.v("YOUP", "SSO target "+target);
                return new ServerResponse.MembersResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null, null));
            }
            else {
                target = project.getIhmUrl().replaceAll("/+$", "")
                        + "/api/projects/" + project.getRemoteId() + "/"
                        + project.getPassword() + "/members";
            }
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/members";
            username = project.getRemoteId();
            password = project.getPassword();
        }
        return new ServerResponse.MembersResponse(requestServer(ccm, target, METHOD_GET, null, null,null, username, password));
    }

    private ResponseData requestServerWithSSO(NextcloudAPI nextcloudAPI, String target, String method, List<String> paramKeys, List<String> paramValues) throws TokenMismatchException {
        StringBuffer result = new StringBuffer();

        Map<String, String> params = null;
        if (paramKeys != null && paramValues != null) {
            params = new HashMap<>();
            for (int i=0; i < paramKeys.size(); i++) {
                String key = paramKeys.get(i);
                String value = paramValues.get(i);
                params.put(key, value);
            }
        }

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
            InputStream inputStream = nextcloudAPI.performNetworkRequest(nextcloudRequest);

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

        } catch (Exception e) {
            // TODO handle errors
            Log.d(getClass().getSimpleName(), "SSO server request error "+e.toString());
        }

        return new VersatileProjectSyncClient.ResponseData(result.toString(), "", 0);
    }

    /**
     * Request-Method for POST, PUT with or without JSON-Object-Parameter
     *
     * @param target Filepath to the wanted function
     * @param method GET, POST, DELETE or PUT
     * @return Body of answer
     * @throws MalformedURLException
     * @throws IOException
     */
    private ResponseData requestServer(CustomCertManager ccm, String target, String method, List<String> paramKeys, List<String> paramValues, String lastETag, String username, String password)
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
        con.setRequestProperty("User-Agent", "MoneyBuster/" + BuildConfig.VERSION_NAME);
        if (lastETag != null && METHOD_GET.equals(method)) {
            con.setRequestProperty("If-None-Match", lastETag);
        }
        con.setConnectTimeout(10 * 1000); // 10 seconds
        Log.d(getClass().getSimpleName(), method + " " + targetURL);
        // send request data (optional)
        byte[] paramData = null;
        if (paramKeys != null) {
            String dataString = "";
            for (int i=0; i < paramKeys.size(); i++) {
                String key = paramKeys.get(i);
                String value = paramValues.get(i);
                if (dataString.length() > 0) {
                    dataString += "&";
                }
                dataString += URLEncoder.encode(key, "UTF-8") + "=";
                dataString += URLEncoder.encode(value, "UTF-8");
            }
            byte[] data = dataString.getBytes();

            Log.d(getClass().getSimpleName(), "Params: " + dataString);
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

        Log.d(TAG, "METHOD : " + method);
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
        Log.i(TAG, "Result length:  " + result.length() + (paramData == null ? "" : "; Request length: " + paramData.length));
        Log.d(TAG, "ETag: " + etag + "; Last-Modified: " + lastModified + " (" + con.getHeaderField("Last-Modified") + ")");
        // return these header fields since they should only be saved after successful processing the result!
        return new ResponseData(result.toString(), etag, lastModified);
    }
}
