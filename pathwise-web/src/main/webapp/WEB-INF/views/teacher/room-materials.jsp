<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="${header.name}: materials">
  <pw:roomhead h="${header}" section="materials"/>
  <p class="muted">Your own notes and PDFs for this room. Everyone in the room can read them; nobody outside it can.</p>

  <c:if test="${materials.canEdit}">
    <div class="grid grid-2 section-gap-sm">
      <section class="card" aria-labelledby="note-title">
        <h2 id="note-title" class="card-title">Write a note</h2>
        <pw:form action="/materials/${header.id}">
          <input type="hidden" name="action" value="add-note">
          <div class="field">
            <label class="field-label" for="n-title">Title</label>
            <input class="input" id="n-title" name="title" required minlength="2" maxlength="160">
          </div>
          <div class="field">
            <label class="field-label" for="n-body">Note</label>
            <textarea class="input" id="n-body" name="body" rows="8" required maxlength="20000"></textarea>
            <p class="legend-note">Markdown works: **bold**, lists, `code`, tables and links.</p>
          </div>
          <button type="submit" class="btn btn-primary btn-sm">Share note</button>
        </pw:form>
      </section>

      <section class="card" aria-labelledby="pdf-title">
        <h2 id="pdf-title" class="card-title">Upload a PDF</h2>
        <form method="post" enctype="multipart/form-data" action="<c:url value='/materials/${header.id}'/>">
          <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}'/>">
          <input type="hidden" name="action" value="add-pdf">
          <div class="field">
            <label class="field-label" for="p-title">Title (optional)</label>
            <input class="input" id="p-title" name="title" maxlength="160" placeholder="Defaults to the file name">
          </div>
          <div class="field">
            <label class="field-label" for="p-file">PDF file (up to <c:out value="${maxMb}"/> MB)</label>
            <input class="input" id="p-file" name="file" type="file" accept="application/pdf,.pdf" required>
          </div>
          <button type="submit" class="btn btn-primary btn-sm">Upload</button>
        </form>
      </section>
    </div>
  </c:if>

  <section class="section-gap" aria-labelledby="list-title">
    <h2 id="list-title" class="card-title">Shared with the room</h2>
    <c:choose>
      <c:when test="${empty materials.items}"><pw:empty icon="file-text" title="Nothing shared yet" message="Notes and PDFs you share appear here and on the room's page for students."/></c:when>
      <c:otherwise>
        <c:forEach var="m" items="${materials.items}">
          <article class="card section-gap-sm" aria-label="<c:out value='${m.title}'/>">
            <div class="card-header">
              <div>
                <p class="card-eyebrow"><c:out value="${m.kind == 'pdf' ? 'PDF' : 'Note'}"/> · <c:out value="${m.authorName}"/></p>
                <h3 class="card-title"><c:out value="${m.title}"/></h3>
              </div>
              <c:if test="${materials.canEdit}">
                <pw:form action="/materials/${header.id}">
                  <input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<c:out value='${m.id}'/>">
                  <button type="submit" class="btn btn-sm btn-ghost">Remove</button>
                </pw:form>
              </c:if>
            </div>
            <c:choose>
              <c:when test="${m.kind == 'pdf'}">
                <p><a href="<c:url value='/materials/${header.id}/${m.id}'/>" target="_blank" rel="noopener"><pw:icon name="file-text"/><c:out value="${m.fileName}"/></a>
                  <span class="meta">· <c:out value="${m.sizeLabel}"/></span></p>
              </c:when>
              <c:otherwise><div class="prose"><pw:html value="${m.html}"/></div></c:otherwise>
            </c:choose>
          </article>
        </c:forEach>
      </c:otherwise>
    </c:choose>
  </section>
</pw:shell>
