<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Games">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/practice'/>">Practice</a><pw:icon name="chevron-right"/>
    <span aria-current="page">Games</span>
  </nav>
  <div class="page-header">
    <div>
      <h1>Games</h1>
      <p>Short games that train one idea each. They count as light practice in your skill levels and never as a grade. There's no clock to beat: take your time.</p>
    </div>
  </div>
  <c:choose>
    <c:when test="${empty cards}">
      <pw:empty icon="gamepad-2" title="No games yet" message="Games arrive with subjects. Check back after a subject is published."/>
    </c:when>
    <c:otherwise>
      <div class="grid grid-2">
        <c:forEach var="g" items="${cards}">
          <a class="card card-interactive game-card" href="<c:url value='/app/games/${g.key}'/>">
            <span class="game-card-icon t-<c:out value='${g.template}'/>" aria-hidden="true"><pw:icon name="${g.template == 'bug_hunt' ? 'bug' : (g.template == 'match_pairs' ? 'link' : 'route')}"/></span>
            <div class="grow">
              <p class="card-eyebrow"><c:out value="${g.templateTitle}"/><c:if test="${not empty g.topicTitle}"> · <c:out value="${g.topicTitle}"/></c:if></p>
              <h2 class="card-title"><c:out value="${g.title}"/></h2>
              <p class="muted"><c:out value="${g.description}"/></p>
              <p class="game-meta">
                <c:if test="${not empty g.estMinutes}"><span>About <c:out value="${g.estMinutes}"/> min</span></c:if>
                <span><c:choose><c:when test="${g.plays == 0}">Not played yet</c:when><c:otherwise>Played <c:out value="${g.plays}"/>× · best <c:out value="${g.best}"/></c:otherwise></c:choose></span>
              </p>
            </div>
          </a>
        </c:forEach>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
