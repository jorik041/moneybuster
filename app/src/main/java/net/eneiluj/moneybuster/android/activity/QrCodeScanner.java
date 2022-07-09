package net.eneiluj.moneybuster.android.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.google.zxing.Result;

import net.eneiluj.moneybuster.R;

public class QrCodeScanner extends AppCompatActivity {
    private CodeScanner mCodeScanner;

    private static final String TAG = QrCodeScanner.class.getSimpleName();

    public static final String KEY_QR_CODE = "net.eneiluj.moneybuster.android.activity.key_qr_code";

    private final static int PERMISSION_CAMERA = 2;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.CAMERA }, PERMISSION_CAMERA);

        setContentView(R.layout.activity_code_scanner);
        CodeScannerView scannerView = findViewById(R.id.scanner_view);
        mCodeScanner = new CodeScanner(this, scannerView);
        mCodeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleResult(result);
                    }
                });
            }
        });
        scannerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCodeScanner.startPreview();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mCodeScanner.startPreview();
        if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[Scanner onResume]"); }
    }

    @Override
    public void onPause() {
        mCodeScanner.releaseResources();
        if (BillsListViewActivity.DEBUG) { Log.d(TAG, "[Scanner onPause]"); }
        super.onPause();
    }

    public void handleResult(Result rawResult) {
        // Do something with the result here
        // Prints scan results
        Log.v(TAG, "QR result " + rawResult.getText());
        // Prints the scan format (qrcode, pdf417 etc.)
        Log.v(TAG, "QRresult" + rawResult.getBarcodeFormat().toString());
        //If you would like to resume scanning, call this method below:
        //mScannerView.resumeCameraPreview(this);

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Vibrate for 300 milliseconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            v.vibrate(300);
        }

        Intent intent = new Intent();
        intent.putExtra(KEY_QR_CODE, rawResult.getText());
        setResult(RESULT_OK, intent);
        finish();
    }
}
