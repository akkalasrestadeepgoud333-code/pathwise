<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Settings">
  <div class="page-header"><div><h1>Platform settings</h1><p>Changes take effect immediately and are recorded in the audit log.</p></div></div>

  <section class="card" aria-labelledby="signup-title">
    <h2 id="signup-title" class="card-title">Sign-ups</h2>
    <pw:form action="/admin/settings">
      <label class="check-row"><input type="checkbox" name="registrationOpen" value="on" <c:if test="${registrationOpen}">checked</c:if>>
        <span>Anyone can create an account</span></label>
      <p class="legend-note">When this is off, the sign-up form and the app say sign-ups are closed. Administrators can still create admin accounts with <span class="mono">pathwise create-admin</span>.</p>
      <button type="submit" class="btn btn-primary btn-sm">Save</button>
    </pw:form>
  </section>

  <section class="card section-gap" aria-labelledby="ai-title">
    <h2 id="ai-title" class="card-title">AI tutor</h2>
    <p>Currently: <strong><c:out value="${ai}"/></strong></p>
    <p class="legend-note">Set in the server's configuration file (<span class="mono">pathwise.ai.provider</span>), because it depends on software installed on the server. Restart Pathwise after changing it.</p>
  </section>
</pw:shell>
