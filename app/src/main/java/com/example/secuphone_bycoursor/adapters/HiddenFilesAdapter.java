package com.example.secuphone_bycoursor.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone_bycoursor.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HiddenFilesAdapter extends RecyclerView.Adapter<HiddenFilesAdapter.FileViewHolder> {

    private final List<File> files;
    private final OnFileActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public interface OnFileActionListener {
        void onUnhideFile(File file);
    }

    public HiddenFilesAdapter(List<File> files, OnFileActionListener listener) {
        this.files = files;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hidden_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = files.get(position);
        
        // Set file name
        holder.fileName.setText(file.getName());
        
        // Format the file size
        long fileSizeInBytes = file.length();
        String fileSizeText = formatFileSize(fileSizeInBytes);
        holder.fileInfo.setText(fileSizeText + " • " + 
                dateFormat.format(new Date(file.lastModified())));
        
        // Set file type icon based on extension
        setFileTypeIcon(holder.fileTypeIcon, file.getName());
        
        // Set click listener for unhide button
        holder.actionButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUnhideFile(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    /**
     * Format file size to human-readable format
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        return String.format(Locale.getDefault(), "%.1f %s", 
                size / Math.pow(1024, digitGroups), 
                units[digitGroups]);
    }
    
    /**
     * Set appropriate icon based on file extension
     */
    private void setFileTypeIcon(ImageView imageView, String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        
        // Set icon based on file type
        if (extension.equals("pdf")) {
            imageView.setImageResource(R.drawable.ic_file_pdf);
        } else if (extension.equals("doc") || extension.equals("docx")) {
            imageView.setImageResource(R.drawable.ic_file_doc);
        } else if (extension.equals("jpg") || extension.equals("jpeg") || 
                   extension.equals("png") || extension.equals("gif")) {
            imageView.setImageResource(R.drawable.ic_file_image);
        } else if (extension.equals("mp4") || extension.equals("mov") || 
                   extension.equals("avi")) {
            imageView.setImageResource(R.drawable.ic_file_video);
        } else if (extension.equals("mp3") || extension.equals("wav") || 
                   extension.equals("ogg")) {
            imageView.setImageResource(R.drawable.ic_file_audio);
        } else {
            imageView.setImageResource(R.drawable.ic_file_generic);
        }
    }
    
    /**
     * Get file extension from filename
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileName;
        TextView fileInfo;
        ImageView fileTypeIcon;
        ImageButton actionButton;

        FileViewHolder(View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.file_name);
            fileInfo = itemView.findViewById(R.id.file_info);
            fileTypeIcon = itemView.findViewById(R.id.file_type_icon);
            actionButton = itemView.findViewById(R.id.file_action_button);
        }
    }
} 