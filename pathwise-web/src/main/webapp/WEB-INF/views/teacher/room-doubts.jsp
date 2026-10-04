<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="${roomHeader.name}: doubts">
  <pw:roomhead h="${roomHeader}" section="doubts"/>
  <p class="muted">Doubts your students asked you privately. Only you, the room's other teachers and the student can see them.</p>
  <c:choose>
    <c:when test="${empty doubts}"><pw:empty icon="circle-help" title="No doubts yet" message="Students can ask you from Doubts → Ask, choosing this room."/></c:when>
    <c:otherwise>
      <ul class="list card">
        <c:forEach var="d" items="${doubts}">
          <li class="list-item"><span class="list-icon"><pw:icon name="${d.status == 'resolved' ? 'circle-check' : 'circle-help'}"/></span>
            <div class="grow"><div class="title"><a href="<c:url value='/app/doubts/${d.id}'/>"><c:out value="${d.title}"/></a></div>
              <div class="meta"><c:out value="${d.askedBy}"/> · <c:out value="${d.when}"/> · <c:out value="${d.answers}"/> repl<c:out value="${d.answers == 1 ? 'y' : 'ies'}"/></div></div>
            <span class="state-chip item-chip-<c:out value='${d.status == "resolved" ? "done" : "todo"}'/>"><c:out value="${d.statusLabel}"/></span></li>
        </c:forEach>
      </ul>
    </c:otherwise>
  </c:choose>
</pw:shell>
