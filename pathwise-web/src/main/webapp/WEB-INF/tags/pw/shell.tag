<%@ tag description="Signed-in app shell: sidebar (desktop), top bar, bottom tabs (mobile), flash message" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="title" required="true" %>
<%@ attribute name="script" required="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="pw" tagdir="/WEB-INF/tags/pw" %>
<c:set var="user" value="${requestScope.currentUser}"/>
<c:set var="nav" value="${requestScope.nav}"/>
<pw:document title="${title}" script="${script}">
<div class="shell">
  <aside class="sidebar" aria-label="Main navigation">
    <pw:logo href="/app/today"/>
    <nav class="nav" aria-label="Primary">
      <div class="nav-section">Learn</div>
      <a href="<c:url value='/app/today'/>"<c:if test="${nav == 'today'}"> aria-current="page"</c:if>><pw:icon name="compass"/>Today</a>
      <a href="<c:url value='/app/next'/>"<c:if test="${nav == 'next'}"> aria-current="page"</c:if>><pw:icon name="route"/>Next steps</a>
      <a href="<c:url value='/app/learn'/>"<c:if test="${nav == 'learn'}"> aria-current="page"</c:if>><pw:icon name="map"/>Learn</a>
      <a href="<c:url value='/app/practice'/>"<c:if test="${nav == 'practice'}"> aria-current="page"</c:if>><pw:icon name="target"/>Practice</a>
      <a href="<c:url value='/app/assessments'/>"<c:if test="${nav == 'assessments'}"> aria-current="page"</c:if>><pw:icon name="clipboard-check"/>Assessments</a>
      <a href="<c:url value='/app/progress'/>"<c:if test="${nav == 'progress'}"> aria-current="page"</c:if>><pw:icon name="activity"/>Progress</a>
      <a href="<c:url value='/app/achievements'/>"<c:if test="${nav == 'achievements'}"> aria-current="page"</c:if>><pw:icon name="trophy"/>Achievements</a>
      <a href="<c:url value='/app/rooms'/>"<c:if test="${nav == 'rooms'}"> aria-current="page"</c:if>><pw:icon name="users"/>Rooms</a>
      <a href="<c:url value='/app/doubts'/>"<c:if test="${nav == 'doubts'}"> aria-current="page"</c:if>><pw:icon name="circle-help"/>Doubts</a>
      <a href="<c:url value='/app/live'/>"<c:if test="${nav == 'live'}"> aria-current="page"</c:if>><pw:icon name="users"/>Live sessions</a>
      <a href="<c:url value='/app/tutor'/>"<c:if test="${nav == 'tutor'}"> aria-current="page"</c:if>><pw:icon name="sparkles"/>Tutor</a>
      <c:if test="${user.teacher or user.admin}">
        <div class="nav-section">Teach</div>
        <a href="<c:url value='/teach'/>"<c:if test="${nav == 'teach'}"> aria-current="page"</c:if>><pw:icon name="school"/>Teaching</a>
      </c:if>
      <c:if test="${user.admin}">
        <div class="nav-section">Platform</div>
        <a href="<c:url value='/admin'/>"<c:if test="${nav == 'admin'}"> aria-current="page"</c:if>><pw:icon name="shield"/>Overview</a>
        <a href="<c:url value='/admin/content'/>"<c:if test="${nav == 'admin-content'}"> aria-current="page"</c:if>><pw:icon name="book-open"/>Content</a>
        <a href="<c:url value='/admin/assessments'/>"<c:if test="${nav == 'admin-assessments'}"> aria-current="page"</c:if>><pw:icon name="chart-column"/>Item analysis</a>
        <a href="<c:url value='/admin/moderation'/>"<c:if test="${nav == 'admin-moderation'}"> aria-current="page"</c:if>><pw:icon name="shield"/>Moderation</a>
        <a href="<c:url value='/admin/users'/>"<c:if test="${nav == 'admin-users'}"> aria-current="page"</c:if>><pw:icon name="users"/>People</a>
        <a href="<c:url value='/admin/messages'/>"<c:if test="${nav == 'admin-messages'}"> aria-current="page"</c:if>><pw:icon name="mail"/>Messages</a>
        <a href="<c:url value='/admin/rooms'/>"<c:if test="${nav == 'admin-rooms'}"> aria-current="page"</c:if>><pw:icon name="school"/>Rooms</a>
        <a href="<c:url value='/admin/games'/>"<c:if test="${nav == 'admin-games'}"> aria-current="page"</c:if>><pw:icon name="gamepad-2"/>Games</a>
        <a href="<c:url value='/admin/audit'/>"<c:if test="${nav == 'admin-audit'}"> aria-current="page"</c:if>><pw:icon name="file-text"/>Audit log</a>
        <a href="<c:url value='/admin/settings'/>"<c:if test="${nav == 'admin-settings'}"> aria-current="page"</c:if>><pw:icon name="settings"/>Settings</a>
      </c:if>
      <div class="nav-section">You</div>
      <a href="<c:url value='/app/settings'/>"<c:if test="${nav == 'settings'}"> aria-current="page"</c:if>><pw:icon name="settings"/>Settings</a>
    </nav>
    <div class="sidebar-footer">
      <div class="user-chip">
        <span class="avatar" aria-hidden="true"><c:out value="${user.initials}"/></span>
        <div class="grow">
          <div class="name"><c:out value="${user.displayName}"/></div>
          <div class="role"><c:out value="${user.admin ? 'Administrator' : (user.teacher ? 'Teacher' : 'Student')}"/></div>
        </div>
      </div>
      <pw:form action="/logout">
        <button type="submit" class="btn btn-ghost btn-block"><pw:icon name="log-out"/>Sign out</button>
      </pw:form>
    </div>
  </aside>

  <div class="column">
    <header class="topbar">
      <pw:logo href="/app/today"/>
      <div class="topbar-actions">
        <a class="icon-btn bell" href="<c:url value='/app/notifications'/>" aria-label="Notifications<c:if test='${requestScope.unreadNotifications > 0}'>, <c:out value='${requestScope.unreadNotifications}'/> unread</c:if>"><pw:icon name="bell"/><c:if test="${requestScope.unreadNotifications > 0}"><span class="bell-count" aria-hidden="true"><c:out value="${requestScope.unreadNotifications > 9 ? '9+' : requestScope.unreadNotifications}"/></span></c:if></a>
        <button type="button" class="icon-btn" data-theme-toggle data-save-url="<c:url value='/app/settings/theme'/>" aria-label="Switch theme">
          <pw:icon name="moon"/>
        </button>
        <a class="icon-btn" href="<c:url value='/app/settings'/>" aria-label="Settings"><pw:icon name="settings"/></a>
      </div>
    </header>

    <main id="main" class="main" tabindex="-1">
      <div class="page">
        <c:if test="${not empty requestScope.flash}">
          <pw:alert kind="${requestScope.flash.kind}" message="${requestScope.flash.message}"/>
        </c:if>
        <jsp:doBody/>
      </div>
    </main>
  </div>

  <nav class="tabbar" aria-label="Primary">
    <a href="<c:url value='/app/today'/>"<c:if test="${nav == 'today'}"> aria-current="page"</c:if>><pw:icon name="compass"/>Today</a>
    <a href="<c:url value='/app/learn'/>"<c:if test="${nav == 'learn'}"> aria-current="page"</c:if>><pw:icon name="map"/>Learn</a>
    <a href="<c:url value='/app/practice'/>"<c:if test="${nav == 'practice'}"> aria-current="page"</c:if>><pw:icon name="target"/>Practice</a>
    <c:if test="${user.teacher or user.admin}">
      <a href="<c:url value='/teach'/>"<c:if test="${nav == 'teach'}"> aria-current="page"</c:if>><pw:icon name="school"/>Teach</a>
    </c:if>
    <c:if test="${user.admin}">
      <a href="<c:url value='/admin'/>"<c:if test="${nav == 'admin'}"> aria-current="page"</c:if>><pw:icon name="shield"/>Admin</a>
    </c:if>
    <a href="<c:url value='/app/settings'/>"<c:if test="${nav == 'settings'}"> aria-current="page"</c:if>><pw:icon name="user"/>You</a>
  </nav>
</div>
</pw:document>
