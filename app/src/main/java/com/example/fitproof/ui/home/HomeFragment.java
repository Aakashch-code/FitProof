package com.example.fitproof.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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
import com.google.android.gms.tasks.Task;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1;
    private FragmentHomeBinding binding;
    private GoogleSignInClient googleSignInClient;

    // Launcher for Google Sign-In
    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == getActivity().RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            accessGoogleFitData();
                        }
                    } catch (ApiException e) {
                        Log.e(TAG, "Google Sign-In failed: " + e.getStatusCode(), e);
                        Toast.makeText(requireContext(), "Sign-in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "Google Sign-In cancelled", Toast.LENGTH_SHORT).show();
                }
            });

    // Launcher for ACTIVITY_RECOGNITION permission
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    checkGoogleFitPermissions();
                } else {
                    Toast.makeText(requireContext(), "Activity recognition permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Initialize Google Sign-In client
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso);

        // Check permissions
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
        } else {
            checkGoogleFitPermissions();
        }

        return binding.getRoot();
    }

    private void checkGoogleFitPermissions() {
        FitnessOptions fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .build();

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account == null) {
            initiateGoogleSignIn();
            return;
        }

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this,
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    account,
                    fitnessOptions);
        } else {
            accessGoogleFitData();
        }
    }

    private void initiateGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == getActivity().RESULT_OK) {
                accessGoogleFitData();
            } else {
                Toast.makeText(requireContext(), "Google Fit permissions denied", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Google Fit permissions request failed with result code: " + resultCode);
            }
        }
    }

    private void accessGoogleFitData() {
        Log.d(TAG, "Accessing Google Fit data");
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account == null) {
            Log.w(TAG, "No signed-in account in accessGoogleFitData, initiating Hintsinitiating sign-in");
            Toast.makeText(requireContext(), "Please sign in to Google Fit", Toast.LENGTH_SHORT).show();
            initiateGoogleSignIn();
            return;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA) // Updated: Remove output type
                .aggregate(DataType.TYPE_CALORIES_EXPENDED) // Updated: Remove output type
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Log.d(TAG, "Sending DataReadRequest for steps and calories");
        Fitness.getHistoryClient(requireContext(), account)
                .readData(readRequest)
                .addOnSuccessListener(dataReadResponse -> {
                    Log.d(TAG, "Data fetch successful, displaying data");
                    displayData(dataReadResponse);
                })
                .addOnFailureListener(e -> {
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
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                });
    }
    private void displayData(DataReadResponse dataReadResponse) {
        if (!isAdded() || getActivity() == null) {
            Log.w(TAG, "Fragment not attached, skipping UI update");
            return;
        }

        int totalSteps = 0;
        float totalCalories = 0f;

        if (dataReadResponse.getBuckets().isEmpty()) {
            Toast.makeText(requireContext(), "No fitness data available for the selected period", Toast.LENGTH_SHORT).show();
            binding.stepsCount.setText("Steps: N/A");
            binding.calories.setText("Calories: N/A");
            return;
        }

        for (var bucket : dataReadResponse.getBuckets()) {
            for (var dataSet : bucket.getDataSets()) {
                if (dataSet.isEmpty()) continue;
                for (var dp : dataSet.getDataPoints()) {
                    for (var field : dp.getDataType().getFields()) {
                        if (field.equals(Field.FIELD_STEPS)) {
                            totalSteps += dp.getValue(field).asInt();
                        } else if (field.equals(Field.FIELD_CALORIES)) {
                            totalCalories += dp.getValue(field).asFloat();
                        }
                    }
                }
            }
        }

        binding.stepsCount.setText("Steps: " + totalSteps);
        binding.calories.setText("Calories: " + String.format("%.1f", totalCalories));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}