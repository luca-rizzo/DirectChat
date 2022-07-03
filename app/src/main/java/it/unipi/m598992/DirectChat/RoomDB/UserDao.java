package it.unipi.m598992.DirectChat.RoomDB;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import it.unipi.m598992.DirectChat.RoomDB.entity.User;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(User user);

    @Query("SELECT User.name FROM User JOIN Message ON User.name = Message.user_name GROUP BY Message.user_name HAVING COUNT(*) > 0")
    LiveData<List<User>> getAllUserChatList();
}
