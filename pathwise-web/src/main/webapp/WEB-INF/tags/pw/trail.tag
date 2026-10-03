<%@ tag description="Decorative learning-trail illustration (no data; purely visual)" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="cssClass" required="false" %>
<%@ attribute name="onInk" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<svg class="trail <c:out value='${cssClass}'/><c:if test='${onInk}'> trail-ink</c:if>" viewBox="0 0 480 320" aria-hidden="true" focusable="false">
  <path class="trail-base" d="M40 270 C 120 270, 110 190, 190 190 S 280 120, 330 120 S 420 70, 440 40" fill="none" stroke-width="3" stroke-linecap="round"/>
  <path class="trail-path" d="M190 190 S 280 120, 330 120 S 420 70, 440 40" fill="none" stroke-width="3" stroke-linecap="round"/>

  <!-- mastered -->
  <circle class="trail-node-bg" cx="40" cy="270" r="22"/>
  <circle class="trail-ring-mastered" cx="40" cy="270" r="17" fill="none" stroke-width="5"/>
  <path class="trail-check" d="M32 270l6 6 11-12" fill="none" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>

  <!-- proficient -->
  <circle class="trail-node-bg" cx="112" cy="232" r="18"/>
  <circle class="trail-ring-track" cx="112" cy="232" r="13" fill="none" stroke-width="4"/>
  <circle class="trail-ring-proficient" cx="112" cy="232" r="13" fill="none" stroke-width="4" stroke-dasharray="61 82" transform="rotate(-90 112 232)" stroke-linecap="round"/>

  <!-- current: you are here -->
  <circle class="trail-pulse trail-current-halo" cx="190" cy="190" r="22"/>
  <circle class="trail-node-bg" cx="190" cy="190" r="24"/>
  <circle class="trail-ring-track" cx="190" cy="190" r="18" fill="none" stroke-width="5"/>
  <circle class="trail-ring-developing" cx="190" cy="190" r="18" fill="none" stroke-width="5" stroke-dasharray="58 113" transform="rotate(-90 190 190)" stroke-linecap="round"/>
  <circle class="trail-current-dot" cx="190" cy="190" r="6"/>

  <!-- recommended next -->
  <circle class="trail-node-bg" cx="330" cy="120" r="22"/>
  <circle class="trail-ring-next" cx="330" cy="120" r="17" fill="none" stroke-width="3" stroke-dasharray="4 5"/>
  <path class="trail-arrow" d="M323 120h13m-5-6 6 6-6 6" fill="none" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>

  <!-- summit -->
  <circle class="trail-node-bg" cx="440" cy="40" r="20"/>
  <path class="trail-summit" d="M428 50l8-14 5 7 3-4 8 11z"/>

  <g class="trail-labels">
    <text x="40" y="310" text-anchor="middle">Recursion</text>
    <text x="112" y="272" text-anchor="middle">Trees</text>
    <text x="190" y="236" text-anchor="middle">Traversals</text>
    <text x="330" y="164" text-anchor="middle">Binary search trees</text>
  </g>
</svg>
