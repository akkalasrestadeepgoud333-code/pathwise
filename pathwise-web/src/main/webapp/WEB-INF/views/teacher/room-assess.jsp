<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="pg" value="${page}"/>
<c:set var="h" value="${pg.header}"/>
<pw:shell title="${h.name}: assessments">
  <pw:roomhead h="${h}" section="assess"/>

  <c:choose>
    <c:when test="${empty pg.rows}">
      <pw:empty icon="clipboard-check" title="No room assessments yet" message="Build a quiz or test from the question bank. You can also add the platform's ready-made assessments to the curriculum."/>
    </c:when>
    <c:otherwise>
      <div class="card table-wrap">
        <table class="table">
          <caption class="visually-hidden">This room's assessments</caption>
          <thead><tr><th scope="col">Assessment</th><th scope="col">Status</th><th scope="col">Due</th><th scope="col" class="num">Submitted</th><th scope="col">Results</th><th scope="col"><span class="visually-hidden">Actions</span></th></tr></thead>
          <tbody>
            <c:forEach var="a" items="${pg.rows}">
              <tr>
                <th scope="row"><c:out value="${a.title}"/><div class="meta"><c:out value="${a.kindLabel}"/> · <c:out value="${a.questions}"/> questions</div></th>
                <td><span class="chip<c:if test='${a.status == "published"}'> chip-success</c:if>"><c:out value="${a.statusLabel}"/></span></td>
                <td><c:out value="${a.dueLabel}"/></td>
                <td class="num"><c:out value="${a.submitted}"/>/<c:out value="${a.students}"/></td>
                <td><c:out value="${a.feedbackLabel}"/></td>
                <td>
                  <pw:form action="/teach/rooms/${h.id}/assess" cssClass="inline-form">
                    <input type="hidden" name="assessmentId" value="<c:out value='${a.id}'/>">
                    <c:if test="${a.canPublish}"><button type="submit" name="action" value="publish-assessment" class="btn btn-sm btn-primary">Publish</button></c:if>
                    <c:if test="${a.canRelease}"><button type="submit" name="action" value="release-results" class="btn btn-sm btn-primary">Release results</button></c:if>
                    <c:if test="${a.status == 'published'}"><a class="btn btn-sm" href="<c:url value='/teach/rooms/${h.id}/assess/${a.id}'/>"><pw:icon name="chart-column" size="sm"/>Analysis</a></c:if>
                    <c:if test="${h.canAuthor}"><button type="submit" name="action" value="archive-assessment" class="btn btn-sm btn-ghost">Archive</button></c:if>
                  </pw:form>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </c:otherwise>
  </c:choose>

  <c:if test="${h.canAuthor}">
    <section class="card section-gap" aria-labelledby="new-title">
      <h2 id="new-title" class="card-title">New assessment</h2>
      <p class="muted">Pick a topic, then choose questions from its bank. Questions marked <em>test-only</em> never appear in practice, so students won't have seen them.</p>
      <form method="get" action="<c:url value='/teach/rooms/${h.id}/assess/new'/>" class="form-row">
        <div class="field grow">
          <label class="field-label" for="topic">Topic</label>
          <select class="input" id="topic" name="topic" required>
            <c:forEach var="t" items="${pg.topics}"><option value="<c:out value='${t.key}'/>"><c:out value="${t.subjectTitle}"/>: <c:out value="${t.title}"/></option></c:forEach>
          </select>
        </div>
        <button type="submit" class="btn btn-primary">Choose questions</button>
      </form>
    </section>
  </c:if>
</pw:shell>
