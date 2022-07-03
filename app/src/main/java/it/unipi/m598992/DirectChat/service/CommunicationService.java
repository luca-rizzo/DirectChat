package it.unipi.m598992.DirectChat.service;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import it.unipi.m598992.DirectChat.activity.ChatActivity;
import it.unipi.m598992.DirectChat.activity.MainActivity;
import it.unipi.m598992.DirectChat.repository.MessageUserRepository;
import it.unipi.m598992.DirectChat.R;
import it.unipi.m598992.DirectChat.RoomDB.entity.Message;
import it.unipi.m598992.DirectChat.RoomDB.entity.User;

public class CommunicationService extends Service {
    private static final String NOTIFICATION_CHANNEL_ID = "communication_service_channel";


    //costanti per indicare ruolo nella comunicazione
    public static final int SERVER_ROLE = 0;
    public static final int CLIENT_ROLE = 1;

    //codici di stato del service
    private final int CONNECTED = 0;
    private final int NOT_CONNECTED = 1;
    private final int CONNECTING = 2;
    private final int IS_SENDING = 3;

    //id identificativo della notifica in corso necessario per aggiornarla
    public final int ONGOING_NOTIFICATION_ID = 22;

    private final int CHUCK_FILE_SIZE = 8192; //8 KB

    //Lista azioni gestite dal service
    public static final String ACTION_SEND_MSG = "it.unipi.m598992.directChat.ACTION_SEND_MSG";
    public static final String ACTION_START_COMMUNICATION = "it.unipi.m598992.directChat.ACTION_START_COMMUNICATION";
    public static final String ACTION_CHECK_COMMUNICATION = "it.unipi.m598992.directChat.ACTION_CHECK_COMMUNICATION";
    public static final String ACTION_STOP_COMMUNICATION = "it.unipi.m598992.directChat.STOP_COMMUNICATION";
    public static final String RESTART_COUNTING_NEWMSG = "it.unipi.m598992.directChat.RESTART_COUNTING_NEWMSG";

    //chiavi che identificano parametri extra da estrarre dall'intent ricevuto
    public static final String EXTRAS_COMMUNICATION_ROLE = "it.unipi.m598992.directChat.EXTRAS_COMMUNICATION_ROLE";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "it.unipi.m598992.directChat.EXTRAS_GROUP_OWNER_ADDRESS";
    public static final String EXTRAS_GROUP_OWNER_PORT = "it.unipi.m598992.directChat.EXTRAS_GROUP_OWNER_PORT";
    public static final String EXTRAS_DEVICE_NAME = "it.unipi.m598992.directChat.EXTRAS_DEVICE_NAME";
    public static final String EXTRAS_MSG_TYPE = "it.unipi.m598992.directChat.EXTRAS_MSG_TYPE";
    public static final String EXTRAS_TEXT_CONTENT = "it.unipi.m598992.directChat.EXTRAS_TEXT_CONTENT";
    public static final String EXTRAS_CONTENT_URI = "it.unipi.m598992.directChat.EXTRAS_CONTENT_URI";

    //contatore atomico (necessario perchè acceduto da diversi thread contemporaneamente) per tracciare i messaggi non letti dall'utente
    //e mostrarli in una notifica
    private AtomicInteger newMessageNumber;
    //booleano atomico che indica se la chat activity è aperta o meno
    private AtomicBoolean isChatActivityOpen;
    //intero atomico per tracciare lo stato di avanzamento dell'invio di un file
    private AtomicInteger currentSendingProgress;
    //stringa che identifica il file corrente che stiamo inviando
    private String currentFileNameSending;
    //nome del dispositivo con cui stiamo comunicando
    private String deviceToManage;
    //intero atomico che indica lo stato in cui si trova il service
    private AtomicInteger serviceState;


    //repository per poter aggiornare il database in seguito ad un messaggio inviato o ricevuto
    private MessageUserRepository repository;
    //ExecutorService per la gestione dei thread
    private ExecutorService executorService;
    private Socket communicationSocket;
    private ServerSocket serverSocket;
    //stream di scrittura usato dai thread writer
    private DataOutputStream dataOutputStream;

    //builder sfruttato per costruire le notifiche
    private NotificationCompat.Builder notificationBuilder;

    //menager di sistema per notificare nuove notifiche
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        Log.d(MainActivity.TAG, "service created");
        super.onCreate();
        executorService = Executors.newCachedThreadPool();
        repository = MessageUserRepository.getInstance(getApplication());
        communicationSocket = null;
        dataOutputStream = null;
        deviceToManage = null;
        newMessageNumber = new AtomicInteger(0);
        currentSendingProgress = new AtomicInteger(0);
        currentFileNameSending = "";
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        isChatActivityOpen = new AtomicBoolean(false);
        serviceState = new AtomicInteger(NOT_CONNECTED);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int currentServiceState = this.serviceState.get();
        switch (intent.getAction()) {
            case ACTION_CHECK_COMMUNICATION:
                //**** Intent ricevuto da ChatActivity ****//
                if (currentServiceState == CONNECTED) {
                    //sono connesso --> notifico la ChatActivity
                    Intent i = new Intent(ChatActivity.COMMUNICATION_OK);
                    i.putExtra(ChatActivity.EXTRAS_OTHER_SIDE_DEVICE_NAME, deviceToManage);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && newMessageNumber.get() > 0) {
                        //aggiorno la notifica indicando che non ho più nuovi messaggi
                        newMessageNumber.set(0);
                        isChatActivityOpen.set(true);
                        Notification notification = notificationBuilder.setContentTitle(getString(R.string.notification_title, deviceToManage))
                                .setContentText(getString(R.string.notification_content, newMessageNumber.get())).setSilent(true)
                                .build();
                        notificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
                    }
                } else if (currentServiceState == IS_SENDING) {
                    //sto inviando --> notifico la ChatActivity che sono connesso e lo informo sullo stato di progresso invio file
                    Intent i = new Intent(ChatActivity.COMMUNICATION_OK);
                    i.putExtra(ChatActivity.EXTRAS_OTHER_SIDE_DEVICE_NAME, deviceToManage);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && newMessageNumber.get() > 0) {
                        //aggiorno la notifica indicando che non ho più nuovi messaggi
                        newMessageNumber.set(0);
                        isChatActivityOpen.set(true);
                        Notification notification = notificationBuilder.setContentTitle(getString(R.string.notification_title, deviceToManage))
                                .setContentText(getString(R.string.notification_content, newMessageNumber.get())).setSilent(true)
                                .build();
                        notificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
                    }
                    //notifico anche lo stato do progresso dell'invio del file
                    i = new Intent(ChatActivity.SENDING_UPDATE);
                    i.putExtra(ChatActivity.EXTRAS_PROGRESS_STATE, currentSendingProgress.get());
                    i.putExtra(ChatActivity.EXTRAS_FILE_NAME, currentFileNameSending);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
                } else if (currentServiceState == NOT_CONNECTED) {
                    //non sono connesso ---> lo notifico alla chat activity
                    Intent i = new Intent(ChatActivity.COMMUNICATION_CLOSED);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
                }
                break;
            case ACTION_START_COMMUNICATION:
                if (currentServiceState == CONNECTED || currentServiceState == IS_SENDING || currentServiceState == CONNECTING)
                    return START_NOT_STICKY;
                //**** Intent ricevuto da MainActivity ****//
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        serviceState.set(CONNECTING);
                        //prelevo gli extra necessari per cominciare la comunicazione
                        int groupOwnerPort = intent.getIntExtra(CommunicationService.EXTRAS_GROUP_OWNER_PORT, 8888);
                        String groupOwnerAddress = intent.getStringExtra(CommunicationService.EXTRAS_GROUP_OWNER_ADDRESS);
                        int role = intent.getIntExtra(CommunicationService.EXTRAS_COMMUNICATION_ROLE, 1);
                        String thisDeviceName = intent.getStringExtra(CommunicationService.EXTRAS_DEVICE_NAME);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            //se la versione è maggiore di android 1O, MainActivity mi ha avviato con startForeground() --> genero la notifica per rimanere
                            //in foreground anche una volta che l'app è chiusa
                            generateForegroundNotification();
                        }
                        try {
                            if (role == SERVER_ROLE) {
                                //siamo il server
                                startListeningForMsgServer(groupOwnerPort, thisDeviceName);
                            } else {
                                //siamo il client
                                startListeningForMsgClient(groupOwnerAddress, groupOwnerPort, thisDeviceName);
                            }
                        } catch (IOException e) {
                            if (e.getMessage() != null)
                                Log.e(MainActivity.TAG, "Eccezione: " + e.getMessage());
                        } finally {
                            closeAllAndStop();

                            //avverto la chatActivity (se aperta) che la comunicazione è terminata!
                            Intent i = new Intent(ChatActivity.COMMUNICATION_CLOSED);
                            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
                        }
                    }
                });
                break;
            case RESTART_COUNTING_NEWMSG:
                //**** Intent ricevuto da ChatActivity per notificare che non è puù attiva e che il service deve ricominciare a contare i nuovi messaggi ****//
                isChatActivityOpen.set(false);
                break;
            case ACTION_SEND_MSG:
                //**** Intent ricevuto da ChatActivity per inviare un nuovo messaggio *****//
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sendMsg(intent);
                        } catch (IOException e) {
                            closeAllAndStop();
                            e.printStackTrace();
                        }
                    }
                });
                break;
            //**** Intent ricevuto da MainActivity per stoppare il service in seguito ad una richiesta esplicita dell'utente *****//
            case ACTION_STOP_COMMUNICATION:
                closeAllAndStop();
                Log.d(MainActivity.TAG, "Stopping service");
                break;
        }
        return START_NOT_STICKY;
    }

    //descrizione: chiusura di tutte le strutture dati attive e terminazione Service.
    //chiamato in seguito:
    // 1) l'altro lato chiude la connessione --> il thread in read lancia un'eccezione, si esce dal readingLoop e si chiama closeAllAndStop()
    // 2) chiudo io la comunicazione --> chiamo in ACTION_STOP_COMMUNICATION il metodo closeAllAndStop();
    // 3) errori interni I/O in lettura o scrittura ---> si lanciano eccezioni che fanno chiamare closeAllAndStop()
    private void closeAllAndStop() {
        try {
            if (communicationSocket != null) {
                Log.d(MainActivity.TAG, "Closing communicationSocket");
                communicationSocket.close();
                communicationSocket = null;
            }

            if (serverSocket != null) {
                //se il server (per qualche motivo) rimane bloccato nella accept
                serverSocket.close();
                serverSocket = null;
            }
            if (dataOutputStream != null) {
                dataOutputStream.close();
                dataOutputStream = null;
            }
        } catch (IOException e) {
            // Give up
            Log.e(MainActivity.TAG, e.getMessage());
        } finally {
            if (executorService != null) {
                executorService.shutdownNow();
                executorService = null;
            }
            stopForeground(true);
            serviceState.set(NOT_CONNECTED);
            stopSelf();
        }
    }

    public void startListeningForMsgClient(String groupOwnerAddress, int groupOwnerPort, String thisDeviceName) throws IOException {
        communicationSocket = new Socket();
        communicationSocket.bind(null);
        try {
            //race condition se il client si avvia prima del server --> attendo un secondo prima di collegarmi
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.d(MainActivity.TAG, "Reading thread interrupted");
        }

        Log.d(MainActivity.TAG, "Client: Try to connect");
        try {
            communicationSocket.connect(new InetSocketAddress(groupOwnerAddress, groupOwnerPort), 6000);
        } catch (IOException e) {
            Log.d(MainActivity.TAG, e.getMessage());
            return;
        }
        Log.d(MainActivity.TAG, "Socket client connected");
        messageReadingLoop(thisDeviceName);
    }

    public void startListeningForMsgServer(int groupOwnerPort, String thisDeviceName) throws IOException {
        serverSocket = new ServerSocket(groupOwnerPort);
        Log.d(MainActivity.TAG, "Server: Try to connect");
        serverSocket.setSoTimeout(6000);
        try {
            communicationSocket = serverSocket.accept();
        } catch (SocketTimeoutException e) {
            return;
        }
        Log.d(MainActivity.TAG, "Socket server connected");
        serverSocket.close();
        serverSocket = null;
        messageReadingLoop(thisDeviceName);
    }

    private void messageReadingLoop(String thisDeviceName) throws IOException {
        //salvo l'output stream che sarà sfruttuato dai thread writer per inviare successivamente messaggi
        dataOutputStream = new DataOutputStream(communicationSocket.getOutputStream());
        //comunico il nome del device all'altro lato
        dataOutputStream.writeUTF(thisDeviceName);

        try (DataInputStream dataInputStream = new DataInputStream(communicationSocket.getInputStream())) {
            //leggo il nome del device con cui comunico
            deviceToManage = dataInputStream.readUTF();
            //salvo il nuovo utente nel database
            repository.insertUser(new User(deviceToManage));

            //comunico alla chat activity che l'avvio della comunicazione è andato a buon fine
            Intent i = new Intent(ChatActivity.COMMUNICATION_OK);
            i.putExtra(ChatActivity.EXTRAS_OTHER_SIDE_DEVICE_NAME, deviceToManage);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
            serviceState.set(CONNECTED);

            //modifico l'eventuale notifica
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Notification notification = notificationBuilder.setContentTitle(getString(R.string.notification_title, deviceToManage))
                        .setContentText(getString(R.string.notification_content, newMessageNumber.get())).setSilent(false)
                        .build();
                notificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
            }

            int msgType;
            long size;
            String fileTitle;
            while (true) {
                msgType = dataInputStream.readInt();
                switch (msgType) {
                    case Message.TXT:
                        readTxtMsg(dataInputStream);
                        break;
                    case Message.GENERAL_FILE:
                    case Message.AUDIO:
                    case Message.IMAGE:
                    case Message.VIDEO:
                    case Message.REC:
                        size = dataInputStream.readLong();
                        fileTitle = dataInputStream.readUTF();
                        readFileMsg(msgType, size, fileTitle, dataInputStream);
                }
                //se la chat activity era chiusa notifico l'arrivo di un nuovo messaggio
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !isChatActivityOpen.get()) {
                    Notification notification = notificationBuilder.setContentTitle(getString(R.string.notification_title, deviceToManage))
                            .setContentText(getString(R.string.notification_content, newMessageNumber.incrementAndGet())).setSilent(false)
                            .build();
                    notificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
                }
            }
        }
    }

    private void readTxtMsg(DataInputStream dataInputStream) throws IOException {
        //leggo la stringa di testo inviata
        String line = dataInputStream.readUTF();
        Message msg = new Message(new Date(), Message.TXT, "", line, true, deviceToManage);
        repository.insertMessage(msg);
    }


    private void readFileMsg(int type, long size, String fileTitle, DataInputStream dataInputStream) throws IOException {
        byte[] fileChunksContent = new byte[CHUCK_FILE_SIZE];
        long totalByteRead = 0;
        int byteRead;
        File newFile = createFileInMyExternal(type, fileTitle);
        Uri fileInSharedUri = null;
        DataOutputStream fileInSharedOut = null;
        if (newFile != null) {
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(newFile))) {
                if (haveToSaveToSharedFolder(type)) {
                    //controllo se l'utente aveva inficato nelle preferenze la volonta di salvare i file nelle cartelle condivise
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        fileInSharedUri = getNewMediaStoreFileUri(type, fileTitle);
                    } else {
                        fileInSharedUri = getNewFileInSharedUri(type, fileTitle);
                    }
                    if (fileInSharedUri != null)
                        fileInSharedOut = new DataOutputStream(getContentResolver().openOutputStream(fileInSharedUri));
                }
                while (totalByteRead < size) {
                    byteRead = dataInputStream.read(fileChunksContent, 0, CHUCK_FILE_SIZE);
                    if (byteRead == -1) {
                        Log.d(MainActivity.TAG, "Deleting files");
                        //EOF prima di aver letto tutto il file
                        newFile.delete();
                        if (fileInSharedUri != null)
                            getContentResolver().delete(fileInSharedUri, null, null);
                        throw new IOException("Communication protocol not respected");
                    }
                    totalByteRead += byteRead;

                    out.write(fileChunksContent, 0, byteRead);
                    if (fileInSharedOut != null)
                        fileInSharedOut.write(fileChunksContent, 0, byteRead);
                }
            } finally {
                if (fileInSharedOut != null) {
                    fileInSharedOut.close();
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fileInSharedUri != null) {
                //in tale caso modifichiamo l'entry corrispondente all'Uri nel MediaStore indicando che abbiamo terminato di scrivere (IS_PENDING = 0)
                ContentValues newFileDetails = new ContentValues();
                newFileDetails.put(MediaStore.MediaColumns.IS_PENDING, 0);
                newFileDetails.put(MediaStore.MediaColumns.MIME_TYPE, getMIMEType(fileInSharedUri));
                getContentResolver().update(fileInSharedUri, newFileDetails, null, null);
            } else if (fileInSharedUri != null) {
                //in tale caso notifico MediaScanner di un nuovo file
                MediaScannerConnection.scanFile(getApplicationContext(),
                        new String[]{getFilePathInShared(type, fileTitle)},
                        new String[]{getMIMEType(fileInSharedUri)}, null);
            }

            //creo URI del file tramite il FileProvider che userò per condividere il file con altre app
            Uri fileURI = FileProvider.getUriForFile(this, "it.unipi.m598992.DirectChat.fileprovider", newFile);
            //sfrutto repository per inserire messaggio nel database
            Message msg = new Message(new Date(), type, fileURI.toString(), "", true, deviceToManage);
            repository.insertMessage(msg);
        } else {
            throw new IOException("Internal error");
        }
    }

    //descrizione: metodo che ritorna true se l'utente aveva espresso la volonta di salvare i messaggi di tipo type
    //nelle cartelle condivise
    public boolean haveToSaveToSharedFolder(int type) {
        if (Build.VERSION.SDK_INT < 29) {
            int access_storage_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            //controllo di sicurezza: l'utente potrebbe disattivare i permessi dalle impostazioni dopo averli concessi all'inizio
            //e aver salvato la preferenza nelle preferenze condivise
            if (access_storage_permission != PERMISSION_GRANTED) {
                return false;
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        switch (type) {
            case Message.AUDIO:
                return prefs.getBoolean("saveAudiosToShared", false);
            case Message.IMAGE:
                return prefs.getBoolean("saveImagesToShared", false);
            case Message.VIDEO:
                return prefs.getBoolean("saveVideosToShared", false);
            case Message.GENERAL_FILE:
                return prefs.getBoolean("saveGeneralFileToShared", false);
            default:
                return false;
        }
    }

    //descrizione: metodo che ritorna il mime type di un file a partire dalla sua Uri
    private String getMIMEType(Uri fileUri) {
        DocumentFile file = DocumentFile.fromSingleUri(getApplicationContext(), fileUri);
        String MIMEType;
        if (file != null && file.getType() != null) {
            MIMEType = file.getType();
        } else {
            MIMEType = getContentResolver().getType(fileUri);
        }
        return MIMEType;
    }

    //descrizione: metodo che crea un file nelle cartelle "convenzionali" della base directory associata all'app in memoria esterna e ritorna
    //l'oggetto java associato a tale file
    private File createFileInMyExternal(int type, String fileName) throws IOException {
        //ottengo la directory "convenzionale"
        File storageDir = null;
        if(canWriteToExternal()){
            switch (type) {
                case Message.AUDIO:
                case Message.REC:
                    storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
                    break;
                case Message.IMAGE:
                    storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    break;
                case Message.VIDEO:
                    storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
                    break;
                case Message.GENERAL_FILE:
                    storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            }
            if (storageDir == null) {
                return null;
            } else {
                if (!storageDir.exists()) {
                    //la directory non esisteva --> la creo
                    if (storageDir.mkdirs()) {
                        File newFile = new File(storageDir.getAbsolutePath(), fileName);
                        if (newFile.exists()) {
                            newFile = new File(storageDir.getAbsolutePath(), "(COPY)" + System.currentTimeMillis() + fileName);
                        }
                        if (!newFile.createNewFile())
                            return null;
                        return newFile;
                    } else
                        return null;
                } else {
                    File newFile = new File(storageDir.getAbsolutePath(), fileName);
                    if (newFile.exists()) {
                        newFile = new File(storageDir.getAbsolutePath(), "(COPY)" + System.currentTimeMillis() + fileName);
                    }
                    if (!newFile.createNewFile())
                        return null;
                    return newFile;
                }
            }
        }
        return null;
    }

    //descrizione: crea un file nella cartella "convenzionale" condivisa in memoria esterna e ritorna l'uri associata a tale file
    public Uri getNewFileInSharedUri(int type, String fileName) throws IOException {
        if(canWriteToExternal()){
            File newFile = new File(getFilePathInShared(type, fileName));
            File storageDir = newFile.getParentFile();
            if (storageDir == null) {
                return null;
            } else {
                if (!storageDir.exists()) {
                    //la directory non esisteva --> la creo
                    if (storageDir.mkdirs()) {
                        if (newFile.exists()) {
                            newFile = new File(storageDir.getAbsolutePath(), "(COPY)" + System.currentTimeMillis() + fileName);
                        }
                        if (!newFile.createNewFile())
                            return null;
                        return FileProvider.getUriForFile(this, "it.unipi.m598992.DirectChat.fileprovider", newFile);
                    } else
                        return null;
                } else {
                    if (newFile.exists()) {
                        newFile = new File(storageDir.getAbsolutePath(), "(COPY)" + System.currentTimeMillis() + fileName);
                    }
                    if (!newFile.createNewFile())
                        return null;
                    return FileProvider.getUriForFile(this, "it.unipi.m598992.DirectChat.fileprovider", newFile);
                }
            }
        }
        return null;
    }

    //descrizione: ritorna l'ipotetico path che avrebbe il file nella cartella condivisa "convenzionale" in memoria esterna
    public String getFilePathInShared(int type, String filename) {
        File storageDir = null;
        switch (type) {
            case Message.AUDIO:
                storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                break;
            case Message.IMAGE:
                storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                break;
            case Message.VIDEO:
                storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                break;
            case Message.GENERAL_FILE:
                storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }
        return storageDir + "/" + MainActivity.APP_NAME + "/" + filename;
    }

    //descrizione: inserisce una entry nella collezione "convenzionale" del MediaStore e ritorna l'uri corrispondente nel MediaStore
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public Uri getNewMediaStoreFileUri(int type, String fileName) {
        ContentResolver resolver = getApplicationContext().getContentResolver();
        //Ottengo la collezzione "convenzionale"
        Uri collection = null;
        ContentValues newFileDetails = new ContentValues();
        switch (type) {
            case Message.AUDIO:
                collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                newFileDetails.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + MainActivity.APP_NAME);
                break;
            case Message.IMAGE:
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                newFileDetails.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + MainActivity.APP_NAME);
                break;
            case Message.VIDEO:
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                newFileDetails.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + MainActivity.APP_NAME);
                break;
            case Message.GENERAL_FILE:
                collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                newFileDetails.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + MainActivity.APP_NAME);
                break;
        }
        newFileDetails.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        newFileDetails.put(MediaStore.MediaColumns.TITLE, removeExtension(fileName));
        //l'operazione è pendente: il file non verrà visualizzato nel media store fin quando non setto IS_PENDING a 0
        newFileDetails.put(MediaStore.MediaColumns.IS_PENDING, 1);
        return resolver.insert(collection, newFileDetails);
    }

    //descrizione: prende in input il nome di un file con estensione e ritorna la stringa costtuita dal nome del file senza estensione
    public String removeExtension(String fileName) {
        if (fileName.indexOf(".") > 0) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        } else {
            return fileName;
        }
    }

    private void sendMsg(Intent intent) throws IOException {
        if (dataOutputStream == null || communicationSocket == null || executorService == null) {
            //controllo di sicurezza
            Intent i = new Intent(ChatActivity.COMMUNICATION_CLOSED);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
            return;
        }

        int msgType = intent.getIntExtra(CommunicationService.EXTRAS_MSG_TYPE, 0);
        Message msg;
        switch (msgType) {
            case Message.TXT:
                dataOutputStream.writeInt(msgType);
                String txtMsg = intent.getStringExtra(CommunicationService.EXTRAS_TEXT_CONTENT);
                dataOutputStream.writeUTF(txtMsg);
                msg = new Message(new Date(), msgType, "", txtMsg, false, deviceToManage);
                repository.insertMessage(msg);
                break;
            case Message.GENERAL_FILE:
            case Message.AUDIO:
            case Message.IMAGE:
            case Message.VIDEO:
            case Message.REC:
                Uri contentUri = intent.getParcelableExtra(EXTRAS_CONTENT_URI);
                sendFileMessage(msgType, contentUri);
        }
    }

    public void sendFileMessage(int msgType, Uri contentUri) throws IOException {
        long fileSize = getFileSize(contentUri);
        String fileName = getFileName(contentUri);

        if (fileSize == -1 || fileName.isEmpty()) {
            Log.d(MainActivity.TAG, "NO FILE");
            return;
        }

        currentFileNameSending = fileName;
        //modifico lo stato del service
        serviceState.set(IS_SENDING);
        //notifico la chatActivity dell'inizio dell'invio
        Intent i = new Intent(ChatActivity.SENDING_UPDATE);
        i.putExtra(ChatActivity.EXTRAS_PROGRESS_STATE, 0);
        i.putExtra(ChatActivity.EXTRAS_FILE_NAME, fileName);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
        //invio tipo del file, dimensione e nome del file
        dataOutputStream.writeInt(msgType);
        dataOutputStream.writeLong(fileSize);
        dataOutputStream.writeUTF(fileName);

        byte[] fileChunksContent = new byte[CHUCK_FILE_SIZE];
        try (BufferedInputStream in = new BufferedInputStream(getContentResolver().openInputStream(contentUri))) {
            long byteSent = 0;
            int byteRead;
            int previousProgress;
            int currentProgress = 0;
            while ((byteRead = in.read(fileChunksContent, 0, CHUCK_FILE_SIZE)) != -1) {
                dataOutputStream.write(fileChunksContent, 0, byteRead);
                byteSent += byteRead;
                //informo chat activity dei progressi nell'invio tramite broadcast receiver
                previousProgress = currentProgress;
                currentProgress = (int) ((float) byteSent / (float) fileSize * 100);
                if (currentProgress % 5 == 0 && currentProgress != previousProgress) {
                    i.putExtra(ChatActivity.EXTRAS_PROGRESS_STATE, currentProgress);
                    i.putExtra(ChatActivity.EXTRAS_FILE_NAME, fileName);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
                    if (currentProgress % 10 == 0)
                        currentSendingProgress.set(currentProgress);
                }
            }
        }
        //modifico lo stato del service
        serviceState.set(CONNECTED);
        Message msg = new Message(new Date(), msgType, contentUri.toString(), "", false, deviceToManage);

        repository.insertMessage(msg);
    }

    //descrizione: ritorna il basename del file associato all'uri fileUri
    private String getFileName(Uri fileUri) {
        DocumentFile file = DocumentFile.fromSingleUri(getApplicationContext(), fileUri);
        if (file != null && file.getName() != null) {
            return file.getName();
        }
        return "";
    }

    //descrizione: ritorna la dimensione del file associato all'uri fileUri
    private long getFileSize(Uri fileUri) {
        DocumentFile file = DocumentFile.fromSingleUri(getApplicationContext(), fileUri);
        if (file != null) {
            return file.length();
        }
        return -1;
    }

    @Override
    public void onDestroy() {
        Log.d(MainActivity.TAG, "Destroying service");
        super.onDestroy();
        closeAllAndStop();
    }

    //descrizione: metodo che crea il channel e la notifica necessari per il foreground service
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void generateForegroundNotification() {
        createChannel();
        //se tocco la notifica apro MainActivity
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.application_logo)
                .setContentIntent(pendingIntent);
        Notification notification = notificationBuilder.setContentTitle(getString(R.string.starting_notification_title)).build();
        isChatActivityOpen.set(true);
        //sottometto la notifica
        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }

    public void createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    "Communication service", NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationChannel.enableLights(false);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    //descrizione: ritorna true se posso scrivere nella memoria esterna
    private boolean canWriteToExternal(){
        return Environment.isExternalStorageEmulated() || Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }
}