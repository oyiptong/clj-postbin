"use strict";

var kWebSocketUrl = "ws://" + window.location.host + "/ws";
var kPostUrl = window.location.href + "post";
var kXHRCommandUrl = window.location.href + "xhr";

(function(postbinApp, angular) {
  var postbinDash = angular.module("PostbinDash", [], function($interpolateProvider) {
    $interpolateProvider.startSymbol("[[");
    $interpolateProvider.endSymbol("]]");
  });
  postbinApp.postbinDash = postbinDash;

  var Socket = function($rootScope) {
    this.socket = null;
    this.$rootScope = $rootScope;
    this.openConnection();
  }
  Socket.prototype = {
    openConnection: function() {
      this.socket = new WebSocket(kWebSocketUrl);
    },

    on: function(eventName, callback) {
      var that = this;
      this.socket.addEventListener(eventName, function(evt) {
        that.$rootScope.$apply(function() {
          callback.apply(that, [evt.data]);
        });
      });
    },

    send: function(data) {
      this.socket.send(data);
    },
  }
  postbinDash.service("$socket", Socket);

  postbinDash.controller("messageDisplayCtrl", function($scope, $socket, $http) {
    $scope.messages = [];
    $scope.postData = "";
    $scope.socketEnabled = false;
    $scope.socketDisabled = true;

    /** app state modifiers **/

    $scope.handleRemoteCommand = function(command) {
      if (command.type == "delta") {
        $scope.addDelta(command.data);
      }
      else if (command.type == "purge") {
        $scope.purge()
      }
      else if (command.type == "data") {
        $scope.replaceData(command.data.reverse());
      }
    }
    $scope.$on("remoteCommand", function(command) {
      $scope.handleRemoteCommand(command);
    }); 

    $scope.addDelta = function(delta) {
      var datedDelta  = $scope.dataMakeDate(delta);
      $scope.messages.unshift.apply($scope.messages, datedDelta);
    }

    $scope.purge = function() {
      $scope.messages = [];
    }

    $scope.dataMakeDate = function(data) {
      var datedData = []
      for (var i=0; i < data.length; i++) {
        data[i].time = new Date(data[i].time * 1000);
        datedData.push(data[i]);
      }
      return datedData;
    }

    $scope.replaceData = function(data) {
      $scope.messages = $scope.dataMakeDate(data);
    }
    $scope.replaceData(window.postbinApp.initData.reverse());

    /** remote communication **/

    $scope.sendMessage = function() {
      var postData = $scope.postData; 
      if (postData == "") {
        return false;
      }
      if ($scope.socketEnabled) {
        $socket.send(JSON.stringify({type: "message", data: postData}));
      }
      else {
        $http.post(kPostUrl, $scope.postData).then(function() {
            $scope.postData = "";
        });
      }
      $scope.postData = "";
    }

    $scope.deleteAllMessages = function() {
      if ($scope.socketEnabled) {
        $socket.send(JSON.stringify({type: "delete"}));
      }
      else {
        $scope.sendXHRCommand(JSON.stringify({type: "delete"}));
      }
    }

    $scope.fetchAllData = function() {
      if ($scope.socketEnabled) {
        $socket.send(JSON.stringify({type: "fetch-all"}));
      }
      else {
        $scope.sendXHRCommand(JSON.stringify({type: "fetch-all"}));
      }
    }

    /** XHR handlers **/

    $scope.sendXHRCommand = function(message) {
      $http.post(kXHRCommandUrl, message)
        .then(function(remoteCommand){
          if (remoteCommand) {
            $scope.$broadcast("remoteCommand", remoteCommand);
          }
        });
    }

    /** socket handlers **/

    $socket.on("message", function(data) {
      var command = JSON.parse(data);
      $scope.handleRemoteCommand(command);
    });

    $socket.on("open", function() {
      $scope.socketEnabled = true;
      $scope.socketDisabled = false;
    });

    $socket.on("close", function() {
      $scope.socketEnabled = false;
    });
  });

})(window.postbinApp = window.postbinApp || {}, angular);
