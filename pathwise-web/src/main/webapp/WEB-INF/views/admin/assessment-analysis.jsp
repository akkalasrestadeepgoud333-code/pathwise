<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="an" value="${analysis}"/>
<pw:shell title="${an.title}: analysis">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='${empty backHref ? "/admin/assessments" : backHref}'/>"><c:out value="${empty backLabel ? 'Assessments' : backLabel}"/></a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${an.title}"/></span>
  </nav>
  <div class="page-header">
    <div>
      <h1>Item analysis</h1>
      <p><c:out value="${an.gradedAttempts}"/> graded attempt<c:if test="${an.gradedAttempts != 1}">s</c:if> · average <c:out value="${an.averagePercent}"/>%</p>
    </div>
  </div>
  <section class="card" aria-labelledby="how-title">
    <h2 id="how-title" class="card-title">How to read this</h2>
    <p class="muted"><strong>Facility</strong> is the share of available credit students earned (1.00 means everyone got it).
      <strong>Discrimination</strong> compares the top and bottom 27% of students by total score: positive means stronger students did better on this item.
      It is shown only when there are at least 4 graded attempts. These are classical test statistics, worked out from the attempts above. They are not a model prediction.</p>
    <c:if test="${an.gradedAttempts < 10}">
      <pw:alert kind="info" message="Only ${an.gradedAttempts} graded attempt(s) so far. With this few, treat the flags below as hints to look closer, not conclusions."/>
    </c:if>
  </section>

  <c:forEach var="it" items="${an.items}">
    <article class="card section-gap analysis-item" aria-labelledby="ai<c:out value='${it.position}'/>">
      <div class="card-header">
        <p class="section-kind" id="ai<c:out value='${it.position}'/>">Item <c:out value="${it.position}"/> · <c:out value="${it.responses}"/> responses</p>
        <div class="analysis-stats">
          <span class="chip">Facility <span class="mono"><c:out value="${it.difficultyLabel}"/></span></span>
          <span class="chip">Discrimination <span class="mono"><c:out value="${it.discriminationLabel}"/></span></span>
        </div>
      </div>
      <div class="q-stem prose"><pw:html value="${it.stem}"/></div>
      <c:if test="${not empty it.flag}"><pw:alert kind="info" message="${it.flag}"/></c:if>
      <c:if test="${not empty it.options}">
        <ul class="analysis-options">
          <c:forEach var="o" items="${it.options}">
            <li class="<c:if test='${o.correct}'>is-correct</c:if>">
              <span class="analysis-label"><c:if test="${o.correct}"><pw:icon name="check" size="sm"/></c:if><pw:html value="${o.label}"/></span>
              <span class="analysis-bar" aria-hidden="true"><span class="w-<c:out value='${o.percent - o.percent % 5}'/>"></span></span>
              <span class="mono"><c:out value="${o.count}"/> (<c:out value="${o.percent}"/>%)</span>
            </li>
          </c:forEach>
        </ul>
      </c:if>
    </article>
  </c:forEach>
</pw:shell>
