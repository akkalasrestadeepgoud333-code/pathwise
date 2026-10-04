<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="pp" value="${people}"/>
<c:set var="h" value="${pp.header}"/>
<pw:shell title="${h.name}: people">
  <pw:roomhead h="${h}" section="people"/>

  <c:if test="${not empty inviteLink}">
    <div class="card invite-once" role="status">
      <p class="card-title">Your new invite link</p>
      <p class="muted">Copy it now. Pathwise stores only a fingerprint of it, so it can't be shown again.</p>
      <label class="visually-hidden" for="invite-link">Invite link</label>
      <input class="input mono" id="invite-link" value="<c:out value='${inviteLink}'/>" readonly>
    </div>
  </c:if>

  <c:if test="${not empty pp.pending}">
    <section class="card section-gap" aria-labelledby="pend-title">
      <h2 id="pend-title" class="card-title">Waiting to join (<c:out value="${pp.pending.size()}"/>)</h2>
      <ul class="list">
        <c:forEach var="m" items="${pp.pending}">
          <li class="list-item">
            <div class="grow"><div class="title"><c:out value="${m.name}"/></div><div class="meta"><c:out value="${m.email}"/> · asked <c:out value="${m.joinedLabel}"/></div></div>
            <c:if test="${h.canManageStudents}">
              <pw:form action="/teach/rooms/${h.id}/people" cssClass="inline-form">
                <input type="hidden" name="userId" value="<c:out value='${m.userId}'/>">
                <button type="submit" name="action" value="approve" class="btn btn-sm btn-primary">Approve</button>
                <button type="submit" name="action" value="reject" class="btn btn-sm btn-ghost">Decline</button>
              </pw:form>
            </c:if>
          </li>
        </c:forEach>
      </ul>
    </section>
  </c:if>

  <div class="grid grid-main-aside section-gap">
    <section class="card" aria-labelledby="students-title">
      <h2 id="students-title" class="card-title">Students (<c:out value="${pp.students.size()}"/>)</h2>
      <c:choose>
        <c:when test="${empty pp.students}"><p class="muted">No students yet. Share the join code <strong class="mono"><c:out value="${h.codeLabel}"/></strong> or an invite link.</p></c:when>
        <c:otherwise>
          <ul class="list">
            <c:forEach var="m" items="${pp.students}">
              <li class="list-item">
                <span class="avatar avatar-sm" aria-hidden="true"><c:out value="${m.name.substring(0, 1)}"/></span>
                <div class="grow"><div class="title"><c:out value="${m.name}"/></div><div class="meta"><c:out value="${m.email}"/> · joined <c:out value="${m.joinedLabel}"/> · last active <c:out value="${m.lastActiveLabel}"/></div></div>
                <c:if test="${h.canManageStudents}">
                  <pw:form action="/teach/rooms/${h.id}/people" cssClass="inline-form">
                    <input type="hidden" name="userId" value="<c:out value='${m.userId}'/>">
                    <button type="submit" name="action" value="remove" class="btn btn-sm btn-ghost">Remove</button>
                  </pw:form>
                </c:if>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>

    <div class="stack">
      <section class="card" aria-labelledby="teachers-title">
        <h2 id="teachers-title" class="card-title">Teachers</h2>
        <ul class="list">
          <c:forEach var="m" items="${pp.teachers}">
            <li class="list-item teacher-item">
              <div class="grow"><div class="title"><c:out value="${m.name}"/></div><div class="meta"><c:out value="${m.roleLabel}"/> · <c:out value="${m.email}"/></div>
                <c:if test="${m.role == 'co_teacher' and h.canManageCoTeachers}">
                  <pw:form action="/teach/rooms/${h.id}/people" cssClass="perm-form">
                    <input type="hidden" name="userId" value="<c:out value='${m.userId}'/>">
                    <fieldset>
                      <legend class="visually-hidden">Permissions for <c:out value="${m.name}"/></legend>
                      <label class="check-row"><input type="checkbox" name="author" value="1"<c:if test="${m.perms.author}"> checked</c:if>><span>Edit curriculum and drafts</span></label>
                      <label class="check-row"><input type="checkbox" name="publish" value="1"<c:if test="${m.perms.publish}"> checked</c:if>><span>Publish</span></label>
                      <label class="check-row"><input type="checkbox" name="grade" value="1"<c:if test="${m.perms.grade}"> checked</c:if>><span>Grade and release results</span></label>
                      <label class="check-row"><input type="checkbox" name="moderate" value="1"<c:if test="${m.perms.moderate}"> checked</c:if>><span>Moderate discussion</span></label>
                      <label class="check-row"><input type="checkbox" name="analytics" value="1"<c:if test="${m.perms.analytics}"> checked</c:if>><span>See individual students' data</span></label>
                    </fieldset>
                    <div class="practice-actions">
                      <button type="submit" name="action" value="perms" class="btn btn-sm">Save</button>
                      <button type="submit" name="action" value="remove" class="btn btn-sm btn-ghost">Remove</button>
                    </div>
                  </pw:form>
                </c:if>
              </div>
            </li>
          </c:forEach>
        </ul>
      </section>

      <c:if test="${h.canManageStudents}">
        <section class="card" aria-labelledby="invite-title">
          <h2 id="invite-title" class="card-title">Invite links</h2>
          <p class="muted">A link admits people straight in, without approval, until it expires or is used up.</p>
          <pw:form action="/teach/rooms/${h.id}/people" cssClass="stack-form">
            <input type="hidden" name="action" value="invite">
            <div class="form-row">
              <div class="field">
                <label class="field-label" for="inv-role">For</label>
                <select class="input" id="inv-role" name="role">
                  <option value="student">Students</option>
                  <c:if test="${h.canManageCoTeachers}"><option value="co_teacher">A co-teacher</option></c:if>
                </select>
              </div>
              <div class="field">
                <label class="field-label" for="inv-uses">Uses</label>
                <input class="input" id="inv-uses" name="maxUses" type="number" min="1" max="1000" value="40">
              </div>
              <div class="field">
                <label class="field-label" for="inv-days">Days</label>
                <input class="input" id="inv-days" name="days" type="number" min="1" max="30" value="14">
              </div>
            </div>
            <button type="submit" class="btn btn-sm btn-primary">Create link</button>
          </pw:form>
          <c:if test="${not empty pp.invites}">
            <ul class="list section-gap-sm">
              <c:forEach var="i" items="${pp.invites}">
                <li class="list-item">
                  <div class="grow"><div class="title"><c:out value="${i.roleLabel}"/> link</div>
                    <div class="meta"><c:out value="${i.usesLabel}"/> · until <c:out value="${i.expiresLabel}"/><c:if test="${not i.active}"> · inactive</c:if></div></div>
                  <c:if test="${i.active}">
                    <pw:form action="/teach/rooms/${h.id}/people" cssClass="inline-form">
                      <input type="hidden" name="inviteId" value="<c:out value='${i.id}'/>">
                      <button type="submit" name="action" value="revoke" class="btn btn-sm btn-ghost">Turn off</button>
                    </pw:form>
                  </c:if>
                </li>
              </c:forEach>
            </ul>
          </c:if>
        </section>
      </c:if>
    </div>
  </div>
</pw:shell>
