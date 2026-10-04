<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="i" value="${invite}"/>
<pw:shell title="Join a room">
  <div class="game-intro">
    <c:choose>
      <c:when test="${i.valid}">
        <section class="card" aria-labelledby="join-title">
          <p class="card-eyebrow">Invite</p>
          <h1 id="join-title" class="card-title">Join <c:out value="${i.roomName}"/></h1>
          <p>You've been invited as <strong><c:out value="${i.roleLabel}"/></strong>.
            <c:if test="${i.role == 'student'}">Your teachers will see your progress in this room's work. Other students won't.</c:if></p>
          <pw:form action="/join/${token}">
            <button type="submit" class="btn btn-primary btn-lg">Join room</button>
          </pw:form>
        </section>
      </c:when>
      <c:otherwise>
        <section class="card" aria-labelledby="bad-title">
          <h1 id="bad-title" class="card-title">This link can't be used</h1>
          <p><c:out value="${i.problem}"/></p>
          <a class="btn" href="<c:url value='/app/rooms'/>">Join with a code instead</a>
        </section>
      </c:otherwise>
    </c:choose>
  </div>
</pw:shell>
