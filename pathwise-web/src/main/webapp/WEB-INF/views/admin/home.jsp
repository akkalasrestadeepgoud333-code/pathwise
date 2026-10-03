<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Admin">
  <div class="page-header">
    <div>
      <h1>Platform overview</h1>
      <p>Live counts from the database. Personal data stays on each user's own pages.</p>
    </div>
  </div>

  <div class="grid grid-4">
    <div class="card stat"><span class="stat-label">People</span><span class="stat-value"><c:out value="${overview.users}"/></span><span class="subtle"><c:out value="${overview.signupsLast7Days}"/> joined this week</span></div>
    <div class="card stat"><span class="stat-label">Students</span><span class="stat-value"><c:out value="${overview.students}"/></span></div>
    <div class="card stat"><span class="stat-label">Teachers</span><span class="stat-value"><c:out value="${overview.teachers}"/></span></div>
    <div class="card stat"><span class="stat-label">Signed-in sessions</span><span class="stat-value"><c:out value="${overview.activeSessions}"/></span><span class="subtle"><c:out value="${overview.admins}"/> administrator<c:if test="${overview.admins != 1}">s</c:if></span></div>
  </div>

  <section class="card section-gap" aria-labelledby="audit-title">
    <div class="card-header">
      <h2 id="audit-title" class="card-title">Recent activity log</h2>
      <span class="chip">Append-only</span>
    </div>
    <c:choose>
      <c:when test="${empty audit}">
        <p class="muted">No events yet.</p>
      </c:when>
      <c:otherwise>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th scope="col">When (IST)</th><th scope="col">Who</th><th scope="col">Action</th><th scope="col">Entity</th></tr></thead>
            <tbody>
              <c:forEach var="e" items="${audit}">
                <tr>
                  <td class="tabular"><c:out value="${e.atLabel}"/></td>
                  <td><c:out value="${empty e.actorName ? 'System' : e.actorName}"/></td>
                  <td class="mono"><c:out value="${e.action}"/></td>
                  <td><c:out value="${e.entityKind}"/></td>
                </tr>
              </c:forEach>
            </tbody>
          </table>
        </div>
      </c:otherwise>
    </c:choose>
  </section>
</pw:shell>
