<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="pg" value="${page}"/>
<c:set var="h" value="${pg.header}"/>
<pw:shell title="${h.name}: assignments">
  <pw:roomhead h="${h}" section="assignments"/>

  <c:choose>
    <c:when test="${empty pg.rows}">
      <pw:empty icon="file-text" title="No assignments yet" message="Set written work for the room. When you grade it, the grade counts toward the linked skills."/>
    </c:when>
    <c:otherwise>
      <div class="card table-wrap">
        <table class="table">
          <caption class="visually-hidden">This room's assignments</caption>
          <thead><tr><th scope="col">Assignment</th><th scope="col">Status</th><th scope="col">Due</th><th scope="col" class="num">Handed in</th><th scope="col" class="num">To grade</th><th scope="col"><span class="visually-hidden">Actions</span></th></tr></thead>
          <tbody>
            <c:forEach var="a" items="${pg.rows}">
              <tr>
                <th scope="row"><c:out value="${a.title}"/><div class="meta"><c:out value="${a.points}"/></div></th>
                <td><span class="chip<c:if test='${a.status == "published"}'> chip-success</c:if>"><c:out value="${a.statusLabel}"/></span></td>
                <td><c:out value="${a.dueLabel}"/></td>
                <td class="num"><c:out value="${a.submitted}"/>/<c:out value="${a.students}"/></td>
                <td class="num"><c:out value="${a.toGrade}"/></td>
                <td>
                  <pw:form action="/teach/rooms/${h.id}/assignments" cssClass="inline-form">
                    <input type="hidden" name="assignmentId" value="<c:out value='${a.id}'/>">
                    <c:if test="${a.canPublish}"><button type="submit" name="action" value="publish-assignment" class="btn btn-sm btn-primary">Publish</button></c:if>
                    <c:if test="${a.status == 'published'}"><a class="btn btn-sm<c:if test='${a.toGrade > 0}'> btn-primary</c:if>" href="<c:url value='/teach/rooms/${h.id}/assignments/${a.id}'/>">Grade</a></c:if>
                    <c:if test="${h.canAuthor}"><button type="submit" name="action" value="archive-assignment" class="btn btn-sm btn-ghost">Archive</button></c:if>
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
      <h2 id="new-title" class="card-title">New assignment</h2>
      <pw:form action="/teach/rooms/${h.id}/assignments" cssClass="stack-form">
        <input type="hidden" name="action" value="create-assignment">
        <div class="form-row">
          <div class="field grow">
            <label class="field-label" for="as-title">Title</label>
            <input class="input" id="as-title" name="title" required maxlength="160" placeholder="Explain when a linked list beats an array">
          </div>
          <div class="field">
            <label class="field-label" for="as-points">Points</label>
            <input class="input" id="as-points" name="maxPoints" type="number" min="1" max="1000" value="10">
          </div>
          <div class="field">
            <label class="field-label" for="as-due">Due date <span class="subtle">(optional)</span></label>
            <input class="input" id="as-due" name="dueOn" type="date">
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="as-ins">Instructions <span class="subtle">(Markdown)</span></label>
          <textarea class="input" id="as-ins" name="instructions" rows="5" maxlength="8000"></textarea>
        </div>
        <label class="check-row"><input type="checkbox" name="allowLate" value="0"><span>Don't accept work after the due date</span></label>
        <details class="unit-edit">
          <summary>Skills this assignment shows (optional, up to 10)</summary>
          <p class="legend-note">Grades count as official evidence for these skills, weighted by the grade.</p>
          <ul class="pick-list skill-picks">
            <c:forEach var="k" items="${pg.skills}">
              <li><label class="pick"><input type="checkbox" name="skill" value="<c:out value='${k.id}'/>"><span class="grow"><c:out value="${k.title}"/><span class="meta"><c:out value="${k.topic}"/></span></span></label></li>
            </c:forEach>
          </ul>
        </details>
        <button type="submit" class="btn btn-primary">Save as draft</button>
      </pw:form>
    </section>
  </c:if>
</pw:shell>
