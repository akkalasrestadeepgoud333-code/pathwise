<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Content">
  <div class="page-header">
    <div>
      <h1>Content packs</h1>
      <p>Bundled, version-controlled subjects. Each pack is validated before import and applied in a single transaction. Re-importing keeps students' progress.</p>
    </div>
  </div>

  <c:choose>
    <c:when test="${empty packs}">
      <pw:empty title="No content packs found" icon="book-open" message="Add packs under content-packs/ in the web module's resources."/>
    </c:when>
    <c:otherwise>
      <div class="grid">
        <c:forEach var="pk" items="${packs}">
          <section class="card">
            <div class="card-header">
              <div>
                <p class="card-eyebrow mono"><c:out value="${pk.key}"/></p>
                <h2 class="card-title"><c:out value="${pk.title}"/></h2>
              </div>
              <c:choose>
                <c:when test="${not empty pk.problems}"><span class="chip">Has problems</span></c:when>
                <c:when test="${pk.upToDate}"><span class="chip chip-success">Up to date</span></c:when>
                <c:when test="${empty pk.importedVersion}"><span class="chip chip-accent">Not imported</span></c:when>
                <c:otherwise><span class="chip chip-accent">Update available</span></c:otherwise>
              </c:choose>
            </div>
            <p class="muted">Bundled version <c:out value="${pk.availableVersion}"/>
              <c:if test="${not empty pk.importedVersion}"> · imported version <c:out value="${pk.importedVersion}"/> on <c:out value="${pk.importedLabel}"/></c:if></p>
            <c:if test="${not empty pk.problems}">
              <div class="alert alert-error" role="alert"><pw:icon name="triangle-alert"/>
                <div><p><strong>This pack can't be imported until these are fixed:</strong></p>
                  <ul><c:forEach var="e" items="${pk.problems}" end="9"><li><c:out value="${e}"/></li></c:forEach></ul></div>
              </div>
            </c:if>
            <c:if test="${empty pk.problems}">
              <pw:form action="/admin/content">
                <input type="hidden" name="pack" value="<c:out value='${pk.key}'/>">
                <c:if test="${pk.upToDate}"><input type="hidden" name="force" value="1"></c:if>
                <button type="submit" class="btn <c:out value='${pk.upToDate ? "" : "btn-primary"}'/>">
                  <pw:icon name="arrow-right"/><c:out value="${pk.upToDate ? 'Re-import anyway' : 'Import now'}"/></button>
              </pw:form>
            </c:if>
          </section>
        </c:forEach>
      </div>
    </c:otherwise>
  </c:choose>
</pw:shell>
