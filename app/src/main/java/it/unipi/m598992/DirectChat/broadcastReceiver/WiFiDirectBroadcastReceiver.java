package it.unipi.m598992.DirectChat.broadcastReceiver;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import androidx.core.location.LocationManagerCompat;

import it.unipi.m598992.DirectChat.activity.MainActivity;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    //Activity da "avvisare" quando si ricevono intent broadcast riguardanti il WiFi direct
    private final MainActivity activity;


    public WiFiDirectBroadcastReceiver(MainActivity activity) {
        super();
        this.activity = activity;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            /*CAMBIAMENTO NELLO STATO DEL WIFI DIRECT*/

            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED);
            if(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED){
                Log.d(MainActivity.TAG, "Wi-Fi P2P enabled");
                activity.setWiFiDirectActive(true);
            }
            else{
                Log.d(MainActivity.TAG, "Wi-Fi P2P disabled");
                activity.setWiFiDirectActive(false);
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            /*CAMBIAMENTO NELLA LISTA DEI PEERS O NEL LORO STATO*/

            Log.d(MainActivity.TAG, "Peer list changed");
            activity.requestPeerList();
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            /* GESTIONE NUOVA CONNESSIONE O DISCONNESISONE */
            if(isInitialStickyBroadcast())
                return;
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            if(networkInfo.isConnected()){
                Log.d(MainActivity.TAG, "Wi-fi direct new connection");
                //nuova connessione
                activity.newWiFiDirectConnection();
            }
            else {
                //disconnessione
                Log.d(MainActivity.TAG, "Wi-fi direct disconnected");
                activity.disconnectP2P();
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            //Ottengo il nome del dispositivo
            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            activity.setWifiP2PdeviceName(device.deviceName);
        } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
            /* CAMBIAMENTO NELLO STATO DEL DISCOVERY */

            int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 10000);
            if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                // Wifi P2P discovery iniziato
                Log.d(MainActivity.TAG, "Wi-Fi P2P discovery started");
                activity.setIsDiscovering(true);
            }
            else {
                // Wifi P2P discovery fermato
                Log.d(MainActivity.TAG, "Wi-Fi P2P discovery stopped");
                activity.setIsDiscovering(false);
            }
        }
        else if (LocationManager.PROVIDERS_CHANGED_ACTION.equals(action)) {
            /* CAMBIAMENTO NELLO STATO DEL SERVIZIO DI LOCATION */
            Log.d(MainActivity.TAG, "Location state change");
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            activity.setLocationState(LocationManagerCompat.isLocationEnabled(locationManager));
        }
    }
}
