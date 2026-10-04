<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="r" value="${result}"/>
<pw:shell title="${title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/practice'/>">Practice</a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${title}"/></span>
  </nav>
  <div class="practice">
    <header class="practice-head">
      <h1><c:out value="${title}"/></h1>
      <div class="practice-dots" aria-label="Question <c:out value='${r.position}'/> of <c:out value='${r.total}'/>">
        <c:forEach begin="1" end="${r.total}" var="i"><span class="<c:if test='${i <= r.position}'>done</c:if>"></span></c:forEach>
      </div>
    </header>
    <article class="card question-card">
      <p class="section-kind">Question <c:out value="${r.position}"/> of <c:out value="${r.total}"/></p>
      <pw:feedback f="${r.feedback}"/>
      <div class="practice-actions">
        <c:if test="${not r.feedback.correct}"><pw:form action="/app/tutor/start" cssClass="inline-form">
          <input type="hidden" name="mode" value="explain"><input type="hidden" name="attempt" value="<c:out value='${attemptId}'/>"><input type="hidden" name="position" value="<c:out value='${r.position}'/>">
          <button type="submit" class="btn btn-ghost"><pw:icon name="sparkles" size="sm"/>Explain my mistake</button>
        </pw:form></c:if>
        <a class="btn btn-primary" href="<c:url value='/app/practice/${attemptId}'/>" autofocus>
          <c:out value="${r.last ? 'See how it went' : 'Next question'}"/> <pw:icon name="arrow-right" size="sm"/></a>
      </div>
    </article>
  </div>
</pw:shell>
