<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="b" value="${builder}"/>
<c:set var="h" value="${b.header}"/>
<pw:shell title="New assessment">
  <pw:roomhead h="${h}" section="assess"/>
  <h2>New assessment: <c:out value="${b.topic.title}"/></h2>

  <pw:form action="/teach/rooms/${h.id}/assess" cssClass="stack-form">
    <input type="hidden" name="action" value="create-assessment">
    <input type="hidden" name="topic" value="<c:out value='${b.topic.key}'/>">
    <section class="card" aria-labelledby="set-title">
      <h3 id="set-title" class="card-title">Settings</h3>
      <div class="form-row">
        <div class="field grow">
          <label class="field-label" for="a-title">Title</label>
          <input class="input" id="a-title" name="title" required maxlength="160" value="<c:out value='${b.topic.title}'/> quiz">
        </div>
        <div class="field">
          <label class="field-label" for="a-kind">Kind</label>
          <select class="input" id="a-kind" name="kind"><option value="quiz">Quiz</option><option value="topic_test">Topic test</option><option value="practice_test">Practice test</option></select>
        </div>
      </div>
      <div class="form-row">
        <div class="field">
          <label class="field-label" for="a-limit">Time limit (minutes)</label>
          <input class="input" id="a-limit" name="timeLimit" type="number" min="1" max="240" placeholder="None">
        </div>
        <div class="field">
          <label class="field-label" for="a-attempts">Attempts</label>
          <input class="input" id="a-attempts" name="maxAttempts" type="number" min="1" max="20" value="1">
        </div>
        <div class="field">
          <label class="field-label" for="a-due">Due date <span class="subtle">(optional)</span></label>
          <input class="input" id="a-due" name="dueOn" type="date">
        </div>
      </div>
      <fieldset class="field">
        <legend class="field-label">When students see the answers</legend>
        <label class="check-row"><input type="radio" name="feedback" value="immediate" checked><span>As soon as they submit</span></label>
        <label class="check-row"><input type="radio" name="feedback" value="after_due"><span>After the due date (needs a due date)</span></label>
        <label class="check-row"><input type="radio" name="feedback" value="manual_release"><span>When I release them (they see their score straight away)</span></label>
      </fieldset>
      <label class="check-row"><input type="checkbox" name="shuffle" value="1" checked><span>Shuffle question order for each student</span></label>
      <div class="field">
        <label class="field-label" for="a-ins">Instructions <span class="subtle">(optional)</span></label>
        <textarea class="input" id="a-ins" name="instructions" rows="2" maxlength="2000"></textarea>
      </div>
    </section>

    <section class="card" aria-labelledby="q-title">
      <h3 id="q-title" class="card-title">Questions (<c:out value="${b.questions.size()}"/> in the bank)</h3>
      <c:choose>
        <c:when test="${empty b.questions}"><p class="muted">This topic has no questions yet. Choose another topic.</p></c:when>
        <c:otherwise>
          <ul class="pick-list">
            <c:forEach var="q" items="${b.questions}">
              <li>
                <label class="pick">
                  <input type="checkbox" name="q" value="<c:out value='${q.id}'/>">
                  <span class="grow"><c:out value="${q.stem}"/>
                    <span class="meta"><c:out value="${q.type}"/> · difficulty <c:out value="${q.difficulty}"/><c:if test="${q.testOnly}"> · <strong>test-only</strong></c:if></span></span>
                </label>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>
    <div class="practice-actions">
      <button type="submit" class="btn btn-primary">Save as draft</button>
      <a class="btn btn-ghost" href="<c:url value='/teach/rooms/${h.id}/assess'/>">Cancel</a>
    </div>
  </pw:form>
</pw:shell>
