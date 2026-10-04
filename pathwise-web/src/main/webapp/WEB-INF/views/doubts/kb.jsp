<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Knowledge base">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/doubts'/>">Doubts</a><pw:icon name="chevron-right"/><span aria-current="page">Knowledge base</span>
  </nav>
  <div class="page-header"><div><h1>Knowledge base</h1><p>Explanations checked by a moderator, many grown from doubts that students asked.</p></div></div>
  <form method="get" action="<c:url value='/app/kb'/>" class="form-row search-row" role="search">
    <label class="visually-hidden" for="q">Search the knowledge base</label>
    <input class="input grow" id="q" name="q" value="<c:out value='${q}'/>" placeholder="Search">
    <button type="submit" class="btn">Search</button>
  </form>
  <c:choose>
    <c:when test="${empty articles}"><pw:empty icon="book-open" title="Nothing here yet" message="Resolved doubts that help many students are promoted here by moderators."/></c:when>
    <c:otherwise>
      <ul class="list card">
        <c:forEach var="a" items="${articles}">
          <li class="list-item"><span class="list-icon"><pw:icon name="book-open"/></span>
            <div class="grow"><div class="title"><a href="<c:url value='/app/kb/${a.id}'/>"><c:out value="${a.title}"/></a></div>
              <div class="meta"><c:if test="${not empty a.topicTitle}"><c:out value="${a.topicTitle}"/> · </c:if><c:out value="${a.snippet}"/></div></div></li>
        </c:forEach>
      </ul>
    </c:otherwise>
  </c:choose>
</pw:shell>
