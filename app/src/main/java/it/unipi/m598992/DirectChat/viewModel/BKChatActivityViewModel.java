package it.unipi.m598992.DirectChat.viewModel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import it.unipi.m598992.DirectChat.repository.MessageUserRepository;
import it.unipi.m598992.DirectChat.RoomDB.entity.Message;

public class BKChatActivityViewModel extends AndroidViewModel {

    //nome del device associato alla chat
    private String deviceName;

    //istanza del repository per interagire con la sorgente dati
    private final MessageUserRepository messageUserRepository;

    //lista corrente di messaggi
    private LiveData<List<Message>> messageList;

    //insieme di tipi che l'utente vuole mostrare
    private final HashSet<Integer> selectedMessageType;

    public BKChatActivityViewModel(@NonNull Application application) {
        //application is needed to create the DB
        super(application);
        messageUserRepository = MessageUserRepository.getInstance(application);
        //inizialmente l'utente visualizza tutti i tipi di messaggi
        selectedMessageType = new HashSet<Integer>(Arrays.asList(Message.TXT, Message.GENERAL_FILE, Message.VIDEO, Message.IMAGE, Message.REC, Message.AUDIO)) {
        };
    }

    //descrizione: ritorna tutti i messaggi associati all'utente a seconda dei tipi attualmente nel set selectedMessageType
    public LiveData<List<Message>> getAllMessage(){
        if(messageList == null){
            // La lista viene richiesta una sola volta in tutto il ciclo di vita dell'activity
            // la lista persiste, ad esempio, alla rotazione ---> evito di effettuare il lavoro ripetutamente
            messageList = messageUserRepository.getMessageFromToType(deviceName, selectedMessageType);
        }
        return messageList;
    }

    //descrizione: richiede al repository di cancellare l'insieme di messaggi in messageToDelete dalla sorgente dati
    public void deleteMessages(List<Message> messageToDelete){
        messageUserRepository.deleteMessages(messageToDelete);
    }

    //descrizione: imposta i tipi di messaggio che l'utente richiede di visualizzare
    public void setSelectedMessageType(HashSet<Integer> selectedMessageType){
        this.selectedMessageType.clear();
        this.selectedMessageType.addAll(selectedMessageType);
        messageList = messageUserRepository.getMessageFromToType(deviceName, this.selectedMessageType);
    }

    //descrizione: setta il nome del device associato alla chat
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    //descrizione: ritorna una copia dei tipi di messaggio che l'utente ha scelto di visualizzare
    public HashSet<Integer> getCopySelectedMessageType() {
        return new HashSet<>(selectedMessageType);
    }
}
