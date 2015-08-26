/*
* Copyright (c) 2015, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*      notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*      with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*      contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.settings.wificall;

import android.content.BroadcastReceiver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.ims.ImsConfig;

public class WifiCallingStatusContral extends BroadcastReceiver {

    private static final String TAG = WifiCallingStatusContral.class.getSimpleName();
    private static final boolean DBUG = true;
    public static final String ACTION_WIFI_CALL_TURN_ON = "com.android.wificall.TURNON";
    public static final String ACTION_WIFI_CALL_TURN_OFF = "com.android.wificall.TURNOFF";
    public static final String ACTION_WIFI_CALL_ERROR_CODE = "com.android.wificall.ERRORCODE";
    private static Context mContext;
    private static boolean mIsAddlisten = false;
    private static boolean mWifiCallStatus = false;
    private static int mWifiCallPreference = -1;
    private static int mErrorCode = -1;
    private static NetworkInfo mWifiNetwork = null;
    private static PhoneStateListener mPhoneStateListener = new PhoneStateListener(){
        public void onCallStateChanged(int state, String incomingNumber) {
            if (isUsingWifiCall()) {
                WifiCallingNotification.updateWFCCallStateChange(mContext, state);
            }
        };
    };

    public void savePreference(int iPreference, boolean status) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                   "MY_PERFS", mContext.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("currentWifiCallingPrefernce", iPreference);
        editor.putBoolean("currentWifiCallingStatus", status );
        editor.commit();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!WifiCallingNotification.getWifiCallingNotifiEnable(context)) {
            if (DBUG) Log.i(TAG, "getIntent : " + intent.getAction() + " flag : false");
            return;
        }
        mContext = context;
        String action = intent.getAction();
        if (ACTION_WIFI_CALL_TURN_OFF.equals(action)
                || ACTION_WIFI_CALL_TURN_ON.equals(action)) {
            boolean turnOn = ACTION_WIFI_CALL_TURN_ON.equals(action);
            mWifiCallStatus = turnOn;
            int preference = intent.getIntExtra("preference",
                    ImsConfig.WifiCallingPreference.WIFI_PREFERRED);
            mWifiCallPreference = preference;
            savePreference(mWifiCallPreference,mWifiCallStatus);
            ConnectivityManager connect = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            mWifiNetwork = connect.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (preference ==
                    ImsConfig.WifiCallingPreference.CELLULAR_PREFERRED
                    || (mWifiNetwork == null || !mWifiNetwork.isConnected())) {
                turnOn = false;
            }
            WifiCallingNotification.getIntance().updateWFCStatusChange(mContext, turnOn);
        } else if (ACTION_WIFI_CALL_ERROR_CODE.equals(action)) {
            String error = intent.getStringExtra("result");
            if (DBUG) Log.i(TAG, "error : " + error);
            WifiCallingNotification.getIntance().updateRegistrationError(mContext, error);
        } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            SharedPreferences pre = context.getSharedPreferences(
                    WifiCallingWizardActivity.PRIVTE_PREFERENCE, Context.MODE_PRIVATE);
            boolean show = pre.getBoolean(WifiCallingWizardActivity.WIZARD_SHOW_PREFERENCE, true);
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
            if (DBUG)
                Log.i(TAG, "Intent action : " + action
                        + " WifiCallingWizardActivity : " + show
                        + " wifiState : " + wifiState);
            if(show && wifiState == WifiManager.WIFI_STATE_ENABLED){
                Intent start = new Intent(context, WifiCallingWizardActivity.class);
                start.setAction("android.intent.action.MAIN");
                start.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(start);
            }
        } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
            ConnectivityManager connect = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            mWifiNetwork = connect.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (mWifiCallPreference == -1) {
                SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                       "MY_PERFS", mContext.MODE_PRIVATE);
                mWifiCallPreference = sharedPreferences.getInt("currentWifiCallingPrefernce",
                        ImsConfig.WifiCallingPreference.WIFI_PREFERRED);
                mWifiCallStatus = sharedPreferences.getBoolean("currentWifiCallingStatus",
                        true);
            }
            boolean turnOn = mWifiCallStatus;
            if (mWifiCallPreference ==
                    ImsConfig.WifiCallingPreference.CELLULAR_PREFERRED
                    || (mWifiNetwork == null || !mWifiNetwork.isConnected())) {
                turnOn = false;
            }
            WifiCallingNotification.getIntance().updateWFCStatusChange(context,
                    turnOn);
            if (DBUG) {
                Log.i(TAG, "ConnectivityManager.CONNECTIVITY_ACTION info : " + mWifiNetwork
                        + " turnOn:" + turnOn);
            }
        }

        if (mWifiCallStatus && !mIsAddlisten) {
            TelephonyManager teleMana = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            mIsAddlisten = true;
            teleMana.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    private static boolean isUsingWifiCall() {
        if (!WifiCallingNotification.getWifiCallingNotifiEnable(mContext)) {
            return false;
        }
        ConnectivityManager connect = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiNetwork = connect.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (mWifiNetwork == null || !mWifiNetwork.isConnected()) {
            return false;
        }
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                "MY_PERFS", mContext.MODE_PRIVATE);
        mWifiCallPreference = sharedPreferences.getInt("currentWifiCallingPrefernce",
                ImsConfig.WifiCallingPreference.WIFI_PREFERRED);
        mWifiCallStatus = sharedPreferences.getBoolean("currentWifiCallingStatus", true);
        if (mWifiCallStatus == false || mWifiCallPreference ==
                ImsConfig.WifiCallingPreference.CELLULAR_PREFERRED) {
            return false;
        }
        return true;
    }
}