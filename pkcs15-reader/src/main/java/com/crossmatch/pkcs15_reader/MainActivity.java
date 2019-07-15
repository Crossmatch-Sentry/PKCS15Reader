/**
 * Copyright 2018 Crossmatch Technologies, Inc. All rights reserved.
 *
 * Sample program to illustrate reading from a contact smartcard on
 * the Crossmatch Verifier Sentry device.
 *
 * This program uses the built-in pkcs15-tool on the Sentry device to
 * abstract out the details of the smartcard to provide a high level
 * interface. See the pkcs15-tool and manual pages for further information.
 *
 * The pkcs15-tool is invoked using Androids ProcessBuilder.
 *
 * As long as the smartcard you are trying to read is PKCS#15 compliant,
 * such as a Javacard, CAC Card, TWIC card, etc. It should be able to
 * read the containers on the card directly.
 * This requires some understanding of the underlying card layout.
 * You can run the same command on the Sentry device from a cmd shell
 * to see the results of the output you can expect.
 *
 * For example to "dump" the complete contents of a CAC card, which will
 * require the users PIN to be entered you can do something like this:
 *
 *      adb > pkcs15-tool  --dump --pin 1234
 *
 * If you wanted to just dump the CHUID (Card Holder Unique Identifier) container.
 * You first need to figure out the container ID which can be found by the
 * --dump command or
 *
 *      adb > pkcs15-tool --list-data-objects --short
 * This will return something like:
 * Using reader with a card: Microchip SEC1110 (F30B40F3) 00 00
 * Card has 12 Data object(s).
 *         Path:db00          2.16.840.1.101.3.7.1.219.0  Size:  268  Card Capability Container
 *         Path:3000          2.16.840.1.101.3.7.2.48.0  Size: 1856  Card Holder Unique Identifier
 *         Path:6010          2.16.840.1.101.3.7.2.96.16  AuthID:01   Cardholder Fingerprints
 *         Path:3001          2.16.840.1.101.3.7.2.48.1  AuthID:01   Printed Information
 *         Path:6030          2.16.840.1.101.3.7.2.96.48  AuthID:01   Cardholder Facial Image
 *         Path:0100          2.16.840.1.101.3.7.2.1.0  Size: 1090  X.509 Certificate for Digital Signature
 *         Path:0102          2.16.840.1.101.3.7.2.1.2  Size: 1051  X.509 Certificate for Key Management
 *
 * We can then use the name returned directly like this:
 *
 *      adb > pkcs15-tool  --pin 77777777 --verify-pin --read-data-object "Card Holder Unique Identifier"
 *      or
 *      adb > pkcs15-tool  --pin 77777777 --verify-pin --read-data-object 2.16.840.1.101.3.7.2.48.0
 *
 * which will dump out the container in hex
 *
 * You will probably want to save this data to a file for manipulation as desired.
 * To save the containter you can run the command using the "-o" option to save the contents to a file.
 *
 *      > pkcs15-tool  --pin 77777777 --verify-pin --read-data-object 2.16.840.1.101.3.7.2.48.0 -o "chuid.bin"
 *
 * NOTE: you must create the output file into a place your application can write to.
 *
 * This same command can be used in the program.
 * Keep in mind that card reading operations can take some time so this should be done
 * in a background thread and the results sent back to the main thread
 * for display as this program does.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.crossmatch.pkcs15_reader;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.simalliance.openmobileapi.service.pcsc.PcscJni;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private String LOG_TAG = null;

    public static final String mBroadcastStringAction = "com.crossmatch.broadcast.string";
    public static final String mBroadcastIntegerAction = "com.crossmatch.broadcast.integer";
    public static final String mBroadcastArrayListAction = "com.crossmatch.broadcast.arraylist";
    public static final String mBroadcastCardEvent = "com.crossmatch.cardservice.CARD_EVENT";
    private IntentFilter mIntentFilter;
    public Pkcs15Receiver pkcs15Receiver;

    TextView tvConsole;
    TextView tvCardStatus;
    EditText mPin;
    Handler mHandler;
    AlertDialog alertDialog = null;
    Button mDisplayButton;

    public static NewCardNotification newCardNotification = new NewCardNotification();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LOG_TAG = this.getClass().getSimpleName();
        Log.i(LOG_TAG, "In onCreate");

        mHandler = new Handler();

        // Setup UI
        tvConsole = findViewById(R.id.console);
        tvCardStatus = findViewById(R.id.tvCardStatus);
        mDisplayButton = findViewById(R.id.btn_display);
        mDisplayButton.setEnabled(false);

        // Create intent filter for card events
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(mBroadcastStringAction);
        mIntentFilter.addAction(mBroadcastIntegerAction);
        mIntentFilter.addAction(mBroadcastArrayListAction);
        mIntentFilter.addAction(mBroadcastCardEvent);

        /* setup receiver for getting the results of pkcs15-tool background reader */
        setupServiceReceiver();

        // check if the card service is running and start it if not
        if (isMyServiceRunning(CardService.class)) {
            Log.i(LOG_TAG, "Service is already running");
            tvConsole.setText("Service is running\n");
        } else {
            Log.i(LOG_TAG, "Service is not running we need to start it");
            tvConsole.setText("Service is not running so start it\n");
            // This starts the service when app launches --> moved to start on power up
            Intent serviceIntent = new Intent(this, CardService.class);
            startService(serviceIntent);
        }

        tvConsole.setText("Waiting for card...\n");

        // see if there is a notification intent waiting for us
        onNewIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent){
        Bundle extras = intent.getExtras();
        if(extras != null){
            if(extras.containsKey("card_detect"))
            {
                NewCardNotification.cancel(getApplicationContext());
                //setContentView(R.layout.activity_main);
                // extract the extra-data in the Notification
                int card_detect = extras.getInt("card_detect");
                tvConsole.append("Card Detect Intent message: "+card_detect);
                launchPinEntry();
            }
        }
    }

    /**
     * Called when the user selects Display Card button
     *
     * @param view
     */
    public void DisplayCardData(View view) {
        Log.v(LOG_TAG, "Dump card data...");

        /* build up command string to pass to pkcs15 service to run*/

        // dump everything
        //String cmd = "pkcs15-tool --list-applications  --dump --pin "+ mPin.getText();

        // dump CHUID (doesn't need PIN)
        String cmd = "pkcs15-tool --read-data-object 2.16.840.1.101.3.7.2.48.0 ";

        // dump fingerprint container (needs PIN)
        //String cmd = "pkcs15-tool  --read-data-object 2.16.840.1.101.3.7.2.96.16 --verify-pin --pin "+ mPin.getText();

        Log.v(LOG_TAG, "Running pkcs15-tool command: " + cmd);
        Pkcs15IntentService.startActionRunCmd(getApplicationContext(), pkcs15Receiver, cmd, "bar");
    }

    /**
     * Setup the callback for when data is received from the pkcs15 reader service
     */
    public void setupServiceReceiver() {
        pkcs15Receiver = new Pkcs15Receiver(new Handler());

        // this is where we specify what happens when data is received from the service
        pkcs15Receiver.setReceiver(new Pkcs15Receiver.Receiver() {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == RESULT_OK) {
                    String resultValue = resultData.getString("resultValue");
                    tvConsole.append("Got result: "+resultValue+"\n");
                }
            }
        });

    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /* launch PIN entry dialog */
    private void launchPinEntry() {
        mHandler.post(new Runnable() {
            public void run() {
            Log.v(LOG_TAG, "Launching PIN entry dialog");

            final AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);
            View mView = getLayoutInflater().inflate(R.layout.dialog_pin, null);
            mPin = mView.findViewById(R.id.pin_text);
            Button mOk = mView.findViewById(R.id.btn_pinok);
            mBuilder.setView(mView);
            alertDialog = mBuilder.create();
            alertDialog.show();

            mOk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(!mPin.getText().toString().isEmpty()) {
                        Log.v(LOG_TAG, "Accepted user PIN = " + mPin.getText());
                        tvConsole.append("New card PIN: "+ mPin.getText()+ "\n");
                        mDisplayButton.setEnabled(true);
                    } else {
                        Toast.makeText(MainActivity.this,
                                R.string.pin_fail,
                                Toast.LENGTH_SHORT).show();
                    }
                    alertDialog.dismiss();
                }

            });
            }
        });
    }


    /**
     * BroadcastReceiver to receive card reader events
     *
     *
     */
    private BroadcastReceiver mReceiver;
    {
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                tvConsole.setText(tvConsole.getText()
                        + "Broadcast From Service: \n");
                if (intent.getAction().equals(mBroadcastStringAction)) {
                    String status = intent.getStringExtra("Data");
                    tvConsole.setText(tvConsole.getText() + status + "\n\n");
                    if (status.equals("Card Found")) {
                        tvCardStatus.setText(getString(R.string.card_insert_txt));
                        launchPinEntry();
                    }
                } else if (intent.getAction().equals(mBroadcastIntegerAction)) {
                    int reader_state = intent.getIntExtra("Data", 0);
                    tvConsole.setText(tvConsole.getText().toString() + reader_state + "\n\n");
                    if (reader_state == PcscJni.ReaderState.Empty) {
                        tvCardStatus.setText(getString(R.string.card_empty_txt));
                        mDisplayButton.setEnabled(false);
                        if (alertDialog != null)
                            alertDialog.dismiss();
                    }
                } else if (intent.getAction().equals(mBroadcastArrayListAction)) {
                    tvConsole.setText(tvConsole.getText()
                            + intent.getStringArrayListExtra("Data").toString()
                            + "\n\n");
                } else if  (intent.getAction().equals(mBroadcastCardEvent)) {
                    byte[] atr = intent.getByteArrayExtra("ATR");
                    StringBuilder s_atr = new StringBuilder();
                    if (atr == null) {
                        s_atr.append("no ATR");
                    } else {
                        for (int i = 0; i < atr.length; ++i)
                            s_atr.append(Integer.toHexString(0x0100 + (atr[i] & 0x00FF)).substring(1));
                    }

                    final String msg = "Action: "+intent.getAction()+" Extra: "+intent.getCharSequenceExtra("Data")+" ATR:"+s_atr;
                    tvConsole.setText(tvConsole.getText()+msg+"\n\n");
                    Log.d(LOG_TAG, msg);
                    // Handle here so we don't get notification from CardBroadcastReceiver
                    mReceiver.abortBroadcast();
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mReceiver);
        super.onPause();
    }

}
