// Runs in <head> before first paint so the page never flashes the wrong theme.
// Priority: the signed-in user's saved choice (rendered by the server as data-theme) > this device's choice > OS preference.
(function () {
  var root = document.documentElement;
  if (root.hasAttribute("data-theme")) return;
  try {
    var saved = localStorage.getItem("pw-theme");
    if (saved === "light" || saved === "dark") root.setAttribute("data-theme", saved);
  } catch (e) { /* storage blocked: fall back to the OS preference */ }
})();
