package net.eneiluj.moneybuster.util;

/**
 * Callback
 * Created by stefan on 01.10.15.
 */
public interface ICallback {
    void onFinish();

    void onFinish(String result, String message);

    void onScheduled();
}
