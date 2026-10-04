<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="People">
  <div class="page-header"><div><h1>People</h1><p>Search by name or email. Learning data stays on each person's own pages; this is for account care.</p></div></div>

  <form method="get" action="<c:url value='/admin/users'/>" class="form-row section-gap-sm" role="search">
    <input class="input grow" type="search" name="q" value="<c:out value='${q}'/>" maxlength="100" placeholder="Name or email" aria-label="Search people">
    <button type="submit" class="btn btn-sm">Search</button>
  </form>

  <c:choose>
    <c:when test="${empty people}"><pw:empty icon="users" title="No one matches" message="Try part of a name or an email address."/></c:when>
    <c:otherwise>
      <div class="card table-wrap">
        <table class="table">
          <thead><tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Roles</th><th scope="col">Status</th><th scope="col">Joined</th></tr></thead>
          <tbody>
            <c:forEach var="p" items="${people}">
              <tr>
                <th scope="row"><a href="<c:url value='/admin/users/${p.id}'/>"><c:out value="${p.displayName}"/></a>
                  <c:if test="${p.verifiedEducator}"> <span class="chip chip-success">Verified Educator</span></c:if></th>
                <td class="mono"><c:out value="${p.email}"/></td>
                <td><c:out value="${p.rolesLabel}"/></td>
                <td><span class="chip<c:if test="${p.status == 'suspended'}"> chip-danger</c:if>"><c:out value="${p.status}"/></span></td>
                <td class="tabular"><c:out value="${p.createdLabel}"/></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
      <p class="legend-note">Showing up to 50, newest first.</p>
    </c:otherwise>
  </c:choose>
</pw:shell>
