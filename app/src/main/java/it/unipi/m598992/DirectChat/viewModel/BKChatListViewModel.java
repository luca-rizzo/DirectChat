package it.unipi.m598992.DirectChat.viewModel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import it.unipi.m598992.DirectChat.repository.MessageUserRepository;
import it.unipi.m598992.DirectChat.RoomDB.entity.User;

public class BKChatListViewModel extends AndroidViewModel {
    //istanza del repository per interagire con la sorgente dati
    private final MessageUserRepository messageUserRepository;

    //lista corrente di chat con utenti
    private LiveData<List<User>> userList;

    public BKChatListViewModel(@NonNull Application application) {
        super(application);
        messageUserRepository = MessageUserRepository.getInstance(application);
    }

    //descrizione: ritorna la lista di tutte le chat salvate
    public LiveData<List<User>> getAllUser(){
        if(userList == null){
            //messageList is directly requested only once in the lifecycle of the activity
            userList = messageUserRepository.getAllUserChatList();
        }
        return userList;
    }
}
