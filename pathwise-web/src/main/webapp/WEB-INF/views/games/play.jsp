<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="g" value="${game}"/>
<pw:shell title="${g.title}" script="/static/js/games/shell.js">
  <div class="game" id="game" data-template="<c:out value='${g.template}'/>" data-puzzle="<c:out value='${g.puzzleJson}'/>"
       data-finish-url="<c:url value='/app/games/play/${g.attemptId}/finish'/>" data-relaxed="<c:out value='${g.relaxed}'/>"
       data-min-gap="<c:out value='${g.minGapMillis}'/>">
    <header class="game-hud">
      <h1 class="game-title"><c:out value="${g.title}"/></h1>
      <div class="game-hud-stats">
        <span class="game-progress" data-progress></span>
        <span class="game-stat" data-score-wrap><span class="visually-hidden">Score </span><strong data-score>0</strong> pts</span>
        <span class="game-stat game-clock" data-clock-wrap><pw:icon name="timer" size="sm"/><span data-clock>0:00</span></span>
        <button type="button" class="icon-btn" data-pause aria-label="Pause" title="Pause"><pw:icon name="pause"/></button>
      </div>
    </header>
    <div class="game-stage" data-stage>
      <noscript><pw:alert kind="info" message="This game needs JavaScript. Practice questions work without it."/></noscript>
      <p class="muted" data-loading>Loading the game…</p>
    </div>
    <p class="game-feedback" role="status" aria-live="polite" data-feedback></p>
    <div class="game-paused card" data-paused hidden>
      <h2 class="card-title">Paused</h2>
      <p class="muted">Take as long as you like. Nothing is timed.</p>
      <button type="button" class="btn btn-primary" data-resume><pw:icon name="play"/>Resume</button>
    </div>
  </div>
</pw:shell>
