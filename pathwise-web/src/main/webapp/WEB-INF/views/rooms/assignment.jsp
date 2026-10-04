<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="${a.title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/rooms'/>">Rooms</a><pw:icon name="chevron-right"/>
    <a href="<c:url value='/app/rooms/${a.roomId}'/>"><c:out value="${a.roomName}"/></a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${a.title}"/></span>
  </nav>
  <div class="assess-rules">
    <header class="page-header">
      <div>
        <p class="card-eyebrow">Assignment · <c:out value="${a.points}"/> points</p>
        <h1><c:out value="${a.title}"/></h1>
        <p><c:out value="${a.dueLabel}"/><c:if test="${a.overdue}"> (passed)<c:if test="${a.allowLate}"> · late work is still accepted</c:if></c:if></p>
      </div>
      <span class="state-chip item-chip-<c:out value='${a.status == "graded" or a.status == "submitted" ? "done" : (a.status == "none" ? "todo" : "started")}'/>"><c:out value="${a.statusLabel}"/></span>
    </header>

    <c:if test="${not empty a.instructions.html}">
      <section class="card prose" aria-label="Instructions"><pw:html value="${a.instructions}"/></section>
    </c:if>

    <c:if test="${a.status == 'graded'}">
      <section class="hero-card section-gap" aria-labelledby="grade-title">
        <p class="card-eyebrow">Graded</p>
        <h2 id="grade-title"><c:out value="${a.grade}"/> / <c:out value="${a.points}"/></h2>
        <c:if test="${not empty a.feedback}"><p><c:out value="${a.feedback}"/></p></c:if>
        <c:if test="${not empty a.skills}"><p class="muted">This grade now counts toward: <c:forEach var="k" items="${a.skills}" varStatus="st"><c:out value="${k}"/><c:if test="${not st.last}">, </c:if></c:forEach>.</p></c:if>
      </section>
    </c:if>
    <c:if test="${a.status == 'returned' and not empty a.feedback}">
      <pw:alert kind="info" message="Your teacher sent this back: ${a.feedback}"/>
    </c:if>

    <section class="card section-gap" aria-labelledby="work-title">
      <h2 id="work-title" class="card-title">Your work</h2>
      <c:choose>
        <c:when test="${a.canEdit}">
          <pw:form action="/app/rooms/${a.roomId}/assignments/${a.id}" cssClass="stack-form">
            <label class="visually-hidden" for="body">Your answer</label>
            <textarea class="input" id="body" name="body" rows="12" maxlength="20000"><c:out value="${a.body}"/></textarea>
            <div class="practice-actions">
              <button type="submit" name="action" value="hand-in" class="btn btn-primary">Hand in</button>
              <button type="submit" name="action" value="save" class="btn btn-ghost">Save draft</button>
            </div>
            <p class="legend-note">Only you and your teachers can see this.</p>
          </pw:form>
        </c:when>
        <c:otherwise>
          <c:choose>
            <c:when test="${empty a.body}"><p class="muted">Nothing was handed in.</p></c:when>
            <c:otherwise><div class="submission-body"><c:out value="${a.body}"/></div></c:otherwise>
          </c:choose>
          <c:if test="${a.status == 'submitted'}"><p class="legend-note">Handed in<c:if test="${a.late}"> late</c:if>. You can edit it again only if your teacher sends it back.</p></c:if>
        </c:otherwise>
      </c:choose>
    </section>
  </div>
</pw:shell>
