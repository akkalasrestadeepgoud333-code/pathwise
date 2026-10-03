<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="p" value="${player}"/>
<c:set var="sec" value="${p.section}"/>
<pw:shell title="${p.moduleTitle}" script="/static/js/visuals/visuals.js">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/learn'/>">Learn</a><pw:icon name="chevron-right"/>
    <a href="<c:url value='/app/map/${p.subjectKey}'/>"><c:out value="${p.subjectTitle}"/></a><pw:icon name="chevron-right"/>
    <a href="<c:url value='/app/topics/${p.subjectKey}/${p.topicKey}'/>"><c:out value="${p.topicTitle}"/></a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${p.moduleTitle}"/></span>
  </nav>

  <header class="player-head">
    <h1><c:out value="${p.moduleTitle}"/></h1>
    <div class="player-meta">
      <span><pw:icon name="book-open" size="sm"/>Section <c:out value="${p.index}"/> of <c:out value="${p.total}"/></span>
      <span><pw:icon name="activity" size="sm"/>≈ <c:out value="${p.estMinutes}"/> min</span>
      <span><c:out value="${p.doneCount}"/> done</span>
    </div>
    <div class="progress<c:if test='${p.moduleCompleted}'> progress-success</c:if>" role="progressbar" aria-label="Module progress"
         aria-valuemin="0" aria-valuemax="100" aria-valuenow="<c:out value='${p.percent}'/>">
      <span class="w-<c:out value='${p.percent - p.percent % 5}'/>"></span>
    </div>
  </header>

  <c:if test="${justCompleted}">
    <div class="complete-banner" role="status">
      <span class="list-icon"><pw:icon name="award"/></span>
      <div>
        <h2>Module complete: you've studied <c:out value="${p.moduleTitle}"/></h2>
        <p>Nice work. Practice questions that measure what you've learned are coming next in Pathwise.</p>
      </div>
      <a class="btn" href="<c:url value='/app/topics/${p.subjectKey}/${p.topicKey}'/>">Back to topic</a>
    </div>
  </c:if>

  <details class="outline-mobile">
    <summary>Contents · <c:out value="${p.doneCount}"/>/<c:out value="${p.total}"/> done <pw:icon name="chevron-right" size="sm"/></summary>
    <ol class="outline-list">
      <c:forEach var="o" items="${p.outline}" varStatus="st">
        <li class="<c:if test='${o.done}'>done</c:if>"><a href="<c:url value='/app/modules/${p.moduleKey}/${o.key}'/>"<c:if test="${o.current}"> aria-current="step"</c:if>>
          <span class="outline-dot"><c:choose><c:when test="${o.done}"><pw:icon name="check"/></c:when><c:otherwise><c:out value="${st.count}"/></c:otherwise></c:choose></span><c:out value="${o.title}"/></a></li>
      </c:forEach>
    </ol>
  </details>

  <div class="player">
    <aside class="player-outline" aria-label="Module contents">
      <div class="card">
        <p class="card-eyebrow">In this module</p>
        <ol class="outline-list">
          <c:forEach var="o" items="${p.outline}" varStatus="st">
            <li class="<c:if test='${o.done}'>done</c:if>"><a href="<c:url value='/app/modules/${p.moduleKey}/${o.key}'/>"<c:if test="${o.current}"> aria-current="step"</c:if>>
              <span class="outline-dot"><c:choose><c:when test="${o.done}"><pw:icon name="check" label="Done"/></c:when><c:otherwise><c:out value="${st.count}"/></c:otherwise></c:choose></span><c:out value="${o.title}"/></a></li>
          </c:forEach>
        </ol>
        <c:if test="${not empty p.objectives}">
          <p class="card-eyebrow section-gap">You'll be able to</p>
          <ul class="objectives">
            <c:forEach var="obj" items="${p.objectives}"><li><c:out value="${obj}"/></li></c:forEach>
          </ul>
        </c:if>
      </div>
    </aside>

    <article class="card section-card" aria-labelledby="section-title">
      <div class="section-kind">
        <c:choose>
          <c:when test="${sec.kind == 'visual'}"><pw:icon name="sparkles" size="sm"/>Interactive</c:when>
          <c:when test="${sec.kind == 'flashcards'}"><pw:icon name="layout-dashboard" size="sm"/>Flashcards</c:when>
          <c:when test="${sec.kind == 'example'}"><pw:icon name="target" size="sm"/>Worked example</c:when>
          <c:when test="${sec.kind == 'summary'}"><pw:icon name="circle-check" size="sm"/>Summary</c:when>
          <c:when test="${sec.kind == 'resources'}"><pw:icon name="book-open" size="sm"/>Go further</c:when>
          <c:when test="${sec.kind == 'overview'}"><pw:icon name="compass" size="sm"/>Overview</c:when>
          <c:otherwise><pw:icon name="book-open" size="sm"/>Explanation</c:otherwise>
        </c:choose>
      </div>
      <h2 id="section-title"><c:out value="${sec.title}"/></h2>

      <c:if test="${not empty sec.html.html}">
        <div class="prose"><pw:html value="${sec.html}"/></div>
      </c:if>

      <c:if test="${sec.kind == 'visual'}">
        <figure class="visual" data-visual data-spec="<c:out value='${sec.visualJson}'/>">
          <p class="visual-fallback" data-fallback><c:out value="${sec.alt}"/></p>
          <figcaption class="visually-hidden"><c:out value="${sec.alt}"/></figcaption>
        </figure>
      </c:if>

      <c:if test="${sec.kind == 'flashcards'}">
        <p class="muted">Try to answer each card in your head, then open it to check.</p>
        <div class="cards">
          <c:forEach var="card" items="${sec.cards}">
            <details class="flashcard">
              <summary><span class="front"><pw:html value="${card.front}"/></span><span class="reveal-hint"><pw:icon name="eye" size="sm"/>Show answer</span></summary>
              <div class="back"><pw:html value="${card.back}"/></div>
            </details>
          </c:forEach>
        </div>
      </c:if>

      <c:if test="${sec.kind == 'resources'}">
        <ul class="links-list">
          <c:forEach var="l" items="${sec.links}">
            <li><a href="<c:out value='${l.url}'/>" rel="noopener noreferrer" target="_blank">
              <span><c:out value="${l.title}"/><c:if test="${not empty l.licence}"> <span class="subtle">· <c:out value="${l.licence}"/></span></c:if></span>
              <span class="chip"><c:out value="${l.kind == 'video_link' ? 'Video' : 'Article'}"/> ↗</span></a></li>
          </c:forEach>
        </ul>
      </c:if>

      <div class="player-nav">
        <c:choose>
          <c:when test="${not empty p.prevKey}"><a class="btn btn-ghost" href="<c:url value='/app/modules/${p.moduleKey}/${p.prevKey}'/>">← Previous</a></c:when>
          <c:otherwise><span></span></c:otherwise>
        </c:choose>
        <pw:form action="/app/modules/${p.moduleKey}/${sec.key}/done">
          <button type="submit" class="btn btn-primary">
            <c:choose>
              <c:when test="${empty p.nextKey}">Finish module <pw:icon name="check"/></c:when>
              <c:otherwise>Got it, continue <pw:icon name="arrow-right"/></c:otherwise>
            </c:choose>
          </button>
        </pw:form>
      </div>
    </article>
  </div>
</pw:shell>
