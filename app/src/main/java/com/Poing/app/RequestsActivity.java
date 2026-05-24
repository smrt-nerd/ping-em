package com.Poing.app;

import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class RequestsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String currentUid;

    // Store request data as a structured list so positions stay correct
    private final List<RequestItem> requestItems = new ArrayList<>();
    private android.widget.ArrayAdapter<RequestItem> adapter;
    private ListView requestsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme
        SharedPreferences prefs = getSharedPreferences("poing_prefs", MODE_PRIVATE);
        String theme = prefs.getString("theme", "default");
        if (theme.equals("dark")) setTheme(R.style.Theme_Poing_Dark);
        else if (theme.equals("light")) setTheme(R.style.Theme_Poing_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_requests);

        db = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        requestsList = findViewById(R.id.requestsList);

        // Custom adapter that uses RequestItem directly
        adapter = new android.widget.ArrayAdapter<RequestItem>(this,
                0, requestItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // Each row: profile pic + name + student ID + accept/decline buttons
                LinearLayout layout = new LinearLayout(RequestsActivity.this);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setPadding(16, 16, 16, 16);
                layout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                // Round profile pic
                CircleImageView pic = new CircleImageView(RequestsActivity.this);
                LinearLayout.LayoutParams picParams = new LinearLayout.LayoutParams(80, 80);
                picParams.setMarginEnd(16);
                pic.setLayoutParams(picParams);
                pic.setImageResource(R.mipmap.ic_launcher_round);

                RequestItem item = requestItems.get(position);
                if (item.base64Pic != null && !item.base64Pic.isEmpty()) {
                    byte[] bytes = Base64.decode(item.base64Pic, Base64.DEFAULT);
                    pic.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                }

                // Name + student ID column
                LinearLayout infoCol = new LinearLayout(RequestsActivity.this);
                infoCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                infoCol.setLayoutParams(infoParams);

                TextView nameText = new TextView(RequestsActivity.this);
                nameText.setText(item.username);
                nameText.setTextSize(16f);
                nameText.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView idText = new TextView(RequestsActivity.this);
                idText.setText("ID: " + item.studentId);
                idText.setTextSize(13f);

                infoCol.addView(nameText);
                infoCol.addView(idText);

                // Accept button — uses item's requestId directly, not position
                Button acceptBtn = new Button(RequestsActivity.this);
                acceptBtn.setText("✓");
                acceptBtn.setOnClickListener(v -> acceptRequest(item));

                // Decline button
                Button declineBtn = new Button(RequestsActivity.this);
                declineBtn.setText("✗");
                declineBtn.setOnClickListener(v -> declineRequest(item));

                layout.addView(pic);
                layout.addView(infoCol);
                layout.addView(acceptBtn);
                layout.addView(declineBtn);
                return layout;
            }
        };

        requestsList.setAdapter(adapter);
        loadRequests();
    }

    // Data class for each request row — avoids position mismatch bug
    static class RequestItem {
        String requestId;
        String fromUid;
        String username;
        String studentId;
        String base64Pic;

        RequestItem(String requestId, String fromUid,
                    String username, String studentId, String base64Pic) {
            this.requestId = requestId;
            this.fromUid = fromUid;
            this.username = username;
            this.studentId = studentId;
            this.base64Pic = base64Pic != null ? base64Pic : "";
        }
    }

    private void loadRequests() {
        requestItems.clear();
        adapter.notifyDataSetChanged();

        db.collection("friendRequests")
                .whereEqualTo("toUid", currentUid)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        Toast.makeText(this, "No pending requests.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String fromUid = doc.getString("fromUid");
                        String requestId = doc.getId();

                        // Load sender's profile
                        db.collection("users").document(fromUid).get()
                                .addOnSuccessListener(userDoc -> {
                                    String username = userDoc.getString("username");
                                    String studentId = userDoc.getString("studentId");
                                    String base64 = userDoc.getString("profilePic");

                                    // Add to list using requestId — not position
                                    requestItems.add(new RequestItem(
                                            requestId, fromUid, username, studentId, base64));
                                    adapter.notifyDataSetChanged();
                                });
                    }
                });
    }

    // Uses item's own requestId — not affected by list position changes
    private void acceptRequest(RequestItem item) {
        Map<String, Object> friendship = new HashMap<>();
        friendship.put("uid1", currentUid);
        friendship.put("uid2", item.fromUid);

        db.collection("friends").add(friendship)
                .addOnSuccessListener(ref ->
                        db.collection("friendRequests").document(item.requestId)
                                .update("status", "accepted")
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Friend added!", Toast.LENGTH_SHORT).show();
                                    requestItems.remove(item);
                                    adapter.notifyDataSetChanged();
                                }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void declineRequest(RequestItem item) {
        db.collection("friendRequests").document(item.requestId)
                .update("status", "declined")
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Request declined.", Toast.LENGTH_SHORT).show();
                    requestItems.remove(item);
                    adapter.notifyDataSetChanged();
                });
    }
}