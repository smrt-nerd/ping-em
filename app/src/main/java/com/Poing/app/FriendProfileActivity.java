package com.Poing.app;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import de.hdodenhof.circleimageview.CircleImageView;

public class FriendProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme
        android.content.SharedPreferences prefs = getSharedPreferences("poing_prefs", MODE_PRIVATE);
        String theme = prefs.getString("theme", "default");
        if (theme.equals("dark")) setTheme(R.style.Theme_Poing_Dark);
        else if (theme.equals("light")) setTheme(R.style.Theme_Poing_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_profile);

        // Get the friend's UID passed from FriendsActivity
        String friendUid = getIntent().getStringExtra("friendUid");
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        CircleImageView profilePic = findViewById(R.id.friendProfilePic);
        TextView usernameText = findViewById(R.id.friendUsername);
        TextView statusText = findViewById(R.id.friendStatus);
        TextView studentIdText = findViewById(R.id.friendStudentId);
        TextView emailText = findViewById(R.id.friendEmail);

        // Load friend's data from Firestore
        FirebaseFirestore.getInstance().collection("users").document(friendUid).get()
                .addOnSuccessListener(doc -> {
                    usernameText.setText(doc.getString("username"));
                    studentIdText.setText(doc.getString("studentId"));
                    emailText.setText(doc.getString("email"));

                    // Show campus status
                    Boolean onCampus = doc.getBoolean("onCampus");
                    statusText.setText(Boolean.TRUE.equals(onCampus)
                            ? "🟢 Currently on campus"
                            : "⚫ Not on campus");

                    // Check profile pic privacy
                    // If "friends only" is on, only show pic to accepted friends
                    Boolean picFriendsOnly = doc.getBoolean("profilePicFriendsOnly");
                    String base64 = doc.getString("profilePic");

                    if (base64 != null && !base64.isEmpty()) {
                        if (Boolean.TRUE.equals(picFriendsOnly)) {
                            // We're already friends if we got here, so show it
                            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                            profilePic.setImageBitmap(
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                        } else {
                            // Public pic — show it
                            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                            profilePic.setImageBitmap(
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                        }
                    }
                    // If no pic, default launcher icon stays
                });
    }
}