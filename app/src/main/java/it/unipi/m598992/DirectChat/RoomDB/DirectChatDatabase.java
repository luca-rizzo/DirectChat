package it.unipi.m598992.DirectChat.RoomDB;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import it.unipi.m598992.DirectChat.RoomDB.entity.Message;
import it.unipi.m598992.DirectChat.RoomDB.entity.User;

@Database(entities = {User.class, Message.class}, version = 1, exportSchema = false)
@TypeConverters({Converters.class})

public abstract class DirectChatDatabase extends RoomDatabase {
    //metodi per ottenere oggetti che consentono di interagire con la base di dati
    public abstract UserDao getUserDao();
    public abstract MessageDao getMessageDao();

    //**** PATTERN SINGLETON *****//
    private static DirectChatDatabase instance;
    public static synchronized DirectChatDatabase getInstance(Context context){
        if(instance==null){
            instance = Room.databaseBuilder(context.getApplicationContext(), DirectChatDatabase.class, "direct_chat_database").fallbackToDestructiveMigration().build();
        }
        return instance;
    }
}
