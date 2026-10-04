<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="t" value="${attempt}"/>
<pw:shell title="${t.title}" script="/static/js/assessment/attempt.js">
  <div class="assess-attempt">
    <header class="assess-bar" aria-label="Assessment status">
      <div class="grow">
        <h1 class="assess-bar-title"><c:out value="${t.title}"/></h1>
        <p class="meta"><span data-answered><c:out value="${t.answered}"/></span> of <c:out value="${t.total}"/> answered · <span data-save-status role="status">Answers save automatically</span></p>
      </div>
      <c:if test="${not empty t.deadlineEpochMs}">
        <div class="assess-timer" role="timer" aria-live="off" data-deadline="<c:out value='${t.deadlineEpochMs}'/>" data-seconds-left="<c:out value='${t.secondsLeft}'/>">
          <pw:icon name="timer"/><span data-clock><c:out value="${t.timeLeftLabel}"/></span>
        </div>
      </c:if>
    </header>
    <p class="assess-policy"><pw:icon name="lock" size="sm"/><c:out value="${t.aiPolicyText}"/></p>

    <c:if test="${confirming}">
      <section class="card assess-confirm" aria-labelledby="confirm-title">
        <h2 id="confirm-title" class="card-title">Submit with unanswered questions?</h2>
        <p>Unanswered questions score zero. You can still go back and answer them below.</p>
        <pw:form action="/app/assessments/attempt/${t.attemptId}/submit">
          <input type="hidden" name="confirm" value="yes">
          <button type="submit" class="btn btn-primary">Submit anyway</button>
        </pw:form>
      </section>
    </c:if>

    <pw:form action="/app/assessments/attempt/${t.attemptId}/submit" id="attempt-form" cssClass="assess-form">
      <c:forEach var="it" items="${t.items}">
        <article class="card question-card assess-item" id="q<c:out value='${it.position}'/>" aria-labelledby="qt<c:out value='${it.position}'/>">
          <input type="hidden" name="item" value="<c:out value='${it.itemId}'/>">
          <p class="section-kind" id="qt<c:out value='${it.position}'/>">Question <c:out value="${it.position}"/> of <c:out value="${t.total}"/></p>
          <pw:question q="${it.question}" optionName="o.${it.itemId}" textName="t.${it.itemId}" savedOptions="${it.savedOptions}" savedText="${it.savedText}" idPrefix="${it.position}-"/>
        </article>
      </c:forEach>
      <div class="assess-submit card">
        <p class="muted">Check your answers, then submit. You can't change them afterwards.</p>
        <div class="practice-actions">
          <button type="submit" class="btn btn-primary btn-lg" data-submit>Submit answers <pw:icon name="arrow-right"/></button>
          <button type="submit" class="btn btn-ghost" formaction="<c:url value='/app/assessments/attempt/${t.attemptId}/save'/>" data-save>Save and continue later</button>
        </div>
      </div>
    </pw:form>
  </div>
</pw:shell>
