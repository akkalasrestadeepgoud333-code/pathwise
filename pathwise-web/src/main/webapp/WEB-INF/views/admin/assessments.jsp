<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Assessments">
  <div class="page-header">
    <div>
      <h1>Assessments</h1>
      <p>Published assessments from content packs, with how students did. Open one for item analysis.</p>
    </div>
  </div>
  <c:choose>
    <c:when test="${empty rows}">
      <pw:empty icon="clipboard-check" title="No assessments published" message="Assessments come from content packs. Import a pack that defines some."/>
    </c:when>
    <c:otherwise>
      <div class="card table-wrap">
        <table class="table">
          <caption class="visually-hidden">Published assessments</caption>
          <thead><tr><th scope="col">Assessment</th><th scope="col">Kind</th><th scope="col" class="num">Questions</th><th scope="col">Time</th>
            <th scope="col" class="num">Graded attempts</th><th scope="col" class="num">Average</th><th scope="col"><span class="visually-hidden">Actions</span></th></tr></thead>
          <tbody>
            <c:forEach var="a" items="${rows}">
              <tr>
                <th scope="row"><c:out value="${a.title}"/><div class="meta mono"><c:out value="${a.key}"/></div></th>
                <td><c:out value="${a.kindLabel}"/></td>
                <td class="num"><c:out value="${a.questions}"/></td>
                <td><c:out value="${a.timeLabel}"/></td>
                <td class="num"><c:out value="${a.gradedAttempts}"/></td>
                <td class="num"><c:out value="${empty a.averagePercent ? '—' : a.averagePercent.toString().concat('%')}"/></td>
                <td><a class="btn btn-sm" href="<c:url value='/admin/assessments/${a.key}'/>"><pw:icon name="chart-column" size="sm"/>Analysis</a></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
