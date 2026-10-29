// The script every document of a tab runs first, before the page's own
// (see PageScript.kt, which fills in the parts marked NOOR). It protects the
// page with protectPage (protect.js), given the profile's config, unless
// shields are down for the site; a sign-in site keeps the phone's own screen
// and hardware (it checks them against the device) and gets the noise.
//
// The protection runs from a realm of its own: an empty frame's, made and
// taken out of the document again at once. V8 leaves a function out of a
// stack trace when its realm's security token isn't that of the code
// reading the stack, and an empty frame that has loaded nothing keeps a
// token of its own, so the protection's functions, made there, show no frame
// in a stack a page reads, as the browser's own built-ins show none (a page
// that runs its code inside them, through an argument's valueOf, would
// otherwise find this script in the stack). The frame is given a document of
// its own text (srcdoc), which comes later, so until it is taken out it is
// the empty document it starts as; nothing is asked of the network. WebView
// runs this script in that frame too, as its window is made: there it does
// nothing (the frame's mark). The protection is compiled there with the
// frame's eval, which a page's policy (CSP) may forbid: the frame is first
// asked to compile "0", so such a page hears of nothing of the protection.
// Then, or failing anything else, the protection runs in the page's realm.
(() => {
"use strict";
// Only sites are protected.
if (!/^(https?|file|data|blob|about):$/.test(location.protocol)) return;
const KEY = /*NOOR:KEY*/"";
try {
  const element = window.frameElement;
  if (element && Object.prototype.hasOwnProperty.call(element, KEY)) return;
} catch (e) {}
const config = /*NOOR:CONFIG*/null;
const SOURCE = /*NOOR:SOURCE*/"";
/*NOOR:CODE*/
const within = (host, site) => host === site || host.endsWith("." + site);
const host = (() => {
  try { if (window.top.location.hostname) return window.top.location.hostname; } catch (e) {}
  try { return window.location.hostname; } catch (e) { return ""; }
})().toLowerCase();
const site = host.indexOf("www.") === 0 ? host.slice(4) : host;
// Shields down for the site: it sees the phone as it is.
if (config.downSites.indexOf(site) !== -1) return;
const realDevice = config.signInSites.some((s) => within(host, s));
const given = {
  seeds: config.seeds,
  screen: realDevice ? null : config.screen,
  hardware: realDevice ? null : config.hardware,
  brands: config.brands,
  fullVersionList: config.fullVersionList,
  fullVersion: config.fullVersion,
};

const hiddenProtect = () => {
  let frame = null;
  let parent = null;
  try {
    const document = window.document;
    frame = document.createElementNS("http://www.w3.org/1999/xhtml", "iframe");
    frame[KEY] = true;
    frame.srcdoc = "";
    parent = document.documentElement || document;
    parent.appendChild(frame);
    const realm = frame.contentWindow;
    if (!realm || realm.location.href !== "about:blank") return null;
    const evaluate = realm.eval;
    evaluate("0");
    const made = evaluate(SOURCE);
    return typeof made === "function" ? made : null;
  } catch (e) {
    return null;
  } finally {
    try { if (frame && parent && frame.parentNode === parent) parent.removeChild(frame); } catch (e) {}
  }
};

// What the protection hands the browser to call later is handed over from
// this realm, the page's, which the browser still calls back (see `outside`
// in protect.js): taken before the page's scripts can change them.
const reflectApply = Reflect.apply;
const reflectConstruct = Reflect.construct;
const callMethod = Function.prototype.call;
const bindMethod = Function.prototype.bind;
const outside = {
  apply: (fn, self, args) => reflectApply(fn, self, args),
  construct: (Type, args) => reflectConstruct(Type, args),
  // A function of this realm that calls `fn` on `self` with no frame of its own.
  callback: (fn, self) => reflectApply(bindMethod, callMethod, [fn, self]),
};

const hidden = hiddenProtect();
if (hidden) {
  try {
    hidden(window, Object.assign({}, given, { framesHidden: true, outside }));
    return;
  } catch (e) {}
}
try {
  protectPage(window, given);
} catch (e) {}
})();
//# sourceURL=/*NOOR:NAME*/
