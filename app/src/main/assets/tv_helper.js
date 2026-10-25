(function () {
  'use strict';
  try {
    if (window.__fcTvHelper) { return; }
    window.__fcTvHelper = true;

    // 1) Always-visible focus ring so you can see where the remote cursor is from the couch.
    var style = document.createElement('style');
    style.textContent =
      '*:focus{outline:4px solid #5aa9ff !important;outline-offset:3px !important;}' +
      '*{-webkit-tap-highlight-color:transparent;}' +
      'html,body{overscroll-behavior:none;}';
    (document.head || document.documentElement).appendChild(style);

    // 2) Make click-only elements (divs/spans with cursor:pointer) reachable with the D-pad.
    var NATIVE = /^(A|BUTTON|INPUT|SELECT|TEXTAREA|SUMMARY|VIDEO|AUDIO|IFRAME)$/;
    var timer = null;

    function markClickables() {
      timer = null;
      if (!document.body) { return; }
      var nodes = document.body.querySelectorAll('div,span,li,img,article,section,svg');
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        if (el.hasAttribute('tabindex') || NATIVE.test(el.tagName) || el.isContentEditable) { continue; }
        var cs = window.getComputedStyle(el);
        if (cs.cursor !== 'pointer') { continue; }
        var p = el.parentElement;
        // Only the outermost pointer element, so children don't become extra focus stops.
        if (p && window.getComputedStyle(p).cursor === 'pointer') { continue; }
        el.setAttribute('tabindex', '0');
        el.setAttribute('data-fc-focus', '1');
      }
    }

    function schedule() {
      if (timer === null) { timer = setTimeout(markClickables, 400); }
    }

    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    schedule();

    // 3) OK / Enter on one of those elements = click.
    document.addEventListener('keydown', function (ev) {
      if (ev.key !== 'Enter' && ev.keyCode !== 13) { return; }
      var a = document.activeElement;
      if (a && a.getAttribute && a.getAttribute('data-fc-focus') === '1') {
        ev.preventDefault();
        a.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
      }
    }, true);
  } catch (e) { /* never break the page */ }
})();
