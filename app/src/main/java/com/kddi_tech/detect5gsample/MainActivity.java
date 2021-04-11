package com.kddi_tech.detect5gsample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    // ログ用タグ
    private final String LOG_TAG = MainActivity.class.getSimpleName();

    // TelephonyManagerインスタンス
    private TelephonyManager mBaseTelephonyManager;
    // TelephonyManagerインスタンス(データ通信SIM用)
    private TelephonyManager mDataTelephonyManager;

    // 最新のOverrideNetworkTypeの値
    private String mCurrentOverrideNetworkType = "NONE";

    // 最新のSubIdの値
    private int mCurrentSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    // Capabilityフラグ関係
    private boolean mHasTemporarilyNotMetered = false;
    private boolean mHasNotMetered = false;

    // BandWidth関係
    private int mBandWidthDown = 0;
    private int mBandWidthUp = 0;

    // View関係
    private TextView mTvOverrideNetworkType;
    private TextView mTvTemporarilyNotMetered;
    private TextView mTvNotMetered;
    private TextView mTvBandWidthDown;
    private TextView mTvBandWidthUp;
    private TextView mTvNeedPermission;

    // PhoneStateListener(ベース)
    // データ通信用subIdの変化を検出し、データ通信SIM用TelephonyManagerの再生成を行う
    private final PhoneStateListener mBasePhoneStateListener = new PhoneStateListener() {
        @Override
        public void onActiveDataSubscriptionIdChanged(int subId) {
            Log.d(LOG_TAG, "onActiveDataSubscriptionIdChanged: subId=" + subId);
            if (mCurrentSubId == subId) {
                // データ通信用subIdに変化がない場合は処理を行わない
                return;
            }
            mCurrentSubId = subId;

            // データ通信用subIdに変化があった場合、データ通信SIM用TelephonyManagerの再生成とPhoneStateListenerの監視やり直し
            stopListenPhoneStateForData();
            createTelephonyManagerForData(subId);
            startListenPhoneStateForData();

            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                // データ通信できない状態の場合、最新のOverrideNetworkTypeの値をリセット
                mCurrentOverrideNetworkType = "NONE";
            }

            updateUI();
        }
    };

    // PhoneStateListener(データ通信SIM用)
    // DisplayInfoの変化を監視する
    private final PhoneStateListener mDataPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo telephonyDisplayInfo) {
            String networkType;
            switch (telephonyDisplayInfo.getOverrideNetworkType()) {
                case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE:
                    networkType = "NONE";
                    break;
                case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA:
                    networkType = "LTE-CA";
                    break;
                case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO:
                    networkType = "LTE-ADV-PRO";
                    break;
                case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA:
                    networkType = "NR-NSA";
                    break;
                case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE:
                    networkType = "NR-NSA-MMWAVE";
                    break;
                default:
                    networkType = "-";
                    break;
            }
            Log.d(LOG_TAG, "onDisplayInfoChanged: overrideNetworkType=" + networkType);
            mCurrentOverrideNetworkType = networkType;

            updateUI();
        }
    };

    // NetworkCallback
    private final ConnectivityManager.NetworkCallback mNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(@NonNull Network network, NetworkCapabilities networkCapabilities) {
            Log.d(LOG_TAG, "onCapabilitiesChanged network=" + network + ", capa=" + networkCapabilities);
            // モバイル通信関係だけ拾いたいので念のため判定
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                mHasNotMetered = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
                mHasTemporarilyNotMetered = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED);

                mBandWidthDown = networkCapabilities.getLinkDownstreamBandwidthKbps();
                mBandWidthUp = networkCapabilities.getLinkUpstreamBandwidthKbps();
            }

            updateUI();
        }

    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");
        setContentView(R.layout.activity_main);

        mBaseTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume");

        // 監視開始
        startListening();

        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause");

        // 監視終了
        stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        // 監視終了
        stopListening();
    }

    // UI初期化
    private void initUI(){
        mTvOverrideNetworkType = findViewById(R.id.override_network_type);
        mTvTemporarilyNotMetered = findViewById(R.id.temporarily_not_metered);
        mTvNotMetered = findViewById(R.id.not_metered);
        mTvBandWidthDown = findViewById(R.id.band_width_down);
        mTvBandWidthUp = findViewById(R.id.band_width_up);
        mTvNeedPermission = findViewById(R.id.need_permission);

        updateUI();
    }

    // UI更新
    private void updateUI(){
        Log.d(LOG_TAG, "updateUI");
        mTvOverrideNetworkType.setText(mCurrentOverrideNetworkType);
        mTvTemporarilyNotMetered.setText(String.valueOf(mHasTemporarilyNotMetered));
        mTvNotMetered.setText(String.valueOf(mHasNotMetered));
        mTvBandWidthDown.setText(String.valueOf(mBandWidthDown));
        mTvBandWidthUp.setText(String.valueOf(mBandWidthUp));

        if (isPermissionGranted()) {
            mTvNeedPermission.setVisibility(View.GONE);
        } else {
            mTvNeedPermission.setVisibility(View.VISIBLE);
        }
    }

    // 監視開始
    private void startListening() {

        if (!isPermissionGranted()) {
            return;
        }

        // データ通信用SIMのTelephonyManagerインスタンスを取得
        // (Pause中にユーザがデータ通信用SIMを変更した可能性があるため)
        createTelephonyManagerForData(SubscriptionManager.getDefaultDataSubscriptionId());

        // PhoneStateListenerの監視開始
        mBaseTelephonyManager.listen(mBasePhoneStateListener, PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE);
        startListenPhoneStateForData();

        // NetworkCallbackの監視開始
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
            connectivityManager.registerNetworkCallback(builder.build(), mNetworkCallback);
        }
    }

    // 監視停止
    private void stopListening() {

        if (!isPermissionGranted()) {
            return;
        }

        // PhoneStateListenerの監視停止
        mBaseTelephonyManager.listen(mBasePhoneStateListener, PhoneStateListener.LISTEN_NONE);
        stopListenPhoneStateForData();

        // NetworkCallbackの監視停止
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(mNetworkCallback);
            } catch (Exception e) {
                Log.d(LOG_TAG, "Callback is not registered");
            }
        }
    }

    // データ通信SIM用TelephonyManagerインスタンス生成
    private void createTelephonyManagerForData(int subId) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mDataTelephonyManager = null;
        } else {
            mDataTelephonyManager = mBaseTelephonyManager.createForSubscriptionId(subId);
        }
    }

    // データ通信SIM用PhoneStateListenerの監視開始
    private void startListenPhoneStateForData() {
        if (mDataTelephonyManager == null) {
            return;
        }
        mDataTelephonyManager.listen(mDataPhoneStateListener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED);
    }

    // データ通信SIM用PhoneStateListenerの監視停止
    private void stopListenPhoneStateForData() {
        if (mDataTelephonyManager == null) {
            return;
        }
        mDataTelephonyManager.listen(mDataPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    // Permission取得済みかどうか
    // ※サンプルアプリがクラッシュしないように暫定で入れてあるが、本来はRuntimePermissionの取得処理が必要
    private boolean isPermissionGranted() {
        return this.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }
}