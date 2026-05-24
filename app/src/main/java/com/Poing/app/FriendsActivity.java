package com.Poing.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class FriendsActivity extends AppCompatActivity implements FriendAdapter.FriendActionListener {

    private FirebaseFirestore db;
    private String currentUid;
    private String foundUid;
    private DrawerLayout drawerLayout;

    // searchStatusText — shown outside the card for "Searching..." and "No user found."
    // searchResult — shown inside the card for "✓ Already friends"
    private TextView searchStatusText;
    private TextView searchResult;
    private Button sendRequestButton;
    private RecyclerView friendsRecyclerView;

    private final ArrayList<FriendItem> pinnedList = new ArrayList<>();
    private final ArrayList<FriendItem> regularList = new ArrayList<>();
    private FriendAdapter adapter;
    private ArrayList<String> pinnedUids = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("poing_prefs", MODE_PRIVATE);
        String theme = prefs.getString("theme", "default");
        if (theme.equals("dark")) setTheme(R.style.Theme_Poing_Dark);
        else if (theme.equals("light")) setTheme(R.style.Theme_Poing_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        db = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        drawerLayout = findViewById(R.id.drawerLayout);
        EditText searchField = findViewById(R.id.searchField);
        Button searchButton = findViewById(R.id.searchButton);

        // Two separate text views for search feedback
        searchStatusText = findViewById(R.id.searchStatusText); // outside card
        searchResult = findViewById(R.id.searchResult);         // inside card
        sendRequestButton = findViewById(R.id.sendRequestButton);
        friendsRecyclerView = findViewById(R.id.friendsRecyclerView);
        Button viewRequestsButton = findViewById(R.id.viewRequestsButton);
        CircleImageView drawerProfilePic = findViewById(R.id.drawerProfilePic);
        TextView drawerUsername = findViewById(R.id.drawerUsername);

        adapter = new FriendAdapter(this, pinnedList, regularList, this);
        friendsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        friendsRecyclerView.setAdapter(adapter);

        // Drag-to-reorder — only within pinned section
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                if (from.getAdapterPosition() < pinnedList.size()
                        && to.getAdapterPosition() < pinnedList.size()) {
                    adapter.onItemMoved(from.getAdapterPosition(), to.getAdapterPosition());
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        };
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(friendsRecyclerView);
        adapter.setTouchHelper(touchHelper);

        // Hamburger opens left drawer
        findViewById(R.id.menuButton).setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                drawerLayout.closeDrawers();
            } else {
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START);
            }
        });

        findViewById(R.id.drawerProfileButton).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, ProfileActivity.class));
        });

        findViewById(R.id.drawerSettingsButton).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Load drawer profile info
        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(doc -> {
                    drawerUsername.setText(doc.getString("username"));
                    String base64 = doc.getString("profilePic");
                    if (base64 != null && !base64.isEmpty()) {
                        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                        drawerProfilePic.setImageBitmap(
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                    }
                });

        searchButton.setOnClickListener(v -> {
            String query = searchField.getText().toString().trim();
            if (query.isEmpty()) {
                Toast.makeText(this, "Enter a username or student ID.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            searchUser(query);
        });

        sendRequestButton.setOnClickListener(v -> sendFriendRequest());
        viewRequestsButton.setOnClickListener(v ->
                startActivity(new Intent(this, RequestsActivity.class)));

        loadFriends();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFriends();
    }

    // Search by username first, then student ID
    // Uses searchStatusText (outside card) for status messages
    private void searchUser(String query) {
        foundUid = null;
        sendRequestButton.setVisibility(View.GONE);
        searchStatusText.setText("Searching...");
        findViewById(R.id.searchResultCard).setVisibility(View.GONE);
        searchResult.setText("");

        db.collection("users").whereEqualTo("username", query).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        handleSearchResult(snapshot.getDocuments().get(0));
                    } else {
                        db.collection("users").whereEqualTo("studentId", query).get()
                                .addOnSuccessListener(snapshot2 -> {
                                    if (!snapshot2.isEmpty()) {
                                        handleSearchResult(snapshot2.getDocuments().get(0));
                                    } else {
                                        // Show outside the card so it's always visible
                                        searchStatusText.setText("No user found.");
                                        findViewById(R.id.searchResultCard)
                                                .setVisibility(View.GONE);
                                    }
                                })
                                .addOnFailureListener(e ->
                                        searchStatusText.setText("Search failed. Try again."));
                    }
                })
                .addOnFailureListener(e ->
                        searchStatusText.setText("Search failed. Try again."));
    }

    // Shows search result card with profile pic, username, student ID
    private void handleSearchResult(com.google.firebase.firestore.DocumentSnapshot doc) {
        String uid = doc.getString("uid");
        String username = doc.getString("username");
        String studentId = doc.getString("studentId");
        String base64 = doc.getString("profilePic");
        Boolean picPrivate = doc.getBoolean("profilePicFriendsOnly");

        if (uid == null || uid.equals(currentUid)) {
            searchStatusText.setText("No user found.");
            return;
        }

        foundUid = uid;

        // Clear status text since we're showing the card now
        searchStatusText.setText("");

        View searchResultCard = findViewById(R.id.searchResultCard);
        CircleImageView searchResultPic = findViewById(R.id.searchResultPic);
        TextView searchResultName = findViewById(R.id.searchResultName);
        TextView searchResultIdText = findViewById(R.id.searchResultId);

        searchResultCard.setVisibility(View.VISIBLE);
        searchResultName.setText(username);
        searchResultIdText.setText("ID: " + studentId);

        // Respect privacy — friends-only pic shows default to non-friends
        if (base64 != null && !base64.isEmpty() && !Boolean.TRUE.equals(picPrivate)) {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            searchResultPic.setImageBitmap(
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        } else {
            searchResultPic.setImageResource(R.mipmap.ic_launcher_round);
        }

        // Check if already friends
        boolean alreadyFriend = false;
        for (FriendItem f : pinnedList) {
            if (f.uid.equals(uid)) { alreadyFriend = true; break; }
        }
        if (!alreadyFriend) {
            for (FriendItem f : regularList) {
                if (f.uid.equals(uid)) { alreadyFriend = true; break; }
            }
        }

        if (alreadyFriend) {
            searchResult.setText("✓ Already friends");
            sendRequestButton.setVisibility(View.GONE);
            foundUid = null;
        } else {
            searchResult.setText("");
            sendRequestButton.setVisibility(View.VISIBLE);
        }
    }

    private void sendFriendRequest() {
        if (foundUid == null) return;
        String requestId = currentUid + "_" + foundUid;

        db.collection("friendRequests").document(requestId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Toast.makeText(this, "Request already sent.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> request = new HashMap<>();
                    request.put("fromUid", currentUid);
                    request.put("toUid", foundUid);
                    request.put("status", "pending");

                    db.collection("friendRequests").document(requestId).set(request)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Friend request sent!",
                                        Toast.LENGTH_SHORT).show();
                                sendRequestButton.setVisibility(View.GONE);
                                foundUid = null;
                            });
                });
    }

    // Step 1 — load pinned UIDs first, then friendships
    private void loadFriends() {
        pinnedList.clear();
        regularList.clear();
        pinnedUids.clear();
        adapter.notifyDataSetChanged();

        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(userDoc -> {
                    List<?> rawPinned = (List<?>) userDoc.get("pinnedFriends");
                    if (rawPinned != null) {
                        for (Object o : rawPinned) pinnedUids.add(o.toString());
                    }
                    loadAllFriendships();
                });
    }

    // Step 2 — collect all friend UIDs deduped
    private void loadAllFriendships() {
        ArrayList<String> allFriendUids = new ArrayList<>();

        db.collection("friends").whereEqualTo("uid1", currentUid).get()
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String uid = doc.getString("uid2");
                        if (uid != null && !allFriendUids.contains(uid))
                            allFriendUids.add(uid);
                    }
                    db.collection("friends").whereEqualTo("uid2", currentUid).get()
                            .addOnSuccessListener(snapshot2 -> {
                                for (QueryDocumentSnapshot doc : snapshot2) {
                                    String uid = doc.getString("uid1");
                                    if (uid != null && !allFriendUids.contains(uid))
                                        allFriendUids.add(uid);
                                }
                                loadFriendDetails(allFriendUids);
                            });
                });
    }

    // Step 3 — load each friend's profile, sort into pinned/regular
    private void loadFriendDetails(ArrayList<String> uids) {
        final int total = uids.size();
        final int[] loadedCount = {0};

        if (total == 0) {
            adapter.notifyDataSetChanged();
            return;
        }

        for (String uid : uids) {
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        String username = doc.getString("username");
                        Boolean onCampus = doc.getBoolean("onCampus");
                        String base64 = doc.getString("profilePic");
                        String pic = (base64 != null) ? base64 : "";

                        FriendItem item = new FriendItem(uid, username,
                                Boolean.TRUE.equals(onCampus), pic);

                        // Duplicate guard
                        boolean alreadyInPinned = false;
                        for (FriendItem f : pinnedList) {
                            if (f.uid.equals(uid)) { alreadyInPinned = true; break; }
                        }
                        boolean alreadyInRegular = false;
                        for (FriendItem f : regularList) {
                            if (f.uid.equals(uid)) { alreadyInRegular = true; break; }
                        }

                        if (!alreadyInPinned && !alreadyInRegular) {
                            if (pinnedUids.contains(uid)) {
                                pinnedList.add(item);
                            } else {
                                regularList.add(item);
                            }
                        }

                        pinnedList.sort((a, b) ->
                                pinnedUids.indexOf(a.uid) - pinnedUids.indexOf(b.uid));

                        loadedCount[0]++;
                        if (loadedCount[0] == total) {
                            adapter.notifyDataSetChanged();
                        }
                    });
        }
    }

    @Override
    public void onPin(int position) {
        int regularIndex = position - pinnedList.size()
                - (!pinnedList.isEmpty() && !regularList.isEmpty() ? 1 : 0);
        if (regularIndex < 0 || regularIndex >= regularList.size()) return;
        FriendItem item = regularList.get(regularIndex);
        if (pinnedUids.size() >= 5) {
            showPinLimitDialog(item);
        } else {
            pinFriend(item);
        }
    }

    @Override
    public void onUnpin(int position) {
        if (position >= pinnedList.size()) return;
        FriendItem item = pinnedList.get(position);
        unpinFriend(item);
    }

    @Override
    public void onPinnedReordered(List<FriendItem> reorderedPinned) {
        ArrayList<String> newOrder = new ArrayList<>();
        for (FriendItem item : reorderedPinned) newOrder.add(item.uid);
        db.collection("users").document(currentUid)
                .update("pinnedFriends", newOrder);
    }

    @Override
    public void onUnfriend(FriendItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Unfriend")
                .setMessage("Remove " + item.username + " from your friends?")
                .setPositiveButton("Unfriend", (dialog, which) -> {
                    db.collection("friends").whereEqualTo("uid1", currentUid)
                            .whereEqualTo("uid2", item.uid).get()
                            .addOnSuccessListener(snapshot -> {
                                if (!snapshot.isEmpty())
                                    snapshot.getDocuments().get(0).getReference().delete();
                            });
                    db.collection("friends").whereEqualTo("uid1", item.uid)
                            .whereEqualTo("uid2", currentUid).get()
                            .addOnSuccessListener(snapshot -> {
                                if (!snapshot.isEmpty())
                                    snapshot.getDocuments().get(0).getReference().delete();
                            });
                    pinnedList.remove(item);
                    regularList.remove(item);
                    pinnedUids.remove(item.uid);
                    adapter.notifyDataSetChanged();
                    db.collection("users").document(currentUid)
                            .update("pinnedFriends", pinnedUids);
                    Toast.makeText(this, item.username + " removed.",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pinFriend(FriendItem item) {
        pinnedUids.add(item.uid);
        regularList.remove(item);
        pinnedList.add(item);
        db.collection("users").document(currentUid)
                .update("pinnedFriends", pinnedUids)
                .addOnSuccessListener(unused -> {
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, item.username + " pinned!",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void unpinFriend(FriendItem item) {
        pinnedUids.remove(item.uid);
        pinnedList.remove(item);
        regularList.add(0, item);
        db.collection("users").document(currentUid)
                .update("pinnedFriends", pinnedUids)
                .addOnSuccessListener(unused -> {
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, item.username + " unpinned.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showPinLimitDialog(FriendItem newItem) {
        String[] pinnedNames = new String[pinnedList.size()];
        final int[] selectedIndex = {-1};
        for (int i = 0; i < pinnedList.size(); i++) {
            pinnedNames[i] = pinnedList.get(i).username;
        }

        new AlertDialog.Builder(this)
                .setTitle("Pin limit reached — replace a pin with " + newItem.username + "?")
                .setSingleChoiceItems(pinnedNames, -1,
                        (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton("Replace", (dialog, which) -> {
                    if (selectedIndex[0] == -1) {
                        Toast.makeText(this, "Select a friend to unpin first.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FriendItem toUnpin = pinnedList.get(selectedIndex[0]);
                    unpinFriend(toUnpin);
                    pinFriend(newItem);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}