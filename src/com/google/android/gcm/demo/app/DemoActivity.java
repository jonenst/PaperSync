/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gcm.demo.app;

import java.io.IOException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * Main UI for the demo app.
 */
public class DemoActivity extends Activity {

    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_BACKEND_ACK = "backendAck";
    private static final String PROPERTY_BACKEND_ACK_USER = "backendAckUser";
    private static final String PROPERTY_USERNAME = "username";
    private static final String PROPERTY_PASSWORD = "password";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    /**
     * Substitute you own sender ID here. This is the project number you got
     * from the API Console, as described in "Getting Started."
     */
    String SENDER_ID = "214625485032";

    /**
     * Tag used on log messages.
     */
    static final String TAG = "jon";

    GoogleCloudMessaging gcm;
    Context context;

    String regid;

    void handleSendText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {
            Log.v(TAG, "Got text: " + sharedText);
            SendImageInBackground(sharedText);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        setContentView(R.layout.main);

        context = getApplicationContext();

        final Button register = (Button) findViewById(R.id.register_button);
        final EditText username = (EditText) findViewById(R.id.username_field);
        final EditText password = (EditText) findViewById(R.id.password_field);
        final EditText verifypassword = (EditText) findViewById(R.id.password_repeat_field);
        register.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                RegisterInBackground(username.getText().toString(),
                        password.getText().toString(),
                        verifypassword.getText().toString());
            }
        });
        final Button check = (Button) findViewById(R.id.check_button);
        check.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckInBackground(username.getText().toString(),
                        password.getText().toString());
            }
        });
        final EditText pair_username = (EditText) findViewById(R.id.pair_field);
        final Button pair = (Button) findViewById(R.id.pair_button);
        pair.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PairInBackground(pair_username.getText().toString());
            }
        });

        setCurrentUser(getUser());

        // Check device for Play Services APK. If check succeeds, proceed with GCM registration.
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(intent); // Handle text being sent
            }
        } else if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);
            Log.v(TAG, "Got regid" + regid);

            if (regid.isEmpty()) {
                registerInBackground();
            } else {
                ensureBackendAckinBackground(this);
            }
        } else {
            Log.v(TAG, "No valid Google Play Services APK found.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check device for Play Services APK.
        checkPlayServices();
    }
    private void setCurrentUser(String username) {
        TextView tv = (TextView) findViewById(R.id.current_user);
        if (!username.isEmpty()) {
            tv.setText("current user : " + username); //FIXME i18n
        } else {
            tv.setText("no valid user"); //FIXME i18n
        }
    }
    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.v(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.v(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }
    
    private void storeBackendAck(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_BACKEND_ACK, regId);
        editor.putString(PROPERTY_BACKEND_ACK_USER, getUser());
        editor.commit();
    }

    private void storeUserCredentials(DemoActivity demoActivity,
            String username, String password) {
        final SharedPreferences prefs = getGcmPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_USERNAME, username);
        editor.putString(PROPERTY_PASSWORD, password);
        editor.commit();
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.v(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.v(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    private String getUser() {
        final SharedPreferences prefs = getGcmPreferences(context);
        return prefs.getString(PROPERTY_USERNAME, "");
    }
    private String getCredentials() {
        final SharedPreferences prefs = getGcmPreferences(context);
        return prefs.getString(PROPERTY_USERNAME, "") + ":" + prefs.getString(PROPERTY_PASSWORD, "");
    }

    private boolean needBackendAck(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        String backendAck = prefs.getString(PROPERTY_BACKEND_ACK, "");
        String backendAckUser = prefs.getString(PROPERTY_BACKEND_ACK_USER, "");
        Log.v(TAG, "Got backendAck: " + backendAck + ", user: " + backendAckUser);
        return !getUser().isEmpty() && (!registrationId.equals(backendAck) || !backendAckUser.equals(getUser()));
    }

    private void ensureBackendAckinBackground(Context context) {
        if (needBackendAck(context)) {
            SendToBackendInBackground();
        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regid);
                    
                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.
                    return sendRegistrationIdToBackend();

                } catch (IOException ex) {
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean b) {
                Toast.makeText(context, b ? "Success" : "Fail...", Toast.LENGTH_SHORT).show();
            }
        }.execute(null, null, null);
    }
    
    private void SendToBackendInBackground() {
        Log.v(TAG, "Before AsyncTask");
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                Log.v(TAG, "In AsyncTask");
                // You should send the registration ID to your server over HTTP, so it
                // can use GCM/HTTP or CCS to send messages to your app.
                try {
                    return sendRegistrationIdToBackend();
                } catch (ClientProtocolException e) {
                } catch (IOException e) {
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean b) {
                Toast.makeText(context, b ? "Success" : "Fail...", Toast.LENGTH_SHORT).show();
            }
        }.execute(null, null, null);
    }

    private void PairInBackground(final String PairUsername) {
        Log.v(TAG, "Before AsyncTask");
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                Log.v(TAG, "In AsyncTask");
                // You should send the registration ID to your server over HTTP, so it
                // can use GCM/HTTP or CCS to send messages to your app.
                try {
                    return sendPairRequestToBackend(PairUsername);
                } catch (ClientProtocolException e) {
                } catch (IOException e) {
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean b) {
                Toast.makeText(context, b ? "Success" : "Fail...", Toast.LENGTH_SHORT).show();
            }
        }.execute(null, null, null);
    }

    private void SendImageInBackground(final String url) {
        Log.v(TAG, "Before AsyncTask");
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                Log.v(TAG, "In AsyncTask");
                // You should send the registration ID to your server over HTTP, so it
                // can use GCM/HTTP or CCS to send messages to your app.
                try {
                    return sendImageToBackend(url);
                } catch (ClientProtocolException e) {
                } catch (IOException e) {
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean b) {
                Toast.makeText(context, b ? "Success" : "Fail...", Toast.LENGTH_SHORT).show();
            }
        }.execute(null, null, null);
    }
    
    private void RegisterInBackground (
            final String username,
            final String password,
            final String verifyPassword) {
        Log.v(TAG, "Before AsyncTask");
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                Log.v(TAG, "In AsyncTask");
                // You should send the registration ID to your server over HTTP, so it
                // can use GCM/HTTP or CCS to send messages to your app.
                try {
                    return RegisterToBackend(username, password, verifyPassword);
                } catch (ClientProtocolException e) {
                } catch (IOException e) {
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean b) {
                Toast.makeText(context, b ? "Success" : "Fail...", Toast.LENGTH_SHORT).show();
                if (b)
                    setCurrentUser(username);
            }
        }.execute(null, null, null);
    }

    private void CheckInBackground (
            final String username,
            final String password) {
        Log.v(TAG, "Before AsyncTask");
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                Log.v(TAG, "In AsyncTask");
                try {
                    return sendCheckToBackend(username, password);
                } catch (ClientProtocolException e) {
                } catch (IOException e) {
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean b) {
                Toast.makeText(context, b ? "Success" : "Fail...", Toast.LENGTH_SHORT).show();
                if (b)
                    setCurrentUser(username);
            }
        }.execute(null, null, null);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGcmPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getSharedPreferences(DemoActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }
    
    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP or CCS to send
     * messages to your app. Not needed for this demo since the device sends upstream messages
     * to a server that echoes back the message using the 'from' address in the message.
     */
    private boolean sendRegistrationIdToBackend() throws
    ClientProtocolException, IOException {
            Log.v(TAG, "Sending regid "+ regid +" to backend");

            // Create a new HttpClient and Post Header
            HttpClient httpclient = getNewHttpClient();
            HttpPost httppost = new HttpPost("https://le-simplex.mooo.com:8431/register-id");
            String credentials = getCredentials();
            String base64EncodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
            httppost.addHeader("Authorization", "Basic " + base64EncodedCredentials);

            // Add your data
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
            nameValuePairs.add(new BasicNameValuePair("regid", regid));
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            // Execute HTTP Post Request
            Log.v(TAG, "Send to le-simplex.moo.com");
            HttpResponse response = httpclient.execute(httppost);
            Log.v(TAG, "Got reponse status code " + Integer.toString(response.getStatusLine().getStatusCode()));
            if (response.getStatusLine().getStatusCode() == 200) {
                storeBackendAck(this, regid);
                return true;
            }
            return false;
    }

    private boolean sendPairRequestToBackend(String PairUsername) throws
    ClientProtocolException, IOException {
            Log.v(TAG, "Sending asking for pairing to  " + PairUsername + " to backend");

            // Create a new HttpClient and Post Header
            HttpClient httpclient = getNewHttpClient();
            HttpPost httppost = new HttpPost("https://le-simplex.mooo.com:8431/pair");
            String credentials = getCredentials();
            String base64EncodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
            httppost.addHeader("Authorization", "Basic " + base64EncodedCredentials);

            // Add your data
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
            nameValuePairs.add(new BasicNameValuePair("pair-username", PairUsername));
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            // Execute HTTP Post Request
            Log.v(TAG, "Send to le-simplex.moo.com");
            HttpResponse response = httpclient.execute(httppost);
            Log.v(TAG, "Got reponse status code " + Integer.toString(response.getStatusLine().getStatusCode()));
            return true;
     }

    private boolean sendImageToBackend(String url) throws
    ClientProtocolException, IOException {
        Log.v(TAG, "Sending url "+ url +" to backend");

        // Create a new HttpClient and Post Header
        HttpClient httpclient = getNewHttpClient();
        HttpPost httppost = new HttpPost("https://le-simplex.mooo.com:8431/send");
        String credentials = getCredentials();
        String base64EncodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        httppost.addHeader("Authorization", "Basic " + base64EncodedCredentials);

        // Add your data
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("url", url));
        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

        // Execute HTTP Post Request
        Log.v(TAG, "Send image to le-simplex.moo.com");
        HttpResponse response = httpclient.execute(httppost);
        Log.v(TAG, "Got reponse status code " + Integer.toString(response.getStatusLine().getStatusCode()));
        if (response.getStatusLine().getStatusCode() == 200) {
            return true;
        }
        return false;
    }

    private boolean sendCheckToBackend(String username, String password) throws
    ClientProtocolException, IOException {
        Log.v(TAG, "Sending check to backend");

        // Create a new HttpClient and Post Header
        HttpClient httpclient = getNewHttpClient();
        HttpGet httpget = new HttpGet("https://le-simplex.mooo.com:8431/check");
        String credentials = username + ":" + password;
        String base64EncodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        httpget.addHeader("Authorization", "Basic " + base64EncodedCredentials);


        // Execute HTTP Post Request
        Log.v(TAG, "Send image to le-simplex.moo.com");
        HttpResponse response = httpclient.execute(httpget);
        Log.v(TAG, "Got reponse status code " + Integer.toString(response.getStatusLine().getStatusCode()));
        if (response.getStatusLine().getStatusCode() == 200) {
            storeUserCredentials(this, username, password);
            if (needBackendAck(this)) {
                sendRegistrationIdToBackend();
            }
            return true;
        }
        return false;
    }

    private boolean RegisterToBackend(String username, String password, String verifyPassword) throws
    ClientProtocolException, IOException {
        Log.v(TAG, "Registering username " + username + " pwd: " + password + ", " + verifyPassword);

        // Create a new HttpClient and Post Header
        HttpClient httpclient = getNewHttpClient();
        HttpPost httppost = new HttpPost("https://le-simplex.mooo.com:8431/register");

        // Add your data
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("username", username));
        nameValuePairs.add(new BasicNameValuePair("new-password", password));
        nameValuePairs.add(new BasicNameValuePair("verify-password", verifyPassword));
        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

        // Execute HTTP Post Request
        HttpResponse response = httpclient.execute(httppost);
        Log.v(TAG, "Got reponse " + response.getStatusLine().toString());
        return sendCheckToBackend(username, password);
    }

    private HttpClient getNewHttpClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 8080));
            registry.register(new Scheme("https", sf, 8431));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
    }
}
