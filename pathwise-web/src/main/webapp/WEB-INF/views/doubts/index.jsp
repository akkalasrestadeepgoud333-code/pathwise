<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="l" value="${lists}"/>
<pw:shell title="Doubts">
  <div class="page-header">
    <div>
      <h1>Doubts</h1>
      <p>Ask your teacher privately, or ask the open doubt network, where you're anonymous unless you choose otherwise.</p>
    </div>
    <a class="btn btn-primary" href="<c:url value='/app/doubts/ask'/>"><pw:icon name="circle-help"/>Ask a doubt</a>
  </div>

  <div class="grid grid-main-aside">
    <section aria-labelledby="net-title">
      <div class="section-head">
        <h2 id="net-title">Doubt network</h2>
        <a href="<c:url value='/app/kb'/>">Knowledge base →</a>
      </div>
      <form method="get" action="<c:url value='/app/doubts'/>" class="form-row search-row" role="search">
        <label class="visually-hidden" for="q">Search doubts</label>
        <input class="input grow" id="q" name="q" value="<c:out value='${l.query}'/>" placeholder="Search, e.g. preorder traversal">
        <button type="submit" class="btn">Search</button>
      </form>
      <c:choose>
        <c:when test="${empty l.network}"><pw:empty icon="circle-help" title="${empty l.query ? 'No public doubts yet' : 'No matches'}" message="${empty l.query ? 'Be the first to ask. You stay anonymous unless you choose to show your name.' : 'Try other words, or ask it yourself.'}"/></c:when>
        <c:otherwise>
          <ul class="list card doubt-list">
            <c:forEach var="t" items="${l.network}">
              <li class="list-item">
                <span class="list-icon"><pw:icon name="${t.status == 'resolved' ? 'circle-check' : 'circle-help'}"/></span>
                <div class="grow"><div class="title"><a href="<c:url value='/app/doubts/${t.id}'/>"><c:out value="${t.title}"/></a></div>
                  <div class="meta"><c:out value="${t.categoryLabel}"/> · <c:out value="${t.answers}"/> answer<c:if test="${t.answers != 1}">s</c:if> · <c:out value="${t.when}"/></div></div>
                <c:if test="${t.verified}"><span class="chip chip-success">Teacher verified</span></c:if>
                <span class="state-chip item-chip-<c:out value='${t.status == "resolved" ? "done" : "todo"}'/>"><c:out value="${t.statusLabel}"/></span>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>

    <section class="card" aria-labelledby="mine-title">
      <h2 id="mine-title" class="card-title">Your doubts</h2>
      <c:choose>
        <c:when test="${empty l.mine}"><p class="muted">Nothing yet.</p></c:when>
        <c:otherwise>
          <ul class="list">
            <c:forEach var="t" items="${l.mine}">
              <li class="list-item">
                <div class="grow"><div class="title"><a href="<c:url value='/app/doubts/${t.id}'/>"><c:out value="${t.title}"/></a></div>
                  <div class="meta"><c:out value="${t.scopeLabel}"/><c:if test="${not empty t.context}"> · <c:out value="${t.context}"/></c:if> · <c:out value="${t.answers}"/> answer<c:if test="${t.answers != 1}">s</c:if></div></div>
                <span class="state-chip item-chip-<c:out value='${t.status == "resolved" ? "done" : "todo"}'/>"><c:out value="${t.statusLabel}"/></span>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>
  </div>
</pw:shell>
