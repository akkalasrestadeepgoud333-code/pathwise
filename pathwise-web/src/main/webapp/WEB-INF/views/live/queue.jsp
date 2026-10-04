<%@ page contentType="text/html;charset=UTF-8" %>
<%-- The refreshing part of a live session: who's here, raised hands and the question queue. --%>
<div class="grid grid-2">
  <section class="card" aria-labelledby="hands-title">
    <h2 id="hands-title" class="card-title">Hands up</h2>
    <p class="meta"><c:out value="${s.participants}"/> here</p>
    <c:choose>
      <c:when test="${empty s.hands}"><p class="muted">No hands raised.</p></c:when>
      <c:otherwise><ol class="plain-list"><c:forEach var="h" items="${s.hands}"><li><c:out value="${h}"/></li></c:forEach></ol></c:otherwise>
    </c:choose>
  </section>
  <section class="card" aria-labelledby="queue-title">
    <h2 id="queue-title" class="card-title">Questions</h2>
    <c:choose>
      <c:when test="${empty s.questions}"><p class="muted">No questions yet.</p></c:when>
      <c:otherwise>
        <ol class="plain-list live-queue">
          <c:forEach var="q" items="${s.questions}">
            <li class="live-q live-q-<c:out value='${q.status}'/>">
              <p class="meta"><c:out value="${q.who}"/><c:if test="${q.mine}"> (you)</c:if> ·
                <c:choose>
                  <c:when test="${q.status == 'answering'}"><strong>Being answered now</strong></c:when>
                  <c:when test="${q.status == 'answered'}">Answered</c:when>
                  <c:when test="${q.status == 'dismissed'}">Dismissed</c:when>
                  <c:otherwise>Waiting</c:otherwise>
                </c:choose> · <c:out value="${q.votes}"/> upvote<c:if test="${q.votes != 1}">s</c:if></p>
              <p><c:out value="${q.body}"/></p>
              <div class="form-row">
                <c:if test="${s.canTakePart and not q.mine and q.status == 'queued'}">
                  <pw:form action="/app/live/${s.id}">
                    <input type="hidden" name="questionId" value="<c:out value='${q.id}'/>">
                    <button type="submit" name="action" value="vote" class="btn btn-sm" aria-pressed="<c:out value='${q.myVote}'/>"><c:out value="${q.myVote ? 'Upvoted' : 'Me too'}"/></button>
                  </pw:form>
                </c:if>
                <c:if test="${s.host and s.status == 'live'}">
                  <pw:form action="/app/live/${s.id}" cssClass="form-row">
                    <input type="hidden" name="action" value="question"><input type="hidden" name="questionId" value="<c:out value='${q.id}'/>">
                    <c:if test="${q.status == 'queued'}"><button type="submit" name="to" value="answering" class="btn btn-sm btn-primary">Answer now</button></c:if>
                    <c:if test="${q.status == 'queued' or q.status == 'answering'}"><button type="submit" name="to" value="answered" class="btn btn-sm">Mark answered</button>
                      <button type="submit" name="to" value="dismissed" class="btn btn-sm btn-ghost">Dismiss</button></c:if>
                    <c:if test="${q.status == 'dismissed'}"><button type="submit" name="to" value="queued" class="btn btn-sm btn-ghost">Back to queue</button></c:if>
                  </pw:form>
                </c:if>
              </div>
            </li>
          </c:forEach>
        </ol>
      </c:otherwise>
    </c:choose>
  </section>
</div>
