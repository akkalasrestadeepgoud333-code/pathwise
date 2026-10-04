<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="t" value="${thread}"/>
<pw:shell title="${t.title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/doubts'/>">Doubts</a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${t.scopeLabel}"/></span>
  </nav>
  <div class="assess-rules">
    <article class="card doubt" aria-labelledby="d-title">
      <p class="card-eyebrow"><c:out value="${t.scopeLabel}"/><c:if test="${not empty t.roomName}"> · <c:out value="${t.roomName}"/></c:if> · <c:out value="${t.categoryLabel}"/><c:if test="${not empty t.topicTitle}"> · <c:out value="${t.topicTitle}"/></c:if></p>
      <h1 id="d-title" class="doubt-title"><c:out value="${t.title}"/></h1>
      <p class="meta"><span class="who who-<c:out value='${t.asker.kind}'/>"><c:out value="${t.asker.label}"/></span> · <c:out value="${t.when}"/> ·
        <span class="state-chip item-chip-<c:out value='${t.status == "resolved" ? "done" : "todo"}'/>"><c:out value="${t.statusLabel}"/></span></p>
      <div class="prose"><pw:html value="${t.body}"/></div>
      <c:if test="${not t.mine}">
        <details class="unit-edit report">
          <summary>Report</summary>
          <pw:form action="/app/doubts/${t.id}" cssClass="form-row">
            <input type="hidden" name="action" value="report"><input type="hidden" name="kind" value="thread"><input type="hidden" name="targetId" value="<c:out value='${t.id}'/>">
            <select class="input" name="reason" aria-label="Reason"><option value="spam">Spam</option><option value="harassment">Harassment</option><option value="inappropriate">Inappropriate</option><option value="cheating">Asking for test answers</option><option value="misinformation">Wrong or misleading</option><option value="self_harm">Someone may be at risk</option><option value="other">Other</option></select>
            <input class="input grow" name="details" maxlength="1000" placeholder="Anything a moderator should know (optional)" aria-label="Details">
            <button type="submit" class="btn btn-sm">Send report</button>
          </pw:form>
          <c:if test="${not empty t.asker.alias}">
            <pw:form action="/app/doubts/${t.id}" cssClass="inline-form">
              <input type="hidden" name="action" value="block"><input type="hidden" name="alias" value="<c:out value='${t.asker.alias}'/>">
              <button type="submit" class="btn btn-sm btn-ghost">Block this person</button>
            </pw:form>
          </c:if>
        </details>
      </c:if>
    </article>

    <c:if test="${not empty t.practiceHref}">
      <div class="card section-gap-sm resolved-next">
        <p><pw:icon name="sparkles"/> Resolved. Lock it in with a few practice questions on <strong><c:out value="${t.topicTitle}"/></strong>.</p>
        <a class="btn btn-sm btn-primary" href="<c:url value='${t.practiceHref}'/>">Practise this topic</a>
      </div>
    </c:if>

    <h2 class="section-gap"><c:out value="${t.posts.size()}"/> answer<c:if test="${t.posts.size() != 1}">s</c:if></h2>
    <c:forEach var="p" items="${t.posts}">
      <article class="card answer<c:if test='${p.accepted}'> is-accepted</c:if><c:if test='${p.status != "visible"}'> is-moderated</c:if>" aria-label="Answer">
        <div class="answer-head">
          <span class="who who-<c:out value='${p.who.kind}'/>"><c:out value="${p.who.label}"/></span>
          <span class="meta"><c:out value="${p.when}"/></span>
          <c:if test="${p.accepted}"><span class="chip chip-success"><pw:icon name="check" size="sm"/>Accepted</span></c:if>
          <c:if test="${p.verified}"><span class="chip chip-accent"><pw:icon name="award" size="sm"/>Teacher verified</span></c:if>
          <c:if test="${p.status != 'visible'}"><span class="chip">Moderated: <c:out value="${p.status}"/></span></c:if>
        </div>
        <div class="prose"><pw:html value="${p.body}"/></div>
        <div class="answer-actions">
          <pw:form action="/app/doubts/${t.id}" cssClass="inline-form">
            <input type="hidden" name="postId" value="<c:out value='${p.id}'/>">
            <c:choose>
              <c:when test="${p.canVote}"><button type="submit" name="action" value="vote" class="btn btn-sm<c:if test='${p.votedByMe}'> btn-primary</c:if>" aria-pressed="<c:out value='${p.votedByMe}'/>">Helpful · <c:out value="${p.votes}"/></button></c:when>
              <c:otherwise><span class="meta">Helpful · <c:out value="${p.votes}"/></span></c:otherwise>
            </c:choose>
            <c:if test="${p.canAccept}"><button type="submit" name="action" value="accept" class="btn btn-sm">This answered it</button></c:if>
            <c:if test="${p.canVerify}"><button type="submit" name="action" value="verify" class="btn btn-sm">Mark teacher verified</button></c:if>
          </pw:form>
          <c:if test="${not p.mine}">
            <details class="report-inline">
              <summary>Report</summary>
              <pw:form action="/app/doubts/${t.id}" cssClass="form-row">
                <input type="hidden" name="action" value="report"><input type="hidden" name="kind" value="post"><input type="hidden" name="targetId" value="<c:out value='${p.id}'/>">
                <select class="input" name="reason" aria-label="Reason"><option value="spam">Spam</option><option value="harassment">Harassment</option><option value="inappropriate">Inappropriate</option><option value="misinformation">Wrong or misleading</option><option value="cheating">Gives away test answers</option><option value="self_harm">Someone may be at risk</option><option value="other">Other</option></select>
                <button type="submit" class="btn btn-sm">Send</button>
              </pw:form>
              <c:if test="${not empty p.who.alias}">
                <pw:form action="/app/doubts/${t.id}" cssClass="inline-form">
                  <input type="hidden" name="action" value="block"><input type="hidden" name="alias" value="<c:out value='${p.who.alias}'/>">
                  <button type="submit" class="btn btn-sm btn-ghost">Block this person</button>
                </pw:form>
              </c:if>
            </details>
          </c:if>
        </div>
      </article>
    </c:forEach>

    <c:if test="${t.canAnswer}">
      <section class="card section-gap" aria-labelledby="ans-title">
        <h2 id="ans-title" class="card-title"><c:out value="${t.mine ? 'Add more detail or reply' : 'Your answer'}"/></h2>
        <pw:form action="/app/doubts/${t.id}" cssClass="stack-form">
          <input type="hidden" name="action" value="answer">
          <label class="visually-hidden" for="a-body">Answer</label>
          <textarea class="input" id="a-body" name="body" rows="5" required maxlength="8000" placeholder="Explain the idea, not just the answer. Markdown works."></textarea>
          <c:if test="${t.answerAnonymousByDefault}">
            <label class="check-row"><input type="checkbox" name="showName" value="1"><span>Show my name (otherwise you appear as an anonymous helper)</span></label>
          </c:if>
          <button type="submit" class="btn btn-primary">Post</button>
        </pw:form>
      </section>
    </c:if>
    <c:if test="${t.canModerate and t.scope == 'public'}">
      <p class="section-gap"><a class="btn btn-sm" href="<c:url value='/admin/moderation/promote/${t.id}'/>"><pw:icon name="book-open" size="sm"/>Promote to the knowledge base</a></p>
    </c:if>
  </div>
</pw:shell>
