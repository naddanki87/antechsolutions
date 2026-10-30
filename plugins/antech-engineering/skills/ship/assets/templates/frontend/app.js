'use strict';

// Same-origin API (served by Spring Boot). Data is only ever rendered with textContent.
const API = '/api/v1/items';

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
  form: document.getElementById('item-form'),
  formError: document.getElementById('form-error'),
  status: document.getElementById('status'),
  list: document.getElementById('items'),
};

function showStatus(text) {
  els.status.textContent = text;
  els.status.hidden = !text;
}

function renderItems(items) {
  els.list.replaceChildren();
  if (items.length === 0) return showStatus('No items yet.');
  showStatus('');
  for (const item of items) {
    const li = document.createElement('li');
    const title = document.createElement('span');
    title.textContent = item.title;
    const del = document.createElement('button');
    del.type = 'button';
    del.className = 'link';
    del.textContent = 'Delete';
    del.addEventListener('click', () => removeItem(item.id, del));
    li.append(title, del);
    els.list.append(li);
  }
}

async function loadItems() {
  showStatus('Loading…');
  try {
    const page = await api(`${API}?page=0&size=50&sort=createdAt,desc`);
    renderItems(page.content);
  } catch (e) {
    showStatus(`Could not load items: ${e.message}`);
  }
}

async function removeItem(id, button) {
  button.disabled = true;
  try {
    await api(`${API}/${encodeURIComponent(id)}`, { method: 'DELETE' });
    await loadItems();
  } catch (e) {
    button.disabled = false;
    showStatus(`Could not delete: ${e.message}`);
  }
}

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
    await api(API, { method: 'POST', body: { title, notes: els.form.notes.value.trim() || null } });
    els.form.reset();
    await loadItems();
  } catch (e) {
    const fields = e.fieldErrors.map((f) => `${f.field}: ${f.message}`).join(', ');
    els.formError.textContent = fields || e.message;
    els.formError.hidden = false;
  } finally {
    submit.disabled = false;
  }
});

loadItems();
