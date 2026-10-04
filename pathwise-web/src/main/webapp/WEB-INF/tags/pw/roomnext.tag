<%@ tag description="From your rooms: each room's next item and what's due soon" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="rooms" required="true" type="java.util.List" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<c:if test="${not empty rooms}">
  <section class="section-gap" aria-labelledby="from-rooms">
    <h2 id="from-rooms">From your rooms</h2>
    <div class="grid grid-2">
      <c:forEach var="r" items="${rooms}">
        <article class="card room-next">
          <p class="card-eyebrow"><a href="<c:url value='/app/rooms/${r.roomId}'/>"><c:out value="${r.roomName}"/></a></p>
          <c:if test="${not empty r.next}">
            <h3 class="card-title"><a href="<c:url value='${r.next.href}'/>"><c:out value="${r.next.title}"/></a></h3>
            <p class="meta"><c:out value="${r.next.kindLabel}"/> · <c:out value="${r.next.statusLabel}"/> · next in your teacher's order<c:if test="${not empty r.nextDue}"> <span class="chip chip-accent"><c:out value="${r.nextDue}"/></span></c:if></p>
          </c:if>
          <c:if test="${not empty r.dueSoon}">
            <ul class="list due-list">
              <c:forEach var="d" items="${r.dueSoon}">
                <li class="list-item">
                  <span class="list-icon"><pw:icon name="timer"/></span>
                  <div class="grow"><div class="title"><a href="<c:url value='${d.href}'/>"><c:out value="${d.title}"/></a></div>
                    <div class="meta"><c:out value="${d.kindLabel}"/> · due <c:out value="${d.dueLabel}"/></div></div>
                  <span class="chip<c:if test='${d.daysLeft < 2}'> chip-accent</c:if>"><c:out value="${d.inLabel}"/></span>
                </li>
              </c:forEach>
            </ul>
          </c:if>
        </article>
      </c:forEach>
    </div>
  </section>
</c:if>
