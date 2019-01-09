package net.eneiluj.moneybuster.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;
import android.support.annotation.WorkerThread;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
//import android.util.ArrayMap;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import at.bitfire.cert4android.CustomCertManager;
import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.CreditDebt;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBBillOwer;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.Transaction;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;

/**
 * Some helper functionality in alike the Android support library.
 * Currently, it offers methods for working with HTML string resources.
 */
public class SupportUtil {

    /**
     * Creates a {@link Spanned} from a HTML string on all SDK versions.
     *
     * @param source Source string with HTML markup
     * @return Spannable for using in a {@link TextView}
     * @see Html#fromHtml(String)
     * @see Html#fromHtml(String, int)
     */
    public static Spanned fromHtml(String source) {
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(source);
        }
    }

    /**
     * Fills a {@link TextView} with HTML content and activates links in that {@link TextView}.
     *
     * @param view       The {@link TextView} which should be filled.
     * @param stringId   The string resource containing HTML tags (escaped by <code>&lt;</code>)
     * @param formatArgs Arguments for the string resource.
     */
    public static void setHtml(TextView view, int stringId, Object... formatArgs) {
        view.setText(SupportUtil.fromHtml(view.getResources().getString(stringId, formatArgs)));
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Create a new {@link HttpURLConnection} for strUrl.
     * If protocol equals https, then install CustomCertManager in {@link SSLContext}.
     *
     * @param ccm
     * @param strUrl
     * @return HttpURLConnection with custom trust manager
     * @throws MalformedURLException
     * @throws IOException
     */
    public static HttpURLConnection getHttpURLConnection(CustomCertManager ccm, String strUrl) throws MalformedURLException, IOException {
        URL url = new URL(strUrl);
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        if (ccm != null && url.getProtocol().equals("https")) {
            HttpsURLConnection httpsCon = (HttpsURLConnection) httpCon;
            httpsCon.setHostnameVerifier(ccm.hostnameVerifier(httpsCon.getHostnameVerifier()));
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{ccm}, null);
                httpsCon.setSSLSocketFactory(sslContext.getSocketFactory());
            } catch (NoSuchAlgorithmException e) {
                Log.e(SupportUtil.class.getSimpleName(), "Exception", e);
                // ignore, use default TrustManager
            } catch (KeyManagementException e) {
                Log.e(SupportUtil.class.getSimpleName(), "Exception", e);
                // ignore, use default TrustManager
            }
        }
        return httpCon;
    }

    @WorkerThread
    public static CustomCertManager getCertManager(Context ctx) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        return new CustomCertManager(ctx, preferences.getBoolean(ctx.getString(R.string.pref_key_trust_system_certs), true));
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch(NumberFormatException e) {
            return false;
        } catch(NullPointerException e) {
            return false;
        }
        return true;
    }

    public final static boolean isValidEmail(CharSequence target) {
        if (target == null)
            return false;

        return android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    public static int getStatsOfProject(long projId, MoneyBusterSQLiteOpenHelper db,
                                  Map<Long, Integer> membersNbBills,
                                  Map<Long, Double> membersBalance,
                                  Map<Long, Double> membersPaid,
                                  Map<Long, Double> membersSpent) {
        int nbBills = 0;
        Map<Long, Double> membersWeight = new HashMap<>();

        List<DBBill> dbBills = db.getBillsOfProject(projId);
        List<DBMember> dbMembers = db.getMembersOfProject(projId);

        // init
        for (DBMember m : dbMembers) {
            membersNbBills.put(m.getId(), 0);
            membersBalance.put(m.getId(), 0.0);
            membersPaid.put(m.getId(), 0.0);
            membersSpent.put(m.getId(), 0.0);
            membersWeight.put(m.getId(), m.getWeight());
        }

        for (DBBill b : dbBills) {
            if (b.getState() != DBBill.STATE_DELETED) {
                nbBills++;
                membersNbBills.put(
                        b.getPayerId(),
                        membersNbBills.get(b.getPayerId()) + 1
                );
                double amount = b.getAmount();
                membersBalance.put(
                        b.getPayerId(),
                        membersBalance.get(b.getPayerId()) + amount
                );
                membersPaid.put(
                        b.getPayerId(),
                        membersPaid.get(b.getPayerId()) + amount
                );
                double nbOwerShares = 0.0;
                for (DBBillOwer bo : b.getBillOwers()) {
                    nbOwerShares += membersWeight.get(bo.getMemberId());
                }
                for (DBBillOwer bo : b.getBillOwers()) {
                    double owerWeight = membersWeight.get(bo.getMemberId());
                    double spent = amount/nbOwerShares*owerWeight;
                    membersBalance.put(
                            bo.getMemberId(),
                            membersBalance.get(bo.getMemberId()) - spent
                    );
                    membersSpent.put(
                            bo.getMemberId(),
                            membersSpent.get(bo.getMemberId()) + spent
                    );
                }
            }
        }
        return nbBills;
    }

    public static List<Transaction> settleBills(Map<Long, Double> membersBalance) {
        List<CreditDebt> credits = new ArrayList<>();
        List<CreditDebt> debts = new ArrayList<>();
        List<Transaction> transactions = new ArrayList<>();

        // Create lists of credits and debts
        for (long memberId : membersBalance.keySet()) {
            double rBalance = Math.round( (membersBalance.get(memberId)) * 100.0 ) / 100.0;
            if (rBalance > 0) {
                credits.add(new CreditDebt(memberId, membersBalance.get(memberId)));
            }
            else if (rBalance < 0) {
                debts.add(new CreditDebt(memberId, -membersBalance.get(memberId)));
            }
        }

        // Try and find exact matches
        for (CreditDebt credit : credits) {
            List<CreditDebt> match = exactMatch(credit.getBalance(), debts, 0);
            if (match != null && match.size() > 0) {
                for (CreditDebt m : match) {
                    transactions.add(new Transaction(m.getMemberId(), credit.getMemberId(), m.getBalance()));
                    debts.remove(m);
                }
                credits.remove(credit);
            }
        }

        // Split any remaining debts & credits
        while (credits.size() > 0 && debts.size() > 0) {
            if (credits.get(0).getBalance() > debts.get(0).getBalance()) {
                transactions.add(new Transaction(
                        debts.get(0).getMemberId(),
                        credits.get(0).getMemberId(),
                        debts.get(0).getBalance())
                );
                credits.get(0).setBalance(credits.get(0).getBalance() - debts.get(0).getBalance());
                debts.remove(0);
            }
            else {
                transactions.add(new Transaction(
                        debts.get(0).getMemberId(),
                        credits.get(0).getMemberId(),
                        credits.get(0).getBalance())
                );
                debts.get(0).setBalance(debts.get(0).getBalance() - credits.get(0).getBalance());
                credits.remove(0);
            }
        }

        return transactions;
    }

    private static List<CreditDebt> exactMatch(double credit, List<CreditDebt> debts, int startIndex) {
        if (startIndex >= debts.size()) {
            return null;
        }
        if (debts.get(startIndex).getBalance() > credit) {
            return exactMatch(credit, debts, startIndex+1);
        }
        else if (debts.get(startIndex).getBalance() == credit) {
            List<CreditDebt> res = new ArrayList<>();
            res.add(debts.get(startIndex));
            return res;
        }
        else {
            List<CreditDebt> match = exactMatch(credit - debts.get(startIndex).getBalance(), debts, startIndex+1);
            if (match != null && match.size() > 0) {
                match.add(debts.get(startIndex));
            }
            else {
                match = exactMatch(credit, debts, startIndex+1);
            }
            return match;
        }
    }
}
