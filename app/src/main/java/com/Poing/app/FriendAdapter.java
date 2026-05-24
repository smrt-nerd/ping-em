package com.Poing.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendViewHolder> {

    // Interface — FriendsActivity implements all callbacks
    public interface FriendActionListener {
        void onPin(int position);
        void onUnpin(int position);
        void onPinnedReordered(List<FriendItem> pinnedFriends);
        void onUnfriend(FriendItem item);
    }

    private final Context context;
    private final List<FriendItem> pinnedFriends;
    private final List<FriendItem> regularFriends;
    private final FriendActionListener listener;

    // ItemTouchHelper reference — needed to start drag from handle touch
    private ItemTouchHelper touchHelper;

    public void setTouchHelper(ItemTouchHelper touchHelper) {
        this.touchHelper = touchHelper;
    }

    // View types
    // 0 = pinned friend row
    // 1 = "Other Friends" separator
    // 2 = regular friend row

    public FriendAdapter(Context context, List<FriendItem> pinnedFriends,
                         List<FriendItem> regularFriends, FriendActionListener listener) {
        this.context = context;
        this.pinnedFriends = pinnedFriends;
        this.regularFriends = regularFriends;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < pinnedFriends.size()) return 0;
        if (!pinnedFriends.isEmpty() && !regularFriends.isEmpty()
                && position == pinnedFriends.size()) return 1;
        return 2;
    }

    @Override
    public int getItemCount() {
        int count = pinnedFriends.size() + regularFriends.size();
        if (!pinnedFriends.isEmpty() && !regularFriends.isEmpty()) count++;
        return count;
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 1) {
            TextView tv = new TextView(context);
            tv.setText("Other Friends");
            tv.setTextSize(14f);
            tv.setPadding(32, 24, 32, 8);
            tv.setAlpha(0.6f);
            return new FriendViewHolder(tv);
        }
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        if (getItemViewType(position) == 1) return;

        boolean isPinned = position < pinnedFriends.size();

        FriendItem item;
        if (isPinned) {
            item = pinnedFriends.get(position);
        } else {
            int separatorOffset = (!pinnedFriends.isEmpty() && !regularFriends.isEmpty()) ? 1 : 0;
            int regularIndex = position - pinnedFriends.size() - separatorOffset;
            if (regularIndex < 0 || regularIndex >= regularFriends.size()) return;
            item = regularFriends.get(regularIndex);
        }

        holder.nameText.setText(isPinned ? "📌 " + item.username : item.username);
        holder.statusText.setText(item.onCampus ? "🟢 On campus" : "⚫ Not on campus");

        // Load profile picture
        if (item.base64Pic != null && !item.base64Pic.isEmpty()) {
            byte[] bytes = Base64.decode(item.base64Pic, Base64.DEFAULT);
            holder.profilePic.setImageBitmap(
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        } else {
            holder.profilePic.setImageResource(R.mipmap.ic_launcher_round);
        }

        // Show drag handle only for pinned rows
        if (holder.dragHandle != null) {
            if (isPinned) {
                holder.dragHandle.setVisibility(View.VISIBLE);
                // Touch on drag handle starts the drag — doesn't conflict with long press
                holder.dragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (touchHelper != null) {
                            touchHelper.startDrag(holder);
                        }
                    }
                    return false;
                });
            } else {
                holder.dragHandle.setVisibility(View.GONE);
                holder.dragHandle.setOnTouchListener(null);
            }
        }

        // Long press opens profile — no conflict with drag since drag uses handle
        holder.itemView.setOnLongClickListener(v -> {
            openProfile(item.uid);
            return true;
        });
        holder.itemView.setOnClickListener(null);

        // 3-dot popup menu
        final FriendItem finalItem = item;
        holder.menuButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.menuButton);
            popup.getMenu().add("View Profile");
            popup.getMenu().add(isPinned ? "Unpin" : "Pin");
            popup.getMenu().add("Unfriend");

            popup.setOnMenuItemClickListener(menuItem -> {
                String title = menuItem.getTitle().toString();
                switch (title) {
                    case "View Profile":
                        openProfile(finalItem.uid);
                        break;
                    case "Pin":
                        listener.onPin(holder.getAdapterPosition());
                        break;
                    case "Unpin":
                        listener.onUnpin(holder.getAdapterPosition());
                        break;
                    case "Unfriend":
                        listener.onUnfriend(finalItem);
                        break;
                }
                return true;
            });
            popup.show();
        });
    }

    private void openProfile(String uid) {
        Intent intent = new Intent(context, FriendProfileActivity.class);
        intent.putExtra("friendUid", uid);
        context.startActivity(intent);
    }

    public void onItemMoved(int fromPosition, int toPosition) {
        if (fromPosition < pinnedFriends.size() && toPosition < pinnedFriends.size()) {
            Collections.swap(pinnedFriends, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
            listener.onPinnedReordered(pinnedFriends);
        }
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        CircleImageView profilePic;
        TextView nameText, statusText, dragHandle;
        Button menuButton;

        FriendViewHolder(View itemView) {
            super(itemView);
            if (itemView instanceof TextView) return;
            dragHandle = itemView.findViewById(R.id.dragHandle);
            profilePic = itemView.findViewById(R.id.friendItemPic);
            nameText = itemView.findViewById(R.id.friendItemName);
            statusText = itemView.findViewById(R.id.friendItemStatus);
            menuButton = itemView.findViewById(R.id.friendItemMenu);
        }

        FriendViewHolder(TextView tv) {
            super(tv);
        }
    }
}