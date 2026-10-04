<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Promote to the knowledge base">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/admin/moderation'/>">Moderation</a><pw:icon name="chevron-right"/><span aria-current="page">Promote</span>
  </nav>
  <h1>Promote to the knowledge base</h1>
  <p class="muted">Edit it into a clear explanation. Remove anything personal: articles are public to everyone signed in.</p>
  <pw:form action="/admin/moderation" cssClass="stack-form card">
    <input type="hidden" name="action" value="publish-article">
    <input type="hidden" name="threadId" value="<c:out value='${threadId}'/>">
    <div class="field"><label class="field-label" for="k-title">Title</label>
      <input class="input" id="k-title" name="title" required maxlength="160" value="<c:out value='${draft.title}'/>"></div>
    <div class="field"><label class="field-label" for="k-topic">Topic</label>
      <select class="input" id="k-topic" name="topic"><option value="">None</option>
        <c:forEach var="t" items="${options.topics}"><option value="<c:out value='${t.key}'/>"<c:if test="${t.key == draft.topic}"> selected</c:if>><c:out value="${t.title}"/></option></c:forEach>
      </select></div>
    <div class="field"><label class="field-label" for="k-body">Article (Markdown)</label>
      <textarea class="input" id="k-body" name="body" rows="14" required maxlength="20000"><c:out value="${draft.body}"/></textarea></div>
    <button type="submit" class="btn btn-primary">Publish</button>
  </pw:form>
</pw:shell>
