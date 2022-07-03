package it.unipi.m598992.DirectChat.RoomDB;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import it.unipi.m598992.DirectChat.RoomDB.entity.Message;

@Dao
public interface MessageDao {

    @Insert
    void insert(Message message);

    @Delete
    void deleteList(List<Message> messageToDelete);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Message message);

    @Query("SELECT * FROM Message WHERE user_name = :name ORDER BY date DESC")
    LiveData<List<Message>> getAllMessageFromTo(String name);

    @Query("SELECT * FROM Message WHERE user_name = :name ORDER BY date DESC")
    List<Message> getAllMessageFromToSync(String name);

    @Query("DELETE FROM Message WHERE user_name = :user_name")
    void deleteAllUserMessage(String user_name);

    @Query("SELECT * FROM Message WHERE user_name = :user_name and type IN (:messageType) ORDER BY date DESC")
    LiveData<List<Message>> getAllMessageFromToType(String user_name, List<Integer> messageType);
}
