<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="s" value="${map.subject}"/>
<c:url var="iconSprite" value="/static/img/icons.svg"/>
<pw:shell title="${s.title} map">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/learn'/>">Learn</a><pw:icon name="chevron-right"/>
    <span aria-current="page"><c:out value="${s.title}"/></span>
  </nav>
  <div class="page-header">
    <div>
      <h1><c:out value="${s.title}"/></h1>
      <p><c:out value="${s.modulesDone}"/> of <c:out value="${s.modules}"/> modules studied<c:if test="${not empty map.currentTitle}"> · you were last in <strong><c:out value="${map.currentTitle}"/></strong></c:if></p>
    </div>
  </div>

  <div class="map-toolbar">
    <div class="view-switch" role="navigation" aria-label="Map view">
      <a href="<c:url value='/app/map/${s.key}?view=graph'/>"<c:if test="${view == 'graph'}"> aria-current="page"</c:if>><pw:icon name="route" size="sm"/>Map</a>
      <a href="<c:url value='/app/map/${s.key}?view=list'/>"<c:if test="${view == 'list'}"> aria-current="page"</c:if>><pw:icon name="menu" size="sm"/>Trail</a>
    </div>
    <span class="subtle">Lines run down from a topic to the topics that build on it.</span>
  </div>

  <div class="view-<c:out value='${view}'/>">
  <c:if test="${view != 'list'}">
    <div class="map-graph-block">
      <div class="map-frame" tabindex="0" aria-label="Learning map. Use Tab to move between topics.">
        <svg class="map-svg" xmlns="http://www.w3.org/2000/svg" width="<c:out value='${map.width}'/>" height="<c:out value='${map.height}'/>"
             viewBox="<c:out value='0 0 ${map.width} ${map.height}'/>" role="group" aria-label="<c:out value='${s.title}'/> topics and prerequisites">
          <c:forEach var="e" items="${map.edges}">
            <path class="map-edge<c:if test='${not e.required}'> recommended</c:if><c:if test='${e.studied}'> studied</c:if>" d="<c:out value='${e.path}'/>"/>
          </c:forEach>
          <c:forEach var="n" items="${map.nodes}">
            <a class="map-node <c:out value='${n.coverage}'/><c:if test='${n.current}'> current</c:if><c:if test='${n.optional}'> optional</c:if>"
               href="<c:url value='/app/topics/${s.key}/${n.key}'/>"
               transform="<c:out value='translate(${n.x} ${n.y})'/>"
               aria-label="<c:out value='${n.title}'/>: <c:out value='${n.coverageLabel}'/><c:if test='${n.modulesTotal > 0}'>, <c:out value='${n.modulesDone}'/> of <c:out value='${n.modulesTotal}'/> modules</c:if><c:if test='${not empty n.buildsOn}'>, builds on <c:out value='${n.buildsOn[0]}'/></c:if>">
              <title><c:out value="${n.title}"/></title>
              <rect class="node-card" width="208" height="72" rx="14"/>
              <text class="node-eyebrow" x="16" y="22"><c:choose><c:when test="${not empty n.parentTitle}"><c:out value="${n.parentTitle}"/></c:when><c:when test="${n.advanced}">Advanced</c:when><c:otherwise>Stage <c:out value="${n.stage}"/></c:otherwise></c:choose><c:if test="${n.optional}"> · optional</c:if></text>
              <text class="node-title" x="16" y="42"><c:out value="${n.title}"/></text>
              <text class="node-meta" x="16" y="60"><c:choose>
                <c:when test="${n.coverage == 'no-content'}">Coming soon</c:when>
                <c:when test="${not empty n.buildsOn and n.coverage == 'not-started'}">Builds on <c:out value="${n.buildsOn[0]}"/></c:when>
                <c:otherwise><c:out value="${n.modulesDone}"/>/<c:out value="${n.modulesTotal}"/> · <c:out value="${n.coverageLabel}"/></c:otherwise>
              </c:choose></text>
              <g transform="translate(176 36)">
                <circle class="ring-track" r="14"/>
                <c:if test="${n.modulesDone > 0}"><circle class="ring-fill" r="14" stroke-dasharray="<c:out value='${n.ringDash}'/>" transform="rotate(-90)"/></c:if>
                <c:choose>
                  <c:when test="${n.coverage == 'studied'}"><use class="ring-icon" href="${fn:escapeXml(iconSprite)}#i-check" x="-8" y="-8" width="16" height="16"/></c:when>
                  <c:when test="${not empty n.buildsOn and n.coverage == 'not-started'}"><use class="lock" href="${fn:escapeXml(iconSprite)}#i-lock" x="-7" y="-7" width="14" height="14"/></c:when>
                </c:choose>
              </g>
              <c:if test="${n.current}">
                <circle class="here-halo" cx="0" cy="36" r="10"/>
                <circle class="here-pin" cx="0" cy="36" r="6"/>
              </c:if>
            </a>
          </c:forEach>
        </svg>
      </div>
      <div class="map-legend" aria-label="Legend">
        <span><span class="state-chip state-studied"><pw:icon name="check"/>Studied</span></span>
        <span><span class="state-chip state-in-progress">In progress</span></span>
        <span><span class="state-chip state-not-started"><pw:icon name="lock"/>Builds on a topic you haven't studied</span></span>
        <span><i class="legend-swatch"></i>Required first</span>
        <span><i class="legend-swatch recommended"></i>Recommended first</span>
      </div>
      <p class="legend-note">"Studied" means you finished the learning material. Mastery is measured separately, from practice and assessments, once those are available.</p>
    </div>
  </c:if>

  <c:if test="${view != 'graph'}">
    <ol class="trail-list map-list-block" aria-label="Topics in learning order">
      <c:forEach var="stage" items="${map.stages}">
        <li>
          <div class="stage-label">Stage <c:out value="${stage.number}"/></div>
          <ul class="stage-items">
            <c:forEach var="n" items="${stage.nodes}">
              <li class="<c:out value='${n.coverage}'/><c:if test='${n.current}'> current</c:if>">
                <a class="card card-interactive trail-card" href="<c:url value='/app/topics/${s.key}/${n.key}'/>">
                  <div class="grow">
                    <div class="title"><c:out value="${n.title}"/><c:if test="${n.current}"> <span class="chip chip-primary">You are here</span></c:if></div>
                    <div class="meta">
                      <c:if test="${not empty n.parentTitle}"><c:out value="${n.parentTitle}"/> · </c:if>
                      <c:choose>
                        <c:when test="${n.coverage == 'no-content'}">Coming soon</c:when>
                        <c:otherwise><c:out value="${n.modulesDone}"/> of <c:out value="${n.modulesTotal}"/> modules</c:otherwise>
                      </c:choose>
                      <c:if test="${not empty n.buildsOn and n.coverage == 'not-started'}"> · builds on <c:out value="${n.buildsOn[0]}"/></c:if>
                      <c:if test="${n.optional}"> · optional</c:if><c:if test="${n.advanced}"> · advanced</c:if>
                    </div>
                  </div>
                  <span class="state-chip state-<c:out value='${n.coverage}'/>"><c:out value="${n.coverageLabel}"/></span>
                </a>
              </li>
            </c:forEach>
          </ul>
        </li>
      </c:forEach>
    </ol>
  </c:if>
  </div>
</pw:shell>
