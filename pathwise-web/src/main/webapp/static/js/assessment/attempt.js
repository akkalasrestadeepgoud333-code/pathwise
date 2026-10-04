// Assessment attempt page: countdown, autosave, auto-submit, leave warning.
// The server is authoritative: it stores the deadline, refuses late saves and auto-submits overdue
// attempts on its own. This script only makes the experience smoother; without it the page still works.

const form = document.getElementById("attempt-form");
const timer = document.querySelector("[data-deadline]");
const clock = document.querySelector("[data-clock]");
const status = document.querySelector("[data-save-status]");
const answeredEl = document.querySelector("[data-answered]");
const saveUrl = form ? form.querySelector("[data-save]").getAttribute("formaction") : null;

let dirty = false;
let submitting = false;
let saving = false;

function token() {
  const meta = document.querySelector('meta[name="csrf-token"]');
  return meta ? meta.content : "";
}

function setStatus(text) {
  if (status) status.textContent = text;
}

function countAnswered() {
  let n = 0;
  form.querySelectorAll(".assess-item").forEach((item) => {
    const chosen = item.querySelector("input[type=radio]:checked, input[type=checkbox]:checked");
    const text = item.querySelector("input.input");
    if (chosen || (text && text.value.trim() !== "")) n++;
  });
  if (answeredEl) answeredEl.textContent = String(n);
}

async function save() {
  if (!dirty || saving || submitting || !saveUrl) return;
  saving = true;
  dirty = false;
  setStatus("Saving…");
  try {
    const res = await fetch(saveUrl, {
      method: "POST",
      headers: { "X-CSRF-Token": token(), "X-Requested-With": "fetch" },
      body: new URLSearchParams(new FormData(form)),
      credentials: "same-origin",
    });
    if (res.status === 204) {
      setStatus("Saved " + new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }));
    } else if (res.status === 409) {
      setStatus("Time is up: submitting your saved answers…");
      finish();
    } else {
      dirty = true;
      setStatus("Not saved yet (will retry)");
    }
  } catch (e) {
    dirty = true;
    setStatus("Offline: answers will save when you reconnect");
  } finally {
    saving = false;
  }
}

function finish() {
  if (submitting) return;
  submitting = true;
  const auto = document.createElement("input");
  auto.type = "hidden";
  auto.name = "auto";
  auto.value = "1";
  form.appendChild(auto);
  form.submit();
}

if (form) {
  form.addEventListener("input", () => {
    dirty = true;
    setStatus("Unsaved changes");
    countAnswered();
  });
  form.addEventListener("submit", () => {
    submitting = true;
  });
  setInterval(save, 30000);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") save();
  });
  window.addEventListener("beforeunload", (event) => {
    if (dirty && !submitting) {
      event.preventDefault();
      event.returnValue = "";
    }
  });
}

if (timer && clock && form) {
  // Count down from the server's remaining seconds (immune to a wrong device clock).
  const end = Date.now() + Number(timer.dataset.secondsLeft) * 1000;
  let warned = false;
  const tick = () => {
    const left = Math.max(0, Math.round((end - Date.now()) / 1000));
    const m = Math.floor(left / 60);
    const s = String(left % 60).padStart(2, "0");
    clock.textContent = m + ":" + s + " left";
    timer.classList.toggle("is-low", left <= 120);
    if (left <= 120 && left > 0 && !warned) {
      warned = true;
      setStatus("Two minutes left");
    }
    if (left === 0) {
      clearInterval(handle);
      setStatus("Time is up: submitting your answers…");
      finish();
    }
  };
  const handle = setInterval(tick, 1000);
  tick();
}
