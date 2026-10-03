<%@ page contentType="text/html;charset=UTF-8" %>
<pw:document title="Every student, a different path" description="Pathwise is a personalised learning ecosystem: learn, practise, play and get explainable next steps.">
  <header class="landing-nav">
    <pw:logo/>
    <div class="actions">
      <a class="btn btn-ghost" href="<c:url value='/login'/>">Sign in</a>
      <a class="btn btn-primary" href="<c:url value='/register'/>">Get started</a>
    </div>
  </header>

  <main id="main">
    <section class="landing-hero" aria-labelledby="hero-title">
      <div>
        <span class="chip chip-primary"><pw:icon name="sparkles" size="sm"/>Early access · v0.1</span>
        <h1 id="hero-title">Every student deserves <em>a different path</em> to understanding.</h1>
        <p class="lead">Pathwise is a personalised learning space. You learn, practise and play. Then it suggests what to do next and tells you why. You're always free to choose your own way.</p>
        <div class="ctas">
          <a class="btn btn-primary btn-lg" href="<c:url value='/register'/>">Start learning, it's free <pw:icon name="arrow-right"/></a>
          <a class="btn btn-lg" href="<c:url value='/register?as=teacher'/>"><pw:icon name="school"/>I'm a teacher</a>
        </div>
      </div>
      <pw:trail cssClass="landing-art"/>
    </section>

    <section class="principles" aria-labelledby="principles-title">
      <p class="card-eyebrow">How Pathwise is designed</p>
      <h2 id="principles-title">Built on a few promises we don't break.</h2>
      <div class="grid grid-3">
        <article class="card principle">
          <div class="list-icon"><pw:icon name="route"/></div>
          <h3>Next steps with reasons</h3>
          <p>Every suggestion comes with a plain explanation based on your own work. Nothing is hidden, and you can always choose something else.</p>
        </article>
        <article class="card principle">
          <div class="list-icon"><pw:icon name="target"/></div>
          <h3>Progress means learning</h3>
          <p>Badges, XP and streaks come from what you've shown you understand, never from time spent on a screen.</p>
        </article>
        <article class="card principle">
          <div class="list-icon"><pw:icon name="users"/></div>
          <h3>Teachers set the course</h3>
          <p>Teachers decide the curriculum. Inside it, each student gets their own path, and their privacy is protected.</p>
        </article>
      </div>
    </section>
  </main>

  <footer class="landing-footer">
    Pathwise · free and open source to run · no ads, no tracking
  </footer>
</pw:document>
