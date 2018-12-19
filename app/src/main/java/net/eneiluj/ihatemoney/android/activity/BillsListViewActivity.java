package net.eneiluj.ihatemoney.android.activity;

import android.Manifest;
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
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.support.v7.widget.helper.ItemTouchHelper.SimpleCallback;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import net.eneiluj.ihatemoney.R;
import net.eneiluj.ihatemoney.model.Category;
import net.eneiluj.ihatemoney.model.DBLocation;
import net.eneiluj.ihatemoney.model.DBLogjob;
import net.eneiluj.ihatemoney.model.Item;
import net.eneiluj.ihatemoney.model.ItemAdapter;
import net.eneiluj.ihatemoney.model.NavigationAdapter;
import net.eneiluj.ihatemoney.model.SyncError;
import net.eneiluj.ihatemoney.persistence.IHateMoneySQLiteOpenHelper;
import net.eneiluj.ihatemoney.persistence.LoadLogjobsListTask;
import net.eneiluj.ihatemoney.persistence.SessionServerSyncHelper;
import net.eneiluj.ihatemoney.service.LoggerService;
import net.eneiluj.ihatemoney.service.WebTrackService;
import net.eneiluj.ihatemoney.util.ICallback;
import net.eneiluj.ihatemoney.util.PhoneTrackClientUtil;

public class BillsListViewActivity extends AppCompatActivity implements ItemAdapter.LogjobClickListener {

    private final static int PERMISSION_LOCATION = 1;

    private final static int PERMISSION_FOREGROUND_SERVICE = 1;

    private static final String TAG = BillsListViewActivity.class.getSimpleName();

    public final static String CREATED_LOGJOB = "net.eneiluj.ihatemoney.created_logjob";
    public final static String CREDENTIALS_CHANGED = "net.eneiluj.ihatemoney.CREDENTIALS_CHANGED";
    public static final String ADAPTER_KEY_ALL = "all";
    public static final String ADAPTER_KEY_ENABLED = "enabled";
    public static final String ADAPTER_KEY_PHONETRACK = "pt";
    public static final String ADAPTER_KEY_CUSTOM = "custom";
    public static final String CATEGORY_PHONETRACK = "pt";
    public static final String CATEGORY_CUSTOM = "cu";

    public final static String UPDATED_LOGJOBS = "net.eneiluj.ihatemoney.UPDATED_LOGJOBS";
    public final static String UPDATED_LOGJOB_ID = "net.eneiluj.ihatemoney.UPDATED_LOGJOB_ID";

    private static final String SAVED_STATE_NAVIGATION_SELECTION = "navigationSelection";
    private static final String SAVED_STATE_NAVIGATION_ADAPTER_SLECTION = "navigationAdapterSelection";
    private static final String SAVED_STATE_NAVIGATION_OPEN = "navigationOpen";

    private final static int create_logjob_cmd = 0;
    private final static int show_single_logjob_cmd = 1;
    private final static int server_settings = 2;
    private final static int about = 3;
    private final static int addproject = 4;


    @BindView(R.id.logjobsListActivityActionBar)
    Toolbar toolbar;
    @BindView(R.id.drawerLayout)
    DrawerLayout drawerLayout;
    @BindView(R.id.account)
    TextView account;
    @BindView(R.id.swiperefreshlayout)
    SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.fab_create_phonetrack)
    com.github.clans.fab.FloatingActionButton fabCreatePhoneTrack;
    @BindView(R.id.fab_create_custom)
    com.github.clans.fab.FloatingActionButton fabCreateCustom;
    @BindView(R.id.floatingMenu)
    com.github.clans.fab.FloatingActionMenu fabMenu;
    @BindView(R.id.navigationList)
    RecyclerView listNavigationCategories;
    @BindView(R.id.navigationMenu)
    RecyclerView listNavigationMenu;
    @BindView(R.id.recycler_view)
    RecyclerView listView;

    private ActionBarDrawerToggle drawerToggle;
    private ItemAdapter adapter = null;
    private NavigationAdapter adapterCategories;
    private NavigationAdapter.NavigationItem itemAll, itemEnabled, itemPhonetrack, itemCustom, itemUncategorized;
    private Category navigationSelection = new Category(null, null);
    private String navigationOpen = "";
    private ActionMode mActionMode;
    private IHateMoneySQLiteOpenHelper db = null;
    private SearchView searchView = null;
    private ICallback syncCallBack = new ICallback() {
        @Override
        public void onFinish() {
            adapter.clearSelection();
            if (mActionMode != null) {
                mActionMode.finish();
            }
            refreshLists();
            //swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public void onFinish(String result, String message) {
        }

        @Override
        public void onScheduled() {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // First Run Wizard
        /*if (!SessionServerSyncHelper.isConfigured(this)) {
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

        db = IHateMoneySQLiteOpenHelper.getInstance(this);

        setupActionBar();
        setupLogjobsList();
        setupNavigationList(categoryAdapterSelectedItem);
        setupNavigationMenu();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[request 2 permissions]"); }
            ActivityCompat.requestPermissions(BillsListViewActivity.this, new String[]{Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION);
        }
        else {
            if (LoggerService.DEBUG) { Log.d(TAG, "[request 1 permission]"); }
            ActivityCompat.requestPermissions(BillsListViewActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION);
        }

        Map<String, Integer> enabled = db.getEnabledCount();
        int nbEnabledLogjobs = enabled.containsKey("1") ? enabled.get("1") : 0;
        if (nbEnabledLogjobs > 0) {
            // start loggerservice !
            Intent intent = new Intent(BillsListViewActivity.this, LoggerService.class);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                startService(intent);
            } else {
                startForegroundService(intent);
            }
        }
    }

    @Override
    protected void onResume() {
        // refresh and sync every time the activity gets visible
        refreshLists();
        swipeRefreshLayout.setRefreshing(false);
        db.getIhateMoneyServerSyncHelper().addCallbackPull(syncCallBack);
        if (db.getIhateMoneyServerSyncHelper().isSyncPossible()) {
            synchronize();
        }
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
        if (LoggerService.DEBUG) { Log.d(TAG, "[onPause]"); }
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
        outState.putString(SAVED_STATE_NAVIGATION_ADAPTER_SLECTION, adapterCategories.getSelectedItem());
        outState.putString(SAVED_STATE_NAVIGATION_OPEN, navigationOpen);
    }

    private void setupActionBar() {
        setSupportActionBar(toolbar);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.action_drawer_open, R.string.action_drawer_close);
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayout.addDrawerListener(drawerToggle);
    }

    private void setupLogjobsList() {
        initList();
        // Pull to Refresh
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (db.getIhateMoneyServerSyncHelper().isSyncPossible()) {
                    synchronize();
                } else {
                    //swipeRefreshLayout.setRefreshing(false);
                    // don't bother user if no conf
                    if (SessionServerSyncHelper.isConfigured(getApplicationContext())) {
                        Toast.makeText(getApplicationContext(), getString(R.string.error_sync, getString(PhoneTrackClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show();
                    }
                }
                if (db.getLocationCount() > 0) {
                    Intent syncIntent = new Intent(BillsListViewActivity.this, WebTrackService.class);
                    startService(syncIntent);
                    showToast(getString(R.string.uploading_started));
                }
                else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        });

        fabCreateCustom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent createIntent = new Intent(getApplicationContext(), EditCustomLogjobActivity.class);
                startActivityForResult(createIntent, create_logjob_cmd);
                fabMenu.close(false);
            }
        });
        fabCreatePhoneTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent createIntent = new Intent(getApplicationContext(), EditPhoneTrackLogjobActivity.class);
                startActivityForResult(createIntent, create_logjob_cmd);
                fabMenu.close(false);
            }
        });
    }

    private void setupNavigationList(final String selectedItem) {
        itemAll = new NavigationAdapter.NavigationItem(ADAPTER_KEY_ALL, getString(R.string.label_all_logjobs), null, R.drawable.ic_allgrey_24dp);
        itemEnabled = new NavigationAdapter.NavigationItem(ADAPTER_KEY_ENABLED, getString(R.string.label_enabled), null, R.drawable.ic_check_box_grey_24dp);
        itemPhonetrack = new NavigationAdapter.NavigationItem(ADAPTER_KEY_PHONETRACK, getString(R.string.label_phonetrack_lj), null, R.drawable.ic_phonetrack_grey_24dp);
        itemCustom = new NavigationAdapter.NavigationItem(ADAPTER_KEY_CUSTOM, getString(R.string.label_custom_lj), null, R.drawable.ic_link_menu_grey_24dp);
        adapterCategories = new NavigationAdapter(new NavigationAdapter.ClickListener() {
            @Override
            public void onItemClick(NavigationAdapter.NavigationItem item) {
                selectItem(item, true);
            }

            private void selectItem(NavigationAdapter.NavigationItem item, boolean closeNavigation) {
                adapterCategories.setSelectedItem(item.id);

                // update current selection
                if (itemAll == item) {
                    navigationSelection = new Category(null, null);
                } else if (itemEnabled == item) {
                    navigationSelection = new Category(null, true);
                } else if (itemUncategorized == item) {
                    navigationSelection = new Category("", null);
                } else if (itemPhonetrack == item) {
                    navigationSelection = new Category(CATEGORY_PHONETRACK, null);
                } else if (itemCustom == item) {
                    navigationSelection = new Category(CATEGORY_CUSTOM, null);
                } else {
                    navigationSelection = new Category(item.label, null);
                }

                // auto-close sub-folder in Navigation if selection is outside of that folder
                if (navigationOpen != null) {
                    int slashIndex = navigationSelection.category == null ? -1 : navigationSelection.category.indexOf('/');
                    String rootCategory = slashIndex < 0 ? navigationSelection.category : navigationSelection.category.substring(0, slashIndex);
                    if (!navigationOpen.equals(rootCategory)) {
                        navigationOpen = null;
                    }
                }

                // update views
                if (closeNavigation) {
                    drawerLayout.closeDrawers();
                }
                refreshLists(true);
            }

            @Override
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
            }
        });
        adapterCategories.setSelectedItem(selectedItem);
        listNavigationCategories.setAdapter(adapterCategories);
    }


    private class LoadCategoryListTask extends AsyncTask<Void, Void, List<NavigationAdapter.NavigationItem>> {
        @Override
        protected List<NavigationAdapter.NavigationItem> doInBackground(Void... voids) {
            /*List<NavigationAdapter.NavigationItem> categories = db.getCategories();
            if (!categories.isEmpty() && categories.get(0).label.isEmpty()) {
                itemUncategorized = categories.get(0);
                itemUncategorized.label = getString(R.string.action_uncategorized);
                itemUncategorized.icon = NavigationAdapter.ICON_NOFOLDER;
            } else {
                itemUncategorized = null;
            }*/
            itemUncategorized = null;

            int nbPT = 0;
            int nbCU = 0;
            List<DBLogjob> ljs = db.getLogjobs();
            for (DBLogjob lj : ljs) {
                if (lj.getToken().isEmpty() && lj.getDeviceName().isEmpty()) {
                    nbCU++;
                }
                else {
                    nbPT++;
                }
            }

            Map<String, Integer> favorites = db.getEnabledCount();
            int numFavorites = favorites.containsKey("1") ? favorites.get("1") : 0;
            int numNonFavorites = favorites.containsKey("0") ? favorites.get("0") : 0;
            itemEnabled.count = numFavorites;
            itemAll.count = numFavorites + numNonFavorites;
            itemPhonetrack.count = nbPT;
            itemCustom.count = nbCU;

            ArrayList<NavigationAdapter.NavigationItem> items = new ArrayList<>();
            items.add(itemAll);
            items.add(itemEnabled);
            items.add(itemPhonetrack);
            items.add(itemCustom);
            NavigationAdapter.NavigationItem lastPrimaryCategory = null, lastSecondaryCategory = null;
            /*for (NavigationAdapter.NavigationItem item : categories) {
                int slashIndex = item.label.indexOf('/');
                String currentPrimaryCategory = slashIndex < 0 ? item.label : item.label.substring(0, slashIndex);
                String currentSecondaryCategory = null;
                boolean isCategoryOpen = currentPrimaryCategory.equals(navigationOpen);

                if (isCategoryOpen && !currentPrimaryCategory.equals(item.label)) {
                    String currentCategorySuffix = item.label.substring(navigationOpen.length() + 1);
                    int subSlashIndex = currentCategorySuffix.indexOf('/');
                    currentSecondaryCategory = subSlashIndex < 0 ? currentCategorySuffix : currentCategorySuffix.substring(0, subSlashIndex);
                }

                boolean belongsToLastPrimaryCategory = lastPrimaryCategory != null && currentPrimaryCategory.equals(lastPrimaryCategory.label);
                boolean belongsToLastSecondaryCategory = belongsToLastPrimaryCategory && lastSecondaryCategory != null && lastSecondaryCategory.label.equals(currentPrimaryCategory + "/" + currentSecondaryCategory);

                if (isCategoryOpen && !belongsToLastPrimaryCategory && currentSecondaryCategory != null) {
                    lastPrimaryCategory = new NavigationAdapter.NavigationItem("category:" + currentPrimaryCategory, currentPrimaryCategory, 0, NavigationAdapter.ICON_MULTIPLE_OPEN);
                    items.add(lastPrimaryCategory);
                    belongsToLastPrimaryCategory = true;
                }

                if (belongsToLastPrimaryCategory && belongsToLastSecondaryCategory) {
                    lastSecondaryCategory.count += item.count;
                    lastSecondaryCategory.icon = NavigationAdapter.ICON_SUB_MULTIPLE;
                } else if (belongsToLastPrimaryCategory) {
                    if (isCategoryOpen) {
                        item.label = currentPrimaryCategory + "/" + currentSecondaryCategory;
                        item.id = "category:" + item.label;
                        item.icon = NavigationAdapter.ICON_SUB_FOLDER;
                        items.add(item);
                        lastSecondaryCategory = item;
                    } else {
                        lastPrimaryCategory.count += item.count;
                        lastPrimaryCategory.icon = NavigationAdapter.ICON_MULTIPLE;
                        lastSecondaryCategory = null;
                    }
                } else {
                    if (isCategoryOpen) {
                        item.icon = NavigationAdapter.ICON_MULTIPLE_OPEN;
                    } else {
                        item.label = currentPrimaryCategory;
                        item.id = "category:" + item.label;
                    }
                    items.add(item);
                    lastPrimaryCategory = item;
                    lastSecondaryCategory = null;
                }
            }*/
            return items;
        }

        @Override
        protected void onPostExecute(List<NavigationAdapter.NavigationItem> items) {
            adapterCategories.setItems(items);
        }
    }


    private void setupNavigationMenu() {
        //final NavigationAdapter.NavigationItem itemTrashbin = new NavigationAdapter.NavigationItem("trashbin", getString(R.string.action_trashbin), null, R.drawable.ic_delete_grey600_24dp);
        final NavigationAdapter.NavigationItem itemAddProject = new NavigationAdapter.NavigationItem("addproject", getString(R.string.action_add_project), null, R.drawable.ic_add_green_24dp);
        final NavigationAdapter.NavigationItem itemSettings = new NavigationAdapter.NavigationItem("settings", getString(R.string.action_settings), null, R.drawable.ic_settings_grey600_24dp);
        final NavigationAdapter.NavigationItem itemAbout = new NavigationAdapter.NavigationItem("about", getString(R.string.simple_about), null, R.drawable.ic_info_outline_grey600_24dp);

        ArrayList<NavigationAdapter.NavigationItem> itemsMenu = new ArrayList<>();
        itemsMenu.add(itemAddProject);
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
                } else if (item == itemAddProject) {
                    Intent newProjectIntent = new Intent(getApplicationContext(), NewProjectActivity.class);
                    startActivityForResult(newProjectIntent, addproject);
                }
            }

            @Override
            public void onIconClick(NavigationAdapter.NavigationItem item) {
                onItemClick(item);
            }
        });


        this.updateUsernameInDrawer();
        final BillsListViewActivity that = this;
        this.account.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsIntent = new Intent(that, SettingsActivity.class);
                startActivityForResult(settingsIntent, server_settings);
            }
        });

        adapterMenu.setItems(itemsMenu);
        listNavigationMenu.setAdapter(adapterMenu);
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
                        final DBLogjob dbLogjob = (DBLogjob) adapter.getItem(viewHolder.getAdapterPosition());
                        // get locations
                        final List<DBLocation> locations = db.getLocationOfLogjob(String.valueOf(dbLogjob.getId()));
                        db.deleteLogjob(dbLogjob.getId());
                        adapter.remove(dbLogjob);
                        refreshLists();
                        Log.v(TAG, "Item deleted through swipe ----------------------------------------------");
                        Snackbar.make(swipeRefreshLayout, R.string.action_logjob_deleted, Snackbar.LENGTH_LONG)
                                .setAction(R.string.action_undo, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        db.addLogjob(dbLogjob);
                                        for (DBLocation dbloc : locations) {
                                            db.addLocation(dbloc);
                                        }
                                        refreshLists();
                                        Snackbar.make(swipeRefreshLayout, R.string.action_logjob_restored, Snackbar.LENGTH_SHORT)
                                                .show();
                                        notifyLoggerService(dbLogjob.getId());
                                    }
                                })
                                .show();
                        notifyLoggerService(dbLogjob.getId());
                        break;
                    }
                    case ItemTouchHelper.RIGHT: {
                        final DBLogjob dbLogjob = (DBLogjob) adapter.getItem(viewHolder.getAdapterPosition());
                        db.toggleEnabled(dbLogjob, syncCallBack);
                        refreshLists();
                        notifyLoggerService(dbLogjob.getId());
                        break;
                    }
                }
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                ItemAdapter.LogjobViewHolder logjobViewHolder = (ItemAdapter.LogjobViewHolder) viewHolder;
                // show swipe icon on the side
                logjobViewHolder.showSwipe(dX>0);
                // move only swipeable part of item (not leave-behind)
                getDefaultUIUtil().onDraw(c, recyclerView, logjobViewHolder.logjobSwipeable, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                getDefaultUIUtil().clearView(((ItemAdapter.LogjobViewHolder) viewHolder).logjobSwipeable);
            }
        });
        touchHelper.attachToRecyclerView(listView);
    }

    private void refreshLists() {
        refreshLists(false);
    }
    private void refreshLists(final boolean scrollToTop) {
        String subtitle;
        if (navigationSelection.favorite != null && navigationSelection.favorite) {
            subtitle = getString(R.string.app_name) + " - " + getString(R.string.label_enabled);
        } else if (navigationSelection.category == CATEGORY_PHONETRACK) {
            subtitle = getString(R.string.app_name);
        } else if (navigationSelection.category == CATEGORY_CUSTOM) {
            subtitle = getString(R.string.app_name) + " - " + getString(R.string.label_custom);
        } else {
            subtitle = getString(R.string.app_name) + " - " + getString(R.string.label_all_logjobs);
        }
        setTitle(subtitle);
        CharSequence query = null;
        if (searchView != null && !searchView.isIconified() && searchView.getQuery().length() != 0) {
            query = searchView.getQuery();
        }

        LoadLogjobsListTask.LogjobsLoadedListener callback = new LoadLogjobsListTask.LogjobsLoadedListener() {
            @Override
            public void onLogjobsLoaded(List<Item> ljItems, boolean showCategory) {
                adapter.setShowCategory(showCategory);
                adapter.setItemList(ljItems);
                if(scrollToTop) {
                    listView.scrollToPosition(0);
                }
            }
        };
        new LoadLogjobsListTask(getApplicationContext(), callback, navigationSelection, query).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
        // Check which request we're responding to
        if (requestCode == create_logjob_cmd) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                //not need because of db.synchronisation in createActivity

                DBLogjob createdLogjob = (DBLogjob) data.getExtras().getSerializable(CREATED_LOGJOB);
                adapter.add(createdLogjob);
            }
            listView.scrollToPosition(0);
        } else if (requestCode == server_settings) {
            // Create new Instance with new URL and credentials
            db = IHateMoneySQLiteOpenHelper.getInstance(this);
            if (db.getIhateMoneyServerSyncHelper().isSyncPossible()) {
                this.updateUsernameInDrawer();
                adapter.removeAll();
                synchronize();
            } else {
                if (SessionServerSyncHelper.isConfigured(getApplicationContext())) {
                    Toast.makeText(getApplicationContext(), getString(R.string.error_sync, getString(PhoneTrackClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void updateUsernameInDrawer() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String username = preferences.getString(SettingsActivity.SETTINGS_USERNAME, SettingsActivity.DEFAULT_SETTINGS);
        String url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS).replace("https://", "").replace("http://", "");
        if(!SettingsActivity.DEFAULT_SETTINGS.equals(username) && !SettingsActivity.DEFAULT_SETTINGS.equals(url)) {
            this.account.setText(username + "@" + url.substring(0, url.length() - 1));
        }
        else {
            this.account.setText("Tap here to connect");
        }
    }

    @Override
    public void onLogjobClick(int position, View v) {
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
            DBLogjob logjob = (DBLogjob) adapter.getItem(position);
            Intent intent;
            if (logjob.getToken().isEmpty() && logjob.getDeviceName().isEmpty()) {
                intent = new Intent(getApplicationContext(), EditCustomLogjobActivity.class);
            }
            else {
                intent = new Intent(getApplicationContext(), EditPhoneTrackLogjobActivity.class);
            }
            intent.putExtra(EditLogjobActivity.PARAM_LOGJOB_ID, logjob.getId());
            startActivityForResult(intent, show_single_logjob_cmd);

        }
    }

    @Override
    public void onLogjobEnabledClick(int position, View view) {
        DBLogjob logjob = (DBLogjob) adapter.getItem(position);
        if (logjob != null) {
            IHateMoneySQLiteOpenHelper db = IHateMoneySQLiteOpenHelper.getInstance(view.getContext());
            db.toggleEnabled(logjob, syncCallBack);
            adapter.notifyItemChanged(position);
            refreshLists();

            notifyLoggerService(logjob.getId());
        }
    }

    @Override
    public void onLogjobInfoButtonClick(int position, View view) {
        DBLogjob logjob = (DBLogjob) adapter.getItem(position);
        if (logjob != null) {
            String ljId = String.valueOf(logjob.getId());
            IHateMoneySQLiteOpenHelper db = IHateMoneySQLiteOpenHelper.getInstance(view.getContext());
            long tsLastLoc = db.getLastLocTimestamp(ljId);
            long tsLastSync = db.getLastSyncTimestamp(ljId);
            SyncError lastSyncErr = db.getLastSyncError(ljId);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

            if (LoggerService.DEBUG) { Log.d(TAG, "[LAST " + tsLastLoc + " "+tsLastSync+ "]"); }

            String nbsyncText = view.getContext().getString(R.string.logjob_info_nbsync, logjob.getNbSync());
            String nbnotsyncText = view.getContext().getString(R.string.logjob_info_nbnotsync, db.getLogjobLocationCount(logjob.getId()));
            String lastLocText = "";
            String lastSyncText = "";
            String lastSyncErrText = "";

            View iView = LayoutInflater.from(this).inflate(R.layout.items_infodialog, null);
            TextView tv = iView.findViewById(R.id.infoNbsyncText);
            tv.setText(nbsyncText);
            TextView tv2 = iView.findViewById(R.id.infoNbnotsyncText);
            tv2.setText(nbnotsyncText);

            if (tsLastLoc != 0) {
                Date d = new Date(tsLastLoc*1000);
                lastLocText = view.getContext().getString(R.string.logjob_info_lastloc, sdf.format(d));

                TextView tv3 = iView.findViewById(R.id.infoLastLocText);
                tv3.setText(lastLocText);
            }
            else {
                iView.findViewById(R.id.infoLastLocLayout).setVisibility(View.GONE);
            }
            if (tsLastSync != 0) {
                Date d = new Date(tsLastSync*1000);
                lastSyncText = view.getContext().getString(R.string.logjob_info_lastsync, sdf.format(d));

                TextView tv4 = iView.findViewById(R.id.infoLastSyncText);
                tv4.setText(lastSyncText);
            }
            else {
                iView.findViewById(R.id.infoLastSyncLayout).setVisibility(View.GONE);
            }

            if (lastSyncErr.getTimestamp() != 0) {
                Date d = new Date(lastSyncErr.getTimestamp()*1000);
                lastSyncErrText = view.getContext().getString(R.string.logjob_info_lastsync_error, sdf.format(d), lastSyncErr.getMessage());

                TextView tv5 = iView.findViewById(R.id.infoLastSyncErrText);
                tv5.setText(lastSyncErrText);
            }
            else {
                iView.findViewById(R.id.infoLastSyncErrLayout).setVisibility(View.GONE);
            }

            AlertDialog.Builder builder;
            builder = new AlertDialog.Builder(view.getContext(), android.R.style.Theme_Material_Dialog_Alert);
            builder.setTitle(view.getContext().getString(R.string.logjob_info_dialog_title, logjob.getTitle()))
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
    public boolean onLogjobLongClick(int position, View v) {
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
        //swipeRefreshLayout.setRefreshing(true);
        db.getIhateMoneyServerSyncHelper().addCallbackPull(syncCallBack);
        db.getIhateMoneyServerSyncHelper().scheduleSync(false);
    }

    private void notifyLoggerService(long jobId) {
        Intent intent = new Intent(BillsListViewActivity.this, LoggerService.class);
        intent.putExtra(UPDATED_LOGJOBS, true);
        intent.putExtra(UPDATED_LOGJOB_ID, jobId);
        startService(intent);
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
                        DBLogjob logjob = (DBLogjob) adapter.getItem(i);
                        db.deleteLogjob(logjob.getId());
                        // Not needed because of dbsync
                        //adapter.remove(logjob);
                        notifyLoggerService(logjob.getId());
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
        filter.addAction(LoggerService.BROADCAST_LOCATION_STARTED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_STOPPED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_UPDATED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_GPS_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_GPS_ENABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED);
        filter.addAction(WebTrackService.BROADCAST_SYNC_STARTED);
        filter.addAction(WebTrackService.BROADCAST_SYNC_DONE);
        filter.addAction(WebTrackService.BROADCAST_SYNC_FAILED);
        filter.addAction(SessionServerSyncHelper.BROADCAST_SESSIONS_SYNC_FAILED);
        filter.addAction(SessionServerSyncHelper.BROADCAST_SESSIONS_SYNCED);
        registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Broadcast receiver
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[broadcast received " + intent + "]"); }
            if (intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case LoggerService.BROADCAST_LOCATION_UPDATED:
                    String ljId = intent.getStringExtra(LoggerService.BROADCAST_EXTRA_PARAM);
                    if (LoggerService.DEBUG) { Log.d(TAG, "[broadcast loc updated " + ljId + "]"); }
                    // to update all items
                    //adapter.notifyDataSetChanged();
                    // but we update just the changed one
                    DBLogjob lj;
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        lj = (DBLogjob) adapter.getItem(i);
                        if (String.valueOf(lj.getId()).equals(ljId)) {
                            adapter.notifyItemChanged(i);
                            break;
                        }
                    }
                    break;
                case WebTrackService.BROADCAST_SYNC_STARTED:
                    swipeRefreshLayout.setRefreshing(true);
                    break;
                // when sync is finished (fail or success)
                case WebTrackService.BROADCAST_SYNC_DONE:
                    String ljId2 = intent.getStringExtra(LoggerService.BROADCAST_EXTRA_PARAM);
                    if (ljId2 != null) {
                        if (LoggerService.DEBUG) {
                            Log.d(TAG, "[broadcast loc synced " + ljId2 + "]");
                        }
                        // to update all items
                        //adapter.notifyDataSetChanged();
                        // but we update just the changed one
                        DBLogjob lj2;
                        for (int i = 0; i < adapter.getItemCount(); i++) {
                            lj2 = (DBLogjob) adapter.getItem(i);
                            if (String.valueOf(lj2.getId()).equals(ljId2)) {
                                adapter.notifyItemChanged(i);
                                if (LoggerService.DEBUG) {
                                    Log.d(TAG, "[notifyItemChanged " + i + "]");
                                }
                                break;
                            }
                        }
                    }
                    // without parameter : end of sync service
                    else {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    break;
                case (WebTrackService.BROADCAST_SYNC_FAILED): {
                    // TODO show that there was an error for the logjob
                    // TODO let the user see the error...
                    String ljId3 = intent.getStringExtra(LoggerService.BROADCAST_EXTRA_PARAM);
                    String errorMessage = intent.getStringExtra(LoggerService.BROADCAST_ERROR_MESSAGE);
                    showToast(getString(R.string.uploading_failed) + "\n" + errorMessage, Toast.LENGTH_LONG);
                    break;
                }
                case SessionServerSyncHelper.BROADCAST_SESSIONS_SYNC_FAILED:
                    String errorMessage = intent.getStringExtra(LoggerService.BROADCAST_ERROR_MESSAGE);
                    showToast(errorMessage, Toast.LENGTH_LONG);
                    break;
                case SessionServerSyncHelper.BROADCAST_SESSIONS_SYNCED:
                    showToast(getString(R.string.sessions_sync_success));
                    break;
                case LoggerService.BROADCAST_LOCATION_STARTED:
                    showToast(getString(R.string.tracking_started));
                    //setLocLed(LED_YELLOW);
                    break;
                case LoggerService.BROADCAST_LOCATION_STOPPED:
                    showToast(getString(R.string.tracking_stopped));
                    //setLocLed(LED_RED);
                    break;
                case LoggerService.BROADCAST_LOCATION_GPS_DISABLED:
                    showToast(getString(R.string.gps_disabled_warning), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED:
                    showToast(getString(R.string.net_disabled_warning), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_DISABLED:
                    showToast(getString(R.string.location_disabled), Toast.LENGTH_LONG);
                    //setLocLed(LED_RED);
                    break;
                case LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED:
                    showToast(getString(R.string.using_network), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_GPS_ENABLED:
                    showToast(getString(R.string.using_gps), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED:
                    showToast(getString(R.string.location_permission_denied), Toast.LENGTH_LONG);
                    //setLocLed(LED_RED);
                    ActivityCompat.requestPermissions(BillsListViewActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION);
                    break;
            }
        }
    };
}