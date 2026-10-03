<%@ tag description="Labelled form field with hint and accessible error" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="name" required="true" %>
<%@ attribute name="label" required="true" %>
<%@ attribute name="type" required="false" %>
<%@ attribute name="value" required="false" %>
<%@ attribute name="hint" required="false" %>
<%@ attribute name="error" required="false" %>
<%@ attribute name="autocomplete" required="false" %>
<%@ attribute name="inputmode" required="false" %>
<%@ attribute name="placeholder" required="false" %>
<%@ attribute name="required" required="false" type="java.lang.Boolean" %>
<%@ attribute name="autofocus" required="false" type="java.lang.Boolean" %>
<%@ attribute name="reveal" required="false" type="java.lang.Boolean" description="Adds a show/hide button (password fields)" %>
<%@ attribute name="strength" required="false" type="java.lang.Boolean" description="Adds a password strength hint" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<c:set var="fieldId" value="f-${name}"/>
<div class="field">
  <label class="field-label" for="<c:out value='${fieldId}'/>"><c:out value="${label}"/></label>
  <div class="input-wrap">
    <input class="input" id="<c:out value='${fieldId}'/>" name="<c:out value='${name}'/>"
           type="<c:out value='${empty type ? "text" : type}'/>"
           <c:if test="${type != 'password'}">value="<c:out value='${value}'/>"</c:if>
           <c:if test="${not empty autocomplete}">autocomplete="<c:out value='${autocomplete}'/>"</c:if>
           <c:if test="${not empty inputmode}">inputmode="<c:out value='${inputmode}'/>"</c:if>
           <c:if test="${not empty placeholder}">placeholder="<c:out value='${placeholder}'/>"</c:if>
           <c:if test="${required}">required</c:if>
           <c:if test="${autofocus}">autofocus</c:if>
           <c:if test="${not empty error}">aria-invalid="true"</c:if>
           aria-describedby="<c:out value='${fieldId}'/>-hint <c:out value='${fieldId}'/>-error">
    <c:if test="${reveal}">
      <button type="button" class="icon-btn reveal" hidden data-reveal="<c:out value='${fieldId}'/>"
              data-icon-base="<c:url value='/static/img/icons.svg'/>" aria-label="Show password" aria-pressed="false">
        <pw:icon name="eye"/>
      </button>
    </c:if>
  </div>
  <c:if test="${strength}">
    <div class="strength" hidden data-strength-for="<c:out value='${fieldId}'/>" data-strength-label="<c:out value='${fieldId}'/>-strength" data-score="0" aria-hidden="true">
      <span></span><span></span><span></span><span></span>
    </div>
  </c:if>
  <c:if test="${not empty hint}">
    <p class="field-hint" id="<c:out value='${fieldId}'/>-hint"><c:out value="${hint}"/><c:if test="${strength}"> <span id="<c:out value='${fieldId}'/>-strength" aria-live="polite"></span></c:if></p>
  </c:if>
  <c:if test="${not empty error}">
    <p class="field-error" id="<c:out value='${fieldId}'/>-error"><pw:icon name="triangle-alert" size="sm"/><c:out value="${error}"/></p>
  </c:if>
</div>
