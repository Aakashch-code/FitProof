package com.example.fitproof.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.fitproof.R;
import com.example.fitproof.databinding.FragmentHomeBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1;
    private static final int STREAK_STEP_THRESHOLD = 100; // Minimum steps to count as an active day

    private FragmentHomeBinding binding;
    private GoogleSignInClient googleSignInClient;
    private FitnessOptions fitnessOptions;
    private long lastSyncTime = 0;

    // Activity result launchers
    private final ActivityResultLauncher<Intent> signInLauncher = createSignInLauncher();
    private final ActivityResultLauncher<String> permissionLauncher = createPermissionLauncher();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        initializeGoogleSignIn();
        setupSyncButton();
        checkPermissions();

        // Use binding directly
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        String currentDate = sdf.format(new Date());

        binding.tvDate.setText(currentDate);

        return binding.getRoot();
    }

    private void initializeGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso);

        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .build();
    }

    private void setupSyncButton() {
        binding.btnSyncWorkout.setOnClickListener(v -> {
            showToast("Syncing Google Fit data...");
            fetchGoogleFitData(true);
        });
    }

    private ActivityResultLauncher<Intent> createSignInLauncher() {
        return registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == requireActivity().RESULT_OK) {
                handleSignInResult(result.getData());
            } else {
                showToast("Google Sign-In cancelled");
            }
        });
    }

    private ActivityResultLauncher<String> createPermissionLauncher() {
        return registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                checkGoogleFitPermissions();
            } else {
                showToast("Activity recognition permission denied");
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
        } else {
            checkGoogleFitPermissions();
        }
    }

    private void checkGoogleFitPermissions() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account == null) {
            initiateGoogleSignIn();
            return;
        }

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(this, GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, account, fitnessOptions);
        } else {
            fetchGoogleFitData(false);
        }
    }

    private void initiateGoogleSignIn() {
        signInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void handleSignInResult(Intent data) {
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException.class);
            if (account != null) {
                fetchGoogleFitData(false);
            }
        } catch (ApiException e) {
            Log.e(TAG, "Google Sign-In failed: " + e.getStatusCode(), e);
            showToast("Sign-in failed: " + e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == requireActivity().RESULT_OK) {
                fetchGoogleFitData(false);
            } else {
                Log.e(TAG, "Google Fit permissions request failed with result code: " + resultCode);
                showToast("Google Fit permissions denied");
            }
        }
    }

    private void fetchGoogleFitData(boolean isManualSync) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account == null) {
            Log.w(TAG, "No signed-in account, initiating sign-in");
            showToast("Please sign in to Google Fit");
            initiateGoogleSignIn();
            return;
        }

        // Set time range: today for current data, last 7 days for weekly average and streak
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long endTime = cal.getTimeInMillis() + TimeUnit.DAYS.toMillis(1); // Include today
        long startTime = cal.getTimeInMillis() - TimeUnit.DAYS.toMillis(6); // Last 7 days

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Log.d(TAG, "Sending DataReadRequest for steps and calories");
        Fitness.getHistoryClient(requireContext(), account)
                .readData(readRequest)
                .addOnSuccessListener(dataReadResponse -> {
                    lastSyncTime = System.currentTimeMillis();
                    displayFitnessData(dataReadResponse);
                    if (isManualSync) {
                        showToast("Sync completed");
                    }
                })
                .addOnFailureListener(e -> handleDataFetchFailure(e, isManualSync));
    }

    private void handleDataFetchFailure(Exception e, boolean isManualSync) {
        String errorMessage = "Failed to fetch data: " + e.getMessage();
        if (e instanceof ApiException) {
            ApiException apiException = (ApiException) e;
            errorMessage += " (Status Code: " + apiException.getStatusCode() + ")";
            if (apiException.getStatusCode() == 5007) {
                Log.w(TAG, "NEEDS_OAUTH_PERMISSIONS, retrying permissions");
                checkGoogleFitPermissions();
            }
        }
        Log.e(TAG, errorMessage, e);
        showToast(errorMessage);
        if (isManualSync) {
            lastSyncTime = 0; // Reset sync time on failure
            updateSyncTimeUI();
        }
    }

    private void displayFitnessData(DataReadResponse dataReadResponse) {
        if (!isAdded() || getActivity() == null) {
            Log.w(TAG, "Fragment not attached, skipping UI update");
            return;
        }

        int todaySteps = 0;
        float todayCalories = 0f;
        float totalSteps = 0;
        float totalCalories = 0f;
        int activeDays = 0;
        int streak = 0;
        boolean isPreviousDayActive = true; // For streak calculation

        if (dataReadResponse.getBuckets().isEmpty()) {
            showToast("No fitness data available");
            updateUI(todaySteps, todayCalories, 0, 0, 0);
            return;
        }

        // Process each daily bucket (7 days, including today)
        for (var bucket : dataReadResponse.getBuckets()) {
            int dailySteps = 0;
            float dailyCalories = 0f;

            for (var dataSet : bucket.getDataSets()) {
                if (dataSet.isEmpty()) continue;
                for (var dp : dataSet.getDataPoints()) {
                    for (var field : dp.getDataType().getFields()) {
                        if (field.equals(Field.FIELD_STEPS)) {
                            dailySteps += dp.getValue(field).asInt();
                        } else if (field.equals(Field.FIELD_CALORIES)) {
                            dailyCalories += dp.getValue(field).asFloat();
                        }
                    }
                }
            }

            // Check if the bucket is for today (last bucket)
            if (bucket.getEndTime(TimeUnit.MILLISECONDS) == dataReadResponse.getBuckets()
                    .get(dataReadResponse.getBuckets().size() - 1)
                    .getEndTime(TimeUnit.MILLISECONDS)) {
                todaySteps = dailySteps;
                todayCalories = dailyCalories;
            }

            // Accumulate for weekly average
            totalSteps += dailySteps;
            totalCalories += dailyCalories;

            // Streak calculation: count days with steps >= STREAK_STEP_THRESHOLD
            if (dailySteps >= STREAK_STEP_THRESHOLD) {
                activeDays++;
                if (isPreviousDayActive) {
                    streak++;
                }
            } else {
                isPreviousDayActive = false;
            }
        }

        // Calculate weekly averages
        float weeklyAvgSteps = totalSteps / 7;
        float weeklyAvgCalories = totalCalories / 7;

        updateUI(todaySteps, todayCalories, weeklyAvgSteps, weeklyAvgCalories, streak);
    }

    private void updateUI(int todaySteps, float todayCalories, float weeklyAvgSteps, float weeklyAvgCalories, int streak) {
        binding.stepsCount.setText("Steps: " + todaySteps);
        binding.calories.setText("Calories: " + String.format("%.1f", todayCalories));
        binding.weeklyAvg.setText(String.format("Weekly Avg: %.0f steps, %.1f cal", weeklyAvgSteps, weeklyAvgCalories));
        binding.streak.setText("Streak: " + streak + " days");
        updateSyncTimeUI();
    }

    private void updateSyncTimeUI() {
        if (lastSyncTime == 0) {
            binding.tvLastSync.setText("Last Sync: Never");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            binding.tvLastSync.setText("Last Sync: " + sdf.format(new Date(lastSyncTime)));
        }
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}