package it.unipi.m598992.DirectChat.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import it.unipi.m598992.DirectChat.activity.MainActivity;
import it.unipi.m598992.DirectChat.R;
import it.unipi.m598992.DirectChat.RoomDB.entity.User;
import it.unipi.m598992.DirectChat.adapter.BKChatListAdapter;
import it.unipi.m598992.DirectChat.viewModel.BKChatListViewModel;

public class BKChatListFragment extends Fragment {
    private RecyclerView recyclerView;
    private BKChatListAdapter bkChatListAdapter;
    private BKChatListViewModel bkChatListViewModel;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Restituiamo il layout per questo fragment
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);
        recyclerView = view.findViewById(R.id.bkChatRV);
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bkChatListAdapter = new BKChatListAdapter((MainActivity) getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        recyclerView.setAdapter(bkChatListAdapter);
        bkChatListViewModel = new ViewModelProvider(this).get(BKChatListViewModel.class);
        bkChatListViewModel.getAllUser().observe(getViewLifecycleOwner(), new Observer<List<User>>() {
            @Override
            public void onChanged(List<User> users) {
                bkChatListAdapter.submitList(users);
            }
        });
    }

    public BKChatListAdapter getBkChatListAdapter() {
        return bkChatListAdapter;
    }
}