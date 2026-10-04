<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Live doubt sessions">
  <div class="page-header">
    <div>
      <h1>Live doubt sessions</h1>
      <p>Drop in, ask in a queue, upvote what you also want answered. Everyone, including the host, sees you only as "Participant N". Text only.</p>
    </div>
  </div>

  <c:if test="${home.canHost}">
    <section class="card section-gap-sm" aria-labelledby="host-title">
      <h2 id="host-title" class="card-title">Host a session</h2>
      <pw:form action="/app/live" cssClass="form-row">
        <input class="input grow" name="title" required minlength="3" maxlength="120" placeholder="e.g. Trees: your questions before Friday's test" aria-label="Session title">
        <button type="submit" class="btn btn-primary btn-sm">Create</button>
      </pw:form>
    </section>
  </c:if>

  <c:choose>
    <c:when test="${empty home.sessions}">
      <pw:empty icon="users" title="No sessions right now" message="Teachers and mentors open sessions here. Meanwhile, ask in Doubts and someone will answer."/>
    </c:when>
    <c:otherwise>
      <div class="grid section-gap">
        <c:forEach var="x" items="${home.sessions}">
          <a class="card card-link" href="<c:url value='/app/live/${x.id}'/>">
            <div class="card-header">
              <p class="card-eyebrow">Hosted by <c:out value="${x.hostName}"/></p>
              <span class="chip<c:if test="${x.status == 'live'}"> chip-success</c:if>"><c:out value="${x.statusLabel}"/></span>
            </div>
            <h2 class="card-title"><c:out value="${x.title}"/></h2>
            <p class="meta"><c:out value="${x.participants}"/> here<c:if test="${x.locked}"> · locked to new people</c:if></p>
          </a>
        </c:forEach>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
