<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="r" value="${rules}"/>
<c:set var="a" value="${r.card}"/>
<pw:shell title="${a.title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/assessments'/>">Assessments</a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${a.title}"/></span>
  </nav>
  <div class="assess-rules">
    <header class="page-header">
      <div>
        <p class="card-eyebrow"><c:out value="${a.kindLabel}"/> · <c:out value="${a.subjectTitle}"/></p>
        <h1><c:out value="${a.title}"/></h1>
        <c:if test="${not empty r.instructions}"><p><c:out value="${r.instructions}"/></p></c:if>
      </div>
    </header>

    <section class="card" aria-labelledby="before-title">
      <h2 id="before-title" class="card-title">Before you start</h2>
      <ul class="list assess-rule-list">
        <li class="list-item"><span class="list-icon"><pw:icon name="clipboard-check"/></span>
          <div class="grow"><div class="title"><c:out value="${a.questions}"/> questions</div>
            <div class="meta">Unanswered questions score zero. You can move freely between questions until you submit.</div></div></li>
        <li class="list-item"><span class="list-icon"><pw:icon name="timer"/></span>
          <div class="grow"><div class="title"><c:out value="${a.timeLabel}"/></div>
            <div class="meta"><c:out value="${empty a.timeLimitMinutes ? 'Take the time you need.' : 'The timer starts when you press Start and keeps running if you leave the page. When it reaches zero, your saved answers are submitted.'}"/></div></div></li>
        <li class="list-item"><span class="list-icon"><pw:icon name="activity"/></span>
          <div class="grow"><div class="title"><c:out value="${a.maxAttempts}"/> attempt<c:if test="${a.maxAttempts != 1}">s</c:if> allowed</div>
            <div class="meta">You've used <c:out value="${a.attemptsUsed}"/>.</div></div></li>
        <li class="list-item"><span class="list-icon"><pw:icon name="lock"/></span>
          <div class="grow"><div class="title">Help during the assessment</div><div class="meta"><c:out value="${r.aiPolicyText}"/></div></div></li>
        <li class="list-item"><span class="list-icon"><pw:icon name="eye"/></span>
          <div class="grow"><div class="title">Results</div><div class="meta"><c:out value="${r.feedbackText}"/></div></div></li>
      </ul>
      <p class="legend-note">Your answers here count as official evidence and update your skill levels. That's what lets a skill reach Mastered.</p>
    </section>

    <div class="practice-actions section-gap">
      <c:choose>
        <c:when test="${not empty a.openAttemptId}">
          <a class="btn btn-primary btn-lg" href="<c:url value='/app/assessments/attempt/${a.openAttemptId}'/>">Resume attempt <pw:icon name="arrow-right"/></a>
        </c:when>
        <c:when test="${r.startDecision == 'OK'}">
          <pw:form action="/app/assessments/${a.key}/start">
            <button type="submit" class="btn btn-primary btn-lg"><c:choose><c:when test="${a.attemptsUsed > 0}">Start attempt <c:out value="${a.attemptsUsed + 1}"/></c:when><c:otherwise>Start now</c:otherwise></c:choose> <pw:icon name="arrow-right"/></button>
          </pw:form>
        </c:when>
        <c:otherwise>
          <pw:alert kind="info" message="${r.startMessage}"/>
        </c:otherwise>
      </c:choose>
      <c:if test="${not empty a.lastGradedAttemptId}">
        <a class="btn btn-ghost" href="<c:url value='/app/assessments/result/${a.lastGradedAttemptId}'/>">See latest result</a>
      </c:if>
    </div>
  </div>
</pw:shell>
