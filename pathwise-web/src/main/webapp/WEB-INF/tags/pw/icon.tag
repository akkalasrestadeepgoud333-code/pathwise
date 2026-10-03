<%@ tag description="Lucide icon from the local SVG sprite. Decorative unless a label is given." pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="name" required="true" %>
<%@ attribute name="size" required="false" description="sm | lg" %>
<%@ attribute name="label" required="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<svg class="icon<c:if test='${not empty size}'> icon-<c:out value='${size}'/></c:if>"<c:choose><c:when test="${empty label}"> aria-hidden="true"</c:when><c:otherwise> role="img" aria-label="<c:out value='${label}'/>"</c:otherwise></c:choose> focusable="false"><use href="<c:url value='/static/img/icons.svg'/>#i-<c:out value='${name}'/>"></use></svg>
