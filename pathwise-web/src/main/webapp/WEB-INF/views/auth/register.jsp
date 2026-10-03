<%@ page contentType="text/html;charset=UTF-8" %>
<pw:document title="Create your account">
  <div class="auth">
    <%@ include file="_panel.jspf" %>
    <main id="main" class="auth-main">
      <div class="auth-card">
        <div class="auth-mobile-brand"><pw:logo/></div>
        <h1>Create your account</h1>
        <p class="lead">It takes a minute. Pathwise is free.</p>

        <c:if test="${not empty formError}"><pw:alert kind="error" message="${formError}"/></c:if>

        <pw:form action="/register">
          <fieldset class="choice-group">
            <legend>I'm joining as</legend>
            <label class="choice">
              <input type="radio" name="role" value="student"<c:if test="${form.role != 'teacher'}"> checked</c:if>>
              <span class="choice-icon"><pw:icon name="graduation-cap"/></span>
              <span><strong>A student</strong><span>Learn, practise and track your progress</span></span>
            </label>
            <label class="choice">
              <input type="radio" name="role" value="teacher"<c:if test="${form.role == 'teacher'}"> checked</c:if>>
              <span class="choice-icon"><pw:icon name="school"/></span>
              <span><strong>A teacher</strong><span>Run classrooms. You can learn too.</span></span>
            </label>
          </fieldset>
          <c:if test="${not empty errors.role}"><p class="field-error"><c:out value="${errors.role}"/></p></c:if>

          <pw:field name="displayName" label="Your name" value="${form.displayName}" autocomplete="name"
                    required="true" autofocus="true" error="${errors.displayName}"/>
          <pw:field name="email" label="Email" type="email" value="${form.email}" autocomplete="email"
                    inputmode="email" required="true" error="${errors.email}"/>
          <pw:field name="password" label="Password" type="password" autocomplete="new-password" required="true"
                    reveal="true" strength="true" hint="At least 10 characters. A short phrase works well."
                    error="${errors.password}"/>
          <button type="submit" class="btn btn-primary btn-lg btn-block">Create account</button>
        </pw:form>

        <p class="auth-switch">Already have an account? <a href="<c:url value='/login'/>">Sign in</a></p>
      </div>
    </main>
  </div>
</pw:document>
