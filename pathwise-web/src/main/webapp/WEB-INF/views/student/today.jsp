<%@ page contentType="text/html;charset=UTF-8" %>
<pw:shell title="Today">
  <div class="page-header">
    <div>
      <h1><c:out value="${greeting}"/>, <c:out value="${currentUser.firstName}"/>.</h1>
      <p>This is your home in Pathwise. Your next steps will show up here.</p>
    </div>
  </div>

  <div class="grid grid-main-aside">
    <section class="hero-card" aria-labelledby="next-title">
      <p class="card-eyebrow">Your next step</p>
      <h2 id="next-title">Your learning path starts once a subject is published.</h2>
      <p>When subjects are available, this card will suggest one thing to do next, with the reason drawn from your own work. You can always choose something else.</p>
      <pw:trail cssClass="hero-trail" onInk="true"/>
    </section>

    <section class="card" aria-labelledby="account-title">
      <p class="card-eyebrow">Your account</p>
      <h2 id="account-title" class="card-title">You're all set</h2>
      <ul class="list">
        <li class="list-item">
          <span class="list-icon"><pw:icon name="circle-check"/></span>
          <div class="grow"><div class="title">Account created</div><div class="meta">Signed in securely</div></div>
        </li>
        <li class="list-item">
          <span class="list-icon"><pw:icon name="sun"/></span>
          <div class="grow"><div class="title">Light or dark</div><div class="meta">Use the moon button in the top bar</div></div>
        </li>
        <li class="list-item">
          <span class="list-icon"><pw:icon name="laptop"/></span>
          <div class="grow"><div class="title">Your devices</div><div class="meta"><a href="<c:url value='/app/settings#sessions'/>">See where you're signed in</a></div></div>
        </li>
      </ul>
    </section>
  </div>

  <div class="section-gap">
    <pw:empty title="No subjects yet" icon="map"
              message="Subjects, your learning map and practice will appear here once your teacher or an administrator publishes them."/>
  </div>
</pw:shell>
