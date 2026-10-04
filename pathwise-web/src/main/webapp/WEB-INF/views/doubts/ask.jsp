<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="o" value="${options}"/>
<pw:shell title="Ask a doubt">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/doubts'/>">Doubts</a><pw:icon name="chevron-right"/><span aria-current="page">Ask</span>
  </nav>
  <div class="assess-rules">
    <h1>Ask a doubt</h1>
    <c:if test="${similar != null}">
      <section class="card similar" aria-labelledby="sim-title">
        <h2 id="sim-title" class="card-title"><c:out value="${empty similar ? 'No similar answers found' : 'These might already answer it'}"/></h2>
        <c:if test="${not empty similar}">
          <ul class="list">
            <c:forEach var="m" items="${similar}">
              <li class="list-item"><span class="list-icon"><pw:icon name="${m.kind == 'kb' ? 'book-open' : 'circle-check'}"/></span>
                <div class="grow"><div class="title"><a href="<c:url value='${m.kind == "kb" ? "/app/kb/" : "/app/doubts/"}${m.id}'/>"><c:out value="${m.title}"/></a></div>
                  <div class="meta"><c:out value="${m.kind == 'kb' ? 'Knowledge base' : 'Resolved doubt'}"/> · <c:out value="${m.snippet}"/></div></div></li>
            </c:forEach>
          </ul>
        </c:if>
        <p class="legend-note">Still stuck? Post it below.</p>
      </section>
    </c:if>
    <pw:form action="/app/doubts/ask" cssClass="stack-form card">
      <fieldset class="field">
        <legend class="field-label">Who should see it?</legend>
        <label class="check-row"><input type="radio" name="room" value=""<c:if test="${empty f_room}"> checked</c:if>>
          <span><strong>The doubt network.</strong> Everyone signed in can help. You appear as an anonymous asker unless you tick "show my name".</span></label>
        <c:forEach var="r" items="${o.rooms}">
          <label class="check-row"><input type="radio" name="room" value="<c:out value='${r.id}'/>"<c:if test="${f_room == r.id}"> checked</c:if>>
            <span><strong>My teachers in <c:out value="${r.name}"/>.</strong> Private: only you and the room's teachers see it, with your name.</span></label>
        </c:forEach>
      </fieldset>
      <div class="field">
        <label class="field-label" for="d-title">Your question</label>
        <input class="input" id="d-title" name="title" required minlength="8" maxlength="160" value="<c:out value='${f_title}'/>" placeholder="Why does postorder visit the root last?">
      </div>
      <div class="field">
        <label class="field-label" for="d-body">Details <span class="subtle">(what you tried, where you got stuck; Markdown works)</span></label>
        <textarea class="input" id="d-body" name="body" rows="7" required minlength="10" maxlength="8000"><c:out value="${f_body}"/></textarea>
      </div>
      <div class="form-row">
        <div class="field">
          <label class="field-label" for="d-cat">Kind of help</label>
          <select class="input" id="d-cat" name="category">
            <c:forEach var="k" items="${o.categories}"><option value="<c:out value='${k.key}'/>"<c:if test="${k.key == f_category}"> selected</c:if>><c:out value="${k.value}"/></option></c:forEach>
          </select>
        </div>
        <div class="field grow">
          <label class="field-label" for="d-topic">Topic <span class="subtle">(optional)</span></label>
          <select class="input" id="d-topic" name="topic">
            <option value="">Not sure</option>
            <c:forEach var="t" items="${o.topics}"><option value="<c:out value='${t.key}'/>"<c:if test="${t.key == f_topic}"> selected</c:if>><c:out value="${t.title}"/></option></c:forEach>
          </select>
        </div>
      </div>
      <label class="check-row"><input type="checkbox" name="showName" value="1"<c:if test="${f_showName == '1'}"> checked</c:if>><span>Show my name in the doubt network (private doubts always show it to your teachers)</span></label>
      <div class="practice-actions">
        <button type="submit" name="action" value="check" class="btn">Check for similar answers</button>
        <button type="submit" name="action" value="post" class="btn btn-primary">Post</button>
      </div>
    </pw:form>
  </div>
</pw:shell>
