<%@ tag description="Inline alert. kind = success | info | error" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="kind" required="true" %>
<%@ attribute name="message" required="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<div class="alert alert-<c:out value='${kind}'/>" role="<c:out value='${kind == "error" ? "alert" : "status"}'/>">
  <pw:icon name="${kind == 'success' ? 'circle-check' : (kind == 'error' ? 'triangle-alert' : 'info')}"/>
  <p><c:out value="${message}"/></p>
</div>
