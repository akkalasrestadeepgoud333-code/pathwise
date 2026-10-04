<%@ tag description="Feedback after answering: verdict, why each option is right or wrong, explanation" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="f" required="true" type="java.util.Map" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<div class="verdict <c:out value='${f.correct ? "verdict-right" : (f.partial > 0 ? "verdict-partial" : "verdict-wrong")}'/>" role="status">
  <pw:icon name="${f.correct ? 'circle-check' : 'triangle-alert'}"/>
  <strong><c:choose><c:when test="${f.correct}">Correct</c:when><c:when test="${f.partial > 0}">Partly right</c:when><c:otherwise>Not quite</c:otherwise></c:choose></strong>
</div>
<c:choose>
  <c:when test="${not empty f.options}">
    <ul class="fb-options">
      <c:forEach var="o" items="${f.options}">
        <li class="fb-option<c:if test='${o.correct}'> is-correct</c:if><c:if test='${o.chosen}'> is-chosen</c:if>">
          <div class="fb-head">
            <c:choose>
              <c:when test="${o.correct}"><span class="chip chip-success"><pw:icon name="check" size="sm"/>Correct answer</span></c:when>
              <c:when test="${o.chosen}"><span class="chip chip-danger">Your choice</span></c:when>
            </c:choose>
            <c:if test="${o.correct and o.chosen}"><span class="chip">Your choice</span></c:if>
            <span class="fb-label"><pw:html value="${o.label}"/></span>
          </div>
          <c:if test="${not empty o.rationale and (o.correct or o.chosen)}"><p class="fb-why"><pw:html value="${o.rationale}"/></p></c:if>
        </li>
      </c:forEach>
    </ul>
  </c:when>
  <c:otherwise>
    <p class="fb-text">Your answer: <span class="mono"><c:out value="${empty f.given ? '(blank)' : f.given}"/></span>
      <c:if test="${not f.correct and not empty f.expected}"> · Expected: <span class="mono"><c:out value="${f.expected}"/></span></c:if></p>
  </c:otherwise>
</c:choose>
<c:if test="${not empty f.explanation and not empty f.explanation.html}">
  <div class="fb-explain prose"><pw:html value="${f.explanation}"/></div>
</c:if>
