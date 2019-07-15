package com.crossmatch.pkcs15_reader;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

public class Pkcs15Receiver extends ResultReceiver {
    private Receiver receiver;

    // constructor takes a handler
    public Pkcs15Receiver(Handler handler) {
        super(handler);
    }

    // setter for assigning the receiver
    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    // defines our event interface for communication
    public interface Receiver {
        void onReceiveResult(int resultCode, Bundle resultData);
    }

    // Delegate method which passes the result to the receiver if the receiver has been assigned
    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (receiver != null) {
            receiver.onReceiveResult(resultCode, resultData);
        }
    }
}
