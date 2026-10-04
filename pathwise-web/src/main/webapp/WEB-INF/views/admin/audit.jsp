<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Audit log">
  <div class="page-header"><div><h1>Audit log</h1><p>Every sign-in, permission change, moderation decision and admin action. The log is append-only: the database refuses edits and deletions.</p></div></div>

  <form method="get" action="<c:url value='/admin/audit'/>" class="form-row section-gap-sm" role="search">
    <label class="field-label" for="action">Action starts with</label>
    <input class="input" id="action" name="action" value="<c:out value='${action}'/>" maxlength="60" placeholder="e.g. admin.">
    <button type="submit" class="btn btn-sm">Filter</button>
  </form>

  <c:choose>
    <c:when test="${empty entries}"><pw:empty icon="file-text" title="Nothing matches" message="Try a shorter prefix, such as identity."/></c:when>
    <c:otherwise>
      <div class="card table-wrap">
        <table class="table">
          <thead><tr><th scope="col">#</th><th scope="col">When (IST)</th><th scope="col">Who</th><th scope="col">Action</th><th scope="col">Entity</th><th scope="col">Details</th></tr></thead>
          <tbody>
            <c:forEach var="e" items="${entries}">
              <tr>
                <td class="tabular"><c:out value="${e.id}"/></td>
                <td class="tabular"><c:out value="${e.atLabel}"/></td>
                <td><c:out value="${empty e.actorName ? 'System' : e.actorName}"/></td>
                <td class="mono"><c:out value="${e.action}"/></td>
                <td><c:out value="${e.entityKind}"/> <span class="meta mono"><c:out value="${e.entityId}"/></span></td>
                <td class="audit-details mono"><c:out value="${e.details}"/></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
      <c:if test="${not empty nextBefore}">
        <p class="section-gap-sm"><a class="btn btn-sm" href="<c:url value='/admin/audit'><c:param name='action' value='${action}'/><c:param name='before' value='${nextBefore}'/></c:url>">Older entries</a></p>
      </c:if>
    </c:otherwise>
  </c:choose>
</pw:shell>
