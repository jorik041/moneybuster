package net.eneiluj.nextcloud.phonetrack.android.quicksettings;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import net.eneiluj.nextcloud.phonetrack.android.activity.EditPhoneTrackLogjobActivity;

/**
 * This {@link TileService} adds a quick settings tile that leads to the new logjob view.
 */
@TargetApi(Build.VERSION_CODES.N)
public class NewLogjobTileService extends TileService {

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        tile.setState(Tile.STATE_ACTIVE);

        tile.updateTile();
    }

    @Override
    public void onClick() {
        // create new logjob intent
        final Intent newLogjobIntent = new Intent(getApplicationContext(), EditPhoneTrackLogjobActivity.class);
        // ensure it won't open twice if already running
        newLogjobIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // ask to unlock the screen if locked, then start new logjob intent
        unlockAndRun(new Runnable() {
            @Override
            public void run() {
                startActivityAndCollapse(newLogjobIntent);
            }
        });

    }
}
