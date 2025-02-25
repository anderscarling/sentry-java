package io.sentry.samples.android;

import android.app.Application;
import android.os.StrictMode;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.fragment.FragmentLifecycleIntegration;

/** Apps. main Application. */
public class MyApplication extends Application {

  @Override
  public void onCreate() {
    strictMode();
    super.onCreate();

    // Example how to initialize the SDK manually which allows access to SentryOptions callbacks.
    // Make sure you disable the auto init via manifest meta-data: io.sentry.auto-init=false
    SentryAndroid.init(
        this,
        options -> {
          //          options.setBeforeSend(
          //              (event, hint) -> {
          //                event.setTag("sample-key", "before-send");
          //                return event;
          //              });
          //          options.setAnrTimeoutIntervalMillis(2000);
          options.addIntegration(new FragmentLifecycleIntegration(MyApplication.this));
        });
  }

  private void strictMode() {
    //    https://developer.android.com/reference/android/os/StrictMode
    //    StrictMode is a developer tool which detects things you might be doing by accident and
    //    brings them to your attention so you can fix them.
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
          new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());

      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
    }
  }
}
