package com.crossmatch.pkcs15_reader;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.StrictMode;
import android.os.UserHandle;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.simalliance.openmobileapi.service.pcsc.PcscException;
import org.simalliance.openmobileapi.service.pcsc.PcscJni;


public class CardService extends Service {
    private boolean DEVELOPER_MODE = true;
    private String LOG_TAG = null;

    private ArrayList<String> mList;

    long context = 0;   // PCSC context returned from open
    String[] terminals = null;
    String reader = "";
    volatile boolean stopThread = false;

    public CardService() {
    }

    @Override
    public void onCreate() {
        if (DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        super.onCreate();
        LOG_TAG = this.getClass().getSimpleName();
        Log.i(LOG_TAG, "In onCreate");
        mList = new ArrayList<String>();
        mList.add("Object 1");
        mList.add("Object 2");
        mList.add("Object 3");

    }

    /**
     * Cleanly shutdown the PCSC reader and release context
     *
     * @param ex Exception
     */
    private void shutdown(Exception ex) {
        Log.v(LOG_TAG,"PcscException: " + ex.getMessage() + "\n");
        //showErrorAlert(ex.getMessage());
        try {
            if (context != 0)
                PcscJni.releaseContext(context);
        } catch (PcscException e) {
            e.printStackTrace();
        }
        //showErrorAlert(ex.getMessage());
    }

    /* log content and scroll to bottom */
    private void logText(String message) {
        Log.v(LOG_TAG, message);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG, "In onStartCommand");
        new Thread(new Runnable() {
            public void run() {
                // we need to save this so we don't get warnings if running with shared system UID
                UserHandle userHandle = android.os.Process.myUserHandle();

                Intent broadcastIntent = new Intent();
//                broadcastIntent.setAction(CardActivity.mBroadcastStringAction);
//                broadcastIntent.putExtra("Data", "Broadcast Data");
//                sendBroadcast(broadcastIntent);
//                //sendBroadcastAsUser(broadcastIntent, userHandle);

                Log.i(LOG_TAG, "In SCardEstablishContext");
                try {
                    context = PcscJni.establishContext(PcscJni.Scope.User);
                    Log.v(LOG_TAG,"ok: " + context + "\n");
                } catch (PcscException ex) {
                    Log.v(LOG_TAG,"PcscException: " + ex.getMessage() + "\n");
                    //showErrorAlert(ex.getMessage());
                    return;
                }

                /* SCardListReaders */
                ArrayList<String> listArr = new ArrayList<String>();
                Log.v(LOG_TAG,"\nSCardListReaders: ");
                try {
                    terminals = PcscJni.listReaders(context, null);
                    int cntReader=1;
                    // I really only care about reader 0 even if there are more readers...
                    for (String terminal: terminals)
                    {
                        listArr.add(terminal);
                        Log.v(LOG_TAG,"\n" + cntReader + "- " + terminal);
                        cntReader++;
                    }
                    Log.v(LOG_TAG,"\n");
                    if (terminals.length > 0) {
                        reader = terminals[0];
                    }
                } catch (PcscException ex) {
                    shutdown(ex);
                    return;
                }

                if (listArr.isEmpty())
                    Log.e(LOG_TAG,"No Readers found. ReScan to try again.\n");
                else {
                    Log.v(LOG_TAG,"Found "+listArr.size()+" readers.\n");

                    String[] readers = new String[listArr.size()];

                    try {
                        long timeout = -1;  // INFINITE timeout
                        int[] currentstatus = new int[listArr.size()];
                        int[] eventstatus = new int[listArr.size()];
                        // Initialize our current state to "we don't know"
                        for (int i=0; i < listArr.size(); i++) {
                            readers[i] = listArr.get(i);
                            currentstatus[i] = PcscJni.ReaderState.Unaware;
                        }

                        boolean rv = PcscJni.getStatus(context,timeout,readers, currentstatus, eventstatus);
                        while(rv && !stopThread) {
                            int current_reader;
                            int reader_state = PcscJni.ReaderState.Unknown;
                            int card_present = 0;
                            for (current_reader=0; current_reader < listArr.size(); current_reader++) {
                                //logText("Checking reader "+current_reader + "\n");
                                if ( (eventstatus[current_reader] & PcscJni.ReaderState.Changed) > 0)
                                {
                                    /* If something has changed the new state is now the current state */
                                    currentstatus[current_reader] = eventstatus[current_reader];
                                }
                                else
                                {
                                    /* If nothing changed then skip to the next reader */
                                    continue;
                                }

                                /* From here we know that the state for the current reader has
                                 * changed because we did not pass through the continue statement
                                 * above.
						        /* Specify the current reader's number and name */
                                logText("Reader "+current_reader+" "+readers[current_reader] + "\n");

                                /* Dump the full current state */
                                //logText("Event state is " + eventstatus[current_reader] + "\n");
                                logText("Card state: ");

                                if ( (eventstatus[current_reader] & PcscJni.ReaderState.Ignore) > 0) {
                                    logText("Ignore this reader, ");
                                    reader_state = PcscJni.ReaderState.Ignore;
                                }

                                if ( (eventstatus[current_reader]  & PcscJni.ReaderState.Unknown) > 0) {
                                    logText("Reader unknown - we should re-read readers now\n");
                                    //goto get_readers;
                                }

                                if ( (eventstatus[current_reader] & PcscJni.ReaderState.Unavailable) > 0) {
                                    reader_state = PcscJni.ReaderState.Unavailable;
                                    logText("Status unavailable, ");
                                }

                                if ( (eventstatus[current_reader]  & PcscJni.ReaderState.Empty) > 0) {
                                    reader_state = PcscJni.ReaderState.Empty;
                                    logText("Card removed, ");
                                }

                                if ( (eventstatus[current_reader]  & PcscJni.ReaderState.Present) > 0) {
                                    reader_state = PcscJni.ReaderState.Present;
                                    logText("Card inserted, ");
                                    card_present = 1;
                                }

                                if ( (eventstatus[current_reader]  & PcscJni.ReaderState.AtrMatch) > 0) {
                                    reader_state = PcscJni.ReaderState.AtrMatch;
                                    logText("ATR matches card, ");
                                    card_present = 1;
                                }

                                if ( (eventstatus[current_reader]  & PcscJni.ReaderState.Exclusive) > 0) {
                                    reader_state = PcscJni.ReaderState.Exclusive;
                                    logText("Exclusive Mode, ");
                                    card_present = 1;
                                }

                                if ( (eventstatus[current_reader]  & PcscJni.ReaderState.Inuse) > 0) {
                                    reader_state = PcscJni.ReaderState.Inuse;
                                    logText("Shared Mode, ");
                                    card_present = 1;
                                }

                                if ( (eventstatus[current_reader]  & PcscJni.ReaderState.Mute) > 0) {
                                    reader_state = PcscJni.ReaderState.Inuse;
                                    logText("Unresponsive card, ");
                                }

                                broadcastIntent.setAction(MainActivity.mBroadcastIntegerAction);
                                broadcastIntent.putExtra("Data", reader_state);
                                sendBroadcast(broadcastIntent);
                                Log.i(LOG_TAG, "Sending broadcastIntent with DATA = "+reader_state+"\n");
                                //sendBroadcastAsUser(broadcastIntent, userHandle);


                                /* Also dump the ATR if available */
                                int[] status = new int[] { PcscJni.Status.Unknown, 0 };
                                byte[] atr;
                                try {
                                    atr = PcscJni.getStatusChange(context, 0, readers[current_reader], status);
                                } catch (PcscException ex) {
                                    shutdown(ex);
                                    return;
                                }
                                if (atr==null) {
                                    logText("No card on reader: "+readers[current_reader] + "\n");
                                    // if card was removed cancel any existing Notification
                                    NewCardNotification.cancel(getApplicationContext());
                                } else {
                                    logText("Found card on reader: "+readers[current_reader] + "\n");

                                    StringBuilder string = new StringBuilder();
                                    for (int i = 0; i < atr.length; ++i)
                                        string.append(Integer.toHexString(0x0100 + (atr[i] & 0x00FF)).substring(1));
                                    logText("ATR: " + string.toString() + "\n");
                                    //System.out.println("Status: " + status(status[0]));
                                    broadcastIntent.setAction(MainActivity.mBroadcastStringAction);
                                    broadcastIntent.putExtra("Data", "Card Found");
                                    sendBroadcast(broadcastIntent);
                                    //Log.i(LOG_TAG, "Sending broadcastIntent with DATA = Card Found\n");

                                    // launch card detect activity(?)
                                    // by sending a unique broadcast intent:
                                    broadcastIntent.setAction("com.crossmatch.cardservice.CARD_EVENT");
                                    broadcastIntent.putExtra("Data", "Card Found");
                                    broadcastIntent.putExtra("ATR", atr);
                                    //sendBroadcast(broadcastIntent);
                                    sendOrderedBroadcast(broadcastIntent,null);
                                    //Log.i(LOG_TAG, "sendOrderedBroadcast with DATA = Card Found + ATR\n");

                                }

                            } // for
                            rv = PcscJni.getStatus(context, timeout, readers, currentstatus, eventstatus);

                        } // while

                    } catch (PcscException ex) {
                        Log.v(LOG_TAG,"PcscException: " + ex.getMessage() + "\n");
                        //showErrorAlert(ex.getMessage());
                    }
                }

                /* SCardReleaseContext */
                Log.v(LOG_TAG,"\nSCardReleaseContext: ");
                try {
                    PcscJni.releaseContext(context);
                    Log.v(LOG_TAG,"ok\n");
                } catch (PcscException ex) {
                    Log.v(LOG_TAG,"PcscException: " + ex.getMessage() + "\n");
                    //showErrorAlert(ex.getMessage());
                }

                context = 0;





            }
        }).start();
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // won't be called as service is not bound
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.i(LOG_TAG, "In onTaskRemoved");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "In onDestroy");
    }
}
