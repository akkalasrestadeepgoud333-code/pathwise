<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Rooms">
  <div class="page-header">
    <div>
      <h1>Rooms</h1>
      <p>Classes you belong to. Your teacher sets the curriculum; your own next steps adapt within it. Other students never see your results.</p>
    </div>
  </div>
  <div class="grid grid-main-aside">
    <section aria-labelledby="my-title">
      <h2 id="my-title" class="visually-hidden">My rooms</h2>
      <c:choose>
        <c:when test="${empty rooms.learning and empty rooms.pending}">
          <pw:empty icon="school" title="You're not in a room yet" message="Ask your teacher for the room's join code or an invite link."/>
        </c:when>
        <c:otherwise>
          <div class="grid">
            <c:forEach var="r" items="${rooms.learning}">
              <a class="card card-interactive room-card" href="<c:url value='/app/rooms/${r.id}'/>">
                <span class="game-card-icon" aria-hidden="true"><pw:icon name="school"/></span>
                <div class="grow"><h3 class="card-title"><c:out value="${r.name}"/></h3>
                  <p class="meta"><c:out value="${r.students}"/> student<c:if test="${r.students != 1}">s</c:if><c:if test="${r.archived}"> · Archived</c:if></p></div>
                <pw:icon name="arrow-right"/>
              </a>
            </c:forEach>
            <c:forEach var="r" items="${rooms.pending}">
              <div class="card room-card">
                <span class="game-card-icon" aria-hidden="true"><pw:icon name="timer"/></span>
                <div class="grow"><h3 class="card-title"><c:out value="${r.name}"/></h3><p class="meta">Waiting for the teacher to approve you</p></div>
              </div>
            </c:forEach>
          </div>
        </c:otherwise>
      </c:choose>
      <c:if test="${not empty rooms.teaching}">
        <p class="section-gap muted">You also teach <c:out value="${rooms.teaching.size()}"/> room<c:if test="${rooms.teaching.size() != 1}">s</c:if>. <a href="<c:url value='/teach'/>">Go to Teaching</a></p>
      </c:if>
    </section>
    <section class="card" aria-labelledby="join-title">
      <h2 id="join-title" class="card-title">Join a room</h2>
      <pw:form action="/app/rooms/join" cssClass="stack-form">
        <div class="field">
          <label class="field-label" for="code">Join code</label>
          <input class="input mono code-input" id="code" name="code" required maxlength="12" autocomplete="off" autocapitalize="characters" placeholder="K7M3-QX9P">
        </div>
        <button type="submit" class="btn btn-primary">Join</button>
      </pw:form>
    </section>
  </div>
</pw:shell>
