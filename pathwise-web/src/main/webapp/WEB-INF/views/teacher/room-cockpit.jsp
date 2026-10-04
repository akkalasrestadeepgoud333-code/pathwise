<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="k" value="${cockpit}"/>
<c:set var="h" value="${k.header}"/>
<pw:shell title="${h.name}">
  <pw:roomhead h="${h}" section="today"/>

  <c:if test="${not k.hasCurriculum and not h.archived}">
    <section class="hero-card" aria-labelledby="start-title">
      <p class="card-eyebrow">Next step</p>
      <h2 id="start-title">Build the curriculum</h2>
      <p>Add units, then drop in modules, games and assessments. Students see them in order, and each gets their own path through them.</p>
      <a class="btn btn-primary btn-lg" href="<c:url value='/teach/rooms/${h.id}/curriculum'/>">Open the curriculum <pw:icon name="arrow-right"/></a>
      <pw:trail cssClass="hero-trail" onInk="true"/>
    </section>
  </c:if>

  <c:if test="${k.toGrade > 0}">
    <a class="card card-interactive room-card to-grade" href="<c:url value='/teach/rooms/${h.id}/assignments'/>">
      <span class="game-card-icon" aria-hidden="true"><pw:icon name="file-text"/></span>
      <div class="grow"><h2 class="card-title"><c:out value="${k.toGrade}"/> submission<c:if test="${k.toGrade != 1}">s</c:if> to grade</h2>
        <p class="meta">Handed-in assignments waiting for you</p></div>
      <pw:icon name="arrow-right"/>
    </a>
  </c:if>

  <div class="grid grid-2 section-gap">
    <section class="card" aria-labelledby="pending-title">
      <h2 id="pending-title" class="card-title">Waiting to join</h2>
      <c:choose>
        <c:when test="${empty k.pending}"><p class="muted">No requests right now.</p></c:when>
        <c:otherwise>
          <ul class="list">
            <c:forEach var="m" items="${k.pending}">
              <li class="list-item">
                <div class="grow"><div class="title"><c:out value="${m.name}"/></div><div class="meta"><c:out value="${m.email}"/> · asked <c:out value="${m.joinedLabel}"/></div></div>
                <c:if test="${h.canManageStudents}">
                  <pw:form action="/teach/rooms/${h.id}" cssClass="inline-form">
                    <input type="hidden" name="userId" value="<c:out value='${m.userId}'/>">
                    <button type="submit" name="action" value="approve" class="btn btn-sm btn-primary">Approve</button>
                    <button type="submit" name="action" value="reject" class="btn btn-sm btn-ghost">Decline</button>
                  </pw:form>
                </c:if>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>

    <section class="card" aria-labelledby="attention-title">
      <h2 id="attention-title" class="card-title">Students who may need you</h2>
      <c:choose>
        <c:when test="${not h.canAnalytics}"><p class="muted">The owner hasn't given you access to individual students' data.</p></c:when>
        <c:when test="${empty k.attention}"><p class="muted">No one stands out right now. This list flags missed deadlines, inactivity before a deadline, and two or more room skills needing practice.</p></c:when>
        <c:otherwise>
          <ul class="list">
            <c:forEach var="a" items="${k.attention}" end="7">
              <li class="list-item">
                <span class="list-icon"><pw:icon name="triangle-alert"/></span>
                <div class="grow"><div class="title"><c:out value="${a.name}"/></div>
                  <div class="meta"><c:forEach var="r" items="${a.reasons}" varStatus="st"><c:out value="${r}"/><c:if test="${not st.last}"> · </c:if></c:forEach></div></div>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>

    <section class="card" aria-labelledby="deadlines-title">
      <h2 id="deadlines-title" class="card-title">Deadlines</h2>
      <c:choose>
        <c:when test="${empty k.deadlines}"><p class="muted">Nothing due in the next two weeks.</p></c:when>
        <c:otherwise>
          <ul class="list">
            <c:forEach var="d" items="${k.deadlines}">
              <li class="list-item">
                <span class="list-icon"><pw:icon name="timer"/></span>
                <div class="grow"><div class="title"><c:out value="${d.title}"/></div><div class="meta"><c:out value="${d.overdue ? 'Was due ' : 'Due '}"/><c:out value="${d.dueLabel}"/></div></div>
                <span class="mono"><c:out value="${d.submitted}"/>/<c:out value="${d.students}"/> submitted</span>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>

    <section class="card" aria-labelledby="ann-title">
      <h2 id="ann-title" class="card-title">Announcements</h2>
      <c:if test="${h.canAnnounce}">
        <pw:form action="/teach/rooms/${h.id}" cssClass="stack-form">
          <input type="hidden" name="action" value="announce">
          <label class="visually-hidden" for="ann-body">Announcement</label>
          <textarea class="input" id="ann-body" name="body" rows="3" maxlength="4000" required placeholder="Quiz on Friday covers linked lists…"></textarea>
          <div class="practice-actions">
            <label class="check-row"><input type="checkbox" name="pinned" value="1"><span>Pin to the top</span></label>
            <button type="submit" class="btn btn-primary btn-sm">Post</button>
          </div>
        </pw:form>
      </c:if>
      <ul class="list section-gap-sm">
        <c:forEach var="a" items="${k.announcements}">
          <li class="list-item announcement">
            <div class="grow">
              <div class="meta"><c:if test="${a.pinned}"><span class="chip">Pinned</span> </c:if><c:out value="${a.author}"/> · <c:out value="${a.when}"/></div>
              <p class="announcement-body"><c:out value="${a.body}"/></p>
            </div>
            <c:if test="${h.canAnnounce}">
              <pw:form action="/teach/rooms/${h.id}" cssClass="inline-form">
                <input type="hidden" name="id" value="<c:out value='${a.id}'/>">
                <button type="submit" name="action" value="delete-announcement" class="btn btn-sm btn-ghost">Delete</button>
              </pw:form>
            </c:if>
          </li>
        </c:forEach>
      </ul>
    </section>
  </div>
</pw:shell>
