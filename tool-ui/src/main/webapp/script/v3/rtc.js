define([ 'jquery', 'bsp-utils', 'tabex', 'atmosphere' ], function($, bsp_utils, tabex, atmosphere) {
  if (DISABLE_RTC) {
    return {
      initialize: function () {
      },

      restore: function () {
      },

      receive: function () {
      },

      execute: function () {
      }
    };
  }

  var RESTORE_CHANNEL = 'restore';
  var BROADCAST_CHANNEL = 'broadcast';
  var CLOSE_CHANNEL = 'close';
  var PUSH_KEY_PREFIX = 'brightspot.rtc.push.';
  var SESSION_ID_KEY = 'brightspot.rtc.sessionId';
  var CLOSES_KEY_PREFIX = 'brightspot.rtc.closes.';

  var share = tabex.client();
  var master;
  var closes = [ ];

  share.on('!sys.master', function (data) {
    if (data.node_id === data.master_id) {
      if (master) {
        return;

      } else {
        master = true;
      }

    } else {
      master = false;
      atmosphere.unsubscribe();
      return;
    }

    var request = {
      url: ROOT_PATH + '/_rtc',
      contentType: 'application/json',
      disableDisconnect: true,
      fallbackTransport: 'sse',
      reconnect: false,
      trackMessageLength: true,
      transport: 'sse'
    };

    var socket;
    var subscribe = bsp_utils.throttle(5000, function () {
      socket = atmosphere.subscribe(request);
    });

    var isOnline = false;
    var pingInterval;

    var offlineExecutes = [];
    var onlineExecutes = {
      push: function (message) {
        socket.push(JSON.stringify(message));
      }
    };

    var redoRestores = [];
    var offlineRestores = [];
    var onlineRestores = {
      push: function (message) {
        redoRestores.push(message);
        onlineExecutes.push(message);
        share.emit(RESTORE_CHANNEL, message.className, true);
      }
    };

    var offlineCloses = [];
    var onlineCloses = {
      push: function (message) {
        socket.push(JSON.stringify(message));
      }
    };

    request.onOpen = function () {
      isOnline = true;

      pingInterval = setInterval(function () {
        if (isOnline) {
          onlineExecutes.push({
            type: 'ping'
          });
        }
      }, 10000);

      for (var i = 0, length = localStorage.length; i < length; ++ i) {
        var key = localStorage.key(i);

        if (key && key.indexOf(CLOSES_KEY_PREFIX) === 0) {
          var previousCloses = JSON.parse(localStorage.getItem(key));
          localStorage.removeItem(key);

          $.each(previousCloses, function (i, close) {
            onlineCloses.push(close);
          });
        }
      }

      var oldSessionId = localStorage.getItem(SESSION_ID_KEY);
      var newSessionId = socket.getUUID();

      if (oldSessionId) {
        socket.push(JSON.stringify({
          type: 'migrate',
          oldSessionId: oldSessionId,
          newSessionId: newSessionId
        }));
      }

      localStorage.setItem(SESSION_ID_KEY, newSessionId);

      $.each(redoRestores, function (i, message) {
        onlineExecutes.push(message);
        share.emit(RESTORE_CHANNEL, message.className, true);
      });

      $.each(offlineRestores, function (i, message) {
        onlineRestores.push(message);
      });

      offlineRestores = [];

      $.each(offlineExecutes, function (i, message) {
        onlineExecutes.push(message);
      });

      offlineExecutes = [];
    };

    request.onClose = function () {
      isOnline = false;

      if (pingInterval) {
        clearInterval(pingInterval);
        pingInterval = null;
      }

      subscribe();
    };

    request.onMessage = function (response) {
      share.emit(BROADCAST_CHANNEL, response.responseBody, true);
    };

    request.onMessagePublished = function (response) {
      $.each(response.messages, function (i, message) {
        share.emit(BROADCAST_CHANNEL, message, true);
      });
    };

    subscribe();

    share.on(CLOSE_CHANNEL, function (closes) {
      $.each(closes, function (i, close) {
        (isOnline ? onlineCloses : offlineCloses).push(close);
      });
    });

    var checkRequests = bsp_utils.throttle(50, function () {
      var minKey;

      for (var j = 0; j < 100; ++j) {
        minKey = null;

        for (var i = 0, length = localStorage.length; i < length; ++i) {
          var key = localStorage.key(i);

          if (key && key.indexOf(PUSH_KEY_PREFIX) === 0 && (!minKey || minKey > key)) {
            minKey = key;
          }
        }

        if (!minKey) {
          return;
        }

        var push = JSON.parse(localStorage.getItem(minKey));
        localStorage.removeItem(minKey);

        if (push.restore) {
          (isOnline ? onlineRestores : offlineRestores).push(push.data);

        } else {
          (isOnline ? onlineExecutes : offlineExecutes).push(push.data);
        }
      }
    });

    setInterval(checkRequests, 50);
    $(window).on('storage', checkRequests);

    $(window).on('beforeunload', function () {
      localStorage.setItem(CLOSES_KEY_PREFIX + socket.getUUID(), JSON.stringify(closes));
    });
  });

  var restoreCallbacks = { };

  share.on(RESTORE_CHANNEL, function (state) {
    var callback = restoreCallbacks[state];

    if (callback) {
      callback();
    }
  });

  var broadcastCallbacks = { };

  share.on(BROADCAST_CHANNEL, function (messageString) {
    var message = JSON.parse(messageString);
    var callbacks = broadcastCallbacks[message.broadcast];

    if (callbacks) {
      $.each(callbacks, function(i, callback) {
        callback(message.data);
      });
    }
  });

  var pushId = 0;

  function push(restore, data) {
    ++ pushId;

    localStorage.setItem(PUSH_KEY_PREFIX + $.now() + pushId, JSON.stringify({
      restore: restore,
      data: data
    }));
  }

  $(window).on('beforeunload', function () {
    if (!master) {
      share.emit(CLOSE_CHANNEL, closes);
    }
  });

  function initialize(state, data, callback) {
    restoreCallbacks[state] = callback;

    push(true, {
      type: 'restore',
      className: state,
      data: data
    });
  }

  return {
    initialize: function (state, data, callback) {
      initialize(state, data, callback);

      closes.push({
        type: 'close',
        className: state,
        data: data
      });
    },

    restore: function(state, data, callback) {
      initialize(state, data, callback);
    },

    receive: function(broadcast, callback) {
      var callbacks = broadcastCallbacks[broadcast];

      if (!callbacks) {
        callbacks = broadcastCallbacks[broadcast] = [ ];
      }

      callbacks.push(callback);
    },

    execute: function(action, data) {
      push(false, {
        type: 'action',
        className: action,
        data: data
      });
    }
  };
});
