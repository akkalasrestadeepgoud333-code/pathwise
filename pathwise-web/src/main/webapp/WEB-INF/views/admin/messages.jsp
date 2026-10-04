<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Messages">
  <div class="page-header"><div><h1>Messages</h1><p>Send a notice to a group. It appears under each person's bell and in the Android app's notifications, signed with your name. To message one person, open them under People.</p></div></div>

  <section class="card" aria-labelledby="send-title">
    <h2 id="send-title" class="card-title">New message</h2>
    <pw:form action="/admin/messages">
      <fieldset class="field">
        <legend class="field-label">Send to</legend>
        <label class="check-row"><input type="radio" name="audience" value="everyone" checked><span>Everyone</span></label>
        <label class="check-row"><input type="radio" name="audience" value="students"><span>Students (people who aren't teachers or administrators)</span></label>
        <label class="check-row"><input type="radio" name="audience" value="teachers"><span>Teachers</span></label>
        <label class="check-row"><input type="radio" name="audience" value="admins"><span>Administrators</span></label>
      </fieldset>
      <div class="field">
        <label class="field-label" for="g-title">Subject</label>
        <input class="input" id="g-title" name="title" required minlength="3" maxlength="120">
      </div>
      <div class="field">
        <label class="field-label" for="g-body">Message</label>
        <textarea class="input" id="g-body" name="body" rows="5" required maxlength="450"></textarea>
        <p class="legend-note">Up to 450 characters. Suspended and removed accounts don't receive it.</p>
      </div>
      <button type="submit" class="btn btn-primary"><pw:icon name="mail"/>Send</button>
    </pw:form>
  </section>

  <section class="card section-gap" aria-labelledby="sent-title">
    <h2 id="sent-title" class="card-title">Recently sent</h2>
    <c:choose>
      <c:when test="${empty sent}"><p class="muted">Nothing sent yet.</p></c:when>
      <c:otherwise>
        <ul class="plain-list">
          <c:forEach var="m" items="${sent}">
            <li><span class="tabular"><c:out value="${m.atLabel}"/></span> · <c:out value="${m.actorName}"/> → <c:out value="${m.entityKind == 'user' ? 'one person' : m.entityId}"/>
              <span class="muted mono"><c:out value="${m.details}"/></span></li>
          </c:forEach>
        </ul>
      </c:otherwise>
    </c:choose>
  </section>
</pw:shell>
