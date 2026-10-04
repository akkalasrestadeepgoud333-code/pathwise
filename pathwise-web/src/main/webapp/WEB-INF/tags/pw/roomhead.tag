<%@ tag description="Teacher room header: name, code and section tabs" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="h" required="true" type="java.util.Map" %>
<%@ attribute name="section" required="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<nav class="breadcrumbs" aria-label="Breadcrumb">
  <a href="<c:url value='/teach'/>">Teaching</a><pw:icon name="chevron-right"/>
  <span aria-current="page"><c:out value="${h.name}"/></span>
</nav>
<header class="room-head">
  <div class="grow">
    <h1><c:out value="${h.name}"/><c:if test="${h.archived}"> <span class="chip">Archived</span></c:if></h1>
    <p class="meta"><c:out value="${h.students}"/> student<c:if test="${h.students != 1}">s</c:if> · <c:out value="${h.joinPolicyLabel}"/>
      <c:if test="${h.pending > 0}"> · <a href="<c:url value='/teach/rooms/${h.id}/people'/>"><c:out value="${h.pending}"/> waiting for approval</a></c:if></p>
  </div>
  <div class="room-code" aria-label="Join code">
    <span class="room-code-label">Join code</span>
    <strong class="mono"><c:out value="${h.codeLabel}"/></strong>
  </div>
</header>
<nav class="room-tabs" aria-label="Room sections">
  <a href="<c:url value='/teach/rooms/${h.id}'/>"<c:if test="${section == 'today'}"> aria-current="page"</c:if>>Today</a>
  <a href="<c:url value='/teach/rooms/${h.id}/curriculum'/>"<c:if test="${section == 'curriculum'}"> aria-current="page"</c:if>>Curriculum</a>
  <a href="<c:url value='/teach/rooms/${h.id}/assess'/>"<c:if test="${section == 'assess'}"> aria-current="page"</c:if>>Assessments</a>
  <a href="<c:url value='/teach/rooms/${h.id}/assignments'/>"<c:if test="${section == 'assignments'}"> aria-current="page"</c:if>>Assignments</a>
  <a href="<c:url value='/teach/rooms/${h.id}/doubts'/>"<c:if test="${section == 'doubts'}"> aria-current="page"</c:if>>Doubts</a>
  <a href="<c:url value='/teach/rooms/${h.id}/people'/>"<c:if test="${section == 'people'}"> aria-current="page"</c:if>>People</a>
  <a href="<c:url value='/teach/rooms/${h.id}/analytics'/>"<c:if test="${section == 'analytics'}"> aria-current="page"</c:if>>Analytics</a>
  <c:if test="${h.canEditSettings}"><a href="<c:url value='/teach/rooms/${h.id}/settings'/>"<c:if test="${section == 'settings'}"> aria-current="page"</c:if>>Settings</a></c:if>
  <a href="<c:url value='/app/rooms/${h.id}'/>" class="room-tab-aside">Student view</a>
</nav>
