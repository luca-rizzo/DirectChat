package it.unipi.m598992.DirectChat.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import it.unipi.m598992.DirectChat.fragment.BKChatListFragment;
import it.unipi.m598992.DirectChat.fragment.PeerListFragment;

public class VPAdapter extends FragmentStateAdapter {

    public VPAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0){
            return new PeerListFragment();
        }
        else{
            return new BKChatListFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
