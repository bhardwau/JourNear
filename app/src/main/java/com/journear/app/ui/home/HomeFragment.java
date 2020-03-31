package com.journear.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.journear.app.R;
import com.journear.app.ui.CreateJourneyActivity;

public class HomeFragment extends Fragment {

    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                ViewModelProviders.of(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        final TextView textView = root.findViewById(R.id.text_home);

        homeViewModel.getText().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });

        root.findViewById(R.id.navButtonCreate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent createJourneyIntent = new Intent(getActivity(), CreateJourneyActivity.class);
                startActivity(createJourneyIntent);
//                NavHostFragment.findNavController(HomeFragment.this).navigate(R.id.nav_createJourney);
            }
        });

        return root;
    }
}