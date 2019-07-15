package com.crossmatch.pkcs15_reader;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 *     run the input "cmd" as specified using the Android ProcessBuilder
 *     This is intended to run the pkcs15-tool command line program in a
 *     separate thread and return the result to the caller
 */
public class Pkcs15IntentService extends IntentService {
    final String LOG_TAG = "Pkcs15IntentService";

    private static final String ACTION_RUNCMD = "com.crossmatch.pkcs15_reader.action.RUNCMD";

    // TODO: Rename parameters
    private static final String EXTRA_RUNCMD = "com.crossmatch.pkcs15_reader.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "com.crossmatch.pkcs15_reader.extra.PARAM2";
    private static final String EXTRA_RECVR = "com.crossmatch.pkcs15_reader.extra.RECEIVER";

    private ResultReceiver resultReceiver;

    public Pkcs15IntentService() {
        super("Pkcs15IntentService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionRunCmd(Context context, ResultReceiver receiver, String cmd, String param2) {
        Intent intent = new Intent(context, Pkcs15IntentService.class);
        intent.setAction(ACTION_RUNCMD);
        intent.putExtra(EXTRA_RECVR, receiver);
        intent.putExtra(EXTRA_RUNCMD, cmd);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            // extract the receiver passed into the service
            resultReceiver = intent.getParcelableExtra(EXTRA_RECVR);

            if (ACTION_RUNCMD.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_RUNCMD);
                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionRunCmd(param1, param2);
            } else {
                Log.e(LOG_TAG,"unknown intent received");
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionRunCmd(String cmd, String param2) {
        Log.i(LOG_TAG, "Starting handleActionRunCmd()");

        String[] command = cmd.split(" ");
        Log.v(LOG_TAG, "Cmd to run input: "+cmd+" command: "+ Arrays.asList(command));

        StringBuilder cmdReturn = new StringBuilder();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);   // send error output to input stream
            Process process = processBuilder.start();

            InputStream inputStream = process.getInputStream();
            int c;

            while ((c = inputStream.read()) != -1) {
                cmdReturn.append((char) c);
            }

            //tvConsole.setText(cmdReturn.toString());

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // send results back to Activity
        Bundle bundle = new Bundle();
        bundle.putString("resultValue", cmdReturn.toString());
        resultReceiver.send(Activity.RESULT_OK, bundle);

        Log.i(LOG_TAG, "handleActionRunCmd() is complete");
    }

}
