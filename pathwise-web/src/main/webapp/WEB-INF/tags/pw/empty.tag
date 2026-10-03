<%@ tag description="Helpful empty state with an illustration and optional actions (body)" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="title" required="true" %>
<%@ attribute name="message" required="true" %>
<%@ attribute name="icon" required="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<div class="empty">
  <div class="list-icon" aria-hidden="true"><pw:icon name="${empty icon ? 'compass' : icon}" size="lg"/></div>
  <h3><c:out value="${title}"/></h3>
  <p><c:out value="${message}"/></p>
  <jsp:doBody/>
</div>
