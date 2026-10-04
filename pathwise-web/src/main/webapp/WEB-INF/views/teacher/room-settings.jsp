<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="s" value="${settings}"/>
<pw:shell title="${s.name}: settings">
  <pw:roomhead h="${roomHeader}" section="settings"/>
  <div class="grid grid-main-aside">
    <section class="card" aria-labelledby="set-title">
      <h2 id="set-title" class="card-title">Room settings</h2>
      <c:if test="${s.archived}"><pw:alert kind="info" message="This room is archived and read-only. Restore it to make changes."/></c:if>
      <pw:form action="/teach/rooms/${s.id}/settings" cssClass="stack-form">
        <input type="hidden" name="action" value="save-settings">
        <div class="field">
          <label class="field-label" for="s-name">Name</label>
          <input class="input" id="s-name" name="name" required maxlength="120" value="<c:out value='${s.name}'/>">
        </div>
        <div class="field">
          <label class="field-label" for="s-subject">Subject</label>
          <select class="input" id="s-subject" name="subject">
            <option value="">Any subject</option>
            <c:forEach var="x" items="${s.subjects}"><option value="<c:out value='${x.key}'/>"<c:if test="${x.key == s.subjectKey}"> selected</c:if>><c:out value="${x.title}"/></option></c:forEach>
          </select>
        </div>
        <fieldset class="field">
          <legend class="field-label">How students join</legend>
          <label class="check-row"><input type="radio" name="joinPolicy" value="approval"<c:if test="${s.joinPolicy == 'approval'}"> checked</c:if>><span>With the code, then you approve them</span></label>
          <label class="check-row"><input type="radio" name="joinPolicy" value="open"<c:if test="${s.joinPolicy == 'open'}"> checked</c:if>><span>With the code, straight in</span></label>
          <label class="check-row"><input type="radio" name="joinPolicy" value="invite_only"<c:if test="${s.joinPolicy == 'invite_only'}"> checked</c:if>><span>Only with invite links</span></label>
        </fieldset>
        <label class="check-row"><input type="checkbox" name="classStats" value="1"<c:if test="${s.classStats}"> checked</c:if>>
          <span>Show students the class average progress (only with 5 or more students, and never anyone's individual results)</span></label>
        <div class="field">
          <label class="field-label" for="s-desc">Description</label>
          <textarea class="input" id="s-desc" name="description" rows="3" maxlength="2000"><c:out value="${s.description}"/></textarea>
        </div>
        <button type="submit" class="btn btn-primary"<c:if test="${s.archived}"> disabled</c:if>>Save</button>
      </pw:form>
    </section>
    <div class="stack">
      <section class="card" aria-labelledby="code-title">
        <h2 id="code-title" class="card-title">Join code</h2>
        <p class="room-code-big mono"><c:out value="${s.codeLabel}"/></p>
        <pw:form action="/teach/rooms/${s.id}/settings">
          <button type="submit" name="action" value="new-code" class="btn btn-sm"<c:if test="${s.archived}"> disabled</c:if>>Make a new code</button>
        </pw:form>
        <p class="legend-note">A new code stops the old one working. Students already in the room stay in.</p>
      </section>
      <section class="card" aria-labelledby="arch-title">
        <h2 id="arch-title" class="card-title"><c:out value="${s.archived ? 'Restore' : 'Archive'}"/></h2>
        <p class="muted"><c:out value="${s.archived ? 'Restoring makes the room editable again.' : 'Archiving makes the room read-only for everyone. Nothing is deleted.'}"/></p>
        <pw:form action="/teach/rooms/${s.id}/settings">
          <button type="submit" name="action" value="<c:out value='${s.archived ? "restore" : "archive"}'/>" class="btn btn-sm"><c:out value="${s.archived ? 'Restore room' : 'Archive room'}"/></button>
        </pw:form>
      </section>
    </div>
  </div>
</pw:shell>
