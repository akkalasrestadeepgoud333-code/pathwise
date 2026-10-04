<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Progress">
  <div class="page-header">
    <div>
      <h1>Your progress</h1>
      <p>Where each skill stands, based on <strong><c:out value="${totalEvidence}"/></strong> answers so far. Levels are estimates that get firmer as you answer more.</p>
    </div>
  </div>

  <div class="level-summary" aria-label="Skills by level">
    <span class="state-chip state-mastered">Mastered <strong><c:out value="${counts['mastered']}"/></strong></span>
    <span class="state-chip state-proficient">Proficient <strong><c:out value="${counts['proficient']}"/></strong></span>
    <span class="state-chip state-developing">Developing <strong><c:out value="${counts['developing']}"/></strong></span>
    <span class="state-chip state-needs-support">Needs practice <strong><c:out value="${counts['needs-support']}"/></strong></span>
    <span class="state-chip state-exploring">Getting started <strong><c:out value="${counts['exploring']}"/></strong></span>
    <span class="state-chip state-not-started">Not started <strong><c:out value="${counts['not-started']}"/></strong></span>
  </div>

  <c:forEach var="s" items="${subjects}">
    <section class="section-gap" aria-labelledby="sub-<c:out value='${s.key}'/>">
      <h2 id="sub-<c:out value='${s.key}'/>"><c:out value="${s.title}"/></h2>
      <div class="grid grid-2">
        <c:forEach var="t" items="${s.topics}">
          <div class="card progress-topic<c:if test='${not t.active}'> quiet</c:if>">
            <div class="card-header">
              <h3 class="card-title"><a href="<c:url value='/app/topics/${s.key}/${t.key}'/>"><c:out value="${t.title}"/></a></h3>
            </div>
            <ul class="skill-levels">
              <c:forEach var="k" items="${t.skills}">
                <li>
                  <span class="skill-name"><c:out value="${k.title}"/></span>
                  <span class="level-bar level-<c:out value='${k.level}'/>" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span>
                  <span class="state-chip state-<c:out value='${k.state}'/>"><c:out value="${k.stateLabel}"/></span>
                  <span class="skill-meta"><c:choose><c:when test="${k.evidence == 0}">no answers yet</c:when><c:otherwise><c:out value="${k.evidence}"/> answer<c:if test="${k.evidence != 1}">s</c:if> · <c:out value="${k.confidence}"/> confidence</c:otherwise></c:choose></span>
                </li>
              </c:forEach>
            </ul>
          </div>
        </c:forEach>
      </div>
    </section>
  </c:forEach>
  <p class="legend-note section-gap">How levels work: Pathwise combines your answers, giving more weight to recent and harder ones and less to answers given with hints. "Mastered" also needs a recent correct challenge question. These are transparent estimates, not exam grades and not machine-learning predictions.</p>
</pw:shell>
