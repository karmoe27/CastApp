package no.hib.kariannemoen.castapp;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import java.io.IOException;

public class Main2Activity extends AppCompatActivity {

    private static final String APP_ID = "4E0C81CB";
    private static final String TAG = "ChromeCastActivity";
    private static final String NAMESPACE = "urn:x-cast:com.google.cast.media";
    private static final int REQUEST_GMS_ERROR = 0;

    private final MediaRouter.Callback mediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            Log.d(TAG, "onRouteSelected: " + route.getName());

            CastDevice device = CastDevice.getFromBundle(route.getExtras());
            setSelectedDevice(device);
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            Log.d(TAG, "onRouteUnselected: " + route.getName());
            stopApplication();
//            setSelectedDevice(null);
//            setSessionStarted(false);
        }
    };
    private final Cast.Listener castClientListener = new Cast.Listener() {
        @Override
        public void onApplicationDisconnected(int statusCode) {
            try {
                Cast.CastApi.removeMessageReceivedCallbacks(apiClient, NAMESPACE);
            } catch (IOException e) {
                Log.w(TAG, "Exception while launching application", e);
            }
//            setSelectedDevice(null);
//            setSessionStarted(false);
        }

        @Override
        public void onVolumeChanged() {
            if(apiClient != null) {
                Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(apiClient));
            }
        }
    };
    private final GoogleApiClient.ConnectionCallbacks connectionCallback = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(Bundle bundle) {
            try {
                Cast.CastApi.launchApplication(apiClient, APP_ID, false)
                        .setResultCallback(connectionResultCallback);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }

        @Override
        public void onConnectionSuspended(int i) {

        }
    };
    private final GoogleApiClient.OnConnectionFailedListener connectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
//            setSelectedDevice(null);
        }
    };
    private final ResultCallback<Cast.ApplicationConnectionResult> connectionResultCallback = new ResultCallback<Cast.ApplicationConnectionResult>() {
        @Override
        public void onResult(Cast.ApplicationConnectionResult result) {
            Status status = result.getStatus();

            if(status.isSuccess()) {
                applicationStarted = true;

                try {
                    Cast.CastApi.setMessageReceivedCallbacks(apiClient, NAMESPACE, incomingMsgHandler);
                } catch (IOException e) {
                    Log.e(TAG, "Exception while creating channel", e);
                }
                setSessionStarted(true);
            } else {
                setSessionStarted(false);
            }
        }
    };
    public final Cast.MessageReceivedCallback incomingMsgHandler = new Cast.MessageReceivedCallback() {
        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message){
            Log.d(TAG, String.format("message namespace: %s message: %s", namespace, message));
            txtLog.append(String.format("\nmessage namespace: %s message: %s", namespace, message));
        }
    };
    private final View.OnClickListener btnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_ping:
                    sendMessage(editMessage.getText().toString());
                    break;
            }
        }
    };

    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private CastDevice selectedDevice;
    private GoogleApiClient apiClient;
    private boolean applicationStarted;
    private TextView txtStatus;
    private TextView txtLog;
    private EditText editMessage;
    private Button pingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        txtStatus = (TextView) findViewById(R.id.txt_status);
        txtLog = (TextView) findViewById(R.id.txt_log);
        editMessage = (EditText) findViewById(R.id.edit_message);
        pingButton = (Button) findViewById(R.id.btn_ping);
        pingButton.setOnClickListener(btnClickListener);

        mediaRouter = MediaRouter.getInstance(getApplicationContext());
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(APP_ID))
                .build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu, menu);

        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider)
                MenuItemCompat.getActionProvider(mediaRouteMenuItem);

        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    protected void onStop() {
//        setSelectedDevice(null);
        mediaRouter.removeCallback(mediaRouterCallback);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int errorCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(errorCode != ConnectionResult.SUCCESS) {
            GooglePlayServicesUtil.getErrorDialog(errorCode, this, REQUEST_GMS_ERROR).show();
        }
    }

    @Override
    protected void onPause() {
        disconnectApiClient();
        super.onPause();
    }

    private void setSelectedDevice(CastDevice device) {
        Log.d(TAG, "setSelectedDevice: " + device);

        selectedDevice = device;

        if(selectedDevice != null) {
            try {
                stopApplication();
                disconnectApiClient();
                connectApiClient();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Exception while connecting API client", e);
                disconnectApiClient();
            }
        } else {
            disconnectApiClient();
            mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
        }
    }

    private void connectApiClient() {
        Cast.CastOptions apiOptions = Cast.CastOptions
                .builder(selectedDevice, castClientListener).build();
        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptions)
                .addConnectionCallbacks(connectionCallback)
                .addOnConnectionFailedListener(connectionFailedListener)
                .build();
        apiClient.connect();
    }

    private void disconnectApiClient() {
        if(apiClient != null) {
            apiClient.disconnect();
            apiClient = null;
        }
    }

    private void stopApplication() {
        if(apiClient == null) return;

        if(applicationStarted) {
            Cast.CastApi.stopApplication(apiClient);
            applicationStarted = false;
        }
    }

    private void sendMessage(String message) {
        if(apiClient != null) {
            try {
                Cast.CastApi.sendMessage(apiClient, NAMESPACE, message)
                        .setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status result) {
                                if(!result.isSuccess()) {
                                    Log.e(TAG, "Sending message failed");
                                }
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Exception while sending message", e);
            }
        }
    }

    private void setSessionStarted(boolean enabled) {
        pingButton.setEnabled(enabled);
        txtStatus.setText(enabled ? "connected" : "disconnected");
    }
}
