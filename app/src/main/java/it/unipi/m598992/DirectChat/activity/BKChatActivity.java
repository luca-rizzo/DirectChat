package it.unipi.m598992.DirectChat.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ShareCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import it.unipi.m598992.DirectChat.worker.DeleteFilesWorker;
import it.unipi.m598992.DirectChat.R;
import it.unipi.m598992.DirectChat.RoomDB.entity.Message;
import it.unipi.m598992.DirectChat.adapter.MessageListAdapter;
import it.unipi.m598992.DirectChat.viewModel.BKChatActivityViewModel;

public class BKChatActivity extends AppCompatActivity implements MessageListAdapter.SelectMessageCVListener, MessageListAdapter.PlayPauseButtonListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MessageListAdapter.ScrollRVListener {
    public static final String EXTRAS_OTHER_DEVICE_NAME = "it.unipi.m598992.directChat.EXTRAS_DEVICE_NAME";


    private ActionMode myCab = null;

    private RecyclerView bkMsgRV;
    private TextView selectedTypeTextView;

    private BKChatActivityViewModel bkChatActivityViewModel;
    private MessageListAdapter messageListAdapter;


    private MediaPlayer mediaPlayer = null;
    //costante booleana che indica se il media player è nello stato Started
    private boolean isPlaying = false;
    //costante booleana che indica se il media player è nello stato Paused
    private boolean isPaused = false;
    //valore che indica posizione seek bar nell'audio che vogliamo riprodurre
    private int seekBarProgress = 0;
    //handler sfruttato per sottomettere nella MessageQueue del Thread UI task di aggiornamento della seekBar del brano in riproduzione
    private Handler handler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bkchat);
        String userName = getIntent().getStringExtra(EXTRAS_OTHER_DEVICE_NAME);
        setTitle(userName);
        bkMsgRV = findViewById(R.id.bkMsgRV);
        selectedTypeTextView = findViewById(R.id.selectedType);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setReverseLayout(true);
        bkMsgRV.setLayoutManager(linearLayoutManager);
        bkMsgRV.setHasFixedSize(true);
        messageListAdapter = new MessageListAdapter(this, this, this);
        bkMsgRV.setAdapter(messageListAdapter);
        // ottengo un ViewModel che associo al ciclo di vita dell'activity in modo da far persistere i dati in seguito ai "cambiamenti" di stato
        // e di configurazione dell'activity
        bkChatActivityViewModel = new ViewModelProvider(this).get(BKChatActivityViewModel.class);
        bkChatActivityViewModel.setDeviceName(userName);
        //osservo i cambiamenti in modo da aggiornare l'adapter in caso di cambiamenti alla lista di messaggi (aggiunta o rimozione di messaggi)
        bkChatActivityViewModel.getAllMessage().observe(this, new Observer<List<Message>>() {
            @Override
            public void onChanged(List<Message> messages) {
                //aggiorniamo l'adapter e di conseguenza la recyclerView
                messageListAdapter.submitList(messages);
            }
        });
        setSelectedTypeTextView();
        handler = new Handler();
    }

    //descrizione: setto la textView che mostra i tipi di messaggi che sto visualizzando
    private void setSelectedTypeTextView() {
        selectedTypeTextView.setSelected(true);
        HashSet<Integer> selectedMessageType = bkChatActivityViewModel.getCopySelectedMessageType();
        if (selectedMessageType.size() == 6) {
            selectedTypeTextView.setText(R.string.show_all_messages);
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            for (Integer type : selectedMessageType) {
                switch (type) {
                    case Message.TXT:
                        stringBuilder.append(getString(R.string.text_messages)).append(", ");
                        break;
                    case Message.AUDIO:
                        stringBuilder.append(getString(R.string.audio_messages)).append(", ");
                        break;
                    case Message.VIDEO:
                        stringBuilder.append(getString(R.string.video_messages)).append(", ");
                        break;
                    case Message.REC:
                        stringBuilder.append(getString(R.string.rec_messages)).append(", ");
                        break;
                    case Message.IMAGE:
                        stringBuilder.append(getString(R.string.image_messages)).append(", ");
                        break;
                    case Message.GENERAL_FILE:
                        stringBuilder.append(getString(R.string.general_file_messages)).append(", ");
                        break;
                }
            }
            String selectedType = stringBuilder.toString();
            selectedType = selectedType.substring(0, selectedType.length() - 2);
            selectedTypeTextView.setText(getString(R.string.show_type_messages, selectedType));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handler != null)
            handler.removeCallbacksAndMessages(null);
        if (!isChangingConfigurations()) {
            //in questo caso stoppo audio e rilascio media player --> altrimenti lo farò nella onSavedInstanceState()
            //dopo aver salvato i valori che mi servono per riprendere la riproduzione dove era stata interrotta
            if (mediaPlayer != null) {
                stopPlayingAndReset();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
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
            //salvo progresso della seekBar per far partire audio dove ha selezionato utente
            if (VH != null) {
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

    //descrizione: creazione del menu associato all'activity
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bkchat_activity_menu, menu);
        return true;
    }

    //descrizione: gestione dei click agli item del menu dell'activity
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.showOnlyItem) {
            new ChooseTypeMsgDialog().show(getSupportFragmentManager(), ChooseTypeMsgDialog.FRAGMENT_TAG);
        }
        return super.onOptionsItemSelected(item);

    }

    //descrizione: metodo di MessageListAdapter.ScrollRVListener che consente di far scrollare la recycler view all'ultimo messaggio ricevuto
    @Override
    public void scrollRV() {
        bkMsgRV.scrollToPosition(0);
    }

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
                bkChatActivityViewModel.deleteMessages(messagesToDelete);
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
                Toast.makeText(BKChatActivity.this, R.string.text_copied_clipboard, Toast.LENGTH_SHORT).show();
                actionMode.finish();
                return true;
            } else if (itemId == R.id.shareContent) {
                //lancio un intent di condivisione del messaggio
                Uri contentUri = Uri.parse(messageListAdapter.getCopyCurrectSelectedMessage().get(0).getContentUri());
                ShareCompat.IntentBuilder intentBuilder = new ShareCompat.IntentBuilder(BKChatActivity.this);
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
            if (message.getType() != Message.TXT)
                messageUriToDelete.add(message.getContentUri());
        }
        Data.Builder data = new Data.Builder();
        data.putStringArray(DeleteFilesWorker.FILES_TO_DELETE, messageUriToDelete.toArray(new String[0]));
        OneTimeWorkRequest deleteWorkRequest = new OneTimeWorkRequest.Builder(DeleteFilesWorker.class)
                .setInputData(data.build())
                .build();
        //metto in coda la richiesta
        WorkManager.getInstance(getApplicationContext()).enqueue(deleteWorkRequest);
    }

    private void setSelectedMessageType(HashSet<Integer> selectedMessageType) {
        //smetto di osservare la precedente lista
        bkChatActivityViewModel.getAllMessage().removeObservers(this);
        //imosto nel ViewModel il tipo di messaggi che voglio visualizzare
        bkChatActivityViewModel.setSelectedMessageType(selectedMessageType);

        //osservo la nuova lista
        bkChatActivityViewModel.getAllMessage().observe(this, new Observer<List<Message>>() {
            @Override
            public void onChanged(List<Message> messages) {
                //modifico recycler view in seguito ad un cambiamento alla lista che osservo
                messageListAdapter.submitList(messages);
            }
        });
        //aggiorno la textView
        setSelectedTypeTextView();
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

    //DialogFragment da mostrare nel caso in cui l'utente richiede di mostrare solo alcuni messaggi
    public static class ChooseTypeMsgDialog extends DialogFragment {
        public final static String FRAGMENT_TAG = "choose_media_type_dialog";
        private final static String TYPE_SELECTED = "Type_selected";

        boolean[] itemBooleanArray = new boolean[]{false, false, false, false, false, false, false};

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            String[] typeArray = {getString(R.string.text_messages), getString(R.string.image_messages), getString(R.string.rec_messages), getString(R.string.video_messages), getString(R.string.audio_messages), getString(R.string.general_file_messages), getString(R.string.all_messages)};
            //se l'utente aveva selezionato dei tipi e il dialog è stato ricreato ripristino i tipi selezionati
            if (savedInstanceState != null) {
                itemBooleanArray = savedInstanceState.getBooleanArray(TYPE_SELECTED);
            }
            return new AlertDialog.Builder(requireContext()).setTitle(R.string.chose_msg_type).setMultiChoiceItems(typeArray, itemBooleanArray, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int whichButton, boolean isChecked) {
                    if (isChecked) {
                        if (whichButton == 6) {
                            //ho selezionato item "Tutti i messaggi" --> seleziono tutte le altre checkbox
                            AlertDialog dialog = (AlertDialog) dialogInterface;
                            ListView v = dialog.getListView();
                            for (int i = 0; i < 6; i++) {
                                v.setItemChecked(i, true);
                                itemBooleanArray[i] = true;
                            }
                        }
                    } else {
                        AlertDialog dialog = (AlertDialog) dialogInterface;
                        ListView v = dialog.getListView();
                        if (whichButton == 6) {
                            //ho de-selezionato item "Tutti i messaggi" --> de-seleziono tutte le altre checkbox
                            for (int i = 0; i < 6; i++) {
                                v.setItemChecked(i, false);
                                itemBooleanArray[i] = false;
                            }
                        } else {
                            //nel caso in cui "Tutti i messaggi" era checked lo deseleziono
                            itemBooleanArray[6] = false;
                            v.setItemChecked(6, false);
                        }
                    }
                    itemBooleanArray[whichButton] = isChecked;
                }
            }).setPositiveButton(R.string.ok_choose_type, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    HashSet<Integer> selectedType = new HashSet<>();
                    if (itemBooleanArray[6]) {
                        selectedType.addAll(Arrays.asList(Message.TXT, Message.GENERAL_FILE, Message.VIDEO, Message.IMAGE, Message.REC, Message.AUDIO));
                    } else {
                        //Da 0 a 5
                        for (i = 0; i < 6; i++) {
                            if (itemBooleanArray[i]) {
                                selectedType.add(i);
                            }
                        }
                    }
                    BKChatActivity bkChatActivity = ((BKChatActivity) getActivity());
                    if (bkChatActivity != null && !Arrays.equals(itemBooleanArray, new boolean[]{false, false, false, false, false, false, false}))
                        bkChatActivity.setSelectedMessageType(selectedType);
                    dismiss();
                }
            }).setNegativeButton(R.string.cancel_choose_type, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dismiss();
                }
            }).create();
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBooleanArray(TYPE_SELECTED, itemBooleanArray);
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
        if (mediaPlayer != null) {
            stopPlayingAndReset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
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
    }
}