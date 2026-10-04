<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="a" value="${article}"/>
<pw:shell title="${a.title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/kb'/>">Knowledge base</a><pw:icon name="chevron-right"/><span aria-current="page"><c:out value="${a.title}"/></span>
  </nav>
  <article class="assess-rules">
    <p class="card-eyebrow">Pathwise explanation<c:if test="${not empty a.topicTitle}"> · <c:out value="${a.topicTitle}"/></c:if></p>
    <h1><c:out value="${a.title}"/></h1>
    <div class="card prose"><pw:html value="${a.body}"/></div>
    <p class="legend-note">Updated <c:out value="${a.updated}"/>.<c:if test="${not empty a.sourceThreadId}"> <a href="<c:url value='/app/doubts/${a.sourceThreadId}'/>">See the original doubt</a>.</c:if></p>
  </article>
</pw:shell>
