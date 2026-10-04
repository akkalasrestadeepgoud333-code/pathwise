<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="g" value="${intro.card}"/>
<pw:shell title="${g.title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/practice'/>">Practice</a><pw:icon name="chevron-right"/>
    <a href="<c:url value='/app/games'/>">Games</a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${g.title}"/></span>
  </nav>
  <div class="game-intro">
    <header class="page-header">
      <div>
        <p class="card-eyebrow"><c:out value="${g.templateTitle}"/><c:if test="${not empty g.estMinutes}"> · about <c:out value="${g.estMinutes}"/> minutes</c:if></p>
        <h1><c:out value="${g.title}"/></h1>
        <p><c:out value="${g.description}"/></p>
      </div>
    </header>
    <section class="card" aria-labelledby="trains-title">
      <h2 id="trains-title" class="card-title">What it trains</h2>
      <p><c:out value="${intro.teaches}"/></p>
      <c:if test="${not empty intro.skills}">
        <ul class="chip-list">
          <c:forEach var="s" items="${intro.skills}"><li class="chip"><c:out value="${s}"/></li></c:forEach>
        </ul>
      </c:if>
      <p class="legend-note">Pathwise checks every finished game by replaying your moves, then counts it as light practice (less than a practice question, and never as a grade).</p>
    </section>
    <pw:form action="/app/games/${g.key}/start" cssClass="game-start">
      <label class="check-row">
        <input type="checkbox" name="relaxed" value="1">
        <span><strong>Relaxed mode</strong>: hide the clock and score while you play.</span>
      </label>
      <div class="practice-actions">
        <button type="submit" class="btn btn-primary btn-lg"><pw:icon name="play"/>Play</button>
        <c:if test="${not empty g.lastAttemptId}">
          <a class="btn btn-ghost" href="<c:url value='/app/games/result/${g.lastAttemptId}'/>">Last result</a>
        </c:if>
      </div>
    </pw:form>
    <noscript><pw:alert kind="info" message="Games need JavaScript. Practice questions work without it."/></noscript>
  </div>
</pw:shell>
