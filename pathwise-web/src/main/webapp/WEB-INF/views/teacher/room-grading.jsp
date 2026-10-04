<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="g" value="${grading}"/>
<c:set var="h" value="${g.header}"/>
<pw:shell title="${g.title}: grading">
  <pw:roomhead h="${h}" section="assignments"/>
  <div class="page-header">
    <div>
      <h2><c:out value="${g.title}"/></h2>
      <p class="muted"><c:out value="${g.dueLabel}"/> · <c:out value="${g.points}"/> points<c:if test="${not empty g.skills}"> · skills: <c:forEach var="k" items="${g.skills}" varStatus="st"><c:out value="${k}"/><c:if test="${not st.last}">, </c:if></c:forEach></c:if></p>
    </div>
  </div>
  <c:if test="${not empty g.instructions.html}">
    <details class="card unit-edit"><summary>Instructions</summary><div class="prose"><pw:html value="${g.instructions}"/></div></details>
  </c:if>

  <c:if test="${not empty g.missing}">
    <p class="muted section-gap-sm">Not handed in yet: <c:forEach var="m" items="${g.missing}" varStatus="st"><c:out value="${m}"/><c:if test="${not st.last}">, </c:if></c:forEach></p>
  </c:if>

  <c:choose>
    <c:when test="${empty g.submissions}"><pw:empty icon="file-text" title="Nothing handed in yet" message="Submissions appear here as students hand them in."/></c:when>
    <c:otherwise>
      <c:forEach var="s" items="${g.submissions}">
        <article class="card section-gap submission" aria-labelledby="sub-<c:out value='${s.userId}'/>">
          <div class="card-header">
            <h3 class="card-title" id="sub-<c:out value='${s.userId}'/>"><c:out value="${s.name}"/></h3>
            <span class="chip<c:if test='${s.status == "graded"}'> chip-success</c:if>"><c:out value="${s.statusLabel}"/><c:if test="${s.status == 'graded'}">: <c:out value="${s.grade}"/>/<c:out value="${g.points}"/></c:if></span>
          </div>
          <p class="meta">Handed in <c:out value="${s.submittedLabel}"/><c:if test="${s.late}"> · late</c:if><c:if test="${s.version > 1}"> · version <c:out value="${s.version}"/></c:if></p>
          <div class="submission-body"><c:out value="${s.body}"/></div>
          <c:if test="${not empty s.feedback}"><p class="fb-why"><strong>Your feedback:</strong> <c:out value="${s.feedback}"/></p></c:if>
          <c:if test="${g.canGrade and s.status == 'submitted'}">
            <pw:form action="/teach/rooms/${h.id}/assignments/${g.id}" cssClass="stack-form grade-form">
              <input type="hidden" name="action" value="grade">
              <input type="hidden" name="assignmentId" value="<c:out value='${g.id}'/>">
              <input type="hidden" name="userId" value="<c:out value='${s.userId}'/>">
              <div class="form-row">
                <div class="field">
                  <label class="field-label" for="gr-<c:out value='${s.userId}'/>">Points (out of <c:out value="${g.points}"/>)</label>
                  <input class="input" id="gr-<c:out value='${s.userId}'/>" name="grade" inputmode="decimal" maxlength="8">
                </div>
                <div class="field grow">
                  <label class="field-label" for="fb-<c:out value='${s.userId}'/>">Feedback</label>
                  <textarea class="input" id="fb-<c:out value='${s.userId}'/>" name="feedback" rows="2" maxlength="8000"></textarea>
                </div>
              </div>
              <div class="practice-actions">
                <button type="submit" class="btn btn-primary btn-sm">Save grade</button>
                <button type="submit" name="sendBack" value="1" class="btn btn-ghost btn-sm">Send back for changes</button>
              </div>
            </pw:form>
          </c:if>
        </article>
      </c:forEach>
    </c:otherwise>
  </c:choose>
</pw:shell>
