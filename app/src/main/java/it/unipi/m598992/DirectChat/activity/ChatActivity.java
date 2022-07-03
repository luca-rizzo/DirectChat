package it.unipi.m598992.DirectChat.activity;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import it.unipi.m598992.DirectChat.R;
import it.unipi.m598992.DirectChat.RoomDB.entity.Message;
import it.unipi.m598992.DirectChat.adapter.MessageListAdapter;
import it.unipi.m598992.DirectChat.fragment.LoadingFragmentDialog;
import it.unipi.m598992.DirectChat.service.CommunicationService;
import it.unipi.m598992.DirectChat.viewModel.ChatActivityViewModel;
import it.unipi.m598992.DirectChat.worker.DeleteFilesWorker;

public class ChatActivity extends AppCompatActivity implements View.OnClickListener, PopupMenu.OnMenuItemClickListener, MessageListAdapter.SelectMessageCVListener, MessageListAdapter.PlayPauseButtonListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MessageListAdapter.ScrollRVListener {

    //**** Chiavi costanti di comunicazione sfruttate da Communication Service ****//
    public static final String COMMUNICATION_CLOSED = "it.unipi.m598992.directChat.COMMUNICATION_CLOSED";
    public static final String COMMUNICATION_OK = "it.unipi.m598992.directChat.COMMUNICATION_OK";
    public static final String SENDING_UPDATE = "it.unipi.m598992.directChat.SENDING_UPDATE";
    public static final String EXTRAS_OTHER_SIDE_DEVICE_NAME = "it.unipi.m598992.directChat.EXTRAS_OTHER_SIDE_DEVCE_NAME";
    public static final String EXTRAS_PROGRESS_STATE = "it.unipi.m598992.directChat.EXTRAS_PROGRESS_STATE";
    public static final String EXTRAS_FILE_NAME = "it.unipi.m598992.directChat.EXTRAS_FILE_NAME";

    public static final int RECORDING_PERMISSION_REQUEST = 1;
    public static final int WIFI_DIRECT_DISCONNECTION_REQUIRED = 42;

    private EditText msgEditTextView;
    private TextView fileNameTxtView;
    private ImageButton sendTxtButton;
    private ImageButton sendPhotoButton;
    private ImageButton sendRecButton;
    private ImageButton sendAttachmentButton;
    private ProgressBar sendingProgressBar;
    private RecyclerView msgRV;
    private Chronometer recChronometer;

    private ChatActivityViewModel chatActivityViewModel;
    private MessageListAdapter messageListAdapter;

    //Broadcast Receiver sfruttato per ottenere informazioni riguardo lo stato del CommunicationService e sui relativi
    //aggiornamenti rigurado l'invio di un file
    private BroadcastReceiver connectionUpdateReceive;


    private MediaRecorder mediaRecorder;
    //costante booleana che indica se il media recorder è nello stato Started
    private boolean isRecording = false;
    //stringa che identifica il path della registrazione in corso
    private String currentRecordingPath;

    private ActionMode myCab = null;

    private MediaPlayer mediaPlayer = null;
    //costante booleana che indica se il media player è nello stato Started
    private boolean isPlaying = false;
    //costante booleana che indica se il media player è nello stato Paused
    private boolean isPaused = false;
    //valore che indica posizione seek bar nell'audio che vogliamo riprodurre
    private int seekBarProgress = 0;
    //handler sfruttato per sottomettere nella MessageQueue del Thread UI task di aggiornamento della seekBar del brano in riproduzione
    private Handler handler;

    private Uri currentPhotoURI;

    //Registro il callback da chiamare quando riceverò un risultato dalla richiesta di scattare una foto
    ActivityResultLauncher<Uri> takePhotoActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            new ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean result) {
                    // stuff here
                    if (result) {
                        //invio al service un intent ACTION_SEND_MSG specificando uri e tipo del file
                        Intent i = new Intent(getApplicationContext(), CommunicationService.class);
                        i.setAction(CommunicationService.ACTION_SEND_MSG);
                        i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.IMAGE);
                        i.putExtra(CommunicationService.EXTRAS_CONTENT_URI, currentPhotoURI);
                        startService(i);
                    }
                }
            });

    //Registro il callback da chiamare quando riceverò un risultato dalla richiesta di selezionare un file generale
    ActivityResultLauncher<Intent> pickFileActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent intent = result.getData();
                        if (intent != null) {
                            Uri selectedFileUri = intent.getData();
                            if (selectedFileUri != null) {
                                //rendo il permesso READ / WRITE persistente anche ai riavvii
                                getContentResolver().takePersistableUriPermission(selectedFileUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                                String mimeType = getMIMEType(selectedFileUri);
                                if (mimeType.isEmpty()) {
                                    Toast.makeText(ChatActivity.this, "Internal error in picking file!", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                Intent i = new Intent(getApplicationContext(), CommunicationService.class);
                                //invio al service un intent ACTION_SEND_MSG specificando uri e tipo del file
                                i.setAction(CommunicationService.ACTION_SEND_MSG);
                                if (mimeType.contains("image")) {
                                    i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.IMAGE);
                                } else if (mimeType.contains("video")) {
                                    i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.VIDEO);
                                } else if (mimeType.contains("audio")) {
                                    i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.AUDIO);
                                } else {
                                    i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.GENERAL_FILE);
                                }
                                i.putExtra(CommunicationService.EXTRAS_CONTENT_URI, selectedFileUri);
                                startService(i);
                            }
                        }
                    }
                }
            });

    //Registro il callback da chiamare quando riceverò un risultato dalla richiesta di selezionare un audio
    ActivityResultLauncher<Intent> pickAudioActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent intent = result.getData();
                        if (intent != null) {
                            Uri selectedAudioUri = intent.getData();
                            if (selectedAudioUri != null) {
                                //rendo il permesso READ / WRITE persistente anche ai riavvii
                                getContentResolver().takePersistableUriPermission(selectedAudioUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                                //invio al service un intent ACTION_SEND_MSG specificando uri e tipo del file
                                Intent i = new Intent(getApplicationContext(), CommunicationService.class);
                                i.setAction(CommunicationService.ACTION_SEND_MSG);
                                i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.AUDIO);
                                i.putExtra(CommunicationService.EXTRAS_CONTENT_URI, selectedAudioUri);
                                startService(i);
                            }
                        }
                    }
                }
            });


    //Registro il callback da chiamare quando riceverò un risultato dalla richiesta di selezionare un a foto o video
    ActivityResultLauncher<Intent> pickPhotoOrVideoActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent intent = result.getData();
                        if (intent != null) {
                            Uri selectedMediaUri = intent.getData();
                            if (selectedMediaUri != null) {
                                //invio al service un intent ACTION_SEND_MSG specificando uri e tipo del file
                                if (getMIMEType(selectedMediaUri).contains("image")) {
                                    Intent i = new Intent(getApplicationContext(), CommunicationService.class);
                                    i.setAction(CommunicationService.ACTION_SEND_MSG);
                                    i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.IMAGE);
                                    i.putExtra(CommunicationService.EXTRAS_CONTENT_URI, selectedMediaUri);
                                    startService(i);
                                } else if (getMIMEType(selectedMediaUri).contains("video")) {
                                    Intent i = new Intent(getApplicationContext(), CommunicationService.class);
                                    i.setAction(CommunicationService.ACTION_SEND_MSG);
                                    i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.VIDEO);
                                    i.putExtra(CommunicationService.EXTRAS_CONTENT_URI, selectedMediaUri);
                                    startService(i);
                                }
                            }
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_chat);

        //getting UI element
        sendTxtButton = findViewById(R.id.sendMsgButton);
        sendPhotoButton = findViewById(R.id.sendPhotoVidButton);
        sendRecButton = findViewById(R.id.sendRecButton);
        sendAttachmentButton = findViewById(R.id.sendAttachmentButton);
        sendingProgressBar = findViewById(R.id.sendingProgressBar);
        fileNameTxtView = findViewById(R.id.fileNameTxtView);
        msgEditTextView = findViewById(R.id.msgTxt);
        recChronometer = findViewById(R.id.recChronometer);

        sendTxtButton.setOnClickListener(this);
        sendAttachmentButton.setOnClickListener(this);
        sendPhotoButton.setOnClickListener(this);
        sendRecButton.setOnClickListener(this);

        //imposto tempo impiegato dagli elementi per comparire e scomparire
        ConstraintLayout layout = findViewById(R.id.chatLayout);
        layout.getLayoutTransition().setDuration(100);

        //cambio ciò che mostro a seconda se l'utente ha inserito testo o meno
        msgEditTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length() == 0) {
                    //msgEditTextView "vuota"

                    //imposto margine destro della msgEditTextView all'inizio del sendAttachmentButton
                    ConstraintLayout.LayoutParams editTextParams = (ConstraintLayout.LayoutParams) msgEditTextView.getLayoutParams();
                    editTextParams.endToStart = R.id.sendAttachmentButton;
                    msgEditTextView.setLayoutParams(editTextParams);

                    sendTxtButton.setVisibility(View.GONE);
                    sendAttachmentButton.setVisibility(View.VISIBLE);
                    sendPhotoButton.setVisibility(View.VISIBLE);
                    sendRecButton.setVisibility(View.VISIBLE);
                } else {
                    //msgEditTextView "con testo"

                    //imposto margine destro della msgEditTextView all'inizio del sendMsgButton
                    ConstraintLayout.LayoutParams editTextParams = (ConstraintLayout.LayoutParams) msgEditTextView.getLayoutParams();
                    editTextParams.endToStart = R.id.sendMsgButton;
                    msgEditTextView.setLayoutParams(editTextParams);

                    sendTxtButton.setVisibility(View.VISIBLE);
                    sendAttachmentButton.setVisibility(View.GONE);
                    sendPhotoButton.setVisibility(View.GONE);
                    sendRecButton.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {

            }

        });

        msgRV = findViewById(R.id.msgRV);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        //ultimi messaggi devono comparire in basso
        linearLayoutManager.setReverseLayout(true);
        msgRV.setLayoutManager(linearLayoutManager);
        msgRV.setHasFixedSize(true);

        messageListAdapter = new MessageListAdapter(this, this, this);
        msgRV.setAdapter(messageListAdapter);

        connectionUpdateReceive = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case COMMUNICATION_CLOSED:
                        //mostro dialog di interruzione comunicazione
                        LoadingFragmentDialog loadingFragmentDialog = (LoadingFragmentDialog) getSupportFragmentManager().findFragmentByTag(LoadingFragmentDialog.FRAGMENT_TAG);
                        if (loadingFragmentDialog == null) {
                            new AlertDisconnectionDialog().show(getSupportFragmentManager(), AlertDisconnectionDialog.FRAGMENT_TAG);
                        }
                        break;
                    case COMMUNICATION_OK:
                        //il service era attivo --> elimino il dialog di caricamento e imposto UI
                        loadingFragmentDialog = (LoadingFragmentDialog) getSupportFragmentManager().findFragmentByTag(LoadingFragmentDialog.FRAGMENT_TAG);
                        if (loadingFragmentDialog != null) {
                            loadingFragmentDialog.dismiss();
                        }
                        //imposto titolo con il nome del device con cui comunico
                        String otherDeviceName = intent.getStringExtra(EXTRAS_OTHER_SIDE_DEVICE_NAME);
                        ChatActivity.this.setTitle(otherDeviceName);
                        // ottengo un ViewModel che associo al ciclo di vita dell'activity in modo da far persistere i dati in seguito ai "cambiamenti" di stato
                        // e di configurazione dell'activity
                        chatActivityViewModel = new ViewModelProvider(ChatActivity.this).get(ChatActivityViewModel.class);
                        chatActivityViewModel.setDeviceName(otherDeviceName);
                        //osservo i cambiamenti in modo da aggiornare l'adapter in caso di cambiamenti alla lista di messaggi (aggiunta o rimozione di messaggi)
                        chatActivityViewModel.getAllMessage().observe(ChatActivity.this, new Observer<List<Message>>() {
                            @Override
                            public void onChanged(List<Message> messages) {
                                //aggiorniamo l'adapter e di conseguenza la recyclerView
                                messageListAdapter.submitList(messages);
                            }
                        });
                        //se ero precedentemente in attesa del completamento dell'invio di un file, reimposto la UI
                        resetSendingState();
                        break;
                    case SENDING_UPDATE:
                        //il service mi sta aggiornando sullo stato di invio di un file
                        int progress = intent.getIntExtra(EXTRAS_PROGRESS_STATE, 100);
                        String fileName = intent.getStringExtra(EXTRAS_FILE_NAME);
                        if (progress == 100) {
                            //adesso puoi inviare messaggi: riattiva tutti gli elementi che permettono di inviare messaggi
                            resetSendingState();
                        } else {
                            setSendingState(progress, fileName);
                        }
                        break;
                }
            }
        };
        handler = new Handler();
    }


    //descrizione: imposta tutti gli elementi visuali che consentono all'utente di apprendere lo stato dell'invio del file
    private void setSendingState(int progress, String fileName) {
        msgEditTextView.setVisibility(View.GONE);
        sendAttachmentButton.setVisibility(View.GONE);
        sendPhotoButton.setVisibility(View.GONE);
        sendRecButton.setVisibility(View.GONE);
        sendingProgressBar.setVisibility(View.VISIBLE);
        fileNameTxtView.setVisibility(View.VISIBLE);
        fileNameTxtView.setText(getString(R.string.sending_file, fileName));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sendingProgressBar.setProgress(progress, true);
        } else {
            sendingProgressBar.setProgress(progress);
        }

        ConstraintLayout.LayoutParams rvLayoutParams = (ConstraintLayout.LayoutParams) msgRV.getLayoutParams();
        rvLayoutParams.bottomToTop = R.id.fileNameTxtView;
        msgRV.setLayoutParams(rvLayoutParams);
    }

    //resetta lo stato di invio file e riattiva tutti gli elementi UI che consentono all'utente di inviare messaggi
    private void resetSendingState() {
        msgEditTextView.setVisibility(View.VISIBLE);
        sendAttachmentButton.setVisibility(View.VISIBLE);
        sendPhotoButton.setVisibility(View.VISIBLE);
        sendRecButton.setVisibility(View.VISIBLE);
        fileNameTxtView.setText("");
        sendingProgressBar.setVisibility(View.GONE);
        fileNameTxtView.setVisibility(View.GONE);

        ConstraintLayout.LayoutParams rvLayoutParams = (ConstraintLayout.LayoutParams) msgRV.getLayoutParams();
        rvLayoutParams.bottomToTop = R.id.msgTxt;
        msgRV.setLayoutParams(rvLayoutParams);
    }

    //descrizione: metodo di MessageListAdapter.ScrollRVListener che consente di far scrollare la recycler view all'ultimo messaggio ricevuto
    @Override
    public void scrollRV() {
        msgRV.scrollToPosition(0);
    }


    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(COMMUNICATION_OK);
        intentFilter.addAction(COMMUNICATION_CLOSED);
        intentFilter.addAction(SENDING_UPDATE);
        //registriamo dinamicamente il receiver per ottenere informazioni dal service
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(connectionUpdateReceive, intentFilter);

        //inviamo un intent per essere aggionrnati sullo stato del service
        checkCommunicationService();

        //attendo che il service avvii la comunicaizone o mi informi sul suo stato se già avviata
        LoadingFragmentDialog loadingFragmentDialog = (LoadingFragmentDialog) getSupportFragmentManager().findFragmentByTag(LoadingFragmentDialog.FRAGMENT_TAG);
        if (loadingFragmentDialog != null) {
            Log.d(MainActivity.TAG, "oldFragmentDialog");
            loadingFragmentDialog.show(getSupportFragmentManager(), LoadingFragmentDialog.FRAGMENT_TAG);
        } else {
            Log.d(MainActivity.TAG, "newFragmentDialog");
            new LoadingFragmentDialog().show(getSupportFragmentManager(), LoadingFragmentDialog.FRAGMENT_TAG);
        }
    }

    //descrizione: invia un intent per essere aggiornato sullo stato del service
    public void checkCommunicationService() {
        Intent i = new Intent(getApplicationContext(), CommunicationService.class);
        i.setAction(CommunicationService.ACTION_CHECK_COMMUNICATION);
        startService(i);
    }


    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(connectionUpdateReceive);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //se avevo avviato i service in modalità foreground (disponibile da Android 8) notifico il service affinchè mi aggiorni se
            //ricevo nuovi messaggi
            Intent i = new Intent(getApplicationContext(), CommunicationService.class);
            i.setAction(CommunicationService.RESTART_COUNTING_NEWMSG);
            startService(i);
        }
    }

    @Override
    public void onClick(View view) {
        if (view == sendTxtButton) {
            //INVIO MESSAGGIO DI TESTO
            String msgTxt = msgEditTextView.getText().toString();
            msgEditTextView.setText("");
            if (!msgTxt.trim().isEmpty()) {
                Intent i = new Intent(getApplicationContext(), CommunicationService.class);
                i.setAction(CommunicationService.ACTION_SEND_MSG);
                i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.TXT);
                i.putExtra(CommunicationService.EXTRAS_TEXT_CONTENT, msgTxt);
                startService(i);
            }
        } else if (view == sendPhotoButton) {
            // Creiamo il file dove salveremo la foto
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(MainActivity.TAG, ex.getMessage());
            }
            //Continuiamo solo se il file è stato correttamente creato
            if (photoFile != null) {
                currentPhotoURI = FileProvider.getUriForFile(this,
                        "it.unipi.m598992.DirectChat.fileprovider",
                        photoFile);
                try {
                    takePhotoActivityLauncher.launch(currentPhotoURI);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "You don't have app to take a picture", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (view == sendAttachmentButton) {
            //mostro un Popup menu per decidere cosa allegare
            PopupMenu popup = new PopupMenu(this, sendAttachmentButton);
            popup.getMenuInflater().inflate(R.menu.popup_attachment_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();
        } else if (view == sendRecButton) {
            //controllo con il solito meccanismo i permessi
            if (checkRecPermission()) {
                if (isRecording) {
                    //stavo registrando: ho cliccato il bottone per stoppare registrazione
                    stopRecording();
                    Intent i = new Intent(getApplicationContext(), CommunicationService.class);
                    i.setAction(CommunicationService.ACTION_SEND_MSG);
                    i.putExtra(CommunicationService.EXTRAS_MSG_TYPE, Message.REC);
                    //ottengo un URI tramite il FileProvider
                    Uri currentRecURI = FileProvider.getUriForFile(this,
                            "it.unipi.m598992.DirectChat.fileprovider",
                            new File(currentRecordingPath));
                    i.putExtra(CommunicationService.EXTRAS_CONTENT_URI, currentRecURI);
                    startService(i);
                } else {
                    startRecording();
                }
            }
        }
    }


    /***** GESTIONE PERMESSO REGISTRAZIONI *****/ /*(SEMPRE SOLITO MECCANISMO)*/
    private boolean checkRecPermission() {
        int access_mic_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (access_mic_permission == PERMISSION_GRANTED) {
            return true;
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            showRequestDialog(false);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORDING_PERMISSION_REQUEST);
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean neverAskAgainChecked;
        if (requestCode == RECORDING_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.can_record, Toast.LENGTH_SHORT).show();
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
                    .setMessage(R.string.need_rec_permission_perm_denied)
                    .setNegativeButton(R.string.need_to_go_to_settings, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    }).create();
        } else {
            //utente non ha cliccato Never ask again --> mostra dialog che consente all'utente di richiedere permessi
            dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.need_rec_permission)
                    .setPositiveButton(R.string.retry_permission_no_necessary, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(ChatActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORDING_PERMISSION_REQUEST);
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

    //descrizione: creazione del menu associato all'activity
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_activity_menu, menu);
        return true;
    }

    //descrizione: gestione dei click agli item del menu dell'activity
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.disconnect) {
            //informo la MainActivity di interromepre comunicazione
            //stoppo il service
            Intent i = new Intent(getApplicationContext(), CommunicationService.class);
            i.setAction(CommunicationService.ACTION_STOP_COMMUNICATION);
            startService(i);
            setResult(WIFI_DIRECT_DISCONNECTION_REQUIRED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    //descrizione: metodo di PopupMenu.OnMenuItemClickListener per gestione dei click agli elementi del popup menu associato al sendAttachmentButton
    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        Intent pickIntent;
        if (menuItem.getItemId() == R.id.pickFromGallery) {
            pickIntent = new Intent(Intent.ACTION_PICK);
            pickIntent.setType("image/* video/*");
            pickPhotoOrVideoActivityLauncher.launch(pickIntent);
            return true;
        } else if (menuItem.getItemId() == R.id.pickAudio) {
            pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
            pickIntent.setType("audio/*");
            pickAudioActivityLauncher.launch(pickIntent);
        } else if (menuItem.getItemId() == R.id.pickGeneralFile) {
            pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
            pickIntent.setType("*/*");
            pickFileActivityLauncher.launch(pickIntent);
            return true;
        }
        return false;
    }

    //descrizione: crea e ritorna un file nella directory esterna PICTURES associata all'app. Se la creazione non va a buon fine ritorna null.
    private File createImageFile() throws IOException {
        // Create an image file name
        if(canWriteToExternal()){
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_.jpg";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File image = new File(storageDir, imageFileName);
            if (image.createNewFile()) {
                return image;
            }
        }
        return null;
    }

    //descrizione: crea e ritorna un path relativo ad una registrazione nella directory esterna RECORDINGS/MUSIC associata all'app.
    private String createRecordingFilePath() {
        File storageDir;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            storageDir = getExternalFilesDir(Environment.DIRECTORY_RECORDINGS);
        } else {
            storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new Date());
        String recFileName = "REC_" + timeStamp + "_.3gpp";
        return storageDir.getAbsolutePath() + "/" + recFileName;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            //in questo caso stoppo audio e rilascio media player --> altrimenti lo farò nella onSavedInstanceState()
            //dopo aver salvato i valori che mi servono per riprendere la riproduzione dove era stata interrotta
            if (mediaPlayer != null) {
                Log.d(MainActivity.TAG, "OOOOOOO");
                stopPlayingAndReset();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
        if (isRecording) {
            stopRecording();
        }
        if (mediaRecorder != null) {
            //rilascio recorder
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (handler != null)
            //elimino l'eventuale handler che gestisce la seekBar dell'audio in riproduzione
            handler.removeCallbacksAndMessages(null);
    }


    //***** METODI PER LA GESTIONE DEL MEDIA PLAYER *****//
    @Override
    public void onPlayPauseButtonListener(Message message, MessageListAdapter.Recording_AudioMessageVH VH) {
        if (isPlaying && message.equals(messageListAdapter.getMsgAudioBeingPlaying())) {
            //stavo suonando e ho cliccato pause sulla musica che stava suonando
            pausePlaying(message);
        } else if (isPlaying) {
            //stavo suonando e ho cliccato play su un altro audio
            stopPlayingAndReset();
            startPlaying(message, VH);
        } else if (isPaused && message.equals(messageListAdapter.getMsgAudioBeingPlaying())) {
            //voglio riprendere un audio in pausa
            resumePlaying(message, VH);
        } else if (isPaused) {
            //un audio era in pausa e ne ho cliccato un altro
            stopPlayingAndReset();
            startPlaying(message, VH);
        } else {
            //voglio far partire un nuovo audio e il music player nè suonava nè era in pausa
            startPlaying(message, VH);
        }
    }


    private void resumePlaying(Message message, MessageListAdapter.Recording_AudioMessageVH VH) {
        //informo adapter del messaggio in riproduzione
        messageListAdapter.setMsgAudioBeingPlaying(message);
        //riparto da dove mi ero fermato o dove ha selezionato utente
        mediaPlayer.seekTo(VH.getAudioSeekBar().getProgress());
        mediaPlayer.start();
        isPlaying = true;
        isPaused = false;
    }

    private void startPlaying(Message message, MessageListAdapter.Recording_AudioMessageVH VH) {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        Uri uri = Uri.parse(message.getContentUri());
        try {
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.prepareAsync();
            //informo adapter del messaggio in riproduzione
            messageListAdapter.setMsgAudioBeingPlaying(message);
            if (VH != null) {
                //salvo progresso della seekBar per far partire audio dove ha selezionato utente
                seekBarProgress = VH.getAudioSeekBar().getProgress();
            }
            isPlaying = true;
        } catch (IOException e) {
            isPlaying = false;
            isPaused = false;
            Log.e(MainActivity.TAG, e.getMessage());
        }
    }

    private void stopPlayingAndReset() {
        //elimino task di gestione della seekBar dell'audio in riproduzione
        handler.removeCallbacksAndMessages(null);
        //informo adapter che non vi è più alcun messaggio in riproduzione
        messageListAdapter.setMsgAudioBeingPlaying(null);
        mediaPlayer.stop();
        mediaPlayer.reset();
        isPlaying = false;
        isPaused = false;
    }

    private void pausePlaying(Message message) {
        //informo adapter del messaggio in pausa
        messageListAdapter.setMsgAudioBeingPlaying(message);
        mediaPlayer.pause();
        isPlaying = false;
        isPaused = true;
    }

    //descrizione: metodo chiamato da MediaPlayer.OnPreparedListener per indicare che il media player è pronto per riprodurre audio
    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        //sfrutto l'input che ha dato l'utente nel settare progress bar per far partire media player dal punto selezionato
        mediaPlayer.seekTo(seekBarProgress);
        mediaPlayer.start();
    }

    //descrizione: metodo chiamato da MediaPlayer.OnCompletionListener per indicare che un audio in riproduzione è terminato
    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        stopPlayingAndReset();
    }

    //descrizione: metodo di MessageListAdapter.PlayPauseButtonListener per gestire input dell'utente sulla seekBar durante la riproduzione di un audio
    //(se mentre audio è in riproduzione l'unìtente muove cursore seek>Bar, il mediaPlayer deve spostarsi di conseguenza)
    @Override
    public void seekMediaPlayerToPos(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    //descrizione: metodo di MessageListAdapter.PlayPauseButtonListener per ottenere il progresso/posizione del media player nella riproduzione
    @Override
    public int getMediaPlayerPos() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    //descrizione: metodo di MessageListAdapter.PlayPauseButtonListener per far partire handler che sottomette messaggi per aggiornare seekBar in relazione al
    //proseguire dell'audio in riproduzione
    @Override
    public void startSeekBarHandler(SeekBar audioSeekBar) {
        Log.d(MainActivity.TAG, "Start handling seek bar");
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    audioSeekBar.setProgress(mediaPlayer.getCurrentPosition());
                    //risottometto lo stesso task/messaggio fra 100 millisec
                    handler.postDelayed(this, 200);
                }
            }
        });
    }

    //descrizione: metodo di MessageListAdapter.PlayPauseButtonListener per far terminare handler che sottomette messaggi
    @Override
    public void stopSeekBarHandler() {
        handler.removeCallbacksAndMessages(null);
    }
    //*****          *****//

    //***** METODI PER LA GESTIONE DEL MEDIA RECORDER *****//
    //descrizione: metodo che inizializza, configura il recorder e fa partire la registrazione
    private void startRecording() {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        currentRecordingPath = createRecordingFilePath();
        mediaRecorder.setOutputFile(currentRecordingPath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        sendRecButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red));
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(MainActivity.TAG, "prepare() failed + " + e.getMessage());
        }
        mediaRecorder.start();
        isRecording = true;
        //evito che l'utente ruoti il cellulare mentre si registra
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        recChronometer.setVisibility(View.VISIBLE);
        msgEditTextView.setVisibility(View.INVISIBLE);
        sendAttachmentButton.setVisibility(View.INVISIBLE);
        sendPhotoButton.setVisibility(View.INVISIBLE);
        recChronometer.setBase(SystemClock.elapsedRealtime());
        recChronometer.start();
    }

    //descrizione: metodo per fermare il recording
    private void stopRecording() {
        //resetto la possibilità di rotazione del cellulare una volta terminata la registrazione
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        mediaRecorder.stop();
        isRecording = false;
        sendRecButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorSecondaryDark));
        recChronometer.setVisibility(View.GONE);
        msgEditTextView.setVisibility(View.VISIBLE);
        sendAttachmentButton.setVisibility(View.VISIBLE);
        sendPhotoButton.setVisibility(View.VISIBLE);
        recChronometer.stop();
    }
    //*****          *****//


    //descrizione: metodo di MessageListAdapter.SelectMessageCVListener per gestire il click sulle cardView associate ai messaggi
    @Override
    public void onCVMessageClick(Message message) {
        if (myCab != null) {
            //è attiva l'actionMode: un click su una chat significa selezione o deselezione dei messaggi
            if (messageListAdapter.isThisMessageSelected(message)) {
                //il messaggio era selezionato --> lo deseleziono
                messageListAdapter.removeSelectedMessage(message);
            } else {
                //il messaggio non era selezionato --> lo seleziono
                messageListAdapter.addSelectedMessage(message);
            }
            int numberOfItemSelected = messageListAdapter.getNumberOfSelectedMessage();
            if (numberOfItemSelected == 0) {
                myCab.finish();
            } else if (numberOfItemSelected == 1) {
                myCab.setTitle(getString(R.string.one_message_selected));
                myCab.invalidate();
            } else {
                myCab.setTitle(getString(R.string.more_messages_selected, numberOfItemSelected));
                myCab.invalidate();
            }
            return;
        }
        if (message.getType() == Message.IMAGE) {
            //ho cliccato su un immagine --> lancio un intent per aprirla
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            Uri msgUri = Uri.parse(message.getContentUri());
            //fornisco i permessi temporanei in lettura e in scrittura
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setDataAndType(msgUri, "image/*");
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show();
            }
        } else if (message.getType() == Message.VIDEO) {
            //ho cliccato su un video --> lancio un intent per aprirlo
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            Uri msgUri = Uri.parse(message.getContentUri());
            //fornisco i permessi temporanei in lettura e in scrittura
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setDataAndType(msgUri, "video/*");
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show();
            }
        } else if (message.getType() == Message.GENERAL_FILE) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            Uri msgUri = Uri.parse(message.getContentUri());
            //fornisco i permessi temporanei in lettura e in scrittura
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setData(msgUri);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_app_available, Toast.LENGTH_SHORT).show();
            }
        }
    }

    //descrizione: metodo di MessageListAdapter.SelectMessageCVListener per gestire il long click sulle cardView associate ai messaggi
    @Override
    public void onCVMessageLongClick(Message message) {
        if (myCab != null)
            //action mode già attiva
            return;
        //avvio action mode
        myCab = startActionMode(cab);
        messageListAdapter.addSelectedMessage(message);
        myCab.setTitle(getString(R.string.one_message_selected));
        myCab.invalidate();
    }


    private final ActionMode.Callback cab = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            MenuInflater inflater = actionMode.getMenuInflater();
            inflater.inflate(R.menu.msg_long_click_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            if (messageListAdapter.getNumberOfSelectedMessage() == 1 && messageListAdapter.getCopyCurrectSelectedMessage().get(0).getType() == Message.TXT) {
                //disabilito item di condivisione messaggio
                MenuItem item = menu.findItem(R.id.shareContent);
                item.setVisible(false);
                //ho selezionato un solo messaggio di testo --> posso copiarne il contenuto --> attivo item per copiare su appunti
                item = menu.findItem(R.id.copyText);
                item.setVisible(true);
                return true;
            } else if (messageListAdapter.getNumberOfSelectedMessage() == 1) {
                //ho selezionato un file --> posso condividerlo --> abilito item per la condivisione
                MenuItem item = menu.findItem(R.id.shareContent);
                item.setVisible(true);
                //disabilito item di copia su appunti
                item = menu.findItem(R.id.copyText);
                item.setVisible(false);
                return true;
            } else {
                //più messaggi selezionati --> posso solamente cancellarli
                MenuItem item = menu.findItem(R.id.shareContent);
                item.setVisible(false);
                item = menu.findItem(R.id.copyText);
                item.setVisible(false);
                return true;
            }
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.deleteMsgItem) {
                //elimino messaggi dal database
                ArrayList<Message> messagesToDelete = messageListAdapter.getCopyCurrectSelectedMessage();
                chatActivityViewModel.deleteMessages(messagesToDelete);
                if (messagesToDelete.contains(messageListAdapter.getMsgAudioBeingPlaying())) {
                    //se stavo riproducendo un audio che sto cancellando fermo la riproduzione
                    if (isPlaying || isPaused) {
                        stopPlayingAndReset();
                    }
                }
                //richiedo al workManager un task per eliminare i file dal fileSystem
                enqueueDeleteFilesTask(messagesToDelete);
                actionMode.finish();
                return true;
            } else if (itemId == R.id.copyText) {
                //copio messaggio di testo negli appunti
                setClipboard(getApplicationContext(), messageListAdapter.getCopyCurrectSelectedMessage().get(0).getTextMsg());
                Toast.makeText(ChatActivity.this, R.string.text_copied_clipboard, Toast.LENGTH_SHORT).show();
                actionMode.finish();
                return true;
            } else if (itemId == R.id.shareContent) {
                //lancio un intent di condivisione del messaggio
                Uri contentUri = Uri.parse(messageListAdapter.getCopyCurrectSelectedMessage().get(0).getContentUri());
                ShareCompat.IntentBuilder intentBuilder = new ShareCompat.IntentBuilder(ChatActivity.this);
                //ShareCompat.IntentBuilder implicitamente consente permessi di reading e writing per le uri associate al nostro FileProvider
                intentBuilder.setStream(contentUri).setText(getString(R.string.sharedFrom)).setType(getMIMEType(contentUri)).startChooser();
                actionMode.finish();
                return true;
            }
            return false;

        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            //notifico adapter per rimuovere tutti i messaggi selezionati
            messageListAdapter.removeAllSelectedMessage();
            myCab = null;
        }
    };

    //descrizione: copia arg text negli appunti
    private void setClipboard(Context context, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", text);
        clipboard.setPrimaryClip(clip);
    }

    //descrizione: mette in coda tramite il WorkManager una richiesta di cancellazione di messaggi dal file system
    private void enqueueDeleteFilesTask(ArrayList<Message> messagesToDelete) {
        ArrayList<String> messageUriToDelete = new ArrayList<>();
        for (Message message : messagesToDelete) {
            if (message.getType() != Message.TXT && !message.getContentUri().isEmpty())
                messageUriToDelete.add(message.getContentUri());
        }
        if (!messagesToDelete.isEmpty()) {
            Data.Builder data = new Data.Builder();
            data.putStringArray(DeleteFilesWorker.FILES_TO_DELETE, messageUriToDelete.toArray(new String[0]));
            OneTimeWorkRequest deleteWorkRequest = new OneTimeWorkRequest.Builder(DeleteFilesWorker.class)
                    .setInputData(data.build())
                    .build();
            //metto in coda la richiesta
            WorkManager.getInstance(getApplicationContext()).enqueue(deleteWorkRequest);
        }
    }

    //descrizione: metodo che ritorna il mime type di un file a partire dalla sua Uri
    private String getMIMEType(Uri fileUri) {
        DocumentFile file = DocumentFile.fromSingleUri(getApplicationContext(), fileUri);
        String MIMEType;
        if (file != null && file.getType() != null) {
            MIMEType = file.getType();
            Log.d(MainActivity.TAG, "1: " + MIMEType);
        } else {
            MIMEType = getContentResolver().getType(fileUri);
            Log.d(MainActivity.TAG, "2: " + MIMEType);
        }
        return MIMEType;
    }

    //DialogFragment da mostrare in seguito ad una disconnessione
    public static class AlertDisconnectionDialog extends DialogFragment {
        public static String FRAGMENT_TAG = "alert_disconnect_dialog";

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Dialog dialog = new AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.communication_interrupted))
                    .setPositiveButton(getString(R.string.close_communication), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (getActivity() != null) {
                                getActivity().setResult(WIFI_DIRECT_DISCONNECTION_REQUIRED);
                                getActivity().finish();
                            }
                        }
                    })
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Message message;
        if (mediaPlayer != null && isPlaying && (message = messageListAdapter.getMsgAudioBeingPlaying()) != null) {
            //salvo i valori relatvi all'audio in riproduzione per poter riavviare il media player in caso di
            //cambio di orientamento del device
            outState.putParcelable("audioBeingPlayed", message);
            outState.putInt("media_player_progress", mediaPlayer.getCurrentPosition());
        }
        if(mediaPlayer != null){
            stopPlayingAndReset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        //salvo la variabile locale nel caso in cui l'utente ruoti il telefono mentre scatta la foto
        outState.putParcelable("currentPhotoUri", currentPhotoURI);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Message audioBeingPlayed = savedInstanceState.getParcelable("audioBeingPlayed");
        int mediaPlayerProgress = savedInstanceState.getInt("media_player_progress", -1);
        if (audioBeingPlayed != null && mediaPlayerProgress != -1) {
            //un audio era in riproduzione: lo riavvio dal punto in cui era stato interrotto
            seekBarProgress = mediaPlayerProgress;
            startPlaying(audioBeingPlayed, null);
        }
        //ripristino la variabile locale se l'utente aveva ruotato il cellulare dopo aver richiesto di scattare una foto
        currentPhotoURI = savedInstanceState.getParcelable("currentPhotoUri");
    }

    //descrizione: ritorna true se posso scrivere nella memoria esterna
    private boolean canWriteToExternal(){
        return Environment.isExternalStorageEmulated() || Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }
}