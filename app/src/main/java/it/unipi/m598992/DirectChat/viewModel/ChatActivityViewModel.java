package it.unipi.m598992.DirectChat.viewModel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import it.unipi.m598992.DirectChat.repository.MessageUserRepository;
import it.unipi.m598992.DirectChat.RoomDB.entity.Message;

public class ChatActivityViewModel extends AndroidViewModel {
    //nome del device associato alla chat
    private String deviceName;

    //istanza del repository per interagire con la sorgente dati
    private final MessageUserRepository messageUserRepository;

    //lista corrente di messaggi
    private LiveData<List<Message>> messageList;


    public ChatActivityViewModel(@NonNull Application application) {
        super(application);
        messageUserRepository = MessageUserRepository.getInstance(application);
    }

    //descrizione: ritorna tutti i messaggi associati all'utente deviceName
    public LiveData<List<Message>> getAllMessage(){
        if(messageList == null){
            // La lista viene richiesta una sola volta in tutto il ciclo di vita dell'activity
            // ---> la lista persiste, ad esempio, alla rotazione ---> evito di effettuare il lavoro ripetutamente
            messageList = messageUserRepository.getMessageFromTo(deviceName);
        }
        return messageList;
    }

    //descrizione: richiede al repository di cancellare l'insieme di messaggi in messageToDelete dalla sorgente dati
    public void deleteMessages(List<Message> messageToDelete){
        messageUserRepository.deleteMessages(messageToDelete);
    }

    //descrizione: setta il nome del device associato alla chat
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

}
