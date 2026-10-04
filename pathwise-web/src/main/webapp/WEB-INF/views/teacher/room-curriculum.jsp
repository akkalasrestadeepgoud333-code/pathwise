<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="cu" value="${curriculum}"/>
<c:set var="h" value="${cu.header}"/>
<pw:shell title="${h.name}: curriculum">
  <pw:roomhead h="${h}" section="curriculum"/>
  <p class="muted">Students see units and items in this order, from the opening date (if you set one). Required items count toward their progress; their next step is the first one they haven't finished.</p>

  <c:forEach var="u" items="${cu.units}">
    <section class="card unit-card section-gap" aria-labelledby="u-<c:out value='${u.id}'/>">
      <div class="unit-head">
        <h2 class="card-title" id="u-<c:out value='${u.id}'/>"><c:out value="${u.title}"/></h2>
        <c:if test="${not empty u.publishLabel}"><span class="chip<c:if test='${u.scheduled}'> chip-accent</c:if>"><c:out value="${u.publishLabel}"/></span></c:if>
        <c:if test="${h.canAuthor}">
          <pw:form action="/teach/rooms/${h.id}/curriculum" cssClass="inline-form unit-tools">
            <input type="hidden" name="unitId" value="<c:out value='${u.id}'/>">
            <input type="hidden" name="action" value="move-unit">
            <button type="submit" name="dir" value="up" class="icon-btn" aria-label="Move unit up"<c:if test="${u.first}"> disabled</c:if>>↑</button>
            <button type="submit" name="dir" value="down" class="icon-btn" aria-label="Move unit down"<c:if test="${u.last}"> disabled</c:if>>↓</button>
          </pw:form>
        </c:if>
      </div>

      <c:choose>
        <c:when test="${empty u.items}"><p class="muted">No items yet.</p></c:when>
        <c:otherwise>
          <ol class="list unit-items">
            <c:forEach var="it" items="${u.items}">
              <li class="list-item">
                <span class="list-icon"><pw:icon name="${it.kind == 'module' ? 'book-open' : (it.kind == 'assessment' ? 'clipboard-check' : 'gamepad-2')}"/></span>
                <div class="grow">
                  <div class="title"><a href="<c:url value='${it.href}'/>"><c:out value="${it.title}"/></a></div>
                  <div class="meta"><c:out value="${it.kindLabel}"/><c:if test="${not it.required}"> · optional</c:if><c:if test="${not empty it.publishLabel}"> · <c:out value="${it.publishLabel}"/></c:if></div>
                </div>
                <c:if test="${h.canAuthor}">
                  <pw:form action="/teach/rooms/${h.id}/curriculum" cssClass="inline-form">
                    <input type="hidden" name="itemId" value="<c:out value='${it.id}'/>">
                    <input type="hidden" name="action" value="move-item">
                    <button type="submit" name="dir" value="up" class="icon-btn" aria-label="Move up"<c:if test="${it.first}"> disabled</c:if>>↑</button>
                    <button type="submit" name="dir" value="down" class="icon-btn" aria-label="Move down"<c:if test="${it.last}"> disabled</c:if>>↓</button>
                  </pw:form>
                  <pw:form action="/teach/rooms/${h.id}/curriculum" cssClass="inline-form">
                    <input type="hidden" name="itemId" value="<c:out value='${it.id}'/>">
                    <button type="submit" name="action" value="delete-item" class="btn btn-sm btn-ghost">Remove</button>
                  </pw:form>
                </c:if>
              </li>
            </c:forEach>
          </ol>
        </c:otherwise>
      </c:choose>

      <c:if test="${h.canAuthor}">
        <details class="unit-edit">
          <summary>Add to this unit</summary>
          <pw:form action="/teach/rooms/${h.id}/curriculum" cssClass="stack-form">
            <input type="hidden" name="action" value="add-item">
            <input type="hidden" name="unitId" value="<c:out value='${u.id}'/>">
            <div class="field">
              <label class="field-label" for="add-<c:out value='${u.id}'/>">Item</label>
              <select class="input" id="add-<c:out value='${u.id}'/>" name="choice" required>
                <option value="">Choose…</option>
                <c:set var="group" value=""/>
                <c:forEach var="ch" items="${cu.choices}">
                  <option value="<c:out value='${ch.value}'/>"><c:out value="${ch.group}"/>: <c:out value="${ch.label}"/></option>
                </c:forEach>
              </select>
            </div>
            <div class="form-row">
              <div class="field">
                <label class="field-label" for="pub-<c:out value='${u.id}'/>">Opens on <span class="subtle">(optional)</span></label>
                <input class="input" type="date" id="pub-<c:out value='${u.id}'/>" name="publishOn">
              </div>
              <label class="check-row"><input type="checkbox" name="required" value="0"><span>Optional item</span></label>
            </div>
            <button type="submit" class="btn btn-sm btn-primary">Add</button>
          </pw:form>
        </details>
        <details class="unit-edit">
          <summary>Edit or delete unit</summary>
          <pw:form action="/teach/rooms/${h.id}/curriculum" cssClass="stack-form">
            <input type="hidden" name="unitId" value="<c:out value='${u.id}'/>">
            <div class="form-row">
              <div class="field">
                <label class="field-label" for="t-<c:out value='${u.id}'/>">Title</label>
                <input class="input" id="t-<c:out value='${u.id}'/>" name="title" value="<c:out value='${u.title}'/>" maxlength="120" required>
              </div>
              <div class="field">
                <label class="field-label" for="o-<c:out value='${u.id}'/>">Opens on</label>
                <input class="input" type="date" id="o-<c:out value='${u.id}'/>" name="publishOn" value="<c:out value='${u.publishValue}'/>">
              </div>
            </div>
            <div class="practice-actions">
              <button type="submit" name="action" value="update-unit" class="btn btn-sm">Save</button>
              <button type="submit" name="action" value="delete-unit" class="btn btn-sm btn-ghost">Delete unit</button>
            </div>
          </pw:form>
        </details>
      </c:if>
    </section>
  </c:forEach>

  <c:if test="${h.canAuthor}">
    <section class="card section-gap" aria-labelledby="new-unit-title">
      <h2 id="new-unit-title" class="card-title">New unit</h2>
      <pw:form action="/teach/rooms/${h.id}/curriculum" cssClass="form-row">
        <input type="hidden" name="action" value="add-unit">
        <div class="field grow">
          <label class="field-label" for="nu-title">Title</label>
          <input class="input" id="nu-title" name="title" maxlength="120" required placeholder="Week 1: Arrays and lists">
        </div>
        <div class="field">
          <label class="field-label" for="nu-date">Opens on <span class="subtle">(optional)</span></label>
          <input class="input" type="date" id="nu-date" name="publishOn">
        </div>
        <button type="submit" class="btn btn-primary">Add unit</button>
      </pw:form>
    </section>
  </c:if>
  <c:if test="${empty cu.units and not h.canAuthor}"><pw:empty icon="map" title="No curriculum yet" message="The owner hasn't added any units yet."/></c:if>
</pw:shell>
