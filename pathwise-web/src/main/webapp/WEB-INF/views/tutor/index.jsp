<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="h" value="${home}"/>
<pw:shell title="Tutor">
  <div class="page-header">
    <div><h1>Tutor</h1>
      <p>Ask about any topic. Answers come from Pathwise's own modules and knowledge base<c:if test="${h.aiOn}">, explained by a local AI model that can make mistakes</c:if>. Only you can see your conversations, and you can delete them.</p></div>
    <span class="chip<c:if test='${h.aiOn}'> chip-accent</c:if> tutor-badge"><pw:icon name="${h.aiOn ? 'sparkles' : 'book-open'}" size="sm"/><c:out value="${h.providerLabel}"/></span>
  </div>
  <c:if test="${not empty h.policyNote}"><pw:alert kind="info" message="${h.policyNote}"/></c:if>
  <c:if test="${not h.aiOn}"><p class="legend-note">AI is off on this server, so the tutor answers with Guided Help: the most relevant modules, articles, hints and explanations Pathwise already has. It never pretends to be an AI.</p></c:if>

  <div class="grid grid-main-aside">
    <section class="card" aria-labelledby="new-title">
      <h2 id="new-title" class="card-title">Ask something new</h2>
      <pw:form action="/app/tutor/start" cssClass="stack-form">
        <fieldset class="field">
          <legend class="field-label">What kind of help?</legend>
          <label class="check-row"><input type="radio" name="mode" value="tutor" checked><span><strong>Explain a concept</strong></span></label>
          <label class="check-row"><input type="radio" name="mode" value="resource"><span><strong>Find what to read</strong></span></label>
        </fieldset>
        <label class="visually-hidden" for="t-q">Your question</label>
        <textarea class="input" id="t-q" name="q" rows="3" required maxlength="2000" placeholder="Why is inorder traversal of a BST sorted?"></textarea>
        <button type="submit" class="btn btn-primary">Ask</button>
        <p class="legend-note">For a hint on a practice question, or to understand a wrong answer, use the tutor buttons on the question itself.</p>
      </pw:form>
    </section>
    <section class="card" aria-labelledby="hist-title">
      <h2 id="hist-title" class="card-title">Your conversations</h2>
      <c:choose>
        <c:when test="${empty h.conversations}"><p class="muted">None yet.</p></c:when>
        <c:otherwise>
          <ul class="list">
            <c:forEach var="cv" items="${h.conversations}">
              <li class="list-item"><div class="grow"><div class="title"><a href="<c:url value='/app/tutor/${cv.id}'/>"><c:out value="${cv.title}"/></a></div>
                <div class="meta"><c:out value="${cv.modeLabel}"/> · <c:out value="${cv.when}"/></div></div></li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>
  </div>
</pw:shell>
