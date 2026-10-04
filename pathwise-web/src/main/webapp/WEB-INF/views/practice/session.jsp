<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="s" value="${practiceSession}"/>
<c:set var="step" value="${s.step}"/>
<pw:shell title="${s.title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/practice'/>">Practice</a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${s.title}"/></span>
  </nav>

  <c:choose>
    <c:when test="${not empty step}">
      <div class="practice">
        <header class="practice-head">
          <h1><c:out value="${s.title}"/></h1>
          <div class="practice-dots" aria-label="Question <c:out value='${step.position}'/> of <c:out value='${step.total}'/>">
            <c:forEach begin="1" end="${step.total}" var="i"><span class="<c:choose><c:when test='${i < step.position}'>done</c:when><c:when test='${i == step.position}'>now</c:when></c:choose>"></span></c:forEach>
          </div>
        </header>
        <article class="card question-card" aria-labelledby="q-title">
          <p class="section-kind" id="q-title">Question <c:out value="${step.position}"/> of <c:out value="${step.total}"/>
            <span class="difficulty" aria-label="Difficulty <c:out value='${step.question.difficulty}'/> of 5"><c:forEach begin="1" end="5" var="i"><i class="<c:if test='${i <= step.question.difficulty}'>on</c:if>"></i></c:forEach></span></p>
          <pw:form action="/app/practice/${s.attemptId}/${step.position}/answer" id="answer-form">
            <pw:question q="${step.question}" optionName="option" textName="text"/>
            <c:if test="${not empty step.hints}">
              <div class="hints" aria-live="polite">
                <c:forEach var="h" items="${step.hints}" varStatus="st">
                  <p class="hint"><strong>Hint <c:out value="${st.count}"/>:</strong> <c:out value="${h}"/></p>
                </c:forEach>
              </div>
            </c:if>
            <div class="practice-actions">
              <button type="submit" class="btn btn-primary">Check answer <pw:icon name="arrow-right" size="sm"/></button>
            </div>
          </pw:form>
          <pw:form action="/app/tutor/start" cssClass="hint-form">
            <input type="hidden" name="mode" value="hint"><input type="hidden" name="attempt" value="<c:out value='${s.attemptId}'/>"><input type="hidden" name="position" value="<c:out value='${step.position}'/>">
            <button type="submit" class="btn btn-ghost btn-sm"><pw:icon name="sparkles" size="sm"/>Ask the tutor for a hint</button>
          </pw:form>
          <c:if test="${step.moreHints}">
            <pw:form action="/app/practice/${s.attemptId}/${step.position}/hint" cssClass="hint-form">
              <button type="submit" class="btn btn-ghost btn-sm"><pw:icon name="circle-help" size="sm"/><c:out value="${empty step.hints ? 'Need a hint?' : 'Another hint'}"/></button>
              <span class="subtle">Hints are fine to use. Answers given with hints count a little less.</span>
            </pw:form>
          </c:if>
        </article>
      </div>
    </c:when>
    <c:otherwise>
      <c:set var="sum" value="${s.summary}"/>
      <div class="practice">
        <header class="practice-head">
          <h1><c:out value="${s.title}"/>: done</h1>
          <p class="muted">You got <strong><c:out value="${sum.correct}"/> of <c:out value="${sum.total}"/></strong> right.
            This was practice: it shapes your suggestions and is never a grade.</p>
        </header>
        <section class="card" aria-labelledby="changes-title">
          <h2 id="changes-title" class="card-title">How your levels moved</h2>
          <ul class="list">
            <c:forEach var="k" items="${sum.skills}">
              <li class="list-item">
                <span class="list-icon"><pw:icon name="${k.improved ? 'sparkles' : (k.declined ? 'activity' : 'target')}"/></span>
                <div class="grow"><div class="title"><c:out value="${k.skillTitle}"/></div><div class="meta"><c:out value="${k.topicTitle}"/></div></div>
                <span class="level-move"><span class="subtle"><c:out value="${k.before}"/></span> → <strong><c:out value="${k.after}"/></strong></span>
              </li>
            </c:forEach>
          </ul>
          <p class="legend-note">Levels come from all your answers, weighted toward recent and harder ones. One set rarely changes much, and that's by design.</p>
        </section>
        <c:if test="${not empty next}">
          <h2 class="section-gap">Suggested next</h2>
          <pw:rec rec="${next}" from="next"/>
        </c:if>
        <div class="practice-actions section-gap">
          <a class="btn" href="<c:url value='/app/practice'/>">Back to practice</a>
          <a class="btn btn-ghost" href="<c:url value='/app/progress'/>">See my progress</a>
        </div>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
