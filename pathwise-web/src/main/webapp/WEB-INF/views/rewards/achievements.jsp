<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="s" value="${ach.summary}"/>
<pw:shell title="Achievements">
  <div class="page-header"><div><h1>Achievements</h1>
    <p>XP comes from learning: finishing modules, correct practice on skills you haven't mastered, verified games, assessments, and helping others. Nothing for logging in or time on a page.</p></div></div>

  <div class="stat-row">
    <div class="card stat level-card">
      <span class="stat-label">Level</span><span class="stat-value"><c:out value="${s.level}"/></span>
      <div class="progress" role="progressbar" aria-label="Progress to the next level" aria-valuemin="0" aria-valuemax="100" aria-valuenow="<c:out value='${s.percentToNext}'/>"><span class="w-<c:out value='${s.percentToNext - s.percentToNext % 5}'/>"></span></div>
      <span class="stat-label"><c:out value="${s.xp}"/> XP · next level at <c:out value="${s.nextLevelAt}"/></span>
    </div>
    <div class="card stat"><span class="stat-label">Learning streak</span>
      <span class="stat-value"><pw:icon name="flame"/> <c:out value="${s.streak}"/> day<c:if test="${s.streak != 1}">s</c:if></span>
      <span class="stat-label"><c:out value="${s.activeToday ? 'Today counts already.' : 'Learn something today to keep it going.'}"/> Best: <c:out value="${ach.longestStreak}"/>. Freezes saved: <c:out value="${ach.freezes}"/>.</span></div>
    <div class="card stat"><span class="stat-label">Badges</span><span class="stat-value"><c:out value="${ach.earned.size()}"/></span>
      <span class="stat-label"><c:if test="${not empty s.nextBadge}">Closest next: <c:out value="${s.nextBadge}"/> (<c:out value="${s.nextBadgePercent}"/>%)</c:if></span></div>
  </div>
  <p class="legend-note">A day counts toward your streak when you pass a module check, answer 5 practice questions, finish a game at 50%+, submit an assessment, try a challenge, or have an answer accepted. Every 7 days in a row saves a freeze (up to 2) that covers one missed day automatically.</p>

  <h2 class="section-gap">Trophy case</h2>
  <div class="badge-grid">
    <c:forEach var="b" items="${ach.earned}">
      <article class="badge-card earned rarity-<c:out value='${b.rarity}'/> cat-<c:out value='${b.category}'/>">
        <span class="badge-art" aria-hidden="true"><pw:icon name="${b.icon}"/></span>
        <h3><c:out value="${b.name}"/></h3>
        <p class="meta"><c:out value="${b.rarity}"/> · earned <c:out value="${b.earnedLabel}"/></p>
        <p class="badge-desc"><c:out value="${b.description}"/></p>
      </article>
    </c:forEach>
    <c:forEach var="b" items="${ach.locked}">
      <article class="badge-card locked rarity-<c:out value='${b.rarity}'/> cat-<c:out value='${b.category}'/>">
        <span class="badge-art" aria-hidden="true"><pw:icon name="${b.icon}"/></span>
        <h3><c:out value="${b.name}"/></h3>
        <p class="meta"><c:out value="${b.rarity}"/> · locked</p>
        <p class="badge-desc"><c:out value="${b.description}"/></p>
        <div class="progress" role="progressbar" aria-label="Progress" aria-valuemin="0" aria-valuemax="100" aria-valuenow="<c:out value='${b.percent}'/>"><span class="w-<c:out value='${b.percent - b.percent % 5}'/>"></span></div>
        <p class="meta"><c:out value="${b.detail}"/></p>
      </article>
    </c:forEach>
  </div>

  <section class="card section-gap" aria-labelledby="xp-title">
    <h2 id="xp-title" class="card-title">Recent XP</h2>
    <c:choose>
      <c:when test="${empty ach.recent}"><p class="muted">Nothing yet. Finish a module or answer a practice question to start.</p></c:when>
      <c:otherwise>
        <ul class="list">
          <c:forEach var="x" items="${ach.recent}">
            <li class="list-item"><div class="grow"><div class="title"><c:out value="${x.note}"/></div><div class="meta"><c:out value="${x.when}"/></div></div>
              <span class="mono xp-delta<c:if test='${x.xp < 0}'> negative</c:if>"><c:out value="${x.xp > 0 ? '+' : ''}"/><c:out value="${x.xp}"/> XP</span></li>
          </c:forEach>
        </ul>
      </c:otherwise>
    </c:choose>
  </section>
</pw:shell>
