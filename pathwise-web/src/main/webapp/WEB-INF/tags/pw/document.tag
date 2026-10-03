<%@ tag description="HTML document skeleton: head, theme, CSRF meta, styles and scripts" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="title" required="true" %>
<%@ attribute name="bodyClass" required="false" %>
<%@ attribute name="description" required="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="savedTheme" value="${requestScope.currentUser.theme}"/>
<!DOCTYPE html>
<html lang="en"<c:if test="${savedTheme == 'light' or savedTheme == 'dark'}"> data-theme="<c:out value='${savedTheme}'/>"</c:if>>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title><c:out value="${title}"/> · Pathwise</title>
  <meta name="description" content="<c:out value='${empty description ? "Pathwise — every student deserves a different path to understanding." : description}'/>">
  <meta name="csrf-token" content="<c:out value='${requestScope.csrfToken}'/>">
  <meta name="htmx-config" content='{"includeIndicatorStyles":false,"allowScriptTags":false,"selfRequestsOnly":true}'>
  <meta name="theme-color" content="#f7f6f2" media="(prefers-color-scheme: light)">
  <meta name="theme-color" content="#0d111a" media="(prefers-color-scheme: dark)">
  <link rel="icon" href="<c:url value='/static/img/favicon.svg'/>" type="image/svg+xml">
  <link rel="preload" href="<c:url value='/static/fonts/inter-latin-var.woff2'/>" as="font" type="font/woff2" crossorigin>
  <link rel="preload" href="<c:url value='/static/fonts/bricolage-latin-var.woff2'/>" as="font" type="font/woff2" crossorigin>
  <link rel="stylesheet" href="<c:url value='/static/css/tokens.css'/>">
  <link rel="stylesheet" href="<c:url value='/static/css/base.css'/>">
  <link rel="stylesheet" href="<c:url value='/static/css/components.css'/>">
  <link rel="stylesheet" href="<c:url value='/static/css/layout.css'/>">
  <script src="<c:url value='/static/js/theme-init.js'/>"></script>
</head>
<body class="<c:out value='${bodyClass}'/>">
  <a class="skip-link" href="#main">Skip to content</a>
  <jsp:doBody/>
  <script src="<c:url value='/static/vendor/htmx-2.0.11.min.js'/>" defer></script>
  <script src="<c:url value='/static/js/ui/app.js'/>" type="module"></script>
</body>
</html>
