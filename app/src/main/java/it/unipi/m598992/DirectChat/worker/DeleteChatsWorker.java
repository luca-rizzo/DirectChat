package it.unipi.m598992.DirectChat.worker;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import it.unipi.m598992.DirectChat.RoomDB.entity.Message;
import it.unipi.m598992.DirectChat.activity.MainActivity;
import it.unipi.m598992.DirectChat.repository.MessageUserRepository;

public class DeleteChatsWorker extends Worker {
    public final static String CHATS_TO_DELETE = "it.unipi.m598992.directChat.chatsToDelete";
    private final MessageUserRepository messageUserRepository;
    public DeleteChatsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        messageUserRepository = MessageUserRepository.getInstance((Application) getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        ContentResolver resolver = getApplicationContext().getContentResolver();
        //ottengo la lista di tutti gli utenti di cui devo eliminare messaggi e file associati
        String[] arrayUserChatToDelete = getInputData().getStringArray(CHATS_TO_DELETE);
        if(arrayUserChatToDelete != null){
            for(String userName : arrayUserChatToDelete){
                //utilizzo approccio bloccante per ottenere la lista di tutti i messaggi
                List<Message> userMessages = messageUserRepository.getMessageFromToSync(userName);
                if(userMessages != null){
                    for(Message currentMessage : userMessages){
                        Log.d(MainActivity.TAG, "Deleting: " + currentMessage.getContentUri());
                        if(currentMessage.getType() != Message.TXT && !currentMessage.getContentUri().isEmpty()){
                            Uri fileUriToDelete = Uri.parse(currentMessage.getContentUri());
                            if(fileUriToDelete.getAuthority().equals("it.unipi.m598992.DirectChat.fileprovider")){
                                resolver.delete(fileUriToDelete,null,null);
                            }
                        }
                    }
                }
                //elimino tutti i messaggi dal database
                messageUserRepository.deleteAllUserMessages(userName);
            }
        }
        return Result.success();
    }
}
