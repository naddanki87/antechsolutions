package com.antech.tasktracker;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.antech.tasktracker.data.Task;
import com.antech.tasktracker.data.TaskStore;
import com.antech.tasktracker.data.TaskValidator;

/** Create or edit a task. Everything is saved to internal storage immediately; sync happens later. */
public class EditActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "task_id";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TaskStore store;
    private Task task;
    private String dueDate;

    private EditText title;
    private EditText notes;
    private Button due;
    private Spinner priority;
    private CheckBox done;
    private TextView error;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        store = TaskStore.get(this);

        title = findViewById(R.id.title);
        notes = findViewById(R.id.notes);
        due = findViewById(R.id.due);
        priority = findViewById(R.id.priority);
        done = findViewById(R.id.done);
        error = findViewById(R.id.error);
        Button delete = findViewById(R.id.delete);

        ArrayAdapter<CharSequence> prios = ArrayAdapter.createFromResource(this, R.array.priority_labels,
                android.R.layout.simple_spinner_item);
        prios.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        priority.setAdapter(prios);
        priority.setSelection(1);

        due.setOnClickListener(v -> pickDate());
        findViewById(R.id.clear_due).setOnClickListener(v -> setDue(null));
        findViewById(R.id.save).setOnClickListener(v -> save());
        delete.setOnClickListener(v -> io.execute(() -> {
            store.deleteLocal(task.id);
            runOnUiThread(this::finish);
        }));

        String id = getIntent().getStringExtra(EXTRA_TASK_ID);
        setTitle(id == null ? R.string.new_task : R.string.edit_task);
        if (id == null) {
            task = new Task();
            delete.setVisibility(View.GONE);
            done.setVisibility(View.GONE);
            setDue(null);
        } else {
            io.execute(() -> {
                Task loaded = store.find(id);
                runOnUiThread(() -> {
                    if (loaded == null) {
                        finish();
                        return;
                    }
                    task = loaded;
                    title.setText(loaded.title);
                    notes.setText(loaded.notes);
                    priority.setSelection(Math.max(0, Arrays.asList(Task.PRIORITIES).indexOf(loaded.priority)));
                    done.setChecked(loaded.done);
                    setDue(loaded.dueDate);
                });
            });
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private void pickDate() {
        LocalDate start = dueDate == null ? LocalDate.now() : LocalDate.parse(dueDate);
        new DatePickerDialog(this, (view, y, m, d) -> setDue(LocalDate.of(y, m + 1, d).toString()),
                start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth()).show();
    }

    private void setDue(String date) {
        dueDate = date;
        due.setText(date == null ? getString(R.string.no_due_date) : date);
    }

    private void save() {
        if (task == null) return;
        String t = title.getText().toString();
        String n = notes.getText().toString().trim();
        String p = Task.PRIORITIES[priority.getSelectedItemPosition()];
        String problem = TaskValidator.validate(t, n, dueDate, p);
        if (problem != null) {
            error.setText(problem);
            error.setVisibility(View.VISIBLE);
            return;
        }
        task.title = t.trim();
        task.notes = n.isEmpty() ? null : n;
        task.dueDate = dueDate;
        task.priority = p;
        task.done = done.isChecked();
        io.execute(() -> {
            store.saveLocal(task);
            runOnUiThread(this::finish);
        });
    }
}
