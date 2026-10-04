<%@ tag description="A recommendation with its reason, a Why-this panel and the student's controls" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="rec" required="true" type="java.util.Map" %>
<%@ attribute name="from" required="false" description="today | next (where to return after not-now/not-relevant)" %>
<%@ attribute name="hero" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<article class="rec<c:if test='${hero}'> rec-hero</c:if> rec-<c:out value='${rec.slot}'/>">
  <p class="rec-slot"><c:out value="${rec.slotLabel}"/><c:if test="${not empty rec.topicTitle}"> · <c:out value="${rec.topicTitle}"/></c:if></p>
  <h3 class="rec-title"><c:out value="${rec.title}"/></h3>
  <p class="rec-reason"><c:out value="${rec.reasonText}"/></p>
  <details class="why">
    <summary><pw:icon name="circle-help" size="sm"/>Why this?</summary>
    <div class="why-body">
      <p><strong>Main reason:</strong> <c:out value="${rec.reasonText}"/></p>
      <c:if test="${not empty rec.factors}">
        <p class="why-heading">What Pathwise weighed</p>
        <ul class="why-factors">
          <c:forEach var="f" items="${rec.factors}">
            <li><span class="why-label"><c:out value="${f.label}"/></span> <c:out value="${f.explanation}"/></li>
          </c:forEach>
        </ul>
      </c:if>
      <p class="why-data"><c:out value="${rec.basedOn}"/> Doing this updates your level, and your next suggestions with it.</p>
      <p class="why-data">This suggestion comes from transparent rules, not a black box. You can always choose something else.</p>
    </div>
  </details>
  <div class="rec-actions">
    <pw:form action="/app/next/${rec.id}/start">
      <button type="submit" class="btn <c:out value='${hero ? "btn-primary btn-lg" : "btn-primary btn-sm"}'/>"><c:out value="${rec.action}"/> <pw:icon name="arrow-right" size="sm"/></button>
    </pw:form>
    <div class="rec-secondary">
      <c:if test="${rec.slot != 'continue'}">
        <pw:form action="/app/next/${rec.id}/already-know">
          <button type="submit" class="btn btn-ghost btn-sm">I already know this</button>
        </pw:form>
      </c:if>
      <pw:form action="/app/next/${rec.id}/not-now">
        <input type="hidden" name="from" value="<c:out value='${from}'/>">
        <button type="submit" class="btn btn-ghost btn-sm">Not now</button>
      </pw:form>
      <pw:form action="/app/next/${rec.id}/not-relevant">
        <input type="hidden" name="from" value="<c:out value='${from}'/>">
        <button type="submit" class="btn btn-ghost btn-sm">Not relevant</button>
      </pw:form>
    </div>
  </div>
</article>
