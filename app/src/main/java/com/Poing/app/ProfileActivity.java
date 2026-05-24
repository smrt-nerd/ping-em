package com.Poing.app;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends AppCompatActivity {

    // Firebase instances
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUid;

    // Edit limit — user gets 2 edits total across username + student ID
    private int editCount = 0;
    private static final int MAX_EDITS = 2;

    // UI elements
    private CircleImageView profilePicView;
    private EditText usernameField, studentIdField;
    private TextView emailText, editCountText;
    private SwitchCompat picPrivacySwitch;
    private Button saveProfileButton; // class-level so loadProfile() can access it
    private String currentBase64Pic = "";

    // Tracks whether the user has actually changed anything
    // Save button stays disabled until this is true
    private boolean hasUnsavedChanges = false;

    // Image picker — launches gallery, result comes back to uploadProfilePic()
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadProfilePic(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before setContentView — must be first
        android.content.SharedPreferences prefs = getSharedPreferences("poing_prefs", MODE_PRIVATE);
        String theme = prefs.getString("theme", "default");
        if (theme.equals("dark")) setTheme(R.style.Theme_Poing_Dark);
        else if (theme.equals("light")) setTheme(R.style.Theme_Poing_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUid = auth.getCurrentUser().getUid();

        // Bind UI elements
        profilePicView = findViewById(R.id.profilePicView);
        usernameField = findViewById(R.id.usernameField);
        studentIdField = findViewById(R.id.studentIdField);
        emailText = findViewById(R.id.emailText);
        editCountText = findViewById(R.id.editCountText);
        picPrivacySwitch = findViewById(R.id.picPrivacySwitch);
        Button changePicButton = findViewById(R.id.changePicButton);
        saveProfileButton = findViewById(R.id.saveProfileButton);
        android.widget.ImageButton editButton = findViewById(R.id.editButton);

        // Email is read-only — set it directly from Firebase Auth
        emailText.setText(auth.getCurrentUser().getEmail());

        // Load user data from Firestore
        loadProfile();

        // Edit button (pencil) — enables the fields so user can type
        editButton.setOnClickListener(v -> {
            if (editCount >= MAX_EDITS) {
                Toast.makeText(this, "You have used all your profile edits.", Toast.LENGTH_LONG).show();
                return;
            }
            usernameField.setEnabled(true);
            studentIdField.setEnabled(true);
            usernameField.requestFocus();
            Toast.makeText(this, "You can now edit your profile.", Toast.LENGTH_SHORT).show();
        });

        // Watch username field — enable save button only when user actually types something
        usernameField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Only activate if field is enabled (edit mode) to avoid false trigger on load
                if (usernameField.isEnabled()) enableSaveButton();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Watch student ID field — same logic
        studentIdField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (studentIdField.isEnabled()) enableSaveButton();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        // Privacy toggle — enable save button when switched
        // Privacy toggle saves instantly — no password needed, no edit count
        picPrivacySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                db.collection("users").document(currentUid)
                        .update("profilePicFriendsOnly", isChecked)
                        .addOnSuccessListener(unused ->
                                Toast.makeText(this, "Privacy setting updated.",
                                        Toast.LENGTH_SHORT).show());
            }
        });

        // Change profile pic — opens gallery, doesn't count as a profile edit
        changePicButton.setOnClickListener(v ->
                imagePickerLauncher.launch("image/*"));

        // Save button — requires password confirmation, counts as one edit
        saveProfileButton.setOnClickListener(v -> {
            if (editCount >= MAX_EDITS) {
                Toast.makeText(this, "You have used all your profile edits.", Toast.LENGTH_LONG).show();
                return;
            }
            if (!hasUnsavedChanges) {
                Toast.makeText(this, "No changes to save.", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmAndSave();
        });
    }

    // Enables the save button and marks that there are unsaved changes
    private void enableSaveButton() {
        hasUnsavedChanges = true;
        saveProfileButton.setEnabled(true);
        saveProfileButton.setAlpha(1.0f);
    }

    // Loads user profile from Firestore and populates the UI
    private void loadProfile() {
        // Disable save button until user makes actual changes
        hasUnsavedChanges = false;
        saveProfileButton.setEnabled(false);
        saveProfileButton.setAlpha(0.5f);

        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(doc -> {
                    // Populate text fields
                    usernameField.setText(doc.getString("username"));
                    studentIdField.setText(doc.getString("studentId"));

                    // Load edit count and update the counter display
                    Long count = doc.getLong("editCount");
                    editCount = count != null ? count.intValue() : 0;
                    editCountText.setText("Profile edits remaining: " + (MAX_EDITS - editCount));

                    // If user has used all edits, lock the fields permanently
                    if (editCount >= MAX_EDITS) {
                        usernameField.setEnabled(false);
                        studentIdField.setEnabled(false);
                    }

                    // Load privacy toggle state
                    Boolean picPrivate = doc.getBoolean("profilePicFriendsOnly");
                    picPrivacySwitch.setChecked(Boolean.TRUE.equals(picPrivate));

                    // Load profile picture if it exists
                    String base64 = doc.getString("profilePic");
                    if (base64 != null && !base64.isEmpty()) {
                        currentBase64Pic = base64;
                        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                        profilePicView.setImageBitmap(
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                    }
                });
    }

    // Handles profile picture selection from gallery
    // Resizes image to 300x300 and stores as Base64 in Firestore
    // Does NOT count as a profile edit
    private void uploadProfilePic(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            // Resize to keep Firestore document size small (max ~30KB at 70% quality)
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, 300, 300, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);

            // Save immediately to Firestore — separate from the edit-counted save
            db.collection("users").document(currentUid)
                    .update("profilePic", base64)
                    .addOnSuccessListener(unused -> {
                        currentBase64Pic = base64;
                        profilePicView.setImageBitmap(
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                        Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show();
                    });

        } catch (IOException e) {
            Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
        }
    }

    // Shows password confirmation dialog before saving changes
    private void confirmAndSave() {
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter your password to confirm");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Confirm Changes")
                .setMessage("Enter your password to save profile changes.")
                .setView(passwordInput)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String password = passwordInput.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "Password required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reauthAndSave(password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Re-authenticates user with password before allowing profile save
    // This is a Firebase security requirement for sensitive operations
    private void reauthAndSave(String password) {
        String email = auth.getCurrentUser().getEmail();
        com.google.firebase.auth.AuthCredential credential =
                com.google.firebase.auth.EmailAuthProvider.getCredential(email, password);

        auth.getCurrentUser().reauthenticate(credential)
                .addOnSuccessListener(unused -> saveProfile())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Wrong password.", Toast.LENGTH_SHORT).show());
    }

    // Saves username, studentId, privacy toggle to Firestore
    // Increments editCount by 1 and locks fields if limit reached
    private void saveProfile() {
        String newUsername = usernameField.getText().toString().trim();
        String newStudentId = studentIdField.getText().toString().trim();

        // Always lock fields after saving — user must press edit button again
        usernameField.setEnabled(false);
        studentIdField.setEnabled(false);

        int newEditCount = editCount + 1;

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", newUsername);
        updates.put("studentId", newStudentId);
        updates.put("editCount", newEditCount);
        // Privacy toggle is saved separately — not included here

        db.collection("users").document(currentUid)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    editCount = newEditCount;
                    hasUnsavedChanges = false;

                    // Update counter display
                    editCountText.setText("Profile edits remaining: " + (MAX_EDITS - editCount));

                    // Lock fields if edit limit reached
                    if (editCount >= MAX_EDITS) {
                        usernameField.setEnabled(false);
                        studentIdField.setEnabled(false);
                    }

                    // Disable save button again until next change
                    saveProfileButton.setEnabled(false);
                    saveProfileButton.setAlpha(0.5f);

                    Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}