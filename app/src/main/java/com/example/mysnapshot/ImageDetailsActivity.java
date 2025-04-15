package com.example.mysnapshot;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_details);

        String imagePath = getIntent().getStringExtra("image_path");
        if (imagePath == null) {
            Toast.makeText(this, "Error: Image path not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Toast.makeText(this, "Error: Image file not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView imageView = findViewById(R.id.detailImageView);
        TextView nameTextView = findViewById(R.id.nameTextView);
        TextView pathTextView = findViewById(R.id.pathTextView);
        TextView sizeTextView = findViewById(R.id.sizeTextView);
        TextView dateTextView = findViewById(R.id.dateTextView);
        Button deleteButton = findViewById(R.id.deleteButton);

        // Load image with error handling and proper scaling
        try {
            // Check if image is valid
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
            
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Toast.makeText(this, "Invalid image file", Toast.LENGTH_SHORT).show();
                imageView.setImageResource(R.drawable.ic_gallery); // Fallback image
            } else {
                // Calculate proper scaling to avoid OutOfMemory errors
                // while still showing the image as large as possible
                int targetW = getResources().getDisplayMetrics().widthPixels;
                int targetH = targetW * options.outHeight / options.outWidth;
                
                // Now load a properly scaled bitmap that fits the screen width
                options = new BitmapFactory.Options();
                options.inJustDecodeBounds = false;
                
                // Calculate inSampleSize for efficient loading
                options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, targetW, targetH);
                
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        // Set scales to fit the image properly
                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        imageView.setAdjustViewBounds(true);
                    } else {
                        // Fallback to direct URI loading if bitmap creation fails
                        imageView.setImageURI(Uri.fromFile(imageFile));
                    }
                } catch (OutOfMemoryError e) {
                    // If we still get OOM error, try with direct URI
                    imageView.setImageURI(Uri.fromFile(imageFile));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            imageView.setImageResource(R.drawable.ic_gallery); // Fallback image
        }

        // Set image details
        nameTextView.setText(String.format("Name: %s", imageFile.getName()));
        pathTextView.setText(String.format("Path: %s", imageFile.getAbsolutePath()));
        
        // Format file size
        long size = imageFile.length();
        String sizeText;
        if (size > 1024 * 1024) {
            sizeText = String.format(Locale.getDefault(), "%.2f MB", size / (1024.0 * 1024.0));
        } else if (size > 1024) {
            sizeText = String.format(Locale.getDefault(), "%.2f KB", size / 1024.0);
        } else {
            sizeText = String.format(Locale.getDefault(), "%d bytes", size);
        }
        sizeTextView.setText(String.format("Size: %s", sizeText));

        // Format date
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        String dateText = sdf.format(new Date(imageFile.lastModified()));
        dateTextView.setText(String.format("Date: %s", dateText));

        // Handle delete button click
        deleteButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Image")
                    .setMessage("Are you sure you want to delete this image?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        try {
                            if (imageFile.delete()) {
                                // Also remove from MediaStore if needed
                                try {
                                    getContentResolver().delete(
                                        Uri.fromFile(imageFile), 
                                        null, 
                                        null
                                    );
                                } catch (Exception e) {
                                    // Ignore MediaStore errors
                                    e.printStackTrace();
                                }
                                
                                // Send result back to MainActivity to refresh the grid
                                setResult(RESULT_OK);
                                Toast.makeText(this, "Image deleted successfully", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(this, "Failed to delete image", Toast.LENGTH_SHORT).show();
                            }
                        } catch (SecurityException e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Permission denied: Cannot delete file", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
    
    private int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
} 