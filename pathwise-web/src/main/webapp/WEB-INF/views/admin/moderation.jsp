<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Moderation">
  <div class="page-header"><div><h1>Moderation</h1><p>Open reports, oldest first. Every action needs a reason and is kept in the moderation and audit logs.</p></div></div>

  <c:if test="${not empty revealed}">
    <div class="card invite-once" role="status"><p class="card-title">Identity revealed</p><p class="mono"><c:out value="${revealed}"/></p>
      <p class="legend-note">This lookup and your reason are recorded. Don't share it beyond what the case needs.</p></div>
  </c:if>

  <c:choose>
    <c:when test="${empty reports}"><pw:empty icon="shield" title="No open reports" message="Reports from students and teachers appear here."/></c:when>
    <c:otherwise>
      <c:forEach var="r" items="${reports}">
        <article class="card section-gap-sm report-card" aria-label="Report">
          <div class="card-header">
            <p class="card-eyebrow"><c:out value="${r.kind}"/> · <c:out value="${r.reason}"/> · <c:out value="${r.when}"/></p>
            <span class="chip"><c:out value="${r.targetStatus}"/></span>
          </div>
          <p class="report-excerpt"><c:out value="${r.excerpt}"/></p>
          <c:if test="${not empty r.details}"><p class="meta">Reporter's note: <c:out value="${r.details}"/></p></c:if>
          <p><a href="<c:url value='/app/doubts/${r.threadId}'/>">Open the doubt</a></p>
          <pw:form action="/admin/moderation" cssClass="form-row">
            <input type="hidden" name="action" value="moderate"><input type="hidden" name="reportId" value="<c:out value='${r.id}'/>">
            <input class="input grow" name="reason" required minlength="3" maxlength="500" placeholder="Reason (required)" aria-label="Reason">
            <button type="submit" name="decision" value="hide" class="btn btn-sm">Hide</button>
            <button type="submit" name="decision" value="remove" class="btn btn-sm">Remove</button>
            <button type="submit" name="decision" value="restore" class="btn btn-sm">Restore</button>
            <button type="submit" name="decision" value="dismiss" class="btn btn-sm btn-ghost">Dismiss</button>
          </pw:form>
        </article>
      </c:forEach>
    </c:otherwise>
  </c:choose>

  <section class="card section-gap" aria-labelledby="reveal-title">
    <h2 id="reveal-title" class="card-title">Reveal who is behind an alias</h2>
    <p class="muted">Only for serious cases (for example, someone at risk or repeated harassment). The reason is required and logged.</p>
    <pw:form action="/admin/moderation" cssClass="form-row">
      <input type="hidden" name="action" value="reveal">
      <input class="input" name="threadId" required placeholder="Doubt id" aria-label="Doubt id">
      <input class="input" name="alias" required inputmode="numeric" maxlength="4" placeholder="Alias number" aria-label="Alias number">
      <input class="input grow" name="reason" required minlength="10" maxlength="500" placeholder="Reason (required)" aria-label="Reason">
      <button type="submit" class="btn btn-sm">Reveal</button>
    </pw:form>
  </section>
</pw:shell>
