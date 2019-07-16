package com.crossmatch.pkcs15_reader;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class PkcsSpinnerDialog extends DialogFragment {

    public PkcsSpinnerDialog() {
        // use empty constructor. If something is needed use onCreate's
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {

        ProgressDialog progressDialog = new  ProgressDialog(getActivity());
        this.setStyle(STYLE_NO_TITLE, getTheme()); // You can use styles or inflate a view
        progressDialog.setMessage("Please wait..."); // set your messages if not inflated from XML

        progressDialog.setCancelable(false);

        return progressDialog;
    }
}
