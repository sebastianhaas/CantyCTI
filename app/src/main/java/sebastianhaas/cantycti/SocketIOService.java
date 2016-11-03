package sebastianhaas.cantycti;

import android.Manifest;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.client.SocketIOException;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.EngineIOException;

public class SocketIOService extends Service {

    public static final String ACTION_INCOMING_CALL = "sebastianhaas.cantycti.action.ACTION_INCOMING_CALL";

    public static final String EXTRA_PHONE_NUMBER = "sebastianhaas.cantycti.extra.PHONE_NUMBER";
    public static final String EXTRA_CALL_STATE = "sebastianhaas.cantycti.extra.CALL_STATE";
    public static final String EXTRA_ID = "sebastianhaas.cantycti.extra.ID";

    private static final String EVENT_KEY_INCOMING_CALL = "incoming call";
    private static final String EVENT_KEY_CALL_REQUEST = "call request";

    private static final int INCOMING_CALL_STATE_RINGING = 0;
    private static final int INCOMING_CALL_STATE_OFFHOOK = 1;
    private static final int INCOMING_CALL_STATE_IDLE = 2;

    private Socket mSocket;

    @Override
    public void onCreate() {
        try {
            // Get socket host from preferences
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String prefSocketHost = sharedPref.getString(SettingsActivity.KEY_PREF_SOCKET_HOST, "");

            Log.d("CantyCTI", "Creating socket.io connection with " + prefSocketHost + "...");
            mSocket = IO.socket(prefSocketHost);
            mSocket.on(Socket.EVENT_CONNECT, mOnConnect);
            mSocket.on(Socket.EVENT_DISCONNECT, mOnDisconnect);
            mSocket.on(Socket.EVENT_CONNECTING, mOnConnecting);
            mSocket.on(Socket.EVENT_CONNECT_ERROR, mOnConnectError);
            mSocket.on(Socket.EVENT_RECONNECTING, mOnReconnecting);
            mSocket.on(Socket.EVENT_RECONNECT_ERROR, mOnReconnectError);
            mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, mOnConnectTimeout);
            mSocket.on(Socket.EVENT_MESSAGE, mOnMessage);
            mSocket.on(EVENT_KEY_CALL_REQUEST, mOnCallRequest);
            mSocket.connect();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDestroy() {
        Log.d("CantyCTI", "Destroying socket.io connection...");
        mSocket.disconnect();
        mSocket.off(Socket.EVENT_CONNECT, mOnConnect);
        mSocket.off(Socket.EVENT_DISCONNECT, mOnDisconnect);
        mSocket.off(Socket.EVENT_CONNECTING, mOnConnecting);
        mSocket.off(Socket.EVENT_CONNECT_ERROR, mOnConnectError);
        mSocket.off(Socket.EVENT_RECONNECTING, mOnReconnecting);
        mSocket.off(Socket.EVENT_RECONNECT_ERROR, mOnReconnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, mOnConnectTimeout);
        mSocket.off(Socket.EVENT_MESSAGE, mOnMessage);
        mSocket.off(EVENT_KEY_CALL_REQUEST, mOnCallRequest);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_INCOMING_CALL.equals(action)) {
                sendIncomingCallEvent(intent);
            }
        }

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private final Emitter.Listener mOnConnect = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            Log.d("CantyCTI", "Socket connected.");
        }
    };

    private final Emitter.Listener mOnDisconnect = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            Log.d("CantyCTI", "Socket disconnected.");
        }
    };

    private final Emitter.Listener mOnConnecting = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            Log.d("CantyCTI", "Socket connecting...");
        }
    };

    private final Emitter.Listener mOnReconnecting = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            Log.d("CantyCTI", "Socket reconnecting...");
        }
    };

    private final Emitter.Listener mOnConnectError = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            try {
                EngineIOException e = (EngineIOException) args[0];
                Log.e("CantyCTI", "Socket connection error.", e);
            } catch (Error e) {
                Log.e("CantyCTI", "Could not retrieve reason for connect error.", e);
            }
        }
    };

    private final Emitter.Listener mOnConnectTimeout = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            try {
                EngineIOException e = (EngineIOException) args[0];
                Log.e("CantyCTI", "Socket connect timeout.", e);
            } catch (Error e) {
                Log.e("CantyCTI", "Could not retrieve reason for connect timeout.", e);
            }
        }
    };

    private final Emitter.Listener mOnReconnectError = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            try {
                SocketIOException e = (SocketIOException) args[0];
                Log.e("CantyCTI", "Socket reconnect error.", e);
            } catch (Error e) {
                Log.e("CantyCTI", "Could not retrieve reason for reconnect error.", e);
            }
        }
    };

    private final Emitter.Listener mOnMessage = new Emitter.Listener() {

        @Override
        public void call(final Object... args) {
            Log.d("CantyCTI", "Message sent " + args[0].toString());
        }
    };

    private final Emitter.Listener mOnCallRequest = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            JSONObject callRequest = (JSONObject) args[0];
            try {
                String phoneNumber = callRequest.getString("phoneNumber");
                handleActionCallRequest(phoneNumber);
            } catch (JSONException e) {
                Log.e("CantyCTI", "Could not parse JSON of call request.", e);
            }
        }
    };

    private void sendIncomingCallEvent(Intent intent) {
        Log.d("CantyCTI", "Emitting incoming call event.");
        final String phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER);
        final String callState = intent.getStringExtra(EXTRA_CALL_STATE);
        final String id = intent.getStringExtra(EXTRA_ID);
        JSONObject incomingCall = new JSONObject();
        try {
            incomingCall.put("phoneNumber", phoneNumber);
            incomingCall.put("id", id);
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(callState)) {
                incomingCall.put("callState", INCOMING_CALL_STATE_IDLE);
            } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(callState)) {
                incomingCall.put("callState", INCOMING_CALL_STATE_RINGING);
            } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(callState)) {
                incomingCall.put("callState", INCOMING_CALL_STATE_OFFHOOK);
            }
        } catch (JSONException e) {
            Log.e("CantyCTI", "Could not build JSON for incoming call event.", e);
        }
        mSocket.emit(EVENT_KEY_INCOMING_CALL, incomingCall);
    }

    private void handleActionCallRequest(String phoneNumber) {
        try {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Log.e("CantyCTI", "No permission granted to make phone call.");
            } else {
                Log.d("CantyCTI", "Preparing call...");
                Intent phoneIntent = new Intent(Intent.ACTION_CALL);
                phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                phoneIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                phoneIntent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(phoneIntent);
            }
        } catch (ActivityNotFoundException e) {
            Log.e("CantyCTI", "Failed to make the requested call.", e);
        }
    }
}
