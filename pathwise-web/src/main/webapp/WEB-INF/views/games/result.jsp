<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="r" value="${result}"/>
<pw:shell title="${r.title}: result">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/games'/>">Games</a><pw:icon name="chevron-right"/>
    <a href="<c:url value='/app/games/${r.key}'/>"><c:out value="${r.title}"/></a><pw:icon name="chevron-right"/>
    <span aria-current="page">Result</span>
  </nav>

  <c:choose>
    <c:when test="${r.status == 'rejected'}">
      <section class="card" aria-labelledby="rej-title">
        <h1 id="rej-title" class="card-title">We couldn't verify this game</h1>
        <p><c:out value="${r.rejectReason}"/> So it doesn't count toward your skill levels. That's all: nothing else changes.</p>
        <div class="practice-actions">
          <pw:form action="/app/games/${r.key}/start"><button type="submit" class="btn btn-primary"><pw:icon name="play"/>Play again</button></pw:form>
        </div>
      </section>
    </c:when>
    <c:otherwise>
      <section class="hero-card game-score" aria-labelledby="score-title">
        <p class="card-eyebrow">Verified · <c:out value="${r.durationLabel}"/><c:if test="${r.relaxed}"> · relaxed mode</c:if></p>
        <h1 id="score-title"><c:out value="${r.score}"/> <span class="game-score-max">/ <c:out value="${r.maxScore}"/></span></h1>
        <p><c:out value="${r.accuracyPercent}"/>% right first time. This counts as light practice, never as a grade.</p>
        <pw:trail cssClass="hero-trail" onInk="true"/>
      </section>

      <div class="grid grid-2 section-gap">
        <section class="card" aria-labelledby="learned-title">
          <h2 id="learned-title" class="card-title">What this showed</h2>
          <ul class="list">
            <c:forEach var="s" items="${r.steps}">
              <li class="list-item">
                <span class="list-icon"><pw:icon name="${s.correct ? 'circle-check' : 'target'}"/></span>
                <div class="grow"><div class="title"><c:out value="${s.note}"/></div><div class="meta"><c:out value="${s.skillTitle}"/></div></div>
              </li>
            </c:forEach>
          </ul>
          <c:if test="${r.capped > 0}">
            <p class="legend-note"><c:out value="${r.capped}"/> of these didn't change your levels: games count at most 12 times per skill per day, so practice questions matter more than replaying.</p>
          </c:if>
        </section>
        <section class="card" aria-labelledby="levels-title">
          <h2 id="levels-title" class="card-title">Your levels now</h2>
          <ul class="list">
            <c:forEach var="k" items="${r.skills}">
              <li class="list-item">
                <div class="grow"><div class="title"><c:out value="${k.title}"/></div><div class="meta"><c:out value="${k.topicTitle}"/> · <c:out value="${k.level}"/></div></div>
                <pw:form action="/app/practice/start">
                  <input type="hidden" name="mode" value="practice"><input type="hidden" name="skill" value="<c:out value='${k.skillId}'/>">
                  <button type="submit" class="btn btn-sm">Practise this</button>
                </pw:form>
              </li>
            </c:forEach>
          </ul>
        </section>
      </div>

      <h2 class="section-gap">Look back</h2>
      <div class="game-review">
        <c:forEach var="v" items="${r.review}">
          <article class="card">
            <h3 class="card-title"><c:out value="${v.heading}"/></h3>
            <c:if test="${not empty v.code}">
              <ol class="code-lines" aria-label="Code">
                <c:forEach var="line" items="${v.code}" varStatus="st">
                  <li class="<c:if test='${st.count == v.highlightLine}'>is-bug</c:if>"><code><c:out value="${line}"/></code></li>
                </c:forEach>
              </ol>
            </c:if>
            <p class="muted"><c:out value="${v.detail}"/></p>
          </article>
        </c:forEach>
      </div>

      <div class="practice-actions section-gap">
        <pw:form action="/app/games/${r.key}/start"><button type="submit" class="btn btn-primary"><pw:icon name="play"/>Play again</button></pw:form>
        <a class="btn" href="<c:url value='/app/games'/>">All games</a>
        <a class="btn btn-ghost" href="<c:url value='/app/progress'/>">See my progress</a>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
