<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Practice">
  <div class="page-header">
    <div>
      <h1>Practice</h1>
      <p>Short sets pitched at your level. Each answer helps Pathwise understand where you are. Practice is never graded.</p>
    </div>
  </div>

  <section class="hero-card" aria-labelledby="quick-title">
    <p class="card-eyebrow">Quick practice</p>
    <h2 id="quick-title">Five questions on what needs you most</h2>
    <p>Pathwise picks the skills you're finding hardest, or what you studied last, and pitches questions near your current level.</p>
    <pw:form action="/app/practice/start">
      <input type="hidden" name="mode" value="quick">
      <button type="submit" class="btn btn-primary btn-lg">Start quick practice <pw:icon name="arrow-right"/></button>
    </pw:form>
    <pw:trail cssClass="hero-trail" onInk="true"/>
  </section>

  <a class="card card-interactive game-card section-gap" href="<c:url value='/app/games'/>">
    <span class="game-card-icon" aria-hidden="true"><pw:icon name="gamepad-2"/></span>
    <div class="grow">
      <h2 class="card-title">Games</h2>
      <p class="muted">Traversal Race, Match Pairs and Bug Hunt. Short, untimed, and they count as light practice.</p>
    </div>
    <pw:icon name="arrow-right"/>
  </a>

  <div class="grid grid-2 section-gap">
    <section class="card" aria-labelledby="focus-title">
      <h2 id="focus-title" class="card-title">Worth strengthening</h2>
      <c:choose>
        <c:when test="${empty focus}">
          <p class="muted">Nothing here yet. Answer a module check or a practice set, and skills that need work will appear here.</p>
        </c:when>
        <c:otherwise>
          <ul class="list">
            <c:forEach var="s" items="${focus}" end="7">
              <li class="list-item">
                <div class="grow"><div class="title"><c:out value="${s.title}"/></div>
                  <div class="meta"><c:out value="${s.topicTitle}"/> · from <c:out value="${s.evidence}"/> answer<c:if test="${s.evidence != 1}">s</c:if></div></div>
                <span class="state-chip state-<c:out value='${s.state}'/>"><c:out value="${s.stateLabel}"/></span>
                <c:if test="${s.practicable}">
                  <pw:form action="/app/practice/start">
                    <input type="hidden" name="mode" value="practice"><input type="hidden" name="skill" value="<c:out value='${s.id}'/>">
                    <button type="submit" class="btn btn-sm">Practise</button>
                  </pw:form>
                </c:if>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>

    <section class="card" aria-labelledby="strong-title">
      <h2 id="strong-title" class="card-title">Ready for a challenge</h2>
      <c:choose>
        <c:when test="${empty strong}">
          <p class="muted">Skills you reach Proficient in will show up here, with harder questions to prove mastery.</p>
        </c:when>
        <c:otherwise>
          <ul class="list">
            <c:forEach var="s" items="${strong}" end="7">
              <li class="list-item">
                <div class="grow"><div class="title"><c:out value="${s.title}"/></div><div class="meta"><c:out value="${s.topicTitle}"/></div></div>
                <span class="state-chip state-<c:out value='${s.state}'/>"><c:out value="${s.stateLabel}"/></span>
                <c:if test="${s.challengeable}">
                  <pw:form action="/app/practice/start">
                    <input type="hidden" name="mode" value="challenge"><input type="hidden" name="skill" value="<c:out value='${s.id}'/>">
                    <button type="submit" class="btn btn-sm">Challenge</button>
                  </pw:form>
                </c:if>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
    </section>
  </div>
</pw:shell>
