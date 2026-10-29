// Brands: pages see Google Chrome's brands in navigator.userAgentData, as
// the user agent says, not Android WebView's. Only on a WebView too old to
// be told them itself (then the request headers can't be changed either).
{
  const data = window.navigator && window.navigator.userAgentData;
  // What is made here is given the page's own prototypes: this script may
  // run from a realm of its own (see `hosted`).
  const own = (value) => setPrototypeOf(value, Array.isArray(value) ? window.Array.prototype : window.Object.prototype);
  const copy = (list) => own(list.map((b) => own({ brand: b.brand, version: b.version })));
  const brands = config.brands;
  if (data && brands) {
    const proto = getPrototypeOf(data);
    defineGetter(proto, "brands", () => copy(brands));
    define(proto, "getHighEntropyValues", (original) => function (hints) {
      return original.call(this, hints).then((values) => {
        if ("brands" in values) values.brands = copy(brands);
        if ("fullVersionList" in values && config.fullVersionList) values.fullVersionList = copy(config.fullVersionList);
        if ("uaFullVersion" in values && config.fullVersion) values.uaFullVersion = config.fullVersion;
        return values;
      });
    });
    define(proto, "toJSON", (original) => function () {
      const values = original.call(this);
      values.brands = copy(brands);
      return values;
    });
  }
}
