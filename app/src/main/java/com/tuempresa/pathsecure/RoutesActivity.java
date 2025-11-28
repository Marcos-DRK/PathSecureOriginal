package com.tuempresa.pathsecure;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class RoutesActivity extends AppCompatActivity {

    private RecyclerView rvRoutes;
    private RoutesAdapter adapter;
    private List<Route> routeList;
    private FirebaseFirestore db;
    private String userId;

    private LinearLayout layoutRecording;
    private Button btnRecord, btnManual, btnStop;
    private boolean isRecordingMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routes);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        rvRoutes = findViewById(R.id.rvRoutes);
        btnRecord = findViewById(R.id.btnRecordRoute);
        btnManual = findViewById(R.id.btnManualRoute);
        btnStop = findViewById(R.id.btnStopRecording);
        layoutRecording = findViewById(R.id.layoutRecordingControls);
        Button btnBack = findViewById(R.id.btnBackMainRoutes);

        rvRoutes.setLayoutManager(new LinearLayoutManager(this));
        routeList = new ArrayList<>();

        adapter = new RoutesAdapter(routeList, new RoutesAdapter.OnRouteActionListener() {
            @Override
            public void onViewRoute(Route route) {
                Intent intent = new Intent(RoutesActivity.this, MainActivity.class);
                intent.putExtra("VIEW_ROUTE_ID", route.getId());
                startActivity(intent);
            }

            @Override
            public void onDeleteRoute(Route route) {
                deleteRoute(route);
            }

            @Override
            public void onEditRoute(Route route) {
                showEditRouteDialog(route);
            }
        });
        rvRoutes.setAdapter(adapter);

        loadRoutes();

        btnRecord.setOnClickListener(v -> startRecordingFlow());
        btnStop.setOnClickListener(v -> stopRecordingFlow());

        btnManual.setOnClickListener(v -> {
            Intent intent = new Intent(RoutesActivity.this, MainActivity.class);
            intent.putExtra("MODE_MANUAL_DRAW", true);
            startActivity(intent);
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadRoutes() {
        db.collection("users").document(userId).collection("routes")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    routeList.clear();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        routeList.add(doc.toObject(Route.class));
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showEditRouteDialog(Route route) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Renombrar Ruta");
        final EditText input = new EditText(this);
        input.setText(route.getName());
        builder.setView(input);

        builder.setPositiveButton("Actualizar", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                db.collection("users").document(userId).collection("routes").document(route.getId())
                        .update("name", newName)
                        .addOnSuccessListener(aVoid -> Toast.makeText(this, "Nombre actualizado", Toast.LENGTH_SHORT).show());
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void startRecordingFlow() {
        isRecordingMode = true;
        layoutRecording.setVisibility(View.VISIBLE);
        btnRecord.setEnabled(false);
        btnManual.setEnabled(false);

        Intent serviceIntent = new Intent(this, RouteRecordingService.class);
        serviceIntent.setAction("START_RECORDING");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopRecordingFlow() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Guardar Ruta");
        final EditText input = new EditText(this);
        input.setHint("Nombre del recorrido");
        builder.setView(input);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String name = input.getText().toString();
            if (name.isEmpty()) name = "Ruta sin nombre";

            Intent serviceIntent = new Intent(this, RouteRecordingService.class);
            serviceIntent.setAction("STOP_RECORDING");
            serviceIntent.putExtra("ROUTE_NAME", name);
            startService(serviceIntent);

            layoutRecording.setVisibility(View.GONE);
            btnRecord.setEnabled(true);
            btnManual.setEnabled(true);
            isRecordingMode = false;
        });
        builder.show();
    }

    private void deleteRoute(Route route) {
        db.collection("users").document(userId).collection("routes").document(route.getId()).delete();
    }
}