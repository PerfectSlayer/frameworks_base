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
import android.graphics.drawable.LayerDrawable;
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

import java.lang.Override;
import java.lang.Runnable;

/**
 * Display a network meter.
 *
 * @author Bruce BUJON (bruce.bujon@gmail.com)
 */
public class NetworkMeterView extends ImageView {
    /*
     * View related.
     */
    /** The out network drawable layer id. */
    protected static final int OUT_NETWORK_DRAWABLE_LAYER_ID = 1;
    /** The in network drawable layer id. */
    protected static final int IN_NETWORK_DRAWABLE_LAYER_ID = 2;
    /** The network out drawables cache. */
    protected final Drawable[] mNetworkOutDrawables;
    /** The view attached status (<code>true</code> if attached, <code>false</code> otherwise). */
    protected boolean mAttached;
    /** The layer drawable icon. */
    //protected final LayerDrawable mLayerDrawable;
    /*
     * Traffic related.
     */
    /** The traffic monitor status (<code>true</code> if monitored, <code>false</code> otherwise). */
    protected boolean mTrafficMonitored;
    /** The traffic handler. */
    protected final Handler mTrafficHandler;
    /** The traffic updater. */
    protected final Runnable mTrafficUpdater;
    /** The traffic updater interval (in ms). */
    protected long mTrafficUpdateInterval;
    /*
     * Settings related.
     */
    /** The intent receiver for connectivity action. */
    protected final BroadcastReceiver mIntentReceiver;


    // TODO Improve
    long totalRxBytes;
    long lastUpdateTime;

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
        mNetworkOutDrawables = new Drawable[] {
                resources.getDrawable(R.drawable.stat_sys_network_out_0),
                resources.getDrawable(R.drawable.stat_sys_network_out_1),
                resources.getDrawable(R.drawable.stat_sys_network_out_2),
                resources.getDrawable(R.drawable.stat_sys_network_out_3),
                resources.getDrawable(R.drawable.stat_sys_network_out_4)
        };
        setImageDrawable(mNetworkOutDrawables[0]);
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
         * Initialize traffic monitoring.
         */
        // Initialize traffic as not monitored
        mTrafficMonitored = false;
        // Create traffic handler
        mTrafficHandler = new Handler();
        // Create traffic updater
        mTrafficUpdater = new Runnable() {
            @Override
            public void run() {
                updateMeter();
                mTrafficHandler.removeCallbacks(mTrafficUpdater);
                mTrafficHandler.postDelayed(mTrafficUpdater, mTrafficUpdateInterval);
            }
        };
        // Initiliaze traffic updater interval
        mTrafficUpdateInterval = 2000;
        /*
         * Initialize setting monitoring.
         */
        // Create setting observer
        SettingsObserver settingsObserver = new SettingsObserver(new Handler(), Settings.System.STATUS_BAR_TRAFFIC,
                new SettingsChangeCallback() {
                    @Override
                    public void onSettingChanged() {
                        // Update enable status
                        updateEnableSettings();
                    }
                });
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
        if (!mAttached) {
            // Mark view as attached
            mAttached = true;
            // Create intent filter for connectivity action
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            // Register intent receiver
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
        // Force update enable settings
        updateEnableSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        // Delegate view detachment
        super.onDetachedFromWindow();
        // Check if view is attached
        if (mAttached) {
            // Unregister intent receiver
            mContext.unregisterReceiver(mIntentReceiver);
            // Mark view as detached
            mAttached = false;
        }
    }

    int[] drawablesId = new int[] {
            R.drawable.stat_sys_network_out_0,
            R.drawable.stat_sys_network_out_1,
            R.drawable.stat_sys_network_out_2,
            R.drawable.stat_sys_network_out_3,
            R.drawable.stat_sys_network_out_4,
    };

    /**
     * Update meter diplay.<br/>
     * Compute network quality based on traffic then update meter display.
     */
    protected void updateMeter() {

        long tempUpdateTime = SystemClock.elapsedRealtime();
        long delay = (tempUpdateTime - lastUpdateTime) / 1000;
        Log.d("NETWORK METER", "Delai: "+delay+" s");
        if (delay == 0) {
            // we just updated the view, nothing further to do
            return;
        }

        long tempRxBytes = TrafficStats.getTotalRxBytes();
        long speed = (tempRxBytes - totalRxBytes) / delay;

        speed /= 1024;
        Log.d("NETWORK METER", "Speed: "+speed+" kb/s");
        speed /= 100;
        if (speed<0)
            speed = 0;
        else if (speed >= drawablesId.length)
            speed = drawablesId.length-1;


        totalRxBytes = tempRxBytes;
        lastUpdateTime = tempUpdateTime;

        Drawable outNetworkDrawable = mNetworkOutDrawables[(int)speed];
        setImageDrawable(outNetworkDrawable);

        /*

        mLayerDrawable.setDrawableByLayerId(OUT_NETWORK_DRAWABLE_LAYER_ID,
                getResources().getDrawable(drawablesId[(int)speed]));
        setImageDrawable(mLayerDrawable);

        */

        /* if (((float) speed) / 1048576 >= 1) { // 1024 * 1024
            setText(decimalFormat.format(((float) speed) / 1048576f) + "MB/s");
        } else if (((float) speed) / 1024f >= 1) {
            setText(decimalFormat.format(((float) speed) / 1024f) + "KB/s");
        } else if (speed > 0) {
            setText(speed + "B/s");
        } else {
            setText("");
        }*/
    }

    /**
     * Update enable settings.<br/>
     * Display or hide network meter and start or stop traffic montoring.
     */
    protected void updateEnableSettings() {
        // Check statusbar network meter setting
        ContentResolver resolver = mContext.getContentResolver();
        boolean showMeter = Settings.System.getInt(resolver, Settings.System.STATUS_BAR_TRAFFIC, 0) == 1;
        // Check if meter should be showed and device is connected
        if (showMeter && isConnected()) {
            // Ensurve view is attached
            if (mAttached) {
                // Start traffic monitor
                startTrafficMonitor();
            }
            // Set meter visibility as visible
            setVisibility(View.VISIBLE);
        } else {
            // Set meter visibility as gone
            setVisibility(View.GONE);
            // Stop traffic monitor
            stopTrafficMonitor();
        }
    }

    /**
     * Start the traffic monitor.
     */
    protected void startTrafficMonitor() {
        // Check if traffic is already monitored
        if (mTrafficMonitored) {
            return;
        }
        Log.d("NETWORK METER", "Start traffic monitor");
        // Start the updater
        mTrafficUpdater.run();
    }

    /**
     * Stop the traffic monitor.
     */
    protected void stopTrafficMonitor() {
        // Check if traffic is monitored
        if (!mTrafficMonitored) {
            return;
        }
        // Stop traffic handler
        mTrafficHandler.removeCallbacks(mTrafficUpdater);
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

    /**
     * Observe a setting change.
     *
     * @author Bruce BUJON (bruce.bujon@gmail.com)
     */
    private class SettingsObserver extends ContentObserver {
        /** The callback to call on setting change. */
        private final SettingsChangeCallback mCallback;

        public SettingsObserver(Handler handler, String settingName, SettingsChangeCallback callback) {
            super(handler);
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