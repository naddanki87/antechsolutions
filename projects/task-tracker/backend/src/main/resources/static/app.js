'use strict';

// Same-origin API (served by Spring Boot). Data is only ever rendered with textContent.
const API = '/api/v1/tasks';

async function api(path, { method = 'GET', body, timeoutMs = 10000 } = {}) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
      signal: ctrl.signal,
    });
    if (res.status === 204) return null;
    const data = await res.json().catch(() => null);
    if (!res.ok) {
      const err = new Error((data && data.detail) || `Request failed (${res.status})`);
      err.status = res.status;
      err.fieldErrors = (data && data.errors) || [];
      throw err;
    }
    return data;
  } catch (e) {
    if (e.name === 'AbortError') throw new Error('The server took too long to respond');
    throw e;
  } finally {
    clearTimeout(timer);
  }
}

const els = {
  form: document.getElementById('task-form'),
  formError: document.getElementById('form-error'),
  status: document.getElementById('status'),
  list: document.getElementById('tasks'),
  chips: document.querySelectorAll('.chip'),
};

let filter = '';

function showStatus(text) {
  els.status.textContent = text;
  els.status.hidden = !text;
}

function metaText(task) {
  const parts = [task.priority.charAt(0) + task.priority.slice(1).toLowerCase()];
  if (task.dueDate) parts.push(`due ${task.dueDate}`);
  if (task.notes) parts.push(task.notes);
  return parts.join(' · ');
}

function renderTask(task) {
  const li = document.createElement('li');
  li.className = task.done ? 'task done' : 'task';

  const box = document.createElement('input');
  box.type = 'checkbox';
  box.checked = task.done;
  box.setAttribute('aria-label', `Mark "${task.title}" as ${task.done ? 'open' : 'done'}`);
  box.addEventListener('change', () => toggleDone(task, box));

  const text = document.createElement('div');
  const title = document.createElement('div');
  title.className = 'title';
  title.textContent = task.title;
  const meta = document.createElement('div');
  meta.className = `meta prio-${task.priority}`;
  meta.textContent = metaText(task);
  text.append(title, meta);

  const del = document.createElement('button');
  del.type = 'button';
  del.className = 'link';
  del.textContent = 'Delete';
  del.addEventListener('click', () => removeTask(task, del));

  li.append(box, text, del);
  return li;
}

async function loadTasks() {
  showStatus('Loading…');
  try {
    const query = filter === '' ? '' : `&done=${filter}`;
    const page = await api(`${API}?page=0&size=100&sort=createdAt,desc${query}`);
    els.list.replaceChildren(...page.content.map(renderTask));
    showStatus(page.content.length === 0 ? 'No tasks here yet.' : '');
  } catch (e) {
    els.list.replaceChildren();
    showStatus(`Could not load tasks: ${e.message}`);
  }
}

async function toggleDone(task, box) {
  box.disabled = true;
  try {
    await api(`${API}/${encodeURIComponent(task.id)}`, {
      method: 'PUT',
      body: {
        title: task.title, notes: task.notes, dueDate: task.dueDate,
        priority: task.priority, done: box.checked, version: task.version,
      },
    });
  } catch (e) {
    showStatus(e.status === 409 ? 'This task changed elsewhere - list refreshed.' : `Could not update: ${e.message}`);
  }
  await loadTasks();
}

async function removeTask(task, button) {
  button.disabled = true;
  try {
    await api(`${API}/${encodeURIComponent(task.id)}`, { method: 'DELETE' });
    await loadTasks();
  } catch (e) {
    button.disabled = false;
    showStatus(`Could not delete: ${e.message}`);
  }
}

els.chips.forEach((chip) => {
  chip.addEventListener('click', () => {
    filter = chip.dataset.filter;
    els.chips.forEach((c) => c.setAttribute('aria-pressed', String(c === chip)));
    loadTasks();
  });
});

els.form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const submit = els.form.querySelector('button[type="submit"]');
  const title = els.form.title.value.trim();
  els.formError.hidden = true;
  if (!title) {
    els.formError.textContent = 'Title is required.';
    els.formError.hidden = false;
    return;
  }
  submit.disabled = true;
  try {
    await api(API, {
      method: 'POST',
      body: {
        title,
        notes: els.form.notes.value.trim() || null,
        dueDate: els.form.dueDate.value || null,
        priority: els.form.priority.value,
      },
    });
    els.form.reset();
    await loadTasks();
  } catch (e) {
    const fields = e.fieldErrors.map((f) => `${f.field}: ${f.message}`).join(', ');
    els.formError.textContent = fields || e.message;
    els.formError.hidden = false;
  } finally {
    submit.disabled = false;
  }
});

loadTasks();
