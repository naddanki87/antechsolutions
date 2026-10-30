package com.antech.tasktracker;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.antech.tasktracker.data.Task;
import com.antech.tasktracker.data.TaskStore;
import com.antech.tasktracker.sync.ApiClient;
import com.antech.tasktracker.sync.SyncWorker;

public class MainActivity extends AppCompatActivity {

    private static final int MENU_SERVER = 1;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TaskStore store;
    private TaskAdapter adapter;
    private TextView status;
    private Boolean filter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = TaskStore.get(this);
        status = findViewById(R.id.status);

        adapter = new TaskAdapter(new TaskAdapter.Listener() {
            @Override
            public void onOpen(Task task) {
                startActivity(new Intent(MainActivity.this, EditActivity.class)
                        .putExtra(EditActivity.EXTRA_TASK_ID, task.id));
            }

            @Override
            public void onToggle(Task task, boolean done) {
                io.execute(() -> {
                    task.done = done;
                    store.saveLocal(task);
                    reload();
                });
            }
        });
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        RadioGroup filters = findViewById(R.id.filters);
        filters.setOnCheckedChangeListener((group, checkedId) -> {
            filter = checkedId == R.id.filter_open ? Boolean.FALSE
                    : checkedId == R.id.filter_done ? Boolean.TRUE : null;
            io.execute(this::reload);
        });

        findViewById(R.id.add).setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
        findViewById(R.id.sync).setOnClickListener(v -> sync());
    }

    @Override
    protected void onResume() {
        super.onResume();
        io.execute(this::reload);
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    /** Runs on the io thread. */
    private void reload() {
        List<Task> tasks = store.list(filter);
        runOnUiThread(() -> {
            adapter.submit(tasks);
            status.setText(tasks.isEmpty() ? getString(R.string.empty) : "");
            status.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void sync() {
        View button = findViewById(R.id.sync);
        button.setEnabled(false);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.syncing);
        new SyncWorker(this).syncNow((ok, message) -> runOnUiThread(() -> {
            button.setEnabled(true);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            io.execute(this::reload);
        }));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_SERVER, Menu.NONE, R.string.server_url);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() != MENU_SERVER) return super.onOptionsItemSelected(item);
        ApiClient api = new ApiClient(this);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(api.baseUrl());
        new AlertDialog.Builder(this)
                .setTitle(R.string.server_url)
                .setMessage(R.string.server_url_hint)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (!api.setBaseUrl(input.getText().toString())) {
                        Toast.makeText(this, R.string.server_url_invalid, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return true;
    }

    static final class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.Holder> {

        interface Listener {
            void onOpen(Task task);

            void onToggle(Task task, boolean done);
        }

        private final List<Task> items = new ArrayList<>();
        private final Listener listener;

        TaskAdapter(Listener listener) {
            this.listener = listener;
        }

        @SuppressWarnings("NotifyDataSetChanged")
        void submit(List<Task> tasks) {
            items.clear();
            items.addAll(tasks);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Task t = items.get(position);
            h.title.setText(t.title);
            h.title.setPaintFlags(t.done ? h.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : h.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            StringBuilder meta = new StringBuilder(t.priority.charAt(0) + t.priority.substring(1).toLowerCase());
            if (t.dueDate != null) meta.append(" · due ").append(t.dueDate);
            if (t.dirty) meta.append(" · not synced");
            h.meta.setText(meta);
            h.done.setOnCheckedChangeListener(null);
            h.done.setChecked(t.done);
            h.done.setOnCheckedChangeListener((b, checked) -> listener.onToggle(t, checked));
            h.itemView.setOnClickListener(v -> listener.onOpen(t));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final CheckBox done;
            final TextView title;
            final TextView meta;

            Holder(View v) {
                super(v);
                done = v.findViewById(R.id.done);
                title = v.findViewById(R.id.title);
                meta = v.findViewById(R.id.meta);
            }
        }
    }
}
