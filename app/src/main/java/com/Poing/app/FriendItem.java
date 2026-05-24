package com.Poing.app;

// Simple data class representing one friend in the list
public class FriendItem {
    public String uid;
    public String username;
    public boolean onCampus;
    public String base64Pic;

    public FriendItem(String uid, String username, boolean onCampus, String base64Pic) {
        this.uid = uid;
        this.username = username;
        this.onCampus = onCampus;
        this.base64Pic = base64Pic;
    }
}