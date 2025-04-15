package com.example.mysnapshot;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import android.content.SharedPreferences;
import android.content.UriPermission;

public class MainActivity extends AppCompatActivity {
    private String currentPhotoPath;
    private File currentFolder;
    private GridView gridView;
    private ImageAdapter imageAdapter;
    
    private static final String PREFS_NAME = "MySnapshotPrefs";
    private static final String KEY_FOLDER_PATH = "folderPath";
    private static final String KEY_FOLDER_URI = "folderUri";

    private final ActivityResultLauncher<String> storagePermissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openFolderPicker();
                } else {
                    Toast.makeText(this, "Storage permission is required to view images", Toast.LENGTH_LONG).show();
                }
            }
    );

    private final ActivityResultLauncher<String> cameraPermissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    if (currentFolder == null) {
                        Toast.makeText(this, "Please choose a folder first", Toast.LENGTH_SHORT).show();
                        checkStoragePermission();
                    } else {
                        dispatchTakePictureIntent();
                    }
                } else {
                    Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show();
                }
            }
    );

    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success) {
                    galleryAddPic();
                    Toast.makeText(this, "Photo saved successfully", Toast.LENGTH_SHORT).show();
                    refreshImages();
                }
            }
    );

    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    try {
                        // Take persistable URI permission for future access
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        
                        // Save the URI for future access
                        saveFolderUri(uri);
                        
                        String folderPath = getPathFromUri(uri);
                        if (folderPath != null) {
                            File selectedFolder = new File(folderPath);
                            if (!selectedFolder.exists()) {
                                if (!selectedFolder.mkdirs()) {
                                    Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show();
                                }
                            }
                            
                            // Setup default folder (if not already set up)
                            setupDefaultFolder();
                            
                            // Copy images from selected folder to default folder
                            copyImagesToLocalFolder(selectedFolder);
                            
                            Toast.makeText(this, "Images copied to app folder", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.folder_access_error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error selecting folder: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> manageStoragePermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        openFolderPicker();
                    } else {
                        Toast.makeText(this, "Storage permission is required to view images", Toast.LENGTH_LONG).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> imageDetailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Image was deleted, refresh the grid
                    refreshImages();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gridView = findViewById(R.id.gridView);
        imageAdapter = new ImageAdapter(this);
        gridView.setAdapter(imageAdapter);

        Button takePhotoButton = findViewById(R.id.takePhotoButton);
        Button chooseFolderButton = findViewById(R.id.chooseFolderButton);

        takePhotoButton.setOnClickListener(v -> {
            if (currentFolder == null) {
                // Create default folder if none selected
                setupDefaultFolder();
            }
            checkCameraPermission();
        });

        chooseFolderButton.setOnClickListener(v -> checkStoragePermission());

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            File imageFile = (File) parent.getItemAtPosition(position);
            if (imageFile != null && imageFile.exists()) {
                Intent intent = new Intent(MainActivity.this, ImageDetailsActivity.class);
                intent.putExtra("image_path", imageFile.getAbsolutePath());
                imageDetailsLauncher.launch(intent);
            } else {
                Toast.makeText(this, R.string.no_images_found, Toast.LENGTH_SHORT).show();
                refreshImages();
            }
        });
        
        // Load saved folder path
        loadSavedFolderPath();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                openFolderPicker();
            } else {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(android.net.Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    manageStoragePermission.launch(intent);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    manageStoragePermission.launch(intent);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED) {
                openFolderPicker();
            } else {
                storagePermissionRequest.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent();
        } else {
            cameraPermissionRequest.launch(Manifest.permission.CAMERA);
        }
    }

    private void openFolderPicker() {
        folderPicker.launch(null);
    }

    private void refreshImages() {
        if (currentFolder != null && currentFolder.exists()) {
            // Show loading indicator if needed
            File[] files = currentFolder.listFiles(file ->
                    file.isFile() && (file.getName().toLowerCase().endsWith(".jpg") ||
                            file.getName().toLowerCase().endsWith(".jpeg") ||
                            file.getName().toLowerCase().endsWith(".png")));
            
            List<File> imageFiles = files != null ? Arrays.asList(files) : new ArrayList<>();
            
            // Sort by date modified (newest first)
            if (!imageFiles.isEmpty()) {
                imageFiles = new ArrayList<>(imageFiles); // Convert to ArrayList for sorting
                imageFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            }
            
            imageAdapter.updateImages(imageFiles);
            
            // Update UI to show empty state or images
            if (imageFiles.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_images_in_folder, currentFolder.getName()), Toast.LENGTH_SHORT).show();
            }
        } else if (currentFolder == null) {
            // No folder selected, setup default
            setupDefaultFolder();
            refreshImages();
        }
    }

    private String getPathFromUri(Uri uri) {
        try {
            // For SAF URI (Storage Access Framework)
            if (DocumentsContract.isTreeUri(uri)) {
                final String docId = DocumentsContract.getTreeDocumentId(uri);
                
                if (docId != null && docId.contains(":")) {
                    String[] split = docId.split(":");
                    String type = split[0];
                    
                    if ("primary".equalsIgnoreCase(type) && split.length > 1) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    } else if ("primary".equalsIgnoreCase(type)) {
                        // Root folder was selected
                        return Environment.getExternalStorageDirectory().toString();
                    } else {
                        // Handle non-primary storage (e.g., SD cards)
                        // We'll use a fallback approach for these
                        return createAppSpecificFolderWithName(split.length > 1 ? split[1] : type);
                    }
                } else if (docId != null) {
                    // Just primary without path
                    if ("primary".equalsIgnoreCase(docId)) {
                        return Environment.getExternalStorageDirectory().toString();
                    } else {
                        // Non-standard format, use app-specific folder
                        return createAppSpecificFolderWithName(docId);
                    }
                }
            }
            // Handle document URIs (file selection)
            else if (DocumentsContract.isDocumentUri(this, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if (docId != null && docId.contains(":")) {
                    String[] split = docId.split(":");
                    String type = split[0];
                    
                    if ("primary".equalsIgnoreCase(type) && split.length > 1) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    } else {
                        // Handle non-primary storage
                        return createAppSpecificFolderWithName(split.length > 1 ? split[1] : type);
                    }
                }
            }
            
            // Create app-specific directory as fallback
            return createAppSpecificFolder();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error accessing folder: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            
            // Create app-specific directory as fallback
            return createAppSpecificFolder();
        }
    }
    
    private String createAppSpecificFolder() {
        File appDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MySnapshot");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        Toast.makeText(this, "Using app folder for storage", Toast.LENGTH_SHORT).show();
        return appDir.getAbsolutePath();
    }
    
    private String createAppSpecificFolderWithName(String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            folderName = "Photos";
        }
        
        File appDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), folderName);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        Toast.makeText(this, "Using folder: " + folderName, Toast.LENGTH_SHORT).show();
        return appDir.getAbsolutePath();
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePicture.launch(photoURI);
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        
        // Create the file in the selected folder
        File image = new File(currentFolder, imageFileName + ".jpg");
        File parentDir = image.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void galleryAddPic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                File imageFile = new File(currentPhotoPath);
                values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.getName());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); 
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + currentFolder.getName()); 
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri imageUri = getContentResolver().insert(collection, values);

                if (imageUri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(imageUri);
                         InputStream is = new FileInputStream(imageFile)) {
                        if (os != null) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                os.write(buffer, 0, len);
                            }
                            values.clear();
                            values.put(MediaStore.Images.Media.IS_PENDING, 0);
                            getContentResolver().update(imageUri, values, null, null);
                            Toast.makeText(this, "Photo saved to gallery", Toast.LENGTH_SHORT).show();
                        } else {
                            throw new IOException("Failed to get output stream.");
                        }
                    } catch (IOException e) {
                        // If error happens, we should delete the pending entry.
                        getContentResolver().delete(imageUri, null, null);
                        Toast.makeText(this, "Error saving image to gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else {
                     Toast.makeText(this, "Error creating MediaStore entry", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Fallback for older versions
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                File f = new File(currentPhotoPath);
                Uri contentUri = Uri.fromFile(f);
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error adding photo to gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentFolder != null) {
            refreshImages();
        }
    }

    private void loadSavedFolderPath() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedPath = prefs.getString(KEY_FOLDER_PATH, null);
        String savedUri = prefs.getString(KEY_FOLDER_URI, null);
                
        // First try to use the saved path
        if (savedPath != null) {
            File folder = new File(savedPath);
            if (folder.exists() && folder.isDirectory()) {
                currentFolder = folder;
                refreshImages();
                Toast.makeText(this, getString(R.string.folder_loaded, folder.getName()), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // If path doesn't work, try the saved URI
        if (savedUri != null) {
            try {
                Uri uri = Uri.parse(savedUri);
                // Check if we still have permission to this URI
                List<UriPermission> permissions = getContentResolver().getPersistedUriPermissions();
                boolean hasPermission = false;
                
                for (UriPermission permission : permissions) {
                    if (permission.getUri().toString().equals(savedUri)) {
                        hasPermission = true;
                        break;
                    }
                }
                
                if (hasPermission) {
                    String folderPath = getPathFromUri(uri);
                    if (folderPath != null) {
                        currentFolder = new File(folderPath);
                        if (currentFolder.exists()) {
                            refreshImages();
                            Toast.makeText(this, getString(R.string.folder_loaded, currentFolder.getName()), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // If we get here, we couldn't restore from saved preferences
        // Clear the saved preferences
        prefs.edit().remove(KEY_FOLDER_PATH).remove(KEY_FOLDER_URI).apply();
        
        // Setup default folder
        setupDefaultFolder();
        refreshImages();
    }
    
    private void saveFolderPath(String path) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_FOLDER_PATH, path)
                .apply();
    }
    
    private void saveFolderUri(Uri uri) {
        if (uri != null) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_FOLDER_URI, uri.toString())
                .apply();
        }
    }

    private void setupDefaultFolder() {
        String path = createAppSpecificFolder();
        currentFolder = new File(path);
        saveFolderPath(path);
        Toast.makeText(this, R.string.using_default_folder, Toast.LENGTH_SHORT).show();
    }

    // Copies all images from the source folder to the default folder
    private void copyImagesToLocalFolder(File sourceFolder) {
        if (sourceFolder == null || !sourceFolder.exists() || !sourceFolder.isDirectory()) {
            Toast.makeText(this, R.string.invalid_source_folder, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Make sure the default folder exists
        setupDefaultFolder();
        
        // Find all image files in the source folder
        File[] files = sourceFolder.listFiles(file ->
                file.isFile() && (
                    file.getName().toLowerCase().endsWith(".jpg") ||
                    file.getName().toLowerCase().endsWith(".jpeg") ||
                    file.getName().toLowerCase().endsWith(".png")
                )
        );
        
        if (files == null || files.length == 0) {
            Toast.makeText(this, R.string.no_images_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show a progress dialog for large operations
        int total = files.length;
        int copied = 0;
        
        Toast.makeText(this, getString(R.string.copying_images, total), Toast.LENGTH_SHORT).show();
        
        // Copy each image file
        for (File sourceFile : files) {
            try {
                File destFile = new File(currentFolder, sourceFile.getName());
                
                // Skip if the file already exists
                if (destFile.exists()) {
                    copied++;
                    continue;
                }
                
                // Copy the file
                try (InputStream in = new FileInputStream(sourceFile);
                     OutputStream out = new FileOutputStream(destFile)) {
                    
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                }
                
                // Preserve the timestamp of the original
                destFile.setLastModified(sourceFile.lastModified());
                copied++;
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        Toast.makeText(this, getString(R.string.copied_images, copied, total), Toast.LENGTH_SHORT).show();
        refreshImages();
    }
} 