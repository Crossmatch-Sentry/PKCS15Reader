package com.crossmatch.pkcs15_reader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class CardBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "CardBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("Action: ");
        sb.append(intent.getAction());
        sb.append("\n");
        sb.append("Extra: ");
        sb.append(intent.getCharSequenceExtra("Data"));
        sb.append("\n");
        sb.append("ATR: ");

        byte[] atr = intent.getByteArrayExtra("ATR");
        StringBuilder s_atr = new StringBuilder();
        if (atr == null) {
            s_atr.append("no ATR");
        } else {
            for (int i = 0; i < atr.length; ++i)
                s_atr.append(Integer.toHexString(0x0100 + (atr[i] & 0x00FF)).substring(1));
        }
        sb.append(s_atr.toString());
        sb.append("\n");
        String log = sb.toString();
        Log.d(TAG, log);
        Toast.makeText(context, log, Toast.LENGTH_LONG).show();

        NewCardNotification.notify(context, "Card Detected", 1);
    }
}
