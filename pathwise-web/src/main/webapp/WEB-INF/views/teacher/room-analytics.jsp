<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="an" value="${analytics}"/>
<c:set var="h" value="${an.header}"/>
<pw:shell title="${h.name}: analytics">
  <pw:roomhead h="${h}" section="analytics"/>
  <p class="muted">Counts and averages of what students have done on Pathwise. Skill levels come from the rule-based learner model (practice, games, checks and assessments). Nothing here is a prediction.</p>

  <div class="stat-row">
    <div class="card stat"><span class="stat-value"><c:out value="${an.students}"/></span><span class="stat-label">students</span></div>
    <div class="card stat"><span class="stat-value"><c:out value="${an.active7d}"/></span><span class="stat-label">active in the last 7 days</span></div>
    <div class="card stat"><span class="stat-value"><c:out value="${empty an.averageCompletion ? '—' : an.averageCompletion.toString().concat('%')}"/></span><span class="stat-label">of required items done, on average</span></div>
  </div>

  <c:if test="${not empty an.attention}">
    <section class="card section-gap" aria-labelledby="att-title">
      <h2 id="att-title" class="card-title">Students who may need you</h2>
      <ul class="list">
        <c:forEach var="a" items="${an.attention}">
          <li class="list-item"><span class="list-icon"><pw:icon name="triangle-alert"/></span>
            <div class="grow"><div class="title"><c:out value="${a.name}"/></div>
              <div class="meta"><c:forEach var="r" items="${a.reasons}" varStatus="st"><c:out value="${r}"/><c:if test="${not st.last}"> · </c:if></c:forEach></div></div></li>
        </c:forEach>
      </ul>
    </section>
  </c:if>

  <section class="card section-gap" aria-labelledby="heat-title">
    <h2 id="heat-title" class="card-title">Room skills</h2>
    <c:choose>
      <c:when test="${empty an.skills}"><p class="muted">Add modules or assessments to the curriculum to see the skills they cover.</p></c:when>
      <c:when test="${an.students == 0}"><p class="muted">No students yet.</p></c:when>
      <c:otherwise>
        <p class="legend-note heat-legend"><span class="heat-key needs"></span>Needs practice <span class="heat-key developing"></span>Getting started or developing <span class="heat-key strong"></span>Proficient or mastered <span class="heat-key none"></span>No evidence yet</p>
        <ul class="heat-list">
          <c:forEach var="s" items="${an.skills}">
            <li>
              <div class="heat-label"><span class="title"><c:out value="${s.skill}"/></span><span class="meta"><c:out value="${s.topic}"/></span></div>
              <div class="heat-bar" role="img" aria-label="<c:out value='${s.needsSupport}'/> need practice, <c:out value='${s.developing}'/> developing, <c:out value='${s.proficientOrMastered}'/> proficient or better, <c:out value='${s.notStarted}'/> no evidence">
                <span class="needs w-<c:out value='${s.needsPercent}'/>"></span><span class="developing w-<c:out value='${s.developingPercent}'/>"></span><span class="strong w-<c:out value='${s.strongPercent}'/>"></span>
              </div>
              <span class="mono heat-count"><c:out value="${s.needsSupport}"/> need practice</span>
            </li>
          </c:forEach>
        </ul>
      </c:otherwise>
    </c:choose>
  </section>

  <section class="card section-gap" aria-labelledby="as-title">
    <h2 id="as-title" class="card-title">Assessments</h2>
    <c:choose>
      <c:when test="${empty an.assessments}"><p class="muted">No published assessments in this room yet.</p></c:when>
      <c:otherwise>
        <div class="table-wrap">
          <table class="table">
            <caption class="visually-hidden">Assessment results</caption>
            <thead><tr><th scope="col">Assessment</th><th scope="col" class="num">Submitted</th><th scope="col" class="num">Average (best attempt)</th><th scope="col">Under 40% · 40–59 · 60–79 · 80+</th></tr></thead>
            <tbody>
              <c:forEach var="a" items="${an.assessments}">
                <tr>
                  <th scope="row"><c:choose><c:when test="${a.ownedByRoom}"><a href="<c:url value='/teach/rooms/${h.id}/assess/${a.id}'/>"><c:out value="${a.title}"/></a></c:when><c:otherwise><c:out value="${a.title}"/></c:otherwise></c:choose></th>
                  <td class="num"><c:out value="${a.submitted}"/>/<c:out value="${a.students}"/></td>
                  <td class="num"><c:out value="${empty a.averagePercent ? '—' : a.averagePercent.toString().concat('%')}"/></td>
                  <td class="mono"><c:out value="${a.buckets[0]}"/> · <c:out value="${a.buckets[1]}"/> · <c:out value="${a.buckets[2]}"/> · <c:out value="${a.buckets[3]}"/></td>
                </tr>
              </c:forEach>
            </tbody>
          </table>
        </div>
      </c:otherwise>
    </c:choose>
  </section>

  <section class="card section-gap" aria-labelledby="st-title">
    <div class="card-header">
      <h2 id="st-title" class="card-title">Students</h2>
      <c:if test="${an.individual and an.students > 0}"><a class="btn btn-sm" href="<c:url value='/teach/rooms/${h.id}/analytics.csv'/>">Download CSV</a></c:if>
    </div>
    <c:choose>
      <c:when test="${not an.individual}"><p class="muted">The owner hasn't given you access to individual students' data, so only class totals are shown.</p></c:when>
      <c:when test="${empty an.rows}"><p class="muted">No students yet.</p></c:when>
      <c:otherwise>
        <div class="table-wrap">
          <table class="table">
            <caption class="visually-hidden">Per-student summary</caption>
            <thead><tr><th scope="col">Student</th><th scope="col" class="num">Curriculum done</th><th scope="col" class="num">Skills needing practice</th><th scope="col" class="num">Assessment average</th><th scope="col" class="num">Practice answers (7 d)</th><th scope="col" class="num">Games (7 d)</th><th scope="col">Last active</th></tr></thead>
            <tbody>
              <c:forEach var="r" items="${an.rows}">
                <tr>
                  <th scope="row"><c:out value="${r.name}"/></th>
                  <td class="num"><c:out value="${r.completionPercent}"/>%</td>
                  <td class="num"><c:out value="${r.needsSupport}"/></td>
                  <td class="num"><c:out value="${empty r.averagePercent ? '—' : r.averagePercent.toString().concat('%')}"/></td>
                  <td class="num"><c:out value="${r.practice7d}"/></td>
                  <td class="num"><c:out value="${r.games7d}"/></td>
                  <td><c:out value="${r.lastActive}"/></td>
                </tr>
              </c:forEach>
            </tbody>
          </table>
        </div>
        <p class="legend-note">Practice and game activity is shown separately from assessment results and never changes a grade.</p>
      </c:otherwise>
    </c:choose>
  </section>
</pw:shell>
