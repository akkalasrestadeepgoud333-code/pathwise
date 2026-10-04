<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Rooms">
  <div class="page-header"><div><h1>Rooms</h1><p>Names, codes and head counts only. Administrators don't see a room's people, work or results from here.</p></div></div>

  <form method="get" action="<c:url value='/admin/rooms'/>" class="form-row section-gap-sm" role="search">
    <input class="input grow" type="search" name="q" value="<c:out value='${q}'/>" maxlength="100" placeholder="Room name or code" aria-label="Search rooms">
    <button type="submit" class="btn btn-sm">Search</button>
  </form>

  <c:choose>
    <c:when test="${empty rooms}"><pw:empty icon="school" title="No rooms" message="Rooms appear here when teachers create them."/></c:when>
    <c:otherwise>
      <div class="card table-wrap">
        <table class="table">
          <thead><tr><th scope="col">Room</th><th scope="col">Code</th><th scope="col">Owner</th><th scope="col">Students</th><th scope="col">Teachers</th><th scope="col">Created</th><th scope="col">Archive</th></tr></thead>
          <tbody>
            <c:forEach var="r" items="${rooms}">
              <tr>
                <th scope="row"><c:out value="${r.name}"/><c:if test="${r.archived}"> <span class="chip">archived</span></c:if></th>
                <td class="mono"><c:out value="${r.code}"/></td>
                <td><c:out value="${r.ownerName}"/></td>
                <td class="tabular"><c:out value="${r.students}"/></td>
                <td class="tabular"><c:out value="${r.teachers}"/></td>
                <td class="tabular"><c:out value="${r.createdLabel}"/></td>
                <td>
                  <pw:form action="/admin/rooms" cssClass="form-row">
                    <input type="hidden" name="roomId" value="<c:out value='${r.id}'/>">
                    <input type="hidden" name="action" value="<c:out value='${r.archived ? "restore" : "archive"}'/>">
                    <input class="input" name="reason" required minlength="5" maxlength="300" placeholder="Reason" aria-label="Reason for <c:out value='${r.archived ? "restoring" : "archiving"}'/> <c:out value='${r.name}'/>">
                    <button type="submit" class="btn btn-sm"><c:out value="${r.archived ? 'Restore' : 'Archive'}"/></button>
                  </pw:form>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
