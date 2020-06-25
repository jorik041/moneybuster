package net.eneiluj.moneybuster.android.activity;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.android.fragment.about.AboutFragmentContributingTab;
import net.eneiluj.moneybuster.android.fragment.about.AboutFragmentCreditsTab;
import net.eneiluj.moneybuster.android.fragment.about.AboutFragmentLicenseTab;
import net.eneiluj.moneybuster.util.ThemeUtils;

public class AboutActivity extends AppCompatActivity {

    ViewPager mViewPager;
    TabLayout mTabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = LayoutInflater.from(this).inflate(R.layout.activity_about, null);
        setContentView(view);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mViewPager = findViewById(R.id.pager);
        mTabLayout = findViewById(R.id.tabs);
        mTabLayout.setSelectedTabIndicatorColor(ThemeUtils.primaryColor(this));

        mViewPager.setAdapter(new TabsPagerAdapter(getSupportFragmentManager()));
        mTabLayout.setupWithViewPager(mViewPager);
    }

    @Override
    public void onBackPressed() {
        //NavUtils.navigateUpFromSameTask(this);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                //Toast.makeText(getApplicationContext(),"Back button clicked", Toast.LENGTH_LONG).show();
                finish();
                break;
        }
        return true;
    }

    private class TabsPagerAdapter extends FragmentPagerAdapter {
        private final int PAGE_COUNT = 3;

        public TabsPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        /**
         * return the right fragment for the given position
         */
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new AboutFragmentCreditsTab();

                case 1:
                    return new AboutFragmentContributingTab();

                case 2:
                    return new AboutFragmentLicenseTab();

                default:
                    return null;
            }
        }

        /**
         * generate title based on given position
         */
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.about_credits_tab_title);

                case 1:
                    return getString(R.string.about_contribution_tab_title);

                case 2:
                    return getString(R.string.about_license_tab_title);

                default:
                    return null;
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish(); // close this activity as oppose to navigating up
        return true;
    }
}