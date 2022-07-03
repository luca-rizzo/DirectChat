package it.unipi.m598992.DirectChat.fragment;

import android.content.Context;
import android.location.LocationManager;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.location.LocationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;


import it.unipi.m598992.DirectChat.activity.MainActivity;
import it.unipi.m598992.DirectChat.R;
import it.unipi.m598992.DirectChat.adapter.PeerListAdapter;

public class PeerListFragment extends Fragment implements WifiP2pManager.PeerListListener, SwipeRefreshLayout.OnRefreshListener {
    private RecyclerView recyclerView;
    private PeerListAdapter peerListAdapter;
    private PeerListFragmentListener peerListFragmentListener;
    private TextView noWiFiDirectTextView;
    private TextView locationOffTextView;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        peerListAdapter = new PeerListAdapter((MainActivity) getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Ottengo il layout associato al fragment
        View view = inflater.inflate(R.layout.fragment_discovery_peer, container, false);
        recyclerView = view.findViewById(R.id.discoveryRV);
        noWiFiDirectTextView = view.findViewById(R.id.noDirectWiFiTextView);
        locationOffTextView = view.findViewById(R.id.locationOff);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //imposto il recycler view e gli associo l'adapter
        recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        recyclerView.setAdapter(peerListAdapter);
        swipeRefreshLayout.setOnRefreshListener(this);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if(context instanceof PeerListFragmentListener){
            //Ottengo il listener per rispondere alle azioni che l'utente compie nella UI
            peerListFragmentListener = (PeerListFragmentListener) context;
        } else{
            throw  new RuntimeException(context + " must implement PeerListFragmentListener");
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if(getActivity() != null){
            LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            //aggiorno lo stato del fragment
            setLocationOffTextViewVisibility(LocationManagerCompat.isLocationEnabled(locationManager));
            setNoWiFiTextViewVisibility(peerListFragmentListener.isWiFiDirectActive());

            if(peerListFragmentListener.isDiscovering()){
                //mostro che sto effettuando discovery
                setRefreshing(true);
            }
            else{
                //avvio discovery
                peerListFragmentListener.startDiscovery();
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //resetto il listener
        peerListFragmentListener = null;
    }

    /* descrizione: metodo di WifiP2pManager.PeerListListener chiamato quando sono disponibili nuovi peer o i correnti
       hanno cambiato stato */
    @Override
    public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
        Log.d(MainActivity.TAG, "Submitting peer list");
        peerListAdapter.submitList(new ArrayList<>(wifiP2pDeviceList.getDeviceList()));
        swipeRefreshLayout.setRefreshing(false);
    }

    /* descrizione: metodo di SwipeRefreshLayout.OnRefreshListener chiamato quando l'utente effettua uno swipe verso
       il basso nel fragment*/
    @Override
    public void onRefresh() {
        peerListFragmentListener.startDiscovery();
    }

    /* descrizione: metodo per settare o resettare il refreshing*/
    public void setRefreshing(boolean refreshing){
        if(refreshing){
            swipeRefreshLayout.setRefreshing(false);
            swipeRefreshLayout.setRefreshing(true);
        }
        else{
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    public PeerListAdapter getPeerListAdapter(){
        return peerListAdapter;
    }

    /*descrizione: disabilita o abilita la scritta che indica che il WiFi direct è disabilitato*/
    public void setNoWiFiTextViewVisibility(boolean wifFiP2Penabled){
        if(wifFiP2Penabled){
            noWiFiDirectTextView.setVisibility(View.GONE);
        }
        else{
            noWiFiDirectTextView.setVisibility(View.VISIBLE);
        }
    }

    /*descrizione: disabilita o abilita la scritta che indica che la locazione è disabilitata*/
    public void setLocationOffTextViewVisibility(boolean locationEnabled){
        if(locationEnabled){
            locationOffTextView.setVisibility(View.GONE);
        }
        else{
            locationOffTextView.setVisibility(View.VISIBLE);
        }
    }

    //descrizione: ritorna true se lo swipeRefreshLayout sta già caricando
    public boolean isRefreshing(){
        return swipeRefreshLayout.isRefreshing();
    }

    /*descrizione: resetta la lista di peer corrente mostrata*/
    public void resetPeerList(){
        peerListAdapter.submitList(new ArrayList<>());
    }

    /*Interfaccia di comunicazione tra il fragment e l'activity host*/
    public interface PeerListFragmentListener {
        void startDiscovery();
        boolean isWiFiDirectActive();
        boolean isDiscovering();
    }
}