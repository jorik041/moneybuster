package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by stefan on 18.04.17.
 */
public class SplashscreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, LogjobsListViewActivity.class);
        startActivity(intent);
        finish();
    }
}