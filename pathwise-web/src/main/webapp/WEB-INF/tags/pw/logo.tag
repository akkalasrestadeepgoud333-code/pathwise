<%@ tag description="Pathwise brand mark + wordmark" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="href" required="false" %>
<%@ attribute name="onInk" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<a class="brand<c:if test='${onInk}'> brand-on-ink</c:if>" href="<c:url value='${empty href ? "/" : href}'/>" aria-label="Pathwise home">
  <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
    <rect width="32" height="32" rx="9" fill="#3F4FE0"/>
    <path d="M9 23.5C9 16.5 23 17 23 9.5" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" stroke-dasharray="2.6 3.2"/>
    <circle cx="9" cy="23.5" r="2.4" fill="#3F4FE0" stroke="#fff" stroke-width="2"/>
    <circle cx="23" cy="9" r="3.2" fill="#F2A93B"/>
  </svg>
  <span>Pathwise</span>
</a>
