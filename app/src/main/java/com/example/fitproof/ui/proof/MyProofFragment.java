package com.example.fitproof.ui.proof;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.fitproof.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.example.fitproof.BuildConfig;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.codec.digest.DigestUtils;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

interface GitHubService {
    @POST("gists")
    Call<JsonObject> createGist(
            @Header("Authorization") String token,
            @Header("Accept") String acceptHeader,
            @Body Map<String, Object> body
    );
}

public class MyProofFragment extends Fragment {

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001;
    private static final String TAG = "MyProofFragment";
    private static final String GITHUB_API_BASE_URL = "https://api.github.com/";
    private static final String GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN;

    private TextView tvSelectedDate, tvWorkoutType, tvVerificationStatus;
    private TextView tvDurationValue, tvActivitySummary, tvHeartPtsValue, tvStepsValue, tvDistanceValue;
    private TextView tvHeartRateValue, tvPaceValue, tvErrorMessage;
    private ImageView ivWorkoutIcon, ivVerificationIcon, ivRetry;
    private LinearLayout layoutDateSelector, layoutVerificationBadge, layoutLoading;
    private CardView cardErrorState;
    private MaterialButton btnSyncWorkout, btnVerifyWorkout;
    private ProgressBar progressBar;

    private FitnessOptions fitnessOptions;
    private long selectedStartTime;
    private long selectedEndTime;

    public MyProofFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_proof, container, false);
        initializeViews(view);
        setupInitialData();
        setupClickListeners();
        setupGoogleFitOptions();
        checkGoogleFitPermissions();
        return view;
    }

    private void initializeViews(View view) {
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);
        tvWorkoutType = view.findViewById(R.id.tv_workout_type);
        tvVerificationStatus = view.findViewById(R.id.tv_verification_status);
        tvDurationValue = view.findViewById(R.id.tv_duration_value);
        tvStepsValue = view.findViewById(R.id.tv_steps_value);
        tvDistanceValue = view.findViewById(R.id.tv_distance_value);
        tvPaceValue = view.findViewById(R.id.tv_pace_value);
        tvErrorMessage = view.findViewById(R.id.tv_error_message);
        ivWorkoutIcon = view.findViewById(R.id.iv_workout_icon);
        ivVerificationIcon = view.findViewById(R.id.iv_verification_icon);
        ivRetry = view.findViewById(R.id.iv_retry);
        tvActivitySummary = view.findViewById(R.id.tv_workout_type);
        tvHeartPtsValue = view.findViewById(R.id.tv_heart_pts_value);
        layoutDateSelector = view.findViewById(R.id.layout_date_selector);
        layoutVerificationBadge = view.findViewById(R.id.layout_verification_badge);
        layoutLoading = view.findViewById(R.id.layout_loading);
        cardErrorState = view.findViewById(R.id.card_error_state);
        btnSyncWorkout = view.findViewById(R.id.btn_sync_workout);
        btnVerifyWorkout = view.findViewById(R.id.btn_verify_workout);

        resetAllValues();
    }

    private void resetAllValues() {
        tvDurationValue.setText("00:00");
        tvStepsValue.setText("0");
        tvDistanceValue.setText("0.00 km");
        tvPaceValue.setText("-- min/km");
        tvActivitySummary.setText("No activities");
        tvHeartPtsValue.setText("0");
    }

    private void setupInitialData() {
        Calendar today = Calendar.getInstance();
        setSelectedDate(today);
        tvWorkoutType.setText("No Activity");
        tvVerificationStatus.setText("Not Synced");
        layoutVerificationBadge.setBackgroundResource(R.drawable.bg_verification_pending);
    }

    private void setSelectedDate(Calendar calendar) {
        String formattedDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(calendar.getTime());
        tvSelectedDate.setText(formattedDate);

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        selectedStartTime = calendar.getTimeInMillis();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        selectedEndTime = calendar.getTimeInMillis();
    }

    private void setupClickListeners() {
        layoutDateSelector.setOnClickListener(v -> showDatePicker());
        btnSyncWorkout.setOnClickListener(v -> startSyncProcess());
        btnVerifyWorkout.setOnClickListener(v -> startVerificationProcess());
        ivRetry.setOnClickListener(v -> retrySync());
    }

    private void setupGoogleFitOptions() {
        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_HEART_POINTS, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_HEART_POINTS, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.AGGREGATE_SPEED_SUMMARY, FitnessOptions.ACCESS_READ)
                .build();
    }

    private void checkGoogleFitPermissions() {
        if (getContext() == null) return;

        GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions);

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(this, GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, account, fitnessOptions);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions);
            if (GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.d(TAG, "Google Fit permissions granted");
            } else {
                showErrorState("Google Fit permissions are required to sync workout data");
            }
        }
    }

    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(selectedYear, selectedMonth, selectedDay);

                    Calendar today = Calendar.getInstance();
                    if (selectedCalendar.after(today)) {
                        showErrorState("Cannot select future dates");
                        return;
                    }

                    setSelectedDate(selectedCalendar);
                    resetAllValues();
                    tvVerificationStatus.setText("Not Synced");
                    layoutVerificationBadge.setBackgroundResource(R.drawable.bg_verification_pending);
                    cardErrorState.setVisibility(View.GONE);
                },
                year, month, day
        );

        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void startSyncProcess() {
        if (getContext() == null) return;

        GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions);

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            showErrorState("Please grant Google Fit permissions first");
            checkGoogleFitPermissions();
            return;
        }

        showLoadingState(true);
        cardErrorState.setVisibility(View.GONE);
        resetAllValues();
        fetchGoogleFitData(account);
    }

    private void fetchGoogleFitData(GoogleSignInAccount account) {
        Log.d(TAG, "Fetching Google Fit data for period: " + new Date(selectedStartTime) + " to " + new Date(selectedEndTime));
        fetchSessionData(account);
        fetchAggregatedData(account);
    }

    private void fetchSessionData(GoogleSignInAccount account) {
        SessionReadRequest readRequest = new SessionReadRequest.Builder()
                .setTimeInterval(selectedStartTime, selectedEndTime, TimeUnit.MILLISECONDS)
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .read(DataType.TYPE_DISTANCE_DELTA)
                .read(DataType.TYPE_HEART_RATE_BPM)
                .read(DataType.TYPE_SPEED)
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .build();

        Fitness.getSessionsClient(requireActivity(), account)
                .readSession(readRequest)
                .addOnSuccessListener(response -> {
                    Log.d(TAG, "Session data fetched successfully. Sessions count: " + response.getSessions().size());

                    boolean hasSessionData = false;
                    String detectedActivity = "No Activity";
                    long totalDuration = 0;

                    for (Session session : response.getSessions()) {
                        Log.d(TAG, "Session: " + session.getName() + ", Activity: " + session.getActivity());
                        hasSessionData = true;
                        detectedActivity = getFriendlyActivityName(session.getActivity());
                        totalDuration += session.getEndTime(TimeUnit.MILLISECONDS) - session.getStartTime(TimeUnit.MILLISECONDS);

                        List<DataSet> dataSets = response.getDataSet(session);
                        for (DataSet dataSet : dataSets) {
                            processDataSet(dataSet);
                        }
                    }

                    if (hasSessionData) {
                        tvWorkoutType.setText(detectedActivity);
                        if (totalDuration > 0) {
                            long minutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration);
                            long seconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration) % 60;
                            tvDurationValue.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                        }
                    }

                    finalizeSyncProcess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch session data", e);
                    finalizeSyncProcess();
                });
    }

    private void fetchAggregatedData(GoogleSignInAccount account) {
        fetchSteps(account);
        fetchActivityNames(account);
        fetchDistance(account);
        fetchHeartPts(account);
        fetchSpeed(account);
        fetchDuration(account);
    }

    private void fetchSteps(GoogleSignInAccount account) {
        DataReadRequest stepsRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .setTimeRange(selectedStartTime, selectedEndTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Fitness.getHistoryClient(requireActivity(), account)
                .readData(stepsRequest)
                .addOnSuccessListener(response -> {
                    int totalSteps = 0;
                    Log.d(TAG, "Number of buckets: " + response.getBuckets().size());
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                                totalSteps += dataPoint.getValue(Field.FIELD_STEPS).asInt();
                            }
                        }
                    }
                    tvStepsValue.setText(String.valueOf(totalSteps));
                    Log.d(TAG, "Steps fetched: " + totalSteps);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch steps: " + e.getMessage(), e);
                    showErrorState("Failed to fetch steps");
                });
    }

    private void fetchDistance(GoogleSignInAccount account) {
        DataReadRequest distanceRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .setTimeRange(selectedStartTime, selectedEndTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Fitness.getHistoryClient(requireActivity(), account)
                .readData(distanceRequest)
                .addOnSuccessListener(response -> {
                    float totalDistance = 0;
                    Log.d(TAG, "Number of buckets: " + response.getBuckets().size());
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                                totalDistance += dataPoint.getValue(Field.FIELD_DISTANCE).asFloat();
                            }
                        }
                    }
                    float distanceInKm = totalDistance / 1000f;
                    tvDistanceValue.setText(String.format(Locale.getDefault(), "%.2f km", distanceInKm));
                    Log.d(TAG, "Distance fetched: " + distanceInKm + " km");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch distance: " + e.getMessage(), e);
                    showErrorState("Failed to fetch distance");
                });
    }

    private void fetchHeartPts(GoogleSignInAccount account) {
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_HEART_POINTS, DataType.AGGREGATE_HEART_POINTS)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(selectedStartTime, selectedEndTime, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(requireActivity(), account)
                .readData(readRequest)
                .addOnSuccessListener(response -> {
                    float totalHeartPoints = 0;
                    Log.d(TAG, "Number of buckets: " + response.getBuckets().size());
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                                totalHeartPoints += dataPoint.getValue(Field.FIELD_INTENSITY).asFloat();
                            }
                        }
                    }
                    tvHeartPtsValue.setText(String.format(Locale.getDefault(), "%.0f", totalHeartPoints));
                    Log.d(TAG, "Heart Points fetched: " + totalHeartPoints);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch heart points: " + e.getMessage(), e);
                    showErrorState("Failed to fetch heart points");
                });
    }

    private void fetchSpeed(GoogleSignInAccount account) {
        DataReadRequest speedRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_SPEED, DataType.AGGREGATE_SPEED_SUMMARY)
                .setTimeRange(selectedStartTime, selectedEndTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Fitness.getHistoryClient(requireActivity(), account)
                .readData(speedRequest)
                .addOnSuccessListener(response -> {
                    float avgSpeed = 0;
                    Log.d(TAG, "Number of buckets: " + response.getBuckets().size());
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                                avgSpeed = dataPoint.getValue(Field.FIELD_AVERAGE).asFloat();
                                break;
                            }
                        }
                    }
                    if (avgSpeed > 0) {
                        float paceMinPerKm = (1000f / avgSpeed) / 60f;
                        tvPaceValue.setText(String.format(Locale.getDefault(), "%.2f min/km", paceMinPerKm));
                        Log.d(TAG, "Pace fetched: " + paceMinPerKm + " min/km");
                    } else {
                        tvPaceValue.setText("-- min/km");
                        Log.d(TAG, "No valid pace data");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch speed: " + e.getMessage(), e);
                    showErrorState("Failed to fetch pace");
                });
    }

    private void fetchDuration(GoogleSignInAccount account) {
        DataReadRequest durationRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                .setTimeRange(selectedStartTime, selectedEndTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Fitness.getHistoryClient(requireActivity(), account)
                .readData(durationRequest)
                .addOnSuccessListener(response -> {
                    long totalDuration = 0;
                    Log.d(TAG, "Number of buckets: " + response.getBuckets().size());
                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                                totalDuration += dataPoint.getEndTime(TimeUnit.MILLISECONDS) - dataPoint.getStartTime(TimeUnit.MILLISECONDS);
                            }
                        }
                    }
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration);
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(totalDuration) % 60;
                    tvDurationValue.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                    Log.d(TAG, "Duration fetched: " + minutes + " minutes " + seconds + " seconds");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch duration: " + e.getMessage(), e);
                    showErrorState("Failed to fetch duration");
                });
    }

    private void fetchActivityNames(GoogleSignInAccount account) {
        Log.d(TAG, "Fetching activity names from " + new Date(selectedStartTime) + " to " + new Date(selectedEndTime));

        DataReadRequest activityRequest = new DataReadRequest.Builder()
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .setTimeRange(selectedStartTime, selectedEndTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Fitness.getHistoryClient(requireActivity(), account)
                .readData(activityRequest)
                .addOnSuccessListener(response -> {
                    Set<String> activityNames = new HashSet<>();
                    Log.d(TAG, "Number of buckets: " + response.getBuckets().size());

                    for (Bucket bucket : response.getBuckets()) {
                        for (DataSet dataSet : bucket.getDataSets()) {
                            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                                String activityName = dataPoint.getValue(Field.FIELD_ACTIVITY).asActivity();
                                String friendlyName = getFriendlyActivityName(activityName);
                                activityNames.add(friendlyName);
                                Log.d(TAG, "Activity: " + friendlyName);
                            }
                        }
                    }

                    String summary;
                    if (activityNames.isEmpty()) {
                        summary = "No activities recorded";
                    } else {
                        summary = TextUtils.join(", ", activityNames);
                    }

                    requireActivity().runOnUiThread(() -> tvActivitySummary.setText(summary));
                    Log.d(TAG, "Activity names: " + summary);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch activity names: " + e.getMessage(), e);
                    if (e instanceof ApiException) {
                        Log.e(TAG, "API error code: " + ((ApiException) e).getStatusCode());
                    }
                    requireActivity().runOnUiThread(() -> tvActivitySummary.setText("Error fetching activities"));
                    showErrorState("Failed to fetch activity names");
                });
    }

    private String getFriendlyActivityName(String activity) {
        switch (activity) {
            case FitnessActivities.WALKING:
                return "Walking";
            case FitnessActivities.RUNNING:
                return "Running";
            case FitnessActivities.RUNNING_JOGGING:
                return "Jogging";
            case FitnessActivities.BIKING:
                return "Cycling";
            case FitnessActivities.SLEEP:
                return "Sleeping";
            case FitnessActivities.YOGA:
                return "Yoga";
            default:
                return activity;
        }
    }

    private void processDataSet(DataSet dataSet) {
        DataType dataType = dataSet.getDataType();

        for (DataPoint dataPoint : dataSet.getDataPoints()) {
            if (dataType.equals(DataType.TYPE_STEP_COUNT_DELTA)) {
                int steps = dataPoint.getValue(Field.FIELD_STEPS).asInt();
                tvStepsValue.setText(String.valueOf(steps));
                Log.d(TAG, "Session steps: " + steps);
            } else if (dataType.equals(DataType.TYPE_DISTANCE_DELTA)) {
                float distance = dataPoint.getValue(Field.FIELD_DISTANCE).asFloat() / 1000f;
                tvDistanceValue.setText(String.format(Locale.getDefault(), "%.2f km", distance));
                Log.d(TAG, "Session distance: " + distance + " km");
            } else if (dataType.equals(DataType.TYPE_HEART_RATE_BPM)) {
                float heartRate = dataPoint.getValue(Field.FIELD_BPM).asFloat();
                tvHeartRateValue.setText(String.format(Locale.getDefault(), "%.0f bpm", heartRate));
                Log.d(TAG, "Session heart rate: " + heartRate + " bpm");
            } else if (dataType.equals(DataType.TYPE_SPEED)) {
                float speed = dataPoint.getValue(Field.FIELD_SPEED).asFloat();
                if (speed > 0) {
                    float pace = (1000f / speed) / 60f;
                    tvPaceValue.setText(String.format(Locale.getDefault(), "%.2f min/km", pace));
                    Log.d(TAG, "Session pace: " + pace + " min/km");
                }
            } else if (dataType.equals(DataType.TYPE_ACTIVITY_SEGMENT)) {
                String activityName = getFriendlyActivityName(dataPoint.getValue(Field.FIELD_ACTIVITY).asActivity());
                tvWorkoutType.setText(activityName);
                Log.d(TAG, "Session activity: " + activityName);
            }
        }
    }

    private void finalizeSyncProcess() {
        showLoadingState(false);
        tvVerificationStatus.setText("Synced");
        layoutVerificationBadge.setBackgroundResource(R.drawable.bg_verification_success);
        btnVerifyWorkout.setEnabled(true);
        Log.d(TAG, "Sync process completed");
    }

    private void startVerificationProcess() {
        showLoadingState(true);
        cardErrorState.setVisibility(View.GONE);

        HashMap<String, Object> workoutData = new HashMap<>();
        workoutData.put("date", tvSelectedDate.getText().toString());
        workoutData.put("workoutType", tvWorkoutType.getText().toString());
        workoutData.put("duration", tvDurationValue.getText().toString());
        workoutData.put("activitySummary", tvActivitySummary.getText().toString());
        workoutData.put("steps", tvStepsValue.getText().toString());
        workoutData.put("distance", tvDistanceValue.getText().toString());
        workoutData.put("heartPts", tvHeartPtsValue.getText().toString());
        workoutData.put("pace", tvPaceValue.getText().toString());

        Log.d(TAG, "Workout data before verification: " + workoutData);

        new Handler().postDelayed(() -> {
            boolean isVerified = simulateVerification();
            showLoadingState(false);

            if (isVerified) {
                tvVerificationStatus.setText("Verified");
                layoutVerificationBadge.setBackgroundResource(R.drawable.bg_verification_success);
                ivVerificationIcon.setImageResource(R.drawable.ic_verify);
                btnVerifyWorkout.setEnabled(false);
                btnVerifyWorkout.setText("Verified");

                String jsonWithHash = createJsonWithHash(workoutData);
                if (jsonWithHash != null) {
                    Log.d(TAG, "JSON with hash: " + jsonWithHash);
                    publishToGitHubGist(jsonWithHash);
                } else {
                    showErrorState("Failed to create JSON with hash");
                }

                showWorkoutDataPopup(workoutData);
            } else {
                showErrorState("Verification failed. Please ensure you have valid workout data.");
            }
        }, 2000);
    }

    private boolean simulateVerification() {
        String steps = tvStepsValue.getText().toString();
        String activitySummary = tvActivitySummary.getText().toString();
        String distance = tvDistanceValue.getText().toString();

        try {
            int stepCount = Integer.parseInt(steps);
            float distanceCount = Float.parseFloat(distance.replace(" km", ""));
            boolean hasActivity = !activitySummary.equals("No activities recorded") && !activitySummary.equals("Error fetching activities");

            return stepCount > 100 || distanceCount > 0.1 || hasActivity;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String computeSHA256Hash(String jsonString) {
        try {
            return DigestUtils.sha256Hex(jsonString);
        } catch (Exception e) {
            Log.e(TAG, "Error computing SHA-256 hash", e);
            return null;
        }
    }

    private String createJsonWithHash(HashMap<String, Object> workoutData) {
        try {
            String proofId = UUID.randomUUID().toString();
            Gson gson = new Gson();
            String workoutJson = gson.toJson(workoutData);
            String hash = computeSHA256Hash(workoutJson);

            if (hash == null) {
                return null;
            }

            Map<String, Object> finalJson = new HashMap<>();
            finalJson.put("proof_id", proofId);
            finalJson.put("timestamp", System.currentTimeMillis());
            finalJson.put("workout_data", workoutData);
            finalJson.put("hash", hash);
            finalJson.put("hash_algorithm", "SHA-256");

            return gson.toJson(finalJson);
        } catch (Exception e) {
            Log.e(TAG, "Error creating JSON with hash", e);
            return null;
        }
    }

    private void publishToGitHubGist(String jsonContent) {
        if (TextUtils.isEmpty(GITHUB_TOKEN) || GITHUB_TOKEN.equals("Personal Access Token")) {
            showErrorState("GitHub token not configured. Please set your personal access token.");
            return;
        }

        showLoadingState(true);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(GITHUB_API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GitHubService service = retrofit.create(GitHubService.class);

        Map<String, Object> fileContent = new HashMap<>();
        fileContent.put("content", jsonContent);

        Map<String, Object> files = new HashMap<>();
        files.put("workout-proof.json", fileContent);

        Map<String, Object> gistData = new HashMap<>();
        gistData.put("description", "Workout Proof - " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        gistData.put("public", true);
        gistData.put("files", files);

        String authToken = "token " + GITHUB_TOKEN;
        Call<JsonObject> call = service.createGist(authToken, "application/vnd.github.v3+json", gistData);

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                showLoadingState(false);

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject gistResponse = response.body();
                    String gistUrl = gistResponse.has("html_url") ?
                            gistResponse.get("html_url").getAsString() : "Unknown URL";

                    Log.d(TAG, "Successfully published to GitHub Gist: " + gistUrl);

                    new AlertDialog.Builder(requireContext())
                            .setTitle("Published to GitHub")
                            .setMessage("Workout proof successfully published!\n\nGist URL: " + gistUrl)
                            .setPositiveButton("Open", (dialog, which) -> {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(gistUrl));
                                startActivity(browserIntent);
                            })
                            .setNegativeButton("OK", (dialog, which) -> dialog.dismiss())
                            .show();
                } else {
                    String errorMessage = "Failed to publish to GitHub";
                    if (response.errorBody() != null) {
                        try {
                            errorMessage = response.errorBody().string();
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading error response", e);
                        }
                    }
                    showErrorState(errorMessage);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                showLoadingState(false);
                showErrorState("Network error: " + t.getMessage());
                Log.e(TAG, "GitHub API call failed", t);
            }
        });
    }

    private void showWorkoutDataPopup(HashMap<String, Object> workoutData) {
        StringBuilder message = new StringBuilder();
        for (String key : workoutData.keySet()) {
            message.append(key).append(": ").append(workoutData.get(key)).append("\n");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Workout Verified")
                .setMessage(message.toString())
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showLoadingState(boolean show) {
        if (layoutLoading != null) {
            layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (btnSyncWorkout != null) {
            btnSyncWorkout.setEnabled(!show);
        }
        if (btnVerifyWorkout != null && !btnVerifyWorkout.getText().toString().equals("Verified")) {
            btnVerifyWorkout.setEnabled(!show);
        }
    }

    private void showErrorState(String message) {
        if (cardErrorState != null) {
            cardErrorState.setVisibility(View.VISIBLE);
        }
        if (tvErrorMessage != null) {
            tvErrorMessage.setText(message);
        }
        Log.e(TAG, "Error: " + message);
        showLoadingState(false);
    }

    private void retrySync() {
        cardErrorState.setVisibility(View.GONE);
        startSyncProcess();
    }
}