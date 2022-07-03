package it.unipi.m598992.DirectChat.RoomDB.entity;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;
import java.util.Objects;

@Entity
public class Message implements Parcelable {
    public static final int TXT = 0;
    public static final int IMAGE = 1;
    public static final int REC = 2;
    public static final int VIDEO = 3;
    public static final int AUDIO = 4;
    public static final int GENERAL_FILE = 5;


    @PrimaryKey(autoGenerate = true)
    private int _ID;

    private Date date;

    private int type;

    @ColumnInfo(name = "content_uri")
    private String contentUri;

    @ColumnInfo(name = "text_msg")
    private String textMsg;

    private boolean received;

    @ColumnInfo(name = "user_name")
    private String userName;

    public Message(Date date, int type, String contentUri, String textMsg, boolean received, String userName) {
        this.date = date;
        this.type = type;
        this.contentUri = contentUri;
        this.textMsg = textMsg;
        this.received = received;
        this.userName = userName;
    }

    public void set_ID(int _ID) {
        this._ID = _ID;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int get_ID() {
        return _ID;
    }

    public Date getDate() {
        return date;
    }

    public int getType() {
        return type;
    }

    public String getContentUri() {
        return contentUri;
    }

    public String getTextMsg() {
        return textMsg;
    }

    public boolean isReceived() {
        return received;
    }

    public String getUserName() {
        return userName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return _ID == message._ID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_ID);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(_ID);
        parcel.writeInt(type);
        parcel.writeString(contentUri);
        parcel.writeString(textMsg);
        parcel.writeByte((byte) (received ? 1 : 0));
        parcel.writeString(userName);
    }

    protected Message(Parcel in) {
        _ID = in.readInt();
        type = in.readInt();
        contentUri = in.readString();
        textMsg = in.readString();
        received = in.readByte() != 0;
        userName = in.readString();
    }

    public static final Creator<Message> CREATOR = new Creator<Message>() {
        @Override
        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }

        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }
    };
}
