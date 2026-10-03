<%@ tag description="Prints sanitised HTML. Only SafeHtml values (produced by the Markdown sanitiser) are accepted." pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="value" required="true" type="app.pathwise.web.content.SafeHtml" %>
<%-- The single audited exception to the escaping rule: the attribute type guarantees sanitised input. --%>
${value.html}
