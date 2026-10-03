<%@ page contentType="text/html;charset=UTF-8" %>
<pw:document title="${errorStatus == 404 ? 'Page not found' : 'Something went wrong'}">
  <main id="main" class="error-page">
    <div>
      <div class="code" aria-hidden="true"><c:out value="${errorStatus}"/></div>
      <h1><c:choose>
        <c:when test="${errorStatus == 404}">This path doesn't lead anywhere</c:when>
        <c:when test="${errorStatus == 403}">This area is off-limits</c:when>
        <c:otherwise>We hit a bump</c:otherwise>
      </c:choose></h1>
      <p><c:out value="${errorMessage}"/></p>
      <a class="btn btn-primary" href="<c:url value='/'/>"><pw:icon name="compass"/>Back to Pathwise</a>
      <p class="request-id">Reference: <span class="mono"><c:out value="${requestId}"/></span></p>
    </div>
  </main>
</pw:document>
