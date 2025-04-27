package com.example.secuphone.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying encrypted files in a RecyclerView.
 */
public class EncryptedFilesAdapter extends RecyclerView.Adapter<EncryptedFilesAdapter.ViewHolder> {

    private final Context context;
    private final List<File> files;
    private final EncryptedFileListener listener;
    private final SimpleDateFormat dateFormat;

    /**
     * Interface for file interactions.
     */
    public interface EncryptedFileListener {
        void onFileSelected(File file);
        void onFileDeleteRequested(File file);
    }

    public EncryptedFilesAdapter(Context context, List<File> files, EncryptedFileListener listener) {
        this.context = context;
        this.files = files;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_encrypted_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = files.get(position);
        
        // Set file name (remove .enc extension if present)
        String fileName = file.getName();
        if (fileName.endsWith(".enc")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        holder.fileNameText.setText(fileName);
        
        // Set file size
        long fileSizeKb = file.length() / 1024;
        if (fileSizeKb < 1024) {
            holder.fileSizeText.setText(String.format(Locale.getDefault(), "%d KB", fileSizeKb));
        } else {
            float fileSizeMb = fileSizeKb / 1024f;
            holder.fileSizeText.setText(String.format(Locale.getDefault(), "%.1f MB", fileSizeMb));
        }
        
        // Set last modified date
        Date lastModified = new Date(file.lastModified());
        holder.fileDateText.setText(dateFormat.format(lastModified));
        
        // Set file icon based on file type
        setFileIcon(holder, fileName.toLowerCase());
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileSelected(file);
            }
        });
        
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileDeleteRequested(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    /**
     * Set file icon based on file extension
     */
    private void setFileIcon(ViewHolder holder, String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
                fileName.endsWith(".png") || fileName.endsWith(".gif") || 
                fileName.endsWith(".bmp")) {
            holder.fileIconText.setText(R.string.icon_image);
        } else if (fileName.endsWith(".pdf")) {
            holder.fileIconText.setText(R.string.icon_pdf);
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            holder.fileIconText.setText(R.string.icon_document);
        } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            holder.fileIconText.setText(R.string.icon_spreadsheet);
        } else if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) {
            holder.fileIconText.setText(R.string.icon_presentation);
        } else if (fileName.endsWith(".txt")) {
            holder.fileIconText.setText(R.string.icon_text);
        } else if (fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".aac")) {
            holder.fileIconText.setText(R.string.icon_audio);
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mkv")) {
            holder.fileIconText.setText(R.string.icon_video);
        } else {
            holder.fileIconText.setText(R.string.icon_file);
        }
    }

    /**
     * ViewHolder for encrypted file items.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView fileIconText;
        TextView fileNameText;
        TextView fileSizeText;
        TextView fileDateText;
        ImageButton deleteButton;

        ViewHolder(View itemView) {
            super(itemView);
            fileIconText = itemView.findViewById(R.id.file_icon_text);
            fileNameText = itemView.findViewById(R.id.file_name_text);
            fileSizeText = itemView.findViewById(R.id.file_size_text);
            fileDateText = itemView.findViewById(R.id.file_date_text);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }
    }
} 