<%@ page contentType="text/html;charset=UTF-8" %>
<c:set var="ch" value="${chat}"/>
<pw:shell title="${ch.title}">
  <nav class="breadcrumbs" aria-label="Breadcrumb">
    <a href="<c:url value='/app/tutor'/>">Tutor</a><pw:icon name="chevron-right"/><span aria-current="page"><c:out value="${ch.modeLabel}"/></span>
  </nav>
  <div class="assess-rules">
    <header class="page-header">
      <div><p class="card-eyebrow"><c:out value="${ch.modeLabel}"/></p><h1 class="doubt-title"><c:out value="${ch.title}"/></h1></div>
      <span class="chip<c:if test='${ch.aiOn}'> chip-accent</c:if> tutor-badge"><c:out value="${ch.providerLabel}"/></span>
    </header>
    <ol class="chat" aria-label="Conversation">
      <c:forEach var="m" items="${ch.messages}" varStatus="st">
        <li class="msg msg-<c:out value='${m.role}'/>"<c:if test="${st.last}"> id="latest"</c:if>>
          <div class="msg-bubble prose"><pw:html value="${m.html}"/></div>
          <div class="meta"><c:out value="${m.role == 'user' ? 'You' : m.sourceLabel}"/> · <c:out value="${m.when}"/></div>
        </li>
      </c:forEach>
    </ol>
    <pw:form action="/app/tutor/${ch.id}" cssClass="stack-form card chat-form">
      <input type="hidden" name="action" value="ask">
      <label class="visually-hidden" for="q">Your message</label>
      <textarea class="input" id="q" name="q" rows="2" required maxlength="2000" placeholder="<c:out value='${ch.placeholder}'/>"></textarea>
      <div class="practice-actions"><button type="submit" class="btn btn-primary">Send</button></div>
    </pw:form>
    <pw:form action="/app/tutor/${ch.id}" cssClass="inline-form section-gap-sm">
      <button type="submit" name="action" value="delete" class="btn btn-sm btn-ghost">Delete this conversation</button>
    </pw:form>
  </div>
</pw:shell>
