<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="${s.title}">
  <p><a href="<c:url value='/app/live'/>">← Live sessions</a></p>
  <div class="page-header">
    <div>
      <p class="card-eyebrow">Hosted by <c:out value="${s.hostName}"/></p>
      <h1><c:out value="${s.title}"/></h1>
      <p>
        <span class="chip<c:if test="${s.status == 'live'}"> chip-success</c:if>"><c:out value="${s.statusLabel}"/></span>
        <c:if test="${s.locked}"><span class="chip">Locked to new people</span></c:if>
        <c:if test="${not empty s.myAlias}"> You are <strong><c:out value="${s.myAlias}"/></strong>.</c:if>
      </p>
    </div>
  </div>

  <c:choose>
    <c:when test="${s.removed}">
      <div class="alert alert-error" role="alert"><pw:icon name="triangle-alert"/><div><p>The host removed you from this session. You can still ask in <a href="<c:url value='/app/doubts'/>">Doubts</a>.</p></div></div>
    </c:when>
    <c:otherwise>
      <c:if test="${s.host}">
        <section class="card section-gap-sm" aria-labelledby="controls-title">
          <h2 id="controls-title" class="card-title">Host controls</h2>
          <div class="form-row">
            <c:if test="${s.status == 'scheduled'}">
              <pw:form action="/app/live/${s.id}"><button type="submit" name="action" value="start" class="btn btn-primary btn-sm"><pw:icon name="play"/>Start</button></pw:form>
            </c:if>
            <c:if test="${s.status != 'ended'}">
              <pw:form action="/app/live/${s.id}"><button type="submit" name="action" value="<c:out value='${s.locked ? "unlock" : "lock"}'/>" class="btn btn-sm"><pw:icon name="lock"/><c:out value="${s.locked ? 'Let new people join' : 'Lock to new people'}"/></button></pw:form>
              <pw:form action="/app/live/${s.id}"><button type="submit" name="action" value="end" class="btn btn-sm">End session</button></pw:form>
            </c:if>
          </div>
          <c:if test="${not empty s.activeAliases}">
            <pw:form action="/app/live/${s.id}" cssClass="form-row section-gap-sm">
              <input type="hidden" name="action" value="remove">
              <label class="field-label" for="alias">Remove a participant</label>
              <select class="input" id="alias" name="alias">
                <c:forEach var="a" items="${s.activeAliases}"><option value="<c:out value='${a}'/>">Participant <c:out value="${a}"/></option></c:forEach>
              </select>
              <button type="submit" class="btn btn-sm btn-danger">Remove</button>
            </pw:form>
            <p class="legend-note">Removed people can't rejoin this session. Removals are logged.</p>
          </c:if>
        </section>
      </c:if>

      <c:if test="${s.canJoin}">
        <pw:form action="/app/live/${s.id}" cssClass="section-gap-sm">
          <button type="submit" name="action" value="join" class="btn btn-primary"><pw:icon name="users"/>Join anonymously</button>
        </pw:form>
      </c:if>

      <c:if test="${s.canTakePart}">
        <section class="card section-gap-sm" aria-labelledby="ask-title">
          <h2 id="ask-title" class="card-title">Ask the host</h2>
          <pw:form action="/app/live/${s.id}">
            <input type="hidden" name="action" value="ask">
            <textarea class="input" name="body" rows="3" required minlength="3" maxlength="500" aria-label="Your question" placeholder="What are you stuck on?"></textarea>
            <div class="form-row section-gap-sm">
              <button type="submit" class="btn btn-primary btn-sm">Add to the queue</button>
            </div>
          </pw:form>
          <div class="form-row section-gap-sm">
            <pw:form action="/app/live/${s.id}">
              <button type="submit" name="action" value="<c:out value='${s.handRaised ? "hand-down" : "hand-up"}'/>" class="btn btn-sm"><c:out value="${s.handRaised ? 'Lower my hand' : 'Raise my hand'}"/></button>
            </pw:form>
            <pw:form action="/app/live/${s.id}"><button type="submit" name="action" value="leave" class="btn btn-sm btn-ghost">Leave</button></pw:form>
          </div>
        </section>
      </c:if>

      <div id="live-panel" class="section-gap" aria-live="polite"
           <c:if test="${s.status != 'ended'}">hx-get="<c:url value='/app/live/${s.id}/queue'/>" hx-trigger="every 4s" hx-swap="innerHTML"</c:if>>
        <jsp:include page="queue.jsp"/>
      </div>
      <c:if test="${s.status != 'ended'}"><p class="legend-note">This list updates every few seconds. <a href="<c:url value='/app/live/${s.id}'/>">Refresh now</a></p></c:if>
    </c:otherwise>
  </c:choose>
</pw:shell>
