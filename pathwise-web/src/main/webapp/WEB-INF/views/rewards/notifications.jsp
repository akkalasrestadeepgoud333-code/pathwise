<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Notifications">
  <div class="page-header">
    <div><h1>Notifications</h1><p>Answers to your doubts, results, badges, room news. Pathwise never sends you nagging reminders.</p></div>
    <c:if test="${not empty items}"><pw:form action="/app/notifications"><button type="submit" class="btn">Mark all as read</button></pw:form></c:if>
  </div>
  <c:choose>
    <c:when test="${empty items}"><pw:empty icon="bell" title="Nothing new" message="You'll hear here when someone answers your doubt, a result is ready or you earn a badge."/></c:when>
    <c:otherwise>
      <ul class="list card notif-list">
        <c:forEach var="n" items="${items}">
          <li class="list-item<c:if test='${n.unread}'> is-unread</c:if>">
            <span class="list-icon"><pw:icon name="${n.type == 'badge_earned' ? 'award' : (n.type == 'milestone' ? 'sparkles' : (n.type == 'announcement' ? 'mail' : 'bell'))}"/></span>
            <div class="grow"><div class="title"><a href="<c:url value='/app/notifications/${n.id}'/>"><c:out value="${n.title}"/></a></div>
              <div class="meta"><c:if test="${not empty n.body}"><c:out value="${n.body}"/> · </c:if><c:out value="${n.when}"/></div></div>
            <c:if test="${n.unread}"><span class="chip chip-accent">New</span></c:if>
          </li>
        </c:forEach>
      </ul>
    </c:otherwise>
  </c:choose>
</pw:shell>
