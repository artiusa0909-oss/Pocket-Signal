package com.pocketsignal.app

/**
 * Passive, privacy-preserving inspection of market-data transport.
 *
 * The probe records only transport type, host, payload size and JSON structure.
 * It never records payload values, cookies, headers, credentials or URL paths.
 * The original WebSocket/fetch/XHR objects and responses are passed through.
 */
internal object PassiveNetworkProbe {
    const val SCRIPT = """
(function () {
  if (window.__pocketSignalProbeInstalled) return;
  window.__pocketSignalProbeInstalled = true;

  var emitted = Object.create(null);
  function safeHost(value) {
    try { return new URL(String(value), location.href).host || 'unknown'; }
    catch (_) { return 'unknown'; }
  }
  function emit(channel, url, data) {
    var host = safeHost(url);
    var key = channel + '|' + host;
    emitted[key] = (emitted[key] || 0) + 1;
    if (emitted[key] > 5) {
      if (emitted[key] === 6) {
        console.info('PS_NET:' + JSON.stringify({
          channel: channel, host: host, note: 'additional messages suppressed'
        }));
      }
      return;
    }

    var info = {
      channel: channel,
      host: host,
      sample: emitted[key],
      payload: describe(data)
    };
    console.info('PS_NET:' + JSON.stringify(info));
  }

  function shape(value, depth) {
    if (depth > 4) return { type: typeof value };
    if (value === null) return { type: 'null' };
    if (Array.isArray(value)) {
      return {
        type: 'array',
        length: value.length,
        item: value.length ? shape(value[0], depth + 1) : null
      };
    }
    if (typeof value === 'object') {
      var keys = Object.keys(value).filter(function (key) {
        return !/(token|auth|cookie|session|password|secret|credential|api.?key)/i.test(key);
      }).slice(0, 24);
      var lower = keys.map(function (key) { return key.toLowerCase(); });
      var aliases = {
        open: ['open', 'o'],
        high: ['high', 'h'],
        low: ['low', 'l'],
        close: ['close', 'c'],
        price: ['price', 'rate', 'bid', 'ask', 'quote']
      };
      var markers = [];
      Object.keys(aliases).forEach(function (name) {
        if (aliases[name].some(function (alias) { return lower.indexOf(alias) >= 0; })) {
          markers.push(name);
        }
      });
      return {
        type: 'object',
        keys: keys,
        markers: markers,
        child: keys.length ? shape(value[keys[0]], depth + 1) : null
      };
    }
    return { type: typeof value };
  }

  function describe(data) {
    if (typeof data === 'string') {
      var result = { type: 'text', length: data.length };
      try {
        var parsed = JSON.parse(data);
        result.format = 'json';
        result.structure = shape(parsed, 0);
      } catch (_) {
        result.format = 'non-json-text';
      }
      return result;
    }
    function binaryStats(buffer) {
      var bytes = new Uint8Array(buffer);
      var counts = new Uint32Array(256);
      var zeros = 0;
      var printable = 0;
      for (var i = 0; i < bytes.length; i++) {
        var value = bytes[i];
        counts[value]++;
        if (value === 0) zeros++;
        if ((value >= 32 && value <= 126) || value === 9 || value === 10 || value === 13) printable++;
      }
      var entropy = 0;
      if (bytes.length) {
        for (var n = 0; n < counts.length; n++) {
          if (counts[n]) {
            var p = counts[n] / bytes.length;
            entropy -= p * Math.log2(p);
          }
        }
      }
      var distinct = 0;
      for (var k = 0; k < counts.length; k++) if (counts[k]) distinct++;
      return {
        bytes: bytes.length,
        distinctByteValues: distinct,
        zeroBytePercent: bytes.length ? Math.round(zeros * 1000 / bytes.length) / 10 : 0,
        printableAsciiPercent: bytes.length ? Math.round(printable * 1000 / bytes.length) / 10 : 0,
        entropyBitsPerByte: Math.round(entropy * 100) / 100
      };
    }
    if (data instanceof ArrayBuffer) {
      return Object.assign({ type: 'binary-array-buffer' }, binaryStats(data));
    }
    if (ArrayBuffer.isView(data)) {
      return Object.assign({ type: 'binary-view' }, binaryStats(data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength)));
    }
    if (typeof Blob !== 'undefined' && data instanceof Blob) {
      return { type: 'binary-blob', bytes: data.size, note: 'content not read' };
    }
    if (data && typeof data === 'object') {
      return { type: 'object', structure: shape(data, 0) };
    }
    return { type: typeof data };
  }

  // WebSocket: observe incoming frames without changing their contents.
  try {
    var NativeWebSocket = window.WebSocket;
    if (NativeWebSocket) {
      function ProbedWebSocket(url, protocols) {
        var socket = protocols === undefined
          ? new NativeWebSocket(url)
          : new NativeWebSocket(url, protocols);
        socket.addEventListener('message', function (event) {
          emit('websocket-message', url, event.data);
        });
        socket.addEventListener('open', function () {
          emit('websocket-open', url, 'connected');
        });
        return socket;
      }
      ProbedWebSocket.prototype = NativeWebSocket.prototype;
      Object.setPrototypeOf(ProbedWebSocket, NativeWebSocket);
      window.WebSocket = ProbedWebSocket;
    }
  } catch (_) {}

  // Fetch: inspect a clone only; the page receives the untouched response.
  try {
    if (window.fetch) {
      var nativeFetch = window.fetch;
      window.fetch = function () {
        var args = arguments;
        var requestUrl = args[0] && args[0].url ? args[0].url : args[0];
        return nativeFetch.apply(this, args).then(function (response) {
          try {
            var clone = response.clone();
            var contentType = clone.headers.get('content-type') || '';
            if (/json|text/i.test(contentType)) {
              clone.text().then(function (body) {
                emit('fetch-response-' + response.status, requestUrl, body);
              }).catch(function () {});
            } else {
              emit('fetch-response-' + response.status, requestUrl,
                { type: 'non-text-response', contentType: contentType });
            }
          } catch (_) {}
          return response;
        });
      };
    }
  } catch (_) {}

  // XMLHttpRequest: observe completed response metadata, never request headers.
  try {
    var NativeXHR = window.XMLHttpRequest;
    if (NativeXHR) {
      var nativeOpen = NativeXHR.prototype.open;
      NativeXHR.prototype.open = function (method, url) {
        this.__psProbeUrl = url;
        return nativeOpen.apply(this, arguments);
      };
      var nativeSend = NativeXHR.prototype.send;
      NativeXHR.prototype.send = function () {
        this.addEventListener('load', function () {
          try {
            var response = this.response;
            if (this.responseType === 'json' || (typeof response === 'object' && response !== null)) {
              emit('xhr-response-' + this.status, this.__psProbeUrl, response);
            } else if (typeof this.responseText === 'string') {
              emit('xhr-response-' + this.status, this.__psProbeUrl, this.responseText);
            } else {
              emit('xhr-response-' + this.status, this.__psProbeUrl,
                { type: 'non-text-response' });
            }
          } catch (_) {}
        }, { once: true });
        return nativeSend.apply(this, arguments);
      };
    }
  } catch (_) {}

  console.info('PS_NET:' + JSON.stringify({
    channel: 'probe-ready',
    document: 'document-start',
    websocket: typeof window.WebSocket,
    fetch: typeof window.fetch,
    xhr: typeof window.XMLHttpRequest
  }));
})();
"""
}
