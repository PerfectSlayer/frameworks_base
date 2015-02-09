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

import android.net.TrafficStats;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.lang.Override;
import java.lang.String;
import java.util.Observable;
import java.util.Observer;

/**
 * Monitor traffic network.
 *
 * @author Bruce BUJON (bruce.bujon@gmail.com)
 */
public enum NetworkTrafficMonitor {
    /** The singleton instance. */
    INSTANCE;

    /** The network monitor log tag. */
    private static final String LOG_TAG = "NETWORK_MONITOR";
    /** The in network bandwith levels (in byte/s). */
    private static final long[] IN_NETWORK_LEVELS = new long[]{
            1l,
            5 * 1024l,
            25 * 1024l,
            125 * 1024l,
            250 * 1024l,
            450 * 1024l,
            750 * 1024l,
            1000 * 1024l
    };
    /** The traffic monitor status (<code>true</code> if monitored, <code>false</code> otherwise). */
    private boolean mTrafficMonitored;
    /** The traffic handler. */
    private final Handler mTrafficHandler;
    /** The traffic updater. */
    private final Runnable mTrafficUpdater;
    /** The observable to delegate change notification. */
    private final DelegateObservable mObservable;
    /** The traffic updater interval (in ms). */
    private long mTrafficUpdateInterval;
    /** The in network quality level. */
    private int mInNetworkLevel;
    /** The total received bytes counter (in bytes). */
    long mTotalRxBytes;
    /** The last update time (relative to boot, in ms). */
    long mLastUpdateTime;

    /**
     * Constructor.
     */
    private NetworkTrafficMonitor() {
        // Initialize traffic as not monitored
        mTrafficMonitored = false;
        // Create traffic handler
        mTrafficHandler = new Handler();
        // Create traffic updater
        mTrafficUpdater = new Runnable() {
            @Override
            public void run() {
                monitorTraffic();
                mTrafficHandler.postDelayed(mTrafficUpdater, mTrafficUpdateInterval);
            }
        };
        // Initialize observable
        mObservable = new DelegateObservable();
        // Initiliaze traffic updater interval
        mTrafficUpdateInterval = 2000;
        // Initialize in network quality level
        mInNetworkLevel = 0;
    }

    /**
     * Add an observer to be notify of network quality.
     *
     * @param observer The observer to be notify.
     */
    public void addObserver(Observer observer) {
        // Add the observer to observable
        mObservable.addObserver(observer);
        // Check if traffic is monitored
        if (!mTrafficMonitored) {
            // Start traffic monitoring
            startTrafficMonitor();
        }
    }

    /**
     * Remove an observer from notifiers
     * @param observer The observer to remove.
     */
    public void removeObserver(Observer observer) {
        // Remove the observer from observable
        mObservable.deleteObserver(observer);
        // Check if remains observers and traffic is monitored
        if (mTrafficMonitored && mObservable.countObservers() == 0) {
            // Stop traffic monitor
            stopTrafficMonitor();
        }
    }

    /**
     * Start the traffic monitor.
     */
    private void startTrafficMonitor() {
        // Prevent race condition
        synchronized (mTrafficHandler) {
            // Check if traffic is already monitored
            if (mTrafficMonitored) {
                return;
            }
            // Mark traffic as monitored
            mTrafficMonitored = true;
            Log.d(NetworkTrafficMonitor.LOG_TAG, "Start traffic monitor");
            // Start the updater
            mTrafficUpdater.run();
        }
    }

    /**
     * Stop the traffic monitor.
     */
    private void stopTrafficMonitor() {
        // Prevent race condition
        synchronized (mTrafficHandler) {
            // Check if traffic is monitored
            if (!mTrafficMonitored) {
                return;
            }
            // Stop traffic handler
            mTrafficHandler.removeCallbacks(mTrafficUpdater);
            // Mark traffic as not monitored
            mTrafficMonitored = false;
            Log.d(NetworkTrafficMonitor.LOG_TAG, "Stop traffic monitor");
        }
    }

    /**
     * Monitor traffic.<br/>
     * Compute network quality level based on data received.
     */
    protected void monitorTraffic() {
        // Get current time
        long lastUpdateTime = SystemClock.elapsedRealtime();
        // Compute update delay
        long updateDelay = (lastUpdateTime - mLastUpdateTime) / 1000;
        // Save last update time
        mLastUpdateTime = lastUpdateTime;
        Log.d(NetworkTrafficMonitor.LOG_TAG, "Delay: " + updateDelay + " s");
        if (updateDelay == 0) {
            return;
        }
        // Get total received bytes
        long totalRxBytes = TrafficStats.getTotalRxBytes();
        // Compute in network speed
        long speed = (totalRxBytes - mTotalRxBytes) / updateDelay;
        // Save total received bytes
        mTotalRxBytes = totalRxBytes;
        // Compute in network level
        int inNetworkLevel = 0;
        for (int i = 0, n = IN_NETWORK_LEVELS.length; i < n; i++) {
            if (speed < IN_NETWORK_LEVELS[i]) {
                break;
            }
            inNetworkLevel++;
        }
        Log.d(NetworkTrafficMonitor.LOG_TAG, "Speed: " + (speed / 1024) + " kb/s (level+" + inNetworkLevel + ")");
        // Check if in network quality level has changed
        if (inNetworkLevel != mInNetworkLevel) {
            // Save in network level quality
            mInNetworkLevel = inNetworkLevel;
            // Mark observable as changed
            mObservable.setChanged();
            // Notify observers
            mObservable.notifyObservers(inNetworkLevel);
            Log.d(NetworkTrafficMonitor.LOG_TAG, "Debug: " + mObservable.countObservers() + " observers");
        }
    }

    /**
     * Get the in network quality level.
     *
     * @return The in network quality level.
     */
    public int getInNetworkLevel() {
        return mInNetworkLevel;
    }

    /**
     * Delegate utility class.
     */
    public class DelegateObservable extends Observable {
        @Override
        public void setChanged() {
            super.setChanged();
        }
    }
}