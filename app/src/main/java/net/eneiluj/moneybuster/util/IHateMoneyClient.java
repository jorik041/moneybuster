package net.eneiluj.moneybuster.util;

import android.support.annotation.WorkerThread;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import at.bitfire.cert4android.CustomCertManager;
import net.eneiluj.moneybuster.BuildConfig;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;

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
        String target;
        String username = null;
        String password = null;
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/" + project.getPassword();
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
        }

        return new ServerResponse.ProjectResponse(requestServer(ccm, target, METHOD_GET, null, null, lastETag, username, password));
    }

    public ServerResponse.EditRemoteProjectResponse editRemoteProject(CustomCertManager ccm, DBProject project, String newName, String newEmail, String newPassword) throws IOException {
        String target;
        String username = null;
        String password = null;
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/" + project.getPassword();
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId();
            //https://ihatemoney.org/api/projects/demo
            username = project.getRemoteId();
            password = project.getPassword();
        }
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(newName == null ? "" : newName);
        paramKeys.add("contact_email");
        paramValues.add(newEmail == null ? "" : newEmail);
        paramKeys.add("password");
        paramValues.add(newPassword == null ? "" : newPassword);
        return new ServerResponse.EditRemoteProjectResponse(requestServer(ccm, target, METHOD_PUT, paramKeys, paramValues, null, username, password));
    }

    public ServerResponse.EditRemoteMemberResponse editRemoteMember(CustomCertManager ccm, DBProject project, DBMember member) throws IOException {
        String target;
        String username = null;
        String password = null;
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/"
                    + project.getPassword() + "/members/" + member.getRemoteId();
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/members/" + member.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
        }
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(member.getName());
        paramKeys.add("weight");
        paramValues.add(String.valueOf(member.getWeight()));
        paramKeys.add("activated");
        paramValues.add(member.isActivated() ? "true" : "false");
        return new ServerResponse.EditRemoteMemberResponse(requestServer(ccm, target, METHOD_PUT, paramKeys, paramValues, null, username, password));
    }

    public ServerResponse.EditRemoteBillResponse editRemoteBill(CustomCertManager ccm, DBProject project, DBBill bill, Map<Long, Long> memberIdToRemoteId) throws IOException {
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
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/"
                    + project.getPassword() + "/bills/" + bill.getRemoteId();

            paramKeys.add("payed_for");
            String payedFor = "";
            for (long boId : bill.getBillOwersIds()) {
                payedFor += String.valueOf(memberIdToRemoteId.get(boId)) + ",";
            }
            payedFor = payedFor.replaceAll(",$", "");
            paramValues.add(payedFor);
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

    public ServerResponse.DeleteRemoteProjectResponse deleteRemoteProject(CustomCertManager ccm, DBProject project) throws IOException {
        String target;
        String username = null;
        String password = null;
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/" + project.getPassword();
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId();
            username = project.getRemoteId();
            password = project.getPassword();
        }
        return new ServerResponse.DeleteRemoteProjectResponse(requestServer(ccm, target, METHOD_DELETE, null,null, null, username, password));
    }

    public ServerResponse.DeleteRemoteBillResponse deleteRemoteBill(CustomCertManager ccm, DBProject project, long billRemoteId) throws IOException {
        String target;
        String username = null;
        String password = null;
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/"
                    + project.getPassword() + "/bills/" + billRemoteId;
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

    public ServerResponse.CreateRemoteBillResponse createRemoteBill(CustomCertManager ccm, DBProject project, DBBill bill, Map<Long, Long> memberIdToRemoteId) throws IOException {
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
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/"
                    + project.getPassword() + "/bills";

            paramKeys.add("payed_for");
            String payedFor = "";
            for (long boId : bill.getBillOwersIds()) {
                payedFor += String.valueOf(memberIdToRemoteId.get(boId)) + ",";
            }
            payedFor = payedFor.replaceAll(",$", "");
            paramValues.add(payedFor);
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

    public ServerResponse.CreateRemoteMemberResponse createRemoteMember(CustomCertManager ccm, DBProject project, DBMember member) throws IOException {
        String target;
        String username = null;
        String password = null;
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/"
                    + project.getPassword() + "/members";
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/members";
            username = project.getRemoteId();
            password = project.getPassword();
        }
        List<String> paramKeys = new ArrayList<>();
        List<String> paramValues = new ArrayList<>();
        paramKeys.add("name");
        paramValues.add(member.getName());
        return new ServerResponse.CreateRemoteMemberResponse(requestServer(ccm, target, METHOD_POST, paramKeys, paramValues, null, username, password));
    }

    public ServerResponse.BillsResponse getBills(CustomCertManager ccm, DBProject project) throws JSONException, IOException {
        String target;
        String username = null;
        String password = null;
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/"
                    + project.getPassword() + "/bills";
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/bills";
            username = project.getRemoteId();
            password = project.getPassword();
        }
        return new ServerResponse.BillsResponse(requestServer(ccm, target, METHOD_GET, null, null,null, username, password));
    }

    public ServerResponse.MembersResponse getMembers(CustomCertManager ccm, DBProject project) throws JSONException, IOException {
        String target;
        String username = null;
        String password = null;
        if (project.getIhmUrl().contains("index.php/apps/cospend")) {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/"
                    + project.getPassword() + "/members";
        }
        else {
            target = project.getIhmUrl().replaceAll("/+$", "")
                    + "/api/projects/" + project.getRemoteId() + "/members";
            username = project.getRemoteId();
            password = project.getPassword();
        }
        return new ServerResponse.MembersResponse(requestServer(ccm, target, METHOD_GET, null, null,null, username, password));
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
        con.setRequestProperty("User-Agent", "ihatemoney-android/" + BuildConfig.VERSION_NAME);
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
