package it.unipi.m598992.DirectChat.adapter;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;


import it.unipi.m598992.DirectChat.R;

public class PeerListAdapter extends ListAdapter<WifiP2pDevice, PeerListAdapter.PeerItemVH> {
    //listener per la gestione dei click e dei long click sulle card view associate ai peer
    private final SelectPeerCVListener selectPeerCVListener;

    public PeerListAdapter(SelectPeerCVListener listener) {
        super(DIFF_CALLBACK);
        selectPeerCVListener = listener;
    }

    public static final DiffUtil.ItemCallback<WifiP2pDevice> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WifiP2pDevice>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull WifiP2pDevice oldDevice, @NonNull WifiP2pDevice newDevice) {
                    return oldDevice.deviceAddress.equals(newDevice.deviceAddress);
                }
                @Override
                public boolean areContentsTheSame(
                        @NonNull WifiP2pDevice oldDevice, @NonNull WifiP2pDevice newDevice) {
                    return oldDevice.deviceName.equals(newDevice.deviceName) && oldDevice.status == newDevice.status;
                }
    };
    @NonNull
    @Override
    public PeerItemVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.peer_item_layout, parent, false);
        return new PeerItemVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerItemVH holder, int position) {
        holder.nameTxtView.setText(getItem(position).deviceName);
        holder.stateTxtView.setText(getDeviceStatus(getItem(position).status, holder.imageView.getContext()));
    }

    /*descrizione: ritorna il dispositivo connesso tramite wifi-direct*/
    public String getConnectedInvitedDeviceName(){
        for(WifiP2pDevice wifiP2pDevice : getCurrentList()){
            if(wifiP2pDevice.status == WifiP2pDevice.CONNECTED || wifiP2pDevice.status == WifiP2pDevice.INVITED){
                return wifiP2pDevice.deviceName;
            }
        }
        return null;
    }

    /*descrizione: ritorna il particolare stato di un dispositivo*/
    private String getDeviceStatus(int deviceStatus, Context context) {
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                return context.getString(R.string.device_available);
            case WifiP2pDevice.INVITED:
                return context.getString(R.string.device_invited);
            case WifiP2pDevice.CONNECTED:
                return context.getString(R.string.device_connected);
            case WifiP2pDevice.FAILED:
                return context.getString(R.string.device_failed);
            case WifiP2pDevice.UNAVAILABLE:
                return context.getString(R.string.device_unavailable);
            default:
                return context.getString(R.string.device_unknown);
        }
    }

    public class PeerItemVH extends RecyclerView.ViewHolder{
        private final ImageView imageView;
        private final TextView nameTxtView;
        private final TextView stateTxtView;

        public PeerItemVH(@NonNull View itemView) {
            super(itemView);
            nameTxtView = itemView.findViewById(R.id.peerNameTxt);
            stateTxtView = itemView.findViewById(R.id.peerStateTxt);
            imageView = itemView.findViewById(R.id.peerImgView);
            CardView cardView = itemView.findViewById(R.id.peerCV);

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta cliccata la
            //cardView
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int myPos = getAdapterPosition();
                    if(selectPeerCVListener != null && myPos!=RecyclerView.NO_POSITION)
                        selectPeerCVListener.onCVItemClicked(getItem(myPos));
                }
            });
        }
    }


    /* Interfaccia di gestione del click su una cardView del RecyclerView associato*/
    public interface SelectPeerCVListener{
        void onCVItemClicked(WifiP2pDevice wifiP2pDevice);
    }
}
