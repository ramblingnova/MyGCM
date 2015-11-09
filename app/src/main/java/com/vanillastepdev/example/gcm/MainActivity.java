package com.vanillastepdev.example.gcm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends Activity {
    static final private String TAG = "GCM_Example";
    static final private String serverAddress = "http://192.168.201.107:3000";

    private Handler handler;
    private TextView registrationIDLabel;
    private TextView deviceIDLabel;
    private TextView deviceNameLabel;
    private RequestQueue mQueue;

    // 디바이스 아이디
    private String deviceID;
    // 토큰
    private String registrationID;
    // 기기명
    private String deviceName;
    private String deviceOS = "Android";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        registrationIDLabel = (TextView) findViewById(R.id.tokenLabel);
        mQueue = Volley.newRequestQueue(this);
        handler = new Handler();

        Button checkButton = (Button) findViewById(R.id.checkPlayServiceButton);
        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPlayService();
            }
        });

        Button deviceIDButton = (Button) findViewById(R.id.getDeviceIDButton);
        deviceIDLabel = (TextView) findViewById(R.id.deviceIdLabel);
        deviceIDButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resolveDeviceID();
                deviceIDLabel.setText(deviceID);
            }
        });

        Button deviceNameButton = (Button) findViewById(R.id.getDeviceNameButton);
        deviceNameLabel = (TextView) findViewById(R.id.deviceNameLabel);
        deviceNameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resolveDeviceName();
                deviceNameLabel.setText(deviceName);
            }
        });

        // 토큰 발급 버튼과 이벤트
        Button requestTokenButton = (Button) findViewById(R.id.requestDeviceTokenButton);
        requestTokenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // API19 에서 에러
//            requestDeviceToken();
                new RequestTokenThread().start();
            }
        });

        // 토큰 등록 버튼과 이벤트
        Button registTokenButton = (Button) findViewById(R.id.registTokenButton);
        registTokenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Register Device Token to App Server");
                registerToken();
            }
        });

        TextView addressView = (TextView) findViewById(R.id.serverAddress);
        addressView.setText("Server Address : " + serverAddress);

        showStoredToken();
    }

    // 디바이스 아이디 가져와 저장하는 함수
    private void resolveDeviceID() {
        String androidID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        deviceIDLabel.setText(androidID);
        Log.d(TAG, "Android ID : " + androidID);

        deviceID = androidID;
    }

    // 디바이스 기기명을 가져와 저장하는 함수
    private void resolveDeviceName() {
        deviceName = Build.DEVICE;
    }

    // 플레이 서비스 사용 가능 여부 체크
    void checkPlayService() {
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        Log.d(TAG, "isGooglePlayServicesAvailable : " + resultCode);
        if (ConnectionResult.SUCCESS == resultCode) {
            // 구글 플레이 서비스 가능
            Toast.makeText(this, "플레이 서비스 사용 가능", Toast.LENGTH_SHORT).show();
        } else {
            // 구글 플레이 서비스 불가능 - 설치/업데이트 다이얼로그 출력
            GoogleApiAvailability.getInstance().getErrorDialog(this, resultCode, 0).show();
        }
    }

    // Registration Intent Service를 이용해서 토큰 발급 받기
    void requestDeviceToken() {
        // 토큰 발급 브로드캐스트 리시버
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String token = intent.getExtras().getString("TOKEN");
                if (registrationID != token) {
                    registrationID = token;
                    saveRegistrationID();
                }

                registrationIDLabel.setText(token);
            }
        }, new IntentFilter(RegistrationIntentService.REGISTRATION_COMPLETE_BROADCAST));

        Log.d(TAG, "start registration service");

        // 토큰 발급 서비스 시작
        Intent intent = new Intent(this, RegistrationIntentService.class);
        startService(intent);
    }

    // 토큰 직접 발급받기 - IntentService로 작성하는 것을 권장
    class RequestTokenThread extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "Trying to regist device");
            try {
                InstanceID instanceID = InstanceID.getInstance(MainActivity.this);
                final String token = instanceID.getToken(getString(R.string.GCM_SenderId), GoogleCloudMessaging.INSTANCE_ID_SCOPE);
                if (registrationID != token) {
                    registrationID = token;
                    saveRegistrationID();
                }

                Log.d(TAG, "Token : " + token);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.registrationIDLabel.setText(token);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Regist Exception", e);
            }
        }
    }

    void showStoredToken() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        String storedToken = sharedPref.getString("REGISTRATION_ID", null);
        if (storedToken != null) {
            registrationID = storedToken;
            registrationIDLabel.setText(registrationID);
        }
    }

    void saveRegistrationID() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("REGISTRATION_ID", registrationID);
        editor.commit();
    }

    // call server url -> user id, message -> 메시지 전송할 때
    // call server url -> registrationID, deviceID, Build.DEVICE, user email -> 로그인 할 때...
    void registerToken() {
        // Device ID가 없으면 발급
        if (deviceID == null)
            resolveDeviceID();
        // Device Name이 없으면 발급
        if (deviceName == null)
            resolveDeviceName();

        StringRequest request = new StringRequest(Request.Method.POST, serverAddress + "/register", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(TAG, "Token 등록 결과  : " + response);
                Toast.makeText(MainActivity.this, "Token 등록 성공", Toast.LENGTH_SHORT).show();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error", error);
                NetworkResponse response = error.networkResponse;
                if (response != null) {
                    Log.e(TAG, "Error Response : " + response.statusCode);
                    Toast.makeText(MainActivity.this, "Token 등록 에러 " + response.toString(), Toast.LENGTH_SHORT).show();
                }
            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                // 바디 작성
                Map<String, String> params = new HashMap<>();
                params.put("deviceID", deviceID);
                params.put("token", registrationID);
                params.put("os", deviceOS);

                return params;
            }

            @Override
            public String getBodyContentType() {
                // 컨텐트 타입
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }
        };
        mQueue.add(request);


    }
}
