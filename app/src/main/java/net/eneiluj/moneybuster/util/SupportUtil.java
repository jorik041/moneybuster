package net.eneiluj.moneybuster.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.CreditDebt;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBBillOwer;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.Transaction;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import at.bitfire.cert4android.CustomCertManager;

//import android.preference.PreferenceManager;
//import android.util.ArrayMap;

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

    public static boolean isDouble(String s) {
        try {
            Double.parseDouble(s);
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
        List<DBMember> dbMembers = db.getMembersOfProject(projId, null);

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

    public static double round2(double n) {
        double r = Math.round( Math.abs(n) * 100.0 ) / 100.0;
        if (n < 0.0) r = -r;
        return r;
    }

    public static List<Transaction> settleBills(List<DBMember> members, Map<Long, Double> membersBalance) {
        List<CreditDebt> crediters = new ArrayList<>();
        List<CreditDebt> debiters = new ArrayList<>();

        // Create lists of credits and debts
        for (DBMember m : members) {
            long memberId = m.getId();
            double balance = membersBalance.get(memberId);

            if (round2(balance) > 0.0) {
                crediters.add(new CreditDebt(memberId, balance));
            }
            else if (round2(balance) < 0.0) {
                debiters.add(new CreditDebt(memberId, balance));
            }
        }

        return reduceBalance(crediters, debiters, null);
    }

    public static List<Transaction> reduceBalance(List<CreditDebt> crediters, List<CreditDebt> debiters, List<Transaction> results) {
        if (debiters.size() == 0 || crediters.size() == 0) {
            return results;
        }

        if (results == null) {
            results = new ArrayList<>();
        }

        Collections.sort(crediters, new Comparator<CreditDebt>() {
            @Override
            public int compare(CreditDebt cd2, CreditDebt cd1)
            {
                if (cd1.getBalance() == cd2.getBalance()) {
                    return 0;
                }
                else {
                    return (cd1.getBalance() < cd2.getBalance()) ? 1 : -1;
                }
            }
        });
        //Log.e(SupportUtil.class.getSimpleName(), "CREEEEEEEEEEEEEEEEEE");
        for (CreditDebt c : crediters) {
            Log.e(SupportUtil.class.getSimpleName(), "* "+c.getMemberId()+" : "+c.getBalance());
        }
        Collections.sort(debiters, new Comparator<CreditDebt>() {
            @Override
            public int compare(CreditDebt cd2, CreditDebt cd1)
            {
                if (cd1.getBalance() == cd2.getBalance()) {
                    return 0;
                }
                else {
                    return (cd1.getBalance() > cd2.getBalance()) ? 1 : -1;
                }
            }
        });

        CreditDebt deb = debiters.remove(debiters.size()-1);
        long debiter = deb.getMemberId();
        double debiterBalance = deb.getBalance();

        CreditDebt cred = crediters.remove(crediters.size()-1);
        long crediter = cred.getMemberId();
        double crediterBalance = cred.getBalance();

        double amount;
        if (Math.abs(debiterBalance) > Math.abs(crediterBalance)) {
            amount = Math.abs(crediterBalance);
        }
        else {
            amount = Math.abs(debiterBalance);
        }

        results.add(new Transaction(debiter, crediter, amount));

        double newDebiterBalance = debiterBalance + amount;
        if (newDebiterBalance < 0.0) {
            debiters.add(new CreditDebt(debiter, newDebiterBalance));
            Collections.sort(debiters, new Comparator<CreditDebt>() {
                @Override
                public int compare(CreditDebt cd2, CreditDebt cd1)
                {
                    if (cd1.getBalance() == cd2.getBalance()) {
                        return 0;
                    }
                    else {
                        return (cd1.getBalance() > cd2.getBalance()) ? 1 : -1;
                    }
                }
            });
        }

        double newCrediterBalance = crediterBalance - amount;
        if (newCrediterBalance > 0.0) {
            crediters.add(new CreditDebt(crediter, newCrediterBalance));
            Collections.sort(crediters, new Comparator<CreditDebt>() {
                @Override
                public int compare(CreditDebt cd2, CreditDebt cd1)
                {
                    if (cd1.getBalance() == cd2.getBalance()) {
                        return 0;
                    }
                    else {
                        return (cd1.getBalance() < cd2.getBalance()) ? 1 : -1;
                    }
                }
            });
        }

        return reduceBalance(crediters, debiters, results);
    }

    public static int getVersionCode(Context context) {
        int versionCode = 9999;
        try {
            PackageInfo pinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionCode = pinfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }

    public static String getVersionName(Context context) {
        String versionName = "0.0.0";
        try {
            PackageInfo pinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = pinfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionName;
    }


}
