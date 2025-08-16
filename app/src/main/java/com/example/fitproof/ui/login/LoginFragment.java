package com.example.fitproof.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.fitproof.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;

public class LoginFragment extends Fragment {

    private static final int RC_SIGN_IN = 100;
    private GoogleSignInClient mGoogleSignInClient;
    private SignInButton signInButton;
    private Button logoutButton;

    public LoginFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);

        // Buttons
        signInButton = view.findViewById(R.id.btnGoogle);
        logoutButton = view.findViewById(R.id.btnLogout);

        signInButton.setOnClickListener(v -> signIn());

        logoutButton.setOnClickListener(v -> {
            mGoogleSignInClient.signOut().addOnCompleteListener(requireActivity(), task -> {
                Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
                updateNavHeader(null);
                signInButton.setVisibility(View.VISIBLE);
                logoutButton.setVisibility(View.GONE);
            });
        });

        // Check if already signed in
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account != null) {
            updateNavHeader(account);
            signInButton.setVisibility(View.GONE);
            logoutButton.setVisibility(View.VISIBLE);
        } else {
            signInButton.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.GONE);
        }

        return view;
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    @SuppressWarnings("deprecation")
    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                Toast.makeText(getContext(), "Welcome " + account.getDisplayName(), Toast.LENGTH_SHORT).show();
                updateNavHeader(account);
                signInButton.setVisibility(View.GONE);
                logoutButton.setVisibility(View.VISIBLE);
            }
        } catch (ApiException e) {
            Log.e("GoogleSignIn", "Sign-in failed: code=" + e.getStatusCode(), e);
            Toast.makeText(getContext(), "Sign-in failed. Code: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNavHeader(GoogleSignInAccount account) {
        NavigationView navigationView = requireActivity().findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);

        TextView navUserName = headerView.findViewById(R.id.Name);
        TextView navUserEmail = headerView.findViewById(R.id.Gmail);
        ImageView navUserImage = headerView.findViewById(R.id.profileImage);

        if (account != null) {
            navUserName.setText(account.getDisplayName());
            navUserEmail.setText(account.getEmail());

            if (account.getPhotoUrl() != null) {
                Glide.with(this).load(account.getPhotoUrl()).into(navUserImage);
            } else {
                navUserImage.setImageResource(R.drawable.default_avatar); // fallback image
            }
        } else {
            navUserName.setText("Guest");
            navUserEmail.setText("");
            navUserImage.setImageResource(R.drawable.default_avatar);
        }
    }

}
