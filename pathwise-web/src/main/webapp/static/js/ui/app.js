// Small progressive enhancements shared by every page. Everything here is optional:
// pages work without JavaScript, these only make them nicer.

function csrfToken() {
  const meta = document.querySelector('meta[name="csrf-token"]');
  return meta ? meta.content : "";
}

function effectiveTheme() {
  const explicit = document.documentElement.getAttribute("data-theme");
  if (explicit === "light" || explicit === "dark") return explicit;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme(choice) {
  const root = document.documentElement;
  if (choice === "light" || choice === "dark") root.setAttribute("data-theme", choice);
  else root.removeAttribute("data-theme");
  try {
    if (choice === "light" || choice === "dark") localStorage.setItem("pw-theme", choice);
    else localStorage.removeItem("pw-theme");
  } catch (e) { /* ignore */ }
  document.querySelectorAll("[data-theme-toggle]").forEach(updateToggleLabel);
}

function saveTheme(url, choice) {
  if (!url) return;
  const body = new URLSearchParams({ theme: choice });
  fetch(url, {
    method: "POST",
    headers: { "X-CSRF-Token": csrfToken(), "X-Requested-With": "fetch" },
    body,
    credentials: "same-origin",
  }).catch(() => { /* the device-level choice still applies */ });
}

function updateToggleLabel(button) {
  const next = effectiveTheme() === "dark" ? "light" : "dark";
  button.setAttribute("aria-label", "Switch to " + next + " theme");
  button.setAttribute("title", "Switch to " + next + " theme");
}

// Theme toggle buttons (top bar)
document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
  updateToggleLabel(button);
  button.addEventListener("click", () => {
    const next = effectiveTheme() === "dark" ? "light" : "dark";
    applyTheme(next);
    saveTheme(button.dataset.saveUrl, next);
    const radio = document.querySelector('input[name="theme"][value="' + next + '"]');
    if (radio) radio.checked = true;
  });
});

// Theme segmented control (settings page): apply instantly, save in the background.
document.querySelectorAll("[data-theme-form]").forEach((form) => {
  form.addEventListener("change", (event) => {
    if (event.target.name !== "theme") return;
    applyTheme(event.target.value);
    saveTheme(form.action, event.target.value);
    const status = form.querySelector("[data-status]");
    if (status) status.textContent = "Saved";
  });
  const submit = form.querySelector("[data-js-hide]");
  if (submit) submit.hidden = true;
});

// Show/hide password
document.querySelectorAll("[data-reveal]").forEach((button) => {
  const input = document.getElementById(button.dataset.reveal);
  if (!input) return;
  button.hidden = false;
  button.addEventListener("click", () => {
    const show = input.type === "password";
    input.type = show ? "text" : "password";
    button.setAttribute("aria-pressed", String(show));
    button.setAttribute("aria-label", show ? "Hide password" : "Show password");
    button.querySelector("use").setAttribute("href", button.dataset.iconBase + (show ? "#i-eye-off" : "#i-eye"));
  });
});

// Password strength hint. Advisory only: the server enforces the real rules.
document.querySelectorAll("[data-strength-for]").forEach((meter) => {
  const input = document.getElementById(meter.dataset.strengthFor);
  const label = document.getElementById(meter.dataset.strengthLabel);
  if (!input) return;
  meter.hidden = false;
  const words = ["", "Too weak", "Okay", "Good", "Strong"];
  input.addEventListener("input", () => {
    const v = input.value;
    let score = 0;
    if (v.length >= 10) score++;
    if (v.length >= 14) score++;
    if (/[a-z]/.test(v) && /[A-Z0-9]/.test(v)) score++;
    if (/[^A-Za-z0-9]/.test(v) || v.split(/\s+/).length >= 3) score++;
    if (v.length > 0 && v.length < 10) score = 1;
    if (v.length === 0) score = 0;
    meter.dataset.score = String(score);
    if (label) label.textContent = words[score];
  });
});

// htmx: send the CSRF token with every request it makes.
document.body.addEventListener("htmx:configRequest", (event) => {
  event.detail.headers["X-CSRF-Token"] = csrfToken();
});
