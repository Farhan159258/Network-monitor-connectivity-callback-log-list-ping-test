package com.example.networkmoniter;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private RecyclerView rvLogs;
    private LogAdapter adapter;
    private final ArrayList<String> logs = new ArrayList<>();

    private ConnectivityManager cm;
    private String lastStatus = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Callbacks arrive on a background thread, so we post to the main thread.
    private final ConnectivityManager.NetworkCallback callback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    updateFromNetwork(network);
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    updateFromNetwork(network);
                }

                @Override
                public void onLost(Network network) {
                    mainHandler.post(() -> showStatus("Offline"));
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        rvLogs = findViewById(R.id.rvLogs);
        Button btnTest = findViewById(R.id.btnTest);

        adapter = new LogAdapter(logs);
        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        rvLogs.setAdapter(adapter);

        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        btnTest.setOnClickListener(v -> testConnection());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If there is no network, the callback never fires, so set the initial state here.
        if (cm.getActiveNetwork() == null) {
            showStatus("Offline");
        }
        cm.registerDefaultNetworkCallback(callback);
    }

    @Override
    protected void onStop() {
        super.onStop();
        cm.unregisterNetworkCallback(callback); // avoids leaks and wasted work
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void updateFromNetwork(Network network) {
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        String status;
        if (caps == null) {
            status = "Offline";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            status = "Wi-Fi";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            status = "Mobile data";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            status = "Ethernet";
        } else {
            status = "Connected (other)";
        }
        final String s = status;
        mainHandler.post(() -> showStatus(s));
    }

    // Main thread only.
    private void showStatus(String status) {
        if (status.equals(lastStatus)) return; // skip duplicate events
        lastStatus = status;
        tvStatus.setText("Status: " + status);
        addLog(status);
    }

    // Main thread only.
    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        logs.add(0, time + "  " + message);
        adapter.notifyItemInserted(0);
        rvLogs.scrollToPosition(0);
    }

    private void testConnection() {
        executor.execute(() -> {
            long start = System.currentTimeMillis();
            String result;
            try {
                URL url = new URL("https://www.google.com/generate_204");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                int code = c.getResponseCode();
                c.disconnect();
                long ms = System.currentTimeMillis() - start;
                result = "Ping OK (" + code + ") " + ms + " ms";
            } catch (Exception e) {
                result = "Ping failed: " + e.getClass().getSimpleName();
            }
            final String msg = result;
            mainHandler.post(() -> addLog(msg));
        });
    }
}