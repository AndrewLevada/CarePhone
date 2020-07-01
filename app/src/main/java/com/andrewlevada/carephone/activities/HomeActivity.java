package com.andrewlevada.carephone.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.andrewlevada.carephone.R;
import com.andrewlevada.carephone.Toolbox;
import com.andrewlevada.carephone.activities.extra.CloudActivity;
import com.andrewlevada.carephone.logic.WhitelistAccesser;
import com.andrewlevada.carephone.logic.blockers.Blocker;
import com.andrewlevada.carephone.logic.blockers.TestService;
import com.andrewlevada.carephone.logic.network.Network;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends CloudActivity {
    public static final String INTENT_REMOTE = "INTENT_REMOTE";

    private int currentHomeFragmentId;
    boolean isRemote;

    private FloatingActionButton fabView;
    public boolean doCloseLinkOnCloudCollapse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        layoutId = R.layout.activity_home;
        layoutCloudId = R.layout.activity_home_cloud;
        super.onCreate(savedInstanceState);

        // Check auth
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(HomeActivity.this, HelloActivity.class));
            finish();
            return;
        }

        // Analytics
        String userUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseCrashlytics.getInstance().setUserId(userUid);
        FirebaseAnalytics.getInstance(this).setUserId(userUid);

        // Firebase Remote Config
        FirebaseRemoteConfigSettings remoteConfigSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(30 * 60)
                .build();
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setConfigSettingsAsync(remoteConfigSettings);
        remoteConfig.setDefaultsAsync(R.xml.remote_config_default);

        // Get remote option from intent
        isRemote = getIntent().getBooleanExtra(INTENT_REMOTE, false);

        // Find views by ids
        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        fabView = findViewById(R.id.fab);

        // Loading default fragment screen
        loadHomeFragment(new WhitelistFragment(this), R.id.home_nav_list);
        navigation.setSelectedItemId(R.id.home_nav_list);

        // Process bottom navigation buttons clicks
        final HomeActivity itself = this;
        navigation.setOnNavigationItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();

            if (currentHomeFragmentId == itemId) return false;

            switch (itemId) {
                case R.id.home_nav_log:
                    fragment = new LogFragment(itself);
                    break;

                case R.id.home_nav_list:
                    fragment = new WhitelistFragment(itself);
                    break;

                case R.id.home_nav_stats:
                    fragment = new StatisticsFragment(itself);
                    break;
            }

            return loadHomeFragment(fragment, itemId);
        });

        WhitelistAccesser.getInstance().initialize(getApplicationContext(), isRemote);

        if (isRemote) return;

        // Back button processing
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = new Intent(HomeActivity.this, HelloActivity.class);
                intent.putExtra(HelloActivity.INTENT_EXTRA_STAY, true);
                startActivity(intent);
                finish();
            }
        });

        // Load whitelist blocker
        if (!checkPermissions()) return;
        tryToLaunchBlocker();
    }

    public void launchService() { //How to launch the service, depending the phone's API.
        if(Build.VERSION.SDK_INT >= 26) {
            startForegroundService(new Intent(this, TestService.class));
        }
        else{
            Intent i;
            i = new Intent(this, TestService.class);
            ContextCompat.startForegroundService(this, i);
        }
    }

    private boolean loadHomeFragment(Fragment fragment, int id) {
        if (fragment == null) return false;

        // Remember switching fragment
        currentHomeFragmentId = id;

        // Hide fab. If fragment needs it, it can request it
        fabView.hide();

        // Make transition between fragments
        FragmentTransaction transition = getSupportFragmentManager().beginTransaction();
        transition.setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out);
        transition.replace(R.id.fragment_container, fragment);
        transition.commit();

        return true;
    }

    @Override
    protected void onDestroy() {
        Toolbox.fastLog("home onDestroy()");
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        Toolbox.fastLog("home onStop()");
        // launchService();
        super.onStop();
    }

    @SuppressLint("InlinedApi")
    private boolean checkPermissions() {
        int sdk = Build.VERSION.SDK_INT;

        List<String> requestedPermissions = new ArrayList<>();

        if (sdk >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_DENIED
                    || checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_DENIED) {
                requestedPermissions.add(Manifest.permission.READ_PHONE_STATE);
                requestedPermissions.add(Manifest.permission.CALL_PHONE);
            }
        }

        if (sdk >= Build.VERSION_CODES.O) {
            if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_DENIED
                    || checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_DENIED) {
                requestedPermissions.add(Manifest.permission.ANSWER_PHONE_CALLS);
                requestedPermissions.add(Manifest.permission.READ_PHONE_NUMBERS);
            }
        }

        if (requestedPermissions.size() != 0) {
            requestPermissions(requestedPermissions.toArray(new String[0]), 0);
            return false;
        }

        if (sdk == Build.VERSION_CODES.O || sdk == Build.VERSION_CODES.O_MR1) {
            String notificationListenerString = Settings.Secure.getString(this.getContentResolver(),"enabled_notification_listeners");
            if (notificationListenerString == null || !notificationListenerString.contains(getPackageName())) {
                showBeforePermissionDialog(R.string.permission_dialog_notification_listener, () ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
                return false;
            }
        }

        return true;
    }

    public void requestFAB(@Nullable View.OnClickListener onClickListener) {
        fabView.show();
        fabView.setOnClickListener(onClickListener);
    }

    public void hideFAB() {
        fabView.hide();
    }

    @Override
    public void updateCloud(boolean extend) {
        super.updateCloud(extend);

        if (!extend && doCloseLinkOnCloudCollapse && !isRemote) {
            Network.cared().removeLinkRequest(null);
            doCloseLinkOnCloudCollapse = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                showBeforePermissionDialog(R.string.permission_dialog_must_accept, this::checkPermissions);
                break;
            }
        }

        if (!checkPermissions()) return;
        tryToLaunchBlocker();
    }

    private void tryToLaunchBlocker() {
        if (!Blocker.enable(getApplicationContext())) {
            FirebaseCrashlytics.getInstance().setCustomKey("blocker_type", "none");
            // TODO: Process unsupported device
        }
    }

    private void showBeforePermissionDialog(@StringRes int messageRes, Toolbox.Callback onClick) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_dialog_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.general_okay, (dialog, which) -> onClick.invoke())
                .show();
    }
}