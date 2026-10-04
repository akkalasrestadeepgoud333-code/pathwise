<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Confirm it's you">
  <div class="page-header"><div><h1>Confirm it's you</h1><p>Role changes and suspensions need your password. It stays confirmed for 15 minutes on this device.</p></div></div>
  <section class="card narrow">
    <pw:form action="/admin/reauth">
      <input type="hidden" name="next" value="<c:out value='${next}'/>">
      <div class="field">
        <label class="field-label" for="password">Your password</label>
        <input class="input" id="password" name="password" type="password" autocomplete="current-password" required>
      </div>
      <button type="submit" class="btn btn-primary">Confirm</button>
    </pw:form>
  </section>
</pw:shell>
