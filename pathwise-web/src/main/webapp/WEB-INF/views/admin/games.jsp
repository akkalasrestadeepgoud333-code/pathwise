<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Games">
  <div class="page-header"><div><h1>Game templates</h1><p>Switching a template off hides its games from the gallery and stops new plays. Finished games and their evidence are kept.</p></div></div>

  <div class="card table-wrap">
    <table class="table">
      <thead><tr><th scope="col">Template</th><th scope="col">Engine</th><th scope="col">Games</th><th scope="col">Verified plays</th><th scope="col">Status</th></tr></thead>
      <tbody>
        <c:forEach var="t" items="${templates}">
          <tr>
            <th scope="row"><c:out value="${t.title}"/> <span class="meta mono"><c:out value="${t.key}"/></span></th>
            <td class="tabular">v<c:out value="${t.engineVersion}"/></td>
            <td class="tabular"><c:out value="${t.games}"/></td>
            <td class="tabular"><c:out value="${t.plays}"/></td>
            <td>
              <pw:form action="/admin/games" cssClass="form-row">
                <input type="hidden" name="key" value="<c:out value='${t.key}'/>">
                <c:choose>
                  <c:when test="${t.enabled}"><span class="chip chip-success">On</span> <button type="submit" class="btn btn-sm">Switch off</button></c:when>
                  <c:otherwise><input type="hidden" name="enabled" value="on"><span class="chip">Off</span> <button type="submit" class="btn btn-sm">Switch on</button></c:otherwise>
                </c:choose>
              </pw:form>
            </td>
          </tr>
        </c:forEach>
      </tbody>
    </table>
  </div>
</pw:shell>
