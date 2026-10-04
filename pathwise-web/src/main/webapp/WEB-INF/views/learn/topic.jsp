<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="t" value="${topic}"/>
<pw:shell title="${t.title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/learn'/>">Learn</a><pw:icon name="chevron-right"/>
    <a href="<c:url value='/app/map/${t.subjectKey}'/>"><c:out value="${t.subjectTitle}"/></a><pw:icon name="chevron-right"/>
    <c:if test="${not empty t.parentKey}">
      <a href="<c:url value='/app/topics/${t.subjectKey}/${t.parentKey}'/>"><c:out value="${t.parentTitle}"/></a><pw:icon name="chevron-right"/>
    </c:if>
    <span aria-current="page"><c:out value="${t.title}"/></span>
  </nav>

  <header class="topic-hero">
    <div class="topic-tags">
      <span class="state-chip state-<c:out value='${t.coverage}'/>"><c:if test="${t.coverage == 'studied'}"><pw:icon name="check"/></c:if><c:out value="${t.coverageLabel}"/></span>
      <c:if test="${t.optional}"><span class="chip">Optional</span></c:if>
      <c:if test="${t.advanced}"><span class="chip chip-accent">Advanced</span></c:if>
    </div>
    <h1><c:out value="${t.title}"/></h1>
    <c:if test="${not empty t.summary}"><p class="lead"><c:out value="${t.summary}"/></p></c:if>
    <c:if test="${not empty t.startModuleKey}">
      <div><a class="btn btn-primary btn-lg" href="<c:url value='/app/modules/${t.startModuleKey}'/>">
        <c:choose><c:when test="${t.coverage == 'in-progress'}">Continue learning</c:when><c:when test="${t.coverage == 'studied'}">Review this topic</c:when><c:otherwise>Start learning</c:otherwise></c:choose>
        <pw:icon name="arrow-right"/></a></div>
    </c:if>
  </header>

  <c:if test="${not empty t.buildsOn and t.coverage != 'studied'}">
    <div class="alert alert-info" role="note">
      <pw:icon name="info"/>
      <p>This topic builds on <strong><c:forEach var="b" items="${t.buildsOn}" varStatus="st"><c:out value="${b}"/><c:if test="${not st.last}">, </c:if></c:forEach></strong>, which you haven't studied yet. You can still start here. Studying those first may make it easier.</p>
    </div>
  </c:if>

  <div class="grid grid-main-aside">
    <div class="grid">
      <section class="card" aria-labelledby="modules-title">
        <h2 id="modules-title" class="card-title">Modules</h2>
        <c:choose>
          <c:when test="${empty t.modules}">
            <p class="muted">Learning material for this topic is coming soon.</p>
          </c:when>
          <c:otherwise>
            <c:forEach var="m" items="${t.modules}">
              <div class="module-row">
                <span class="list-icon"><pw:icon name="${m.state == 'done' ? 'circle-check' : 'book-open'}"/></span>
                <div class="grow">
                  <h3><a href="<c:url value='/app/modules/${m.key}'/>"><c:out value="${m.title}"/></a></h3>
                  <p><c:out value="${m.summary}"/></p>
                  <p class="subtle">≈ <c:out value="${m.estMinutes}"/> min ·
                    <span class="difficulty" aria-label="Difficulty <c:out value='${m.difficulty}'/> of 5"><c:forEach begin="1" end="5" var="i"><i class="<c:if test='${i <= m.difficulty}'>on</c:if>"></i></c:forEach></span>
                  </p>
                </div>
                <c:choose>
                  <c:when test="${m.state == 'done'}"><span class="state-chip state-studied">Studied</span></c:when>
                  <c:when test="${m.state == 'started'}"><span class="state-chip state-in-progress">In progress</span></c:when>
                </c:choose>
              </div>
            </c:forEach>
          </c:otherwise>
        </c:choose>
      </section>

      <c:if test="${not empty t.subtopics}">
        <section class="card" aria-labelledby="sub-title">
          <h2 id="sub-title" class="card-title">Inside this topic</h2>
          <ul class="list">
            <c:forEach var="x" items="${t.subtopics}">
              <li class="list-item">
                <span class="list-icon"><pw:icon name="${x.met ? 'circle-check' : 'route'}"/></span>
                <div class="grow"><a class="title" href="<c:url value='/app/topics/${t.subjectKey}/${x.key}'/>"><c:out value="${x.title}"/></a></div>
                <span class="meta"><c:out value="${x.coverageLabel}"/></span>
              </li>
            </c:forEach>
          </ul>
        </section>
      </c:if>
    </div>

    <aside class="grid">
      <section class="card" aria-labelledby="skills-title">
        <h2 id="skills-title" class="card-title">Skills and your levels</h2>
        <ul class="list">
          <c:forEach var="k" items="${t.skills}">
            <li class="list-item skill-item">
              <div class="grow"><div class="title"><c:out value="${k.title}"/></div>
                <div class="meta"><span class="state-chip state-<c:out value='${k.state}'/>"><c:out value="${k.stateLabel}"/></span>
                  <c:if test="${k.evidence > 0}"> from <c:out value="${k.evidence}"/> answer<c:if test="${k.evidence != 1}">s</c:if></c:if></div></div>
              <c:if test="${k.practicable}">
                <pw:form action="/app/practice/start">
                  <input type="hidden" name="mode" value="practice"><input type="hidden" name="skill" value="<c:out value='${k.id}'/>">
                  <button type="submit" class="btn btn-sm">Practise</button>
                </pw:form>
              </c:if>
            </li>
          </c:forEach>
        </ul>
      </section>

      <section class="card" aria-labelledby="before-title">
        <h2 id="before-title" class="card-title">Builds on</h2>
        <c:choose>
          <c:when test="${empty t.prerequisites}"><p class="muted">Nothing. This is a starting point.</p></c:when>
          <c:otherwise>
            <ul class="list">
              <c:forEach var="p" items="${t.prerequisites}">
                <li class="list-item">
                  <span class="list-icon"><pw:icon name="${p.met ? 'circle-check' : 'lock'}"/></span>
                  <div class="grow"><a class="title" href="<c:url value='/app/topics/${t.subjectKey}/${p.key}'/>"><c:out value="${p.title}"/></a>
                    <div class="meta"><c:out value="${p.strength == 'required' ? 'Needed first' : 'Helpful first'}"/> · <c:out value="${p.coverageLabel}"/></div></div>
                </li>
              </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </section>

      <c:if test="${not empty t.unlocks}">
        <section class="card" aria-labelledby="next-title">
          <h2 id="next-title" class="card-title">Leads to</h2>
          <ul class="list">
            <c:forEach var="u" items="${t.unlocks}">
              <li class="list-item">
                <span class="list-icon"><pw:icon name="arrow-right"/></span>
                <div class="grow"><a class="title" href="<c:url value='/app/topics/${t.subjectKey}/${u.key}'/>"><c:out value="${u.title}"/></a></div>
              </li>
            </c:forEach>
          </ul>
        </section>
      </c:if>
    </aside>
  </div>
</pw:shell>
