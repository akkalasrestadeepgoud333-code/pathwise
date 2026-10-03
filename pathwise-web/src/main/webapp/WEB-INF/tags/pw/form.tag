<%@ tag description="POST form that always carries the CSRF token" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="action" required="true" description="Context-relative path, e.g. /login" %>
<%@ attribute name="cssClass" required="false" %>
<%@ attribute name="id" required="false" %>
<%@ attribute name="dataAttr" required="false" description="Name of one data-* flag attribute to add" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<form method="post" action="<c:url value='${action}'/>" class="<c:out value='${cssClass}'/>"<c:if test="${not empty id}"> id="<c:out value='${id}'/>"</c:if><c:if test="${not empty dataAttr}"> data-<c:out value='${dataAttr}'/></c:if> novalidate>
  <input type="hidden" name="_csrf" value="<c:out value='${requestScope.csrfToken}'/>">
  <jsp:doBody/>
</form>
