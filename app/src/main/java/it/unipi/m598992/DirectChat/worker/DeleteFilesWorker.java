package it.unipi.m598992.DirectChat.worker;


import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class DeleteFilesWorker extends Worker {
    public final static String FILES_TO_DELETE = "it.unipi.m598992.directChat.filesToDelete";

    public DeleteFilesWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }
    @NonNull
    @Override
    public Result doWork() {
        ContentResolver resolver = getApplicationContext().getContentResolver();

        //prelevo la lista di file da eliminare
        String[] arrayFileUriToDelete = getInputData().getStringArray(FILES_TO_DELETE);
        if(arrayFileUriToDelete != null){
            for(String fileUriToDelete : arrayFileUriToDelete){
                Uri fileUri = Uri.parse(fileUriToDelete);
                //controllo se ho i permessi per cancellare il file tramite URI --> li ho solo se sono relativi al mio fileProvider
                if(fileUri.getAuthority().equals("it.unipi.m598992.DirectChat.fileprovider")){
                    resolver.delete(fileUri,null,null);
                }
            }
        }
        return Result.success();
    }
}
