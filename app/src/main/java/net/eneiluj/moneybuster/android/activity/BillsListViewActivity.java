package net.eneiluj.moneybuster.android.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
//import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback;
import android.text.InputType;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.fragment.NewProjectFragment;
import net.eneiluj.moneybuster.model.Category;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBBillOwer;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.Item;
import net.eneiluj.moneybuster.model.ItemAdapter;
import net.eneiluj.moneybuster.model.MenuProject;
import net.eneiluj.moneybuster.model.NavigationAdapter;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.persistence.MoneyBusterServerSyncHelper;
import net.eneiluj.moneybuster.persistence.LoadBillsListTask;
import net.eneiluj.moneybuster.util.ICallback;
import net.eneiluj.moneybuster.util.SpendClientUtil;
import net.eneiluj.moneybuster.util.SupportUtil;

import static net.eneiluj.moneybuster.android.activity.EditProjectActivity.PARAM_PROJECT_ID;

public class BillsListViewActivity extends AppCompatActivity implements ItemAdapter.BillClickListener {

    private final static int PERMISSION_LOCATION = 1;
    public static boolean DEBUG = true;
    public static final String BROADCAST_EXTRA_PARAM = "net.eneiluj.moneybuster.broadcast_extra_param";
    public static final String BROADCAST_ERROR_MESSAGE = "net.eneiluj.moneybuster.broadcast_error_message";

    private final static int PERMISSION_FOREGROUND_SERVICE = 1;

    private static final String TAG = BillsListViewActivity.class.getSimpleName();

    public final static String CREATED_BILL = "net.eneiluj.moneybuster.created_bill";
    public final static String CREATED_PROJECT = "net.eneiluj.moneybuster.created_project";
    public final static String EDITED_PROJECT = "net.eneiluj.moneybuster.edited_project";
    public final static String DELETED_PROJECT = "net.eneiluj.moneybuster.deleted_project";
    public final static String DELETED_BILL = "net.eneiluj.moneybuster.deleted_bill";
    public final static String CREDENTIALS_CHANGED = "net.eneiluj.moneybuster.CREDENTIALS_CHANGED";
    public static final String ADAPTER_KEY_ALL = "all";
    public static final String ADAPTER_KEY_ENABLED = "enabled";
    public static final String ADAPTER_KEY_PHONETRACK = "pt";
    public static final String ADAPTER_KEY_CUSTOM = "custom";
    public static final String CATEGORY_PHONETRACK = "pt";
    public static final String CATEGORY_CUSTOM = "cu";

    public final static String UPDATED_LOGJOBS = "net.eneiluj.moneybuster.UPDATED_LOGJOBS";
    public final static String UPDATED_LOGJOB_ID = "net.eneiluj.moneybuster.UPDATED_LOGJOB_ID";

    private static final String SAVED_STATE_NAVIGATION_SELECTION = "navigationSelection";
    private static final String SAVED_STATE_NAVIGATION_ADAPTER_SLECTION = "navigationAdapterSelection";
    private static final String SAVED_STATE_NAVIGATION_OPEN = "navigationOpen";

    private final static int create_bill_cmd = 0;
    private final static int show_single_bill_cmd = 1;
    private final static int server_settings = 2;
    private final static int about = 3;
    private final static int addproject = 4;
    private final static int removeproject = 5;
    private final static int editproject = 6;

    //private HashMap<Long, Double> membersBalance;


    @BindView(R.id.logjobsListActivityActionBar)
    Toolbar toolbar;
    @BindView(R.id.drawerLayout)
    DrawerLayout drawerLayout;
    @BindView(R.id.projects)
    Spinner projects;
    ArrayAdapter<MenuProject> projectsAdapter;
    @BindView(R.id.swiperefreshlayout)
    SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.fabDrawer_add_project)
    com.github.clans.fab.FloatingActionButton fabAddProject;
    @BindView(R.id.fabDrawer_add_member)
    com.github.clans.fab.FloatingActionButton fabAddMember;
    @BindView(R.id.floatingMenuDrawer)
    com.github.clans.fab.FloatingActionMenu fabMenuDrawerAdd;
    @BindView(R.id.floatingMenuDrawerEdit)
    com.github.clans.fab.FloatingActionMenu fabMenuDrawerEdit;
    @BindView(R.id.fabDrawer_edit_member)
    com.github.clans.fab.FloatingActionButton fabEditMember;
    @BindView(R.id.fabDrawer_statistics)
    com.github.clans.fab.FloatingActionButton fabStatistics;
    @BindView(R.id.fabDrawer_edit_project)
    com.github.clans.fab.FloatingActionButton fabEditProject;
    @BindView(R.id.fabDrawer_remove_project)
    com.github.clans.fab.FloatingActionButton fabRemoveProject;
    @BindView(R.id.fab_add_bill)
    android.support.design.widget.FloatingActionButton fabMenu;
    @BindView(R.id.navigationList)
    RecyclerView listNavigationMembers;
    @BindView(R.id.navigationMenu)
    RecyclerView listNavigationMenu;
    @BindView(R.id.recycler_view)
    RecyclerView listView;

    private ActionBarDrawerToggle drawerToggle;
    private ItemAdapter adapter = null;
    private NavigationAdapter adapterMembers;
    private NavigationAdapter.NavigationItem itemAll, itemEnabled, itemPhonetrack, itemCustom, itemUncategorized;
    private Category navigationSelection = new Category(null, null);
    private String navigationOpen = "";
    private ActionMode mActionMode;
    private MoneyBusterSQLiteOpenHelper db = null;
    private SearchView searchView = null;
    private ICallback syncCallBack = new ICallback() {
        @Override
        public void onFinish() {
            adapter.clearSelection();
            if (mActionMode != null) {
                mActionMode.finish();
            }
            refreshLists();
            swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public void onFinish(String result, String message) {
        }

        @Override
        public void onScheduled() {
            swipeRefreshLayout.setRefreshing(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // First Run Wizard
        /*if (!MoneyBusterServerSyncHelper.isConfigured(this)) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivityForResult(settingsIntent, server_settings);
        }*/
        String categoryAdapterSelectedItem = ADAPTER_KEY_ALL;
        if (savedInstanceState != null) {
            navigationSelection = (Category) savedInstanceState.getSerializable(SAVED_STATE_NAVIGATION_SELECTION);
            navigationOpen = savedInstanceState.getString(SAVED_STATE_NAVIGATION_OPEN);
            categoryAdapterSelectedItem = savedInstanceState.getString(SAVED_STATE_NAVIGATION_ADAPTER_SLECTION);
        }

        setContentView(R.layout.drawer_layout);
        ButterKnife.bind(this);

        db = MoneyBusterSQLiteOpenHelper.getInstance(this);

        setupActionBar();
        setupBillsList();
        setupNavigationMenu();
        setupMembersNavigationList(categoryAdapterSelectedItem);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (DEBUG) { Log.d(TAG, "[request 1 permission]"); }
            ActivityCompat.requestPermissions(BillsListViewActivity.this, new String[]{Manifest.permission.FOREGROUND_SERVICE}, PERMISSION_FOREGROUND_SERVICE);
        }

        /*Map<String, Integer> enabled = db.getEnabledCount();
        int nbEnabledLogjobs = enabled.containsKey("1") ? enabled.get("1") : 0;
        if (nbEnabledLogjobs > 0) {
            // start loggerservice !
            Intent intent = new Intent(BillsListViewActivity.this, LoggerService.class);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                startService(intent);
            } else {
                startForegroundService(intent);
            }
        }*/

        // create project if there isn't any
        if (db.getProjects().isEmpty()) {
            Intent newProjectIntent = new Intent(getApplicationContext(), NewProjectActivity.class);
            newProjectIntent.putExtra(NewProjectFragment.PARAM_DEFAULT_URL, "https://ihatemoney.org");
            startActivityForResult(newProjectIntent, addproject);
        }
    }

    @Override
    protected void onResume() {
        // refresh and sync every time the activity gets visible
        if (projects.getCount() > 0) {
            refreshLists();
        }
        swipeRefreshLayout.setRefreshing(false);
        db.getMoneyBusterServerSyncHelper().addCallbackPull(syncCallBack);
        if (DEBUG) { Log.d(TAG, "[onResume]"); }
        synchronize();

        super.onResume();

        registerBroadcastReceiver();
        // TODO update number of late positions
        //updateStatuses();
    }

    /**
     * On pause
     */
    @Override
    protected void onPause() {
        if (DEBUG) { Log.d(TAG, "[onPause]"); }
        unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }


    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.syncState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(SAVED_STATE_NAVIGATION_SELECTION, navigationSelection);
        outState.putString(SAVED_STATE_NAVIGATION_ADAPTER_SLECTION, adapterMembers.getSelectedItem());
        outState.putString(SAVED_STATE_NAVIGATION_OPEN, navigationOpen);
    }

    private void setupActionBar() {
        setSupportActionBar(toolbar);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.action_drawer_open, R.string.action_drawer_close);
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayout.addDrawerListener(drawerToggle);
    }

    private void setupBillsList() {
        initList();
        // Pull to Refresh
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (DEBUG) {
                    Log.d(TAG, "[onRefresh]");
                }
                if (db.getMoneyBusterServerSyncHelper().isSyncPossible()) {
                    synchronize();
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(getApplicationContext(), getString(R.string.error_sync, getString(SpendClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show();
                }
                // TODO synchronize
                /*if (db.getLocationCount() > 0) {
                    Intent syncIntent = new Intent(BillsListViewActivity.this, WebTrackService.class);
                    startService(syncIntent);
                    showToast(getString(R.string.uploading_started));
                }
                else {
                    swipeRefreshLayout.setRefreshing(false);
                }*/
            }
        });

        fabAddProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent newProjectIntent = new Intent(getApplicationContext(), NewProjectActivity.class);
                if (projectsAdapter.getCount() > 0) {
                    long pid = projectsAdapter.getItem(0).getId();
                    String url = db.getProject(pid).getIhmUrl();
                    newProjectIntent.putExtra(NewProjectFragment.PARAM_DEFAULT_URL, url);
                } else {
                    newProjectIntent.putExtra(NewProjectFragment.PARAM_DEFAULT_URL, "https://ihatemoney.org");
                }
                startActivityForResult(newProjectIntent, addproject);
                fabMenuDrawerAdd.close(false);
            }
        });
        fabAddMember.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        new ContextThemeWrapper(
                                view.getContext(),
                                R.style.Theme_AppCompat_DayNight_Dialog
                        )
                );
                builder.setTitle(getString(R.string.add_member_dialog_title));

                // Set up the input
                final EditText input = new EditText(getApplicationContext());
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                input.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                builder.setView(input);

                // Set up the buttons
                builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String memberName = input.getText().toString();
                        MenuProject mproj = (MenuProject) projects.getSelectedItem();
                        if (mproj != null) {
                            if (!memberName.equals("")) {
                                List<DBMember> members = db.getMembersOfProject(mproj.getId());
                                List<String> memberNames = new ArrayList<>();
                                for (DBMember m : members) {
                                    memberNames.add(m.getName());
                                }
                                if (!memberNames.contains(memberName)) {
                                    db.addMemberAndSync(
                                            new DBMember(0, 0, mproj.getId(), memberName,
                                                    true, 1, DBBill.STATE_ADDED)
                                    );
                                    refreshLists();
                                } else {
                                    showToast(getString(R.string.member_already_exists));
                                }
                            } else {
                                showToast(getString(R.string.member_edit_empty_name));
                            }
                        }
                        fabMenuDrawerAdd.close(false);
                        //new LoadCategoryListTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                });
                builder.setNegativeButton(getString(R.string.simple_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        fabMenuDrawerAdd.close(false);
                    }
                });

                builder.show();
            }
        });
        fabMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent createIntent = new Intent(getApplicationContext(), EditBillActivity.class);
                MenuProject mproj = (MenuProject) projects.getSelectedItem();
                if (mproj != null) {
                    createIntent.putExtra(EditBillActivity.PARAM_PROJECT_ID, mproj.getId());
                }
                //createIntent.putExtra(EditBillActivity.PARAM_MEMBERS_BALANCE, membersBalance);
                startActivityForResult(createIntent, create_bill_cmd);
            }
        });

        fabEditProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MenuProject proj = (MenuProject) projects.getSelectedItem();
                if (proj != null) {
                    Intent editProjectIntent = new Intent(getApplicationContext(), EditProjectActivity.class);
                    editProjectIntent.putExtra(PARAM_PROJECT_ID, proj.getId());
                    startActivityForResult(editProjectIntent, editproject);

                    fabMenuDrawerEdit.close(false);
                    drawerLayout.closeDrawers();
                }
            }
        });

        fabRemoveProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        new ContextThemeWrapper(
                                view.getContext(),
                                R.style.Theme_AppCompat_DayNight_Dialog
                        )
                );
                builder.setTitle(getString(R.string.confirm_remove_project_dialog_title));
                builder.setMessage(getString(R.string.confirm_remove_project_dialog_message));

                // Set up the buttons
                builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MenuProject proj = (MenuProject) projects.getSelectedItem();
                        if (proj != null) {
                            db.deleteProject(proj.getId());
                            projectsAdapter.remove(proj);
                            projectsAdapter.notifyDataSetChanged();
                            if (projects.getCount() > 0) {
                                projects.setSelection(0);
                                Log.v(TAG, "set selection 0");
                            }

                            fabMenuDrawerEdit.close(false);
                            drawerLayout.closeDrawers();
                            refreshLists();
                        }
                    }
                });
                builder.setNegativeButton(getString(R.string.simple_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
            }
        });

        fabEditMember.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                // it was like that before...
                /*final String selectedMemberIdStr = adapterMembers.getSelectedItem();

                if (selectedMemberIdStr != null && !selectedMemberIdStr.equals("all")) {

                    long selectedMemberId = Long.valueOf(selectedMemberIdStr);
                    editMember(view, selectedMemberId);
                }*/

                MenuProject proj = (MenuProject) projects.getSelectedItem();
                if (proj != null) {
                    final List<DBMember> members = db.getMembersOfProject(proj.getId());
                    List<String> memberNames = new ArrayList<>();
                    for (DBMember m : members) {
                        memberNames.add(m.getName());
                    }
                    CharSequence[] namescs = memberNames.toArray(new CharSequence[memberNames.size()]);

                    AlertDialog.Builder selectBuilder = new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), R.style.Theme_AppCompat_DayNight_Dialog));
                    selectBuilder.setTitle(getString(R.string.choose_member_to_edit));
                    selectBuilder.setSingleChoiceItems(namescs, -1, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // user checked an item
                            editMember(view, members.get(which).getId());
                            dialog.dismiss();
                        }
                    });

                    // add OK and Cancel buttons
                    selectBuilder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });
                    selectBuilder.setNegativeButton(getString(R.string.simple_cancel), null);

                    AlertDialog selectDialog = selectBuilder.create();
                    selectDialog.show();
                }
            }
        });

        fabStatistics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                MenuProject proj = (MenuProject) projects.getSelectedItem();
                if (proj != null) {
                    // get stats
                    Map<Long, Integer> membersNbBills = new ArrayMap<>();
                    HashMap<Long, Double> membersBalance = new HashMap<>();
                    HashMap<Long, Double> membersPaid = new HashMap<>();
                    HashMap<Long, Double> membersSpent = new HashMap<>();

                    NumberFormat numberFormatter = new DecimalFormat("#0.00");

                    int nbBills = SupportUtil.getStatsOfProject(
                            proj.getId(), db,
                            membersNbBills, membersBalance, membersPaid, membersSpent
                    );

                    // generate the dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            new ContextThemeWrapper(
                                    view.getContext(),
                                    R.style.Theme_AppCompat_DayNight_Dialog
                            )
                    );
                    builder.setTitle(getString(R.string.statistic_dialog_title));


                    final View tView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.statistic_table, null);
                    TextView hwho = tView.findViewById(R.id.header_who);
                    hwho.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    TextView hpaid = tView.findViewById(R.id.header_paid);
                    hpaid.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    TextView hspent = tView.findViewById(R.id.header_spent);
                    hspent.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    TextView hbalance = tView.findViewById(R.id.header_balance);
                    hbalance.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    final TableLayout tl = tView.findViewById(R.id.statTable);

                    for (DBMember m : db.getMembersOfProject(proj.getId())) {
                        View row = LayoutInflater.from(getApplicationContext()).inflate(R.layout.statistic_row, null);
                        TextView wv = row.findViewById(R.id.stat_who);
                        wv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                        wv.setText(m.getName());

                        TextView pv = row.findViewById(R.id.stat_paid);
                        pv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                        double rpaid = Math.round( (membersPaid.get(m.getId())) * 100.0 ) / 100.0;
                        if (rpaid == 0.0) {
                            pv.setText("--");
                        }
                        else {
                            pv.setText(numberFormatter.format(rpaid));
                        }

                        TextView sv = row.findViewById(R.id.stat_spent);
                        sv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                        double rspent = Math.round( (membersSpent.get(m.getId())) * 100.0 ) / 100.0;
                        if (rspent == 0.0) {
                            sv.setText("--");
                        }
                        else {
                            sv.setText(numberFormatter.format(rspent));
                        }


                        TextView bv = row.findViewById(R.id.stat_balance);
                        double rbalance = Math.round( (membersBalance.get(m.getId())) * 100.0 ) / 100.0;
                        String sign = "";
                        if (rbalance > 0) {
                            bv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.green));
                            sign = "+";
                        }
                        else if (rbalance < 0) {
                            bv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.red));
                        }
                        else {
                            bv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                        }
                        bv.setText(sign + numberFormatter.format(rbalance));


                        tl.addView(row);
                    }

                    builder.setView(tView).setIcon(R.drawable.ic_chart_grey_24dp);
                    builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                    builder.show();
                    fabMenuDrawerEdit.close(false);
                }
            }
        });
    }

    private void editMember(View view, long memberId) {
        final DBMember memberToEdit = db.getMember(memberId);

        Log.v(TAG, "MEMBER ID " + memberId);

        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(
                        view.getContext(),
                        R.style.Theme_AppCompat_DayNight_Dialog
                )
        );
        builder.setTitle(getString(R.string.edit_member_dialog_title));

        // Set up the inputs
        final View iView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.items_editmember_dialog, null);
        EditText nv = iView.findViewById(R.id.editMemberName);
        nv.setText(memberToEdit.getName());
        nv.setInputType(InputType.TYPE_CLASS_TEXT);
        nv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
        EditText we = iView.findViewById(R.id.editMemberWeight);
        we.setText(String.valueOf(memberToEdit.getWeight()));
        we.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));

        TextView tv = iView.findViewById(R.id.editMemberNameLabel);
        tv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
        TextView wv = iView.findViewById(R.id.editMemberWeightLabel);
        wv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
        CheckBox ch = iView.findViewById(R.id.editMemberActivated);
        ch.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
        ch.setChecked(memberToEdit.isActivated());

                    /*final EditText input = new EditText(getApplicationContext());
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                    input.setText(memberToEdit.getName());*/
        builder.setView(iView);

        // Set up the buttons
        builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                EditText nvi = iView.findViewById(R.id.editMemberName);
                String newMemberName = nvi.getText().toString();

                EditText wvi = iView.findViewById(R.id.editMemberWeight);
                double newMemberWeight = 1.0;
                try {
                    newMemberWeight = Double.valueOf(wvi.getText().toString());
                }
                catch (Exception e) {
                    showToast(getString(R.string.member_edit_weight_error));
                    return;
                }

                CheckBox cvi = iView.findViewById(R.id.editMemberActivated);
                boolean newActivated = cvi.isChecked();

                MenuProject mproj = (MenuProject) projects.getSelectedItem();
                if (mproj != null) {
                    if (!newMemberName.isEmpty() && !newMemberName.equals("")) {
                        db.updateMemberAndSync(memberToEdit, newMemberName, newMemberWeight, newActivated);
                        refreshLists();
                        // this was used to programmatically select member
                        //navigationSelection = new Category(newMemberName, memberToEdit.getId());
                    } else {
                        showToast(getString(R.string.member_edit_empty_name));
                    }
                }
                fabMenuDrawerEdit.close(false);
            }
        });
        builder.setNegativeButton(getString(R.string.simple_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                fabMenuDrawerEdit.close(false);
            }
        });

        builder.show();
    }

    private void setupMembersNavigationList(final String selectedItem) {
        itemAll = new NavigationAdapter.NavigationItem(ADAPTER_KEY_ALL, getString(R.string.label_all_bills), null, R.drawable.ic_allgrey_24dp, true);

        adapterMembers = new NavigationAdapter(new NavigationAdapter.ClickListener() {
            @Override
            public void onItemClick(NavigationAdapter.NavigationItem item) {
                selectItem(item, true);
            }

            private void selectItem(NavigationAdapter.NavigationItem item, boolean closeNavigation) {
                adapterMembers.setSelectedItem(item.id);

                // update current selection
                if (itemAll == item) {
                    navigationSelection = new Category(null, null);
                } else {
                    navigationSelection = new Category(item.label, Long.valueOf(item.id));
                }

                // auto-close sub-folder in Navigation if selection is outside of that folder
                /*if (navigationOpen != null) {
                    int slashIndex = navigationSelection.category == null ? -1 : navigationSelection.category.indexOf('/');
                    String rootCategory = slashIndex < 0 ? navigationSelection.category : navigationSelection.category.substring(0, slashIndex);
                    if (!navigationOpen.equals(rootCategory)) {
                        navigationOpen = null;
                    }
                }*/

                // update views
                if (closeNavigation) {
                    drawerLayout.closeDrawers();
                }
                refreshLists(true);
            }

            /*@Override
            public void onIconClick(NavigationAdapter.NavigationItem item) {
                if (item.icon == NavigationAdapter.ICON_MULTIPLE && !item.label.equals(navigationOpen)) {
                    navigationOpen = item.label;
                    selectItem(item, false);
                } else if (item.icon == NavigationAdapter.ICON_MULTIPLE || item.icon == NavigationAdapter.ICON_MULTIPLE_OPEN && item.label.equals(navigationOpen)) {
                    navigationOpen = null;
                    refreshLists();
                } else {
                    onItemClick(item);
                }
            }*/

            @Override
            public void onIconClick(NavigationAdapter.NavigationItem item) {
                onItemClick(item);
            }
        });
        adapterMembers.setSelectedItem(selectedItem);
        listNavigationMembers.setAdapter(adapterMembers);
        //listNavigationMembers.setNestedScrollingEnabled(false);
    }


    private class LoadCategoryListTask extends AsyncTask<Void, Void, List<NavigationAdapter.NavigationItem>> {
        @Override
        protected List<NavigationAdapter.NavigationItem> doInBackground(Void... voids) {
            itemUncategorized = null;
            MenuProject mproj = (MenuProject) projects.getSelectedItem();

            ArrayList<NavigationAdapter.NavigationItem> items = new ArrayList<>();

            if (mproj == null) {
                return items;
            }

            List<DBMember> dbMembers = db.getMembersOfProject(mproj.getId());

            Map<Long, Integer> membersNbBills = new ArrayMap<>();
            HashMap<Long, Double> membersBalance = new HashMap<>();
            HashMap<Long, Double> membersPaid = new HashMap<>();
            HashMap<Long, Double> membersSpent = new HashMap<>();

            int nbBills = SupportUtil.getStatsOfProject(
                    mproj.getId(), db,
                    membersNbBills, membersBalance, membersPaid, membersSpent
            );

            itemAll.count = nbBills;
            items.add(itemAll);

            NumberFormat balanceFormatter = new DecimalFormat("#0.00");
            NumberFormat weightFormatter = new DecimalFormat("#.##");

            for (DBMember m : dbMembers) {
                double balance = Math.round( (membersBalance.get(m.getId())) * 100.0 ) / 100.0;
                String balanceStr = balanceFormatter.format(balance).replace(",", ".");
                // TODO if activated OR balance != 0
                if (m.isActivated() || balance != 0.0) {
                    String weightStr = "";
                    if (m.getWeight() != 1) {
                        weightStr = " x" + weightFormatter.format(m.getWeight()).replace(",", ".");
                    }
                    String sign = balance > 0.0 ? "+" : "";
                    NavigationAdapter.NavigationItem it = new NavigationAdapter.NavigationItem(
                            String.valueOf(m.getId()),
                            m.getName()+" ("+sign+balanceStr+")"+weightStr,
                            membersNbBills.get(m.getId()),
                            R.drawable.ic_account_circle_grey_24dp,
                            m.isActivated()
                    );

                    items.add(it);
                }
            }
            return items;
        }

        @Override
        protected void onPostExecute(List<NavigationAdapter.NavigationItem> items) {
            adapterMembers.setItems(items);
        }
    }


    private void setupNavigationMenu() {
        //final NavigationAdapter.NavigationItem itemTrashbin = new NavigationAdapter.NavigationItem("trashbin", getString(R.string.action_trashbin), null, R.drawable.ic_delete_grey600_24dp);
        //final NavigationAdapter.NavigationItem itemAddProject = new NavigationAdapter.NavigationItem("addproject", getString(R.string.action_add_project), null, android.R.drawable.ic_menu_add);
        //final NavigationAdapter.NavigationItem itemEditProject = new NavigationAdapter.NavigationItem("editproject", getString(R.string.action_edit_project), null, android.R.drawable.ic_menu_edit);
        //final NavigationAdapter.NavigationItem itemRemoveProject = new NavigationAdapter.NavigationItem("removeproject", getString(R.string.action_remove_project), null, android.R.drawable.ic_menu_delete);
        final NavigationAdapter.NavigationItem itemSettings = new NavigationAdapter.NavigationItem("settings", getString(R.string.action_settings), null, R.drawable.ic_settings_grey600_24dp, true);
        final NavigationAdapter.NavigationItem itemAbout = new NavigationAdapter.NavigationItem("about", getString(R.string.simple_about), null, R.drawable.ic_info_outline_grey600_24dp, true);

        ArrayList<NavigationAdapter.NavigationItem> itemsMenu = new ArrayList<>();
        //itemsMenu.add(itemAddProject);
        //itemsMenu.add(itemEditProject);
        //itemsMenu.add(itemRemoveProject);
        itemsMenu.add(itemSettings);
        itemsMenu.add(itemAbout);

        NavigationAdapter adapterMenu = new NavigationAdapter(new NavigationAdapter.ClickListener() {
            @Override
            public void onItemClick(NavigationAdapter.NavigationItem item) {
                if (item == itemSettings) {
                    Intent settingsIntent = new Intent(getApplicationContext(), PreferencesActivity.class);
                    startActivityForResult(settingsIntent, server_settings);
                } else if (item == itemAbout) {
                    Intent aboutIntent = new Intent(getApplicationContext(), AboutActivity.class);
                    startActivityForResult(aboutIntent, about);
                }/* else if (item == itemAddProject) {
                    Intent newProjectIntent = new Intent(getApplicationContext(), NewProjectActivity.class);
                    if (projectsAdapter.getCount() > 0) {
                        long pid = projectsAdapter.getItem(0).getId();
                        String url = db.getProject(pid).getIhmUrl();
                        newProjectIntent.putExtra(NewProjectFragment.PARAM_DEFAULT_URL, url);
                    }
                    startActivityForResult(newProjectIntent, addproject);
                } else if (item == itemEditProject) {
                    MenuProject proj = (MenuProject) projects.getSelectedItem();
                    Intent editProjectIntent = new Intent(getApplicationContext(), EditProjectActivity.class);
                    editProjectIntent.putExtra(PARAM_PROJECT_ID, proj.getId());
                    startActivityForResult(editProjectIntent, editproject);
                } else if (item == itemRemoveProject) {
                    MenuProject proj = (MenuProject) projects.getSelectedItem();
                    db.deleteProject(proj.getId());
                    projectsAdapter.remove(proj);
                    projectsAdapter.notifyDataSetChanged();
                }*/
            }

            @Override
            public void onIconClick(NavigationAdapter.NavigationItem item) {
                onItemClick(item);
            }
        });



        final BillsListViewActivity that = this;
        /*this.account.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsIntent = new Intent(that, SettingsActivity.class);
                startActivityForResult(settingsIntent, server_settings);
            }
        });*/

        adapterMenu.setItems(itemsMenu);
        listNavigationMenu.setAdapter(adapterMenu);
        //listNavigationMenu.setNestedScrollingEnabled(false);

        // projects

        // restore last selected project
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String lastId = preferences.getString("last_selected_project", "");

        //projectsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, android.R.id.text1);
        projectsAdapter = new ArrayAdapter<>(this, R.layout.item_projects_spinner);
        projectsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        projects.setAdapter(projectsAdapter);
        List<DBProject> projs = db.getProjects();
        MenuProject restoredProject = null;
        MenuProject tmpProject;
        for (DBProject proj : projs) {
            tmpProject = new MenuProject(
                    proj.getId(),
                    proj.getName(),
                    proj.getRemoteId()+"@"+proj.getIhmUrl()
                            .replace("https://", "")
                            .replace("http://", "")
            );
            projectsAdapter.add(tmpProject);
            if (String.valueOf(proj.getId()).equals(lastId)) {
                restoredProject = tmpProject;
            }
        }
        projectsAdapter.notifyDataSetChanged();

        if (restoredProject != null) {
            Log.v(TAG, "RESTORE PROJECT SELECTION " + lastId + " : " + projectsAdapter.getPosition(restoredProject));
            projects.setSelection(projectsAdapter.getPosition(restoredProject));
        }

        projects.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id) {
                // ugly : set the color each time an item is selected
                ((TextView)parent.getChildAt(0)).setTextColor(Color.WHITE);
                MenuProject it = (MenuProject) parent.getItemAtPosition(pos);
                Log.v(TAG, "PROJECT ITEM SELECTED "+pos+" "+id+" "+it.toString());
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                preferences.edit().putString("last_selected_project", String.valueOf(it.getId())).apply();
                // get project info from server
                drawerLayout.closeDrawers();
                refreshLists();
                synchronize();
            }

            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
                Log.v(TAG, "NOOOOOOO ITEM SELECTED ");
            }

        });
        //this.updateUsernameInDrawer();
    }

    public void initList() {
        adapter = new ItemAdapter(this, db);
        listView.setAdapter(adapter);
        listView.setLayoutManager(new LinearLayoutManager(this));
        ItemTouchHelper touchHelper = new ItemTouchHelper(new SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            /**
             * Disable swipe on sections
             *
             * @param recyclerView RecyclerView
             * @param viewHolder   RecyclerView.ViewHoler
             * @return 0 if section, otherwise super()
             */
            @Override
            public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof ItemAdapter.SectionViewHolder) return 0;
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            /**
             * Delete logjob if logjob is swiped to left or right
             *
             * @param viewHolder RecyclerView.ViewHoler
             * @param direction  int
             */
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                switch(direction) {
                    case ItemTouchHelper.LEFT: {
                        final DBBill bill = (DBBill) adapter.getItem(viewHolder.getAdapterPosition());
                        final DBBill dbBill = db.getBill(bill.getId());
                        // get real original state to potentially restore it
                        final int originalState = dbBill.getState();

                        if (originalState == DBBill.STATE_ADDED) {
                            db.deleteBill(dbBill.getId());
                        }
                        else {
                            db.setBillState(dbBill.getId(), DBBill.STATE_DELETED);
                        }
                        adapter.remove(dbBill);
                        refreshLists();
                        Log.v(TAG, "Item deleted through swipe ----------------------------------------------");
                        Snackbar.make(swipeRefreshLayout, R.string.action_bill_deleted, Snackbar.LENGTH_LONG)
                                .setAction(R.string.action_undo, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (originalState == DBBill.STATE_ADDED) {
                                            db.addBill(dbBill);
                                        }
                                        else {
                                            db.setBillState(dbBill.getId(), originalState);
                                        }
                                        refreshLists();
                                        Snackbar.make(swipeRefreshLayout, R.string.action_bill_restored, Snackbar.LENGTH_SHORT)
                                                .show();
                                        synchronize();
                                        //notifyLoggerService(dbBill.getId());
                                    }
                                })
                                .addCallback(new Snackbar.Callback() {

                                    @Override
                                    public void onDismissed(Snackbar snackbar, int event) {
                                        //see Snackbar.Callback docs for event details
                                        Log.v(TAG, "DISMISSED "+event);
                                        if (event == DISMISS_EVENT_TIMEOUT) {
                                            synchronize();
                                        }
                                    }

                                    @Override
                                    public void onShown(Snackbar snackbar) {
                                        Log.v(TAG, "SHOWN");
                                    }
                                })
                                .show();
                        //notifyLoggerService(dbBill.getId());
                        break;
                    }
                    case ItemTouchHelper.RIGHT: {
                        final DBBill dbBill = (DBBill) adapter.getItem(viewHolder.getAdapterPosition());
                        //db.toggleEnabled(dbBill, syncCallBack);
                        refreshLists();
                        //notifyLoggerService(dbBill.getId());
                        break;
                    }
                }
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                ItemAdapter.BillViewHolder billViewHolder = (ItemAdapter.BillViewHolder) viewHolder;
                // show swipe icon on the side
                billViewHolder.showSwipe(dX>0);
                // move only swipeable part of item (not leave-behind)
                getDefaultUIUtil().onDraw(c, recyclerView, billViewHolder.billSwipeable, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                getDefaultUIUtil().clearView(((ItemAdapter.BillViewHolder) viewHolder).billSwipeable);
            }
        });
        touchHelper.attachToRecyclerView(listView);
    }

    private void refreshLists() {
        refreshLists(false);
    }
    private void refreshLists(final boolean scrollToTop) {
        long projId = 0;
        String projName = "";
        MenuProject proj = (MenuProject) projects.getSelectedItem();
        if (proj != null) {
            projId = proj.getId();
            projName = proj.getName();
        }

        String subtitle;
        if (navigationSelection.memberName != null) {
            subtitle = projName + " - " + navigationSelection.memberName;
        }
        else {
            subtitle = projName + " - " + getString(R.string.label_all_bills);
        }

        setTitle(subtitle);
        CharSequence query = null;
        if (searchView != null && !searchView.isIconified() && searchView.getQuery().length() != 0) {
            query = searchView.getQuery();
        }

        LoadBillsListTask.BillsLoadedListener callback = new LoadBillsListTask.BillsLoadedListener() {
            @Override
            public void onBillsLoaded(List<Item> billItems, boolean showCategory) {
                adapter.setShowCategory(showCategory);
                adapter.setItemList(billItems);
                if(scrollToTop) {
                    listView.scrollToPosition(0);
                }
            }
        };
        new LoadBillsListTask(getApplicationContext(), callback, navigationSelection, query, projId).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        new LoadCategoryListTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public ItemAdapter getItemAdapter() {
        return adapter;
    }

    public SwipeRefreshLayout getSwipeRefreshLayout() {
        return swipeRefreshLayout;
    }

    /**
     * Adds the Menu Items to the Action Bar.
     *
     * @param menu Menu
     * @return boolean
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_list_view, menu);
        // Associate searchable configuration with the SearchView
        final MenuItem item = menu.findItem(R.id.search);
        searchView = (SearchView) item.getActionView();

        final LinearLayout searchEditFrame = searchView.findViewById(android.support.v7.appcompat.R.id
                .search_edit_frame);

        searchEditFrame.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            int oldVisibility = -1;
            @SuppressLint("RestrictedApi")
            @Override
            public void onGlobalLayout() {
                int currentVisibility = searchEditFrame.getVisibility();

                if (currentVisibility != oldVisibility) {
                    if (currentVisibility == View.VISIBLE) {
                        fabMenu.setVisibility(View.INVISIBLE);
                    } else {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                fabMenu.setVisibility(View.VISIBLE);
                            }
                        }, 150);
                    }

                    oldVisibility = currentVisibility;
                }
            }

        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                refreshLists();
                return true;
            }
        });
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            searchView.setQuery(intent.getStringExtra(SearchManager.QUERY), true);
        }
        super.onNewIntent(intent);
    }

    /**
     * Handles the Results of started Sub Activities (Created Logjob, Edited Logjob)
     *
     * @param requestCode int to distinguish between the different Sub Activities
     * @param resultCode  int Return Code
     * @param data        Intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG) { Log.d(TAG, "[ACT RESULT]"); }
        // Check which request we're responding to
        if (requestCode == create_bill_cmd) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                //not need because of db.synchronisation in createActivity

                DBBill createdBill = (DBBill) data.getExtras().getSerializable(CREATED_BILL);
                if (DEBUG) { Log.d(TAG, "[ACT RESULT CREATED BILL ] " + createdBill.getWhat()); }
                adapter.add(createdBill);
            }
            listView.scrollToPosition(0);
        } else if (requestCode == addproject) {
            long pid = data.getLongExtra(CREATED_PROJECT, 0);
            //DBProject createdProject = db.getProject();
            System.out.println("BILLS request code : addproject " + pid);
            if (pid != 0) {
                DBProject proj = db.getProject(pid);
                MenuProject mproj = new MenuProject(
                        proj.getId(),
                        proj.getRemoteId(),
                        proj.getRemoteId() + "@" + proj.getIhmUrl()
                                .replace("https://", "")
                                .replace("http://", "")
                );
                projectsAdapter.add(mproj);
                projects.setSelection(projectsAdapter.getPosition(mproj));
                projectsAdapter.notifyDataSetChanged();
                // TODO : get project info just after created so that name is not null
                // TODO : update view when project removed, refresh the shit
            }
        } else if (requestCode == editproject) {
            if (data != null) {
                // adapt after project has been deleted
                long pid = data.getLongExtra(DELETED_PROJECT, 0);
                if (pid != 0) {
                    MenuProject mp;
                    for (int i = 0; i < projectsAdapter.getCount(); i++) {
                        mp = projectsAdapter.getItem(i);
                        if (mp.getId() == pid) {
                            if (DEBUG) {
                                Log.d(TAG, "[SPINNER deleted project " + mp + "]");
                            }
                            projectsAdapter.remove(mp);
                            break;
                        }
                    }
                    projects.setSelection(0);
                    projectsAdapter.notifyDataSetChanged();
                }
                // adapt after project has been edited
                pid = data.getLongExtra(EDITED_PROJECT, 0);
                if (pid != 0) {
                    DBProject proj = db.getProject(pid);
                    MenuProject mp;
                    for (int i = 0; i < projectsAdapter.getCount(); i++) {
                        mp = projectsAdapter.getItem(i);
                        if (mp.getId() == pid) {
                            mp.setName(proj.getName());
                            projectsAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }
        } else if (requestCode == show_single_bill_cmd) {
            // TODO check if bill was added, edited or deleted
            // => apply changes
        }
        /*else if (requestCode == server_settings) {
            // Create new Instance with new URL and credentials
            db = MoneyBusterSQLiteOpenHelper.getInstance(this);
            if (db.getMoneyBusterServerSyncHelper().isSyncPossible()) {
                this.updateUsernameInDrawer();
                adapter.removeAll();
                synchronize();
            } else {
                if (MoneyBusterServerSyncHelper.isConfigured(getApplicationContext())) {
                    Toast.makeText(getApplicationContext(), getString(R.string.error_sync, getString(SpendClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show();
                }
            }
        }*/
    }

    private void updateUsernameInDrawer() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        /*String username = preferences.getString(SettingsActivity.SETTINGS_USERNAME, SettingsActivity.DEFAULT_SETTINGS);
        String url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS).replace("https://", "").replace("http://", "");
        if(!SettingsActivity.DEFAULT_SETTINGS.equals(username) && !SettingsActivity.DEFAULT_SETTINGS.equals(url)) {
            this.account.setText(username + "@" + url.substring(0, url.length() - 1));
        }
        else {
            this.account.setText("Tap here to connect");
        }*/
    }

    @Override
    public void onBillClick(int position, View v) {
        if (mActionMode != null) {
            if (!adapter.select(position)) {
                v.setSelected(false);
                adapter.deselect(position);
            } else {
                v.setSelected(true);
            }
            int size = adapter.getSelected().size();
            mActionMode.setTitle(String.valueOf(getResources().getQuantityString(R.plurals.ab_selected, size, size)));
            int checkedItemCount = adapter.getSelected().size();
            boolean hasCheckedItems = checkedItemCount > 0;

            if (hasCheckedItems && mActionMode == null) {
                // TODO differ if one or more items are selected
                // if (checkedItemCount == 1) {
                // mActionMode = startActionMode(new
                // SingleSelectedActionModeCallback());
                // } else {
                // there are some selected items, start the actionMode
                mActionMode = startSupportActionMode(new MultiSelectedActionModeCallback());
                // }
            } else if (!hasCheckedItems && mActionMode != null) {
                // there no selected items, finish the actionMode
                mActionMode.finish();
            }
        } else {
            DBBill bill = (DBBill) adapter.getItem(position);
            Intent intent;
            intent = new Intent(getApplicationContext(), EditBillActivity.class);
            intent.putExtra(EditBillActivity.PARAM_BILL_ID, bill.getId());
            //intent.putExtra(EditBillActivity.PARAM_MEMBERS_BALANCE, membersBalance);
            startActivityForResult(intent, show_single_bill_cmd);

        }
    }

    /*@Override
    public void onLogjobEnabledClick(int position, View view) {
        DBLogjob logjob = (DBLogjob) adapter.getItem(position);
        if (logjob != null) {
            MoneyBusterSQLiteOpenHelper db = MoneyBusterSQLiteOpenHelper.getInstance(view.getContext());
            db.toggleEnabled(logjob, syncCallBack);
            adapter.notifyItemChanged(position);
            refreshLists();

            notifyLoggerService(logjob.getId());
        }
    }*/

    @Override
    public void onBillInfoButtonClick(int position, View view) {
        DBBill bill = (DBBill) adapter.getItem(position);
        if (bill != null) {

            MoneyBusterSQLiteOpenHelper db = MoneyBusterSQLiteOpenHelper.getInstance(view.getContext());
            DBBill dbBill = db.getBill(bill.getId());

            View iView = LayoutInflater.from(this).inflate(R.layout.items_infodialog, null);

            TextView wv = iView.findViewById(R.id.infoWhatText);
            wv.setText(bill.getWhat());

            TextView dv = iView.findViewById(R.id.infoDateText);
            dv.setText(bill.getDate());

            DBMember payer = db.getMember(bill.getPayerId());
            TextView pv = iView.findViewById(R.id.infoPayerText);
            pv.setText(payer.getName());

            TextView av = iView.findViewById(R.id.infoAmountText);
            av.setText(String.valueOf(bill.getAmount()));

            NumberFormat formatter = new DecimalFormat("#0.00");

            double nbShares = 0;
            for (DBBillOwer bo : bill.getBillOwers()) {
                nbShares += db.getMember(bo.getMemberId()).getWeight();
            }
            double amountPerShare = bill.getAmount() / nbShares;
            String owersStr = "";
            for (DBBillOwer bo : bill.getBillOwers()) {
                DBMember m = db.getMember(bo.getMemberId());
                double owerAmount = m.getWeight() * amountPerShare;
                owerAmount = Math.round( (owerAmount * 100.0 ) )/ 100.0;
                String owerAmountStr = formatter.format(owerAmount).replace(",", ".");
                owersStr += "- " + m.getName() + " ("+owerAmountStr+")\n";
            }
            owersStr = owersStr.trim();
            TextView ov = iView.findViewById(R.id.infoOwersText);
            ov.setText(owersStr);

            AlertDialog.Builder builder;
            //builder = new AlertDialog.Builder(view.getContext(), android.R.style.Theme_Material_Dialog_Alert);
            builder = new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), R.style.Theme_AppCompat_DayNight_Dialog));
            builder.setTitle(view.getContext().getString(R.string.bill_info_dialog_title, dbBill.getWhat()))
                    //.setMessage(infoText)
                    .setView(iView)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();
        }
    }

    @Override
    public boolean onBillLongClick(int position, View v) {
        boolean selected = adapter.select(position);
        if (selected) {
            v.setSelected(true);
            mActionMode = startSupportActionMode(new MultiSelectedActionModeCallback());
            int checkedItemCount = adapter.getSelected().size();
            mActionMode.setTitle(getResources().getQuantityString(R.plurals.ab_selected, checkedItemCount, checkedItemCount));
        }
        return selected;
    }

    @Override
    public void onBackPressed() {
        if (searchView == null || searchView.isIconified()) {
            super.onBackPressed();
        } else {
            searchView.setIconified(true);
        }
    }

    private void synchronize() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (DEBUG) { Log.d(TAG, "CALLER : " + stackTraceElements[3].getMethodName()); }
        if (db.getMoneyBusterServerSyncHelper().isSyncPossible()) {
            swipeRefreshLayout.setRefreshing(true);
            MenuProject proj = (MenuProject) projects.getSelectedItem();
            if (proj != null) {
                long projId = proj.getId();
                if (DEBUG) { Log.d(TAG, "SYNC ASKED : " + proj); }
                db.getMoneyBusterServerSyncHelper().addCallbackPull(syncCallBack);
                db.getMoneyBusterServerSyncHelper().scheduleSync(false, projId);
            }
            else {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private void notifyLoggerService(long jobId) {
        /*Intent intent = new Intent(BillsListViewActivity.this, LoggerService.class);
        intent.putExtra(UPDATED_LOGJOBS, true);
        intent.putExtra(UPDATED_LOGJOB_ID, jobId);
        startService(intent);*/
    }

    /**
     * Handler for the MultiSelect Actions
     */
    private class MultiSelectedActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // inflate contextual menu
            mode.getMenuInflater().inflate(R.menu.menu_list_context_multiple, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        /**
         * @param mode ActionMode - used to close the Action Bar after all work is done.
         * @param item MenuItem - the item in the List that contains the Node
         * @return boolean
         */
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_delete:
                    List<Integer> selection = adapter.getSelected();
                    for (Integer i : selection) {
                        DBBill bill = (DBBill) adapter.getItem(i);
                        db.deleteBill(bill.getId());
                        // Not needed because of dbsync
                        //adapter.remove(logjob);
                        //notifyLoggerService(logjob.getId());
                    }
                    mode.finish(); // Action picked, so close the CAB
                    //after delete selection has to be cleared
                    searchView.setIconified(true);
                    refreshLists();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            adapter.clearSelection();
            mActionMode = null;
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Display toast message
     * @param text Message
     */
    private void showToast(CharSequence text) {
        showToast(text, Toast.LENGTH_SHORT);
    }

    /**
     * Display toast message
     * @param text Message
     * @param duration Duration
     */
    private void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    /**
     * Register broadcast receiver for synchronization
     * and tracking status updates
     */
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(MoneyBusterServerSyncHelper.BROADCAST_PROJECT_SYNC_FAILED);
        filter.addAction(MoneyBusterServerSyncHelper.BROADCAST_PROJECT_SYNCED);
        registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Broadcast receiver
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) { Log.d(TAG, "[broadcast received " + intent + "]"); }
            if (intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case MoneyBusterServerSyncHelper.BROADCAST_PROJECT_SYNC_FAILED:
                    String errorMessage = intent.getStringExtra(BROADCAST_ERROR_MESSAGE);
                    showToast(errorMessage, Toast.LENGTH_LONG);
                    break;
                case MoneyBusterServerSyncHelper.BROADCAST_PROJECT_SYNCED:
                    String projName = intent.getStringExtra(BROADCAST_EXTRA_PARAM);
                    refreshLists();
                    showToast(getString(R.string.project_sync_success, projName));
                    break;
            }
        }
    };
}