<%@ page contentType="text/html;charset=UTF-8" %>
<pw:document title="Sign in">
  <div class="auth">
    <%@ include file="_panel.jspf" %>
    <main id="main" class="auth-main">
      <div class="auth-card">
        <div class="auth-mobile-brand"><pw:logo/></div>
        <h1>Welcome back</h1>
        <p class="lead">Sign in to pick up where you left off.</p>

        <c:if test="${param.signedOut == '1'}"><pw:alert kind="success" message="You've been signed out."/></c:if>
        <c:if test="${not empty formError}"><pw:alert kind="error" message="${formError}"/></c:if>

        <pw:form action="/login">
          <input type="hidden" name="next" value="<c:out value='${next}'/>">
          <pw:field name="email" label="Email" type="email" value="${form.email}" autocomplete="email"
                    inputmode="email" required="true" autofocus="${empty form.email}" error="${errors.email}"/>
          <pw:field name="password" label="Password" type="password" autocomplete="current-password"
                    required="true" reveal="true" autofocus="${not empty form.email}" error="${errors.password}"/>
          <button type="submit" class="btn btn-primary btn-lg btn-block">Sign in</button>
        </pw:form>

        <p class="auth-switch">New to Pathwise? <a href="<c:url value='/register'/>">Create an account</a></p>
      </div>
    </main>
  </div>
</pw:document>
