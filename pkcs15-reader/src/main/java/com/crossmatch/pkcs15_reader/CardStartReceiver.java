package com.crossmatch.pkcs15_reader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CardStartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Start the CardService when the system boots
            Intent serviceIntent = new Intent(context.getApplicationContext(), CardService.class);
            context.getApplicationContext().startService(serviceIntent);
        }
    }
}
