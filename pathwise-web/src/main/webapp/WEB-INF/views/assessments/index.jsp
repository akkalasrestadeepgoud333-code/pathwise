<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Assessments">
  <div class="page-header">
    <div>
      <h1>Assessments</h1>
      <p>Timed checks that count as official evidence of what you know. Practice never counts here, and these questions never appear in practice.</p>
    </div>
  </div>

  <c:choose>
    <c:when test="${empty cards}">
      <pw:empty icon="clipboard-check" title="No assessments yet" message="When an assessment is published for your subjects, it will appear here."/>
    </c:when>
    <c:otherwise>
      <div class="grid grid-2 assess-grid">
        <c:forEach var="a" items="${cards}">
          <article class="card assess-card" aria-labelledby="a-<c:out value='${a.key}'/>">
            <div class="assess-card-head">
              <span class="chip"><c:out value="${a.kindLabel}"/></span>
              <span class="state-chip assess-<c:out value='${a.state}'/>"><c:out value="${a.stateLabel}"/></span>
            </div>
            <h2 class="card-title" id="a-<c:out value='${a.key}'/>"><c:out value="${a.title}"/></h2>
            <p class="meta"><c:out value="${a.subjectTitle}"/></p>
            <ul class="assess-facts">
              <li><pw:icon name="clipboard-check" size="sm"/><c:out value="${a.questions}"/> questions</li>
              <li><pw:icon name="timer" size="sm"/><c:out value="${a.timeLabel}"/></li>
              <li><pw:icon name="activity" size="sm"/><c:out value="${a.attemptsUsed}"/> of <c:out value="${a.maxAttempts}"/> attempt<c:if test="${a.maxAttempts != 1}">s</c:if> used</li>
            </ul>
            <c:if test="${not empty a.best}"><p class="assess-best">Best score <strong><c:out value="${a.best}"/></strong></p></c:if>
            <div class="assess-actions">
              <c:choose>
                <c:when test="${not empty a.openAttemptId}">
                  <a class="btn btn-primary" href="<c:url value='/app/assessments/attempt/${a.openAttemptId}'/>">Resume <pw:icon name="arrow-right" size="sm"/></a>
                </c:when>
                <c:when test="${a.state == 'open' or a.state == 'retake'}">
                  <a class="btn btn-primary" href="<c:url value='/app/assessments/${a.key}'/>"><c:out value="${a.state == 'open' ? 'View and start' : 'Retake'}"/> <pw:icon name="arrow-right" size="sm"/></a>
                </c:when>
                <c:otherwise>
                  <a class="btn" href="<c:url value='/app/assessments/${a.key}'/>">Details</a>
                </c:otherwise>
              </c:choose>
              <c:if test="${not empty a.lastGradedAttemptId}">
                <a class="btn btn-ghost" href="<c:url value='/app/assessments/result/${a.lastGradedAttemptId}'/>">Latest result</a>
              </c:if>
            </div>
          </article>
        </c:forEach>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
