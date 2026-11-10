// The search box of the sidebar: filters the views and elements of search.js by name, type and description.
// Without JavaScript the box stays hidden and the sidebar is a plain list.
(function () {
  var form = document.querySelector("aside.sidebar form.search");
  var input = document.getElementById("search");
  var results = document.getElementById("search-results");
  var tree = document.querySelector("aside.sidebar .tree");
  var items = window.DOC_SEARCH;
  if (!form || !input || !results || !tree || !items) {
    return;
  }
  form.hidden = false;
  var MAX = 50;
  function text(item) {
    if (!item.s) {
      item.s = (item.n + " " + item.t + " " + item.d).toLowerCase();
    }
    return item.s;
  }
  function show() {
    var words = input.value.toLowerCase().split(/\s+/).filter(function (w) { return w; });
    results.innerHTML = "";
    if (!words.length) {
      results.hidden = true;
      tree.hidden = false;
      return;
    }
    var found = 0;
    for (var i = 0; i < items.length && found < MAX; i++) {
      var item = items[i];
      var all = text(item);
      if (words.every(function (w) { return all.indexOf(w) >= 0; })) {
        var li = document.createElement("li");
        var a = document.createElement("a");
        a.href = item.u;
        a.textContent = item.n;
        var type = document.createElement("small");
        type.textContent = " " + item.t;
        li.appendChild(a);
        li.appendChild(type);
        results.appendChild(li);
        found++;
      }
    }
    if (!found) {
      var none = document.createElement("li");
      none.className = "none";
      none.textContent = results.getAttribute("data-none");
      results.appendChild(none);
    }
    results.hidden = false;
    tree.hidden = true;
  }
  input.addEventListener("input", show);
  input.addEventListener("keydown", function (e) {
    if (e.key === "Escape") {
      input.value = "";
      show();
    }
  });
})();
