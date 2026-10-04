<%@ tag description="Answer inputs for one question (no answers are ever rendered here)" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="q" required="true" type="java.util.Map" %>
<%@ attribute name="optionName" required="true" description="form field name for options" %>
<%@ attribute name="textName" required="true" description="form field name for text answers" %>
<%@ attribute name="savedOptions" required="false" type="java.util.List" description="option ids to pre-select (a saved answer)" %>
<%@ attribute name="savedText" required="false" description="text to pre-fill (a saved answer)" %>
<%@ attribute name="idPrefix" required="false" description="makes element ids unique when several questions share a page" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<div class="q-stem prose"><pw:html value="${q.stem}"/></div>
<c:choose>
  <c:when test="${q.type == 'single_choice' or q.type == 'multi_choice'}">
    <fieldset class="q-options">
      <legend class="visually-hidden">Answer options</legend>
      <c:if test="${q.type == 'multi_choice'}"><p class="q-hint-line">Select all that apply.</p></c:if>
      <c:forEach var="o" items="${q.options}">
        <label class="q-option">
          <input type="<c:out value='${q.type == "multi_choice" ? "checkbox" : "radio"}'/>" name="<c:out value='${optionName}'/>" value="<c:out value='${o.id}'/>"<c:if test="${not empty savedOptions and savedOptions.contains(o.id)}"> checked</c:if>>
          <span class="q-mark" aria-hidden="true"></span>
          <span class="q-label"><pw:html value="${o.label}"/></span>
        </label>
      </c:forEach>
    </fieldset>
  </c:when>
  <c:otherwise>
    <div class="field">
      <label class="field-label" for="t-<c:out value='${idPrefix}${q.id}'/>">Your answer</label>
      <input class="input" id="t-<c:out value='${idPrefix}${q.id}'/>" name="<c:out value='${textName}'/>" autocomplete="off"
             <c:if test="${q.type == 'numeric'}">inputmode="decimal"</c:if> maxlength="500" value="<c:out value='${savedText}'/>"
             placeholder="<c:out value='${q.type == "numeric" ? "A number" : "e.g. 8, 4, 2"}'/>">
    </div>
  </c:otherwise>
</c:choose>
