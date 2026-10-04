<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Teaching">
  <div class="page-header">
    <div>
      <h1>Teaching</h1>
      <p>Rooms are your classes. Students join with a code, you set the curriculum, and each student moves through it on their own path.</p>
    </div>
  </div>

  <div class="grid grid-main-aside">
    <section aria-labelledby="rooms-title">
      <h2 id="rooms-title" class="visually-hidden">Your rooms</h2>
      <c:choose>
        <c:when test="${empty rooms.teaching}">
          <pw:empty title="No rooms yet" icon="school" message="Create your first room. You'll get a join code to share with your class."/>
        </c:when>
        <c:otherwise>
          <div class="grid">
            <c:forEach var="r" items="${rooms.teaching}">
              <a class="card card-interactive room-card" href="<c:url value='/teach/rooms/${r.id}'/>">
                <span class="game-card-icon" aria-hidden="true"><pw:icon name="school"/></span>
                <div class="grow">
                  <p class="card-eyebrow"><c:out value="${r.roleLabel}"/><c:if test="${r.archived}"> · Archived</c:if></p>
                  <h3 class="card-title"><c:out value="${r.name}"/></h3>
                  <p class="meta"><c:out value="${r.students}"/> student<c:if test="${r.students != 1}">s</c:if> · code <span class="mono"><c:out value="${r.codeLabel}"/></span></p>
                </div>
                <pw:icon name="arrow-right"/>
              </a>
            </c:forEach>
          </div>
        </c:otherwise>
      </c:choose>
    </section>

    <section class="card" aria-labelledby="create-title">
      <h2 id="create-title" class="card-title">Create a room</h2>
      <pw:form action="/teach" cssClass="stack-form">
        <div class="field">
          <label class="field-label" for="r-name">Name</label>
          <input class="input" id="r-name" name="name" required maxlength="120" placeholder="DSA – Section A">
        </div>
        <div class="field">
          <label class="field-label" for="r-subject">Subject</label>
          <select class="input" id="r-subject" name="subject">
            <option value="">Any subject</option>
            <c:forEach var="s" items="${subjects}"><option value="<c:out value='${s.key}'/>"><c:out value="${s.title}"/></option></c:forEach>
          </select>
        </div>
        <fieldset class="field">
          <legend class="field-label">How students join</legend>
          <label class="check-row"><input type="radio" name="joinPolicy" value="approval" checked><span>With the code, then you approve them</span></label>
          <label class="check-row"><input type="radio" name="joinPolicy" value="open"><span>With the code, straight in</span></label>
          <label class="check-row"><input type="radio" name="joinPolicy" value="invite_only"><span>Only with invite links you send</span></label>
        </fieldset>
        <div class="field">
          <label class="field-label" for="r-desc">Description <span class="subtle">(optional)</span></label>
          <textarea class="input" id="r-desc" name="description" rows="3" maxlength="2000"></textarea>
        </div>
        <button type="submit" class="btn btn-primary">Create room</button>
      </pw:form>
    </section>
  </div>
</pw:shell>
