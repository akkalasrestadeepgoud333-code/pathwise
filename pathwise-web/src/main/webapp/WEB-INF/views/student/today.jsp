<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Today">
  <div class="page-header">
    <div>
      <h1><c:out value="${greeting}"/>, <c:out value="${currentUser.firstName}"/>.</h1>
      <p>Here's a good next step and why. You're always free to choose something else.</p>
    </div>
  </div>

  <div class="card rewards-strip" aria-label="Your progress">
    <a href="<c:url value='/app/achievements'/>"><pw:icon name="trophy"/>Level <c:out value="${rewards.level}"/> · <c:out value="${rewards.xp}"/> XP</a>
    <a href="<c:url value='/app/achievements'/>"><pw:icon name="flame"/><c:out value="${rewards.streak}"/>-day streak</a>
    <c:if test="${not empty rewards.nextBadge}"><span class="meta">Next badge: <c:out value="${rewards.nextBadge}"/> (<c:out value="${rewards.nextBadgePercent}"/>%)</span></c:if>
  </div>

  <div class="grid grid-main-aside">
    <c:choose>
      <c:when test="${not empty top}">
        <section class="hero-card hero-rec" aria-label="Your next step">
          <p class="card-eyebrow">Your next step</p>
          <pw:rec rec="${top}" from="today" hero="true"/>
          <pw:trail cssClass="hero-trail" onInk="true"/>
        </section>
      </c:when>
      <c:otherwise>
        <section class="hero-card" aria-labelledby="next-title">
          <p class="card-eyebrow">Your next step</p>
          <h2 id="next-title">Your learning path starts once a subject is published.</h2>
          <p>When subjects are available, this card suggests one thing to do next, with a plain reason. You can always choose something else.</p>
          <pw:trail cssClass="hero-trail" onInk="true"/>
        </section>
      </c:otherwise>
    </c:choose>

    <section class="card" aria-labelledby="choose-title">
      <p class="card-eyebrow">Your choice, always</p>
      <h2 id="choose-title" class="card-title">Explore on your own</h2>
      <ul class="list">
        <li class="list-item">
          <span class="list-icon"><pw:icon name="map"/></span>
          <div class="grow"><div class="title"><a href="<c:url value='/app/learn'/>">Browse subjects</a></div><div class="meta">Open a Learning Map and start anywhere</div></div>
        </li>
        <li class="list-item">
          <span class="list-icon"><pw:icon name="target"/></span>
          <div class="grow"><div class="title"><a href="<c:url value='/app/practice'/>">Quick practice</a></div><div class="meta">Five questions at your level</div></div>
        </li>
        <li class="list-item">
          <span class="list-icon"><pw:icon name="clipboard-check"/></span>
          <div class="grow"><div class="title"><a href="<c:url value='${assessmentPrompt.href}'/>"><c:out value="${assessmentPrompt.title}"/></a></div><div class="meta"><c:out value="${assessmentPrompt.meta}"/></div></div>
        </li>
        <li class="list-item">
          <span class="list-icon"><pw:icon name="activity"/></span>
          <div class="grow"><div class="title"><a href="<c:url value='/app/progress'/>">Your progress</a></div><div class="meta">How each skill is going</div></div>
        </li>
      </ul>
    </section>
  </div>

  <pw:roomnext rooms="${roomNext}"/>

  <c:if test="${not empty more}">
    <div class="section-head section-gap">
      <h2>More next steps</h2>
      <a href="<c:url value='/app/next'/>">See all and set goals →</a>
    </div>
    <div class="grid grid-2">
      <c:forEach var="r" items="${more}" end="3"><div class="card"><pw:rec rec="${r}" from="today"/></div></c:forEach>
    </div>
  </c:if>

  <c:choose>
    <c:when test="${empty today.subjects}">
      <div class="section-gap">
        <pw:empty title="No subjects yet" icon="map"
                  message="Subjects, your learning map and practice will appear here once your teacher or an administrator publishes them."/>
      </div>
    </c:when>
    <c:otherwise>
      <h2 class="section-gap">Your subjects</h2>
      <div class="grid grid-2">
        <c:forEach var="s" items="${today.subjects}">
          <a class="card card-interactive subject-card" href="<c:url value='/app/map/${s.key}'/>">
            <div><p class="card-eyebrow"><c:out value="${s.course}"/></p><h2><c:out value="${s.title}"/></h2></div>
            <div class="subject-meta">
              <span><pw:icon name="circle-check" size="sm"/><c:out value="${s.modulesDone}"/> of <c:out value="${s.modules}"/> modules studied</span>
            </div>
            <div class="progress" role="progressbar" aria-label="Modules studied" aria-valuemin="0" aria-valuemax="100" aria-valuenow="<c:out value='${s.percent}'/>">
              <span class="w-<c:out value='${s.percent - s.percent % 5}'/>"></span>
            </div>
          </a>
        </c:forEach>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
