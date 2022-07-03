package it.unipi.m598992.DirectChat.fragment;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import it.unipi.m598992.DirectChat.R;

public class LoadingFragmentDialog extends DialogFragment {
    public static String FRAGMENT_TAG = "loading_dialog";

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.layout_loading_dialog).setCancelable(false).create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }
}