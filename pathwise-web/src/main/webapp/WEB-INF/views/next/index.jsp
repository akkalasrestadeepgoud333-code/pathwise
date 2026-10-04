<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Your next steps">
  <div class="page-header">
    <div>
      <h1>Your next steps</h1>
      <p>Suggestions built from your own answers and progress, each with its reasons. They're suggestions: you decide.</p>
    </div>
  </div>

  <c:choose>
    <c:when test="${empty top}">
      <pw:empty title="No suggestions right now" icon="compass"
                message="Open a subject from Learn and study a module or two. Suggestions appear as soon as there's something to base them on."/>
    </c:when>
    <c:otherwise>
      <div class="grid grid-main-aside">
        <div>
          <c:set var="labels" value="${{'continue':'Continue where you left off','strengthen':'Strengthen','challenge':'Challenge yourself','explore':'Explore next','revise':'Revise'}}"/>
          <c:forEach var="slot" items="${slots}">
            <c:if test="${not empty slot.value}">
              <section class="slot" aria-labelledby="slot-<c:out value='${slot.key}'/>">
                <h2 class="slot-title" id="slot-<c:out value='${slot.key}'/>"><c:out value="${labels[slot.key]}"/></h2>
                <div class="slot-recs">
                  <c:forEach var="r" items="${slot.value}">
                    <pw:rec rec="${r}" from="next"/>
                  </c:forEach>
                </div>
              </section>
            </c:if>
          </c:forEach>
        </div>

        <aside class="grid">
          <section class="card" aria-labelledby="goal-title">
            <h2 id="goal-title" class="card-title">Your goals</h2>
            <c:choose>
              <c:when test="${empty goals}"><p class="muted">Set a goal and suggestions will lean toward it, and toward the topics it builds on.</p></c:when>
              <c:otherwise>
                <ul class="list">
                  <c:forEach var="g" items="${goals}">
                    <li class="list-item">
                      <span class="list-icon"><pw:icon name="target"/></span>
                      <div class="grow"><div class="title">Master <c:out value="${g.topicTitle}"/></div>
                        <c:if test="${not empty g.targetDate}"><div class="meta">by <c:out value="${g.targetDate}"/></div></c:if></div>
                      <pw:form action="/app/next/goals/${g.id}/end"><button type="submit" class="btn btn-ghost btn-sm" aria-label="Remove goal">Remove</button></pw:form>
                    </li>
                  </c:forEach>
                </ul>
              </c:otherwise>
            </c:choose>
            <pw:form action="/app/next/goals" cssClass="goal-form">
              <div class="field">
                <label class="field-label" for="goal-topic">Add a goal: master…</label>
                <select class="input" id="goal-topic" name="topic" required>
                  <option value="">Choose a topic</option>
                  <c:forEach var="o" items="${topicOptions}"><option value="<c:out value='${o.value}'/>"><c:out value="${o.label}"/></option></c:forEach>
                </select>
              </div>
              <div class="field">
                <label class="field-label" for="goal-date">Target date (optional)</label>
                <input class="input" type="date" id="goal-date" name="date">
              </div>
              <button type="submit" class="btn btn-sm">Save goal</button>
            </pw:form>
          </section>

          <section class="card" aria-labelledby="how-title">
            <h2 id="how-title" class="card-title">How suggestions work</h2>
            <p class="muted">Pathwise estimates each skill from your answers. Recent and harder questions count more, and hints a little less. It then applies clear rules: fix shaky foundations first, finish what you started, confirm strong skills with a challenge, and review before you forget. It is not machine learning, and it never decides for you.</p>
            <a href="<c:url value='/app/progress'/>">See your levels →</a>
          </section>

          <c:if test="${not empty history}">
            <section class="card" aria-labelledby="hist-title">
              <h2 id="hist-title" class="card-title">Recently</h2>
              <ul class="list">
                <c:forEach var="h" items="${history}" end="5">
                  <li class="list-item"><div class="grow"><div class="title"><c:out value="${h.title}"/></div><div class="meta"><c:out value="${h.slotLabel}"/></div></div></li>
                </c:forEach>
              </ul>
            </section>
          </c:if>
        </aside>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
