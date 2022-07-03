package it.unipi.m598992.DirectChat.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.unipi.m598992.DirectChat.RoomDB.MessageDao;
import it.unipi.m598992.DirectChat.RoomDB.UserDao;
import it.unipi.m598992.DirectChat.RoomDB.DirectChatDatabase;
import it.unipi.m598992.DirectChat.RoomDB.entity.Message;
import it.unipi.m598992.DirectChat.RoomDB.entity.User;

public class MessageUserRepository {
    //oggetti Dao per interagire con la base di dati
    private final UserDao userDao;
    private final MessageDao messageDao;

    //ExecutorService per gestire le richieste sulla sorgente dati (database in questo caso) sfruttando i thread del threadPool associato
    private final ExecutorService executorService;

    //Pattern singleton
    private static MessageUserRepository instance;
    public static synchronized MessageUserRepository getInstance(Application application){
        if(instance==null){
            instance = new MessageUserRepository(application);
        }
        return instance;
    }

    private MessageUserRepository(Application application){
        //ottengo la sorgente dati su cui opererà il repository
        DirectChatDatabase directChatDatabase = DirectChatDatabase.getInstance(application);
        //ottengo i Dao per modificare il database
        userDao = directChatDatabase.getUserDao();
        messageDao = directChatDatabase.getMessageDao();
        executorService = Executors.newCachedThreadPool();
    }

    //descrizione: inserisce messaggio message nel database
    public void insertMessage(Message message){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                messageDao.insert(message);
            }
        });
    }

    //descrizione: inserisce utente user nel database
    public void insertUser(User user){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                userDao.insert(user);
            }
        });
    }

    //descrizione: preleva tutti i messaggi inviati o ricevuti da deviceName
    public LiveData<List<Message>> getMessageFromTo(String userName){
        //Room esegue query con LiveData in un background thread --> i dati saranno aggiornati tramite observer
        return messageDao.getAllMessageFromTo(userName);
    }

    //descrizione: preleva tutti i messaggi inviati o ricevuti in modalità sincrona/bloccante. Da eseguire in un background thread
    public List<Message> getMessageFromToSync(String userName){
        //Accede in modalità bloccante al database
        return messageDao.getAllMessageFromToSync(userName);
    }

    //descrizione: preleva tutti i messaggi inviati o ricevuti da deviceName di un dato tipo
    public LiveData<List<Message>> getMessageFromToType(String deviceName, HashSet<Integer> messageType){
        //Room esegue query con LiveData in un background thread
        return messageDao.getAllMessageFromToType(deviceName, new ArrayList<>(messageType));
    }

    //descrizione: ritorna la lista degli utenti con una chat esistente (almeno un messaggio presente)
    public LiveData<List<User>> getAllUserChatList(){
        //Room esegue query con LiveData in un background thread
        return userDao.getAllUserChatList();
    }

    //descrizione: elimina dalla sorgente dati (dataBase in questo caso) tutti i messaggi associati all'utente user
    public void deleteAllUserMessages(String userName){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                messageDao.deleteAllUserMessage(userName);
            }
        });
    }

    //descrizione: elimina dalla sorgente dati (dataBase in questo caso) tutti i messaggi contenuti in messageToDelete
    public void deleteMessages(List<Message> messageToDelete){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                messageDao.deleteList(messageToDelete);
            }
        });
    }
}
