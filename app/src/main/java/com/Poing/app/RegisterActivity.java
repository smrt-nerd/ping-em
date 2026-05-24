package com.Poing.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText usernameField, studentIdField, emailField,
            passwordField, confirmPasswordField;
    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        usernameField = findViewById(R.id.usernameField);
        studentIdField = findViewById(R.id.studentIdField);
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        confirmPasswordField = findViewById(R.id.confirmPasswordField);
        Button registerButton = findViewById(R.id.registerButton);
        TextView goToLogin = findViewById(R.id.goToLogin);
        ImageButton eyeButton = findViewById(R.id.eyeButton);
        ImageButton eyeButton2 = findViewById(R.id.eyeButton2);

        // Eye button for password field
        eyeButton.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            if (passwordVisible) {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT);
                eyeButton.setImageResource(android.R.drawable.ic_menu_view);
            } else {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eyeButton.setImageResource(android.R.drawable.ic_secure);
            }
            passwordField.setSelection(passwordField.getText().length());
        });

        // Eye button for confirm password field
        eyeButton2.setOnClickListener(v -> {
            confirmPasswordVisible = !confirmPasswordVisible;
            if (confirmPasswordVisible) {
                confirmPasswordField.setInputType(InputType.TYPE_CLASS_TEXT);
                eyeButton2.setImageResource(android.R.drawable.ic_menu_view);
            } else {
                confirmPasswordField.setInputType(InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eyeButton2.setImageResource(android.R.drawable.ic_secure);
            }
            confirmPasswordField.setSelection(confirmPasswordField.getText().length());
        });

        registerButton.setOnClickListener(v -> attemptRegister());
        goToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        String username = usernameField.getText().toString().trim();
        String studentId = studentIdField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();

        // Empty field check
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(studentId)
                || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)
                || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Password length
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Passwords must match
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Student ID must be numbers only
        if (!studentId.matches("[0-9]+")) {
            Toast.makeText(this, "Student ID must contain numbers only.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Student ID must be exactly 8 digits
        if (studentId.length() != 8) {
            Toast.makeText(this, "Student ID must be exactly 8 digits.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Check username uniqueness
        db.collection("users").whereEqualTo("username", username).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        Toast.makeText(this, "Username already taken.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Check student ID uniqueness
                    db.collection("users").whereEqualTo("studentId", studentId).get()
                            .addOnSuccessListener(snapshot2 -> {
                                if (!snapshot2.isEmpty()) {
                                    Toast.makeText(this, "Student ID already registered.",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                // All validations passed
                                createAccount(username, studentId, email, password);
                            });
                });
    }

    private void createAccount(String username, String studentId,
                               String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) return;

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", user.getUid());
                    userData.put("username", username);
                    userData.put("studentId", studentId);
                    userData.put("email", email);
                    userData.put("onCampus", false);
                    userData.put("ghostMode", false);
                    userData.put("editCount", 0);
                    userData.put("pinnedFriends", new ArrayList<>());
                    userData.put("profilePicFriendsOnly", false);

                    db.collection("users").document(user.getUid())
                            .set(userData)
                            .addOnSuccessListener(unused -> {
                                startActivity(new Intent(this, FriendsActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this,
                                    "Failed to save profile: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Registration failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
    }
}