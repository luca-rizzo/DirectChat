package it.unipi.m598992.DirectChat.activity;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;

import it.unipi.m598992.DirectChat.R;
import it.unipi.m598992.DirectChat.RoomDB.entity.User;
import it.unipi.m598992.DirectChat.adapter.BKChatListAdapter;
import it.unipi.m598992.DirectChat.adapter.PeerListAdapter;
import it.unipi.m598992.DirectChat.adapter.VPAdapter;
import it.unipi.m598992.DirectChat.broadcastReceiver.WiFiDirectBroadcastReceiver;
import it.unipi.m598992.DirectChat.fragment.BKChatListFragment;
import it.unipi.m598992.DirectChat.fragment.LoadingFragmentDialog;
import it.unipi.m598992.DirectChat.fragment.PeerListFragment;
import it.unipi.m598992.DirectChat.service.CommunicationService;
import it.unipi.m598992.DirectChat.worker.DeleteChatsWorker;

public class MainActivity extends AppCompatActivity implements PeerListFragment.PeerListFragmentListener, WifiP2pManager.ConnectionInfoListener, BKChatListAdapter.SelectUserCVListener, PeerListAdapter.SelectPeerCVListener {
    public static final String APP_NAME = "DirectChat";
    public static final String TAG = "myDebugTag";

    private static final int NECESSARY_PERMISSION_CODE = 1;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;


    //Broadcast receiver che "ascolta" gli intent broadcast relativi al WiFi direct e notifica la MainActivity
    private WiFiDirectBroadcastReceiver receiver;
    //Insieme di intent broadcast che il riceiver "ascolta"
    private IntentFilter intentFilter;

    //Manager di sistema per richiedere servizi al framework WiFi direct
    private WifiP2pManager wifiP2pManager;
    //Channel di comunicazione con il framework WiFi direct
    private WifiP2pManager.Channel wifiP2pChannel;


    /**** Variabili di informazione riguardanti lo stato del wifi direct ****/
    private boolean isWiFiDirectActive = false;
    private boolean isDiscovering = false;
    private String wifiP2PdeviceName = "";
    private boolean connectionInfoAvailable = false;

    //current action mode: se è != null vuol dire che un action mode è attiva
    private ActionMode myCab = null;

    //listener sfruttato in vari punti del codice per le richieste al framework WiFi direct
    private final WifiP2pManager.ActionListener actionListener = new WifiP2pManager.ActionListener() {
        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure(int reason) {
            String errorMsg = "Wi-fi direct Failed: ";
            switch (reason) {
                case WifiP2pManager.BUSY:
                    errorMsg += "Framework busy";
                    break;
                case WifiP2pManager.ERROR:
                    errorMsg += "Internal error";
                    break;
                case WifiP2pManager.P2P_UNSUPPORTED:
                    errorMsg += "Unsupported";
                    break;
                default:
                    errorMsg += "Unknown message";
                    break;
            }
            Log.d(TAG, errorMsg);
        }
    };

    //Registro il callback da chiamare quando riceverò un risultato dalla ChatActivity
    ActivityResultLauncher<Intent> chatActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == ChatActivity.WIFI_DIRECT_DISCONNECTION_REQUIRED) {
                        disconnectP2P();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager2);
        setTabLayoutAndVP();

        if (permissionGranted()) {
            //possiedo i permessi: inizializziamo il wifi direct
            if (!initializeWiFiDirect()) {
                finish();
            }
            intentFilter = new IntentFilter();
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
            intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
            receiver = new WiFiDirectBroadcastReceiver(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //registro dinamicamente il receiver
        if (receiver != null && intentFilter != null)
            registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //annulla la registrazione del receiver
        if (receiver != null && intentFilter != null)
            unregisterReceiver(receiver);
    }

    /***** GESTIONE PERMESSI *****/

    //descrizione: metodo per la gestione dei permessi necessari
    private boolean permissionGranted() {
        int access_fine_location_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (access_fine_location_permission == PERMISSION_GRANTED) {
            //permesso necessario
            return true;
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            //l'utente ha rifiutato i permessi (non in maniera permanente con Never Ask me again)
            showRequestDialog(false);
            Log.d(TAG, "Devi consentire i permessi");
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, NECESSARY_PERMISSION_CODE);
        }
        return false;
    }

    //descrizione: metodo chiamato in seguito alla richiesta dei permessi
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean neverAskAgainChecked;
        if (requestCode == NECESSARY_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                //possiedo i permessi: inizializziamo il wifi direct
                if (!initializeWiFiDirect()) {
                    finish();
                }
                intentFilter = new IntentFilter();
                intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
                intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
                intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
                intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
                intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
                receiver = new WiFiDirectBroadcastReceiver(this);
                registerReceiver(receiver, intentFilter);
            } else {
                neverAskAgainChecked = !shouldShowRequestPermissionRationale(permissions[0]);
                showRequestDialog(neverAskAgainChecked);
            }
        }
    }

    //descrizione: mostra un dialog di spiegazione se l'utente ha negato i permessi
    private void showRequestDialog(boolean neverAsk) {
        Dialog dialog;
        if (neverAsk) {
            //l'utente ha selezionato Never ask me again --> lo informo che deve andare nelle impostazioni
            dialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.necessary_permission_setting))
                    .setNegativeButton(getString(R.string.need_to_go_to_settings), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    }).create();
        } else {
            //l'utente non ha selezionato Never ask me again --> gli chiedo se vuole richiesti i permessi
            dialog = new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.necessary_permission_retry))
                    .setPositiveButton(getString(R.string.retry_permission), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, NECESSARY_PERMISSION_CODE);
                        }
                    }).setNegativeButton(getString(R.string.deny_permission), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    }).create();
        }
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }


    //***** METODI RELATIVI AL WI FI DIRECT E ALLA GESTIONE DELL'AVVIO DELLA COMUNICAZIONE*****//

    //descrizione: metodo inizializzazione WiFi direct. Inizializza il manager e il channel per comunicare con il framework
    //ritorna: true se l'inizializzazione è andata a buon fine, false altrimenti
    private boolean initializeWiFiDirect() {
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            Log.e(TAG, "Cannot get Wi-Fi system service.");
            return false;
        }
        wifiP2pChannel = wifiP2pManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.d(TAG, "Wifi P2P Channel disconnected");
            }
        });
        if (wifiP2pChannel == null) {
            Log.e(TAG, "Cannot initialize Wi-Fi Direct.");
            return false;
        }

        wifiP2pManager.requestConnectionInfo(wifiP2pChannel, new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
                if (wifiP2pInfo.groupOwnerAddress != null) {
                    Log.d(MainActivity.TAG, "Info Available");
                    connectionInfoAvailable = true;
                }
            }
        });
        return true;
    }

    //descrizione: metodo callback di WifiP2pManager.ConnectionInfoListener che avvisa la disponibilità di una nuova connessione
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        if (wifiP2pInfo.groupFormed) {
            Log.d(MainActivity.TAG, "onConnectionInfoAvailable");
            int role;
            if(wifiP2pInfo.isGroupOwner){
                role = CommunicationService.SERVER_ROLE;
            } else{
                role = CommunicationService.CLIENT_ROLE;
            }

            String groupOwnerAddress = wifiP2pInfo.groupOwnerAddress.getHostAddress();
            int groupOwnerPort = 8888;
            connectionInfoAvailable = true;

            //avvio il service che gestirà la comunicazione
            Intent i = new Intent(getApplicationContext(), CommunicationService.class);
            i.setAction(CommunicationService.ACTION_START_COMMUNICATION);
            i.putExtra(CommunicationService.EXTRAS_COMMUNICATION_ROLE, role);
            i.putExtra(CommunicationService.EXTRAS_GROUP_OWNER_ADDRESS, groupOwnerAddress);
            i.putExtra(CommunicationService.EXTRAS_GROUP_OWNER_PORT, groupOwnerPort);
            i.putExtra(CommunicationService.EXTRAS_DEVICE_NAME, wifiP2PdeviceName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }

            /* gestione di una possibile race condition: lo stato del dispositivo è connected ma non ho ancora ricevuto la chiamata
            a onCOnnectionInfoAvailable. Se il dialog era avviato, lo sospendo e avvio la chatActivity*/
            LoadingFragmentDialog loadingDialog = (LoadingFragmentDialog) getSupportFragmentManager().findFragmentByTag(LoadingFragmentDialog.FRAGMENT_TAG);
            if (loadingDialog != null) {
                loadingDialog.dismiss();
                Intent intent = new Intent(this, ChatActivity.class);
                chatActivityResultLauncher.launch(intent);
            }

        }
    }

    /* descrizione: metodo di PeerListAdapter.SelectPeerCVListener chiamato quando l'utente clicca una particolare cardView
       associata ad un peer nel PeerListFragment */
    @SuppressLint("MissingPermission")
    @Override
    public void onCVItemClicked(WifiP2pDevice wifiP2pDevice) {
        if (wifiP2pDevice.status == WifiP2pDevice.CONNECTED && connectionInfoAvailable) {
            //il service "dovrebbe" essere attivo: sarà la chat activity a verificarlo
            Intent intent = new Intent(this, ChatActivity.class);
            chatActivityResultLauncher.launch(intent);
        } else if (wifiP2pDevice.status == WifiP2pDevice.CONNECTED) {
            /* gestione di una possibile race condition: lo stato del dispositivo è connected ma non ho ancora ricevuto la chiamata
            a onConnectionInfoAvailable. Avvio un dialog che verrà eliminato nel metodo onConnectionInfoAvailable*/
            new LoadingFragmentDialog().show(getSupportFragmentManager(), LoadingFragmentDialog.FRAGMENT_TAG);
        } else if (wifiP2pDevice.status == WifiP2pDevice.AVAILABLE) {
            //comincia la comunicazione con il peer!
            PeerListFragment peerListFragment = (PeerListFragment) getSupportFragmentManager().findFragmentByTag("f0");
            if (peerListFragment != null) {
                String connectedDeviceName = peerListFragment.getPeerListAdapter().getConnectedInvitedDeviceName();
                if (connectedDeviceName == null) {
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = wifiP2pDevice.deviceAddress;
                    wifiP2pManager.connect(wifiP2pChannel, config, actionListener);
                } else {
                    Toast.makeText(this, getString(R.string.already_connected), Toast.LENGTH_LONG).show();
                }
            }
        } else if (wifiP2pDevice.status == WifiP2pDevice.INVITED) {
            //l'utente vuole eliminare invito
            if (wifiP2pManager != null && wifiP2pChannel != null) {
                disconnectP2P();
            }
        }
    }

    /* descrizione: metodo di PeerListFragment.PeerListFragmentListener utilizzato per avvisare la MainActivity di effettuare discovery
                    in seguito ad un refresh o nella onResume. Verrà chiamato anche in seguito all'avviso che il wifiDirect è attivo */
    @SuppressLint("MissingPermission")
    @Override
    public void startDiscovery() {
        if (isWiFiDirectActive && !isDiscovering) {
            //non stavo effettuando la ricerca dei peer: comincio la ricerca
            wifiP2pManager.discoverPeers(wifiP2pChannel, actionListener);
        } else if (!isWiFiDirectActive){
            //il WiFiDirect è disabilitato. Informo il peerListFragment
            PeerListFragment peerListFragment = (PeerListFragment) getSupportFragmentManager().findFragmentByTag("f0");
            if (peerListFragment != null) {
                //stoppo refreshing
                peerListFragment.setRefreshing(false);
            }
        }
    }

    /* descrizione: metodo di PeerListFragment.PeerListFragmentListener che informa PeerListFragment se il WiFiDirect è attivo*/
    @Override
    public boolean isWiFiDirectActive() {
        return isWiFiDirectActive;
    }

    /* descrizione: metodo di PeerListFragment.PeerListFragmentListener che informa PeerListFragment se il dispositivo è in discovery*/
    @Override
    public boolean isDiscovering() {
        return isDiscovering;
    }

    /* descrizione: metodo che consente di rimuovere una connessione e di cancellare il relativo gruppo formato.
                    Inoltre effettua il refresh della lista di peer in PeerListFragment */
    @SuppressLint("MissingPermission")
    public void disconnectP2P() {
        if (wifiP2pManager != null && wifiP2pChannel != null) {
            wifiP2pManager.cancelConnect(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "cancelConnect onSuccess -");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "cancelConnect onFailure -" + reason);
                }
            });
            wifiP2pManager.removeGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "removeGroup onSuccess -");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "removeGroup onFailure -" + reason);
                }
            });
        }
        connectionInfoAvailable = false;
        PeerListFragment peerListFragment = (PeerListFragment) getSupportFragmentManager().findFragmentByTag("f0");
        if (peerListFragment != null) {
            peerListFragment.resetPeerList();
        }
    }

    public void setWifiP2PdeviceName(String wifiP2PdeviceName) {
        this.wifiP2PdeviceName = wifiP2PdeviceName;
    }

    public void setIsDiscovering(boolean discovering) {
        this.isDiscovering = discovering;
        PeerListFragment peerListFragment = (PeerListFragment) getSupportFragmentManager().findFragmentByTag("f0");
        if(!discovering){
            if (peerListFragment != null) {
                //stoppo refreshing
                peerListFragment.setRefreshing(false);
            }
        } else{
            if (peerListFragment != null) {
                if(!peerListFragment.isRefreshing())
                    //comincio refreshing
                    peerListFragment.setRefreshing(true);
            }
        }
    }

    //descrizione: metodo che attiva o disattiva le funzionalità dell'app e aggiorna la UI sulla base dello stato del WiFi direct
    public void setWiFiDirectActive(boolean wiFiDirectActive) {
        this.isWiFiDirectActive = wiFiDirectActive;
        PeerListFragment peerListFragment = (PeerListFragment) getSupportFragmentManager().findFragmentByTag("f0");
        if (wiFiDirectActive) {
            if (peerListFragment != null) {
                //notifico il PeerListFragment di non mostrare più la scritta di wifi direct disattivato
                peerListFragment.setNoWiFiTextViewVisibility(true);
            }
            startDiscovery();
        } else {
            if (peerListFragment != null) {
                Log.d(MainActivity.TAG, "OH i am here");
                //resetto la lista di peer di PeerListFragment
                peerListFragment.resetPeerList();
                //notifico il PeerListFragment di mostrare la scritta di wifi direct disattivato
                peerListFragment.setNoWiFiTextViewVisibility(false);
                //stoppo refreshing
                peerListFragment.setRefreshing(false);
            }
        }
    }

    //descrizione: metodo chiamato in seguito alla ricezione di un intent broadcast WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
    //che indica una nuova connessione. Richiede info riguardo la connessione e imposta l'activity come listener
    public void newWiFiDirectConnection(){
        wifiP2pManager.requestConnectionInfo(wifiP2pChannel, this);
    }

    //descrizione: metodo chiamato in seguito alla ricezione di un intent broadcast WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
    //che indica cambiamenti nella lista di peer. Richiede la lista di Peer e imposta il fragment come listener
    @SuppressLint("MissingPermission")
    public void requestPeerList(){
        PeerListFragment peerListFragment = (PeerListFragment) getSupportFragmentManager().findFragmentByTag("f0");
        //chiamo requestPeers per ottenere la lista dei peer disponibili
        if(peerListFragment != null)
            wifiP2pManager.requestPeers(wifiP2pChannel, peerListFragment);
    }

    //**** ****//

    //descrizione: inizializza il viewPager e il relativo adapter che fornisce i fragment
    private void setTabLayoutAndVP() {
        VPAdapter vpAdapter = new VPAdapter(this);
        viewPager.setAdapter(vpAdapter);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            //quando clicco su un tab modifico il fragment mostrato dal viewPager
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            //se l'utente effettua lo swipe nel viewPager modifico il relativo tab
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                TabLayout.Tab tabSelected = tabLayout.getTabAt(position);
                if(tabSelected != null)
                    tabSelected.select();
                //se passo dalla posizione 1 alla 0 (da BKChatFragment a PeerListFragment) e l'action mode era attiva la disattivo
                if (position == 0 && myCab != null) {
                    myCab.finish();
                }
            }
        });
    }

    private final ActionMode.Callback cab = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            MenuInflater inflater = actionMode.getMenuInflater();
            inflater.inflate(R.menu.bkchat_long_click_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.deleteBkChatItem) {
                //l'utente ha richeisto la cancellazione delle chat selezionate
                BKChatListFragment bkChatListFragment = (BKChatListFragment) getSupportFragmentManager().findFragmentByTag("f1");
                if (bkChatListFragment != null) {
                    enqueueDeleteChatsTask(bkChatListFragment.getBkChatListAdapter().getCopyCurrentSelectedUserChats());
                    Toast.makeText(MainActivity.this, getString(R.string.starting_deleting_chats), Toast.LENGTH_SHORT).show();
                }
                actionMode.finish();
                return true;
            } else
                return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            myCab = null;
            BKChatListFragment bkChatListFragment = (BKChatListFragment) getSupportFragmentManager().findFragmentByTag("f1");
            if (bkChatListFragment != null) {
                BKChatListAdapter bkChatListAdapter = bkChatListFragment.getBkChatListAdapter();
                bkChatListAdapter.removeAllSelectedChats();
            }
        }
    };

    //descrizione: metodo di BKChatListAdapter.SelectUserCVListener per la gestione di un click ad una card view del BKChatListFragment
    @Override
    public void onCVItemClick(User user) {
        if (myCab != null) {
            //è attiva l'actionMode: un click su una chat significa selezione o deselezione della chat
            BKChatListFragment bkChatListFragment = (BKChatListFragment) getSupportFragmentManager().findFragmentByTag("f1");
            if (bkChatListFragment != null) {
                BKChatListAdapter bkChatListAdapter = bkChatListFragment.getBkChatListAdapter();
                if (bkChatListAdapter.isThisUserChatSelected(user)) {
                    //la chat era selezionata --> la deseleziono
                    bkChatListAdapter.removeFromSelectedUserChats(user);
                } else {
                    //la chat non era selezionata --> la seleziono
                    bkChatListAdapter.addToSelectedUserChats(user);
                }
                int numberOfItemSelected = bkChatListAdapter.getNumberOfChatSelected();
                if (numberOfItemSelected == 0) {
                    myCab.finish();
                } else if (numberOfItemSelected == 1) {
                    myCab.setTitle(getString(R.string.one_chat_selected));
                } else {
                    myCab.setTitle(getString(R.string.more_chats_selected, numberOfItemSelected));
                }
            }
        } else {
            //apro la relativa chat
            Intent intent = new Intent(this, BKChatActivity.class);
            intent.putExtra(BKChatActivity.EXTRAS_OTHER_DEVICE_NAME, user.getName());
            startActivity(intent);
        }
    }

    //descrizione: metodo di BKChatListAdapter.SelectUserCVListener per la gestione di un longClick ad una card view del BKChatListFragment
    @Override
    public void onCVItemLongClick(User user) {
        if (myCab != null) {
            //action mode già attiva
            return;
        }
        //avvio l'actionMode
        myCab = startActionMode(cab);
        BKChatListFragment bkChatListFragment = (BKChatListFragment) getSupportFragmentManager().findFragmentByTag("f1");
        if (bkChatListFragment != null) {
            bkChatListFragment.getBkChatListAdapter().addToSelectedUserChats(user);
            myCab.setTitle((MainActivity.this).getString(R.string.one_chat_selected));
        }
    }

    //descrizione: creazione del menu associato all'activity
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    //descrizione: gestione dei click agli item del menu
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.aboutItem) {
            Intent aboutIntent = new Intent(this, AboutActivity.class);
            startActivity(aboutIntent);
            return true;
        } else if (item.getItemId() == R.id.settingsItem) {
            Intent settingIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //descrizione: mette in coda tramite il WorkManager una richiesta di chat con i relativi messaggi e file associati
    private void enqueueDeleteChatsTask(ArrayList<User> userChatsToDelete) {
        ArrayList<String> userNameListToDelete = new ArrayList<>();
        for (User user : userChatsToDelete) {
            userNameListToDelete.add(user.getName());
        }
        Data.Builder data = new Data.Builder();
        data.putStringArray(DeleteChatsWorker.CHATS_TO_DELETE, userNameListToDelete.toArray(new String[0]));
        OneTimeWorkRequest deleteWorkRequest = new OneTimeWorkRequest.Builder(DeleteChatsWorker.class)
                .setInputData(data.build())
                .build();
        //metto in coda la richiesta
        WorkManager.getInstance(getApplicationContext()).enqueue(deleteWorkRequest);
    }

    //descrizione: metodo chiamato da WiFIDirectBroadcastReceiver per essere aggiornato sullo stato della locazione
    public void setLocationState(boolean locationActive){
        PeerListFragment peerListFragment = (PeerListFragment) getSupportFragmentManager().findFragmentByTag("f0");
        if (peerListFragment != null) {
            peerListFragment.setLocationOffTextViewVisibility(locationActive);
        }
        if(!locationActive){
            disconnectP2P();
        }
    }
}