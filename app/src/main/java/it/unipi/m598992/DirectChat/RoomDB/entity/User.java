package it.unipi.m598992.DirectChat.RoomDB.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class User {

    @PrimaryKey @NonNull
    private String name;

    public User(@NonNull String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
