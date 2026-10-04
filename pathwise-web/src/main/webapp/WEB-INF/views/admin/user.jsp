<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="${person.displayName}">
  <p><a href="<c:url value='/admin/users'/>">← People</a></p>
  <div class="page-header">
    <div>
      <h1><c:out value="${person.displayName}"/></h1>
      <p class="mono"><c:out value="${person.email}"/></p>
    </div>
    <span class="chip<c:if test="${person.status == 'suspended'}"> chip-danger</c:if>"><c:out value="${person.status}"/></span>
  </div>

  <div class="grid grid-4">
    <div class="card stat"><span class="stat-label">Roles</span><span class="stat-value stat-small"><c:out value="${person.rolesLabel}"/></span></div>
    <div class="card stat"><span class="stat-label">Trust level</span><span class="stat-value"><c:out value="${person.trustLevel}"/></span></div>
    <div class="card stat"><span class="stat-label">Joined</span><span class="stat-value stat-small"><c:out value="${person.createdLabel}"/></span></div>
    <div class="card stat"><span class="stat-label">Last seen (IST)</span><span class="stat-value stat-small"><c:out value="${person.lastSeenLabel}"/></span></div>
  </div>

  <c:if test="${not reauthenticated}">
    <div class="alert alert-info section-gap-sm" role="note"><pw:icon name="lock"/>
      <div><p>Role changes and suspensions ask for your password first (it stays confirmed for 15 minutes).
        <a href="<c:url value='/admin/reauth'><c:param name='next' value='/admin/users/${person.id}'/></c:url>">Confirm now</a></p></div></div>
  </c:if>

  <section class="card section-gap" aria-labelledby="roles-title">
    <h2 id="roles-title" class="card-title">Roles</h2>
    <p class="muted">Everyone is a student. Teachers can create rooms; mentors can host live doubt sessions; administrators run Pathwise.</p>
    <div class="form-row">
      <c:forEach var="role" items="${['teacher', 'mentor', 'admin']}">
        <pw:form action="/admin/users/${person.id}">
          <input type="hidden" name="role" value="<c:out value='${role}'/>">
          <c:choose>
            <c:when test="${person[role]}">
              <button type="submit" name="action" value="revoke" class="btn btn-sm" <c:if test="${self and role == 'admin'}">disabled</c:if>>Remove <c:out value="${role}"/></button>
            </c:when>
            <c:otherwise>
              <button type="submit" name="action" value="grant" class="btn btn-sm">Make <c:out value="${role}"/></button>
            </c:otherwise>
          </c:choose>
        </pw:form>
      </c:forEach>
    </div>
  </section>

  <section class="card section-gap" aria-labelledby="community-title">
    <h2 id="community-title" class="card-title">Community</h2>
    <div class="form-row">
      <pw:form action="/admin/users/${person.id}">
        <c:choose>
          <c:when test="${person.verifiedEducator}"><button type="submit" name="action" value="unverify" class="btn btn-sm">Remove Verified Educator</button></c:when>
          <c:otherwise><button type="submit" name="action" value="verify" class="btn btn-sm">Mark as Verified Educator</button></c:otherwise>
        </c:choose>
      </pw:form>
      <pw:form action="/admin/users/${person.id}" cssClass="form-row">
        <input type="hidden" name="action" value="trust">
        <label class="field-label" for="level">Trust level</label>
        <select class="input" id="level" name="level">
          <c:forEach var="l" begin="0" end="4"><option value="<c:out value='${l}'/>" <c:if test="${l == person.trustLevel}">selected</c:if>><c:out value="${l}"/></option></c:forEach>
        </select>
        <button type="submit" class="btn btn-sm">Set</button>
      </pw:form>
    </div>
  </section>

  <c:if test="${not self}">
    <section class="card section-gap" aria-labelledby="status-title">
      <h2 id="status-title" class="card-title">Account status</h2>
      <c:choose>
        <c:when test="${person.status == 'suspended'}">
          <p class="muted">Suspended: this person can't sign in. Their work and progress are kept.</p>
          <pw:form action="/admin/users/${person.id}" cssClass="form-row">
            <input type="hidden" name="action" value="reactivate">
            <input class="input grow" name="reason" required minlength="5" maxlength="300" placeholder="Reason (required, logged)" aria-label="Reason">
            <button type="submit" class="btn btn-sm">Reactivate</button>
          </pw:form>
        </c:when>
        <c:otherwise>
          <p class="muted">Suspending signs this person out everywhere and stops them signing in. Nothing is deleted.</p>
          <pw:form action="/admin/users/${person.id}" cssClass="form-row">
            <input type="hidden" name="action" value="suspend">
            <input class="input grow" name="reason" required minlength="5" maxlength="300" placeholder="Reason (required, logged)" aria-label="Reason">
            <button type="submit" class="btn btn-sm btn-danger">Suspend</button>
          </pw:form>
        </c:otherwise>
      </c:choose>
    </section>
  </c:if>

  <section class="card section-gap" aria-labelledby="history-title">
    <h2 id="history-title" class="card-title">Admin history for this account</h2>
    <c:choose>
      <c:when test="${empty history}"><p class="muted">Nothing recorded recently.</p></c:when>
      <c:otherwise>
        <ul class="plain-list">
          <c:forEach var="h" items="${history}">
            <li><span class="tabular"><c:out value="${h.atLabel}"/></span> · <span class="mono"><c:out value="${h.action}"/></span>
              by <c:out value="${empty h.actorName ? 'System' : h.actorName}"/> <span class="muted mono"><c:out value="${h.details}"/></span></li>
          </c:forEach>
        </ul>
      </c:otherwise>
    </c:choose>
  </section>
</pw:shell>
