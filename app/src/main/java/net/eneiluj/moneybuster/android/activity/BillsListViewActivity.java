package net.eneiluj.moneybuster.android.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.WriterException;
import com.kizitonwose.colorpreferencecompat.ColorPreferenceCompat;
import com.larswerkman.lobsterpicker.LobsterPicker;
import com.larswerkman.lobsterpicker.sliders.LobsterShadeSlider;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.fragment.EditBillFragment;
import net.eneiluj.moneybuster.android.fragment.NewProjectFragment;
import net.eneiluj.moneybuster.android.ui.TextDrawable;
import net.eneiluj.moneybuster.model.Category;
import net.eneiluj.moneybuster.model.DBBill;
import net.eneiluj.moneybuster.model.DBBillOwer;
import net.eneiluj.moneybuster.model.DBCategory;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.Item;
import net.eneiluj.moneybuster.model.ItemAdapter;
import net.eneiluj.moneybuster.model.NavigationAdapter;
import net.eneiluj.moneybuster.model.ProjectType;
import net.eneiluj.moneybuster.model.Transaction;
import net.eneiluj.moneybuster.persistence.LoadBillsListTask;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.persistence.MoneyBusterServerSyncHelper;
import net.eneiluj.moneybuster.service.SyncService;
import net.eneiluj.moneybuster.util.CospendClientUtil;
import net.eneiluj.moneybuster.util.ICallback;
import net.eneiluj.moneybuster.util.MoneyBuster;
import net.eneiluj.moneybuster.util.SupportUtil;
import net.eneiluj.moneybuster.util.ThemeUtils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.eneiluj.moneybuster.android.activity.EditProjectActivity.PARAM_PROJECT_ID;
import static net.eneiluj.moneybuster.util.SupportUtil.getVersionCode;
import static net.eneiluj.moneybuster.util.SupportUtil.getVersionName;
import static net.eneiluj.moneybuster.util.SupportUtil.settleBills;

//import android.support.v4.widget.DrawerLayout;
//import android.support.v4.widget.SwipeRefreshLayout;

public class BillsListViewActivity extends AppCompatActivity implements ItemAdapter.BillClickListener {

    private final static int PERMISSION_FOREGROUND = 1;
    public static boolean DEBUG = true;
    public static final String BROADCAST_EXTRA_PARAM = "net.eneiluj.moneybuster.broadcast_extra_param";
    public static final String BROADCAST_ERROR_MESSAGE = "net.eneiluj.moneybuster.broadcast_error_message";
    public static final String BROADCAST_ACCOUNT_PROJECTS_SYNC_FAILED = "net.eneiluj.moneybuster.broadcast_acc_proj_failed";
    public static final String BROADCAST_SSO_TOKEN_MISMATCH = "net.eneiluj.moneybuster.broadcast.token_mismatch";
    public static final String BROADCAST_ACCOUNT_PROJECTS_SYNCED = "net.eneiluj.moneybuster.broadcast.broadcast_acc_proj_synced";

    public final static String PARAM_DIALOG_CONTENT = "net.eneiluj.moneybuster.PARAM_DIALOG_CONTENT";
    public final static String PARAM_PROJECT_TO_SELECT = "net.eneiluj.moneybuster.PARAM_PROJECT_TO_SELECT";

    private final static int PERMISSION_FOREGROUND_SERVICE = 1;

    private static final String TAG = BillsListViewActivity.class.getSimpleName();

    public final static String CREATED_BILL = "net.eneiluj.moneybuster.created_bill";
    public final static String CREATED_PROJECT = "net.eneiluj.moneybuster.created_project";
    public final static String EDITED_PROJECT = "net.eneiluj.moneybuster.edited_project";
    public final static String DELETED_PROJECT = "net.eneiluj.moneybuster.deleted_project";
    public final static String DELETED_BILL = "net.eneiluj.moneybuster.deleted_bill";
    public static final String ADAPTER_KEY_ALL = "all";

    public final static String CREDENTIALS_CHANGED = "net.eneiluj.moneybuster.CREDENTIALS_CHANGED";

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
    private final static int scan_qrcode_import_cmd = 7;

    private static boolean activityVisible = false;

    //private HashMap<Long, Double> membersBalance;


    Toolbar toolbar;
    DrawerLayout drawerLayout;
    TextView selectedProjectLabel;
    SwipeRefreshLayout swipeRefreshLayout;
    com.github.clans.fab.FloatingActionMenu fabMenuDrawerEdit;
    com.github.clans.fab.FloatingActionButton fabEditMember;
    com.github.clans.fab.FloatingActionButton fabEditProject;
    com.github.clans.fab.FloatingActionButton fabRemoveProject;
    FloatingActionButton fabAddBill;
    FloatingActionButton fabAddMember;
    FloatingActionButton fabSidebarAddProject;
    FloatingActionButton fabBillListAddProject;
    FloatingActionButton fabAbout;
    FloatingActionButton fabStatistics;
    FloatingActionButton fabSettle;
    FloatingActionButton fabShareProject;
    FloatingActionButton fabSelectProject;
    RecyclerView listNavigationMembers;
    RecyclerView listNavigationMenu;
    RecyclerView listView;

    private String statsTextToShare;

    private ActionBarDrawerToggle drawerToggle;
    private ItemAdapter adapter = null;
    private NavigationAdapter adapterMembers;
    private NavigationAdapter.NavigationItem itemAll;
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
        activityVisible = true;
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
        toolbar = findViewById(R.id.billsListActivityActionBar);
        drawerLayout = findViewById(R.id.drawerLayout);
        selectedProjectLabel = findViewById(R.id.selectedProject);
        swipeRefreshLayout = findViewById(R.id.swiperefreshlayout);
        fabMenuDrawerEdit = findViewById(R.id.floatingMenuDrawerEdit);
        fabEditMember = findViewById(R.id.fabDrawer_edit_member);
        fabStatistics = findViewById(R.id.fab_statistics);
        fabSettle = findViewById(R.id.fab_settle);
        fabEditProject = findViewById(R.id.fabDrawer_edit_project);
        fabShareProject = findViewById(R.id.fab_share);
        fabRemoveProject = findViewById(R.id.fabDrawer_remove_project);
        fabAddBill = findViewById(R.id.fab_add_bill);
        fabAddMember = findViewById(R.id.fab_add_member);
        fabAbout = findViewById(R.id.fab_about);
        fabSidebarAddProject = findViewById(R.id.fab_add_project);
        fabBillListAddProject = findViewById(R.id.fab_bill_list_add_project);
        fabSelectProject = findViewById(R.id.fab_select_project);
        listNavigationMembers = findViewById(R.id.navigationList);
        listNavigationMenu = findViewById(R.id.navigationMenu);
        listView = findViewById(R.id.recycler_view);

        //ButterKnife.bind(this);

        db = MoneyBusterSQLiteOpenHelper.getInstance(this);

        setupActionBar();
        setupBillsList();
        setupNavigationMenu();
        setupMembersNavigationList(categoryAdapterSelectedItem);

        // ask user what to do if no project an no account configured
        if (db.getProjects().isEmpty() && !MoneyBusterServerSyncHelper.isNextcloudAccountConfigured(this)) {
            AlertDialog.Builder selectBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AppThemeDialog));
            selectBuilder.setTitle(getString(R.string.empty_action_dialog_title));

            List<String> options = new ArrayList<>();
            options.add(getString(R.string.configure_account_choice));
            options.add(getString(R.string.add_project_choice));
            CharSequence[] optcs = options.toArray(new CharSequence[options.size()]);

            selectBuilder.setSingleChoiceItems(optcs, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    if (which == 0) {
                        Intent newProjectIntent = new Intent(getApplicationContext(), SettingsActivity.class);
                        startActivity(newProjectIntent);
                        dialog.dismiss();
                    } else if (which == 1) {
                        Intent newProjectIntent = new Intent(getApplicationContext(), NewProjectActivity.class);
                        newProjectIntent.putExtra(NewProjectFragment.PARAM_DEFAULT_IHM_URL, "https://ihatemoney.org");
                        newProjectIntent.putExtra(NewProjectFragment.PARAM_DEFAULT_NC_URL, "https://mynextcloud.org");
                        startActivityForResult(newProjectIntent, addproject);
                        dialog.dismiss();
                    }
                }
            });
            selectBuilder.setNegativeButton(getString(R.string.simple_cancel), null);
            selectBuilder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            AlertDialog selectDialog = selectBuilder.create();
            selectDialog.show();
        }

        // select a project if there are some and none is selected
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long selectedProjectId = preferences.getLong("selected_project", 0);
        List<DBProject> dbProjects = db.getProjects();
        if (selectedProjectId == 0 && dbProjects.size() > 0) {
            setSelectedProject(dbProjects.get(0).getId());
            Log.v(TAG, "set selection 0");
        }

        displayWelcomeDialog();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
                    != PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "[request foreground permission]");
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.FOREGROUND_SERVICE},
                        PERMISSION_FOREGROUND
                );
            }
        }

        if (!SyncService.isRunning() && preferences.getBoolean(getString(R.string.pref_key_periodical_sync), false)) {
            Intent intent = new Intent(this, SyncService.class);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                startService(intent);
            } else {
                startForegroundService(intent);
            }
        }

        long projectToSelect = getIntent().getLongExtra(PARAM_PROJECT_TO_SELECT, 0);
        if (projectToSelect != 0) {
            setSelectedProject(projectToSelect);
            DBProject project = db.getProject(projectToSelect);

            String dialogContent = getIntent().getStringExtra(PARAM_DIALOG_CONTENT);
            if (dialogContent != null) {
                android.app.AlertDialog.Builder builder;
                builder = new android.app.AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AppThemeDialog));
                builder.setTitle(this.getString(R.string.activity_dialog_title, project.getName()))
                        .setMessage(dialogContent)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setIcon(R.drawable.ic_sync_grey_24dp)
                        .show();
            }
        }

    }

    private void displayWelcomeDialog() {
        // WELCOME/NEWS dialog
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        //preferences.edit().putLong("last_welcome_dialog_displayed_at_version", -1).apply();
        long lastV = preferences.getLong("last_welcome_dialog_displayed_at_version", -1);
        String dialogContent = null;
        if (lastV == -1) {
            dialogContent = getString(R.string.first_welcome_dialog_content);
            // save last version for which welcome dialog was shown
            preferences.edit().putLong("last_welcome_dialog_displayed_at_version", 0).apply();
        }

        if (dialogContent != null) {
            // show the dialog
            String dialogTitle = getString(R.string.welcome_dialog_title, getVersionName(this));

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(
                    new ContextThemeWrapper(
                            this,
                            R.style.AppThemeDialog
                    )
            );
            builder.setTitle(dialogTitle);
            builder.setMessage(dialogContent);
            // Set up the buttons
            builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    displayWelcomeDialog();
                }
            });
            builder.setNeutralButton(getString(R.string.changelog), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(getString(R.string.changelog_url)));
                    startActivity(i);
                }
            });

            builder.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // refresh and sync every time the activity gets visible
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long selectedProjectId = preferences.getLong("selected_project", 0);
        if (selectedProjectId != 0) {
            refreshLists();
        }
        swipeRefreshLayout.setRefreshing(false);

        if (!db.getMoneyBusterServerSyncHelper().isSyncPossible()) {
            swipeRefreshLayout.setEnabled(false);
        }
        else {
            swipeRefreshLayout.setEnabled(true);
            db.getMoneyBusterServerSyncHelper().addCallbackPull(syncCallBack);
            if (DEBUG) {
                Log.d(TAG, "[onResume]");
            }
            boolean offlineMode = preferences.getBoolean(getString(R.string.pref_key_offline_mode), false);
            if (!offlineMode) {
                synchronize();
            }
        }

        registerBroadcastReceiver();

        displayWelcomeDialog();
        activityVisible = true;
    }

    /**
     * On pause
     */
    @Override
    protected void onPause() {
        if (DEBUG) { Log.d(TAG, "[onPause]"); }
        super.onPause();
        try {
            unregisterReceiver(mBroadcastReceiver);
        }
        catch (RuntimeException e) {
            if (DEBUG) { Log.d(TAG, "RECEIVER PROBLEM, let's ignore it..."); }
        }
        activityVisible = false;
    }

    public static boolean isActivityVisible() {
        return activityVisible;
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
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.action_drawer_open, R.string.action_drawer_close) {
            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                // Do whatever you want here
                fabMenuDrawerEdit.close(false);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                // Do whatever you want here
            }
        };
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerLayout.findViewById(R.id.drawer_top_layout).setBackgroundColor(ThemeUtils.primaryColor(this));
        ImageView logoView = drawerLayout.findViewById(R.id.drawer_logo);
        logoView.setColorFilter(ThemeUtils.primaryColor(this), PorterDuff.Mode.OVERLAY);

        toolbar.setBackgroundColor(ThemeUtils.primaryColor(this));

        Window window = getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int color = ThemeUtils.primaryDarkColor(this);
                window.setStatusBarColor(color);
            }
        }
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

                    Toast.makeText(getApplicationContext(), getString(R.string.error_sync, getString(CospendClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show();
                }
            }
        });

        if (!db.getMoneyBusterServerSyncHelper().isSyncPossible()) {
            System.out.println("DISABLLLLLL");
            swipeRefreshLayout.setEnabled(false);
        }

        fabMenuDrawerEdit.setOnMenuButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "[3 DOTS clicked]");
                if (fabMenuDrawerEdit.isOpened()) {
                    fabMenuDrawerEdit.close(true);
                }
                else {
                    fabMenuDrawerEdit.open(true);
                }
            }
        });

        fabSidebarAddProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addProject();
                drawerLayout.closeDrawers();
            }
        });

        fabBillListAddProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addProject();
                drawerLayout.closeDrawers();
            }
        });

        fabAddMember.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                final long selectedProjectId = preferences.getLong("selected_project", 0);

                if (selectedProjectId != 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            new ContextThemeWrapper(
                                    view.getContext(),
                                    R.style.AppThemeDialog
                            )
                    );
                    builder.setTitle(getString(R.string.add_member_dialog_title));

                    // Set up the input
                    final EditText input = new EditText(new ContextThemeWrapper(
                            view.getContext(),
                            R.style.AppThemeDialog
                    ));
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                    builder.setView(input);

                    // Set up the buttons
                    builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String memberName = input.getText().toString();

                            if (!memberName.equals("")) {
                                List<DBMember> members = db.getMembersOfProject(selectedProjectId, null);
                                List<String> memberNames = new ArrayList<>();
                                for (DBMember m : members) {
                                    memberNames.add(m.getName());
                                }
                                if (!memberNames.contains(memberName)) {
                                    db.addMemberAndSync(
                                            new DBMember(0, 0, selectedProjectId, memberName,
                                                    true, 1, DBBill.STATE_ADDED,
                                                    null, null, null)
                                    );
                                    refreshLists();
                                } else {
                                    showToast(getString(R.string.member_already_exists));
                                }
                            } else {
                                showToast(getString(R.string.member_edit_empty_name));
                            }

                            //new LoadCategoryListTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            InputMethodManager inputMethodManager = (InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                        }
                    });
                    builder.setNegativeButton(getString(R.string.simple_cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            InputMethodManager inputMethodManager = (InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                        }
                    });

                    builder.show();
                    input.setSelectAllOnFocus(true);
                    input.requestFocus();
                    // show keyboard
                    InputMethodManager inputMethodManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                }
            }
        });
        /*fabScanQrcodeImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent createIntent = new Intent(getApplicationContext(), QrCodeScanner.class);
                startActivityForResult(createIntent, scan_qrcode_import_cmd);

                drawerLayout.closeDrawers();
            }
        });*/
        fabAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent aboutIntent = new Intent(getApplicationContext(), AboutActivity.class);
                startActivityForResult(aboutIntent, about);

                drawerLayout.closeDrawers();
            }
        });

        fabAddBill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent createIntent = new Intent(getApplicationContext(), EditBillActivity.class);
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long selectedProjectId = preferences.getLong("selected_project", 0);
                if (selectedProjectId != 0) {
                    if (db.getActivatedMembersOfProject(selectedProjectId).size() < 2) {
                        showToast(getString(R.string.edit_bill_impossible_no_member));
                    }
                    else {
                        createIntent.putExtra(EditBillActivity.PARAM_PROJECT_ID, selectedProjectId);
                        createIntent.putExtra(EditBillActivity.PARAM_PROJECT_TYPE, db.getProject(selectedProjectId).getType().getId());
                        startActivityForResult(createIntent, create_bill_cmd);
                    }
                }
            }
        });

        fabEditProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long selectedProjectId = preferences.getLong("selected_project", 0);

                if (selectedProjectId != 0) {
                    DBProject proj = db.getProject(selectedProjectId);
                    if (!proj.isLocal()) {
                        Intent editProjectIntent = new Intent(getApplicationContext(), EditProjectActivity.class);
                        editProjectIntent.putExtra(PARAM_PROJECT_ID, selectedProjectId);
                        startActivityForResult(editProjectIntent, editproject);

                        fabMenuDrawerEdit.close(false);
                        drawerLayout.closeDrawers();
                    }
                    else {
                        showToast(getString(R.string.edit_project_local_impossible));
                    }
                }
            }
        });

        fabRemoveProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                final long selectedProjectId = preferences.getLong("selected_project", 0);
                DBProject proj = db.getProject(selectedProjectId);

                if (selectedProjectId != 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            new ContextThemeWrapper(
                                    view.getContext(),
                                    R.style.AppThemeDialog
                            )
                    );
                    builder.setTitle(getString(R.string.confirm_remove_project_dialog_title));
                    if (!proj.isLocal()) {
                        builder.setMessage(getString(R.string.confirm_remove_project_dialog_message));
                    }

                    // Set up the buttons
                    builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            db.deleteProject(selectedProjectId);
                            List<DBProject> dbProjects = db.getProjects();
                            if (dbProjects.size() > 0) {
                                setSelectedProject(dbProjects.get(0).getId());
                                Log.v(TAG, "set selection 0");
                            } else {
                                setSelectedProject(0);
                            }

                            fabMenuDrawerEdit.close(false);
                            //drawerLayout.closeDrawers();
                            refreshLists();
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
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long selectedProjectId = preferences.getLong("selected_project", 0);

                if (selectedProjectId != 0) {
                    final List<DBMember> members = db.getMembersOfProject(selectedProjectId, null);
                    List<String> memberNames = new ArrayList<>();
                    for (DBMember m : members) {
                        memberNames.add(m.getName());
                    }
                    CharSequence[] namescs = memberNames.toArray(new CharSequence[memberNames.size()]);

                    AlertDialog.Builder selectBuilder = new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), R.style.AppThemeDialog));
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
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long selectedProjectId = preferences.getLong("selected_project", 0);

                if (selectedProjectId != 0) {

                    final DBProject proj = db.getProject(selectedProjectId);
                    String projectName;
                    if (proj.getName() == null) {
                        projectName = proj.getRemoteId();
                    }
                    else {
                        projectName = proj.getName();
                    }

                    // generate the dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            new ContextThemeWrapper(
                                    view.getContext(),
                                    R.style.AppThemeDialog
                            )
                    );
                    builder.setTitle(getString(R.string.statistic_dialog_title));

                    Calendar calendarMin = Calendar.getInstance();
                    Calendar calendarMax = Calendar.getInstance();

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

                    final View tView = LayoutInflater.from(view.getContext()).inflate(R.layout.statistic_table, null);

                    EditText editDateMin = tView.findViewById(R.id.statsDateMin);
                    EditText editDateMax = tView.findViewById(R.id.statsDateMax);
                    TextView totPayedText = tView.findViewById(R.id.totalPayedText);
                    TextView tableTitle = tView.findViewById(R.id.tableTitle);

                    // CATEGORY
                    if (proj.getType().equals(ProjectType.COSPEND)) {
                        List<String> categoryNameList = new ArrayList<>();
                        categoryNameList.add(getString(R.string.category_all));

                        List<DBCategory> userCategories = db.getCategories(selectedProjectId);
                        for (DBCategory cat : userCategories) {
                            categoryNameList.add(cat.getIcon()+" "+cat.getName());
                        }

                        categoryNameList.add("\uD83D\uDED2 "+getString(R.string.category_groceries));
                        categoryNameList.add("\uD83C\uDF89 "+getString(R.string.category_leisure));
                        categoryNameList.add("\uD83C\uDFE0 "+getString(R.string.category_rent));
                        categoryNameList.add("\uD83C\uDF29 "+getString(R.string.category_bills));
                        categoryNameList.add("\uD83D\uDEB8 "+getString(R.string.category_excursion));
                        categoryNameList.add("\uD83D\uDC9A "+getString(R.string.category_health));
                        categoryNameList.add("\uD83D\uDECD "+getString(R.string.category_shopping));
                        categoryNameList.add("\uD83D\uDCB0 "+getString(R.string.category_reimbursement));
                        categoryNameList.add("\uD83C\uDF74 "+getString(R.string.category_restaurant));
                        categoryNameList.add("\uD83D\uDECC "+getString(R.string.category_accomodation));
                        categoryNameList.add("\uD83D\uDE8C "+getString(R.string.category_transport));
                        categoryNameList.add("\uD83C\uDFBE "+getString(R.string.category_sport));


                        String[] categoryNames = categoryNameList.toArray(new String[categoryNameList.size()]);

                        String[] categoryIdsTmp = getResources().getStringArray(R.array.categoryValues);
                        List<String> categoryIdList = new ArrayList<>();
                        categoryIdList.add(categoryIdsTmp[0]);
                        for (DBCategory cat : userCategories) {
                            categoryIdList.add(String.valueOf(cat.getRemoteId()));
                        }
                        for (int i = 1; i < categoryIdsTmp.length; i++) {
                            categoryIdList.add(categoryIdsTmp[i]);
                        }

                        String[] categoryIds = new String[categoryIdList.size()];
                        categoryIdList.toArray(categoryIds);

                        ArrayList<Map<String, String>> dataC = new ArrayList<>();
                        // first add "all"
                        HashMap<String, String> hashMap0 = new HashMap<>();
                        hashMap0.put("name", categoryNames[0]);
                        hashMap0.put("id", categoryIds[0]);
                        dataC.add(hashMap0);
                        // first add "all except reimbursement"
                        HashMap<String, String> hashMap1 = new HashMap<>();
                        hashMap1.put("name", getString(R.string.category_all_except_reimbursement));
                        hashMap1.put("id", "-100");
                        dataC.add(hashMap1);
                        // then add categories
                        for (int i = 1; i < categoryNames.length; i++) {
                            HashMap<String, String> hashMap = new HashMap<>();
                            hashMap.put("name", categoryNames[i]);
                            hashMap.put("id", categoryIds[i]);
                            dataC.add(hashMap);
                        }
                        String[] fromC = {"name", "id"};
                        int[] toC = new int[]{android.R.id.text1};
                        SimpleAdapter simpleAdapterC = new SimpleAdapter(tView.getContext(), dataC, android.R.layout.simple_spinner_item, fromC, toC);
                        simpleAdapterC.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        Spinner statsCategorySpinner = tView.findViewById(R.id.statsCategorySpinner);
                        statsCategorySpinner.setAdapter(simpleAdapterC);
                        //statsCategorySpinner.setSelection(0);

                        statsCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                                Log.d(TAG, "CATEGORY");
                                String isoDateMin = null;
                                if (editDateMin.getText() != null && !editDateMin.getText().toString().equals("")) {
                                    isoDateMin = sdf.format(calendarMin.getTime());
                                }
                                String isoDateMax = null;
                                if (editDateMax.getText() != null && !editDateMax.getText().toString().equals("")) {
                                    isoDateMax = sdf.format(calendarMax.getTime());
                                }
                                updateStatsView(tView, view, selectedProjectId, isoDateMin, isoDateMax);
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parentView) {
                                Log.d(TAG, "CATEGORY NOTHING");
                            }

                        });

                        // PAYMENT MODE
                        List<String> paymentModeNameList = new ArrayList<>();
                        paymentModeNameList.add(getString(R.string.payment_mode_all));
                        paymentModeNameList.add("\uD83D\uDCB3 "+getString(R.string.payment_mode_credit_card));
                        paymentModeNameList.add("\uD83D\uDCB5 "+getString(R.string.payment_mode_cash));
                        paymentModeNameList.add("\uD83C\uDFAB "+getString(R.string.payment_mode_check));

                        String[] paymentModeNames = paymentModeNameList.toArray(new String[paymentModeNameList.size()]);
                        //String[] repeatNames = getResources().getStringArray(R.array.repeatBillEntries);

                        String[] paymentModeIds = getResources().getStringArray(R.array.paymentModeValues);

                        ArrayList<Map<String, String>> dataP = new ArrayList<>();
                        for (int i = 0; i < paymentModeNames.length; i++) {
                            HashMap<String, String> hashMap = new HashMap<>();
                            hashMap.put("name", paymentModeNames[i]);
                            hashMap.put("id", paymentModeIds[i]);
                            dataP.add(hashMap);
                        }
                        String[] fromP = {"name", "id"};
                        int[] toP = new int[]{android.R.id.text1};
                        SimpleAdapter simpleAdapterP = new SimpleAdapter(tView.getContext(), dataP, android.R.layout.simple_spinner_item, fromP, toP);
                        simpleAdapterP.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        Spinner statsPaymentModeSpinner = tView.findViewById(R.id.statsPaymentModeSpinner);
                        statsPaymentModeSpinner.setAdapter(simpleAdapterP);
                        //statsPaymentModeSpinner.setSelection(0);

                        statsPaymentModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                                Log.d(TAG, "PAYMODE");
                                String isoDateMin = null;
                                if (editDateMin.getText() != null && !editDateMin.getText().toString().equals("")) {
                                    isoDateMin = sdf.format(calendarMin.getTime());
                                }
                                String isoDateMax = null;
                                if (editDateMax.getText() != null && !editDateMax.getText().toString().equals("")) {
                                    isoDateMax = sdf.format(calendarMax.getTime());
                                }
                                updateStatsView(tView, view, selectedProjectId, isoDateMin, isoDateMax);
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parentView) {
                                Log.d(TAG, "PAYMODE");
                            }

                        });
                    }
                    else {
                        LinearLayout statsCategoryLayout = tView.findViewById(R.id.statsCategoryLayout);
                        statsCategoryLayout.setVisibility(View.GONE);
                        LinearLayout statsPaymentModeLayout = tView.findViewById(R.id.statsPaymentModeLayout);
                        statsPaymentModeLayout.setVisibility(View.GONE);
                    }

                    // DATE MIN and MAX
                    final DatePickerDialog.OnDateSetListener date = new DatePickerDialog.OnDateSetListener() {

                        @Override
                        public void onDateSet(DatePicker view, int year, int monthOfYear,
                                              int dayOfMonth) {
                            calendarMin.set(Calendar.YEAR, year);
                            calendarMin.set(Calendar.MONTH, monthOfYear);
                            calendarMin.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                            //updateStatsView(tView, selectedProjectId, calendarMin, calendarMax);
                        }

                    };

                    DatePickerDialog dateMinPickerDialog = new DatePickerDialog(view.getContext(), date, calendarMin
                            .get(Calendar.YEAR), calendarMin.get(Calendar.MONTH),
                            calendarMin.get(Calendar.DAY_OF_MONTH)) {

                        @Override
                        public void onDateChanged(DatePicker view,
                                                  int year,
                                                  int month,
                                                  int dayOfMonth) {
                            calendarMin.set(Calendar.YEAR, year);
                            calendarMin.set(Calendar.MONTH, month);
                            calendarMin.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                            String isoDate = sdf.format(calendarMin.getTime());
                            try {
                                Date date = sdf.parse(isoDate);
                                java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(tView.getContext());
                                editDateMin.setText(dateFormat.format(date));
                            } catch (Exception e) {
                                editDateMin.setText(isoDate);
                            }

                            String isoDateMin = null;
                            if (editDateMin.getText() != null && !editDateMin.getText().toString().equals("")) {
                                isoDateMin = sdf.format(calendarMin.getTime());
                            }
                            String isoDateMax = null;
                            if (editDateMax.getText() != null && !editDateMax.getText().toString().equals("")) {
                                isoDateMax = sdf.format(calendarMax.getTime());
                            }
                            updateStatsView(tView, view, selectedProjectId, isoDateMin, isoDateMax);
                            this.dismiss();
                        }
                    };


                    editDateMin.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if(event.getAction() == MotionEvent.ACTION_UP){
                                dateMinPickerDialog.show();
                                // Do what you want
                                return true;
                            }
                            return false;
                        }
                    });
                    /*editDateMin.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            datePickerDialog.show();
                        }
                    });*/

                    final DatePickerDialog.OnDateSetListener dateMaxSetListener = new DatePickerDialog.OnDateSetListener() {

                        @Override
                        public void onDateSet(DatePicker view, int year, int monthOfYear,
                                              int dayOfMonth) {
                            calendarMax.set(Calendar.YEAR, year);
                            calendarMax.set(Calendar.MONTH, monthOfYear);
                            calendarMax.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        }

                    };

                    DatePickerDialog dateMaxPickerDialog = new DatePickerDialog(view.getContext(), dateMaxSetListener, calendarMax
                            .get(Calendar.YEAR), calendarMax.get(Calendar.MONTH),
                            calendarMax.get(Calendar.DAY_OF_MONTH)) {

                        @Override
                        public void onDateChanged(DatePicker view,
                                                  int year,
                                                  int month,
                                                  int dayOfMonth) {
                            calendarMax.set(Calendar.YEAR, year);
                            calendarMax.set(Calendar.MONTH, month);
                            calendarMax.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                            String isoDate = sdf.format(calendarMax.getTime());
                            try {
                                Date date = sdf.parse(isoDate);
                                java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(tView.getContext());
                                editDateMax.setText(dateFormat.format(date));
                            } catch (Exception e) {
                                editDateMax.setText(isoDate);
                            }

                            String isoDateMin = null;
                            if (editDateMin.getText() != null && !editDateMin.getText().toString().equals("")) {
                                isoDateMin = sdf.format(calendarMin.getTime());
                            }
                            String isoDateMax = null;
                            if (editDateMax.getText() != null && !editDateMax.getText().toString().equals("")) {
                                isoDateMax = sdf.format(calendarMax.getTime());
                            }
                            updateStatsView(tView, view, selectedProjectId, isoDateMin, isoDateMax);
                            this.dismiss();
                        }
                    };


                    editDateMax.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if(event.getAction() == MotionEvent.ACTION_UP){
                                dateMaxPickerDialog.show();
                                // Do what you want
                                return true;
                            }
                            return false;
                        }
                    });


                    builder.setView(tView).setIcon(R.drawable.ic_chart_grey_24dp);
                    builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                    builder.setNeutralButton(getString(R.string.simple_stats_share), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // share it
                            Intent shareIntent = new Intent();
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_stats_title, projectName));
                            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, statsTextToShare);
                            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_stats_title, projectName)));
                        }
                    });
                    builder.show();

                    updateStatsView(tView, view, selectedProjectId, null, null);

                    fabMenuDrawerEdit.close(false);
                }
            }
        });

        fabSettle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long selectedProjectId = preferences.getLong("selected_project", 0);

                if (selectedProjectId != 0) {
                    final DBProject proj = db.getProject(selectedProjectId);
                    String projectName;
                    if (proj.getName() == null) {
                        projectName = proj.getRemoteId();
                    }
                    else {
                        projectName = proj.getName();
                    }
                    // get stats
                    Map<Long, Integer> membersNbBills = new HashMap<>();
                    HashMap<Long, Double> membersBalance = new HashMap<>();
                    HashMap<Long, Double> membersPaid = new HashMap<>();
                    HashMap<Long, Double> membersSpent = new HashMap<>();

                    NumberFormat numberFormatter = new DecimalFormat("#0.00");

                    int nbBills = SupportUtil.getStatsOfProject(
                            proj.getId(), db,
                            membersNbBills, membersBalance, membersPaid, membersSpent,
                            0, null, null, null
                    );

                    List<DBMember> membersSortedByName = db.getMembersOfProject(proj.getId(), MoneyBusterSQLiteOpenHelper.key_name);

                    final List<Transaction> transactions = settleBills(membersSortedByName, membersBalance);
                    if (transactions == null || transactions.size() == 0) {
                        return;
                    }
                    // get members names per id
                    final Map<Long, String> memberIdToName = new HashMap<>();
                    for (DBMember m : membersSortedByName) {
                        memberIdToName.put(m.getId(), m.getName());
                    }

                    // generate the dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            new ContextThemeWrapper(
                                    view.getContext(),
                                    R.style.AppThemeDialog
                            )
                    );
                    builder.setTitle(getString(R.string.settle_dialog_title));


                    final View tView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.settle_table, null);
                    TextView hwho = tView.findViewById(R.id.header_who);
                    hwho.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    TextView htowhom = tView.findViewById(R.id.header_towhom);
                    htowhom.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    TextView hhowmuch = tView.findViewById(R.id.header_howmuch);
                    hhowmuch.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));

                    final TableLayout tl = tView.findViewById(R.id.settleTable);

                    for (Transaction t : transactions) {
                        View row = LayoutInflater.from(getApplicationContext()).inflate(R.layout.settle_row, null);
                        TextView wv = row.findViewById(R.id.settle_who);
                        wv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                        wv.setText(memberIdToName.get(t.getOwerMemberId()));

                        TextView pv = row.findViewById(R.id.settle_towhom);
                        pv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                        pv.setText(memberIdToName.get(t.getReceiverMemberId()));

                        TextView sv = row.findViewById(R.id.settle_howmuch);
                        sv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
                        double amount = Math.round(t.getAmount() * 100.0) / 100.0;
                        sv.setText(numberFormatter.format(amount));

                        tl.addView(row);
                    }

                    builder.setView(tView).setIcon(R.drawable.ic_compare_arrows_white_24dp);
                    builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                    builder.setNegativeButton(getString(R.string.simple_create_bills), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            createBillsFromTransactions(selectedProjectId, transactions);
                        }
                    });
                    builder.setNeutralButton(getString(R.string.simple_settle_share), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String text = getString(R.string.share_settle_intro, projectName) + "\n";
                            // generate text to share
                            for (Transaction t : transactions) {
                                double amount = Math.round(t.getAmount() * 100.0) / 100.0;
                                Log.v(TAG, "TRANSAC " + memberIdToName.get(t.getOwerMemberId()) + " => "
                                        + memberIdToName.get(t.getReceiverMemberId()) + " ("
                                        + amount + ")"
                                );
                                text += "\n" + getString(
                                        R.string.share_settle_sentence,
                                        memberIdToName.get(t.getOwerMemberId()),
                                        memberIdToName.get(t.getReceiverMemberId()),
                                        amount
                                );
                            }
                            // share it
                            Intent shareIntent = new Intent();
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_settle_title, projectName));
                            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, text);
                            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_settle_title, projectName)));
                        }
                    });
                    builder.show();
                    fabMenuDrawerEdit.close(false);
                }
            }
        });

        fabShareProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long selectedProjectId = preferences.getLong("selected_project", 0);

                final DBProject proj = db.getProject(selectedProjectId);

                if (selectedProjectId != 0 && proj.getIhmUrl() != null && !proj.getIhmUrl().equals("")) {
                    // get url, id and password
                    String projId = proj.getRemoteId();
                    String url = proj.getIhmUrl()
                            .replace("https://", "")
                            .replace("http://", "")
                            .replace("/index.php/apps/cospend", "");
                    String password = proj.getPassword();

                    String hostEnd;
                    final String publicWebUrl;
                    String publicWebLink;
                    if (proj.getIhmUrl().contains("index.php/apps/cospend")) {
                        hostEnd = "cospend";
                        publicWebUrl = proj.getIhmUrl() + "/loginproject/" + proj.getRemoteId();
                    }
                    else {
                        hostEnd = "ihatemoney";
                        publicWebUrl = proj.getIhmUrl() + "/" + proj.getRemoteId();
                    }
                    publicWebLink = "<a href=\"" + publicWebUrl + "\">" + publicWebUrl + "</a>";

                    final String shareUrl = "https://net.eneiluj.moneybuster." + hostEnd + "/" +
                            url + "/" + projId + "/" + password;
                    final String shareLink = "<a href=\"" + shareUrl + "\">" + shareUrl + "</a>";

                    // generate the dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            new ContextThemeWrapper(
                                    view.getContext(),
                                    R.style.AppThemeDialog
                            )
                    );
                    builder.setTitle(getString(R.string.share_dialog_title));

                    final View tView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.share_project_items, null);

                    TextView publicUrlTitle = tView.findViewById(R.id.textViewShareProjectPublicUrlTitle);
                    publicUrlTitle.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));

                    TextView qrCodeTitle = tView.findViewById(R.id.textViewShareProjectQRCodeTitle);
                    qrCodeTitle.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));

                    TextView publicUrlHint = tView.findViewById(R.id.textViewShareProjectPublicUrlHint);
                    publicUrlHint.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));

                    TextView publicUrl = tView.findViewById(R.id.textViewShareProjectPublicUrl);
                    publicUrl.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    publicUrl.setText(Html.fromHtml(publicWebLink));
                    publicUrl.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View view) {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setData(Uri.parse(publicWebUrl));
                            startActivity(i);
                        }
                    });

                    TextView link = tView.findViewById(R.id.textViewShareProject);
                    link.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    link.setText(Html.fromHtml(shareLink));
                    link.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(final View view) {
                            showToast(getString(R.string.qrcode_link_open_attempt_warning));
                        }
                    });

                    TextView hint = tView.findViewById(R.id.textViewShareProjectHint);
                    hint.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
                    ImageView img = tView.findViewById(R.id.imageViewShareProject);
                    try {
                        Bitmap bitmap = ThemeUtils.encodeAsBitmap(shareUrl);
                        img.setImageBitmap(bitmap);
                    } catch (WriterException e) {
                        e.printStackTrace();
                    }

                    builder.setView(tView)
                            .setIcon(R.drawable.ic_share_grey_24dp);
                    builder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
                    builder.setNeutralButton(getString(R.string.simple_share_share), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // share it
                            Intent shareIntent = new Intent();
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_share_intent_title, proj.getName()));
                            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareUrl);
                            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_share_chooser_title, proj.getName())));
                        }
                    });
                    builder.show();
                    fabMenuDrawerEdit.close(false);
                }
            }
        });

        fabSelectProject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                showProjectSelectionDialog();
                fabMenuDrawerEdit.close(true);
            }
        });

        selectedProjectLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (db.getProjects().size() > 0) {
                    showProjectSelectionDialog();
                }
                else {
                    addProject();
                    drawerLayout.closeDrawers();
                }
            }
        });


        // color
        boolean darkTheme = MoneyBuster.getAppTheme(this);
        // if dark theme and main color is black, make fab button lighter/gray
        if (darkTheme && ThemeUtils.primaryColor(this) == Color.BLACK) {
            fabAddBill.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            fabBillListAddProject.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            fabStatistics.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            fabSettle.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            fabShareProject.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            fabAddMember.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            fabAbout.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            fabMenuDrawerEdit.setMenuButtonColorNormal(Color.DKGRAY);
            fabSelectProject.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            fabSidebarAddProject.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
        }
        else {
            fabAddBill.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryColor(this)));
            fabBillListAddProject.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryColor(this)));
            fabStatistics.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryColor(this)));
            fabSettle.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryColor(this)));
            fabShareProject.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryColor(this)));
            fabAddMember.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryColor(this)));
            fabAbout.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryColor(this)));
            fabSidebarAddProject.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryDarkColor(this)));
            fabMenuDrawerEdit.setMenuButtonColorNormal(ThemeUtils.primaryColor(this));
            fabSelectProject.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryDarkColor(this)));
        }
        fabAddBill.setRippleColor(ThemeUtils.primaryDarkColor(this));
        fabBillListAddProject.setRippleColor(ThemeUtils.primaryDarkColor(this));
        fabStatistics.setRippleColor(ThemeUtils.primaryDarkColor(this));
        fabSettle.setRippleColor(ThemeUtils.primaryDarkColor(this));
        fabShareProject.setRippleColor(ThemeUtils.primaryDarkColor(this));
        fabAbout.setRippleColor(ThemeUtils.primaryDarkColor(this));
        fabAddMember.setRippleColor(ThemeUtils.primaryDarkColor(this));
        fabSidebarAddProject.setRippleColor(ThemeUtils.primaryColor(this));

        fabSelectProject.setRippleColor(ThemeUtils.primaryColor(this));

        fabMenuDrawerEdit.setMenuButtonColorPressed(ThemeUtils.primaryColor(this));

        fabEditMember.setColorNormal(ThemeUtils.primaryColor(this));
        fabEditMember.setColorPressed(ThemeUtils.primaryColor(this));
        fabEditProject.setColorNormal(ThemeUtils.primaryColor(this));
        fabEditProject.setColorPressed(ThemeUtils.primaryColor(this));
        fabRemoveProject.setColorNormal(ThemeUtils.primaryColor(this));
        fabRemoveProject.setColorPressed(ThemeUtils.primaryColor(this));
    }

    private void showHideButtons() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final long selectedProjectId = preferences.getLong("selected_project", 0);

        if (selectedProjectId == 0) {
            fabAddBill.hide();
            fabAddMember.hide();
            fabStatistics.hide();
            fabSettle.hide();
            fabShareProject.hide();
            fabMenuDrawerEdit.setVisibility(View.GONE);
            fabBillListAddProject.show();
        }
        else {
            fabAddBill.show();
            fabAddMember.show();
            fabStatistics.show();
            fabSettle.show();
            if (db.getProject(selectedProjectId).isLocal()) {
                fabShareProject.hide();
            }
            else {
                fabShareProject.show();
            }
            fabMenuDrawerEdit.setVisibility(View.VISIBLE);
            fabBillListAddProject.hide();
        }
    }

    private void createBillsFromTransactions(long projectId, List<Transaction> transactions) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String dateNowStr = sdf.format(new Date());

        for (Transaction t : transactions) {
            long owerId = t.getOwerMemberId();
            long receiverId = t.getReceiverMemberId();
            //double amount = Math.round(t.getAmount() * 100.0) / 100.0;
            double amount = t.getAmount();
            DBBill bill = new DBBill(
                    0, 0, projectId, owerId, amount,
                    dateNowStr, getString(R.string.settle_bill_what),
                    DBBill.STATE_ADDED, DBBill.NON_REPEATED, DBBill.PAYMODE_NONE, DBBill.CATEGORY_NONE);
            bill.getBillOwers().add(new DBBillOwer(0, 0, receiverId));
            db.addBill(bill);
        }
        refreshLists(true);
    }

    // TODO find a way to avoid date if not already set but initialize picker to today
    // and add max date
    private void updateStatsView(View tView, View view, long selectedProjectId, String dateMin, String dateMax) {
        final DBProject proj = db.getProject(selectedProjectId);
        // get filter values
        int categoryId;
        String paymentMode;
        if (proj.getType().equals(ProjectType.COSPEND)) {
            Spinner statsCategorySpinner = tView.findViewById(R.id.statsCategorySpinner);
            Map<String, String> item = (Map<String, String>) statsCategorySpinner.getSelectedItem();
            categoryId = Integer.valueOf(item.get("id"));

            Spinner statsPaymentModeSpinner = tView.findViewById(R.id.statsPaymentModeSpinner);
            Map<String, String> itemP = (Map<String, String>) statsPaymentModeSpinner.getSelectedItem();
            paymentMode = itemP.get("id");
            if (paymentMode.equals(DBBill.PAYMODE_NONE)) {
                paymentMode = null;
            }
        }
        else {
            categoryId = 0;
            paymentMode = null;
        }

        Log.v(TAG, "DATESSSS "+ dateMin + " and "+dateMax);
        Log.v(TAG, "CATGFIL "+ categoryId + " and PAYMODEFIL "+paymentMode);

        // get stats
        Map<Long, Integer> membersNbBills = new HashMap<>();
        HashMap<Long, Double> membersBalance = new HashMap<>();
        HashMap<Long, Double> membersPaid = new HashMap<>();
        HashMap<Long, Double> membersSpent = new HashMap<>();

        NumberFormat numberFormatter = new DecimalFormat("#0.00");

        int nbBills = SupportUtil.getStatsOfProject(
                selectedProjectId, db,
                membersNbBills, membersBalance, membersPaid, membersSpent,
                categoryId, paymentMode, dateMin, dateMax
        );

        List<DBMember> membersSortedByName = db.getMembersOfProject(selectedProjectId, null);
        String projectName;
        if (proj.getName() == null) {
            projectName = proj.getRemoteId();
        }
        else {
            projectName = proj.getName();
        }
        String statsText = getString(R.string.share_stats_intro, projectName) + "\n\n";
        statsText += getString(R.string.share_stats_header) + "\n";

        TextView hwho = tView.findViewById(R.id.header_who);
        hwho.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
        TextView hpaid = tView.findViewById(R.id.header_paid);
        hpaid.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
        TextView hspent = tView.findViewById(R.id.header_spent);
        hspent.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
        TextView hbalance = tView.findViewById(R.id.header_balance);
        hbalance.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
        final TableLayout tl = tView.findViewById(R.id.statTable);
        // clear table
        int i;
        for (i = tl.getChildCount()-1; i > 0; i--) {
            tl.removeViewAt(i);
        }

        double totalPayed = 0.0;

        for (DBMember m : membersSortedByName) {
            totalPayed += membersPaid.get(m.getId());
            statsText += "\n" + m.getName() + " (";

            View row = LayoutInflater.from(getApplicationContext()).inflate(R.layout.statistic_row, null);
            TextView wv = row.findViewById(R.id.stat_who);
            wv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
            wv.setText(m.getName());

            TextView pv = row.findViewById(R.id.stat_paid);
            pv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
            double rpaid = Math.round( (membersPaid.get(m.getId())) * 100.0 ) / 100.0;
            if (rpaid == 0.0) {
                pv.setText("--");
                statsText += "-- | ";
            }
            else {
                pv.setText(numberFormatter.format(rpaid));
                statsText += numberFormatter.format(rpaid) + " | ";
            }

            TextView sv = row.findViewById(R.id.stat_spent);
            sv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
            double rspent = Math.round( (membersSpent.get(m.getId())) * 100.0 ) / 100.0;
            if (rspent == 0.0) {
                sv.setText("--");
                statsText += "-- | ";
            }
            else {
                sv.setText(numberFormatter.format(rspent));
                statsText += numberFormatter.format(rspent) + " | ";
            }


            TextView bv = row.findViewById(R.id.stat_balance);
            double balance = membersBalance.get(m.getId());
            double rbalance = Math.round( Math.abs(balance) * 100.0 ) / 100.0;
            String sign = "";
            if (balance > 0) {
                bv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.green));
                sign = "+";
            }
            else if (balance < 0) {
                bv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.red));
                sign = "-";
            }
            else {
                bv.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
            }
            bv.setText(sign + numberFormatter.format(rbalance));
            statsText += sign  + numberFormatter.format(rbalance) + ")";

            tl.addView(row);
        }
        statsTextToShare = statsText;

        TextView totalPayedTV = tView.findViewById(R.id.totalPayedText);
        totalPayedTV.setText(getString(R.string.total_payed, totalPayed));
    }

    private void addProject() {
        String defaultNcUrl = "https://mynextcloud.org";
        String defaultIhmUrl = "https://ihatemoney.org";
        Intent newProjectIntent = new Intent(getApplicationContext(), NewProjectActivity.class);
        List<DBProject> projects = db.getProjects();

        String url;
        // look for a default NC url in existing projects
        for (DBProject project : projects) {
            url = project.getIhmUrl();
            if (url != null && !url.equals("")) {
                if (url.contains("/index.php/apps/cospend")) {
                    defaultNcUrl = url.replace("/index.php/apps/cospend", "");
                    break;
                }
            }
        }
        // look for a default IHM url in existing projects
        for (DBProject project : projects) {
            url = project.getIhmUrl();
            if (url != null && !url.equals("")) {
                if (!url.contains("/index.php/apps/cospend")) {
                    defaultIhmUrl = url;
                    break;
                }
            }
        }

        newProjectIntent.putExtra(NewProjectFragment.PARAM_DEFAULT_NC_URL, defaultNcUrl);
        newProjectIntent.putExtra(NewProjectFragment.PARAM_DEFAULT_IHM_URL, defaultIhmUrl);
        startActivityForResult(newProjectIntent, addproject);
    }

    private void showProjectSelectionDialog() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final long selectedProjectId = preferences.getLong("selected_project", 0);

        final List<DBProject> dbProjects = db.getProjects();
        List<String> projectNames = new ArrayList<>();
        List<Long> projectIds = new ArrayList<>();
        for (DBProject p : dbProjects) {
            if (p.getName() == null) {
                projectNames.add(p.getRemoteId());
            }
            else {
                projectNames.add(
                        p.getName()
                                + "\n(" + p.getRemoteId() + "@"
                                + p.getIhmUrl()
                                .replace("https://", "")
                                .replace("http://", "")
                                .replace("/index.php/apps/cospend", "")
                                + ")"
                );
            }
            projectIds.add(p.getId());
        }

        int checkedItem = -1;
        if (selectedProjectId != 0) {
            checkedItem = projectIds.indexOf(selectedProjectId);
        }
        CharSequence[] namescs = projectNames.toArray(new CharSequence[projectNames.size()]);

        AlertDialog.Builder selectBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AppThemeDialog));
        selectBuilder.setTitle(getString(R.string.choose_project_to_select));
        selectBuilder.setSingleChoiceItems(namescs, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // user checked an item
                setSelectedProject(dbProjects.get(which).getId());
                //preferences.edit().putLong("selected_project", dbProjects.get(which).getId()).apply();

                drawerLayout.closeDrawers();
                refreshLists();
                boolean offlineMode = preferences.getBoolean(getString(R.string.pref_key_offline_mode), false);
                if (!offlineMode) {
                    synchronize();
                }
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

    private void setSelectedProject(long projectId) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        preferences.edit().putLong("selected_project", projectId).apply();

        showHideButtons();

        DBProject proj = db.getProject(projectId);
        if (proj == null) {
            List<DBProject> dbProjects = db.getProjects();
            if (dbProjects.size() > 0) {
                proj = dbProjects.get(0);
                preferences.edit().putLong("selected_project", proj.getId()).apply();
            }
            else {
                selectedProjectLabel.setText(getString(R.string.drawer_no_project));
                return;
            }
        }

        // TODO add isLocal to project

        // we always set selected project text
        String selText;
        // local project
        if (proj.isLocal()) {
            selText = proj.getRemoteId() + "@local";
        }
        // remote project
        else {
            selText = (proj.getName() == null) ? "???" : proj.getName();
            selText += "\n";
            selText += proj.getRemoteId() + "@";
            selText += proj.getIhmUrl()
                    .replace("https://", "")
                    .replace("http://", "")
                    .replace("/index.php/apps/cospend", "");
        }
        selectedProjectLabel.setText(selText);
    }

    private void editMember(View view, long memberId) {
        final DBMember memberToEdit = db.getMember(memberId);
        Integer r = memberToEdit.getR();
        Integer g = memberToEdit.getG();
        Integer b = memberToEdit.getB();

        int color;
        if (r != null && g != null && b != null) {
            color = Color.rgb(memberToEdit.getR(), memberToEdit.getG(), memberToEdit.getB());
        }
        else {
            color = TextDrawable.getColorFromName(memberToEdit.getName());
        }

        Log.v(TAG, "MEMBER ID " + memberId);

        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(
                        view.getContext(),
                        R.style.AppThemeDialog
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

        TextView tvCol = iView.findViewById(R.id.editMemberColorLabel);
        tvCol.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
        Button bu = iView.findViewById(R.id.editMemberColor);
        bu.setBackgroundColor(color);
        bu.setText("");

        bu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View buview) {
                LayoutInflater inflater = getLayoutInflater();
                View colorView = inflater.inflate(R.layout.dialog_color, new LinearLayout(view.getContext()));

                final LobsterPicker lobsterPicker = colorView.findViewById(R.id.lobsterPicker);
                LobsterShadeSlider shadeSlider = colorView.findViewById(R.id.shadeSlider);

                lobsterPicker.addDecorator(shadeSlider);
                lobsterPicker.setColorHistoryEnabled(true);
                lobsterPicker.setHistory(color);
                lobsterPicker.setColor(color);

                new AlertDialog.Builder(new ContextThemeWrapper(
                        view.getContext(),
                        R.style.AppThemeDialog
                ))
                        .setView(colorView)
                        .setTitle(getString(R.string.settings_colorpicker_title))
                        .setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                int newColor = lobsterPicker.getColor();
                                bu.setBackgroundColor(newColor);
                            }
                        })
                        .setNegativeButton(getString(R.string.simple_cancel), null)
                        .show();

            }
        });

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
                    newMemberWeight = Double.valueOf(wvi.getText().toString().replace(',', '.'));
                }
                catch (Exception e) {
                    showToast(getString(R.string.member_edit_weight_error));
                    return;
                }

                CheckBox cvi = iView.findViewById(R.id.editMemberActivated);
                boolean newActivated = cvi.isChecked();

                Button bu = iView.findViewById(R.id.editMemberColor);
                int newColor = ((ColorDrawable) bu.getBackground()).getColor();
                int red = Color.red(newColor);
                int green = Color.green(newColor);
                int blue = Color.blue(newColor);

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                long selectedProjectId = preferences.getLong("selected_project", 0);

                if (selectedProjectId != 0) {
                    if (!newMemberName.isEmpty() && !newMemberName.equals("")) {
                        db.updateMemberAndSync(memberToEdit, newMemberName, newMemberWeight, newActivated, red, green, blue);
                        refreshLists();
                        // this was used to programmatically select member
                        //navigationSelection = new Category(newMemberName, memberToEdit.getId());
                    } else {
                        showToast(getString(R.string.member_edit_empty_name));
                    }
                }
                fabMenuDrawerEdit.close(false);
                // restore keyboard auto hide behaviour
                InputMethodManager inputMethodManager = (InputMethodManager) iView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        });
        builder.setNegativeButton(getString(R.string.simple_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                fabMenuDrawerEdit.close(false);
                // restore keyboard auto hide behaviour
                InputMethodManager inputMethodManager = (InputMethodManager) iView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        });

        builder.show();
        nv.setSelectAllOnFocus(true);
        nv.requestFocus();
        // show keyboard
        InputMethodManager inputMethodManager = (InputMethodManager) iView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    private void setupMembersNavigationList(final String selectedItem) {
        itemAll = new NavigationAdapter.NavigationItem(ADAPTER_KEY_ALL, getString(R.string.label_all_bills), null, R.drawable.ic_allgrey_24dp, false);

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
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            long selectedProjectId = preferences.getLong("selected_project", 0);

            ArrayList<NavigationAdapter.NavigationItem> items = new ArrayList<>();

            if (selectedProjectId == 0) {
                return items;
            }

            List<DBMember> dbMembers = db.getMembersOfProject(selectedProjectId, null);

            Map<Long, Integer> membersNbBills = new HashMap<>();
            HashMap<Long, Double> membersBalance = new HashMap<>();
            HashMap<Long, Double> membersPaid = new HashMap<>();
            HashMap<Long, Double> membersSpent = new HashMap<>();

            int nbBills = SupportUtil.getStatsOfProject(
                    selectedProjectId, db,
                    membersNbBills, membersBalance, membersPaid, membersSpent,
                    0, null, null, null
            );

            itemAll.count = nbBills;
            items.add(itemAll);

            NumberFormat balanceFormatter = new DecimalFormat("#0.00");
            NumberFormat weightFormatter = new DecimalFormat("#.##");

            for (DBMember m : dbMembers) {
                double balance = membersBalance.get(m.getId());
                double rBalance = Math.round( balance * 100.0 ) / 100.0;
                double rAbsBalance = Math.round( Math.abs(balance) * 100.0 ) / 100.0;
                String balanceStr = balanceFormatter.format(rAbsBalance).replace(",", ".");

                if (m.isActivated() || balance != 0.0) {
                    String weightStr = "";
                    if (m.getWeight() != 1) {
                        weightStr = " x" + weightFormatter.format(m.getWeight()).replace(",", ".");
                    }
                    String sign = "";
                    if (rBalance > 0.0) {
                        sign = "+";
                    }
                    else if (rBalance < 0.0) {
                         sign = "-";
                    }
                    NavigationAdapter.NavigationItem it = new NavigationAdapter.NavigationItem(
                            String.valueOf(m.getId()),
                            m.getName()+" ("+sign+balanceStr+")"+weightStr,
                            membersNbBills.get(m.getId()),
                            R.drawable.ic_person_grey_24dp,
                            true
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
        final NavigationAdapter.NavigationItem itemSettings = new NavigationAdapter.NavigationItem("settings", getString(R.string.action_settings), null, R.drawable.ic_settings_grey600_24dp, false);
        final NavigationAdapter.NavigationItem itemAbout = new NavigationAdapter.NavigationItem("about", "", null, -1, false);

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
                    // about is now triggered by a fab
                }
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
        long selectedProjectId = preferences.getLong("selected_project", 0);

        Log.v(TAG, "RESTORE PROJECT SELECTION " + selectedProjectId);
        setSelectedProject(selectedProjectId);

        //this.updateUsernameInDrawer();
    }

    public void initList() {
        adapter = new ItemAdapter(this, db);
        listView.setAdapter(adapter);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(linearLayoutManager);
        listView.addItemDecoration(new DividerItemDecoration(listView.getContext(),
                linearLayoutManager.getOrientation()));
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
             * Delete bill if it is swiped to left or right
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
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        final boolean offlineMode = preferences.getBoolean(getString(R.string.pref_key_offline_mode), false);
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
                                        if (!offlineMode) {
                                            synchronize();
                                        }
                                        //notifyLoggerService(dbBill.getId());
                                    }
                                })
                                .addCallback(new Snackbar.Callback() {

                                    @Override
                                    public void onDismissed(Snackbar snackbar, int event) {
                                        //see Snackbar.Callback docs for event details
                                        Log.v(TAG, "DISMISSED "+event);
                                        if (event == DISMISS_EVENT_TIMEOUT) {
                                            if (!offlineMode) {
                                                synchronize();
                                            }
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
                        //final DBBill dbBill = (DBBill) adapter.getItem(viewHolder.getAdapterPosition());

                        refreshLists();

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

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long selectedProjectId = preferences.getLong("selected_project", 0);

        boolean local = false;
        if (selectedProjectId != 0) {
            DBProject proj = db.getProject(selectedProjectId);
            local = proj.isLocal();
            if (proj != null) {
                projId = proj.getId();
                if (proj.isLocal()) {
                    projName = proj.getRemoteId();
                }
                else {
                    projName = (proj.getName() == null) ? "???" : proj.getName();
                }
            }
        }
        final boolean isProjectLocal = local;

        String subtitle;
        if (selectedProjectId != 0) {
            if (navigationSelection.memberName != null) {
                subtitle = projName + " - " + navigationSelection.memberName;
            } else {
                subtitle = projName + " - " + getString(R.string.label_all_bills);
            }
        }
        else {
            subtitle = getString(R.string.app_name);
        }
        // to display correct name on project selector when project was just added
        setSelectedProject(selectedProjectId);

        setTitle(subtitle);
        CharSequence query = null;
        if (searchView != null && !searchView.isIconified() && searchView.getQuery().length() != 0) {
            query = searchView.getQuery();
        }

        LoadBillsListTask.BillsLoadedListener callback = new LoadBillsListTask.BillsLoadedListener() {
            @Override
            public void onBillsLoaded(List<Item> billItems, boolean showCategory) {
                adapter.setProjectLocal(isProjectLocal);
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

    private void displaySearchHelp() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean noMoreSearchHelp = preferences.getBoolean(SettingsActivity.SETTINGS_NO_MORE_SEARCH_HELP, false);

        if (!noMoreSearchHelp) {
            AlertDialog.Builder helpBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.AppThemeDialog));
            helpBuilder.setTitle(getString(R.string.search_help_dialog_title));
            helpBuilder.setMessage(getString(R.string.search_help_dialog_content));

            // add OK and Cancel buttons
            helpBuilder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            helpBuilder.setNeutralButton(getString(R.string.simple_ok_no_more), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    preferences.edit().putBoolean(SettingsActivity.SETTINGS_NO_MORE_SEARCH_HELP, true).apply();
                }
            });

            AlertDialog selectDialog = helpBuilder.create();
            selectDialog.show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.search:
                displaySearchHelp();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

        final LinearLayout searchEditFrame = searchView.findViewById(androidx.appcompat.R.id
                .search_edit_frame);

        searchEditFrame.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            int oldVisibility = -1;
            @SuppressLint("RestrictedApi")
            @Override
            public void onGlobalLayout() {
                int currentVisibility = searchEditFrame.getVisibility();

                if (currentVisibility != oldVisibility) {
                    if (currentVisibility == View.VISIBLE) {
                        fabAddBill.setVisibility(View.INVISIBLE);
                        fabSidebarAddProject.setVisibility(View.INVISIBLE);
                    } else {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                fabAddBill.setVisibility(View.VISIBLE);
                                fabSidebarAddProject.setVisibility(View.VISIBLE);
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
     * Handles the Results of started Sub Activities
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
            if (DEBUG) { Log.d(TAG, "BILLS request code : addproject " + pid); }
            if (pid != 0) {
                setSelectedProject(pid);
            }
        } else if (requestCode == editproject) {
            if (data != null) {
                // adapt after project has been deleted
                long pid = data.getLongExtra(DELETED_PROJECT, 0);
                Log.d(TAG, "onActivityResult editproject PID : "+pid);
                if (pid != 0) {
                    setSelectedProject(0);
                }
                // adapt after project has been edited
                pid = data.getLongExtra(EDITED_PROJECT, 0);
                if (pid != 0) {
                    setSelectedProject(pid);
                }
            }
        } else if (requestCode == show_single_bill_cmd) {

        } else if (requestCode == scan_qrcode_import_cmd) {
            if (data != null) {
                // adapt after project has been deleted
                String scannedUrl = data.getStringExtra(QrCodeScanner.KEY_QR_CODE);
                Log.d(TAG, "onActivityResult SCANNED URL : "+scannedUrl);
                Intent i = new Intent(this, NewProjectActivity.class);
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse(scannedUrl));
                startActivity(i);
            }
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
                    Toast.makeText(getApplicationContext(), getString(R.string.error_sync, getString(CospendClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show();
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
            adapter.notifyDataSetChanged();
        } else {
            DBBill bill = (DBBill) adapter.getItem(position);
            Intent intent;
            intent = new Intent(getApplicationContext(), EditBillActivity.class);
            intent.putExtra(EditBillActivity.PARAM_BILL_ID, bill.getId());
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            long selectedProjectId = preferences.getLong("selected_project", 0);
            intent.putExtra(EditBillActivity.PARAM_PROJECT_TYPE, db.getProject(selectedProjectId).getType().getId());
            //intent.putExtra(EditBillActivity.PARAM_MEMBERS_BALANCE, membersBalance);
            startActivityForResult(intent, show_single_bill_cmd);
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
        adapter.notifyDataSetChanged();
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
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            long selectedProjectId = preferences.getLong("selected_project", 0);

            if (selectedProjectId != 0) {
                DBProject proj = db.getProject(selectedProjectId);
                if (proj != null && !proj.isLocal()) {
                    if (DEBUG) {
                        Log.d(TAG, "SYNC ASKED : " + selectedProjectId);
                    }
                    db.getMoneyBusterServerSyncHelper().addCallbackPull(syncCallBack);
                    db.getMoneyBusterServerSyncHelper().scheduleSync(false, selectedProjectId);
                }
                else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
            else {
                swipeRefreshLayout.setRefreshing(false);
            }
            // then sync the nextcloud account projects
            if (MoneyBusterServerSyncHelper.isNextcloudAccountConfigured(getApplicationContext())) {
                db.getMoneyBusterServerSyncHelper().runAccountProjectsSync();
            }
        }
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

                        // get up to date bill
                        final DBBill dbBill = db.getBill(bill.getId());
                        // get real original state to potentially restore it
                        final int originalState = dbBill.getState();

                        if (originalState == DBBill.STATE_ADDED) {
                            db.deleteBill(dbBill.getId());
                        }
                        else {
                            db.setBillState(dbBill.getId(), DBBill.STATE_DELETED);
                        }
                    }
                    mode.finish();
                    // delete selection has to be cleared
                    searchView.setIconified(true);
                    refreshLists();
                    return true;
                case R.id.menu_select_all:
                    adapter.clearSelection();
                    for (int i=0; i < adapter.getItemCount(); i++) {
                        adapter.select(i);
                    }
                    adapter.notifyDataSetChanged();

                    int checkedItemCount = adapter.getSelected().size();
                    mActionMode.setTitle(getResources().getQuantityString(R.plurals.ab_selected, checkedItemCount, checkedItemCount));

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

        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.sync_success_toast,
                (ViewGroup) findViewById(R.id.custom_toast_container));

        ImageView im = layout.findViewById(R.id.toast_icon);
        im.setImageResource(R.drawable.ic_info_outline_grey600_24dp);
        TextView tv = (TextView) layout.findViewById(R.id.text);
        tv.setText(text);

        Toast toast = new Toast(context);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
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
        filter.addAction(MoneyBusterServerSyncHelper.BROADCAST_SYNC_PROJECT);
        filter.addAction(MoneyBusterServerSyncHelper.BROADCAST_NETWORK_AVAILABLE);
        filter.addAction(MoneyBusterServerSyncHelper.BROADCAST_NETWORK_UNAVAILABLE);
        filter.addAction(BROADCAST_ACCOUNT_PROJECTS_SYNC_FAILED);
        filter.addAction(BROADCAST_ACCOUNT_PROJECTS_SYNCED);
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
                    //showToast(getString(R.string.project_sync_success, projName));

                    // show sync success toast
                    LayoutInflater inflater = getLayoutInflater();
                    View layout = inflater.inflate(R.layout.sync_success_toast,
                            (ViewGroup) findViewById(R.id.custom_toast_container));

                    LinearLayout ll = layout.findViewById(R.id.custom_toast_container);
                    ll.setBackgroundColor(Color.TRANSPARENT);
                    TextView text = (TextView) layout.findViewById(R.id.text);
                    //text.setText(getString(R.string.project_sync_success, projName));
                    text.setText("");

                    Toast toast = new Toast(getApplicationContext());
                    toast.setGravity(Gravity.TOP | Gravity.LEFT, 75, 16);
                    toast.setDuration(Toast.LENGTH_SHORT);
                    toast.setView(layout);
                    toast.show();

                    break;
                case MoneyBusterServerSyncHelper.BROADCAST_SYNC_PROJECT:
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    boolean offlineMode = preferences.getBoolean(getString(R.string.pref_key_offline_mode), false);
                    if (!offlineMode) {
                        synchronize();
                    }
                    break;
                case MoneyBusterServerSyncHelper.BROADCAST_NETWORK_AVAILABLE:
                    swipeRefreshLayout.setEnabled(true);
                    break;
                case MoneyBusterServerSyncHelper.BROADCAST_NETWORK_UNAVAILABLE:
                    swipeRefreshLayout.setEnabled(false);
                    break;
                case BROADCAST_ACCOUNT_PROJECTS_SYNCED:
                    // show account projects sync success toast
                    LayoutInflater inflater2 = getLayoutInflater();
                    View layout2 = inflater2.inflate(R.layout.sync_success_toast,
                            (ViewGroup) findViewById(R.id.custom_toast_container));

                    LinearLayout ll2 = layout2.findViewById(R.id.custom_toast_container);
                    ll2.setBackgroundColor(Color.TRANSPARENT);
                    TextView text2 = (TextView) layout2.findViewById(R.id.text);
                    text2.setText("");
                    ImageView im = layout2.findViewById(R.id.toast_icon);
                    im.setImageResource(R.drawable.ic_nextcloud_logo_white);

                    Toast toast2 = new Toast(getApplicationContext());
                    toast2.setGravity(Gravity.TOP | Gravity.LEFT, 75, 62);
                    toast2.setDuration(Toast.LENGTH_SHORT);
                    toast2.setView(layout2);
                    toast2.show();

                    // select a project if there are some and none is selected
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    long selectedProjectId = prefs.getLong("selected_project", 0);
                    List<DBProject> dbProjects = db.getProjects();
                    if (selectedProjectId == 0 && dbProjects.size() > 0) {
                        setSelectedProject(dbProjects.get(0).getId());
                        Log.v(TAG, "set selection 0");
                        refreshLists();
                        if (!db.getMoneyBusterServerSyncHelper().isSyncPossible()) {
                            swipeRefreshLayout.setEnabled(false);
                        }
                        else {
                            swipeRefreshLayout.setEnabled(true);
                            db.getMoneyBusterServerSyncHelper().addCallbackPull(syncCallBack);
                            boolean offlineMode2 = prefs.getBoolean(getString(R.string.pref_key_offline_mode), false);
                            if (!offlineMode2) {
                                synchronize();
                            }
                        }
                    }
                    break;
            }
        }
    };
}