package com.tuempresa.pathsecure;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ContactsActivity extends AppCompatActivity {

    private RecyclerView rvContacts;
    private ContactsAdapter adapter;
    private List<Contact> contactList;
    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        rvContacts = findViewById(R.id.rvContacts);
        Button btnAdd = findViewById(R.id.btnAddContact);

        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        contactList = new ArrayList<>();

        adapter = new ContactsAdapter(contactList, new ContactsAdapter.OnContactActionListener() {
            @Override
            public void onEdit(Contact contact) {
                showEditContactDialog(contact);
            }

            @Override
            public void onDelete(Contact contact) {
                deleteContact(contact);
            }
        });

        rvContacts.setAdapter(adapter);

        loadContacts();

        btnAdd.setOnClickListener(v -> showAddContactDialog());
    }

    private void loadContacts() {
        db.collection("users").document(userId).collection("contacts")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    contactList.clear();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Contact contact = doc.toObject(Contact.class);
                        contactList.add(contact);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nuevo Contacto");
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        final EditText inputName = viewInflated.findViewById(R.id.inputContactName);
        final EditText inputPhone = viewInflated.findViewById(R.id.inputContactPhone);
        final EditText inputEmail = viewInflated.findViewById(R.id.inputContactEmail);
        builder.setView(viewInflated);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            String phone = inputPhone.getText().toString().trim();
            String email = inputEmail.getText().toString().trim();
            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                saveContactToFirebase(name, phone, email);
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void showEditContactDialog(Contact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Contacto");
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);

        final EditText inputName = viewInflated.findViewById(R.id.inputContactName);
        final EditText inputPhone = viewInflated.findViewById(R.id.inputContactPhone);
        final EditText inputEmail = viewInflated.findViewById(R.id.inputContactEmail);

        inputName.setText(contact.getName());
        inputPhone.setText(contact.getPhone());
        inputEmail.setText(contact.getEmail());

        builder.setView(viewInflated);

        builder.setPositiveButton("Actualizar", (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            String phone = inputPhone.getText().toString().trim();
            String email = inputEmail.getText().toString().trim();

            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                updateContactInFirebase(contact.getId(), name, phone, email);
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void saveContactToFirebase(String name, String phone, String email) {
        String contactId = UUID.randomUUID().toString();
        Contact newContact = new Contact(contactId, name, phone, email);
        db.collection("users").document(userId).collection("contacts").document(contactId).set(newContact);
    }

    private void updateContactInFirebase(String contactId, String name, String phone, String email) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("email", email);

        db.collection("users").document(userId).collection("contacts").document(contactId)
                .update(updates)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Contacto actualizado", Toast.LENGTH_SHORT).show());
    }

    private void deleteContact(Contact contact) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar")
                .setMessage("¿Borrar a " + contact.getName() + "?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    db.collection("users").document(userId).collection("contacts").document(contact.getId()).delete();
                })
                .setNegativeButton("No", null)
                .show();
    }
}