package com.android.systemui.statusbar.policy;

import java.text.DecimalFormat;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.google.android.collect.Sets;

/*
*
* Seeing how an Integer object in java requires at least 16 Bytes, it seemed awfully wasteful
* to only use it for a single boolean. 32-bits is plenty of room for what we need it to do.
*
*/
public class NetworkTextView extends TextView {
    private static final int KILOBIT = 1000;
    private static final int KILOBYTE = 1024;

    private static DecimalFormat decimalFormat = new DecimalFormat("##0.#");
    static {
        decimalFormat.setMaximumIntegerDigits(3);
        decimalFormat.setMaximumFractionDigits(1);
    }

    private int mState = 0;
    private boolean mAttached;
    private long totalRxBytes;
    private long totalTxBytes;
    private long lastUpdateTime;
    private int txtSizeSingle;
    private int txtSizeMulti;
    private int KB = KILOBIT;
    private int MB = KB * KB;
    private int GB = MB * KB;
    private boolean mAutoHide;
    private int mAutoHideThreshold;

    private Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long timeDelta = SystemClock.elapsedRealtime() - lastUpdateTime;

            if (timeDelta < getInterval(mState) * .95) {
                if (msg.what != 1) {
                    // we just updated the view, nothing further to do
                    return;
                }
                if (timeDelta < 1) {
                    // Can't div by 0 so make sure the value displayed is minimal
                    timeDelta = Long.MAX_VALUE;
                }
            }
            lastUpdateTime = SystemClock.elapsedRealtime();

            // Calculate the data rate from the change in total bytes and time
            long newTotalRxBytes = TrafficStats.getTotalRxBytes();
            long newTotalTxBytes = TrafficStats.getTotalTxBytes();
            long rxData = newTotalRxBytes - totalRxBytes;
            long txData = newTotalTxBytes - totalTxBytes;

            if (shouldHide(rxData, txData, timeDelta)) {
                setText("");
                setVisibility(View.GONE);
            } else {
                // If bit/s convert from Bytes to bits
                String symbol;
                if (KB == KILOBYTE) {
                    symbol = "B/s";
                } else {
                    symbol = "b/s";
                    rxData = rxData * 8;
                    txData = txData * 8;
                }

                // Get information for uplink ready so the line return can be added
                String output = "";
                if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.UP_TRAFFIC_MASK)) {
                    output = formatOutput(timeDelta, txData, symbol);
                }

                // Ensure text size is where it needs to be
                int textSize;
                if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.UP_TRAFFIC_MASK+NetworkTrafficMonitor.SettingsObserver.DOWN_TRAFFIC_MASK)) {
                    output += "\n";
                    textSize = txtSizeMulti;
                } else {
                    textSize = txtSizeSingle;
                }

                // Add information for downlink if it's called for
                if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.DOWN_TRAFFIC_MASK)) {
                    output += formatOutput(timeDelta, rxData, symbol);
                }

                // Update view if there's anything new to show
                if (!output.contentEquals(getText())) {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, (float)textSize);
                    setText(output);
                }
                setVisibility(View.VISIBLE);
            }

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            totalTxBytes = newTotalTxBytes;
            clearHandlerCallbacks();
            mTrafficHandler.postDelayed(mRunnable, getInterval(mState));
        }

        private String formatOutput(long timeDelta, long data, String symbol) {
            long speed = (long)(data / (timeDelta / 1000F));
            if (speed < KB) {
                return decimalFormat.format(speed) + symbol;
            } else if (speed < MB) {
                return decimalFormat.format(speed / (float)KB) + 'k' + symbol;
            } else if (speed < GB) {
                return decimalFormat.format(speed / (float)MB) + 'M' + symbol;
            }
            return decimalFormat.format(speed / (float)GB) + 'G' + symbol;
        }

        private boolean shouldHide(long rxData, long txData, long timeDelta) {
            if (!mAutoHide)
                return false;
            long speedTxKB = (long)(txData / (timeDelta / 1000f)) / KILOBYTE;
            long speedRxKB = (long)(rxData / (timeDelta / 1000f)) / KILOBYTE;
            if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.DOWN_TRAFFIC_MASK+NetworkTrafficMonitor.SettingsObserver.UP_TRAFFIC_MASK)) {
                return speedRxKB <= mAutoHideThreshold && speedTxKB <= mAutoHideThreshold;
            } else if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.DOWN_TRAFFIC_MASK)) {
                return speedRxKB <= mAutoHideThreshold;
            } else if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.UP_TRAFFIC_MASK)) {
                return speedTxKB <= mAutoHideThreshold;
            } else {
                return false;
            }
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    /*
     *  @hide
     */
    public NetworkTextView(Context context) {
        this(context, null);
    }

    /*
     *  @hide
     */
    public NetworkTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     *  @hide
     */
    public NetworkTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        txtSizeSingle = resources.getDimensionPixelSize(R.dimen.net_traffic_single_text_size);
        txtSizeMulti = resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);
        Handler mHandler = new Handler();
        NetworkTrafficMonitor.SettingsObserver settingsObserver = new NetworkTrafficMonitor.SettingsObserver(mContext,
                Sets.newHashSet(Settings.System.NETWORK_TRAFFIC_STATE, Settings.System.NETWORK_TRAFFIC_AUTOHIDE, Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD),
                new NetworkTrafficMonitor.SettingsChangeCallback() {
                    @Override
                    public void onSettingChanged() {
                        // Update enable status
                        updateSettings();
                    }
                });
        settingsObserver.register();    // TODO save to unregister
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                updateSettings();
            }
        }
    };

    private boolean getConnectAvailable() {
        ConnectivityManager connManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = (connManager != null) ? connManager.getActiveNetworkInfo() : null;
        return network != null && network.isConnected();
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mAutoHide = Settings.System.getIntForUser(resolver, Settings.System.NETWORK_TRAFFIC_AUTOHIDE, 0,
                UserHandle.USER_CURRENT) == 1;

        mAutoHideThreshold = Settings.System.getIntForUser(resolver, Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD,
                10, UserHandle.USER_CURRENT);

        mState = Settings.System.getInt(resolver, Settings.System.NETWORK_TRAFFIC_STATE, 0);
        if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.UNIT_SWITCH_MASK)) {
            KB = KILOBYTE;
        } else {
            KB = KILOBIT;
        }
        MB = KB * KB;
        GB = MB * KB;

        if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.UP_TRAFFIC_MASK) ||
                NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.DOWN_TRAFFIC_MASK)) {
            if (getConnectAvailable()) {
                if (mAttached) {
                    totalRxBytes = TrafficStats.getTotalRxBytes();
                    lastUpdateTime = SystemClock.elapsedRealtime();
                    mTrafficHandler.sendEmptyMessage(1);
                }
                setVisibility(View.VISIBLE);
                updateTrafficDrawable();
                return;
            }
        } else {
            clearHandlerCallbacks();
        }
        setVisibility(View.GONE);
    }

    private static int getInterval(int intState) {
        int intInterval = intState >>> 16;
        return (intInterval >= 250 && intInterval <= 32750) ? intInterval : 1000;
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeCallbacks(mRunnable);
        mTrafficHandler.removeMessages(0);
        mTrafficHandler.removeMessages(1);
    }

    private void updateTrafficDrawable() {
        int intTrafficDrawable;
        if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.UP_TRAFFIC_MASK+NetworkTrafficMonitor.SettingsObserver.DOWN_TRAFFIC_MASK)) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_updown;
        } else if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.UP_TRAFFIC_MASK)) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
        } else if (NetworkTrafficMonitor.SettingsObserver.hasMask(mState, NetworkTrafficMonitor.SettingsObserver.DOWN_TRAFFIC_MASK)) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
        } else {
            intTrafficDrawable = 0;
        }
        setCompoundDrawablesWithIntrinsicBounds(0, 0, intTrafficDrawable, 0);
    }
}