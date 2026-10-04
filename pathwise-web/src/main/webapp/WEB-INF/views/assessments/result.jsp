<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="r" value="${result}"/>
<pw:shell title="${r.title}: result">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/assessments'/>">Assessments</a><pw:icon name="chevron-right"/>
    <a href="<c:url value='/app/assessments/${r.key}'/>"><c:out value="${r.title}"/></a><pw:icon name="chevron-right"/>
    <span aria-current="page">Attempt <c:out value="${r.attemptNo}"/></span>
  </nav>

  <section class="hero-card assess-score" aria-labelledby="score-title">
    <p class="card-eyebrow">Attempt <c:out value="${r.attemptNo}"/> · submitted <c:out value="${r.submittedLabel}"/></p>
    <h1 id="score-title"><c:out value="${r.percent}"/>%</h1>
    <p>You scored <strong><c:out value="${r.scoreLabel}"/></strong>.
      <c:if test="${r.autoSubmitted}">Time ran out, so your saved answers were submitted for you.</c:if>
      <c:if test="${r.late}">This was submitted after the due date.</c:if></p>
    <pw:trail cssClass="hero-trail" onInk="true"/>
  </section>

  <section class="card section-gap" aria-labelledby="skills-title">
    <h2 id="skills-title" class="card-title">By skill</h2>
    <ul class="list">
      <c:forEach var="k" items="${r.skills}">
        <li class="list-item">
          <div class="grow"><div class="title"><c:out value="${k.skillTitle}"/></div><div class="meta"><c:out value="${k.topicTitle}"/></div></div>
          <span class="mono"><c:out value="${k.correct}"/>/<c:out value="${k.total}"/></span>
          <span class="level-move">now <strong><c:out value="${k.levelNow}"/></strong></span>
        </li>
      </c:forEach>
    </ul>
    <p class="legend-note">These answers are official evidence: they carry full weight in your skill levels.</p>
  </section>

  <c:if test="${not empty next}">
    <h2 class="section-gap">Suggested next</h2>
    <pw:rec rec="${next}" from="next"/>
  </c:if>

  <h2 class="section-gap">Your answers</h2>
  <c:if test="${not r.feedbackVisible}">
    <pw:alert kind="info" message="The correct answers aren't released yet. You can see which questions earned points."/>
  </c:if>
  <div class="assess-review">
    <c:forEach var="it" items="${r.items}">
      <article class="card question-card" aria-labelledby="rq<c:out value='${it.position}'/>">
        <p class="section-kind" id="rq<c:out value='${it.position}'/>">Question <c:out value="${it.position}"/> ·
<c:out value="${it.pointsLabel}"/></p>
        <div class="q-stem prose"><pw:html value="${it.stem}"/></div>
        <c:if test="${not empty it.feedback}"><pw:feedback f="${it.feedback}"/></c:if>
      </article>
    </c:forEach>
  </div>

  <div class="practice-actions section-gap">
    <a class="btn" href="<c:url value='/app/assessments'/>">All assessments</a>
    <a class="btn btn-ghost" href="<c:url value='/app/progress'/>">See my progress</a>
  </div>
</pw:shell>
