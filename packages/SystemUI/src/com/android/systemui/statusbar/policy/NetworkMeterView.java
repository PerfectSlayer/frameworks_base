/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;

import java.lang.*;
import java.lang.Object;
import java.lang.Override;
import java.lang.Runnable;
import java.lang.Thread;
import java.util.Observable;
import java.util.Observer;

/**
 * Display a network meter.
 *
 * @author Bruce BUJON (bruce.bujon@gmail.com)
 */
public class NetworkMeterView extends ImageView implements Observer {
    /** The network meter log tag. */
    private static final String LOG_TAG = "NETWORK_METER";
    /*
     * View related.
     */
    /** The out network drawable layer id. */
    protected static final int OUT_NETWORK_DRAWABLE_LAYER_ID = 1;
    /** The in network drawable layer id. */
    protected static final int IN_NETWORK_DRAWABLE_LAYER_ID = 2;
    /** The network in drawables cache. */
    protected final Drawable[] mNetworkInDrawables;
    /** The view attached status (<code>true</code> if attached, <code>false</code> otherwise). */
    protected boolean mAttached;
    /** The layer drawable icon. */
    //protected final LayerDrawable mLayerDrawable;
    /*
     * Settings related.
     */
    /** The intent receiver for connectivity action. */
    protected final BroadcastReceiver mIntentReceiver;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public NetworkMeterView(Context context) {
        this(context, null);
    }

    /**
     * Constructor.
     *
     * @param context The application context.
     * @param attrs   The view attribute set.
     */
    public NetworkMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Constructor.
     *
     * @param context      The application context.
     * @param attrs        The view attribute set
     * @param defStyleAttr The view default style attributes.
     */
    public NetworkMeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        /*
         * Initialize view.
         */
        // Initialive view as detached
        mAttached = false;
        // Create drawables caches
        Resources resources = getResources();
        mNetworkInDrawables = new Drawable[]{    // TODO Test drawable cache
                resources.getDrawable(R.drawable.stat_sys_network_in_0),
                resources.getDrawable(R.drawable.stat_sys_network_in_1),
                resources.getDrawable(R.drawable.stat_sys_network_in_2),
                resources.getDrawable(R.drawable.stat_sys_network_in_3),
                resources.getDrawable(R.drawable.stat_sys_network_in_4),
                resources.getDrawable(R.drawable.stat_sys_network_in_5),
                resources.getDrawable(R.drawable.stat_sys_network_in_6),
                resources.getDrawable(R.drawable.stat_sys_network_in_7),
                resources.getDrawable(R.drawable.stat_sys_network_in_8)
        };
        // Initialize image view drawable
        setImageDrawable(mNetworkInDrawables[0]);
        //Drawable inNetworkDrawable = resources.getDrawable(R.drawable.stat_sys_network_out_0);    // TODO
        // Create layer drawable
        //Drawable[] networkDrawables = new Drawable[] {outNetworkDrawable};  // TODO inNetworkDrawable
        //mLayerDrawable = new LayerDrawable(networkDrawables);
        // Set drawable layer ids
        //mLayerDrawable.setId(0, OUT_NETWORK_DRAWABLE_LAYER_ID);
        // mLayerDrawable.setId(1, IN_NETWORK_DRAWABLE_LAYER_ID);   // TODO
        // Apply layer drawable
        // setImageDrawable(mLayerDrawable);    // TODO
        /*
         * Initialize setting monitoring.
         */
        // Create setting observer
        SettingsObserver settingsObserver = new SettingsObserver(Settings.System.STATUS_BAR_TRAFFIC,
                new SettingsChangeCallback() {
                    @Override
                    public void onSettingChanged() {
                        // Update enable status
                        updateEnableSettings();
                    }
                }); // TODO unreg callback on detach
        // Create intent receiver
        mIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Check intent action
                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                    // Update enable status
                    updateEnableSettings();
                }
            }
        };
        // Force setting loading
        updateEnableSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        // Delegate view attachment
        super.onAttachedToWindow();
        // Check if view already attached
        if (mAttached) {
            return;
        }
        // Mark view as attached
        mAttached = true;
        // Create intent filter for connectivity action
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        // Register intent receiver
        mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        // Force update enable settings
        updateEnableSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        // Delegate view detachment
        super.onDetachedFromWindow();
        // Check if view is attached
        if (!mAttached) {
            return;
        }
        // Unregister intent receiver
        mContext.unregisterReceiver(mIntentReceiver);
        // Remove as traffic observer
        NetworkTrafficMonitor.INSTANCE.removeObserver(this);
        // Mark view as detached
        mAttached = false;
    }

    /**
     * Update enable settings.<br/>
     * Display or hide network meter and register or not as traffic monitor listener.
     */
    protected void updateEnableSettings() {
        // Check statusbar network meter setting
        ContentResolver resolver = mContext.getContentResolver();
        boolean showMeter = Settings.System.getInt(resolver, Settings.System.STATUS_BAR_TRAFFIC, 0) == 1;
        // Check if meter should be showed and device is connected
        if (showMeter && isConnected()) {
            // Ensurve view is attached
            if (mAttached) {
                // Add as traffic observer
                NetworkTrafficMonitor.INSTANCE.addObserver(this);
            }
            // Set meter visibility as visible
            setVisibility(View.VISIBLE);
        } else {
            // Set meter visibility as gone
            setVisibility(View.GONE);
            // Remove as traffic observer
            NetworkTrafficMonitor.INSTANCE.removeObserver(this);
        }
    }

    /**
     * Check if device is connected.
     *
     * @return <code>true</code> if device is connected, <code>false</code> otherwise.
     */
    protected boolean isConnected() {
        // Get connectivity manager
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        // Get active network
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            return false;
        }
        // Check if active network is connected
        return networkInfo.isConnected();
    }

    /*
     * Observer.
     */

    long lastUpdateTime;    // TODO remove

    @Override
    public void update(Observable observable, Object data) {
        // Check data
        if (!(data instanceof Integer)) {
            return;
        }
        // Get in network level
        int inNetworkLevel = (int) data;

        long tempUpdateTime = SystemClock.elapsedRealtime();
        long delay = (tempUpdateTime - lastUpdateTime) / 1000;
        Log.d(NetworkMeterView.LOG_TAG, "Delay: " + delay + " s");
        if (delay == 0) {
            // we just updated the view, nothing further to do
            return;
        }
        Log.d(NetworkMeterView.LOG_TAG, "Debug: " + this.hashCode() + " " + isShown());

        // Check if view is shown
        if (!isShown()) {
            return;
        }
        // Update image drawable
        setImageDrawable(mNetworkInDrawables[inNetworkLevel]);
    }

    /**
     * Observe a setting change.
     *
     * @author Bruce BUJON (bruce.bujon@gmail.com)
     */
    private class SettingsObserver extends ContentObserver {
        /** The callback to call on setting change. */
        private final SettingsChangeCallback mCallback;

        /**
         * Constructor.
         *
         * @param settingName The setting name to observe.
         * @param callback    The callback to notify on setting change.
         */
        public SettingsObserver(String settingName, SettingsChangeCallback callback) {
            super(null);
            // Get content resolver
            ContentResolver resolver = mContext.getContentResolver();
            // Register observer for desired setting
            resolver.registerContentObserver(Settings.System.getUriFor(settingName), false, this);
            // Save callback
            mCallback = callback;
        }

        @Override
        public void onChange(boolean selfChange) {
            // Notify callback
            mCallback.onSettingChanged();
        }
    }

    /**
     * Callback for setting change.
     *
     * @author Bruce BUJON (bruce.bujon@gmail.com)
     */
    private interface SettingsChangeCallback {
        public void onSettingChanged();
    }
}