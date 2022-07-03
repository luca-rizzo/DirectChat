package it.unipi.m598992.DirectChat.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.ListIterator;
import it.unipi.m598992.DirectChat.R;
import it.unipi.m598992.DirectChat.RoomDB.entity.User;

public class BKChatListAdapter extends ListAdapter<User, BKChatListAdapter.UserChatVH> {
    //chat che l'utente ha selezionato tramite actionMode
    private final ArrayList<User> selectedUserChats = new ArrayList<>();

    //listener per la gestione dei click e dei long click sulle card view associate alle chat
    private final SelectUserCVListener selectUserCVListener;

    public BKChatListAdapter(SelectUserCVListener listener) {
        super(DIFF_CALLBACK);
        this.selectUserCVListener = listener;
    }

    public static final DiffUtil.ItemCallback<User> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<User>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull User oldUsr, @NonNull User newUsr) {
                    return oldUsr.getName().equals(newUsr.getName());
                }
                @Override
                public boolean areContentsTheSame(
                        @NonNull User oldUsr, @NonNull User newUsr) {
                    return oldUsr.getName().equals(newUsr.getName());
                }
            };

    @NonNull
    @Override
    public BKChatListAdapter.UserChatVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.user_bk_chat_item, parent, false);
        return new BKChatListAdapter.UserChatVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BKChatListAdapter.UserChatVH holder, int position) {
        holder.userName.setText(getItem(position).getName());
        if(selectedUserChats.contains(getItem(position))){
            //se la cardView è tra quelle selezionate la segno in rosso
            holder.cardView.setCardBackgroundColor(Color.RED);
        }
        else{
            holder.cardView.setCardBackgroundColor(Color.WHITE);
        }
    }

    public class UserChatVH extends RecyclerView.ViewHolder{
        public final TextView userName;
        public final CardView cardView;

        public UserChatVH(@NonNull View itemView) {
            super(itemView);
            userName = itemView.findViewById(R.id.userName);
            cardView = itemView.findViewById(R.id.userCV);

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta cliccata la
            //cardView
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int myPos = getAdapterPosition();
                    if(selectUserCVListener != null && myPos!=RecyclerView.NO_POSITION)
                        selectUserCVListener.onCVItemClick(getItem(myPos));
                }
            });
            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //long click sulla cardView
            cardView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    int myPos = getAdapterPosition();
                    if(selectUserCVListener != null && myPos != RecyclerView.NO_POSITION){
                        selectUserCVListener.onCVItemLongClick(getItem(myPos));
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    //descrizione: aggiunge la chat tra quelle selezionate e notifica l'adapter che cambierà la sua visualizzazione
    public void addToSelectedUserChats(User user){
        this.selectedUserChats.add(user);
        notifyItemChanged(getCurrentList().indexOf(user));
    }

    //descrizione: rimuove la chat tra quelle selezionate e notifica l'adapter che cambierà la sua visualizzazione
    public void removeFromSelectedUserChats(User user){
        this.selectedUserChats.remove(user);
        notifyItemChanged(getCurrentList().indexOf(user));
    }

    //descrizione: rimuove tutte le chat selezionate e per ciascuna notifica l'adapter che cambierà la sua visualizzazione
    public void removeAllSelectedChats(){
        ListIterator<User> iter = selectedUserChats.listIterator();
        while(iter.hasNext()){
            User msg = iter.next();
            iter.remove();
            notifyItemChanged(getCurrentList().indexOf(msg));
        }
    }

    //descrizione: ritorna true se la data chat è stata selezionata
    public boolean isThisUserChatSelected(User user){
        return this.selectedUserChats.contains(user);
    }

    //descrizione: ritorna il numero di chat selezionate
    public int getNumberOfChatSelected(){
        return this.selectedUserChats.size();
    }

    //descrizione: ritorna una copia delle chat (utenti associati) selezionate
    public ArrayList<User> getCopyCurrentSelectedUserChats(){
        return new ArrayList<>(selectedUserChats);
    }

    /* Interfaccia di gestione del click su una cardView del RecyclerView associato*/
    public interface SelectUserCVListener{
        void onCVItemClick(User user);
        void onCVItemLongClick(User user);
    }
}
