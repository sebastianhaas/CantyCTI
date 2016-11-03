package sebastianhaas.cantycti;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.UUID;

public class TelephonyReceiver extends BroadcastReceiver {

    private static String mLastState;
    private static String mLastPhoneNumber;
    private static String mLastId;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("CantyCTI", intent.getAction());
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state.equals(mLastState)) {
            return;
        }
        if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
            if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(mLastState)) {
                Log.d("CantyCTI", state);
                publishPhoneState(context, mLastPhoneNumber, state, mLastId);
                mLastId = null;
                mLastPhoneNumber = null;
            }
        } else if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
            Log.d("CantyCTI", state);
            mLastPhoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            mLastId = UUID.randomUUID().toString();
            publishPhoneState(context, mLastPhoneNumber, state, mLastId);
            Log.d("CantyCTI", mLastPhoneNumber);
        } else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(mLastState)) {
                Log.d("CantyCTI", state);
                publishPhoneState(context, mLastPhoneNumber, state, mLastId);
            }
        }
        mLastState = state;
    }

    private void publishPhoneState(Context context, String phoneNumber, String callState, String id) {
        Intent socketIntent = new Intent(context, SocketIOService.class);
        socketIntent.setAction(SocketIOService.ACTION_INCOMING_CALL);
        socketIntent.putExtra(SocketIOService.EXTRA_PHONE_NUMBER, phoneNumber);
        socketIntent.putExtra(SocketIOService.EXTRA_CALL_STATE, callState);
        socketIntent.putExtra(SocketIOService.EXTRA_ID, id);
        context.startService(socketIntent);
    }
}
