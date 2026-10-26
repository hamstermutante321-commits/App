(function () {
  "use strict";

  if (typeof window === "undefined") {
    return;
  }

  var previous = window.__clarotubeRuntime;
  var location = window.location;
  var host = location && typeof location.hostname === "string"
    ? location.hostname.toLowerCase() : "";
  var allowed = location && location.protocol === "https:" && (
    host === "youtube.com" || host === "www.youtube.com" ||
    host === "m.youtube.com" || host === "music.youtube.com" ||
    host === "youtu.be");

  if (!allowed || window.__clarotubeProtection !== true) {
    if (previous && typeof previous.cleanup === "function") {
      previous.cleanup();
    }
    return;
  }
  if (previous && previous.active) {
    return;
  }

  var doc = window.document;
  if (!doc) {
    return;
  }

  var stats = window.__clarotubeStats;
  if (!stats || typeof stats.skippedAds !== "number" ||
      typeof stats.hiddenAds !== "number") {
    stats = { skippedAds: 0, hiddenAds: 0 };
  }
  window.__clarotubeStats = stats;

  var adSelector = [
    "ytm-ad-slot-renderer",
    "ytd-ad-slot-renderer",
    "ytd-display-ad-renderer",
    "ytm-promoted-sparkles-web-renderer",
    "ytd-promoted-sparkles-web-renderer",
    ".ytp-ad-overlay-container",
    "#player-ads"
  ].join(",");
  var skipSelector = [
    ".ytp-ad-skip-button",
    ".ytp-ad-skip-button-modern",
    ".ytp-skip-ad-button",
    ".ytp-ad-skip-button-slot button"
  ].join(",");

  var styleNode = null;
  var observer = null;
  var intervalId = null;
  var pendingId = null;
  var seenHidden = new WeakSet();
  var activeAd = false;
  var skippedThisAd = false;
  var savedVideo = null;
  var jsonObject = window.JSON || JSON;
  var originalParse = jsonObject.parse;
  var patchedParse = null;
  var responsePrototype = window.Response && window.Response.prototype;
  var originalResponseJson = responsePrototype && responsePrototype.json;
  var patchedResponseJson = null;
  var runtime = { active: true, cleanup: cleanup };

  window.__clarotubeRuntime = runtime;
  window.__clarotubeCleanup = cleanup;

  function hasOwn(value, name) {
    return Object.prototype.hasOwnProperty.call(value, name);
  }

  function isPlayerResponse(value) {
    return value && typeof value === "object" && !Array.isArray(value) && (
      hasOwn(value, "streamingData") || hasOwn(value, "videoDetails") ||
      hasOwn(value, "playabilityStatus") || hasOwn(value, "playbackTracking"));
  }

  function removeAdFields(value) {
    if (!isPlayerResponse(value)) {
      return;
    }
    // These are ad instructions in a player response, not video or stream data.
    ["playerAds", "adPlacements", "adSlots"].forEach(function (field) {
      if (hasOwn(value, field)) {
        try {
          delete value[field];
        } catch (ignored) {
          // A frozen response must be left as it was.
        }
      }
    });
  }

  function sanitize(value) {
    try {
      removeAdFields(value);
      if (value && typeof value === "object") {
        removeAdFields(value.playerResponse);
        removeAdFields(value.player_response);
      }
    } catch (ignored) {
      // A malformed or getter-backed object must not affect playback.
    }
    return value;
  }

  function inspectInitialResponse() {
    try {
      sanitize(window.ytInitialPlayerResponse);
      if (window.ytplayer && window.ytplayer.config && window.ytplayer.config.args) {
        sanitize(window.ytplayer.config.args.player_response);
      }
    } catch (ignored) {
      // The site may replace these globals while loading a new video.
    }
  }

  if (typeof originalParse === "function") {
    patchedParse = function () {
      return sanitize(originalParse.apply(this, arguments));
    };
    try {
      jsonObject.parse = patchedParse;
    } catch (ignored) {
      patchedParse = null;
    }
  }
  if (typeof originalResponseJson === "function") {
    patchedResponseJson = function () {
      var result = originalResponseJson.apply(this, arguments);
      return result && typeof result.then === "function"
        ? result.then(sanitize) : sanitize(result);
    };
    try {
      responsePrototype.json = patchedResponseJson;
    } catch (ignored) {
      patchedResponseJson = null;
    }
  }

  function installStyle() {
    if (styleNode || !doc.createElement) {
      return;
    }
    var parent = doc.head || doc.documentElement;
    if (!parent || !parent.appendChild) {
      return;
    }
    try {
      styleNode = doc.createElement("style");
      styleNode.setAttribute("data-clarotube-protection", "");
      styleNode.textContent = adSelector + "{display:none !important;}";
      parent.appendChild(styleNode);
    } catch (ignored) {
      styleNode = null;
    }
  }

  function countHidden() {
    if (!styleNode || !doc.querySelectorAll) {
      return;
    }
    try {
      var nodes = doc.querySelectorAll(adSelector);
      for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        // YouTube can keep an empty ad placeholder in the player at all times.
        if ((node.id === "player-ads" ||
            (node.classList && node.classList.contains("ytp-ad-overlay-container"))) &&
            node.childElementCount === 0) {
          continue;
        }
        if (!seenHidden.has(node)) {
          seenHidden.add(node);
          stats.hiddenAds++;
        }
      }
    } catch (ignored) {
      // DOM replacements during navigation are expected.
    }
  }

  function restoreVideo() {
    if (!savedVideo) {
      return;
    }
    var saved = savedVideo;
    savedVideo = null;
    try {
      if (saved.video.playbackRate === 8) {
        saved.video.playbackRate = saved.rate;
      }
      if (saved.video.muted === true) {
        saved.video.muted = saved.muted;
      }
    } catch (ignored) {
      // The site may have detached the video element.
    }
  }

  function isUsableButton(button) {
    if (!button || button.disabled || button.hidden ||
        (button.getAttribute && button.getAttribute("aria-hidden") === "true")) {
      return false;
    }
    if (typeof window.getComputedStyle === "function") {
      try {
        var css = window.getComputedStyle(button);
        if (css.display === "none" || css.visibility === "hidden") {
          return false;
        }
      } catch (ignored) {
        return false;
      }
    }
    return typeof button.click === "function";
  }

  function monitorPlayer() {
    if (!doc.querySelector) {
      return;
    }
    var player;
    try {
      player = doc.querySelector(".html5-video-player.ad-showing, #movie_player.ad-showing");
    } catch (ignored) {
      return;
    }
    if (!player) {
      activeAd = false;
      skippedThisAd = false;
      restoreVideo();
      return;
    }
    if (!activeAd) {
      activeAd = true;
      skippedThisAd = false;
    }
    if (skippedThisAd) {
      return;
    }

    try {
      var button = player.querySelector && player.querySelector(skipSelector);
      if (isUsableButton(button)) {
        button.click();
        stats.skippedAds++;
        skippedThisAd = true;
        restoreVideo();
        return;
      }

      var video = player.querySelector && player.querySelector("video");
      if (!video || !Number.isFinite(video.duration) || video.duration <= 0 ||
          video.duration > 180 || !Number.isFinite(video.currentTime)) {
        return;
      }
      if (savedVideo && savedVideo.video !== video) {
        restoreVideo();
      }
      if (!savedVideo) {
        savedVideo = { video: video, rate: video.playbackRate, muted: video.muted };
      }
      video.muted = true;
      video.playbackRate = 8;

      var end = Math.max(0, video.duration - 0.25);
      if (end > video.currentTime + 0.75) {
        video.currentTime = end;
        if (video.currentTime >= end - 0.5) {
          stats.skippedAds++;
          skippedThisAd = true;
        }
      }
    } catch (ignored) {
      // Ads and controls can disappear between detection and interaction.
    }
  }

  function inspect() {
    if (!runtime.active) {
      return;
    }
    installStyle();
    inspectInitialResponse();
    countHidden();
    monitorPlayer();
  }

  function scheduleInspect() {
    if (pendingId !== null || typeof window.setTimeout !== "function") {
      return;
    }
    pendingId = window.setTimeout(function () {
      pendingId = null;
      inspect();
    }, 100);
  }

  function start() {
    if (!runtime.active) {
      return;
    }
    inspect();
    if (typeof window.MutationObserver === "function" && doc.documentElement) {
      try {
        observer = new window.MutationObserver(scheduleInspect);
        observer.observe(doc.documentElement, {
          childList: true, subtree: true, attributes: true, attributeFilter: ["class"]
        });
      } catch (ignored) {
        observer = null;
      }
    }
    if (typeof window.setInterval === "function") {
      intervalId = window.setInterval(inspect, 1000);
    }
  }

  function cleanup() {
    if (!runtime.active) {
      return;
    }
    runtime.active = false;
    if (observer) {
      observer.disconnect();
      observer = null;
    }
    if (intervalId !== null && typeof window.clearInterval === "function") {
      window.clearInterval(intervalId);
      intervalId = null;
    }
    if (pendingId !== null && typeof window.clearTimeout === "function") {
      window.clearTimeout(pendingId);
      pendingId = null;
    }
    if (doc.removeEventListener) {
      doc.removeEventListener("DOMContentLoaded", start);
    }
    if (styleNode && styleNode.parentNode) {
      styleNode.parentNode.removeChild(styleNode);
      styleNode = null;
    }
    restoreVideo();
    if (patchedParse && jsonObject.parse === patchedParse) {
      jsonObject.parse = originalParse;
    }
    if (patchedResponseJson && responsePrototype.json === patchedResponseJson) {
      responsePrototype.json = originalResponseJson;
    }
    if (window.__clarotubeRuntime === runtime) {
      window.__clarotubeRuntime = null;
    }
  }

  if (doc.documentElement) {
    start();
  } else if (doc.addEventListener) {
    doc.addEventListener("DOMContentLoaded", start);
  }
})();
