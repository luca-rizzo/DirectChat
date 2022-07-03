package it.unipi.m598992.DirectChat.activity;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import it.unipi.m598992.DirectChat.R;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final int STORAGE_PERMISSION = 1;

    public static final String SAVE_IMG_TO_SHARED = "saveImagesToShared";
    public static final String SAVE_VIDEOS_TO_SHARED = "saveVideosToShared";
    public static final String SAVE_AUDIOS_TO_SHARED = "saveAudiosToShared";
    public static final String SAVE_GEN_FILE_TO_SHARED = "saveGeneralFileToShared";

    private String currentRequestedKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //controllo se il contenitore era già stato popolato
        SettingsFragment settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentById(R.id.settingsContainer);
        if (settingsFragment == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.settingsContainer, new SettingsFragment()).commit();
        }
        //il contenitore era già stato popolato automaticamente

        if (Build.VERSION.SDK_INT < 29)
            refreshStorageSharedPreferences();

    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener{
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings_preference_screen, rootKey);
            if(getActivity() != null)
                PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key){
            if(key.equals(SAVE_AUDIOS_TO_SHARED) || key.equals(SAVE_IMG_TO_SHARED) || key.equals(SAVE_GEN_FILE_TO_SHARED) || key.equals(SAVE_VIDEOS_TO_SHARED)){
                SwitchPreferenceCompat switchPreferenceCompat = findPreference(key);
                boolean turnedOn = sharedPreferences.getBoolean(key, false);
                if(switchPreferenceCompat != null){
                    switchPreferenceCompat.setChecked(turnedOn);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SAVE_AUDIOS_TO_SHARED) || key.equals(SAVE_IMG_TO_SHARED) || key.equals(SAVE_GEN_FILE_TO_SHARED) || key.equals(SAVE_VIDEOS_TO_SHARED)) {
            boolean turnedOn = sharedPreferences.getBoolean(key, false);
            if (turnedOn) {
                if (Build.VERSION.SDK_INT < 29) {
                    //l'app deve avere i permessi WRITE_EXTERNAL_STORAGE per abilitare l'opzione se la versione android è inferiore ad Android 10
                    if (!checkStoragePermissionUnderSdk29()) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(key, false);
                        currentRequestedKey = key;
                        editor.apply();
                    }
                }
            }
        }
    }


    //***** METODI GESTIONE PERMESSI SCRITTURA NELLO STORAGE CONDIVISO PER ANDROID < 10 *****//

    //descrizione: permessi di storage per scrivere nella memoria condivisa. Nei dispositivi con Android < 10 sfrutterò l'api "standard" di scrittura nelle
    //directory ottenute con Environment.getExternalStoragePublicDirectory() e per farlo necessito dei permessi di scrittura sullo storage esterno
    private boolean checkStoragePermissionUnderSdk29() {
        int access_write_external = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (access_write_external == PERMISSION_GRANTED) {
            return true;
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showRequestDialog(false);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        }
        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean neverAskAgainChecked;
        if (requestCode == STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                //salvo la preferenza
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(currentRequestedKey, true);
                editor.apply();
            } else {
                neverAskAgainChecked = !shouldShowRequestPermissionRationale(permissions[0]);
                showRequestDialog(neverAskAgainChecked);
            }
        }
    }

    private void showRequestDialog(boolean neverAsk) {
        Dialog dialog;
        if (neverAsk) {
            //utente ha cliccato Never ask again --> mostra dialog che indica di andare nelle impostazioni
            dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.need_storage_permission_perm_denied)
                    .setNegativeButton(R.string.need_to_go_to_settings, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).create();
        } else {
            //utente non ha cliccato Never ask again --> mostra dialog che consente all'utente di richiedere permessi
            dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.need_storage_permission)
                    .setPositiveButton(R.string.retry_permission_no_necessary, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(SettingsActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
                        }
                    }).setNegativeButton(R.string.deny_permission_no_necessary, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).create();
        }
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    //descrizione: aggiorno le preferenze sullo storage sulla base dei permessi dell'utente. Egli infatti potrebbe disattivare i permessi non necessari
    //di scrittura sullo storage condiviso dalle impostazioni ---> disabilito anche le preferenze
    private void refreshStorageSharedPreferences(){
        int access_storage_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if(access_storage_permission != PERMISSION_GRANTED){
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(SettingsActivity.SAVE_IMG_TO_SHARED, false);
            editor.putBoolean(SettingsActivity.SAVE_AUDIOS_TO_SHARED, false);
            editor.putBoolean(SettingsActivity.SAVE_VIDEOS_TO_SHARED, false);
            editor.putBoolean(SettingsActivity.SAVE_GEN_FILE_TO_SHARED, false);
            editor.apply();
        }
    }
}