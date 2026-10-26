'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const script = fs.readFileSync(
  path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'protection.js'),
  'utf8'
);

function browser({ hostname = 'm.youtube.com', protocol = 'https:', enabled = true } = {}) {
  const observers = [];
  const intervals = new Map();
  const timeouts = new Map();
  const elements = [];
  const listeners = new Map();
  let nextTimer = 1;

  function element(tagName) {
    const node = {
      tagName: tagName.toUpperCase(),
      id: '',
      textContent: '',
      style: {},
      parentNode: null,
      children: [],
      attributes: new Map(),
      appendChild(child) {
        child.parentNode = this;
        this.children.push(child);
        return child;
      },
      removeChild(child) {
        const index = this.children.indexOf(child);
        if (index >= 0) this.children.splice(index, 1);
        child.parentNode = null;
        return child;
      },
      remove() {
        if (this.parentNode) this.parentNode.removeChild(this);
      },
      setAttribute(name, value) {
        this.attributes.set(name, String(value));
      },
      getAttribute(name) {
        return this.attributes.get(name) ?? null;
      },
      querySelectorAll() { return []; },
      querySelector() { return null; },
      matches() { return false; },
      closest() { return null; }
    };
    elements.push(node);
    return node;
  }

  const html = element('html');
  const head = element('head');
  const body = element('body');
  html.appendChild(head);
  html.appendChild(body);
  const document = {
    readyState: 'complete',
    documentElement: html,
    head,
    body,
    createElement: element,
    getElementById(id) { return elements.find(node => node.id === id && node.parentNode) ?? null; },
    querySelectorAll() { return []; },
    querySelector() { return null; },
    addEventListener(name, callback) {
      const group = listeners.get(name) ?? new Set();
      group.add(callback);
      listeners.set(name, group);
    },
    removeEventListener(name, callback) { listeners.get(name)?.delete(callback); }
  };

  class MutationObserver {
    constructor(callback) {
      this.callback = callback;
      this.active = false;
      observers.push(this);
    }
    observe() { this.active = true; }
    disconnect() { this.active = false; }
    trigger(records = []) { if (this.active) this.callback(records, this); }
  }

  class Response {
    constructor(value) { this.value = value; }
    async json() { return this.value; }
  }

  const originalFetch = async () => ({
    ok: true,
    clone() { return this; },
    async text() { return '{}'; },
    async json() { return {}; }
  });
  const context = {
    location: { hostname, protocol, href: `${protocol}//${hostname}/watch?v=test` },
    document,
    MutationObserver,
    Response,
    fetch: originalFetch,
    __clarotubeProtection: enabled,
    setInterval(callback) {
      const id = nextTimer++;
      intervals.set(id, callback);
      return id;
    },
    clearInterval(id) { intervals.delete(id); },
    setTimeout(callback) {
      const id = nextTimer++;
      timeouts.set(id, callback);
      return id;
    },
    clearTimeout(id) { timeouts.delete(id); },
    addEventListener: document.addEventListener.bind(document),
    removeEventListener: document.removeEventListener.bind(document),
    console
  };
  context.window = context;
  context.self = context;
  vm.createContext(context);

  return {
    context,
    document,
    observers,
    intervals,
    timeouts,
    elements,
    originalFetch,
    inject() { vm.runInContext(script, context, { filename: 'protection.js' }); },
    parse(value) {
      return vm.runInContext('JSON.parse', context)(JSON.stringify(value));
    },
    originalParse() { return vm.runInContext('JSON.parse', context); },
    activeObservers() { return observers.filter(observer => observer.active).length; },
    styles() { return elements.filter(node => node.tagName === 'STYLE' && node.parentNode); },
    tick() { for (const callback of [...intervals.values()]) callback(); }
  };
}

test('protection stays inactive without the flag or on an external host', () => {
  for (const options of [
    { enabled: false },
    { hostname: 'youtube.com.evil.example' },
    { hostname: 'example.com' },
    { hostname: 'other.youtube.com' },
    { protocol: 'http:' }
  ]) {
    const page = browser(options);
    page.inject();
    assert.equal(page.styles().length, 0, JSON.stringify(options));
    assert.equal(page.activeObservers(), 0, JSON.stringify(options));
    assert.equal(page.intervals.size, 0, JSON.stringify(options));
    assert.equal(page.context.fetch, page.originalFetch, JSON.stringify(options));
  }
});

test('injection is idempotent and cleanup releases active work', () => {
  const page = browser();
  const initialParse = page.originalParse();
  const initialResponseJson = page.context.Response.prototype.json;
  page.inject();
  assert.equal(typeof page.context.__clarotubeCleanup, 'function');
  assert.deepEqual(
    Object.keys(page.context.__clarotubeStats).sort(),
    ['hiddenAds', 'skippedAds']
  );
  const firstStyleCount = page.styles().length;
  const firstObserverCount = page.activeObservers();
  const firstIntervalCount = page.intervals.size;
  assert.notEqual(page.originalParse(), initialParse, 'JSON.parse was not wrapped');
  assert.notEqual(page.context.Response.prototype.json, initialResponseJson, 'Response.json was not wrapped');
  page.inject();
  assert.equal(page.styles().length, firstStyleCount, 'duplicate CSS');
  assert.equal(page.activeObservers(), firstObserverCount, 'duplicate observers');
  assert.equal(page.intervals.size, firstIntervalCount, 'duplicate timers');

  page.observers[0].trigger();
  assert.equal(page.timeouts.size, 1, 'observer did not schedule an inspection');

  page.context.__clarotubeProtection = false;
  page.context.__clarotubeCleanup();
  page.context.__clarotubeCleanup();
  assert.equal(page.styles().length, 0, 'CSS remains after cleanup');
  assert.equal(page.activeObservers(), 0, 'observer remains after cleanup');
  assert.equal(page.intervals.size, 0, 'timer remains after cleanup');
  assert.equal(page.timeouts.size, 0, 'timeout remains after cleanup');
  assert.equal(page.context.fetch, page.originalFetch, 'fetch hook remains after cleanup');
  assert.equal(page.originalParse(), initialParse, 'JSON.parse hook remains after cleanup');
  assert.equal(page.context.Response.prototype.json, initialResponseJson, 'Response.json hook remains after cleanup');
});

test('disabled reinjection cleans the previous runtime and can restart', () => {
  const page = browser();
  page.inject();
  const stats = page.context.__clarotubeStats;
  assert.equal(page.styles().length, 1);
  page.context.__clarotubeProtection = false;
  page.inject();
  assert.equal(page.styles().length, 0);
  assert.equal(page.activeObservers(), 0);
  assert.equal(page.intervals.size, 0);
  page.context.__clarotubeProtection = true;
  page.inject();
  assert.equal(page.styles().length, 1);
  assert.equal(page.activeObservers(), 1);
  assert.equal(page.context.__clarotubeStats, stats, 'counters were reset on restart');
});

test('missing document is a safe no-op', () => {
  const page = browser();
  page.context.document = null;
  assert.doesNotThrow(() => page.inject());
  assert.equal(page.intervals.size, 0);
  assert.equal(page.activeObservers(), 0);
});

test('player responses lose only named ad instructions', async () => {
  const page = browser();
  page.inject();
  const content = {
    streamingData: { formats: [{ itag: 18, url: 'https://r1.googlevideo.com/videoplayback' }] },
    videoDetails: { title: 'Sample video', videoId: 'Video123' },
    playabilityStatus: { status: 'OK' },
    captions: { captionTracks: [{ languageCode: 'es' }] },
    consent: { required: true },
    playerAds: [{ id: 'ad' }],
    adPlacements: [{ id: 'placement' }],
    adSlots: [{ id: 'slot' }]
  };
  const parsed = page.parse(content);
  assert.equal(Object.hasOwn(parsed, 'playerAds'), false);
  assert.equal(Object.hasOwn(parsed, 'adPlacements'), false);
  assert.equal(Object.hasOwn(parsed, 'adSlots'), false);
  for (const name of ['streamingData', 'videoDetails', 'playabilityStatus', 'captions', 'consent']) {
    assert.deepEqual(JSON.parse(JSON.stringify(parsed[name])), content[name], `${name} was altered`);
  }

  const wrapped = page.parse({ playerResponse: content, title: 'Page title' });
  assert.equal(Object.hasOwn(wrapped.playerResponse, 'adPlacements'), false);
  assert.equal(wrapped.title, 'Page title');

  const response = await new page.context.Response(JSON.parse(JSON.stringify(content))).json();
  assert.deepEqual(JSON.parse(JSON.stringify(response.streamingData)), content.streamingData);
  assert.equal(Object.hasOwn(response, 'playerAds'), false);

  const ordinary = page.parse({ title: 'Search result', playerAds: ['ordinary field'] });
  assert.deepEqual(Array.from(ordinary.playerAds), ['ordinary field'], 'unrelated JSON was altered');

  const initialResponse = JSON.parse(JSON.stringify(content));
  page.context.ytInitialPlayerResponse = initialResponse;
  page.tick();
  assert.equal(Object.hasOwn(initialResponse, 'adPlacements'), false);
  assert.deepEqual(initialResponse.streamingData, content.streamingData);
  assert.deepEqual(initialResponse.playabilityStatus, content.playabilityStatus);
});

test('only observed ad elements are counted, once each', () => {
  const page = browser();
  const placeholder = { id: 'player-ads', childElementCount: 0 };
  const ad = { id: '', childElementCount: 1 };
  const nodes = [placeholder, ad];
  page.document.querySelectorAll = () => nodes;
  page.inject();
  assert.doesNotMatch(page.styles()[0].textContent, /consent|anti.?adblock|dialog/i);
  assert.equal(page.context.__clarotubeStats.hiddenAds, 1);
  page.tick();
  assert.equal(page.context.__clarotubeStats.hiddenAds, 1, 'same element counted twice');
  nodes.push({ id: '', childElementCount: 1 });
  page.tick();
  assert.equal(page.context.__clarotubeStats.hiddenAds, 2);
  assert.equal(page.context.__clarotubeStats.skippedAds, 0);
});

test('ordinary video stays untouched; one skip click is counted per ad', () => {
  const page = browser();
  const video = { duration: 100, currentTime: 12, playbackRate: 1, muted: false };
  const button = { disabled: false, hidden: false, clicks: 0, click() { this.clicks++; } };
  const player = {
    querySelector(selector) { return selector === 'video' ? video : button; }
  };
  let adShowing = false;
  page.document.querySelector = () => adShowing ? player : null;
  page.inject();
  page.tick();
  assert.deepEqual(video, { duration: 100, currentTime: 12, playbackRate: 1, muted: false });
  assert.equal(button.clicks, 0);
  assert.equal(page.context.__clarotubeStats.skippedAds, 0);

  adShowing = true;
  page.tick();
  page.tick();
  assert.equal(button.clicks, 1, 'one ad was skipped twice');
  assert.equal(page.context.__clarotubeStats.skippedAds, 1);
  assert.deepEqual(video, { duration: 100, currentTime: 12, playbackRate: 1, muted: false });
  adShowing = false;
  page.tick();
  adShowing = true;
  page.tick();
  assert.equal(button.clicks, 2, 'new ad was not counted');
  assert.equal(page.context.__clarotubeStats.skippedAds, 2);
});

test('temporary ad playback changes are restored on cleanup', () => {
  const page = browser();
  const video = { duration: 30, currentTime: 0, playbackRate: 1.25, muted: false };
  const player = { querySelector(selector) { return selector === 'video' ? video : null; } };
  page.document.querySelector = () => player;
  page.inject();
  assert.equal(video.playbackRate, 8);
  assert.equal(video.muted, true);
  assert.ok(video.currentTime > 29, 'ad was not advanced');
  assert.equal(page.context.__clarotubeStats.skippedAds, 1);
  page.context.__clarotubeCleanup();
  assert.equal(video.playbackRate, 1.25);
  assert.equal(video.muted, false);
});
