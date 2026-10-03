<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="cont" value="${today.continueCard}"/>
<c:set var="sug" value="${today.suggestion}"/>
<pw:shell title="Today">
  <div class="page-header">
    <div>
      <h1><c:out value="${greeting}"/>, <c:out value="${currentUser.firstName}"/>.</h1>
      <p>Here's where you are, and a good next step.</p>
    </div>
  </div>

  <div class="grid grid-main-aside">
    <c:choose>
      <c:when test="${not empty cont and not cont.completed}">
        <section class="hero-card" aria-labelledby="next-title">
          <p class="card-eyebrow">Continue where you left off</p>
          <h2 id="next-title"><c:out value="${cont.title}"/></h2>
          <p><c:out value="${cont.subjectTitle}"/> · <c:out value="${cont.topicTitle}"/> · <c:out value="${cont.percent}"/>% through</p>
          <a class="btn btn-primary btn-lg" href="<c:url value='/app/modules/${cont.moduleKey}'/>">Resume <pw:icon name="arrow-right"/></a>
          <pw:trail cssClass="hero-trail" onInk="true"/>
        </section>
      </c:when>
      <c:when test="${not empty sug}">
        <section class="hero-card" aria-labelledby="next-title">
          <p class="card-eyebrow">Suggested next step</p>
          <h2 id="next-title"><c:out value="${sug.title}"/></h2>
          <p><span class="chip chip-accent"><pw:icon name="circle-help" size="sm"/>Why this?</span> <c:out value="${sug.reason}"/></p>
          <a class="btn btn-primary btn-lg" href="<c:url value='/app/modules/${sug.moduleKey}'/>">Start <pw:icon name="arrow-right"/></a>
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
        <c:if test="${not empty cont and cont.completed}">
          <li class="list-item">
            <span class="list-icon"><pw:icon name="circle-check"/></span>
            <div class="grow"><div class="title">Last studied: <c:out value="${cont.title}"/></div><div class="meta"><a href="<c:url value='/app/modules/${cont.moduleKey}'/>">Review it</a></div></div>
          </li>
        </c:if>
        <li class="list-item">
          <span class="list-icon"><pw:icon name="laptop"/></span>
          <div class="grow"><div class="title">Your devices</div><div class="meta"><a href="<c:url value='/app/settings#sessions'/>">See where you're signed in</a></div></div>
        </li>
      </ul>
    </section>
  </div>

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
