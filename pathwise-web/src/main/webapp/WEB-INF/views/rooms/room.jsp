<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="r" value="${room}"/>
<pw:shell title="${r.name}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/rooms'/>">Rooms</a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${r.name}"/></span>
  </nav>
  <div class="page-header">
    <div>
      <h1><c:out value="${r.name}"/><c:if test="${r.archived}"> <span class="chip">Archived</span></c:if></h1>
      <p><c:if test="${not empty r.teachers}">With <c:out value="${r.teachers}"/>. </c:if><c:out value="${r.description}"/></p>
    </div>
    <c:if test="${r.teacherView}"><a class="btn" href="<c:url value='/teach/rooms/${r.id}'/>">Manage this room</a></c:if>
  </div>

  <div class="grid grid-main-aside">
    <div class="stack">
      <c:choose>
        <c:when test="${not empty r.next}">
          <section class="hero-card" aria-labelledby="next-title">
            <p class="card-eyebrow">Your next step in this room</p>
            <h2 id="next-title"><c:out value="${r.next.title}"/></h2>
            <p><c:out value="${r.next.kindLabel}"/> · <c:out value="${r.next.statusLabel}"/>. It's the first required item you haven't finished yet.</p>
            <a class="btn btn-primary btn-lg" href="<c:url value='${r.next.href}'/>"><c:out value="${r.next.status == 'started' ? 'Continue' : 'Start'}"/> <pw:icon name="arrow-right"/></a>
            <pw:trail cssClass="hero-trail" onInk="true"/>
          </section>
        </c:when>
        <c:when test="${r.total > 0}">
          <section class="card" aria-labelledby="done-title">
            <h2 id="done-title" class="card-title">You're up to date</h2>
            <p class="muted">You've finished every required item that's open. Your personal next steps are on <a href="<c:url value='/app/next'/>">Next steps</a>.</p>
          </section>
        </c:when>
      </c:choose>

      <section aria-labelledby="cur-title">
        <div class="section-head">
          <h2 id="cur-title">Curriculum</h2>
          <c:if test="${r.total > 0}"><span class="meta"><c:out value="${r.done}"/> of <c:out value="${r.total}"/> required items done</span></c:if>
        </div>
        <c:choose>
          <c:when test="${empty r.units}"><pw:empty icon="map" title="Nothing here yet" message="Your teacher hasn't opened any units yet."/></c:when>
          <c:otherwise>
            <c:forEach var="u" items="${r.units}">
              <article class="card unit-card section-gap-sm">
                <h3 class="card-title"><c:out value="${u.title}"/></h3>
                <ol class="list unit-items">
                  <c:forEach var="it" items="${u.items}">
                    <li class="list-item">
                      <span class="list-icon item-<c:out value='${it.status}'/>"><pw:icon name="${it.status == 'done' ? 'circle-check' : (it.kind == 'module' ? 'book-open' : (it.kind == 'assessment' ? 'clipboard-check' : (it.kind == 'assignment' ? 'file-text' : 'gamepad-2')))}"/></span>
                      <div class="grow"><div class="title"><a href="<c:url value='${it.href}'/>"><c:out value="${it.title}"/></a></div>
                        <div class="meta"><c:out value="${it.kindLabel}"/><c:if test="${not it.required}"> · optional</c:if></div></div>
                      <span class="state-chip item-chip-<c:out value='${it.status}'/>"><c:out value="${it.statusLabel}"/></span>
                    </li>
                  </c:forEach>
                </ol>
              </article>
            </c:forEach>
          </c:otherwise>
        </c:choose>
      </section>

      <section class="card" aria-labelledby="mat-title">
        <div class="card-header">
          <h2 id="mat-title" class="card-title">Materials from your teacher</h2>
          <c:if test="${materials.canEdit}"><a class="btn btn-sm" href="<c:url value='/teach/rooms/${r.id}/materials'/>">Add or remove</a></c:if>
        </div>
        <c:choose>
          <c:when test="${empty materials.items}"><p class="muted">Nothing shared yet.</p></c:when>
          <c:otherwise>
            <ul class="list">
              <c:forEach var="m" items="${materials.items}">
                <li class="list-item">
                  <span class="list-icon"><pw:icon name="${m.kind == 'pdf' ? 'file-text' : 'book-open'}"/></span>
                  <div class="grow">
                    <c:choose>
                      <c:when test="${m.kind == 'pdf'}">
                        <div class="title"><a href="<c:url value='/materials/${r.id}/${m.id}'/>" target="_blank" rel="noopener"><c:out value="${m.title}"/></a></div>
                        <div class="meta">PDF · <c:out value="${m.sizeLabel}"/> · <c:out value="${m.authorName}"/></div>
                      </c:when>
                      <c:otherwise>
                        <details class="material-note">
                          <summary class="title"><c:out value="${m.title}"/></summary>
                          <div class="prose"><pw:html value="${m.html}"/></div>
                        </details>
                        <div class="meta">Note · <c:out value="${m.authorName}"/></div>
                      </c:otherwise>
                    </c:choose>
                  </div>
                </li>
              </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </section>
    </div>

    <div class="stack">
      <c:if test="${not empty r.classAveragePercent}">
        <section class="card" aria-labelledby="class-title">
          <h2 id="class-title" class="card-title">The class so far</h2>
          <p>On average, students have done <strong><c:out value="${r.classAveragePercent}"/>%</strong> of the required items. Only the class total is shown, never anyone's own results.</p>
        </section>
      </c:if>
      <section class="card" aria-labelledby="ann-title">
        <h2 id="ann-title" class="card-title">Announcements</h2>
        <c:choose>
          <c:when test="${empty r.announcements}"><p class="muted">No announcements yet.</p></c:when>
          <c:otherwise>
            <ul class="list">
              <c:forEach var="a" items="${r.announcements}">
                <li class="list-item announcement"><div class="grow">
                  <div class="meta"><c:if test="${a.pinned}"><span class="chip">Pinned</span> </c:if><c:out value="${a.author}"/> · <c:out value="${a.when}"/></div>
                  <p class="announcement-body"><c:out value="${a.body}"/></p></div></li>
              </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </section>
    </div>
  </div>
</pw:shell>
