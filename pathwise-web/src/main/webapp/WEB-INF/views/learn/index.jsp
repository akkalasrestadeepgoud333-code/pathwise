<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Learn">
  <div class="page-header">
    <div>
      <h1>Learn</h1>
      <p>Pick a subject to open its Learning Map. You can start anywhere. The map shows what each topic builds on.</p>
    </div>
  </div>

  <c:choose>
    <c:when test="${empty subjects}">
      <pw:empty title="No subjects published yet" icon="book-open"
                message="An administrator can import the bundled subjects from Admin › Content."/>
    </c:when>
    <c:otherwise>
      <div class="grid grid-2">
        <c:forEach var="s" items="${subjects}">
          <a class="card card-interactive subject-card" href="<c:url value='/app/map/${s.key}'/>">
            <div>
              <p class="card-eyebrow"><c:out value="${s.course}"/></p>
              <h2><c:out value="${s.title}"/></h2>
            </div>
            <p><c:out value="${s.description}"/></p>
            <div class="subject-meta">
              <span><pw:icon name="route" size="sm"/><c:out value="${s.topics}"/> topics</span>
              <span><pw:icon name="book-open" size="sm"/><c:out value="${s.modules}"/> modules</span>
              <span><pw:icon name="circle-check" size="sm"/><c:out value="${s.modulesDone}"/> studied</span>
            </div>
            <div>
              <div class="progress" role="progressbar" aria-label="Modules studied" aria-valuemin="0" aria-valuemax="100" aria-valuenow="<c:out value='${s.percent}'/>">
                <span class="w-<c:out value='${s.percent - s.percent % 5}'/>"></span>
              </div>
            </div>
          </a>
        </c:forEach>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
