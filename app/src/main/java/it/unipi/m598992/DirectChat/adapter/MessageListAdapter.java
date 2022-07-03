package it.unipi.m598992.DirectChat.adapter;


import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import it.unipi.m598992.DirectChat.R;
import it.unipi.m598992.DirectChat.RoomDB.entity.Message;

public class MessageListAdapter extends ListAdapter<Message, RecyclerView.ViewHolder> {

    //riferimento al VH dell'audio in riproduzione
    public Recording_AudioMessageVH recording_AudioMessageVH;

    //riferimento al messaggio in riproduzione/pausa
    public Message msgAudioBeingPlaying;
    //booleano che indica se il messaggio è in pausa o in riproduzione
    public boolean isAudioPaused;

    //listener per la gestione dei click e dei long click sulle card view associate ai messaggi
    private final SelectMessageCVListener selectMessageCVListener;
    //listener per la gestione dei click ai bottone playPause per la riproduzione della musica
    private final PlayPauseButtonListener playPauseButtonListener;
    //listener per lo scroll della recycler view a cui l'adapter è associato
    private final ScrollRVListener scrollRVistener;

    //lista di messaggi selezionati tramite actionMode
    private final ArrayList<Message> currentSelectedMsg;

    private final SimpleDateFormat format;


    public MessageListAdapter(PlayPauseButtonListener playPauseButtonListener, SelectMessageCVListener selectMessageCVListener, ScrollRVListener scrollRVistener) {
        super(DIFF_CALLBACK);
        this.selectMessageCVListener = selectMessageCVListener;
        this.playPauseButtonListener = playPauseButtonListener;
        this.scrollRVistener = scrollRVistener;
        this.currentSelectedMsg = new ArrayList<>();
        format = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy", java.util.Locale.getDefault());
    }

    public static final DiffUtil.ItemCallback<Message> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Message>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull Message oldMsg, @NonNull Message newMsg) {
                    return oldMsg.get_ID() == newMsg.get_ID();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull Message oldMsg, @NonNull Message newMsg) {
                    if (oldMsg.getType() == Message.TXT && newMsg.getType() == Message.TXT) {
                        return oldMsg.getTextMsg().equals(newMsg.getTextMsg()) && oldMsg.getDate().equals(newMsg.getDate());
                    } else {
                        return oldMsg.getContentUri().equals(newMsg.getContentUri()) && oldMsg.getDate().equals(newMsg.getDate());
                    }
                }
            };

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v;
        //a seconda del tipo del messaggio ritorno il View Holder corrispondente
        switch (viewType) {
            case (Message.IMAGE):
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_image_item_layout, parent, false);
                return new Image_VideoMessageVH(v);
            case (Message.VIDEO):
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_video_item_layout, parent, false);
                return new Image_VideoMessageVH(v);
            case (Message.TXT):
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_txt_item_layout, parent, false);
                return new TxtMessageVH(v);
            case (Message.REC):
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_rec_item_layout, parent, false);
                return new Recording_AudioMessageVH(v);
            case (Message.AUDIO):
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_audio_item_layout, parent, false);
                return new Recording_AudioMessageVH(v);
            default:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_general_file_layout, parent, false);
                return new GeneralFileVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = getItem(position);
        switch (holder.getItemViewType()) {
            case (Message.VIDEO):
            case (Message.IMAGE): {
                Image_VideoMessageVH imageMessageVH = (Image_VideoMessageVH) holder;
                //sfrutto glide per caricare immagine/thumbnail video nell'ImageView
                Glide.with(imageMessageVH.imageView.getContext()).load(msg.getContentUri()).centerCrop().into(imageMessageVH.imageView);
                imageMessageVH.date.setText(format.format(msg.getDate()));
                setCVColorAndMargin(imageMessageVH.cardView, msg.isReceived(), position);
                break;
            }
            case (Message.TXT): {
                TxtMessageVH txtMessageItemVH = (TxtMessageVH) holder;
                txtMessageItemVH.txtMessage.setText(msg.getTextMsg());
                txtMessageItemVH.date.setText(format.format(msg.getDate()));
                setCVColorAndMargin(txtMessageItemVH.cardView, msg.isReceived(), position);
                break;
            }
            case (Message.GENERAL_FILE): {
                GeneralFileVH generalFileVH = (GeneralFileVH) holder;
                DocumentFile file = DocumentFile.fromSingleUri(generalFileVH.cardView.getContext(), Uri.parse(msg.getContentUri()));
                if (file != null && file.getName() != null) {
                    //se il file esiste setto il nome del file
                    generalFileVH.fileName.setText(file.getName());
                }
                generalFileVH.date.setText(format.format(msg.getDate()));
                setCVColorAndMargin(generalFileVH.cardView, msg.isReceived(), position);
                break;
            }
            default: {
                Recording_AudioMessageVH recording_AudioMessageVH = (Recording_AudioMessageVH) holder;
                recording_AudioMessageVH.date.setText(format.format(msg.getDate()));
                if(holder.getItemViewType() == Message.AUDIO){
                    DocumentFile file = DocumentFile.fromSingleUri(recording_AudioMessageVH.cardView.getContext(), Uri.parse(msg.getContentUri()));
                    if (file != null && file.getName() != null) {
                        //se l'audio esiste setto il nome dell'audio
                        recording_AudioMessageVH.audioFileName.setText(removeExtension(file.getName()));
                    }
                }

                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                Uri uri = Uri.parse(msg.getContentUri());
                try {
                    //sfrutto MediaMetadataRetriever per ottenere la lunghezza dell'audio
                    mmr.setDataSource(holder.itemView.getContext(), uri);
                    String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    int millSecond = Integer.parseInt(durationStr);
                    recording_AudioMessageVH.audioSeekBar.setMax(millSecond);
                    recording_AudioMessageVH.audioSeekBar.setProgress(0);
                } catch (IllegalArgumentException e) {
                    recording_AudioMessageVH.audioSeekBar.setMax(0);
                    recording_AudioMessageVH.audioSeekBar.setProgress(0);
                }
                if (getItem(position).equals(msgAudioBeingPlaying)) {
                    //si tratta dell'audio in riproduzione o in pausa ---> ripristino stato
                    if (isAudioPaused) {
                        //un audio in riproduzione è messo in pausa
                        recording_AudioMessageVH.playPauseButton.setImageResource(R.drawable.play_circle_filled);
                        //non ho più la necessità di gestire progresso seekBar per il momento
                        playPauseButtonListener.stopSeekBarHandler();
                    } else {
                        //un audio in pausa è stato ripreso
                        recording_AudioMessageVH.playPauseButton.setImageResource(R.drawable.pause_circle_filled_image);
                        //riprendo a gestire progresso seekBar
                        playPauseButtonListener.startSeekBarHandler(recording_AudioMessageVH.audioSeekBar);
                    }
                    //setto la seekBar al progresso corrente del media player
                    recording_AudioMessageVH.audioSeekBar.setProgress(playPauseButtonListener.getMediaPlayerPos());
                    //associo la seekBar al media player
                    recording_AudioMessageVH.audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if (fromUser)
                                playPauseButtonListener.seekMediaPlayerToPos(progress);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    });
                    //salvo il riferimento al VH corrente per stoppare handler una volta che tale VH scompare dallo schermo
                    this.recording_AudioMessageVH = recording_AudioMessageVH;
                } else {
                    //non si tratta dell'audio in riproduzione --> resetto tutto
                    recording_AudioMessageVH.playPauseButton.setImageResource(R.drawable.play_circle_filled);
                    recording_AudioMessageVH.audioSeekBar.setProgress(0);
                    recording_AudioMessageVH.audioSeekBar.setOnSeekBarChangeListener(null);
                }
                setCVColorAndMargin(recording_AudioMessageVH.cardView, msg.isReceived(), position);
                setImgBtnAndSeekBarColor(recording_AudioMessageVH.audioSeekBar, recording_AudioMessageVH.playPauseButton, msg.isReceived());
            }
        }
    }



    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder == recording_AudioMessageVH) {
            //stoppo handler associato all'aggiornamento della seekbar
            playPauseButtonListener.stopSeekBarHandler();
        }
    }

    private void setCVColorAndMargin(CardView cv, boolean received, int position) {
        if (received) {
            //si tratta di un messaggio ricevuto
            cv.setCardBackgroundColor(cv.getContext().getColor(R.color.colorPrimary));
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            //allineo messaggio a sinistra
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            //aggiungo margine a destra
            params.setMargins(0, 0, 140, 0);
            cv.setLayoutParams(params);
        } else {
            cv.setCardBackgroundColor(cv.getContext().getResources().getColor(R.color.colorSecondary));
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            //allineo messaggio a destra
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            //aggiungo margine a sinistra
            params.setMargins(140, 0, 0, 0);
            cv.setLayoutParams(params);
        }
        cv.requestLayout();
        if (currentSelectedMsg.contains(getItem(position))) {
            //il messaggio è selezionato
            cv.setCardBackgroundColor(Color.RED);
        }
    }

    private void setImgBtnAndSeekBarColor(SeekBar audioSeekBar, ImageButton playPauseButton, boolean received) {
        if(received){
            playPauseButton.setBackgroundTintList(ContextCompat.getColorStateList(playPauseButton.getContext(), R.color.colorPrimaryDark));
            audioSeekBar.setProgressTintList(ContextCompat.getColorStateList(playPauseButton.getContext(), R.color.colorPrimaryDark));
            audioSeekBar.setThumbTintList(ContextCompat.getColorStateList(playPauseButton.getContext(), R.color.colorPrimaryDark));
        } else{
            playPauseButton.setBackgroundTintList(ContextCompat.getColorStateList(playPauseButton.getContext(), R.color.colorSecondaryDark));
            audioSeekBar.setProgressTintList(ContextCompat.getColorStateList(playPauseButton.getContext(), R.color.colorSecondaryDark));
            audioSeekBar.setThumbTintList(ContextCompat.getColorStateList(playPauseButton.getContext(), R.color.colorSecondaryDark));
        }
    }

    //descrizione: prende in input il nome di un file con estensione e ritorna la stringa costtuita dal nome del file senza estensione
    public String removeExtension(String fileName){
        if (fileName.indexOf(".") > 0) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        } else {
            return fileName;
        }
    }


    @Override
    public int getItemViewType(int position) {
        return getItem(position).getType();
    }

    //ViewHolder associato ad un messaggio di Testo
    public class TxtMessageVH extends RecyclerView.ViewHolder {
        private final TextView txtMessage;
        private final TextView date;
        private final CardView cardView;

        public TxtMessageVH(@NonNull View itemView) {
            super(itemView);
            txtMessage = itemView.findViewById(R.id.msgTxt);
            date =  itemView.findViewById(R.id.msgDate);
            cardView = itemView.findViewById(R.id.messageCV);

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //click sulla cardView
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int myPos = getAdapterPosition();
                    if (selectMessageCVListener != null && myPos != RecyclerView.NO_POSITION)
                        selectMessageCVListener.onCVMessageClick(getItem(myPos));
                }
            });

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //long click sulla cardView
            cardView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    int myPos = getAdapterPosition();
                    if (selectMessageCVListener != null && myPos != RecyclerView.NO_POSITION) {
                        selectMessageCVListener.onCVMessageLongClick(getItem(myPos));
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    //ViewHolder associato ad un messaggio con immagini o video
    public class Image_VideoMessageVH extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final TextView date;
        private final CardView cardView;

        public Image_VideoMessageVH(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.msgImg);
            date = itemView.findViewById(R.id.msgDate);
            cardView = itemView.findViewById(R.id.messageCV);

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //click sulla cardView
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int myPos = getAdapterPosition();
                    if (selectMessageCVListener != null && myPos != RecyclerView.NO_POSITION)
                        selectMessageCVListener.onCVMessageClick(getItem(myPos));
                }
            });

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //long click sulla cardView
            cardView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    int myPos = getAdapterPosition();
                    if (selectMessageCVListener != null && myPos != RecyclerView.NO_POSITION) {
                        selectMessageCVListener.onCVMessageLongClick(getItem(myPos));
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    //ViewHolder associato ad un messaggio con audio o registrazioni
    public class Recording_AudioMessageVH extends RecyclerView.ViewHolder {
        private final CardView cardView;
        private final ImageButton playPauseButton;
        private final TextView date;
        private final SeekBar audioSeekBar;
        private final TextView audioFileName;
        public Recording_AudioMessageVH(@NonNull View itemView) {
            super(itemView);
            audioFileName = itemView.findViewById(R.id.audioFileName);
            cardView = itemView.findViewById(R.id.messageCV);
            date = itemView.findViewById(R.id.msgDate);
            playPauseButton = itemView.findViewById(R.id.playStopButton);
            audioSeekBar = itemView.findViewById(R.id.seekBar);
            Recording_AudioMessageVH recordingMessageVH = this;

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //click sulla cardView
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int myPos = getAdapterPosition();
                    if (selectMessageCVListener != null && myPos != RecyclerView.NO_POSITION)
                        selectMessageCVListener.onCVMessageClick(getItem(myPos));
                }
            });

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //long click sulla cardView
            cardView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    int myPos = getAdapterPosition();
                    if (selectMessageCVListener != null && myPos != RecyclerView.NO_POSITION) {
                        selectMessageCVListener.onCVMessageLongClick(getItem(myPos));
                        return true;
                    }
                    return false;
                }
            });

            //setto il listener del bottone play/pause affinche chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //click sul bottone
            playPauseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int myPos = getAdapterPosition();
                    if (playPauseButtonListener != null && myPos != RecyclerView.NO_POSITION)
                        playPauseButtonListener.onPlayPauseButtonListener(getItem(myPos), recordingMessageVH);
                }
            });
        }

        public SeekBar getAudioSeekBar() {
            return audioSeekBar;
        }
    }

    //ViewHolder associato ad un messaggio con audio o registrazioni
    public class GeneralFileVH extends RecyclerView.ViewHolder {

        private final TextView date;
        private final TextView fileName;
        private final CardView cardView;

        public GeneralFileVH(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileNameTxt);
            date = itemView.findViewById(R.id.msgDate);
            cardView = itemView.findViewById(R.id.messageCV);

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //click sulla cardView
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int myPos = getAdapterPosition();
                    if (selectMessageCVListener != null && myPos != RecyclerView.NO_POSITION)
                        selectMessageCVListener.onCVMessageClick(getItem(myPos));
                }
            });

            //setto il listener della cardView affinchè chiami il metodo del listener passato all'adapter una volta che ho effettuato un
            //long click sulla cardView
            cardView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    int myPos = getAdapterPosition();
                    if (selectMessageCVListener != null && myPos != RecyclerView.NO_POSITION) {
                        selectMessageCVListener.onCVMessageLongClick(getItem(myPos));
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    @Override
    public void onCurrentListChanged(@NonNull List<Message> previousList, @NonNull List<Message> currentList) {
        super.onCurrentListChanged(previousList, currentList);
        //se a cambiare è l'ultimo elemento (ho ricevuto un nuovo messaggio o ho cancellato ulimo messaggio) effettuo scroll all'ultimo elemento
        if (shouldScroll(previousList, currentList)) {
            scrollRVistener.scrollRV();
        }
    }

    //descrizione: ritorna true se si dovrebbe scrollare (ho ricevuto un nuovo messaggio o ho cancellato ulimo messaggio); false altrimenti
    private boolean shouldScroll(@NonNull List<Message> previousList, @NonNull List<Message> currentList) {
        return scrollRVistener != null && previousList.size() > 0 && currentList.size() > 0 && previousList.get(0) != null && currentList.get(0) != null && !currentList.get(0).equals(previousList.get(0));
    }

    /* Interfaccia di gestione del click su una cardView del RecyclerView associato*/
    public interface SelectMessageCVListener {
        void onCVMessageClick(Message message);
        void onCVMessageLongClick(Message message);
    }

    /* Interfaccia di gestione del click su bottone per la riproduzione di audio*/
    public interface PlayPauseButtonListener {
        void onPlayPauseButtonListener(Message message, Recording_AudioMessageVH VH);

        void seekMediaPlayerToPos(int position);

        int getMediaPlayerPos();

        void startSeekBarHandler(SeekBar audioSeekBar);

        void stopSeekBarHandler();
    }

    /* Interfaccia di gestione dello scroll della recycler view associata*/
    public interface ScrollRVListener {
        void scrollRV();
    }

    public void setMsgAudioBeingPlaying(Message msgAudioBeingPlaying) {
        int previousAudioPosition = -1;
        if (this.msgAudioBeingPlaying != null && this.msgAudioBeingPlaying.equals(msgAudioBeingPlaying) && !isAudioPaused) {
            //ho stoppato un audio in riproduzione
            isAudioPaused = true;
            notifyItemChanged(getCurrentList().indexOf(msgAudioBeingPlaying));
            return;
        } else if (this.msgAudioBeingPlaying != null && this.msgAudioBeingPlaying.equals(msgAudioBeingPlaying)) {
            //ho ripreso un audio in pausa
            isAudioPaused = false;
            notifyItemChanged(getCurrentList().indexOf(msgAudioBeingPlaying));
            return;
        } else {
            isAudioPaused = false;
        }

        if (this.msgAudioBeingPlaying != null) {
            //voglio stoppare precedente audio in pausa/riproduzione
            previousAudioPosition = getCurrentList().indexOf(this.msgAudioBeingPlaying);
            playPauseButtonListener.stopSeekBarHandler();
        }
        this.msgAudioBeingPlaying = msgAudioBeingPlaying;
        if (previousAudioPosition != -1)
            //aggiorno ViewHolder associato al precedente messaggio in riproduzione
            notifyItemChanged(previousAudioPosition);
        if (msgAudioBeingPlaying != null)
            //aggiorno ViewHolder per indicare audio in riproduzone
            notifyItemChanged(getCurrentList().indexOf(msgAudioBeingPlaying));
    }

    //descrizione: ritorna l'audio corrente in riproduzione
    public Message getMsgAudioBeingPlaying() {
        return msgAudioBeingPlaying;
    }

    //descrizione: aggiunge il messaggio tra quelli selezionati e notifica l'adapter che cambierà la sua visualizzazione
    public void addSelectedMessage(Message msg) {
        currentSelectedMsg.add(msg);
        notifyItemChanged(getCurrentList().indexOf(msg));
    }

    //descrizione: rimuove il messaggio tra quelli selezionati e notifica l'adapter che cambierà la sua visualizzazione
    public void removeSelectedMessage(Message msg) {
        currentSelectedMsg.remove(msg);
        notifyItemChanged(getCurrentList().indexOf(msg));
    }

    //descrizione: ritorna il numero di messaggi selezionati
    public int getNumberOfSelectedMessage() {
        return currentSelectedMsg.size();
    }

    //descrizione: ritorna una copia dei messaggi selezionati
    public ArrayList<Message> getCopyCurrectSelectedMessage() {
        return new ArrayList<>(currentSelectedMsg);
    }

    //descrizione: rimuove tutti i messaggi selezionati e per ciascuno notifico l'adapter che cambierà la sua visualizzazione
    public void removeAllSelectedMessage() {
        ListIterator<Message> iter = currentSelectedMsg.listIterator();
        while (iter.hasNext()) {
            Message msg = iter.next();
            iter.remove();
            notifyItemChanged(getCurrentList().indexOf(msg));
        }
    }

    //descrizione: ritorna true se il dato messaggio è stata selezionato
    public boolean isThisMessageSelected(Message msg) {
        return currentSelectedMsg.contains(msg);
    }
}
