<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Settings">
  <div class="page-header">
    <div>
      <h1>Settings</h1>
      <p>Your account, how Pathwise looks, and where you're signed in.</p>
    </div>
  </div>

  <div class="grid">
    <section class="card" aria-labelledby="profile-title">
      <p class="card-eyebrow">Profile</p>
      <h2 id="profile-title" class="card-title">Your account</h2>
      <ul class="list">
        <li class="list-item">
          <span class="avatar" aria-hidden="true"><c:out value="${currentUser.initials}"/></span>
          <div class="grow">
            <div class="title"><c:out value="${currentUser.displayName}"/></div>
            <div class="meta"><c:out value="${email}"/></div>
          </div>
          <span class="chip"><c:out value="${currentUser.admin ? 'Administrator' : (currentUser.teacher ? 'Teacher' : 'Student')}"/></span>
        </li>
      </ul>
      <pw:form action="/logout" cssClass="section-gap-sm">
        <button type="submit" class="btn btn-sm"><pw:icon name="log-out" size="sm"/>Sign out of this device</button>
      </pw:form>
    </section>

    <section class="card" aria-labelledby="appearance-title">
      <p class="card-eyebrow">Appearance</p>
      <h2 id="appearance-title" class="card-title">Theme</h2>
      <p class="muted">"System" follows your device's light or dark setting.</p>
      <pw:form action="/app/settings/theme" dataAttr="theme-form">
        <div class="segmented" role="radiogroup" aria-label="Theme">
          <label><input type="radio" name="theme" value="system"<c:if test="${currentUser.theme == 'system'}"> checked</c:if>><pw:icon name="monitor" size="sm"/>System</label>
          <label><input type="radio" name="theme" value="light"<c:if test="${currentUser.theme == 'light'}"> checked</c:if>><pw:icon name="sun" size="sm"/>Light</label>
          <label><input type="radio" name="theme" value="dark"<c:if test="${currentUser.theme == 'dark'}"> checked</c:if>><pw:icon name="moon" size="sm"/>Dark</label>
        </div>
        <span class="subtle" data-status aria-live="polite"></span>
        <div><button type="submit" class="btn btn-sm section-gap" data-js-hide>Save theme</button></div>
      </pw:form>
    </section>

    <section class="card" id="sessions" aria-labelledby="sessions-title">
      <div class="card-header">
        <div>
          <p class="card-eyebrow">Security</p>
          <h2 id="sessions-title" class="card-title">Where you're signed in</h2>
        </div>
        <c:if test="${fn:length(sessions) > 1}">
          <pw:form action="/app/settings/sessions/revoke-others">
            <button type="submit" class="btn btn-danger btn-sm"><pw:icon name="log-out" size="sm"/>Sign out everywhere else</button>
          </pw:form>
        </c:if>
      </div>
      <ul class="list">
        <c:forEach var="s" items="${sessions}">
          <li class="list-item">
            <span class="list-icon"><pw:icon name="${s.client == 'android' ? 'smartphone' : 'laptop'}"/></span>
            <div class="grow">
              <div class="title"><c:out value="${s.userAgent}"/><c:if test="${s.current}"> <span class="chip chip-success">This device</span></c:if></div>
              <div class="meta">Signed in <c:out value="${s.createdLabel}"/> · active <c:out value="${s.lastUsedLabel}"/></div>
            </div>
            <c:if test="${not s.current}">
              <pw:form action="/app/settings/sessions/${s.id}/revoke">
                <button type="submit" class="btn btn-sm">Sign out</button>
              </pw:form>
            </c:if>
          </li>
        </c:forEach>
      </ul>
    </section>
  </div>
</pw:shell>
