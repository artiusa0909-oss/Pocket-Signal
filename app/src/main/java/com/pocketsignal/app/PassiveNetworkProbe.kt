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

  // Extract only market-related field names and value types from UTF-8 JSON.
  // No payload values, identifiers or raw text are ever emitted.
  function marketShape(value, depth) {
    if (depth > 4) return { type: typeof value };
    if (value === null) return { type: 'null' };
    if (Array.isArray(value)) {
      return {
        type: 'array',
        length: value.length,
        item: value.length ? marketShape(value[0], depth + 1) : null
      };
    }
    if (typeof value !== 'object') return { type: typeof value };

    var allowed = /^(symbol|asset|instrument|pair|time|timestamp|ts|open|high|low|close|volume|price|rate|bid|ask|o|h|l|c|v|t|s|p|data|candles|quotes|history|ticks|ohlc|result|payload|params|period|interval|precision|digits|type|action|event|items|values|prices|bars|chart|market|success|status)$/i;
    var keys = Object.keys(value).filter(function (key) { return allowed.test(key); }).slice(0, 32);
    var fields = keys.map(function (key) {
      var item = value[key];
      var type = item === null ? 'null' : Array.isArray(item) ? 'array' : typeof item;
      return key + ':' + type;
    });
    var nested = {};
    keys.forEach(function (key) {
      var item = value[key];
      if (item && typeof item === 'object' && depth < 4) {
        nested[key] = marketShape(item, depth + 1);
      }
    });
    return { type: 'object', fields: fields, nested: nested };
  }

  function inspectBinaryText(bytes) {
    var decoded;
    try {
      decoded = new TextDecoder('utf-8').decode(bytes);
    } catch (_) {
      return { encoding: 'not-utf8' };
    }
    var trimmed = decoded.replace(/^\\uFEFF/, '').trim();
    var candidates = [trimmed];
    var firstJson = trimmed.search(/[\\[{]/);
    if (firstJson > 0) candidates.push(trimmed.slice(firstJson));
    for (var i = 0; i < candidates.length; i++) {
      try {
        var parsed = JSON.parse(candidates[i]);
        return {
          encoding: 'utf-8',
          jsonDetected: true,
          framing: i === 0 ? 'plain-json' : 'prefixed-json',
          structure: marketShape(parsed, 0)
        };
      } catch (_) {}
    }
    var first = trimmed.charAt(0);
    return {
      encoding: 'utf-8',
      jsonDetected: false,
      textLength: decoded.length,
      framing: /^[0-9]+$/.test(first) ? 'numeric-prefix-or-protocol' :
        first === '' ? 'empty-text' : 'text-protocol'
    };
  }

  // Classify binary framing without exporting bytes or attempting to decode
  // potentially sensitive account/session payloads.
  function binaryProfile(data) {
    var bytes;
    if (data instanceof ArrayBuffer) {
      bytes = new Uint8Array(data);
    } else if (ArrayBuffer.isView(data)) {
      bytes = new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
    } else {
      return { format: 'blob-or-unsupported-binary' };
    }

    var length = bytes.byteLength;
    var sampleLength = Math.min(length, 2048);
    if (!sampleLength) return { format: 'empty-binary', bytes: length };

    var counts = new Uint32Array(256);
    var printable = 0;
    for (var i = 0; i < sampleLength; i++) {
      var value = bytes[i];
      counts[value]++;
      if ((value >= 32 && value <= 126) || value === 9 || value === 10 || value === 13) {
        printable++;
      }
    }
    var entropy = 0;
    for (var j = 0; j < counts.length; j++) {
      if (counts[j]) {
        var probability = counts[j] / sampleLength;
        entropy -= probability * Math.log2(probability);
      }
    }

    var b0 = bytes[0], b1 = sampleLength > 1 ? bytes[1] : 0;
    var format = 'opaque-binary';
    if (b0 === 0x1f && b1 === 0x8b) {
      format = 'gzip';
    } else if (b0 === 0x50 && b1 === 0x4b) {
      format = 'zip-container';
    } else if (b0 === 0x89 && b1 === 0x50) {
      format = 'png';
    } else if (b0 === 0xff && b1 === 0xd8) {
      format = 'jpeg';
    } else if (sampleLength > 2 && (b0 & 0x0f) === 8 &&
               (((b0 << 8) | b1) % 31) === 0) {
      format = 'zlib';
    } else if (printable / sampleLength > 0.92) {
      format = 'mostly-text';
    } else if (entropy > 7.5) {
      format = 'high-entropy-binary';
    } else if (entropy < 4.0) {
      format = 'low-entropy-structured-binary';
    }

    var profile = {
      format: format,
      bytes: length,
      sampledBytes: sampleLength,
      printableRatio: Math.round(printable / sampleLength * 100) / 100,
      entropyBitsPerByte: Math.round(entropy * 100) / 100
    };
    if (printable / sampleLength > 0.92) {
      profile.textStructure = inspectBinaryText(bytes);
    }
    return profile;
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
    if (data instanceof ArrayBuffer) {
      return {
        type: 'binary-array-buffer',
        bytes: data.byteLength,
        profile: binaryProfile(data)
      };
    }
    if (ArrayBuffer.isView(data)) {
      return {
        type: 'binary-view',
        bytes: data.byteLength,
        profile: binaryProfile(data)
      };
    }
    if (typeof Blob !== 'undefined' && data instanceof Blob) {
      return { type: 'binary-blob', bytes: data.size };
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
